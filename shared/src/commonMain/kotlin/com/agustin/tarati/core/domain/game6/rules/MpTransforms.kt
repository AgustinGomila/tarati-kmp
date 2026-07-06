package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.play.MpGameState
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

    /** Rota el estado 60° (piezas + bases de los asientos). */
    fun rotate60(state: MpGameState): MpGameState = state.copy(
        pieces = state.pieces.mapKeys { (vertex, _) -> Board25.rotate60(vertex) },
        seats = state.seats.map { it.copy(baseId = Board25.rotatedBaseId(it.baseId)) },
    )
}
