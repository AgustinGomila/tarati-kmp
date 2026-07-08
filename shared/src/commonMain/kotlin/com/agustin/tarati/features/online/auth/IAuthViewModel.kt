package com.agustin.tarati.features.online.auth


import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato público del ViewModel de autenticación.
 *
 * Gestiona el ciclo de vida de tokens JWT: login, restauración de sesión,
 * renovación transparente y logout.
 *
 * ### Casos de uso principales:
 *
 * #### 1. Restaurar sesión guardada al iniciar:
 * ```kotlin
 * val token = authViewModel.getStoredToken()
 * if (token != null) {
 *     authViewModel.authenticateWithToken(token)
 * }
 * ```
 *
 * #### 2. Login contra el servidor:
 * ```kotlin
 * authViewModel.loginWithServer("username", "password")
 * // → authState = Authenticated, tokens guardados
 * ```
 *
 * #### 3. Renovación proactiva antes de expirar:
 * ```kotlin
 * // En un LaunchedEffect de ciclo de vida, antes de iniciar matchmaking:
 * if (authViewModel.isTokenExpiringSoon()) {
 *     authViewModel.refreshToken()
 * }
 * ```
 *
 * #### 4. Logout:
 * ```kotlin
 * authViewModel.logout()
 * // → authState = Unauthenticated, tokens borrados
 * ```
 */
interface IAuthViewModel {

    /**
     * Estado actual de autenticación.
     *
     * - [AuthState.Unauthenticated] - Sin credenciales
     * - [AuthState.Authenticating] - Validando token
     * - [AuthState.Authenticated]  - Token válido y activo
     * - [AuthState.Error]          - Error de autenticación
     */
    val authState: StateFlow<AuthState>

    /** True si hay un usuario autenticado actualmente. */
    val isAuthenticated: Boolean
        get() = authState.value is AuthState.Authenticated

    /** Token de acceso actual (null si no autenticado). */
    val accessToken: String?

    /** Información del usuario autenticado (null si no autenticado). */
    val currentUser: UserInfo?
        get() = (authState.value as? AuthState.Authenticated)?.userInfo

    /**
     * Autenticar con un token JWT existente.
     *
     * Usado para restaurar sesión guardada al iniciar la app.
     *
     * @param token Token JWT válido
     * @return Result.success con UserInfo si el token es válido
     */
    suspend fun authenticateWithToken(token: String): Result<UserInfo>

    /**
     * Guardar token en almacenamiento persistente.
     *
     * @param token Token JWT a guardar
     */
    fun saveToken(token: String)

    /**
     * Obtener access token guardado del almacenamiento persistente.
     *
     * @return Token JWT guardado, o null si no hay ninguno
     */
    fun getStoredToken(): String?

    /**
     * Cerrar sesión.
     *
     * Limpia todos los tokens guardados y vuelve al estado Unauthenticated.
     */
    suspend fun logout()

    /**
     * Login contra el servidor con usuario y contraseña.
     *
     * Hace POST /auth/login, guarda el accessToken y el refreshToken,
     * y actualiza authState a Authenticated.
     *
     * @return Result.success con el accessToken, o failure con el error
     */
    /**
     * @param rememberMe Si true, persiste los tokens entre sesiones.
     *   Si false, los tokens viven solo en memoria — al reiniciar la app se requiere login.
     */
    suspend fun loginWithServer(
        username: String,
        password: String,
        rememberMe: Boolean = true,
    ): Result<String>

    /**
     * Renueva el access token usando el refresh token guardado.
     *
     * Hace POST /auth/refresh. En caso de éxito, actualiza accessToken
     * y refreshToken en el repositorio y en authState sin interrumpir
     * la sesión activa. Si el refresh token ya expiró, vuelve a
     * Unauthenticated para forzar un nuevo login.
     *
     * @return Result.success con el nuevo accessToken, o failure si el
     *         refresh token es inválido o expiró.
     */
    suspend fun refreshToken(): Result<String>

