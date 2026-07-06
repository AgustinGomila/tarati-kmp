package com.agustin.tarati.features.game6

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.ai.MpGreedyBot
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.rules.MpCutConfig
import com.agustin.tarati.core.domain.game6.rules.MpMatch
import com.agustin.tarati.core.domain.game6.rules.MpPreMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.core.domain.game6.rules.MpTransforms
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Eventos **one-shot** para la UI (sonido y popup de fin), emitidos en el momento en que ocurren.
 * Se consumen vía [SharedFlow] (sin replay) para que **no** se re-disparen al recomponer o al volver
 * a entrar en modo Multi con una partida ya avanzada/terminada.
 */
sealed interface MpUiEvent {
    /** Se aplicó una jugada; [capture] indica si convirtió piezas (sonido de captura vs. movimiento). */
    data class Moved(val capture: Boolean) : MpUiEvent

    /** La partida terminó con [result] (dispara sonido de fin + popup de resultado). */
    data class Finished(val result: MpResult) : MpUiEvent

    /** Comenzó una partida nueva (sonido de nueva partida). */
    data object NewGame : MpUiEvent
}

/**
 * Configuración editable de la próxima partida: cantidad de jugadores y tipo (Humano/IA) por
 * asiento. Los asientos 0 y 1 siempre están habilitados; 2–5 se habilitan subiendo [playerCount].
 */
data class MpConfig(
    val playerCount: Int,
    val seatIsAI: List<Boolean>,
)

/**
 * Estado de una partida multijugador **local** (hot-seat o contra bots), sin dependencias de
 * plataforma. La UI observa los `StateFlow`; los taps se traducen con [onVertexTap] y los turnos de
 * bot los dispara la pantalla con [playBotMove] (ver [isBotTurn]).
 *
 * La [config] es editable antes de arrancar; [newGame] la aplica a un estado fresco.
 *
 * `@Stable`: toda su API pública son `StateFlow`/`SharedFlow` (observados vía `collectAsState`) o
 * funciones — no hay propiedades mutables no observables. Anotarlo permite a Compose saltar
 * recomposiciones cuando se pasa como parámetro (p. ej. a `MpGameScreen`).
 */
