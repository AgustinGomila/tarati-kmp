package com.agustin.tarati.ui.components.editor

import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.PieceCounts
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditBoardManager : IEditBoardManager {
    // Estados de edición
    private val _isEditing = MutableStateFlow(false)
    override val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _editColor = MutableStateFlow(CobColor.WHITE)
    override val editColor: StateFlow<CobColor> = _editColor.asStateFlow()

    private val _editTurn = MutableStateFlow(CobColor.WHITE)
    override val editTurn: StateFlow<CobColor> = _editTurn.asStateFlow()

    private val _editBoardOrientation = MutableStateFlow(BoardOrientation.PORTRAIT_WHITE)
    override val editBoardOrientation: StateFlow<BoardOrientation> = _editBoardOrientation.asStateFlow()

    // Métodos de edición
    fun endEditing(): Unit = _isEditing.update { false }

    fun toggleEditing(currentTurn: CobColor, boardOrientation: BoardOrientation) {
        val newValue = !_isEditing.value
        _isEditing.update { newValue }

        if (newValue) {
            _editTurn.update { currentTurn }
            _editColor.update { CobColor.WHITE }
            // Sincronizar con la orientación activa del tablero del Sidebar para que
            // el editor arranque exactamente con la vista que el usuario ya tiene.
            _editBoardOrientation.update { boardOrientation }
        }
    }

    fun toggleEditColor(): Unit = _editColor.update { it.opponent }

    fun toggleEditTurn(): Unit = _editTurn.update { it.opponent }

    fun rotateEditBoard() {
        _editBoardOrientation.update {
            when (it) {
                BoardOrientation.PORTRAIT_WHITE -> BoardOrientation.LANDSCAPE_BLACK
                BoardOrientation.LANDSCAPE_BLACK -> BoardOrientation.PORTRAIT_BLACK
                BoardOrientation.PORTRAIT_BLACK -> BoardOrientation.LANDSCAPE_WHITE
                BoardOrientation.LANDSCAPE_WHITE -> BoardOrientation.PORTRAIT_WHITE
            }
        }
    }

    fun clearEditBoard(): GameState = GameState.cleanGameState(_editTurn.value)

    fun editPiece(
        vertex: Vertex,
        currentState: GameState,
    ): GameState {
        val currentCob = currentState.cobs[vertex]
        val mutableCobs = currentState.cobs.toMutableMap()
        val pieceCounts = currentState.getPieceCounts()

        when {
            // Caso 1: No hay pieza - colocar nueva
            currentCob == null -> {
                if (canPlacePiece(_editColor.value, pieceCounts)) {
                    mutableCobs[vertex] = Cob(_editColor.value)
                }
            }
            // Caso 2: Pieza del color seleccionado - mejorar
            currentCob.color == _editColor.value && !currentCob.isUpgraded -> {
                mutableCobs[vertex] = currentCob.copy(isUpgraded = true)
            }
            // Caso 3: Pieza mejorada del color seleccionado - quitar
            currentCob.color == _editColor.value && currentCob.isUpgraded -> {
                mutableCobs.remove(vertex)
            }
            // Caso 4: Pieza del color opuesto - reemplazar
            else -> {
                if (canReplacePiece(currentCounts = pieceCounts)) {
                    mutableCobs[vertex] = Cob(_editColor.value)
                }
            }
        }

        return currentState.copy(cobs = mutableCobs.toMap())
    }

    /**
     * Reubica la pieza de [from] a [to] conservando su color y estado (arrastrar y soltar).
     * No-op si no hay pieza en [from] o si [to] ya está ocupado (no se pisa otra pieza).
     * No altera los contadores: es un movimiento, no un alta/baja.
     */
    fun editMovePiece(
        from: Vertex,
        to: Vertex,
        currentState: GameState,
    ): GameState {
        val cob = currentState.cobs[from] ?: return currentState
        if (from == to || currentState.cobs[to] != null) return currentState

        val mutableCobs = currentState.cobs.toMutableMap()
        mutableCobs.remove(from)
        mutableCobs[to] = cob
        return currentState.copy(cobs = mutableCobs.toMap())
    }

    fun validateDistributionForGameStart(currentState: GameState): Boolean {
        val pieceCounts = currentState.getPieceCounts()
        return isValidDistribution(pieceCounts.white, pieceCounts.black)
    }

    // Funciones de validación (las mismas que tenías)
    private fun canPlacePiece(
        color: CobColor,
        currentCounts: PieceCounts,
    ): Boolean {
        val totalPieces = currentCounts.white + currentCounts.black
        if (totalPieces >= 8) return false

        val newWhiteCount = if (color == CobColor.WHITE) currentCounts.white + 1 else currentCounts.white
        val newBlackCount = if (color == CobColor.BLACK) currentCounts.black + 1 else currentCounts.black

        return isValidDistribution(newWhiteCount, newBlackCount)
    }

    private fun canReplacePiece(currentCounts: PieceCounts): Boolean =
        isValidDistribution(currentCounts.white, currentCounts.black)

    private fun isValidDistribution(
        white: Int,
        black: Int,
    ): Boolean {
        val total = white + black
        if (total > 8) return false

        // Distribuciones permitidas: 7-1, 6-2, 5-3, 4-4
        return when {
            white == 7 && black == 1 -> true
            white == 6 && black == 2 -> true
            white == 5 && black == 3 -> true
            white == 4 && black == 4 -> true
            white == 1 && black == 7 -> true
            white == 2 && black == 6 -> true
            white == 3 && black == 5 -> true
            total < 8 -> true // Durante construcción, mientras no lleguemos a 8
            else -> false
        }
    }
}