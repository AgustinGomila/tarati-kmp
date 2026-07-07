package com.agustin.tarati.features.online.auth


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.models.OwnProfileDto
import com.agustin.tarati.network.models.localizedApiError
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 *   → authState = Authenticated(expiry = now + 15 min)
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
 * @param httpClient     Cliente HTTP KMP inyectado por Koin
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val httpClient: io.ktor.client.HttpClient,
    // Opcional (nullable) para no romper construcciones directas en tests; Koin inyecta el real.
    private val entitlementsRepository: com.agustin.tarati.services.billing.EntitlementsRepository? = null,
    // Reconcilia logros cross-platform al establecerse la sesión (Android: Play Games ↔ servidor;
    // Desktop/Web: pull desde el servidor). Nullable por la misma razón que entitlementsRepository.
    private val achievementsManager: com.agustin.tarati.services.achievements.IAchievementsManager? = null,
) : ViewModel(), IAuthViewModel {

    private val logger = getLogger("AuthViewModel")

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
            val expiresAt = Clock.System.now().toEpochMilliseconds() + _accessTokenDurationMs

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
        } catch (e: Exception) {
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
        _authState.value = AuthState.Authenticating

        return try {
            val response = httpClient.post("$devServerUrl/auth/login") {
                contentType(Application.Json)
                setBody("""{"usernameOrEmail":"$username","password":"$password"}""")
            }

            if (response.status.value == 200) {
                val body = response.bodyAsText()

                val accessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.get(1)
                    ?: return Result.failure(Exception("Server response missing accessToken"))

                val refreshToken = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.get(1)

                if (rememberMe) {
                    authRepository.saveToken(accessToken)
                    if (refreshToken != null) authRepository.saveRefreshToken(refreshToken)
                } else {
                    // Sin "Recordarme": tokens solo en memoria. La sesión dura hasta cerrar la app.
                    authRepository.clearAll()
                }

                // refreshToken en memoria para renovar durante la sesión actual
                if (refreshToken != null) _inMemoryRefreshToken = refreshToken

                authenticateWithToken(accessToken).map { accessToken }
            } else {
                val code = parseServerError(response.bodyAsText())
                val msg = if (code != null) localizedApiError(code) else "Login failed: HTTP ${response.status.value}"
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
            val response = httpClient.post("$devServerUrl/auth/refresh") {
                contentType(Application.Json)
                setBody("""{"refreshToken":"$storedRefreshToken"}""")
            }

            when (response.status.value) {
                200 -> {
                    val body = response.bodyAsText()

                    val newAccessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)
                        ?.groupValues?.get(1)
                        ?: return Result.failure(Exception("Refresh response missing accessToken"))

                    val newRefreshToken = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(body)
                        ?.groupValues?.get(1)

                    // Guardar nuevos tokens en el mismo canal que los originales
                    val isPersisted = authRepository.getRefreshToken() != null
                    if (isPersisted) {
                        authRepository.saveToken(newAccessToken)
                        if (newRefreshToken != null) authRepository.saveRefreshToken(newRefreshToken)
                    } else {
                        // Sesión sin "Recordarme": mantener en memoria
                        if (newRefreshToken != null) _inMemoryRefreshToken = newRefreshToken
                    }

                    // Actualizar estado sin cambiar userInfo — solo expiry y token
                    val currentState = _authState.value
                    val userInfo = (currentState as? AuthState.Authenticated)?.userInfo
                        ?: parseUserInfoFromToken(newAccessToken)
                    val newExpiry = Clock.System.now().toEpochMilliseconds() + _accessTokenDurationMs

                    _accessToken = newAccessToken
                    _authState.value = AuthState.Authenticated(
                        userInfo = userInfo,
                        tokenExpiry = newExpiry
                    )

                    logger.debug("Token refreshed successfully, new expiry in 15 min")
                    Result.success(newAccessToken)

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
        } catch (e: Exception) {
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
        _authState.value = AuthState.Authenticating

        return try {
            val bodyParts = buildList {
                add(""""username":"$username"""")
                add(""""email":"$email"""")
                add(""""password":"$password"""")
                if (!displayName.isNullOrBlank()) add(""""displayName":"$displayName"""")
            }
            val response = httpClient.post("$devServerUrl/auth/register") {
                contentType(Application.Json)
                setBody("{${bodyParts.joinToString(",")}}")
            }

            if (response.status.value in 200..201) {
                val body = response.bodyAsText()
                val accessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.get(1)
                    ?: return Result.failure(Exception("Server response missing accessToken"))

                val refreshToken = Regex(""""refreshToken"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.get(1)

                if (rememberMe) {
                    authRepository.saveToken(accessToken)
                    if (refreshToken != null) authRepository.saveRefreshToken(refreshToken)
                } else {
                    authRepository.clearAll()
                }
                if (refreshToken != null) _inMemoryRefreshToken = refreshToken

                authenticateWithToken(accessToken).map { accessToken }
            } else {
                val code = parseServerError(response.bodyAsText())
                val msg =
                    if (code != null) localizedApiError(code) else "Registration failed: HTTP ${response.status.value}"
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
                httpClient.post("$devServerUrl/auth/logout") {
                    contentType(Application.Json)
                    setBody("""{"refreshToken":"$refreshToken"}""")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
            val body = desiredUsername?.trim()?.takeIf { it.isNotBlank() }
                ?.let { """{"desiredUsername":"$it"}""" }
                ?: "{}"
            val response = httpClient.post("$devServerUrl/auth/guest") {
                contentType(Application.Json)
                setBody(body)
            }

            if (response.status.value == 200) {
                val body = response.bodyAsText()
                val accessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.get(1)
                    ?: return Result.failure(Exception("Missing accessToken in guest response"))

                // Guest sessions are not persisted — in-memory only for the current session.
                _accessToken = accessToken
                authenticateWithToken(accessToken).map { accessToken }
            } else {
                val code = parseServerError(response.bodyAsText())
                val msg =
                    if (code != null) localizedApiError(code) else "Guest login failed: HTTP ${response.status.value}"
                _authState.value = AuthState.Error(message = msg)
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("loginAsGuest error: ${e.message}")
            _authState.value = AuthState.Error(message = e.message ?: "Guest login failed")
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            httpClient.post("$devServerUrl/auth/forgot-password") {
                contentType(Application.Json)
                setBody("""{"email":"${email.trim()}"}""")
            }
            // Siempre éxito desde el punto de vista del cliente — el servidor nunca revela si el email existe
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("forgotPassword error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            val response = httpClient.post("$devServerUrl/auth/reset-password") {
                contentType(Application.Json)
                setBody("""{"token":"$token","newPassword":"$newPassword"}""")
            }
            if (response.status.value == 200) {
                Result.success(Unit)
            } else {
                val code = parseServerError(response.bodyAsText())
                val msg = if (code != null) localizedApiError(code) else "Reset failed: HTTP ${response.status.value}"
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("resetPassword error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchProfile(): Result<Unit> {
        val token = _accessToken ?: return Result.success(Unit)
        return try {
            val response = httpClient.get("$devServerUrl/api/profile") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.value == 200) {
                val dto = response.body<OwnProfileDto>()
                _profileData.value = ProfileData(
                    bio = dto.bio,
                    isVisible = dto.isVisible,
                    challengesEnabled = dto.acceptsChallenges,
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("fetchProfile HTTP ${response.status.value}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("fetchProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(bio: String?, isVisible: Boolean?, challengesEnabled: Boolean?): Result<Unit> {
        val token = _accessToken ?: return Result.failure(Exception("Not authenticated"))
        val bodyParts = mutableListOf<String>()
        if (bio != null) {
            val escaped = bio.trim().jsonEscape()
            bodyParts.add(""""bio":"$escaped"""")
        }
        if (isVisible != null) bodyParts.add(""""isVisible":$isVisible""")
        if (challengesEnabled != null) bodyParts.add(""""challengesEnabled":$challengesEnabled""")
        if (bodyParts.isEmpty()) return Result.success(Unit)

        return try {
            val response = httpClient.put("$devServerUrl/api/profile") {
                header("Authorization", "Bearer $token")
                contentType(Application.Json)
                setBody("{${bodyParts.joinToString(",")}}")
            }
            if (response.status.value == 200) {
                val dto = response.body<OwnProfileDto>()
                _profileData.value = ProfileData(
                    bio = dto.bio,
                    isVisible = dto.isVisible,
                    challengesEnabled = dto.acceptsChallenges,
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("updateProfile HTTP ${response.status.value}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("updateProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val token = _accessToken ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = httpClient.delete("$devServerUrl/api/profile") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.value == 200) {
                authRepository.clearAll()
                _inMemoryRefreshToken = null
                _accessToken = null
                _profileData.value = null
                _authState.value = AuthState.Unauthenticated
                Result.success(Unit)
            } else {
                Result.failure(Exception("deleteAccount HTTP ${response.status.value}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
                    } catch (e: Exception) {
                        // Captura defensiva: cualquier excepción inesperada en el
                        // pipeline de Ktor/serialization que escape de refreshToken().
                        logger.error("Silent refresh threw unexpectedly: ${e::class.simpleName} — ${e.message}")
                        authRepository.clearAll()
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to restore session: ${e.message}")
            authRepository.clearAll()
        }
    }

    private fun String.jsonEscape(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun parseTokenExpiry(token: String): Long? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = base64UrlDecode(parts[1])
        Regex(""""exp"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)
            ?.toLong()?.let { it * 1000L }
    }.getOrNull()

    private fun parseUserInfoFromToken(token: String): UserInfo {
        val parts = token.split(".")
        require(parts.size >= 2) { "Invalid JWT format" }
        val payload = base64UrlDecode(parts[1])

        val userId = Regex(""""sub"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.get(1) ?: ""
        val username = Regex(""""username"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.get(1) ?: userId
        val displayName = Regex(""""displayName"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.get(1)
        val isGuest = Regex(""""isGuest"\s*:\s*true""").containsMatchIn(payload)

        return UserInfo(
            userId = userId,
            username = username,
            displayName = displayName ?: username,
            isGuest = isGuest,
        )
    }

    /** Decodes a URL-safe Base64 string to UTF-8 text. Pure Kotlin, no platform dependencies. */
    private fun base64UrlDecode(input: String): String {
        val padded = buildString {
            append(input.replace('-', '+').replace('_', '/'))
            repeat((4 - input.length % 4) % 4) { append('=') }
        }
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i + 3 < padded.length) {
            val n = (table.indexOf(padded[i]) shl 18) or
                    (table.indexOf(padded[i + 1]) shl 12) or
                    (if (padded[i + 2] == '=') 0 else table.indexOf(padded[i + 2]) shl 6) or
                    (if (padded[i + 3] == '=') 0 else table.indexOf(padded[i + 3]))
            bytes.add((n shr 16).toByte())
            if (padded[i + 2] != '=') bytes.add((n shr 8 and 0xFF).toByte())
            if (padded[i + 3] != '=') bytes.add((n and 0xFF).toByte())
            i += 4
        }
        return bytes.toByteArray().decodeToString()
    }

    // Prefiere el campo `code` (máquina-legible) sobre `message` o `error` (texto inglés).
    private fun parseServerError(body: String): String? =
        Regex(""""code"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?: Regex(""""message"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?: Regex(""""error"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
}