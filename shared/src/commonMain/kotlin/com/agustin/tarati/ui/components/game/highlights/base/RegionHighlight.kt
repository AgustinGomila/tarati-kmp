package com.agustin.tarati.ui.components.game.highlights.base

import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.ui.components.game.highlights.HighlightAction

data class RegionHighlight(
    val region: Region,
    override val pulse: Boolean = true,
    override val duration: Long = 500L,
    override val startDelay: Long = 0,
    override val postDelay: Long = 0,
    override val persistent: Boolean = false,
    val action: HighlightAction = HighlightAction.MOVE,
) : BaseHighlight
