package com.agustin.tarati.features.online.auth


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.models.ApiErrorBody
import com.agustin.tarati.network.models.AuthResponseDto
import com.agustin.tarati.network.models.ForgotPasswordRequest
import com.agustin.tarati.network.models.GuestRequest
import com.agustin.tarati.network.models.LoginRequest
import com.agustin.tarati.network.models.LogoutRequest
import com.agustin.tarati.network.models.OwnProfileDto
import com.agustin.tarati.network.models.RefreshRequest
import com.agustin.tarati.network.models.RegisterRequest
import com.agustin.tarati.network.models.ResetPasswordRequest
import com.agustin.tarati.network.models.TokenPairDto
import com.agustin.tarati.network.models.localizedApiError
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.billing.EntitlementsRepository
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/**
 * ViewModel que gestiona autenticación y tokens JWT.
 *
 * ### Flujo de tokens:
 *
 * ```
 * loginWithServer(user, pass)
 *   → POST /auth/login
 *   → guarda accessToken + refreshToken en AuthRepository
 *   → authState = Authenticated(expiry = claim `exp` del JWT)
 *
 * refreshToken()  [llamado proactivamente ~2 min antes de expirar]
 *   → POST /auth/refresh con refreshToken guardado
 *   → guarda nuevos accessToken + refreshToken
 *   → actualiza authState.tokenExpiry sin cambiar userInfo
 *
 * isTokenExpiringSoon()  [consultado antes de operaciones críticas]
 *   → true si expira en menos de 2 min
 * ```
 *
 * El refresh token dura 7 días. Si ya expiró, se limpia la sesión y
 * el usuario deberá hacer login nuevamente.
 *
 * @param authRepository Repositorio para almacenar tokens persistentemente
 * @param authApi        Cliente HTTP de los endpoints de auth y perfil propio
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
    // Opcional (nullable) para no romper construcciones directas en tests; Koin inyecta el real.
    private val entitlementsRepository: EntitlementsRepository? = null,
    // Reconcilia logros cross-platform al establecerse la sesión (Android: Play Games ↔ servidor;
    // Desktop/Web: pull desde el servidor). Nullable por la misma razón que entitlementsRepository.
    private val achievementsManager: IAchievementsManager? = null,
) : ViewModel(), IAuthViewModel {

    private val logger = getLogger("AuthViewModel")

    // Parser tolerante para el payload (claims) del JWT: ignora claims que no modelamos.
    private val jwtJson = Json { ignoreUnknownKeys = true }

    /** Duración por defecto del access token — fallback si el JWT no trae claim `exp`. */
    private val _accessTokenDurationMs = 15 * 60 * 1000L   // 15 minutos

    // Refresh token en memoria para sesiones sin "Recordarme".
    // Si hay uno persistido en authRepository, ese tiene prioridad.
    private var _inMemoryRefreshToken: String? = null

    // ============ State ============

    private val _authState = MutableStateFlow<AuthState>(
        AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> =
        _authState.asStateFlow()

    private var _accessToken: String? = null
    override val accessToken: String?
        get() = _accessToken

    private val _profileData = MutableStateFlow<ProfileData?>(null)
    override val profileData: StateFlow<ProfileData?> = _profileData.asStateFlow()

    init {
        // Intentar restaurar sesión guardada
        attemptRestoreSession()
    }

    // ============ Public API ============

    override suspend fun authenticateWithToken(token: String): Result<UserInfo> {
        logger.debug("authenticateWithToken")

        _authState.value = AuthState.Authenticating

        return try {
            val userInfo = parseUserInfoFromToken(token)
            // Expiración real del claim `exp` (los guest tokens duran 4 h, no 15 min);
            // fallback a 15 min si el token no la trae.
            val expiresAt = parseTokenExpiry(token)
                ?: (Clock.System.now().toEpochMilliseconds() + _accessTokenDurationMs)

            _accessToken = token
            _authState.value = AuthState.Authenticated(
                userInfo = userInfo,
                tokenExpiry = expiresAt
            )

            // Cargar ownership + reconciliar logros cross-platform para la sesión recién
            // establecida. No bloquea la autenticación.
            loadCrossPlatformState()

            logger.debug("Authenticated as ${userInfo.username}")
            Result.success(userInfo)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Authentication failed: ${e.message}")
            _authState.value = AuthState.Error(
                message = e.message ?: "Authentication failed"
            )
            Result.failure(e)
        }
    }

    /**
     * Carga el estado cross-platform de la sesión: ownership (entitlements) y logros.
     *
     * Se invoca en cada punto donde la sesión queda establecida —login/guest/register vía
     * [authenticateWithToken] y la restauración al arrancar en [attemptRestoreSession]— para
     * que los cosméticos de supporter y los logros queden disponibles apenas hay sesión, sin
     * depender de que el usuario abra una pantalla concreta. Cada carga corre en su propio
     * launch y no bloquea la autenticación.
     */
    private fun loadCrossPlatformState() {
        viewModelScope.launch { entitlementsRepository?.refresh() }
        viewModelScope.launch { achievementsManager?.syncFromServer() }
    }

    override fun saveToken(token: String) {
        logger.debug("saveToken")
        authRepository.saveToken(token)
        _accessToken = token
    }

    override fun getStoredToken(): String? {
        return authRepository.getToken()
    }

    override suspend fun loginWithServer(
        username: String,
        password: String,
        rememberMe: Boolean,
    ): Result<String> {
        logger.debug("loginWithServer: $username, rememberMe=$rememberMe")
        // Capturar antes de cambiar el estado: si la sesión activa es un invitado, se elimina
        // tras el upgrade para no dejar un usuario/presencia invitado colgado.
        val previousGuestToken = currentGuestTokenOrNull()
        _authState.value = AuthState.Authenticating

        return try {
            val response = authApi.login(LoginRequest(usernameOrEmail = username, password = password))

            if (response.status.value == 200) {
                val tokens = response.body<AuthResponseDto>().tokens
                persistTokens(tokens.accessToken, tokens.refreshToken, rememberMe)
                authenticateWithToken(tokens.accessToken).map { tokens.accessToken }
                    .also { if (it.isSuccess) cleanupPreviousGuest(previousGuestToken) }
            } else {
                val msg = errorMessage(response, fallback = "Login failed")
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("loginWithServer error: ${e.message}")
            _authState.value = AuthState.Error(message = e.message ?: "Login failed")
            Result.failure(e)
        }
    }

    /**
     * Renueva el access token usando el refresh token guardado.
     *
     * Hace POST /auth/refresh. En caso de éxito actualiza accessToken,
     * refreshToken y tokenExpiry sin interrumpir la sesión. Si el
     * refresh token expiró, limpia la sesión y devuelve failure.
     */
    override suspend fun refreshToken(): Result<String> {
        logger.debug("refreshToken")

        val storedRefreshToken = authRepository.getRefreshToken() ?: _inMemoryRefreshToken
        if (storedRefreshToken == null) {
            logger.debug("No refresh token available — cannot refresh")
            return Result.failure(Exception("No refresh token available"))
        }

        return try {
            val response = authApi.refresh(RefreshRequest(refreshToken = storedRefreshToken))

            when (response.status.value) {
                200 -> {
                    val tokens = response.body<TokenPairDto>()

                    // Guardar en el mismo canal que los tokens originales: en disco si la sesión
                    // ya tenía refresh token persistido ("Recordarme"), solo en memoria si no.
                    persistTokens(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        rememberMe = authRepository.getRefreshToken() != null,
                    )

                    // Actualizar estado sin cambiar userInfo — solo expiry y token
                    val currentState = _authState.value
                    val userInfo = (currentState as? AuthState.Authenticated)?.userInfo
                        ?: parseUserInfoFromToken(tokens.accessToken)
                    val newExpiry = parseTokenExpiry(tokens.accessToken)
                        ?: (Clock.System.now().toEpochMilliseconds() + _accessTokenDurationMs)

                    _accessToken = tokens.accessToken
                    _authState.value = AuthState.Authenticated(
                        userInfo = userInfo,
                        tokenExpiry = newExpiry
                    )

                    logger.debug("Token refreshed successfully")
                    Result.success(tokens.accessToken)

                }

                401 -> {
                    // Refresh token expirado — sesión inválida, forzar re-login
                    logger.debug("Refresh token expired (401) — clearing session")
                    authRepository.clearAll()
                    _accessToken = null
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(Exception("Session expired — please log in again"))

                }

                else -> {
                    val msg = "Refresh failed: HTTP ${response.status.value}"
                    logger.debug(msg)
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("refreshToken error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * True si el access token actual expira en menos de [thresholdMs] milisegundos.
     *
     * @param thresholdMs Margen en ms (default: 2 minutos)
     */
    override fun isTokenExpiringSoon(thresholdMs: Long): Boolean {
        val state = _authState.value
        if (state !is AuthState.Authenticated) return false
        val now = Clock.System.now().toEpochMilliseconds()
        return (state.tokenExpiry - now) < thresholdMs
    }

    override suspend fun registerWithServer(
        username: String,
        email: String,
        password: String,
        displayName: String?,
        rememberMe: Boolean,
    ): Result<String> {
        logger.debug("registerWithServer: $username, rememberMe=$rememberMe")
        val previousGuestToken = currentGuestTokenOrNull()
        _authState.value = AuthState.Authenticating

        return try {
            val response = authApi.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password,
                    displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
                )
            )

            if (response.status.value in 200..201) {
                val tokens = response.body<AuthResponseDto>().tokens
                persistTokens(tokens.accessToken, tokens.refreshToken, rememberMe)
                authenticateWithToken(tokens.accessToken).map { tokens.accessToken }
                    .also { if (it.isSuccess) cleanupPreviousGuest(previousGuestToken) }
            } else {
                val msg = errorMessage(response, fallback = "Registration failed")
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("registerWithServer error: ${e.message}")
            _authState.value = AuthState.Error(message = e.message ?: "Registration failed")
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        logger.debug("logout")
        // Revocar refresh token en servidor para invalidar la sesión en todos los dispositivos
        val refreshToken = authRepository.getRefreshToken() ?: _inMemoryRefreshToken
        if (refreshToken != null) {
            try {
                authApi.logout(LogoutRequest(refreshToken = refreshToken))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.debug("Server logout failed (proceeding with local logout): ${e.message}")
            }
        }
        authRepository.clearAll()
        _inMemoryRefreshToken = null
        _accessToken = null
        entitlementsRepository?.clear()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun loginAsGuest(desiredUsername: String?): Result<String> {
        logger.debug("loginAsGuest desiredUsername=$desiredUsername")
        _authState.value = AuthState.Authenticating

        return try {
            val response = authApi.guest(
                GuestRequest(desiredUsername = desiredUsername?.trim()?.takeIf { it.isNotBlank() })
            )

            if (response.status.value == 200) {
                // Guest sessions are not persisted — in-memory only for the current session.
                val accessToken = response.body<TokenPairDto>().accessToken
                _accessToken = accessToken
                authenticateWithToken(accessToken).map { accessToken }
            } else {
                val msg = errorMessage(response, fallback = "Guest login failed")
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("loginAsGuest error: ${e.message}")
            _authState.value = AuthState.Error(message = e.message ?: "Guest login failed")
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            authApi.forgotPassword(ForgotPasswordRequest(email = email.trim()))
            // Siempre éxito desde el punto de vista del cliente — el servidor nunca revela si el email existe
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("forgotPassword error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            val response = authApi.resetPassword(
                ResetPasswordRequest(token = token, newPassword = newPassword)
            )
            if (response.status.value == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Reset failed")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("resetPassword error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchProfile(): Result<Unit> {
        val token = _accessToken ?: return Result.success(Unit)
        return try {
            val response = authApi.fetchProfile(token)
            if (response.status.value == 200) {
                _profileData.value = response.body<OwnProfileDto>().toProfileData()
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Fetch profile failed")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("fetchProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(bio: String?, isVisible: Boolean?, challengesEnabled: Boolean?): Result<Unit> {
        val token = _accessToken ?: return Result.failure(Exception("Not authenticated"))
        // Actualización parcial: solo los campos presentes se modifican en el servidor
        // (ausente = sin cambio), por eso se arma un JsonObject en lugar de un DTO fijo.
        val fields = buildJsonObject {
            if (bio != null) put("bio", bio.trim())
            if (isVisible != null) put("isVisible", isVisible)
            if (challengesEnabled != null) put("challengesEnabled", challengesEnabled)
        }
        if (fields.isEmpty()) return Result.success(Unit)

        return try {
            val response = authApi.updateProfile(token, fields)
            if (response.status.value == 200) {
                _profileData.value = response.body<OwnProfileDto>().toProfileData()
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Update profile failed")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("updateProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val token = _accessToken ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = authApi.deleteAccount(token)
            if (response.status.value == 200) {
                authRepository.clearAll()
                _inMemoryRefreshToken = null
                _accessToken = null
                _profileData.value = null
                _authState.value = AuthState.Unauthenticated
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Delete account failed")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.debug("deleteAccount error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    // ============ Private Helpers ============

    /**
     * Guarda los tokens en el mismo canal según "Recordarme": persistidos en disco si
     * [rememberMe], solo en memoria si no. En ambos casos deja el refresh token en memoria
     * para renovar durante la sesión actual (el de disco tiene prioridad al leer).
     */
    private fun persistTokens(accessToken: String, refreshToken: String?, rememberMe: Boolean) {
        if (rememberMe) {
            authRepository.saveToken(accessToken)
            if (refreshToken != null) authRepository.saveRefreshToken(refreshToken)
        } else {
            authRepository.clearAll()
        }
        if (refreshToken != null) _inMemoryRefreshToken = refreshToken
    }

    /** Proyecta la respuesta de perfil del servidor al modelo editable local. */
    private fun OwnProfileDto.toProfileData(): ProfileData =
        ProfileData(bio = bio, isVisible = isVisible, challengesEnabled = acceptsChallenges)

    /**
     * Deriva el mensaje de error de una respuesta fallida: localiza el `code` del cuerpo de
     * error del servidor, o cae a `"$fallback: HTTP <status>"` si el cuerpo no trae código
     * (o no es JSON). Prefiere `code` sobre `message`/`error`.
     */
    private suspend fun errorMessage(response: HttpResponse, fallback: String): String {
        val code = runCatching { response.body<ApiErrorBody>() }.getOrNull()?.firstNonNull()
        return if (code != null) localizedApiError(code) else "$fallback: HTTP ${response.status.value}"
    }

    /**
     * Intenta restaurar sesión guardada al iniciar.
     *
     * Si hay un refreshToken guardado, puede renovar silenciosamente.
     * Si no, intenta usar el accessToken tal cual.
     */
    private fun attemptRestoreSession() {
        val token = authRepository.getToken() ?: return

        try {
            val userInfo = parseUserInfoFromToken(token)
            val expiresAt = parseTokenExpiry(token)
                ?: (Clock.System.now().toEpochMilliseconds() + _accessTokenDurationMs)
            val now = Clock.System.now().toEpochMilliseconds()

            if (expiresAt > now + 30_000L) {
                // Token aún válido (con margen de 30 s)
                _accessToken = token
                _authState.value = AuthState.Authenticated(userInfo = userInfo, tokenExpiry = expiresAt)
                logger.debug("Session restored for ${userInfo.username}")
                // Cargar ownership + reconciliar logros cross-platform para la sesión restaurada.
                loadCrossPlatformState()
            } else {
                // Token expirado — intentar renovar silenciosamente si hay refresh token
                logger.debug("Stored token expired, attempting silent refresh")
                _authState.value = AuthState.Authenticating
                // El refresh se lanza en GameScreen's proactive loop;
                // aquí dejamos estado Authenticating para que el caller lo maneje
                // al verificar si hay refresh token disponible.
                // Usamos viewModelScope para no bloquear el init.
                viewModelScope.launch {
                    try {
                        val result = refreshToken()
                        if (result.isFailure) {
                            logger.debug("Silent refresh failed — clearing session")
                            authRepository.clearAll()
                            _authState.value = AuthState.Unauthenticated
                        } else {
                            // Sesión restaurada vía silent refresh: cargar ownership + logros.
                            loadCrossPlatformState()
                        }
                    } catch (e: CancellationException) {
                        throw e  // viewModelScope cancelado — no tratar como fallo de refresh
                    } catch (e: Throwable) {
                        // Captura defensiva: cualquier excepción inesperada en el
                        // pipeline de Ktor/serialization que escape de refreshToken().
                        logger.error("Silent refresh threw unexpectedly: ${e::class.simpleName} — ${e.message}")
                        authRepository.clearAll()
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Failed to restore session: ${e.message}")
            authRepository.clearAll()
        }
    }

    /** Token de acceso de la sesión activa si —y solo si— es un invitado; si no, null. */
    private fun currentGuestTokenOrNull(): String? =
        _accessToken?.takeIf { currentUser?.isGuest == true }

    /**
     * Elimina en el servidor el invitado del que se hace "upgrade" al iniciar sesión o registrarse.
     * Evita dejar un usuario/presencia invitado colgado (el WS invitado ya lo cierra el flujo de
     * login en la UI). Best-effort: usa el propio token del invitado y no interrumpe el login si falla.
     * Se lanza en [viewModelScope] para no bloquear el retorno de la autenticación.
     */
    private fun cleanupPreviousGuest(guestToken: String?) {
        if (guestToken == null) return
        viewModelScope.launch {
            try {
                val response = authApi.deleteAccount(guestToken)
                logger.debug("Previous guest cleaned up (HTTP ${response.status.value})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.debug("Previous guest cleanup failed: ${e.message}")
            }
        }
    }

    private fun parseTokenExpiry(token: String): Long? =
        runCatching { decodeJwtPayload(token).exp?.let { it * 1000L } }.getOrNull()

    private fun parseUserInfoFromToken(token: String): UserInfo {
        val payload = decodeJwtPayload(token)
        val username = payload.username ?: payload.sub
        return UserInfo(
            userId = payload.sub,
            username = username,
            displayName = payload.displayName ?: username,
            isGuest = payload.isGuest,
        )
    }

    /** Decodifica el payload (claims) de un JWT. Lanza si el formato es inválido. */
    private fun decodeJwtPayload(token: String): JwtPayload {
        val parts = token.trim().split(".")
        require(parts.size >= 2) { "Invalid JWT format" }
        return jwtJson.decodeFromString(base64UrlDecode(parts[1]))
    }

    /**
     * Decodifica Base64 URL-safe (sin padding, como los segmentos de un JWT) a texto UTF-8.
     * Se filtran los caracteres de espacio/salto de línea: el alfabeto base64url no los incluye,
     * y un token con whitespace espurio (p. ej. copiado/almacenado con un `\n`) haría que
     * `decode` lanzara `IllegalArgumentException: Invalid symbol` en vez de degradar limpio.
     */
    private fun base64UrlDecode(input: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .decode(input.filterNot { it.isWhitespace() })
            .decodeToString()
}

/** Claims del payload JWT que consume el cliente. El resto se ignora ([jwtJson]). */
@Serializable
private data class JwtPayload(
    val sub: String = "",
    val username: String? = null,
    val displayName: String? = null,
    val isGuest: Boolean = false,
    val exp: Long? = null,
)
