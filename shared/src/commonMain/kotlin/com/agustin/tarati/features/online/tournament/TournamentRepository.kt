package com.agustin.tarati.features.online.tournament

import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.models.CreateTournamentRequest
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Repositorio para los endpoints de torneos.
 *
 * Consume /api/tournaments/* usando el HttpClient de plataforma ya configurado en Koin.
 * El token JWT se pasa en cada petición.
*/*/
class TournamentRepository(private val httpClient: HttpClient) {
    private val baseUrl = devServerUrl
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun getTournaments(
        token: String,
        status: String? = null,
        type: String? = null,
        limit: Int? = null,
        offset: Int = 0,
    ): Result<List<TournamentSummaryDto>> = runCatching {
        httpClient.get("$baseUrl/api/tournaments") {
            bearerAuth(token)
            if (status != null) parameter("status", status)
            if (type != null) parameter("type", type)
            if (limit != null) parameter("limit", limit)
            if (offset > 0) parameter("offset", offset)
        }.body()
    }

    suspend fun getTournament(token: String, id: String): Result<TournamentDetailDto> = runCatching {
        httpClient.get("$baseUrl/api/tournaments/$id") {
            bearerAuth(token)
        }.body()
    }

    suspend fun createTournament(
        token: String,
        request: CreateTournamentRequest,
    ): Result<TournamentSummaryDto> = runCatching {
        httpClient.post("$baseUrl/api/tournaments") {
            bearerAuth(token)
            contentType(Application.Json)
            setBody(json.encodeToString(request))
        }.body()
    }

    suspend fun register(token: String, id: String): Result<Unit> = runCatching {
        httpClient.post("$baseUrl/api/tournaments/$id/register") {
            bearerAuth(token)
        }
    }

    suspend fun unregister(token: String, id: String): Result<Unit> = runCatching {
        httpClient.delete("$baseUrl/api/tournaments/$id/register") {
            bearerAuth(token)
        }
    }

    suspend fun start(token: String, id: String): Result<Unit> = runCatching {
        httpClient.post("$baseUrl/api/tournaments/$id/start") {
            bearerAuth(token)
        }
    }

    suspend fun cancel(token: String, id: String): Result<Unit> = runCatching {
        httpClient.post("$baseUrl/api/tournaments/$id/cancel") {
            bearerAuth(token)
        }
    }
}
