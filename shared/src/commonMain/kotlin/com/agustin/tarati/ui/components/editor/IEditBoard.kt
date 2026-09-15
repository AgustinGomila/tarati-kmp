package com.agustin.tarati.ui.components.editor

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.play.GameState

interface IEditBoard {
    fun toggleEditColor()

    fun toggleEditTurn()

    fun rotateEditBoard()

    fun togglePlayerSide()

    fun clearEditBoard()

    fun editPiece(vertex: Vertex)

    /** Reubica la pieza de [from] a [to] (arrastrar y soltar en el editor). Default no-op. */
    fun editMovePiece(from: Vertex, to: Vertex) {}

    fun startGameFromEditedState()

    fun toggleEditing()

    fun setGame(gameState: GameState)

    fun copyBoardToClipboard()

    fun pasteBoardFromClipboard(isRequested: Boolean)
}