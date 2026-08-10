package com.agustin.tarati.ui.components.game.highlights.base

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.ui.components.game.highlights.HighlightAction

data class VertexHighlight(
    val vertex: Vertex,
    override val pulse: Boolean = false,
    override val duration: Long = 500L,
    override val startDelay: Long = 0L,
    override val postDelay: Long = 0L,
    override val persistent: Boolean = false,
    val action: HighlightAction = HighlightAction.MOVE,
) : BaseHighlight
