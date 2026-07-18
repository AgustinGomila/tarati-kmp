package com.agustin.tarati.core.domain.game.board

import com.agustin.tarati.core.domain.game.pieces.CobColor
import kotlinx.serialization.Serializable

@Serializable
enum class BoardOrientation {
    PORTRAIT_WHITE,  // 0°   — Human pieces advance ▲ (upward)
    LANDSCAPE_BLACK, // 90°  — Human pieces advance ► (rightward)
    PORTRAIT_BLACK,  // 180° — Human pieces advance ▼ (downward)
    LANDSCAPE_WHITE, // 270° — Human pieces advance ◄ (leftward)
}

fun BoardOrientation.isPortrait(): Boolean =
    this == BoardOrientation.PORTRAIT_WHITE || this == BoardOrientation.PORTRAIT_BLACK

/**
 * Cycles to the next orientation in clockwise order.
 *
 * The cycle models the direction in which the human player's pieces advance,
 * visualised as a compass arrow that rotates 90° clockwise on each tap:
 *
 *   PORTRAIT_WHITE  → LANDSCAPE_WHITE → PORTRAIT_BLACK → LANDSCAPE_BLACK → (repeat)
 *   ▲                  ►                 ▼                 ◄
 */
fun BoardOrientation.rotateCW(): BoardOrientation = when (this) {
    BoardOrientation.PORTRAIT_WHITE -> BoardOrientation.LANDSCAPE_WHITE
    BoardOrientation.LANDSCAPE_WHITE -> BoardOrientation.PORTRAIT_BLACK
    BoardOrientation.PORTRAIT_BLACK -> BoardOrientation.LANDSCAPE_BLACK
    BoardOrientation.LANDSCAPE_BLACK -> BoardOrientation.PORTRAIT_WHITE
}

fun toBoardOrientation(
    landscape: Boolean,
    playerSide: CobColor,
): BoardOrientation =
    when {
        landscape && playerSide == CobColor.BLACK -> BoardOrientation.LANDSCAPE_BLACK
        landscape && playerSide == CobColor.WHITE -> BoardOrientation.LANDSCAPE_WHITE
        !landscape && playerSide == CobColor.BLACK -> BoardOrientation.PORTRAIT_BLACK
        else -> BoardOrientation.PORTRAIT_WHITE
    }