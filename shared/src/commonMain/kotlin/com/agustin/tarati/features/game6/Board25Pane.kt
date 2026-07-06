package com.agustin.tarati.features.game6

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.features.settings.BoardVisualState

/**
 * Envoltura delgada sobre [Board25View] que **desempaca [BoardVisualState]** en las flags de
 * visibilidad/animación de Settings. La comparten el juego local ([MpGameScreen]) y el online
 * ([MpOnlineGameScreen]), que sólo difieren en el origen de los datos (VM vs. `MpOnlineGame`), el
 * `onVertexTap` y el [suppressMoveAnimation] (online, al re-entrar). El `moveCount` sale del estado.
 */
@Composable
fun Board25Pane(
    state: MpGameState,
    seatIsAI: List<Boolean>,
    selection: Vertex?,
    legalTargets: Set<Vertex>,
    threatened: Set<Vertex>,
    lastMove: MpMove?,
    converted: Map<Vertex, PlayerColor>,
    boardVisual: BoardVisualState,
    onVertexTap: (Vertex) -> Unit,
    modifier: Modifier = Modifier,
    suppressMoveAnimation: Boolean = false,
    preMoveFrom: Vertex? = null,
    preMoveTargets: Set<Vertex> = emptySet(),
    pendingPreMove: MpMove? = null,
) {
    Board25View(
        state = state,
        seatIsAI = seatIsAI,
        selection = selection,
        legalTargets = legalTargets,
        threatened = threatened,
        onVertexTap = onVertexTap,
        lastMove = lastMove,
        converted = converted,
        moveCount = state.moveCount,
        animate = boardVisual.animateEffects,
        conversionStyle = boardVisual.conversionAnimationStyle,
        showEdges = boardVisual.edgesVisibles,
        showVertices = boardVisual.verticesVisibles,
        showLabels = boardVisual.labelsVisibles,
        showRegions = boardVisual.regionsVisibles,
        showPerimeter = boardVisual.perimeterVisible,
        modifier = modifier,
        suppressMoveAnimation = suppressMoveAnimation,
        preMoveFrom = preMoveFrom,
        preMoveTargets = preMoveTargets,
        pendingPreMove = pendingPreMove,
    )
}
