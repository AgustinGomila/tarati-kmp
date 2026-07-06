package com.agustin.tarati.features.game6

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.client.MpOnlineClient
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpOnlineGame
import com.agustin.tarati.network.models.MpTableDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
}
