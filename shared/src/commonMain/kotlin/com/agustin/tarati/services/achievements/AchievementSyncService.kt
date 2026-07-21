package com.agustin.tarati.services.achievements

import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.authPost
import com.agustin.tarati.network.models.AchievementProgressRequest
import com.agustin.tarati.network.models.ServerAchievementDto
import com.agustin.tarati.network.models.UnlockAchievementRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Cliente HTTP para los endpoints de logros del servidor de Tarati.
 *
 * Todas las funciones retornan un tipo de resultado sin lanzar excepciones —
 * los fallos de red se capturan internamente y se reportan como [Boolean]
 * false o [Result.Failure]. El caller decide si encolar el intento fallido.
 */
class AchievementSyncService(private val httpClient: HttpClient) {

    private val baseUrl = devServerUrl

    /**
     * Desbloquea un logro one-shot en el servidor.
     * @return true si el servidor procesó la solicitud correctamente (HTTP 2xx).
     */
    suspend fun unlock(token: String, achievementId: AchievementId): Boolean =
        httpClient.authPost<Unit>("$baseUrl/api/achievements/unlock", token) {
            contentType(ContentType.Application.Json)
            setBody(UnlockAchievementRequest(achievementId.id))
        }.isSuccess

    /**
     * Actualiza los pasos de un logro incremental en el servidor.
     * El servidor solo avanza — nunca retrocede el contador.
     * @return true si el servidor procesó la solicitud correctamente (HTTP 2xx).
     */
    suspend fun progress(token: String, achievementId: AchievementId, steps: Int): Boolean =
        httpClient.authPost<Unit>("$baseUrl/api/achievements/progress", token) {
            contentType(ContentType.Application.Json)
            setBody(AchievementProgressRequest(achievementId.id, steps))
        }.isSuccess

    /**
     * Obtiene todos los logros del usuario autenticado desde el servidor.
     * Usado para restaurar los contadores in-memory al iniciar una sesión.
     */
    suspend fun getAll(token: String): Result<List<ServerAchievementDto>> =
        httpClient.authGet("$baseUrl/api/achievements", token)
}
