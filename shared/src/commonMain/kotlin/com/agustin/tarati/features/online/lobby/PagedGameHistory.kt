package com.agustin.tarati.features.online.lobby


import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.PagedResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State models ───────────────────────────────────────────────────────────────

/** Filtros de un listado paginado de partidas. */
data class HistoryFilters(
    val timeControl: String? = null,   // null = todos
    val result: String? = null,        // "win" | "loss" | "draw" | null
    val rated: Boolean? = null,        // null = todos
)

/** Estado de un listado paginado de partidas (historial propio, feed, perfil público). */
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

// ── Loader ─────────────────────────────────────────────────────────────────────

/**
 * Máquina de estados de un listado paginado de partidas con filtros.
 *
 * Converge la lógica que duplicaban [OnlineLobbyViewModel] (historial propio y feed)
 * y [PublicProfileViewModel] (historial del perfil): carga con reset, append de páginas
 * con guardas (`isLoadingMore`/`hasMore`) y setters de filtros que recargan.
 *
 * El token se obtiene con [validToken] (renovación proactiva) en cada petición;
 * sin sesión, la carga es un no-op.
 *
 * @param scope Scope del ViewModel dueño (las cargas se cancelan con él).
 * @param fetch Llamada al repositorio: página `page` (0-based) de tamaño `limit`
 *              con [HistoryFilters] aplicados (los endpoints sin filtros los ignoran).
 */
class PagedGameHistoryLoader(
    private val scope: CoroutineScope,
    private val authViewModel: IAuthViewModel,
    private val fetch: suspend (
        token: String, page: Int, limit: Int, filters: HistoryFilters,
    ) -> Result<PagedResponse<GameHistoryDto>>,
) {
    private val logger = getLogger("PagedGameHistoryLoader")

    private val _state = MutableStateFlow(GameHistoryUiState())
    val state: StateFlow<GameHistoryUiState> = _state.asStateFlow()

    /** Carga la primera página, reseteando el listado. */
    fun load() {
        scope.launch {
            val token = authViewModel.validToken() ?: run {
                logger.debug("load: no token, skipping")
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null, currentPage = 0, games = emptyList()) }
            fetch(token, 0, PAGE_SIZE, _state.value.filters)
                .onSuccess { paged ->
                    _state.update {
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
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Agrega la página siguiente. No-op si ya hay una carga en curso o no hay más páginas. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore) return
        scope.launch {
            val token = authViewModel.validToken() ?: return@launch
            val nextPage = current.currentPage + 1
            _state.update { it.copy(isLoadingMore = true) }
            fetch(token, nextPage, PAGE_SIZE, current.filters)
                .onSuccess { paged ->
                    _state.update {
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
                    _state.update { it.copy(isLoadingMore = false, error = e.message) }
                }
        }
    }

    // ── Filtros (cada cambio recarga desde la primera página) ──────────────────

    fun setTimeControlFilter(tc: String?) = updateFilters { it.copy(timeControl = tc) }

    fun setResultFilter(result: String?) = updateFilters { it.copy(result = result) }

    fun setRatedFilter(rated: Boolean?) = updateFilters { it.copy(rated = rated) }

    fun clearFilters() = updateFilters { HistoryFilters() }

    private fun updateFilters(transform: (HistoryFilters) -> HistoryFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        load()
    }

    companion object {
        /** Tamaño de página de los listados de partidas. */
        const val PAGE_SIZE: Int = 20
    }
}
