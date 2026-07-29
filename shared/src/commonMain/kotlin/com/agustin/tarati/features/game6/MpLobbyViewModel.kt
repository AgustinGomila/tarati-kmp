package com.agustin.tarati.features.game6

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.client.MpOnlineClient
import com.agustin.tarati.network.models.MpFeedGameDto
import com.agustin.tarati.network.models.MpGameHistoryDto
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpOnlineGame
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.PagedResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Estado del tab "Mis Partidas" del lobby MP: página acumulada del historial + flags de carga.
 *
 * @property endReached true cuando ya se cargaron todas las partidas (no hay más páginas).
 */
@Immutable
data class MpHistoryUiState(
    val items: List<MpGameHistoryDto> = emptyList(),
    val isLoading: Boolean = false,
    val page: Int = 0,
    val endReached: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * Estado del tab "Seguidos" del lobby MP: página acumulada del feed social + flags de carga.
 *
 * @property endReached true cuando ya se cargaron todas las entradas (no hay más páginas).
 */
@Immutable
data class MpFeedUiState(
    val items: List<MpFeedGameDto> = emptyList(),
    val isLoading: Boolean = false,
    val page: Int = 0,
    val endReached: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel del **lobby de mesas** del juego multijugador online (M7.2). Clase plana con scope
 * inyectable (como [MpLocalGameViewModel], no un androidx `ViewModel`) → testeable sin
 * `Dispatchers.setMain`.
 *
 * Combina la **lista de mesas públicas abiertas** (REST `GET /api/mp/tables`, refresco periódico) con
 * el estado del [MpOnlineClient] (mesa propia / partida arrancada / errores) y expone las **acciones**
 * (crear/unirse/bots/iniciar/salir) delegando en el cliente. La pantalla arranca/detiene el refresco.
 *
 * `@Stable`: toda su API pública son `StateFlow`/`SharedFlow` (observados vía `collectAsState`) o
 * funciones — sin propiedades mutables no observables. Permite a Compose saltar recomposiciones al
 * pasarlo como parámetro (p. ej. a `MpLobbyScreen`).
 */
@Stable
class MpLobbyViewModel(
    private val client: MpOnlineClient,
    private val getToken: suspend () -> String?,
    private val fetchTables: suspend (token: String) -> Result<List<MpTableDto>>,
    private val fetchLiveGames: suspend (token: String) -> Result<List<MpLiveGameDto>>,
    private val fetchHistory: suspend (token: String, page: Int, limit: Int) -> Result<PagedResponse<MpGameHistoryDto>> =
        { _, _, _ -> Result.success(PagedResponse(emptyList(), 0, 0, HISTORY_PAGE_SIZE)) },
    private val fetchFeed: suspend (token: String, page: Int, limit: Int) -> Result<PagedResponse<MpFeedGameDto>> =
        { _, _, _ -> Result.success(PagedResponse(emptyList(), 0, 0, HISTORY_PAGE_SIZE)) },
    scope: CoroutineScope? = null,
    private val pollIntervalMs: Long = 4_000L,
) {
    private val logger = getLogger("MpLobbyViewModel")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tables = MutableStateFlow<List<MpTableDto>>(emptyList())

    /** Mesas públicas abiertas (para listar en el lobby). */
    val tables: StateFlow<List<MpTableDto>> = _tables.asStateFlow()

    private val _liveGames = MutableStateFlow<List<MpLiveGameDto>>(emptyList())

    /** Partidas en curso (para la pestaña "En Vivo" — observar como espectador). */
    val liveGames: StateFlow<List<MpLiveGameDto>> = _liveGames.asStateFlow()

    /** Mesa propia (del cliente); no-null cuando el usuario está sentado en una mesa del lobby. */
    val currentTable: StateFlow<MpTableDto?> = client.currentTable

    /** Partida arrancada; la pantalla navega a la partida cuando deja de ser null. */
    val currentGame: StateFlow<MpOnlineGame?> = client.currentGame

    /** Códigos de error del servidor (`table_full`, `not_host`, `illegal_move`, …). */
    val errors: SharedFlow<String> = client.errors

    /** Motivo por el que se cerró la mesa actual (`host_left`, …). */
    val tableClosed: SharedFlow<String> = client.tableClosed

    private val _history = MutableStateFlow(MpHistoryUiState())

    /** Historial paginado de partidas MP propias (tab "Mis Partidas"). */
    val history: StateFlow<MpHistoryUiState> = _history.asStateFlow()

    private val _feed = MutableStateFlow(MpFeedUiState())

    /** Feed social paginado: partidas de jugadores seguidos (tab "Seguidos"). */
    val feed: StateFlow<MpFeedUiState> = _feed.asStateFlow()

    private var pollingJob: Job? = null

    /** Arranca el refresco periódico de la lista de mesas (idempotente). */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = _scope.launch {
            while (isActive) {
                fetchOnce()
                delay(pollIntervalMs.milliseconds)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Refresca la lista de mesas una vez (fire-and-forget). */
    fun refresh() {
        _scope.launch { fetchOnce() }
    }

    // ── Historial ("Mis Partidas") ────────────────────────────────────────────────

    /** Carga la primera página del historial. Idempotente si ya está cargado o cargando. */
    fun loadHistory() {
        val st = _history.value
        if (st.isLoading || st.loaded) return
        _scope.launch { fetchHistoryPage(0, replace = true) }
    }

    /** Carga la siguiente página (scroll infinito). No-op si carga en curso o ya no hay más. */
    fun loadMoreHistory() {
        val st = _history.value
        if (st.isLoading || st.endReached) return
        _scope.launch { fetchHistoryPage(st.page + 1, replace = false) }
    }

    /** Recarga desde cero (pull-to-refresh o al re-entrar al tab tras jugar). */
    fun refreshHistory() {
        _history.value = MpHistoryUiState()
        loadHistory()
    }

    private suspend fun fetchHistoryPage(page: Int, replace: Boolean) {
        val token = getToken() ?: return
        _history.update { it.copy(isLoading = true, error = null) }
        fetchHistory(token, page, HISTORY_PAGE_SIZE)
            .onSuccess { resp ->
                _history.update { cur ->
                    val merged = if (replace) resp.items else cur.items + resp.items
                    cur.copy(
                        items = merged,
                        isLoading = false,
                        page = resp.page,
                        endReached = merged.size.toLong() >= resp.total,
                        loaded = true,
                    )
                }
            }
            .onFailure { e ->
                logger.debug("getMpHistory failed: ${e.message}")
                _history.update { it.copy(isLoading = false, error = e.message, loaded = true) }
            }
    }

    // ── Feed social ("Seguidos") ──────────────────────────────────────────────────

    /** Carga la primera página del feed. Idempotente si ya está cargado o cargando. */
    fun loadFeed() {
        val st = _feed.value
        if (st.isLoading || st.loaded) return
        _scope.launch { fetchFeedPage(0, replace = true) }
    }

    /** Carga la siguiente página del feed (scroll infinito). */
    fun loadMoreFeed() {
        val st = _feed.value
        if (st.isLoading || st.endReached) return
        _scope.launch { fetchFeedPage(st.page + 1, replace = false) }
    }

    /** Recarga el feed desde cero. */
    fun refreshFeed() {
        _feed.value = MpFeedUiState()
        loadFeed()
    }

    private suspend fun fetchFeedPage(page: Int, replace: Boolean) {
        val token = getToken() ?: return
        _feed.update { it.copy(isLoading = true, error = null) }
        fetchFeed(token, page, HISTORY_PAGE_SIZE)
            .onSuccess { resp ->
                _feed.update { cur ->
                    val merged = if (replace) resp.items else cur.items + resp.items
                    cur.copy(
                        items = merged,
                        isLoading = false,
                        page = resp.page,
                        endReached = merged.size.toLong() >= resp.total,
                        loaded = true,
                    )
                }
            }
            .onFailure { e ->
                logger.debug("getMpFeed failed: ${e.message}")
                _feed.update { it.copy(isLoading = false, error = e.message, loaded = true) }
            }
    }

    private suspend fun fetchOnce() {
        val token = getToken() ?: return
        fetchTables(token)
            .onSuccess { _tables.value = it }
            .onFailure { logger.debug("getMpTables failed: ${it.message}") }
        fetchLiveGames(token)
            .onSuccess { _liveGames.value = it }
            .onFailure { logger.debug("getMpLiveGames failed: ${it.message}") }
    }

    // ── Acciones (delegan en el cliente; toleran fallo de envío si no hay conexión) ──

    fun createTable(playerCount: Int): Unit = action { client.createTable(playerCount) }
    fun joinTable(tableId: String): Unit = action { client.joinTable(tableId) }
    fun leaveTable(): Unit = action { client.leaveTable() }
    fun addBot(seatIndex: Int): Unit = action { client.addBot(seatIndex) }
    fun removeBot(seatIndex: Int): Unit = action { client.removeBot(seatIndex) }
    fun startTable(): Unit = action { client.startTable() }

    /** Envía una jugada en la partida online en curso (vértices por nombre). */
    fun makeMove(from: String, to: String): Unit = action { client.makeMove(from, to) }

    /** Empieza a **observar** una partida en curso (espectador). */
    fun spectate(gameId: String): Unit = action { client.spectateGame(gameId) }

    /** Deja de observar la partida actual y vuelve al lobby. */
    fun leaveSpectating(): Unit = action { client.leaveSpectating() }

    /** Olvida la partida terminada (al volver al lobby desde la pantalla de resultado). */
    fun clearGame(): Unit = client.clearGame()

    /** ¿La jugada [moveCount] de la partida online es nueva (no ya presentada por la UI)? */
    fun isFreshMove(moveCount: Int): Boolean = client.isFreshMove(moveCount)

    /** Marca [moveCount] como presentada (sonido + animación aplicados) — evita replay al re-entrar. */
    fun markMovePresented(moveCount: Int): Unit = client.markMovePresented(moveCount)

    /** ¿El fin de la partida [gameId] es nuevo (aún no presentado)? Gatea el popup de resultado. */
    fun isFreshGameOver(gameId: String): Boolean = client.isFreshGameOver(gameId)

    /** Marca el fin de [gameId] como presentado — evita repetir la alerta de resultado al re-entrar. */
    fun markGameOverPresented(gameId: String): Unit = client.markGameOverPresented(gameId)

    private fun action(block: suspend () -> Unit) {
        _scope.launch {
            runCatching { block() }.onFailure { logger.error("MP lobby action failed", it) }
        }
    }

    companion object {
        const val HISTORY_PAGE_SIZE: Int = 20
    }
}
