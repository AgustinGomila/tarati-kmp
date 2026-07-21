package com.agustin.tarati.features.online.lobby


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.models.Game
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.network.models.PagedResponse
import io.ktor.client.HttpClient

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
    suspend fun getLiveGames(token: String): Result<List<LiveGameDto>> =
        httpClient.authGet("$baseUrl/api/live-games", token)

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
    ): Result<PagedResponse<GameHistoryDto>> = httpClient.authGet(
        "$baseUrl/api/games", token,
        "page" to page,
        "limit" to limit,
        "timeControl" to timeControl,
        "result" to result,
        "rated" to rated,
    )

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
    ): Result<PagedResponse<GameHistoryDto>> = httpClient.authGet(
        "$baseUrl/api/feed", token,
        "page" to page,
        "limit" to limit,
    )

    /**
     * Obtiene una partida finalizada por ID.
     * Usado para previsualizar una partida antes de navegar a [GameDetailsScreen].
     *
     * @param token  JWT del usuario autenticado.
     * @param gameId ID de la partida.
     */
    suspend fun getGame(token: String, gameId: String): Result<Game> =
        httpClient.authGet("$baseUrl/api/games/$gameId", token)

    /**
     * Obtiene las búsquedas abiertas actualmente en colas de matchmaking.
     * El endpoint excluye la búsqueda del propio usuario autenticado.
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getOpenSearches(token: String): Result<List<OpenSearchDto>> =
        httpClient.authGet("$baseUrl/api/lobby/open-searches", token)

    /**
     * Obtiene la lista de usuarios actualmente conectados al servidor.
     * Excluye bots y usuarios con visibilidad oculta.
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getOnlineUsers(token: String): Result<List<OnlineUserDto>> =
        httpClient.authGet("$baseUrl/api/lobby/online-users", token)

    /**
     * Obtiene las mesas públicas abiertas del lobby multijugador (tablero `25`).
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getMpTables(token: String): Result<List<MpTableDto>> =
        httpClient.authGet("$baseUrl/api/mp/tables", token)

    /**
     * Obtiene las partidas multijugador **en curso** (para observar como espectador — M7.5).
     *
     * @param token JWT del usuario autenticado.
     */
    suspend fun getMpLiveGames(token: String): Result<List<MpLiveGameDto>> =
        httpClient.authGet("$baseUrl/api/mp/games", token)
}
