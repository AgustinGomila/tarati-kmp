package com.agustin.tarati.features.online.auth


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.models.ForgotPasswordRequest
import com.agustin.tarati.network.models.GuestRequest
import com.agustin.tarati.network.models.LoginRequest
import com.agustin.tarati.network.models.LogoutRequest
import com.agustin.tarati.network.models.RefreshRequest
import com.agustin.tarati.network.models.RegisterRequest
import com.agustin.tarati.network.models.ResetPasswordRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

/**
 * Cliente HTTP de los endpoints de autenticación (`/auth/…`) y perfil propio
 * (`/api/profile`).
 *
 * Solo encapsula URL + serialización del request; devuelve la [HttpResponse]
 * cruda porque [AuthViewModel] necesita ramificar por status (200/401/…) y
 * derivar mensajes de error localizados del cuerpo.
 */
class AuthApi(private val httpClient: HttpClient) {

    private val baseUrl = devServerUrl

    // ── Autenticación ─────────────────────────────────────────────────────────

    suspend fun login(request: LoginRequest): HttpResponse =
        postJson("auth/login", request)

    suspend fun register(request: RegisterRequest): HttpResponse =
        postJson("auth/register", request)

    suspend fun guest(request: GuestRequest): HttpResponse =
        postJson("auth/guest", request)

    suspend fun refresh(request: RefreshRequest): HttpResponse =
        postJson("auth/refresh", request)

    suspend fun logout(request: LogoutRequest): HttpResponse =
        postJson("auth/logout", request)

    suspend fun forgotPassword(request: ForgotPasswordRequest): HttpResponse =
        postJson("auth/forgot-password", request)

    suspend fun resetPassword(request: ResetPasswordRequest): HttpResponse =
        postJson("auth/reset-password", request)

    // ── Perfil propio ─────────────────────────────────────────────────────────

    /** GET /api/profile — perfil editable del usuario autenticado. */
    suspend fun fetchProfile(token: String): HttpResponse =
        httpClient.get("$baseUrl/api/profile") {
            bearerAuth(token)
        }

    /**
     * PUT /api/profile — actualización parcial: el servidor solo modifica los
     * campos presentes en [fields] (ausente = sin cambio).
     */
    suspend fun updateProfile(token: String, fields: JsonObject): HttpResponse =
        httpClient.put("$baseUrl/api/profile") {
            bearerAuth(token)
            contentType(Application.Json)
            setBody(fields)
        }

    /** DELETE /api/profile — eliminación permanente de la cuenta. */
    suspend fun deleteAccount(token: String): HttpResponse =
        httpClient.delete("$baseUrl/api/profile") {
            bearerAuth(token)
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend inline fun <reified B> postJson(path: String, body: B): HttpResponse =
        httpClient.post("$baseUrl/$path") {
            contentType(Application.Json)
            setBody(body)
        }
}
