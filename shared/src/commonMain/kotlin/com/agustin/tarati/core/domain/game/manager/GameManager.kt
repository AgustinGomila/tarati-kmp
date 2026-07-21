package com.agustin.tarati.core.domain.game.manager

import com.agustin.tarati.core.domain.game.manager.GameManagerState.Companion.createInitialUiState
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.HistoryEntry
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.domain.history.LinearHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fuente de verdad del estado de partida en curso.
 *
 * ## Por qué cuatro StateFlows separados en lugar de un único StateFlow<GameManagerState>
 * Cada campo ([gameState], [gameStatus], [history], [moveIndex]) tiene una
 * frecuencia de cambio distinta. Un único StateFlow<GameManagerState> emite en
 * cada cambio de cualquier campo, causando recomposiciones en todos los
 * observadores aunque el campo que les interesa no haya cambiado.
 * Con cuatro flujos independientes, cada flujo individual puede ser observado
 * selectivamente cuando solo se necesita una parte del estado.
 *
 * ## Historial navegable
 * El historial vive en un [LinearHistory] (estructura compartida con el juego
 * multijugador), que garantiza el invariante de **línea única**: al agregar un
 * movimiento con el cursor en el pasado (el usuario hizo undo y luego jugó), la
 * línea futura se descarta antes de agregar la nueva entrada — no hay árbol de
 * variantes, como en los editores de texto con undo/redo. Los StateFlows [history]
 * y [moveIndex] son la proyección observable de esa línea; la navegación
 * (undo/redo/moveTo) sólo cambia [moveIndex] (y [gameState]) sin re-emitir
 * [history], preservando su estabilidad para Compose.
 */
class GameManager(
    uiState: GameManagerState = createInitialUiState(),
) {
    private val _gameStatus = MutableStateFlow(uiState.gameStatus)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _gameState = MutableStateFlow(uiState.gameState)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _history = MutableStateFlow(uiState.history)
    val history: StateFlow<StableHistoryList> = _history.asStateFlow()

    private val _moveIndex = MutableStateFlow(uiState.moveIndex)
    val moveIndex: StateFlow<Int> = _moveIndex.asStateFlow()

    // Línea navegable de (movimiento, estado resultante) + la posición inicial. La base arranca en la
    // apertura estándar (independiente de uiState, como antes) y se rehidrata con las entradas de
    // uiState si las hubiera (en la práctica createInitialUiState viene vacío).
    private val line = LinearHistory<GameState, Move>(GameState.initialGameState()).apply {
        restore(uiState.history.toList().map { it.move to it.gameState }, uiState.moveIndex)
    }

    // Public API
    fun updateGameStatus(newStatus: GameStatus) {
        _gameStatus.update { newStatus }
    }

    /**
     * Estado del tablero antes del primer movimiento de la partida actual.
     * Se fija en [setInitialGameState] al iniciar o importar una partida —
     * [clearHistory] y [updateHistory] también lo mantienen, garantizando el
     * invariante «estado en índice -1 == [initialGameState]» — y se usa al
     * navegar a la posición inicial ([undoMove] / [moveToIndex]) y al exportar
     * para reconstruir posiciones intermedias.
     */
    val initialGameState: GameState get() = line.initialState

    fun setInitialGameState(state: GameState) {
        line.rebase(state)
    }

    fun updateGameState(newState: GameState) {
        _gameState.update { newState }
    }

    fun updateHistory(moves: List<Move>, initialState: GameState = initialGameState) {
        line.replay(initialState, moves) { state, move -> state.applyMove(move) }
        publishLine()
    }

    fun getCurrentState(): GameManagerState =
        GameManagerState(
            gameState = _gameState.value,
            history = _history.value,
            moveIndex = _moveIndex.value,
            gameStatus = _gameStatus.value,
        )

    fun addMove(
        move: Move,
        nextState: GameState,
        onMoveRecord: () -> Unit = {},
    ) {
        line.append(move, nextState)
        publishLine()

        onMoveRecord()
        updateGameState(nextState)
    }

    fun undoMove() {
        if (!line.canUndo) return

        updateGameStatus(GameStatus.NO_PLAYING)

        line.undo()
        _moveIndex.update { line.cursor }
        updateGameState(line.currentState())
    }

    fun redoMove() {
        if (!line.canRedo) return

        updateGameStatus(GameStatus.NO_PLAYING)

        line.redo()
        _moveIndex.update { line.cursor }
        updateGameState(line.currentState())
    }

    /**
     * Navega directamente al estado del historial en [index].
     * [index] = -1 restaura la posición inicial (antes del primer movimiento).
     * Índices fuera de [-1, history.size - 1] se ignoran silenciosamente.
     * Equivalente a llamar [undoMove]/[redoMove] repetidamente, pero en O(1).
     */
    fun moveToIndex(index: Int) {
        if (!line.moveTo(index)) return
        updateGameStatus(GameStatus.NO_PLAYING)
        _moveIndex.update { line.cursor }
        updateGameState(line.currentState())
    }

    fun moveToCurrentState() {
        updateGameStatus(GameStatus.NO_PLAYING)

        if (line.size > 0) {
            line.moveToTip()
            _moveIndex.update { line.cursor }
            updateGameState(line.currentState())
        }
    }

    fun clearHistory(gameState: GameState = GameState.initialGameState()) {
        line.reset(gameState)
        publishLine()
        updateGameState(gameState)
    }

    /** Proyecta la línea navegable en los StateFlows observables [history] y [moveIndex]. */
    private fun publishLine() {
        _history.update { StableHistoryList(line.entries.map { (move, state) -> HistoryEntry(move, state) }) }
        _moveIndex.update { line.cursor }
    }
}
