package com.agustin.tarati.features.game6

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.models.CreateMpTournamentRequest
import com.agustin.tarati.network.models.MpTournamentDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.network.protocol.MpServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel del **tab Torneos** del lobby MP (Arena, fase 4b). Clase plana con scope inyectable (como
 * [MpLobbyViewModel]/[MpLeaderboardViewModel], no un androidx `ViewModel`) → testeable sin
 * `Dispatchers.setMain`.
 *
 * Combina la **lista de torneos** (REST `GET /api/mp/tournaments`, refresco periódico) con el detalle
 * del torneo **seleccionado** y las actualizaciones **en vivo** por WebSocket (clasificación / fin /
 * cancelación, vía los flows del [MpOnlineClient]). Las acciones (crear/inscribir/darse de baja/
 * arrancar/cancelar) delegan en el repositorio inyectado.
 *
 * `@Stable`: su API pública son `StateFlow`/`SharedFlow` o funciones — Compose puede saltar
 * recomposiciones al pasarlo como parámetro.
 */
@Stable
class MpTournamentViewModel(
    private val getToken: suspend () -> String?,
    private val fetchList: suspend (token: String) -> Result<List<MpTournamentDto>> =
        { Result.success(emptyList()) },
    private val fetchDetail: suspend (token: String, id: String) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    private val createReq: suspend (token: String, request: CreateMpTournamentRequest) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    private val registerReq: suspend (token: String, id: String) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    private val unregisterReq: suspend (token: String, id: String) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    private val startReq: suspend (token: String, id: String) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    private val cancelReq: suspend (token: String, id: String) -> Result<MpTournamentDto> =
        { _, _ -> Result.failure(NotImplementedError()) },
    standings: SharedFlow<MpServerMessage.TournamentStandingsUpdated> = MutableSharedFlow(),
    finished: SharedFlow<MpServerMessage.TournamentFinished> = MutableSharedFlow(),
    cancelled: SharedFlow<String> = MutableSharedFlow(),
    scope: CoroutineScope? = null,
    private val pollIntervalMs: Long = 5_000L,
) {
    private val logger = getLogger("MpTournamentViewModel")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _list = MutableStateFlow<List<MpTournamentDto>>(emptyList())

    /** Torneos visibles (en registro / activos). */
    val list: StateFlow<List<MpTournamentDto>> = _list.asStateFlow()

    private val _selected = MutableStateFlow<MpTournamentDto?>(null)

    /** Torneo abierto en detalle (null = se muestra la lista). */
    val selected: StateFlow<MpTournamentDto?> = _selected.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Códigos de error de acciones (para toast). */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var pollingJob: Job? = null

    init {
        // Actualizaciones en vivo del torneo seleccionado (y de su fila en la lista).
        standings
            .onEach { patchStandings(it.tournamentId, it.standings, status = null) }
            .launchIn(_scope)
        finished
            .onEach {
                patchStandings(it.tournamentId, it.standings, status = TournamentStatus.FINISHED)
                refreshOnce()
            }
            .launchIn(_scope)
        cancelled
            .onEach { id ->
                _selected.update { s -> if (s?.id == id) s.copy(status = TournamentStatus.CANCELLED) else s }
                refreshOnce()
            }
            .launchIn(_scope)
    }

    /** Arranca el refresco periódico de la lista (idempotente). */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = _scope.launch {
            while (isActive) {
                refreshOnce()
                delay(pollIntervalMs.milliseconds)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh(): Unit = _scope.launchIgnored { refreshOnce() }

    /** Abre el detalle del torneo [id] (carga la versión completa por REST). */
    fun select(id: String): Unit = _scope.launchIgnored {
        _list.value.firstOrNull { it.id == id }?.let { _selected.value = it } // muestra algo mientras carga
        val token = getToken() ?: return@launchIgnored
        fetchDetail(token, id).onSuccess { _selected.value = it }
    }

    /** Vuelve a la lista de torneos. */
    fun clearSelection() {
        _selected.value = null
    }

    fun create(request: CreateMpTournamentRequest): Unit = action { token ->
        createReq(token, request).onSuccess { created ->
            _selected.value = created
            refreshOnce()
        }
    }

    fun register(): Unit = selectedAction(registerReq)
    fun unregister(): Unit = selectedAction(unregisterReq)
    fun start(): Unit = selectedAction(startReq)
    fun cancel(): Unit = selectedAction(cancelReq)

    // ── Interno ─────────────────────────────────────────────────────────────────

    private suspend fun refreshOnce() {
        val token = getToken() ?: return
        fetchList(token)
            .onSuccess { _list.value = it }
            .onFailure { logger.debug("getMpTournaments failed: ${it.message}") }
    }

    /** Aplica standings (y opcionalmente un cambio de estado) al detalle y a la fila de la lista. */
    private fun patchStandings(
        id: String,
        standings: List<com.agustin.tarati.network.models.MpTournamentStandingDto>,
        status: TournamentStatus?,
    ) {
        _selected.update { s ->
            if (s?.id != id) s
            else s.copy(standings = standings, status = status ?: s.status)
        }
        _list.update { list ->
            list.map { t -> if (t.id == id) t.copy(standings = standings, status = status ?: t.status) else t }
        }
    }

    /** Ejecuta una acción sobre el torneo seleccionado; en OK reemplaza el detalle, en error emite el código. */
    private fun selectedAction(op: suspend (token: String, id: String) -> Result<MpTournamentDto>): Unit =
        action { token ->
            val id = _selected.value?.id ?: return@action
            op(token, id)
                .onSuccess { _selected.value = it; refreshOnce() }
                .onFailure { _errors.tryEmit(it.message ?: "action_failed") }
        }

    private fun action(block: suspend (token: String) -> Unit): Unit = _scope.launchIgnored {
        val token = getToken() ?: return@launchIgnored
        runCatching { block(token) }.onFailure { logger.error("MP tournament action failed", it) }
    }

    private fun CoroutineScope.launchIgnored(block: suspend () -> Unit) {
        launch { block() }
    }
}
