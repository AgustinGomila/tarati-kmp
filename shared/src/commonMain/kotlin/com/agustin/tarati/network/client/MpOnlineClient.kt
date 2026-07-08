package com.agustin.tarati.network.client

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpNotation
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.models.MpOnlineGame
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.protocol.ClientMessage
import com.agustin.tarati.network.protocol.MpClientMessage
import com.agustin.tarati.network.protocol.MpServerMessage
import com.agustin.tarati.network.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * API de alto nivel del juego **multijugador** online (M7), análoga a [OnlineGameClient] pero para el
 * protocolo MP anidado. Reusa el **mismo** WebSocket: recibe todos los `ServerMessage`, filtra los
 * `ServerMessage.Mp` y proyecta su [MpServerMessage] a flows que la UI observa; envía acciones como
 * `ClientMessage.Mp(...)`.
 *
 * Está **desacoplado** del `TaratiWebSocketClient` concreto (recibe el flujo de entrada y una función
 * de envío) para poder testear el manejo de mensajes sin una conexión real. En producción se cablea
 * con `wsClient.messages` y `wsClient::send`.
 */
class MpOnlineClient(
    incoming: SharedFlow<ServerMessage>,
    private val sendMessage: suspend (ClientMessage) -> Unit,
    scope: CoroutineScope? = null,
) {
    private val logger = getLogger("MpOnlineClient")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Mesa del lobby en la que está el usuario (null si no está en ninguna o ya arrancó la partida). */
    private val _currentTable = MutableStateFlow<MpTableDto?>(null)
    val currentTable: StateFlow<MpTableDto?> = _currentTable.asStateFlow()

    /** Partida MP en curso (null si no hay). */
    private val _currentGame = MutableStateFlow<MpOnlineGame?>(null)
    val currentGame: StateFlow<MpOnlineGame?> = _currentGame.asStateFlow()

    /** Códigos de error de acciones MP (`table_full`, `not_host`, `illegal_move`, …). */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Motivo por el que se cerró la mesa actual (`host_left`, …). */
    private val _tableClosed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val tableClosed: SharedFlow<String> = _tableClosed.asSharedFlow()

    /**
     * Nº de la última jugada ya **presentada** por la UI (sonido + animación de deslizamiento/flip).
     * Vive en el cliente (`single`) → **sobrevive el cambio de modo Single↔Multi**, así que al re-entrar
     * a la partida NO se re-anima ni re-suena el último movimiento (que sería un "rehacer" espurio).
     * `-1` = ninguna presentada aún.
     */
    private var lastPresentedMoveCount = -1

    /** ¿La jugada [moveCount] es **nueva** (aún no presentada)? Gatea sonido/animación en la pantalla. */
    fun isFreshMove(moveCount: Int): Boolean = moveCount != lastPresentedMoveCount

    /** Marca [moveCount] como ya presentada (sonido + animación aplicados). */
    fun markMovePresented(moveCount: Int) {
        lastPresentedMoveCount = moveCount
    }

    /**
     * `gameId` de la partida cuyo **fin** (sonido + popup de resultado) ya fue presentado. Sobrevive el
     * cambio de modo → al re-entrar a una partida terminada NO se repite la alerta de resultado.
     */
    private var presentedGameOverFor: String? = null

    /** ¿El fin de la partida [gameId] es nuevo (aún no presentado)? Gatea el popup de resultado. */
    fun isFreshGameOver(gameId: String): Boolean = gameId != presentedGameOverFor

    /** Marca el fin de la partida [gameId] como ya presentado (sonido + popup mostrados). */
    fun markGameOverPresented(gameId: String) {
        presentedGameOverFor = gameId
    }

    init {
        incoming
            .onEach { if (it is ServerMessage.Mp) handle(it.payload) }
            .launchIn(_scope)
    }

    // ── Recepción ───────────────────────────────────────────────────────────────

    private fun handle(payload: MpServerMessage) {
        when (payload) {
            is MpServerMessage.TableUpdate -> _currentTable.value = payload.table

            is MpServerMessage.TableClosed -> {
                if (_currentTable.value?.id == payload.tableId) _currentTable.value = null
                _tableClosed.tryEmit(payload.reason)
            }

            is MpServerMessage.Error -> _errors.tryEmit(payload.code)

            is MpServerMessage.GameStarted -> {
                _currentTable.value = null // salimos del lobby, entramos a la partida
                // Al entrar/reconectar, la posición actual ya está "presentada": no animar ni sonar el
                // último movimiento del historial rehidratado (solo se anima lo que llegue después).
                lastPresentedMoveCount = payload.state.moveCount
                _currentGame.value = MpOnlineGame(
                    gameId = payload.gameId,
                    state = payload.state,
                    players = payload.players,
                    turnTimeoutMs = payload.turnTimeoutMs,
                    // Historial ya jugado (join/reconexión): se rehidrata de los tokens serializados.
                    history = payload.moves.mapNotNull { runCatching { MpNotation.parseMove(it) }.getOrNull() },
                    spectating = payload.spectating,
                )
            }

            is MpServerMessage.StateUpdate -> {
                val game = _currentGame.value
                if (game != null && game.gameId == payload.gameId) {
                    // El que movió es el asiento que tenía el turno en el estado PREVIO.
                    val move = parseLastMove(payload.lastMoveFrom, payload.lastMoveTo)
                    val newHistory = if (move != null) {
                        game.history + PlayerMove(game.state.currentSeat.color, move)
                    } else {
                        game.history // retiro / snap → no es una jugada
                    }
                    _currentGame.value = game.copy(
                        state = payload.state,
                        lastMoveFrom = payload.lastMoveFrom,
                        lastMoveTo = payload.lastMoveTo,
                        converted = payload.converted,
                        history = newHistory,
                    )
                }
            }

            // El estado final ya llegó en el StateUpdate previo (state.result != null); confirma el fin.
            is MpServerMessage.GameEnded -> logger.debug("MP game ${payload.gameId} ended")
        }
    }

    // ── Envío (acciones de lobby / partida) ──────────────────────────────────────

    suspend fun createTable(playerCount: Int): Unit =
        sendMp(MpClientMessage.CreateTable(playerCount))

    suspend fun joinTable(tableId: String): Unit =
        sendMp(MpClientMessage.JoinTable(tableId))

    suspend fun leaveTable() {
        val id = _currentTable.value?.id ?: return
        sendMp(MpClientMessage.LeaveTable(id))
        _currentTable.value = null
    }

    suspend fun addBot(seatIndex: Int) {
        val id = _currentTable.value?.id ?: return
        sendMp(MpClientMessage.AddBot(id, seatIndex))
    }

    suspend fun removeBot(seatIndex: Int) {
        val id = _currentTable.value?.id ?: return
        sendMp(MpClientMessage.RemoveBot(id, seatIndex))
    }

    suspend fun startTable() {
        val id = _currentTable.value?.id ?: return
        sendMp(MpClientMessage.StartTable(id))
    }

    suspend fun makeMove(from: String, to: String) {
        val gameId = _currentGame.value?.gameId ?: return
        sendMp(MpClientMessage.MakeMove(gameId, from, to))
    }

    /** Empieza a **observar** una partida en curso (espectador): el servidor manda el estado. */
    suspend fun spectateGame(gameId: String): Unit =
        sendMp(MpClientMessage.SpectateGame(gameId))

    /** Deja de observar la partida actual (si era espectador) y vuelve al lobby. */
    suspend fun leaveSpectating() {
        val game = _currentGame.value ?: return
        if (game.spectating) sendMp(MpClientMessage.LeaveSpectating(game.gameId))
        _currentGame.value = null
    }

    /** Envuelve [msg] en [ClientMessage.Mp] y lo envía por el WebSocket compartido. */
    private suspend fun sendMp(msg: MpClientMessage): Unit = sendMessage(ClientMessage.Mp(msg))

    /** Olvida la partida terminada (al salir de la pantalla de resultado). */
    fun clearGame() {
        _currentGame.value = null
    }

    private fun parseLastMove(from: String?, to: String?): MpMove? {
        if (from == null || to == null) return null
        return runCatching { MpMove(Vertex.parseVertex(from), Vertex.parseVertex(to)) }.getOrNull()
    }
}
