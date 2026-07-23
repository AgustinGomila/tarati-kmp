package com.agustin.tarati.core.domain.game6.play

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.Vertex.Companion.parseVertex
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpNotation.serializeHistory
import com.agustin.tarati.core.domain.game6.play.MpNotation.serializeMove

/**
 * Notación textual del juego multijugador (tablero `25`) — §10 del plan.
 *
 * Diseñada para preservar la **esencia** de la notación de Tarati (juego 1), adaptándola a
 * 2–6 jugadores sin colores binarios `w`/`b` ni promoción (roks):
 *
 * - **Jugador**: letra minúscula `a..f` (`P1 → a … P6 → f`), igual que single usa `w`/`b` para los
 *   cobs. (La etiqueta de UI del jugador sigue en mayúscula vía `PlayerColor.letter`; la minúscula
 *   es solo del texto de la notación.)
 * - **Movimiento**: `from-to` (p.ej. `D1-C1`). En la lista de la UI el jugador lo da la columna,
 *   así que el token va sin prefijo; en la serialización se antepone el jugador (`a:D1-C1`).
 * - **Posición (FEN)**: piezas `vértice+letraJugador` unidas por `/`, luego ` ` + letra de turno.
 *   La letra del jugador va en **minúscula** como nombre canónico (igual que single usa `w`/`b`),
 *   y el **casing** codifica un estado de la pieza (igual que single lo usa para cob/rok): **MAYÚSCULA
 *   = aún en base** (movimiento restringido), **minúscula = ya salió de base** (libre). La letra de
 *   turno va siempre en minúscula (identidad del jugador, sin estado).
 *
 * Orden canónico de las piezas en la FEN: por **jugador** (`A..F`), luego **zona** del vértice
 * (`A < B < C < D < E`), luego **índice** del vértice — cada jugador queda en un bloque contiguo.
 */
object MpNotation {

    const val PIECE_DELIMITER: Char = '/'
    const val TURN_DELIMITER: Char = ' '
    const val MOVE_SEPARATOR: String = "-"
    const val MOVE_PLAYER_SEPARATOR: Char = ':'
    const val HISTORY_SEPARATOR: String = ","

    // ── Posición (FEN) ──────────────────────────────────────────────────────────

    /**
     * FEN de [state]: bloques por jugador (`a..f`), dentro de cada uno por zona e índice de
     * vértice, con el casing de la letra codificando `hasLeftBase`; al final ` ` + letra de turno.
     */
    fun toPositionNotation(state: MpGameState): String =
        buildString {
            state.pieces.entries
                .sortedWith(
                    compareBy({ it.value.owner.ordinal }, { it.key.zone.name }, { it.key.position }),
                )
                .forEachIndexed { i, (vertex, piece) ->
                    if (i > 0) append(PIECE_DELIMITER)
                    append(vertex.name)
                    val letter = piece.owner.letter.lowercaseChar()
                    append(if (piece.hasLeftBase) letter else letter.uppercaseChar())
                }
            append(TURN_DELIMITER)
            append(state.currentSeat.color.letter.lowercaseChar())
        }

    /**
     * Reconstruye las piezas y el jugador de turno de una FEN [notation]. No reconstruye los
     * asientos (los retirados no tienen piezas en el tablero); es suficiente para el editor y
     * los tests de ida y vuelta del mapa de piezas.
     */
    fun parsePosition(notation: String): ParsedPosition {
        val parts = notation.split(TURN_DELIMITER)
        require(parts.size == 2) {
            "FEN inválida: debe tener piezas y turno separados por espacio"
        }
        val turn = PlayerColor.fromLetter(parts[1].trim().first())

        val pieces = mutableMapOf<Vertex, Piece>()
        val piecesPart = parts[0]
        if (piecesPart.isNotEmpty()) {
            for (entry in piecesPart.split(PIECE_DELIMITER)) {
                if (entry.length < 3) continue // mínimo "A1A"
                val ownerChar = entry.last()
                val vertex = parseVertex(entry.dropLast(1).uppercase())
                pieces[vertex] = Piece(
                    owner = PlayerColor.fromLetter(ownerChar),
                    hasLeftBase = ownerChar.isLowerCase(),
                )
            }
        }
        return ParsedPosition(pieces = pieces, turn = turn)
    }

    // ── Movimientos ─────────────────────────────────────────────────────────────

    /** Serializa un movimiento con el jugador que lo hace: `a:D1-C1`. */
    fun serializeMove(color: PlayerColor, move: MpMove): String =
        "${color.letter.lowercaseChar()}$MOVE_PLAYER_SEPARATOR${move.from.name}$MOVE_SEPARATOR${move.to.name}"

    /** Inverso de [serializeMove]. */
    fun parseMove(token: String): PlayerMove {
        val sep = token.indexOf(MOVE_PLAYER_SEPARATOR)
        require(sep == 1) { "Movimiento inválido (falta prefijo de jugador): $token" }
        val color = PlayerColor.fromLetter(token[0])
        val vertices = token.substring(sep + 1).split(MOVE_SEPARATOR)
        require(vertices.size == 2) { "Movimiento inválido: $token" }
        return PlayerMove(color, MpMove(parseVertex(vertices[0]), parseVertex(vertices[1])))
    }

    /** Serializa un historial completo, movimientos separados por comas. */
    fun serializeHistory(moves: List<PlayerMove>): String =
        moves.joinToString(HISTORY_SEPARATOR) { serializeMove(it.color, it.move) }

    /** Inverso de [serializeHistory]. */
    fun parseHistory(history: String): List<PlayerMove> =
        if (history.isEmpty()) emptyList()
        else history.split(HISTORY_SEPARATOR).map { parseMove(it) }
}

/** Piezas y jugador de turno reconstruidos de una FEN por [MpNotation.parsePosition]. */
data class ParsedPosition(
    val pieces: Map<Vertex, Piece>,
    val turn: PlayerColor,
)

/** Un movimiento junto al jugador que lo realiza, para serializar/parsear historiales. */
data class PlayerMove(
    val color: PlayerColor,
    val move: MpMove,
)

/** Extensión de conveniencia con paridad de call-site con `GameState.toPositionNotation()`. */
fun MpGameState.toPositionNotation(): String = MpNotation.toPositionNotation(this)
