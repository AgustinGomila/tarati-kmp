package com.agustin.tarati.features.online.lobby


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.models.Game
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.network.models.PagedResponse
import com.agustin.tarati.network.models.PublicProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Repositorio para los endpoints del lobby online.
 *
 * Consume [GET /api/live-games] y [GET /api/games] usando el [HttpClient]
 * de plataforma ya configurado en Koin. El token JWT se pasa en cada
 * petición para cumplir el middleware de autenticación del servidor.
 *
 * La URL base usa [devServerUrl] igual que el resto del cliente online.
 */
class OnlineLobbyRepository(
    private val httpClient: HttpClient,
) {
    private val baseUrl = devServerUrl

    /**
     * Obtiene el snapshot de partidas actualmente en curso.
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getLiveGames(token: String): Result<List<LiveGameDto>> = runCatching {
        httpClient.get("$baseUrl/api/live-games") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene el historial paginado de partidas del usuario autenticado.
     *
     * @param token        JWT del usuario.
     * @param page         Página 0-based.
     * @param limit        Tamaño de página (1–100).
     * @param timeControl  Filtro por time control key, o null para todos.
     * @param result       Filtro "win" | "loss" | "draw", o null para todos.
     * @param rated        Filtro rated/casual, o null para ambos.
     */
    suspend fun getGameHistory(
        token: String,
        page: Int = 0,
        limit: Int = 20,
        timeControl: String? = null,
        result: String? = null,
        rated: Boolean? = null,
    ): Result<PagedResponse<GameHistoryDto>> = runCatching {
        httpClient.get("$baseUrl/api/games") {
            bearerAuth(token)
            parameter("page", page)
            parameter("limit", limit)
            if (timeControl != null) parameter("timeControl", timeControl)
            if (result != null) parameter("result", result)
            if (rated != null) parameter("rated", rated)
        }.body()
    }

    /**
     * Obtiene el feed social: partidas recientes de jugadores seguidos por el usuario autenticado.
     * Resultados expresados desde la perspectiva del jugador seguido.
     *
     * @param token  JWT del usuario autenticado.
     * @param page   Página 0-based.
     * @param limit  Tamaño de página (1-50).
     */
    suspend fun getFeed(
        token: String,
        page: Int = 0,
        limit: Int = 20,
    ): Result<PagedResponse<GameHistoryDto>> = runCatching {
        httpClient.get("$baseUrl/api/feed") {
            bearerAuth(token)
            parameter("page", page)
            parameter("limit", limit)
        }.body()
    }

    /**
     * Obtiene una partida finalizada por ID.
     * Usado para previsualizar una partida antes de navegar a [GameDetailsScreen].
     *
     * @param token  JWT del usuario autenticado.
     * @param gameId ID de la partida.
     */
    suspend fun getGame(token: String, gameId: String): Result<Game> = runCatching {
        httpClient.get("$baseUrl/api/games/$gameId") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene las búsquedas abiertas actualmente en colas de matchmaking.
     * El endpoint excluye la búsqueda del propio usuario autenticado.
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getOpenSearches(token: String): Result<List<OpenSearchDto>> = runCatching {
        httpClient.get("$baseUrl/api/lobby/open-searches") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene la lista de usuarios actualmente conectados al servidor.
     * Excluye bots y usuarios con visibilidad oculta.
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getOnlineUsers(token: String): Result<List<OnlineUserDto>> = runCatching {
        httpClient.get("$baseUrl/api/lobby/online-users") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene el perfil público de un usuario, incluyendo sus estadísticas
     * sumarizadas por control de tiempo ([PublicProfileDto.stats]).
     *
     * @param token  JWT del usuario autenticado.
     * @param userId ID del usuario cuyo perfil se solicita.
     */
    suspend fun getProfile(token: String, userId: String): Result<PublicProfileDto> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene las mesas públicas abiertas del lobby multijugador (tablero `25`).
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getMpTables(token: String): Result<List<MpTableDto>> = runCatching {
        httpClient.get("$baseUrl/api/mp/tables") {
            bearerAuth(token)
        }.body()
    }

    /**
     * Obtiene las partidas multijugador **en curso** (para observar como espectador — M7.5).
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getMpLiveGames(token: String): Result<List<MpLiveGameDto>> = runCatching {
        httpClient.get("$baseUrl/api/mp/games") {
            bearerAuth(token)
        }.body()
    }
}