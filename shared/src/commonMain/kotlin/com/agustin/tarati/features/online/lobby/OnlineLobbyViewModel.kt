package com.agustin.tarati.features.online.lobby


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import com.agustin.tarati.features.online.lobby.OnlineLobbyViewModel.Companion.LIVE_POLL_INTERVAL
import com.agustin.tarati.features.online.social.SocialRepository
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.network.models.ProfileStatsDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ── State models ───────────────────────────────────────────────────────────────

data class LiveGamesUiState(
    val games: List<LiveGameDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class OpenSearchesUiState(
    val searches: List<OpenSearchDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Criterio de ordenamiento de ítems del lobby. */
enum class LobbySort {
    /** Más recientes al inicio (búsquedas recién creadas, partidas recién empezadas). */
    NEWEST,

    /** Más antiguos al inicio (búsquedas con más espera arriba). */
    OLDEST,

    /** Mayor rating primero. */
    RATING_DESC,
}

/**
 * Filtros y ordenamiento del tab "En Vivo" del lobby.
 * Controla qué tipos de ítems se muestran y cómo se ordenan.
 */
data class LobbyFilters(
    val showLiveGames: Boolean = true,
    val showOpenSearches: Boolean = true,
    val sort: LobbySort = LobbySort.NEWEST,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel para [OnlineLobbyScreen].
 *
 * ## Tab "En Vivo"
 * Polling unificado de [GET /api/live-games] y [GET /api/lobby/open-searches]
 * cada [LIVE_POLL_INTERVAL]. Ambas listas se refrescan en el mismo ciclo para
 * que la UI siempre esté sincronizada.
 *
 * Los filtros [LobbyFilters] controlan qué tipos de ítems muestra la pantalla
 * y con qué criterio de ordenamiento. El ViewModel expone los datos crudos —
 * la pantalla aplica los filtros sobre ellos.
 *
 * ## Tab "Mis Partidas" / feed
 * Carga paginada delegada en [PagedGameHistoryLoader] (compartido con
 * [PublicProfileViewModel]); el historial propio filtra por time control,
 * resultado y tipo de partida.
 */
class OnlineLobbyViewModel(
    private val repository: OnlineLobbyRepository,
    private val socialRepository: SocialRepository,
    private val authViewModel: IAuthViewModel,
) : ViewModel(), IOnlineLobbyViewModel {

    private val logger = getLogger("OnlineLobbyViewModel")

    private val _onlineUsers = MutableStateFlow<List<OnlineUserDto>>(emptyList())
    override val onlineUsers: StateFlow<List<OnlineUserDto>> = _onlineUsers.asStateFlow()

    private val _liveGames = MutableStateFlow(LiveGamesUiState())
    override val liveGames: StateFlow<LiveGamesUiState> = _liveGames.asStateFlow()

    private val _openSearches = MutableStateFlow(OpenSearchesUiState())
    override val openSearches: StateFlow<OpenSearchesUiState> = _openSearches.asStateFlow()

    private val _myStats = MutableStateFlow<ProfileStatsDto?>(null)
    override val myStats: StateFlow<ProfileStatsDto?> = _myStats.asStateFlow()

    private val _lobbyFilters = MutableStateFlow(LobbyFilters())
    override val lobbyFilters: StateFlow<LobbyFilters> = _lobbyFilters.asStateFlow()

    /** Historial propio ([GET /api/games]) con filtros aplicados en el servidor. */
    private val historyLoader = PagedGameHistoryLoader(viewModelScope, authViewModel) { token, page, limit, filters ->
        repository.getGameHistory(
            token = token,
            page = page,
            limit = limit,
            timeControl = filters.timeControl,
            result = filters.result,
            rated = filters.rated,
        )
    }
    override val history: StateFlow<GameHistoryUiState> = historyLoader.state

    /** Feed social ([GET /api/feed]) — sin filtros de servidor; la pantalla filtra en cliente. */
    private val feedLoader = PagedGameHistoryLoader(viewModelScope, authViewModel) { token, page, limit, _ ->
        repository.getFeed(token = token, page = page, limit = limit)
    }
    override val feedState: StateFlow<GameHistoryUiState> = feedLoader.state

    private var pollingJob: Job? = null
    private var connectedPollingJob: Job? = null

    companion object {
        /** Intervalo de refresco del lobby (partidas en vivo + búsquedas). */
        val LIVE_POLL_INTERVAL: Duration = 5.seconds

        /** Usuarios en línea se refrescan cada 2 ciclos (~10 s) dentro del polling de En Vivo. */
        private const val ONLINE_USERS_EVERY_N_CYCLES = 2
    }

    private var pollCycle = 0

    // ── Polling ────────────────────────────────────────────────────────────────

    override fun startLivePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchLiveGames()
                fetchOpenSearches()
                // Incrementar después del chequeo: el ciclo 0 también fetcha online users.
                if (pollCycle % ONLINE_USERS_EVERY_N_CYCLES == 0) {
                    fetchOnlineUsers()
                }
                pollCycle++
                delay(LIVE_POLL_INTERVAL)
            }
        }
    }

    override fun stopLivePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun startConnectedPolling() {
        if (connectedPollingJob?.isActive == true) return
        connectedPollingJob = viewModelScope.launch {
            while (isActive) {
                fetchOnlineUsers()
                delay(LIVE_POLL_INTERVAL)
            }
        }
    }

    override fun stopConnectedPolling() {
        connectedPollingJob?.cancel()
        connectedPollingJob = null
    }

    override fun refreshOpenSearches() {
        viewModelScope.launch { fetchOpenSearches() }
    }

    private suspend fun fetchOnlineUsers() {
        val token = authViewModel.validToken() ?: return
        repository.getOnlineUsers(token).onSuccess { users ->
            _onlineUsers.value = users
        }
    }

    private suspend fun fetchLiveGames() {
        val token = authViewModel.validToken() ?: run {
            _liveGames.update { it.copy(isLoading = false) }
            return
        }
        _liveGames.update { it.copy(isLoading = it.games.isEmpty(), error = null) }

        repository.getLiveGames(token)
            .onSuccess { games ->
                logger.debug("fetchLiveGames: received ${games.size} live games")
                _liveGames.update { it.copy(games = games, isLoading = false) }
            }
            .onFailure { e ->
                logger.error("fetchLiveGames error: ${e::class.simpleName} — ${e.message}")
                _liveGames.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    private suspend fun fetchOpenSearches() {
        val token = authViewModel.validToken() ?: return
        _openSearches.update { it.copy(isLoading = it.searches.isEmpty(), error = null) }

        repository.getOpenSearches(token)
            .onSuccess { searches ->
                logger.debug("fetchOpenSearches: received ${searches.size} open searches")
                _openSearches.update { it.copy(searches = searches, isLoading = false) }
            }
            .onFailure { e ->
                logger.error("fetchOpenSearches error: ${e::class.simpleName} — ${e.message}")
                _openSearches.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    // ── Lobby filters ──────────────────────────────────────────────────────────

    override fun setShowLiveGames(show: Boolean) {
        _lobbyFilters.update { it.copy(showLiveGames = show) }
    }

    override fun setShowOpenSearches(show: Boolean) {
        _lobbyFilters.update { it.copy(showOpenSearches = show) }
    }

    override fun setLobbySort(sort: LobbySort) {
        _lobbyFilters.update { it.copy(sort = sort) }
    }

    // ── Game history ───────────────────────────────────────────────────────────

    override fun loadHistory() {
        loadMyStats()
        historyLoader.load()
    }

    override fun loadMoreHistory(): Unit = historyLoader.loadMore()

    /**
     * Carga las estadísticas sumarizadas del propio usuario una sola vez por sesión
     * del ViewModel. Son agregadas (no dependen de los filtros): la UI selecciona el
     * control de tiempo del lado del cliente. No-op sin sesión.
     */
    private fun loadMyStats() {
        if (_myStats.value != null) return
        val userId = authViewModel.currentUser?.userId ?: return
        viewModelScope.launch {
            val token = authViewModel.validToken() ?: return@launch
            socialRepository.getUserProfile(token, userId)
                .onSuccess { profile -> _myStats.value = profile.stats }
                .onFailure { e -> logger.debug("loadMyStats failed: ${e.message}") }
        }
    }

    // ── History filters ────────────────────────────────────────────────────────

    override fun setTimeControlFilter(tc: String?): Unit = historyLoader.setTimeControlFilter(tc)

    override fun setResultFilter(result: String?): Unit = historyLoader.setResultFilter(result)

    override fun setRatedFilter(rated: Boolean?): Unit = historyLoader.setRatedFilter(rated)

    override fun clearFilters(): Unit = historyLoader.clearFilters()

    // ── Game preview ──────────────────────────────────────────────────────────

    override suspend fun loadAndPreviewGame(gameId: String): MatchDto? {
        val token = authViewModel.validToken() ?: return null
        return repository.getGame(token = token, gameId = gameId)
            .getOrNull()
            ?.toMatchDto()
    }

    // ── Social feed ────────────────────────────────────────────────────────────

    override fun loadFeed(): Unit = feedLoader.load()

    override fun loadMoreFeed(): Unit = feedLoader.loadMore()
}
