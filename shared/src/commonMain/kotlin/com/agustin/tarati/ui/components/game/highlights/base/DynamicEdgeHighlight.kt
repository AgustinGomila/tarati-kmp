package com.agustin.tarati.ui.components.game.highlights.base

import androidx.compose.ui.geometry.Offset
import com.agustin.tarati.ui.components.game.highlights.HighlightAction

data class DynamicEdgeHighlight(
    val from: Offset,
    val to: Offset,
    override val pulse: Boolean = false,
    override val duration: Long = 500L,
    override val startDelay: Long = 0L,
    override val postDelay: Long = 0L,
    override val persistent: Boolean = false,
    val action: HighlightAction = HighlightAction.MOVE,
) : BaseHighlight