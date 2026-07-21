package com.agustin.tarati.features.online.tournament

import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authDelete
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.authPost
import com.agustin.tarati.network.models.CreateTournamentRequest
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType

/**
 * Repositorio para los endpoints de torneos (`/api/tournaments`), usando el
 * HttpClient de plataforma ya configurado en Koin. El token JWT se pasa en
 * cada petición.
 */
class TournamentRepository(private val httpClient: HttpClient) {
    private val baseUrl = devServerUrl

    suspend fun getTournaments(
        token: String,
        status: String? = null,
        type: String? = null,
        limit: Int? = null,
        offset: Int = 0,
    ): Result<List<TournamentSummaryDto>> = httpClient.authGet(
        "$baseUrl/api/tournaments", token,
        "status" to status,
        "type" to type,
        "limit" to limit,
        "offset" to offset.takeIf { it > 0 },
    )

    suspend fun getTournament(token: String, id: String): Result<TournamentDetailDto> =
        httpClient.authGet("$baseUrl/api/tournaments/$id", token)

    suspend fun createTournament(
        token: String,
        request: CreateTournamentRequest,
    ): Result<TournamentSummaryDto> = httpClient.authPost("$baseUrl/api/tournaments", token) {
        contentType(Application.Json)
        setBody(request)
    }

    suspend fun register(token: String, id: String): Result<Unit> =
        httpClient.authPost("$baseUrl/api/tournaments/$id/register", token)

    suspend fun unregister(token: String, id: String): Result<Unit> =
        httpClient.authDelete("$baseUrl/api/tournaments/$id/register", token)

    suspend fun start(token: String, id: String): Result<Unit> =
        httpClient.authPost("$baseUrl/api/tournaments/$id/start", token)

    suspend fun cancel(token: String, id: String): Result<Unit> =
        httpClient.authPost("$baseUrl/api/tournaments/$id/cancel", token)
}
