package com.agustin.tarati.ui.components.game

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.play.Move

@Stable
interface BoardEvents {
    fun onMove(move: Move)

    fun onEditPiece(from: Vertex)

    /** Reubica en modo edición la pieza de [from] a [to]. Default no-op (previews/tests). */
    fun onEditMovePiece(from: Vertex, to: Vertex) {}

    fun onResetCompleted()
}