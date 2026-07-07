package com.agustin.tarati.network.models

import kotlinx.serialization.Serializable

/**
 * DTOs de autenticación compartidos con los endpoints `/auth/` del servidor.
 *
 * Reemplazan la construcción manual de JSON (interpolación de strings) y el parsing
 * por `Regex` que vivían en `AuthViewModel`: los requests se serializan con
 * kotlinx.serialization (sin riesgo de inyección) y las respuestas se deserializan
 * con `body<T>()`. El cliente usa `ignoreUnknownKeys = true`, por lo que los campos
 * del servidor que no interesan (p. ej. `user` en la respuesta de login) se ignoran.
 */

// ── Requests ─────────────────────────────────────────────────────────────────

@Serializable
data class LoginRequest(
    val usernameOrEmail: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class GuestRequest(
    val desiredUsername: String? = null,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

// ── Responses ────────────────────────────────────────────────────────────────

/** Par de tokens JWT. `refreshToken` es opcional: el login de invitado no lo entrega. */
@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String? = null,
)

/** Respuesta de login/registro: el servidor anida los tokens junto al perfil (que el cliente ignora). */
@Serializable
data class AuthResponseDto(
    val tokens: TokenPairDto,
)

/**
 * Cuerpo de error del servidor. `AuthError` usa `code` + `message`; `ErrorResponse`
 * usa `error`. Se prefiere `code` (máquina-legible) para [localizedApiError].
 */
@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    /** Primer campo presente, priorizando el código legible por máquina. */
    fun firstNonNull(): String? = code ?: message ?: error
}
