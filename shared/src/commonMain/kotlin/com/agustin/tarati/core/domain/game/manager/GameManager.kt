package com.agustin.tarati.core.domain.game.manager

import com.agustin.tarati.core.domain.game.manager.GameManagerState.Companion.createInitialUiState
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.HistoryEntry
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
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
 * ## Truncado del historial al agregar un movimiento
 * Si [moveIndex] no apunta al último movimiento (el usuario hizo undo y luego
 * jugó), el historial se trunca hasta [moveIndex] antes de agregar la nueva
 * entrada. Esto garantiza que el árbol de variantes no se ramifique: la historia
 * es siempre una línea única, como en los editores de texto con undo/redo.
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
    var initialGameState: GameState = GameState.initialGameState()
        private set

    fun setInitialGameState(state: GameState) {
        initialGameState = state
    }

    fun updateGameState(newState: GameState) {
        _gameState.update { newState }
    }

    fun updateHistory(moves: List<Move>, initialState: GameState = initialGameState) {
        initialGameState = initialState
        var currentState = initialState

        val historyEntries = moves.map { move ->
            currentState = currentState.applyMove(move)
            HistoryEntry(move, currentState)
        }

        _history.update { StableHistoryList(historyEntries) }
        _moveIndex.update { historyEntries.lastIndex }
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
        val newEntry = move to nextState
        val currentHistory = _history.value.toList()
        val currentMoveIndex = _moveIndex.value

        val truncatedHistory =
            if (currentMoveIndex < currentHistory.lastIndex) {
                currentHistory.take(currentMoveIndex + 1)
            } else {
                currentHistory
            }

        val updatedHistory = truncatedHistory + HistoryEntry.fromPair(newEntry)

        _history.update { StableHistoryList(updatedHistory) }
        _moveIndex.update { updatedHistory.lastIndex }

        onMoveRecord()
        updateGameState(nextState)
    }

    fun undoMove() {
        if (!canUndo()) return

        updateGameStatus(GameStatus.NO_PLAYING)

        val targetIndex = (_moveIndex.value - 1).coerceAtLeast(-1)

        _moveIndex.update { targetIndex }
        val targetState =
            if (targetIndex >= 0) {
                _history.value[targetIndex].gameState
            } else {
                initialGameState
            }
        updateGameState(targetState)
    }

    fun redoMove() {
        if (!canRedo()) return

        updateGameStatus(GameStatus.NO_PLAYING)

        val targetIndex = _moveIndex.value + 1
        _moveIndex.update { targetIndex }
        updateGameState(_history.value[targetIndex].gameState)
    }

    /**
     * Navega directamente al estado del historial en [index].
     * [index] = -1 restaura la posición inicial (antes del primer movimiento).
     * Índices fuera de [-1, history.size - 1] se ignoran silenciosamente.
     * Equivalente a llamar [undoMove]/[redoMove] repetidamente, pero en O(1).
     */
    fun moveToIndex(index: Int) {
        if (index !in -1 until _history.value.size) return
        updateGameStatus(GameStatus.NO_PLAYING)
        _moveIndex.update { index }
        val targetState = if (index >= 0) _history.value[index].gameState
        else initialGameState
        updateGameState(targetState)
    }

    fun moveToCurrentState() {
        updateGameStatus(GameStatus.NO_PLAYING)

        _history.value.toList().lastOrNull()?.let { lastEntry ->
            _moveIndex.update { _history.value.toList().lastIndex }
            updateGameState(lastEntry.gameState)
        }
    }

    fun clearHistory(gameState: GameState = GameState.initialGameState()) {
        initialGameState = gameState
        _history.update { StableHistoryList(emptyList()) }
        _moveIndex.update { -1 }
        updateGameState(gameState)
    }

    // Private helpers
    private fun canUndo() = _moveIndex.value >= 0 && _history.value.toList().isNotEmpty()

    private fun canRedo() = _moveIndex.value + 1 < _history.value.size
}