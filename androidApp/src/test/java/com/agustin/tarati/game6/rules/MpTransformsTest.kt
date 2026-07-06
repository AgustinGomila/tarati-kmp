package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.core.domain.game6.rules.MpTransforms
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de la rotación de perspectiva del estado multijugador (D13). */
class MpTransformsTest {

    @Test
    fun rotate60_preservesPieceOwnersAndCount() {
        val state = MpSetup.initialState(6)
        val rotated = MpTransforms.rotate60(state)
        assertEquals(state.pieces.size, rotated.pieces.size)
        // Mismos dueños (multiset), solo cambian las posiciones.
        assertEquals(
            state.pieces.values.groupingBy { it.owner }.eachCount(),
            rotated.pieces.values.groupingBy { it.owner }.eachCount(),
        )
    }

    @Test
    fun rotate60_mapsEachPieceToRotatedVertex() {
        val state = MpSetup.initialState(4)
        val rotated = MpTransforms.rotate60(state)
        state.pieces.forEach { (vertex, piece) ->
            val target = Board25.rotate60(vertex)
            assertEquals("Pieza de ${vertex.name} → ${target.name}", piece, rotated.pieces[target])
        }
    }

    @Test
    fun rotate60_rotatesSeatBasesConsistently() {
        val state = MpSetup.initialState(6)
        val rotated = MpTransforms.rotate60(state)
        state.seats.forEachIndexed { i, seat ->
            assertEquals(Board25.rotatedBaseId(seat.baseId), rotated.seats[i].baseId)
        }
    }

    @Test
    fun rotate60_preservesTurnMoveCountAndResult() {
        val state = MpSetup.initialState(3)
        val rotated = MpTransforms.rotate60(state)
        assertEquals(state.currentSeatIndex, rotated.currentSeatIndex)
        assertEquals(state.moveCount, rotated.moveCount)
        assertEquals(state.result, rotated.result)
    }

    @Test
    fun sixRotations_returnToOriginal() {
        val state = MpSetup.initialState(6)
        var s = state
        repeat(6) { s = MpTransforms.rotate60(s) }
        assertEquals("R^6 = identidad sobre el estado", state, s)
    }

    @Test
    fun rotate60_keepsSeatPiecesOnTheirRotatedBaseSquare() {
        // Tras rotar, las piezas de cada asiento siguen en el cuadrado de su base (ya rotada) → la
        // regla de salida de base de MpRules sigue siendo coherente con la posición de las piezas.
        val rotated = MpTransforms.rotate60(MpSetup.initialState(6))
        rotated.seats.forEach { seat ->
            Board25.baseById(seat.baseId).startSquare.forEach { vertex ->
                assertEquals("Base ${seat.baseId}: ${vertex.name}", seat.color, rotated.pieces[vertex]?.owner)
            }
        }
    }
}
