package com.agustin.tarati.core.domain.game.pieces

import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.MatchResult
import com.agustin.tarati.core.domain.game.play.MatchResult.BLACK_WON
import com.agustin.tarati.core.domain.game.play.MatchResult.WHITE_WON
import kotlinx.serialization.Serializable

/**
 * Color de un bando. [letter] es la letra minúscula canónica usada en la
 * notación de posición (`w`/`b`) — mismo patrón que `PlayerColor` en game6.
 */
@Serializable
enum class CobColor(val letter: Char) {
    WHITE('w'),
    BLACK('b');

    companion object {
        /** Color cuya letra canónica es [letter], o `null` si no corresponde a ninguno. */
        fun fromLetter(letter: Char): CobColor? = entries.firstOrNull { it.letter == letter }
    }
}

val CobColor.opponent: CobColor get() = if (this == BLACK) WHITE else BLACK

/** Nombre canónico en minúsculas (`"white"`/`"black"`), usado en DTOs del protocolo online. */
val CobColor.description: String
    get() = name.lowercase()

fun cobColorByDescription(description: String): CobColor? =
    when (description.lowercase()) {
        "white" -> WHITE
        "black" -> BLACK
        else -> null
    }

fun CobColor.isMaximizingPlayer(): Boolean = this == WHITE

/**
 * Flips all opponent pieces adjacent to [to] that were NOT already adjacent to [from]
 * before the move (pre-adjacency rule: a piece cannot be captured by a piece that was
 * already adjacent to it before the move).
 *
 * Captured pieces are flipped to the new color but are never auto-promoted — promotion
 * only occurs when a cob is actively advanced onto an upgrade vertex via a move.
 */
fun CobColor.flipAdjacentCobs(
    mutable: MutableMap<Vertex, Cob>,
    to: Vertex,
    from: Vertex,
) {
    // Vecinos directos del origen y del destino (grado ≤ 6 en este tablero).
    val originAdjacents = adjacencyMap[from].orEmpty()
    adjacencyMap[to].orEmpty().forEach { adjacent ->
        if (adjacent !in originAdjacents) {
            mutable[adjacent]?.takeIf { it.color != this }?.let { adjCob ->
                mutable[adjacent] = adjCob.copy(color = this)
            }
        }
    }
}

fun CobColor.getMatchResult(): MatchResult =
    when (this) {
        WHITE -> WHITE_WON
        BLACK -> BLACK_WON
    }