@Stable
class MpLocalGameViewModel(
    private val random: Random = Random.Default,
    private val cut: MpCutConfig = MpCutConfig.Default,
) {
    private val _config = MutableStateFlow(defaultConfig())
    val config: StateFlow<MpConfig> = _config.asStateFlow()

    /**
     * Partida en curso: aplica el corte por estancamiento (triple repetición + N jugadas sin
     * conversión, §2.6 del plan). Se reemplaza por una instancia nueva en cada [rebuild].
     */
    private var match = MpMatch(MpSetup.initialState(_config.value.playerCount), cut = cut)

    private val _state = MutableStateFlow(match.state)
    val state: StateFlow<MpGameState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<Vertex?>(null)
    val selection: StateFlow<Vertex?> = _selection.asStateFlow()

    private val _legalTargets = MutableStateFlow<Set<Vertex>>(emptySet())
    val legalTargets: StateFlow<Set<Vertex>> = _legalTargets.asStateFlow()

    private val _history = MutableStateFlow<List<PlayerMove>>(emptyList())
    val history: StateFlow<List<PlayerMove>> = _history.asStateFlow()

    // ── Historial navegable (undo/redo) ──────────────────────────────────────────
    //
    // [states] guarda un snapshot por ply (states[0] = inicial; states[i] = tras i jugadas), y
    // [_moveIndex] marca la posición **visualizada** (−1 = inicial; history.size−1 = tip/actual). El
    // [match] se mantiene siempre en el tip; aplicar una jugada estando en el pasado descarta la línea
    // futura (branch) y reconstruye el runner. La navegación (undo/redo/moveToIndex) sólo cambia la
    // vista, sin mover el runner.

    /** Snapshots del estado tras cada jugada (states[0] = inicial). Tamaño = history.size + 1. */
    private val states = mutableListOf(match.state)

    /** Índice del movimiento visualizado: −1 = posición inicial; `history.size − 1` = tip (actual). */
    private val _moveIndex = MutableStateFlow(-1)
    val moveIndex: StateFlow<Int> = _moveIndex.asStateFlow()

    /** Piezas enemigas que la pieza seleccionada podría capturar (amenazadas). */
    private val _threatened = MutableStateFlow<Set<Vertex>>(emptySet())
    val threatened: StateFlow<Set<Vertex>> = _threatened.asStateFlow()

    /** Último movimiento aplicado (para animar el desplazamiento de la pieza). */
    private val _lastMove = MutableStateFlow<MpMove?>(null)
    val lastMove: StateFlow<MpMove?> = _lastMove.asStateFlow()

    /** Piezas convertidas por el último movimiento → su dueño **anterior** (para animar el flip). */
    private val _converted = MutableStateFlow<Map<Vertex, PlayerColor>>(emptyMap())
    val converted: StateFlow<Map<Vertex, PlayerColor>> = _converted.asStateFlow()

    /**
     * Señal para (re)evaluar el turno de bot desde la pantalla tras acciones que **no** cambian
     * `currentSeatIndex` — nueva partida que cae sobre el mismo asiento inicial, o convertir un
     * asiento en IA. La UI la incluye en la clave del efecto que dispara [playBotMove], de modo que
     * los bots arrancan solos (paridad con single: "arranca si el primer asiento es IA").
     */
    private val _botKick = MutableStateFlow(0)
    val botKick: StateFlow<Int> = _botKick.asStateFlow()

    /** Eventos one-shot (sonido / popup de fin). Ver [MpUiEvent]. */
    private val _events = MutableSharedFlow<MpUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MpUiEvent> = _events.asSharedFlow()

    // ── Pre-movimiento ──────────────────────────────────────────────────────────
    //
    // Habilitado sólo cuando hay **exactamente un asiento humano** (1 humano vs. bots), análogo al
    // single (1 humano vs. IA). Mientras juega un bot, el humano puede pre-seleccionar una pieza y
    // fijar un destino; al volver su turno la pantalla ejecuta el pendiente vía [tryExecutePreMove].
    // Canal paralelo a [selection]/[legalTargets] (que son del flujo normal, en el turno humano).

    /** Pieza pre-seleccionada (fase de pre-selección; sin destino aún). */
    private val _preMoveFrom = MutableStateFlow<Vertex?>(null)
    val preMoveFrom: StateFlow<Vertex?> = _preMoveFrom.asStateFlow()

    /** Destinos legales de la pieza pre-seleccionada, proyectados sobre el estado actual. */
    private val _preMoveTargets = MutableStateFlow<Set<Vertex>>(emptySet())
    val preMoveTargets: StateFlow<Set<Vertex>> = _preMoveTargets.asStateFlow()

    /** Pre-movimiento confirmado, pendiente de ejecución al volver el turno humano. */
    private val _pendingPreMove = MutableStateFlow<MpMove?>(null)
    val pendingPreMove: StateFlow<MpMove?> = _pendingPreMove.asStateFlow()

    /** Preferencia de Settings (`preMovesEnabled`), sincronizada por la pantalla. */
    private var preMovesEnabled: Boolean = false

    /** Asientos controlados por IA en la partida en curso (fijados al iniciarla con [newGame]). */
    private var activeBotSeats: Set<Int> = botSeatsOf(_config.value)

    // ── Editor de posiciones (D14) ────────────────────────────────────────────────
    //
    // Modo edición análogo al `BoardEditor` de single: se colocan/quitan piezas por color sobre el
    // tablero `25`. Mientras [isEditing] es `true`, [onVertexTap] rutea a [editPiece] y los bots no
    // juegan. Se edita directamente sobre [_state] (buffer de trabajo) sin tocar [states]/[history]:
    // **Cancelar** (re-toque del botón) restaura la posición del tip; **Iniciar** reconstruye el
    // [MpMatch] desde la posición editada (historial/undo reseteados). Los colores editables son los
    // de los asientos de la partida (según [config], 2–6).

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    /** Color con el que se colocan piezas en modo edición (cicla entre los colores de los asientos). */
    private val _editColor = MutableStateFlow(PlayerColor.P1)
    val editColor: StateFlow<PlayerColor> = _editColor.asStateFlow()

    /**
     * Cambia la cantidad de jugadores (2–6) y **recoloca las piezas en vivo**: al incorporar un
     * jugador aparecen sus 4 cobs en la base más distanciada posible (se re-arma el tablero desde
     * la nueva config).
     */
    fun setPlayerCount(count: Int) {
        val clamped = count.coerceIn(MpSetup.MIN_PLAYERS, MpSetup.MAX_PLAYERS)
        val current = _config.value.seatIsAI
        val resized = when {
            clamped > current.size -> current + List(clamped - current.size) { true }
            else -> current.take(clamped)
        }
        _config.value = MpConfig(playerCount = clamped, seatIsAI = resized)
        rebuild()
    }

    /** Fija el tipo (Humano/IA) del asiento [index]; efecto inmediato sobre los turnos de bot. */
    fun setSeatIsAI(index: Int, isAI: Boolean) {
        val current = _config.value
        if (index !in current.seatIsAI.indices) return
        _config.value = current.copy(
            seatIsAI = current.seatIsAI.toMutableList().also { it[index] = isAI },
        )
        activeBotSeats = botSeatsOf(_config.value)
        // Cambiar los asientos altera quién es "el humano" → el pre-movimiento pendiente deja de ser válido.
        clearPreMove()
        _botKick.value += 1
    }

    /** Sincroniza la preferencia de Settings; al desactivarla, descarta el pre-movimiento en curso. */
    fun setPreMovesEnabled(enabled: Boolean) {
        preMovesEnabled = enabled
        if (!enabled) clearPreMove()
    }

    /** Reinicia la partida usando la [config] actual. */
    fun newGame() {
        rebuild()
        _events.tryEmit(MpUiEvent.NewGame)
    }

    // ── Navegación del historial (undo/redo) ─────────────────────────────────────

    /** `true` si la posición visualizada es la actual (el tip); sólo ahí juegan los bots. */
    fun isAtTip(): Boolean = cursor() == tip()

    /** Retrocede una jugada (no-op si ya está en la posición inicial). */
    fun undo(): Unit = navigateTo(cursor() - 1)

    /** Avanza una jugada (no-op si ya está en el tip). */
    fun redo(): Unit = navigateTo(cursor() + 1)

    /** Salta a la posición **tras** la jugada lineal [index] (−1 = inicial). */
    fun moveToIndex(index: Int): Unit = navigateTo(index + 1)

    /** Salta a la posición actual (tip). */
    fun moveToCurrent(): Unit = navigateTo(tip())

    private fun tip(): Int = _history.value.size
    private fun cursor(): Int = _moveIndex.value + 1

    /**
     * Cambia la posición visualizada a [target] (0..tip) sin alterar la línea: restaura el snapshot,
     * limpia selección/pre-move y no anima (las piezas hacen snap). Los bots no juegan fuera del tip.
     */
    private fun navigateTo(target: Int) {
        val c = target.coerceIn(0, tip())
        if (c == cursor()) return
        _moveIndex.value = c - 1
        _state.value = states[c]
        _lastMove.value = null
        _converted.value = emptyMap()
        clearSelection()
        clearPreMove()
    }

    /** Reconstruye el runner desde el estado base (`states[0]`) replayando las primeras [plies] jugadas. */
    private fun rebuildMatch(plies: Int) {
        match = MpMatch(states[0], cut = cut)
        _history.value.take(plies).forEach { match.applyMove(it.move) }
    }

    /**
     * Rota la perspectiva del tablero 60° (un sextante): re-mapea piezas y bases vía
     * [MpTransforms.rotate60]. Como el tablero `25` es 60°-simétrico, la vista se ve igual salvo por
     * los colores (cada jugador gira a la base contigua). No es una jugada: no cambia el turno ni el
     * contador. Limpia la selección y el último movimiento para no animar posiciones viejas.
     */
    fun rotate() {
        // Rota TODA la línea (snapshots + jugadas) para que el undo/redo siga siendo consistente tras el
        // cambio de perspectiva: rotar sólo la vista dejaría las posiciones anteriores en la orientación
        // vieja. Rotación = automorfismo del grafo → las jugadas rotadas siguen siendo legales.
        val rotated = states.map { MpTransforms.rotate60(it) }
        states.clear()
        states.addAll(rotated)
        _history.value = _history.value.map { pm ->
            PlayerMove(pm.color, MpMove(Board25.rotate60(pm.move.from), Board25.rotate60(pm.move.to)))
        }
        rebuildMatch(tip())
        _state.value = states[cursor()]
        _lastMove.value = null
        _converted.value = emptyMap()
        clearSelection()
        clearPreMove()
    }

    /** Re-arma el estado desde la config actual (tablero fresco, sin selección ni historial). */
    private fun rebuild() {
        val cfg = _config.value
        activeBotSeats = botSeatsOf(cfg)
        match = MpMatch(MpSetup.initialState(cfg.playerCount), cut = cut)
        states.clear()
        states.add(match.state)
        _history.value = emptyList()
        _moveIndex.value = -1
        _lastMove.value = null
        _converted.value = emptyMap()
        _state.value = match.state
        clearSelection()
        clearPreMove()
        // La partida fresca arranca en el asiento 0; si la anterior también estaba ahí, currentSeatIndex
        // no cambia y el efecto de bots no se re-dispararía → el kick lo fuerza.
        _botKick.value += 1
    }

    /**
     * `true` si al asiento en turno lo controla un bot, la partida sigue en curso y se está en el tip.
     * Fuera del tip (revisando el historial) los bots no juegan — el usuario navega o vuelve al actual.
     */
    fun isBotTurn(): Boolean {
        val s = _state.value
        return !_isEditing.value && !s.isGameOver && s.currentSeatIndex in activeBotSeats && isAtTip()
    }

    /** Aplica el movimiento elegido por el bot para el asiento en turno (si corresponde). */
    fun playBotMove() {
        if (!isBotTurn()) return
        val move = MpGreedyBot.chooseMove(_state.value, random = random) ?: return
        applyAndClear(move)
    }

    /**
     * Color del **único** asiento humano (1 humano vs. bots), o `null` si hay 0 o 2+ humanos → el
     * pre-movimiento sólo se habilita en el caso análogo al single (hot-seat con 2+ humanos no tiene
     * ventana de espera). Los índices de [MpConfig.seatIsAI] están alineados con [MpGameState.seats].
     */
    fun humanSeatColor(): PlayerColor? {
        val ai = _config.value.seatIsAI
        val humanIndices = ai.indices.filter { !ai[it] }
        if (humanIndices.size != 1) return null
        return _state.value.seats.getOrNull(humanIndices.first())?.color
    }

    /**
     * Maneja el tap de un humano sobre [vertex]. Durante el turno de un bot, si el pre-movimiento está
     * habilitado y hay un único humano, el tap alimenta la FSM de pre-movimiento ([MpPreMove]); en el
     * turno humano, selecciona una pieza propia o mueve al destino legal. Ignora el resto.
     */
    fun onVertexTap(vertex: Vertex) {
        // Modo edición: el tap coloca/quita/reemplaza una pieza (no juega ni selecciona).
        if (_isEditing.value) {
            editPiece(vertex)
            return
        }

        val s = _state.value
        if (s.isGameOver) return

        // Turno ajeno (bot): ruta de pre-movimiento, si aplica.
        if (isBotTurn()) {
            val human = humanSeatColor()
            if (preMovesEnabled && human != null) applyPreMoveTap(human, vertex)
            return
        }

        val currentColor = s.currentSeat.color
        val piece = s.pieces[vertex]

        if (piece != null && piece.owner == currentColor) {
            val moves = MpRules.legalMoves(s).filter { it.from == vertex }
            _selection.value = vertex
            _legalTargets.value = moves.map { it.to }.toSet()
            // Piezas amenazadas: enemigos que la pieza seleccionada podría capturar en algún destino.
            _threatened.value = moves.flatMap { MpRules.captureTargets(s.pieces, it) }.toSet()
            return
        }

        val from = _selection.value
        if (from != null) {
            val move = MpMove(from, vertex)
            if (MpRules.isLegal(s, move)) {
                applyAndClear(move)
                return
            }
        }

        clearSelection()
    }

    /** Interpreta un tap durante el turno de un bot vía [MpPreMove] y aplica el resultado al estado. */
    private fun applyPreMoveTap(human: PlayerColor, vertex: Vertex) {
        when (val result = MpPreMove.onTap(_state.value, human, _preMoveFrom.value, vertex)) {
            is MpPreMove.TapResult.PreSelect -> {
                _preMoveFrom.value = result.from
                _preMoveTargets.value = result.targets
                _pendingPreMove.value = null
            }

            is MpPreMove.TapResult.SetPending -> {
                _pendingPreMove.value = result.move
                _preMoveFrom.value = null
                _preMoveTargets.value = emptySet()
            }

            MpPreMove.TapResult.Clear -> clearPreMove()
            MpPreMove.TapResult.Ignore -> Unit
        }
    }

    /**
     * Ejecuta el pre-movimiento pendiente cuando vuelve el turno humano, revalidándolo contra el
     * estado actual ([MpPreMove.isReady]); si dejó de ser legal, lo descarta en silencio. La pantalla
     * la invoca (tras un breve retardo) al detectar que el turno pasó a ser del humano.
     */
    fun tryExecutePreMove() {
        val pending = _pendingPreMove.value ?: return
        val human = humanSeatColor()
        if (human == null || !MpPreMove.isReady(_state.value, human, pending)) {
            clearPreMove()
            return
        }
        clearPreMove()
        applyAndClear(pending)
    }

    // ── Acciones del editor de posiciones ─────────────────────────────────────────

    /** Colores editables = los de los asientos de la partida (según [config], en orden de turno). */
    private fun seatColors(): List<PlayerColor> = _state.value.seats.map { it.color }

    /**
     * Entra/sale del modo edición. Al entrar edita desde la posición actual (tip). Al salir por este
     * mismo botón (**cancelar**) descarta las ediciones y restaura la posición visualizada — como
     * [states]/[history] no se tocaron durante la edición, basta releer el snapshot del cursor.
     */
    fun toggleEditing() {
        if (_isEditing.value) {
            _isEditing.value = false
            _state.value = states[cursor()]
            clearSelection()
        } else {
            moveToCurrent()
            _isEditing.value = true
            _editColor.value = seatColors().firstOrNull() ?: PlayerColor.P1
            clearSelection()
            clearPreMove()
        }
    }

    /** Cicla el color de edición entre los colores de los asientos. */
    fun cycleEditColor() {
        val colors = seatColors()
        if (colors.isEmpty()) return
        val idx = colors.indexOf(_editColor.value)
        _editColor.value = colors[(idx + 1) % colors.size]
    }

    /** Cicla el asiento en turno (a quién le tocará mover al iniciar la partida editada). */
    fun cycleEditTurn() {
        val s = _state.value
        val n = s.seats.size
        if (n == 0) return
        _state.value = s.copy(currentSeatIndex = (s.currentSeatIndex + 1) % n)
    }

    /**
     * Coloca/quita/reemplaza la pieza en [vertex] con el [editColor] (ciclo colocar→quitar; sin
     * promoción). El `hasLeftBase` se deriva de la base del dueño ([hasLeftBaseFor]).
     */
    private fun editPiece(vertex: Vertex) {
        val s = _state.value
        val color = _editColor.value
        val current = s.pieces[vertex]
        val pieces = s.pieces.toMutableMap()
        when {
            current == null -> pieces[vertex] = Piece(owner = color, hasLeftBase = hasLeftBaseFor(color, vertex))
            current.owner == color -> pieces.remove(vertex)
            else -> pieces[vertex] = Piece(owner = color, hasLeftBase = hasLeftBaseFor(color, vertex))
        }
        _state.value = s.copy(pieces = pieces.toMap())
    }

    /** Vacía el tablero (conserva asientos y turno). */
    fun clearEditBoard() {
        _state.value = _state.value.copy(pieces = emptyMap())
    }

    /** Repuebla la posición inicial estándar para los asientos actuales y pone el turno en el asiento 0. */
    fun resetEditBoard() {
        val s = _state.value
        val pieces = mutableMapOf<Vertex, Piece>()
        s.seats.forEach { seat ->
            Board25.baseById(seat.baseId).startSquare.forEach { v ->
                pieces[v] = Piece(owner = seat.color) // hasLeftBase = false (en su base)
            }
        }
        _state.value = s.copy(pieces = pieces.toMap(), currentSeatIndex = 0)
    }

    /** `true` si la posición editada permite iniciar: al menos 2 colores con piezas (validación libre). */
    fun editCanStart(): Boolean =
        _state.value.pieces.values.map { it.owner }.toSet().size >= 2

    /**
     * Inicia una partida nueva desde la posición editada (historial/undo reseteados). Normaliza el
     * `hasLeftBase` de cada pieza y, si el asiento en turno quedó sin piezas, avanza al primero (en
     * orden de turno) que sí tenga. No-op si [editCanStart] es `false`.
     */
    fun startGameFromEdit() {
        if (!editCanStart()) return
        val s = _state.value
        val colorsWithPieces = s.pieces.values.map { it.owner }.toSet()
        val n = s.seats.size
        val startIndex = (0 until n)
            .map { (s.currentSeatIndex + it) % n }
            .firstOrNull { s.seats[it].color in colorsWithPieces } ?: 0
        val normalized = s.pieces.mapValues { (v, p) -> p.copy(hasLeftBase = hasLeftBaseFor(p.owner, v)) }
        val edited = MpGameState(pieces = normalized, seats = s.seats, currentSeatIndex = startIndex)

        match = MpMatch(edited, cut = cut)
        states.clear()
        states.add(edited)
        _history.value = emptyList()
        _moveIndex.value = -1
        _lastMove.value = null
        _converted.value = emptyMap()
        _state.value = edited
        _isEditing.value = false
        activeBotSeats = botSeatsOf(_config.value)
        clearSelection()
        clearPreMove()
        _botKick.value += 1
    }

    /** `hasLeftBase` derivado: `false` si [vertex] pertenece al cuadrado de la base del dueño [color]. */
    private fun hasLeftBaseFor(color: PlayerColor, vertex: Vertex): Boolean {
        val seat = _state.value.seats.firstOrNull { it.color == color } ?: return true
        return vertex !in Board25.baseById(seat.baseId).startSquare
    }

    private fun applyAndClear(move: MpMove) {
        // Si se está viendo una posición anterior (tras un undo), aplicar una jugada nueva **descarta la
        // línea futura** y continúa desde aquí (paridad con single): se truncan snapshots + historial y
        // se reconstruye el runner en la posición visualizada.
        val c = cursor()
        if (c < tip()) {
            _history.value = _history.value.take(c)
            while (states.size > c + 1) states.removeAt(states.size - 1)
            rebuildMatch(c)
        }
        val before = _state.value
        val color = before.currentSeat.color
        // Dueños anteriores de las piezas que este movimiento convertirá (para animar el flip).
        val conversions = MpRules.captureTargets(before.pieces, move)
            .associateWith { before.pieces.getValue(it).owner }
        _converted.value = conversions
        val after = match.applyMove(move)
        states.add(after)
        _state.value = after
        _history.value += PlayerMove(color, move)
        _moveIndex.value = _history.value.size - 1
        _lastMove.value = move
        clearSelection()
        // Paridad con single: una pre-selección sin confirmar se descarta al cambiar el estado; un
        // pre-movimiento **confirmado** sobrevive (se revalida al volver el turno en [tryExecutePreMove]).
        if (_pendingPreMove.value == null) {
            _preMoveFrom.value = null
            _preMoveTargets.value = emptySet()
        }
        _events.tryEmit(MpUiEvent.Moved(capture = conversions.isNotEmpty()))
        after.result?.let { _events.tryEmit(MpUiEvent.Finished(it)) }
    }

    private fun clearSelection() {
        _selection.value = null
        _legalTargets.value = emptySet()
        _threatened.value = emptySet()
    }

    private fun clearPreMove() {
        _preMoveFrom.value = null
        _preMoveTargets.value = emptySet()
        _pendingPreMove.value = null
    }

    private companion object {
        // Por defecto: 2 jugadores, P1 humano vs P2 IA.
        fun defaultConfig() = MpConfig(playerCount = 2, seatIsAI = listOf(false, true))

        fun botSeatsOf(config: MpConfig): Set<Int> =
            config.seatIsAI.mapIndexedNotNull { index, isAI -> index.takeIf { isAI } }.toSet()
    }
}
