package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpMove
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

    // ── Rotación por N sextantes y perspectiva por-cliente (orientación online) ────────────────

    @Test
    fun rotateN_equalsRepeatedRotate60() {
        val state = MpSetup.initialState(6)
        var manual = state
        repeat(3) { manual = MpTransforms.rotate60(manual) }
        assertEquals(manual, MpTransforms.rotate(state, 3))
    }

    @Test
    fun rotateN_normalizesTimes() {
        val state = MpSetup.initialState(5)
        assertEquals("6 giros = identidad", state, MpTransforms.rotate(state, 6))
        // Un giro negativo equivale a 5 positivos (inverso de la simetría de orden 6).
        assertEquals(MpTransforms.rotate(state, 5), MpTransforms.rotate(state, -1))
    }

    @Test
    fun rotateVertex_isInvertibleWithNegativeTimes() {
        // Des-rotar (−r) deshace rotar (r): el ida-y-vuelta del envío de jugadas online.
        val v = Vertex.parseVertex("D1")
        for (r in 0..5) {
            assertEquals(v, MpTransforms.rotate(MpTransforms.rotate(v, r), -r))
        }
    }

    @Test
    fun rotateMove_rotatesBothEndpoints() {
        val move = MpMove(Vertex.parseVertex("D1"), Vertex.parseVertex("C1"))
        val rotated = MpTransforms.rotate(move, 2)
        assertEquals(MpTransforms.rotate(move.from, 2), rotated.from)
        assertEquals(MpTransforms.rotate(move.to, 2), rotated.to)
    }

    @Test
    fun rotationToBottom_bringsEachSeatBaseToSouth() {
        // La base Sur (abajo) es el índice 0 (base 17). Para cada color, rotar el estado por
        // rotationToBottom debe dejar su base en el índice 0.
        val state = MpSetup.initialState(6)
        val southBaseId = Board25.bases[0].id
        state.seats.forEach { seat ->
            val r = MpTransforms.rotationToBottom(state, seat.color)
            val rotated = MpTransforms.rotate(state, r)
            val myBaseId = rotated.seats.first { it.color == seat.color }.baseId
            assertEquals("Color ${seat.color} debería quedar al Sur", southBaseId, myBaseId)
        }
    }

    @Test
    fun rotationToBottom_isZeroForFirstSeatAndForNullColor() {
        val state = MpSetup.initialState(4)
        // El primer asiento ya arranca al Sur → sin rotación.
        assertEquals(0, MpTransforms.rotationToBottom(state, state.seats.first().color))
        // Espectador (sin color) → sin rotación.
        assertEquals(0, MpTransforms.rotationToBottom(state, null))
        // Color que no está sentado en la mesa (2 jugadores) → sin rotación.
        assertEquals(0, MpTransforms.rotationToBottom(MpSetup.initialState(2), PlayerColor.P4))
    }
}
