package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.rules.MpTransforms.rotate60

/**
 * Transformaciones de simetría sobre el estado multijugador.
 *
 * [rotate60] rota el estado un sextante (60°) reutilizando la simetría del tablero `25`
 * ([Board25.rotate60]): re-mapea las posiciones de las piezas y la base de cada asiento de forma
 * consistente (una pieza que aún no salió de su base sigue asociada a su base, ahora rotada, para
 * que la regla de salida de [MpRules] no cambie). Como el tablero es 60°-simétrico, el resultado es
 * visualmente idéntico salvo por el color de las piezas (cada jugador "gira" a la base contigua). El
 * orden de turno, el contador de jugadas y el resultado se preservan: es una isometría del grafo, así
 * que la partida sigue siendo la misma — solo cambia la perspectiva.
 */
object MpTransforms {

    /** Cantidad de sextantes de un giro completo (6 bases). */
    private const val SEXTANTS = 6

    /** Rota el estado 60° (piezas + bases de los asientos). */
    fun rotate60(state: MpGameState): MpGameState = state.copy(
        pieces = state.pieces.mapKeys { (vertex, _) -> Board25.rotate60(vertex) },
        seats = state.seats.map { it.copy(baseId = Board25.rotatedBaseId(it.baseId)) },
    )

    /** Normaliza [times] al rango `0..5` (acepta valores negativos). */
    private fun norm(times: Int): Int = ((times % SEXTANTS) + SEXTANTS) % SEXTANTS

    /** Rota el estado [times] sextantes (60° cada uno). `times` negativo gira en sentido inverso. */
    fun rotate(state: MpGameState, times: Int): MpGameState {
        var s = state
        repeat(norm(times)) { s = rotate60(s) }
        return s
    }

    /** Rota un vértice [times] sextantes. */
    fun rotate(vertex: Vertex, times: Int): Vertex {
        var v = vertex
        repeat(norm(times)) { v = Board25.rotate60(v) }
        return v
    }

    /** Rota un movimiento [times] sextantes (ambos extremos). */
    fun rotate(move: MpMove, times: Int): MpMove =
        MpMove(rotate(move.from, times), rotate(move.to, times))

    /** Rota un movimiento con jugador [times] sextantes (el color no cambia). */
    fun rotate(playerMove: PlayerMove, times: Int): PlayerMove =
        playerMove.copy(move = rotate(playerMove.move, times))

    /**
     * Sextantes a rotar para llevar la base del asiento de [color] al **Sur** (base índice 0, la
     * orientación "abajo" del tablero) — usado para la perspectiva por-cliente del juego online. Cada
     * [rotate60] mueve una base del índice `i` al `i+1`, así que para llevar el índice `b` al `0` se
     * rota `(6 − b) % 6`. Devuelve `0` si [color] es nulo o su asiento no está en el estado.
     */
    fun rotationToBottom(state: MpGameState, color: PlayerColor?): Int {
        if (color == null) return 0
        val baseId = state.seats.firstOrNull { it.color == color }?.baseId ?: return 0
        val baseIndex = Board25.bases.indexOfFirst { it.id == baseId }
        if (baseIndex < 0) return 0
        return norm(SEXTANTS - baseIndex)
    }
}
