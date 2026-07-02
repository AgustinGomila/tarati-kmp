package com.agustin.tarati.features.online.lobby


import androidx.compose.runtime.Stable
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.ProfileStatsDto
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato público de [OnlineLobbyViewModel].
 *
 * Consumido por [OnlineLobbyScreen] y [NavGraph]. Permite
 * test doubles sin depender del ViewModel concreto.
 */
@Stable
interface IOnlineLobbyViewModel {

    // ── State ──────────────────────────────────────────────────────────────────

    /** Usuarios conectados al servidor ahora. Refresco cada ~10 s durante el polling. */
    val onlineUsers: StateFlow<List<OnlineUserDto>>

    /** Partidas actualmente en curso. Refresco cada [LIVE_POLL_INTERVAL]. */
    val liveGames: StateFlow<LiveGamesUiState>

    /** Búsquedas abiertas en colas de matchmaking. Refresco integrado al polling. */
    val openSearches: StateFlow<OpenSearchesUiState>

    /** Historial paginado del usuario autenticado. */
    val history: StateFlow<GameHistoryUiState>

    /**
     * Estadísticas sumarizadas del propio usuario por control de tiempo
     * (para el resumen W/D/L del tab "Mis Partidas"). Null hasta que se cargan.
     */
    val myStats: StateFlow<ProfileStatsDto?>

    /** Filtros y ordenamiento del tab de lobby (en vivo + búsquedas). */
    val lobbyFilters: StateFlow<LobbyFilters>

    // ── Polling (en vivo + búsquedas) ─────────────────────────────────────────

    /** Inicia el polling de partidas en vivo y búsquedas. Llamar desde el tab "En Vivo". */
    fun startLivePolling()

    fun stopLivePolling()

    /** Refresca la lista de búsquedas abiertas inmediatamente. */
    fun refreshOpenSearches()

    // ── Polling (usuarios conectados) ──────────────────────────────────────────

    /** Inicia el polling de usuarios conectados con fetch inmediato. Llamar desde el tab "Conectados". */
    fun startConnectedPolling()

    fun stopConnectedPolling()

    // ── Lobby filters ──────────────────────────────────────────────────────────

    fun setShowLiveGames(show: Boolean)

    fun setShowOpenSearches(show: Boolean)

    fun setLobbySort(sort: LobbySort)

    // ── Game history ───────────────────────────────────────────────────────────

    fun loadHistory()

    fun loadMoreHistory()

    // ── History filters ────────────────────────────────────────────────────────

    fun setTimeControlFilter(tc: String?)

    fun setResultFilter(result: String?)

    fun setRatedFilter(rated: Boolean?)

    fun clearFilters()

    // ── Social feed ────────────────────────────────────────────────────────────

    /** Partidas recientes de jugadores seguidos. */
    val feedState: StateFlow<GameHistoryUiState>

    /** Carga la primera página del feed. Llamar en [LaunchedEffect(Unit)] del tab "Seguidos". */
    fun loadFeed()

    fun loadMoreFeed()

    // ── Game preview ───────────────────────────────────────────────────────────

    /**
     * Obtiene una partida del servidor y la convierte a [MatchDto].
     * Llamar antes de navegar a GameDetailsScreen para pre-cargar el detalle.
     *
     * @return [MatchDto] si la carga y conversión fueron exitosas, null si hubo error.
     */
    suspend fun loadAndPreviewGame(gameId: String): MatchDto?
}