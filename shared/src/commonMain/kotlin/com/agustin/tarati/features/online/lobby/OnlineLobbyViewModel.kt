package com.agustin.tarati.features.online.lobby


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.lobby.OnlineLobbyViewModel.Companion.LIVE_POLL_INTERVAL
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.network.models.PagedResponse
import com.agustin.tarati.network.models.ProfileStatsDto
import kotlinx.coroutines.CancellationException
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

data class HistoryFilters(
    val timeControl: String? = null,   // null = todos
    val result: String? = null,        // "win" | "loss" | "draw" | null
    val rated: Boolean? = null,        // null = todos
)

data class GameHistoryUiState(
    val games: List<GameHistoryDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val filters: HistoryFilters = HistoryFilters(),
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val total: Long = 0,
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
 * ## Tab "Mis Partidas"
 * Carga paginada con filtros por time control, resultado y tipo de partida.
 */
class OnlineLobbyViewModel(
    private val repository: OnlineLobbyRepository,
    private val authViewModel: IAuthViewModel,
) : ViewModel(), IOnlineLobbyViewModel {

    private val logger = getLogger("OnlineLobbyViewModel")

    private val _onlineUsers = MutableStateFlow<List<OnlineUserDto>>(emptyList())
    override val onlineUsers: StateFlow<List<OnlineUserDto>> = _onlineUsers.asStateFlow()

    private val _liveGames = MutableStateFlow(LiveGamesUiState())
    override val liveGames: StateFlow<LiveGamesUiState> = _liveGames.asStateFlow()

    private val _openSearches = MutableStateFlow(OpenSearchesUiState())
    override val openSearches: StateFlow<OpenSearchesUiState> = _openSearches.asStateFlow()

    private val _history = MutableStateFlow(GameHistoryUiState())
    override val history: StateFlow<GameHistoryUiState> = _history.asStateFlow()

    private val _myStats = MutableStateFlow<ProfileStatsDto?>(null)
    override val myStats: StateFlow<ProfileStatsDto?> = _myStats.asStateFlow()

    private val _lobbyFilters = MutableStateFlow(LobbyFilters())
    override val lobbyFilters: StateFlow<LobbyFilters> = _lobbyFilters.asStateFlow()

    private val _feedState = MutableStateFlow(GameHistoryUiState())
    override val feedState: StateFlow<GameHistoryUiState> = _feedState.asStateFlow()

    private var pollingJob: Job? = null
    private var connectedPollingJob: Job? = null

    companion object {
        /** Intervalo de refresco del lobby (partidas en vivo + búsquedas). */
        val LIVE_POLL_INTERVAL: Duration = 5.seconds

        /** Usuarios en línea se refrescan cada 2 ciclos (~10 s) dentro del polling de En Vivo. */
        private const val ONLINE_USERS_EVERY_N_CYCLES = 2
        const val PAGE_SIZE: Int = 20
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

    // Retorna el access token actual, refrescándolo si está próximo a expirar.
    private suspend fun getValidToken(): String? {
        if (authViewModel.isTokenExpiringSoon()) authViewModel.refreshToken()
        return authViewModel.accessToken
    }

    private suspend fun fetchOnlineUsers() {
        val token = getValidToken() ?: return
        repository.getOnlineUsers(token).onSuccess { users ->
            _onlineUsers.value = users
        }
    }

    private suspend fun fetchLiveGames() {
        val token = getValidToken() ?: run {
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
                if (e is CancellationException) throw e
                logger.error("fetchLiveGames error: ${e::class.simpleName} — ${e.message}")
                _liveGames.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    private suspend fun fetchOpenSearches() {
        val token = getValidToken() ?: return
        _openSearches.update { it.copy(isLoading = it.searches.isEmpty(), error = null) }

        repository.getOpenSearches(token)
            .onSuccess { searches ->
                logger.debug("fetchOpenSearches: received ${searches.size} open searches")
                _openSearches.update { it.copy(searches = searches, isLoading = false) }
            }
            .onFailure { e ->
                if (e is CancellationException) throw e
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
        loadPagedContent(_history) { token, page, limit ->
            repository.getGameHistory(
                token = token,
                page = page,
                limit = limit,
                timeControl = _history.value.filters.timeControl,
                result = _history.value.filters.result,
                rated = _history.value.filters.rated,
            )
        }
    }

    /**
     * Carga las estadísticas sumarizadas del propio usuario una sola vez por sesión
     * del ViewModel. Son agregadas (no dependen de los filtros): la UI selecciona el
     * control de tiempo del lado del cliente. No-op sin sesión.
     */
    private fun loadMyStats() {
        if (_myStats.value != null) return
        val userId = authViewModel.currentUser?.userId ?: return
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            repository.getProfile(token, userId)
                .onSuccess { profile -> _myStats.value = profile.stats }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    logger.debug("loadMyStats failed: ${e.message}")
                }
        }
    }

    override fun loadMoreHistory() {
        loadMorePagedContent(_history) { token, page, limit ->
            repository.getGameHistory(
                token = token,
                page = page,
                limit = limit,
                timeControl = _history.value.filters.timeControl,
                result = _history.value.filters.result,
                rated = _history.value.filters.rated,
            )
        }
    }

    // ── History filters ────────────────────────────────────────────────────────

    override fun setTimeControlFilter(tc: String?) {
        _history.update { it.copy(filters = it.filters.copy(timeControl = tc)) }
        loadHistory()
    }

    override fun setResultFilter(result: String?) {
        _history.update { it.copy(filters = it.filters.copy(result = result)) }
        loadHistory()
    }

    override fun setRatedFilter(rated: Boolean?) {
        _history.update { it.copy(filters = it.filters.copy(rated = rated)) }
        loadHistory()
    }

    override fun clearFilters() {
        _history.update { it.copy(filters = HistoryFilters()) }
        loadHistory()
    }

    // ── Game preview ──────────────────────────────────────────────────────────

    override suspend fun loadAndPreviewGame(gameId: String): MatchDto? {
        val token = getValidToken() ?: return null
        return repository.getGame(token = token, gameId = gameId)
            .getOrNull()
            ?.toMatchDto()
    }

    // ── Social feed ────────────────────────────────────────────────────────────

    override fun loadFeed() {
        loadPagedContent(_feedState) { token, page, limit ->
            repository.getFeed(token = token, page = page, limit = limit)
        }
    }

    override fun loadMoreFeed() {
        loadMorePagedContent(_feedState) { token, page, limit ->
            repository.getFeed(token = token, page = page, limit = limit)
        }
    }

    // ── Pagination helpers ─────────────────────────────────────────────────────

    /**
     * Carga la primera página de contenido paginado en [stateFlow].
     * Resetea el estado y delega la llamada al repositorio a [load].
     */
    private fun loadPagedContent(
        stateFlow: MutableStateFlow<GameHistoryUiState>,
        load: suspend (token: String, page: Int, limit: Int) -> Result<PagedResponse<GameHistoryDto>>,
    ) {
        viewModelScope.launch {
            val token = getValidToken() ?: run {
                logger.debug("loadPagedContent: no token, skipping")
                return@launch
            }
            stateFlow.update { it.copy(isLoading = true, error = null, currentPage = 0, games = emptyList()) }
            load(token, 0, PAGE_SIZE)
                .onSuccess { paged ->
                    stateFlow.update {
                        it.copy(
                            games = paged.items,
                            isLoading = false,
                            currentPage = 0,
                            total = paged.total,
                            hasMore = paged.items.size < paged.total,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    stateFlow.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /**
     * Agrega la siguiente página de contenido paginado en [stateFlow].
     * No-op si ya hay una carga en curso o no hay más páginas.
     */
    private fun loadMorePagedContent(
        stateFlow: MutableStateFlow<GameHistoryUiState>,
        load: suspend (token: String, page: Int, limit: Int) -> Result<PagedResponse<GameHistoryDto>>,
    ) {
        val state = stateFlow.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            val nextPage = state.currentPage + 1
            stateFlow.update { it.copy(isLoadingMore = true) }
            load(token, nextPage, PAGE_SIZE)
                .onSuccess { paged ->
                    stateFlow.update {
                        it.copy(
                            games = it.games + paged.items,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            total = paged.total,
                            hasMore = (it.games.size + paged.items.size) < paged.total,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    stateFlow.update { it.copy(isLoadingMore = false, error = e.message) }
                }
        }
    }
}