    /**
     * True si el access token actual expira en menos de [thresholdMs] milisegundos.
     *
     * Usar antes de operaciones críticas (iniciar matchmaking, conectar WS) para
     * renovar proactivamente sin interrumpir la experiencia del usuario.
     *
     * @param thresholdMs Margen de tiempo en ms (default: 2 minutos)
     */
    fun isTokenExpiringSoon(thresholdMs: Long = 2 * 60 * 1000L): Boolean

    fun clearError()

    /**
     * Inicia sesión como invitado sin necesidad de registro.
     *
     * Llama POST /auth/guest y autentica con el token recibido.
     * El token dura 4 horas y no se renueva automáticamente.
     * Los guests no pueden crear torneos ni jugar partidas rated.
     *
     * @param desiredUsername Nombre de usuario preferido. Si es null o blank, el servidor
     *                        genera uno aleatorio del tipo `guest_XXXXXX`.
     * @return Result.success con el accessToken, o failure con el error.
     */
    suspend fun loginAsGuest(desiredUsername: String? = null): Result<String>

    /**
     * Registra un nuevo usuario y autentica directamente.
     *
     * Hace POST /auth/register, guarda accessToken + refreshToken y
     * actualiza authState a Authenticated.
     *
     * @return Result.success con el accessToken, o failure con el error del servidor.
     */
    suspend fun registerWithServer(
        username: String,
        email: String,
        password: String,
        displayName: String? = null,
        rememberMe: Boolean = true,
    ): Result<String>

    /**
     * Solicita un link de recuperación de contraseña.
     *
     * Hace POST /auth/forgot-password. El servidor siempre responde 200
     * para no revelar si el email existe. El link incluye un token UUID
     * con TTL de 1 hora guardado en Redis.
     *
     * @return Result.success(Unit) si el request llegó al servidor.
     *         Result.failure si hubo un error de red.
     */
    suspend fun forgotPassword(email: String): Result<Unit>

    /**
     * Restablece la contraseña usando el token del email de recuperación.
     *
     * Hace POST /auth/reset-password. El token es de un solo uso.
     * En caso de éxito, revoca todas las sesiones activas del usuario.
     *
     * @return Result.success(Unit) si la contraseña se actualizó correctamente.
     *         Result.failure si el token es inválido o expiró.
     */
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit>

    /**
     * Datos de perfil editable del usuario autenticado (bio, isVisible).
     * Null hasta que se llama [fetchProfile].
     */
    val profileData: StateFlow<ProfileData?>

    /**
     * Obtiene bio e isVisible del servidor y actualiza [profileData].
     * No-op si no hay sesión activa.
     */
    suspend fun fetchProfile(): Result<Unit>

    /**
     * Actualiza bio y/o visibilidad del usuario autenticado.
     *
     * Solo los parámetros no-null se envían al servidor.
     * En caso de éxito actualiza [profileData] con los valores devueltos por el servidor.
     *
     * @param bio Nuevo texto de descripción. Null = no cambiar. String vacío = limpiar bio.
     * @param isVisible Nuevo estado de visibilidad. Null = no cambiar.
     */
    suspend fun updateProfile(
        bio: String? = null,
        isVisible: Boolean? = null,
        challengesEnabled: Boolean? = null,
    ): Result<Unit>

    /**
     * Elimina la cuenta del usuario autenticado.
     *
     * Llama DELETE /api/profile. En caso de éxito limpia tokens locales
     * y devuelve el estado a [AuthState.Unauthenticated].
     *
     * @return Result.success(Unit) si la cuenta fue eliminada.
     *         Result.failure si hubo un error de red o el servidor rechazó la operación.
     */
    suspend fun deleteAccount(): Result<Unit>
}

/**
 * Devuelve el access token actual, renovándolo antes si está próximo a expirar
 * ([isTokenExpiringSoon]). Punto único de obtención de token para los ViewModels que hacen
 * peticiones REST autenticadas. `null` si no hay sesión.
 */
suspend fun IAuthViewModel.validToken(): String? {
    if (isTokenExpiringSoon()) refreshToken()
    return accessToken
}