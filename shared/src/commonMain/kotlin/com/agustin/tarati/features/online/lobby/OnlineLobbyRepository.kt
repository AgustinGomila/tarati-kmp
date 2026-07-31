package com.agustin.tarati.features.online.lobby


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authDelete
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.authPost
import com.agustin.tarati.network.models.CreateMpTournamentRequest
import com.agustin.tarati.network.models.Game
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.MpFeedGameDto
import com.agustin.tarati.network.models.MpGameDetailDto
import com.agustin.tarati.network.models.MpGameHistoryDto
import com.agustin.tarati.network.models.MpLeaderboardEntryDto
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.MpTournamentDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.network.models.PagedResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

    /**
     * Obtiene el historial paginado de partidas multijugador del usuario autenticado.
     *
     * @param token JWT del usuario autenticado.
     * @param page  Página 0-based.
     * @param limit Tamaño de página.
     */
    suspend fun getMpHistory(
        token: String,
        page: Int = 0,
        limit: Int = 20,
    ): Result<PagedResponse<MpGameHistoryDto>> = httpClient.authGet(
        "$baseUrl/api/mp/games/history", token,
        "page" to page,
        "limit" to limit,
    )

    /**
     * Obtiene el feed social MP: partidas recientes de jugadores seguidos por el usuario autenticado.
     *
     * @param token JWT del usuario autenticado.
     * @param page  Página 0-based.
     * @param limit Tamaño de página.
     */
    suspend fun getMpFeed(
        token: String,
        page: Int = 0,
        limit: Int = 20,
    ): Result<PagedResponse<MpFeedGameDto>> = httpClient.authGet(
        "$baseUrl/api/mp/feed", token,
        "page" to page,
        "limit" to limit,
    )

    /**
     * Obtiene el detalle completo de una partida MP **terminada** (incluye el historial serializado
     * para reconstruir el replay jugada a jugada).
     *
     * @param token  JWT del usuario autenticado.
     * @param gameId identificador de la partida.
     */
    suspend fun getMpGameDetail(
        token: String,
        gameId: String,
    ): Result<MpGameDetailDto> = httpClient.authGet("$baseUrl/api/mp/games/$gameId", token)

    /**
     * Obtiene la tabla de clasificación multijugador (Tarati Six) — bucket único de rating MP.
     *
     * @param token JWT del usuario autenticado.
     * @param limit Máximo de entradas (acotado por el servidor).
     */
    suspend fun getMpLeaderboard(
        token: String,
        limit: Int = 100,
    ): Result<List<MpLeaderboardEntryDto>> = httpClient.authGet(
        "$baseUrl/api/mp/leaderboard", token,
        "limit" to limit,
    )

    // ── Torneos MP (Arena, fase 4b) ──────────────────────────────────────────────

    /** Torneos MP visibles (en registro / activos). */
    suspend fun getMpTournaments(token: String): Result<List<MpTournamentDto>> =
        httpClient.authGet("$baseUrl/api/mp/tournaments", token)

    /** Detalle + clasificación de un torneo MP. */
    suspend fun getMpTournament(token: String, id: String): Result<MpTournamentDto> =
        httpClient.authGet("$baseUrl/api/mp/tournaments/$id", token)

    /** Crea un torneo MP; el emisor queda como creador e inscrito. */
    suspend fun createMpTournament(token: String, request: CreateMpTournamentRequest): Result<MpTournamentDto> =
        httpClient.authPost("$baseUrl/api/mp/tournaments", token) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    /** Inscribe al emisor en un torneo MP. */
    suspend fun registerMpTournament(token: String, id: String): Result<MpTournamentDto> =
        httpClient.authPost("$baseUrl/api/mp/tournaments/$id/register", token)

    /** Da de baja al emisor de un torneo MP. */
    suspend fun unregisterMpTournament(token: String, id: String): Result<MpTournamentDto> =
        httpClient.authDelete("$baseUrl/api/mp/tournaments/$id/register", token)

    /** (Creador) Arranca la ventana Arena de un torneo MP. */
    suspend fun startMpTournament(token: String, id: String): Result<MpTournamentDto> =
        httpClient.authPost("$baseUrl/api/mp/tournaments/$id/start", token)

    /** (Creador) Cancela un torneo MP. */
    suspend fun cancelMpTournament(token: String, id: String): Result<MpTournamentDto> =
        httpClient.authPost("$baseUrl/api/mp/tournaments/$id/cancel", token)
}
