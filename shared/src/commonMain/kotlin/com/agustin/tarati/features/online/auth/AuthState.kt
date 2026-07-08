package com.agustin.tarati.features.online.auth

import kotlinx.serialization.Serializable

/**
 * Estado de autenticación del usuario
 * 
 * Representa si el usuario tiene credenciales válidas para
 * conectarse al servidor online.
 */
@Serializable
sealed class AuthState {
    /**
     * No autenticado - sin token guardado
     */
    @Serializable
    data object Unauthenticated : AuthState()

    /**
     * Autenticando - validando token con servidor
     */
    @Serializable
    data object Authenticating : AuthState()

    /**
     * Autenticado - token válido y activo
     * 
     * @property userInfo Información del usuario autenticado
     * @property tokenExpiry Timestamp de expiración del token (epoch millis)
     */
    @Serializable
    data class Authenticated(
        val userInfo: UserInfo,
        val tokenExpiry: Long
    ) : AuthState()

    /**
     * Error de autenticación
     * 
     * @property message Mensaje de error
     * @property canRetry Si el usuario puede reintentar
     */
    @Serializable
    data class Error(
        val message: String,
        private val canRetry: Boolean = true
    ) : AuthState()
}

/**
 * Información del usuario autenticado
 * 
 * @property userId ID único del usuario en el servidor
 * @property username Nombre de usuario único
 * @property email Email del usuario
 * @property displayName Nombre visible (puede ser diferente del username)
 * @property rating Rating actual del usuario
 * @property isGuest Si es un usuario guest (cuenta temporal)
 */
@Serializable
data class UserInfo(
    val userId: String,
    val username: String,
    val email: String? = null,
    val displayName: String = username,
    val rating: Int = 1500,
    val isGuest: Boolean = false
)

/** Datos de perfil editable del usuario autenticado: bio, isVisible, challengesEnabled. */
data class ProfileData(
    val bio: String?,
    val isVisible: Boolean,
    val challengesEnabled: Boolean = true,
)