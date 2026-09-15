package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move

interface BoardRenderEvents {
    fun onReset()

    fun onBoardSizeChange(size: Size)

    fun onUpdateBoardOrientation(orientation: BoardOrientation)

    fun onSyncState(gameState: GameState)

    /**
     * Un arrastrar-y-soltar despachó [move] tras soltar en [startOffset] (px del
     * contenedor). Permite animar la pieza desde el punto de soltado. Default no-op.
     */
    fun onMoveFromDrop(move: Move, startOffset: Offset) {}
}
