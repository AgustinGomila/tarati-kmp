package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests de la construcción del estado inicial (MpSetup) — §2.1 del plan. */
class MpSetupTest {

    @Test
    fun sixPlayers_useAllBasesWith24Pieces() {
        val state = MpSetup.initialState(6)
        assertEquals(6, state.seats.size)
        assertEquals(6 * 4, state.pieces.size)
        assertTrue(state.seats.all { it.status == SeatStatus.ACTIVE }, "Todos los asientos activos")
        assertEquals(0, state.currentSeatIndex)
        assertEquals(setOf(17, 18, 19, 20, 21, 22), state.seats.map { it.baseId }.toSet())
    }

    @Test
    fun eachSeat_hasFourPiecesOnItsBaseSquare() {
        val state = MpSetup.initialState(6)
        state.seats.forEach { seat ->
            val square = Board25.baseById(seat.baseId).startSquare
            square.forEach { vertex ->
                val piece = state.pieces[vertex]
                assertEquals(seat.color, piece?.owner, "Base ${seat.baseId}: ${vertex.name} es de ${seat.color}")
                assertEquals(false, piece?.hasLeftBase, "Piezas iniciales no salieron de base")
            }
        }
    }

    @Test
    fun colorsAreAssignedInOrder() {
        val state = MpSetup.initialState(3)
        assertEquals(
            listOf(PlayerColor.P1, PlayerColor.P2, PlayerColor.P3),
            state.seats.map { it.color },
        )
    }

    @Test
    fun twoPlayers_useOppositeBases() {
        // Índices 0 y 3 del anillo → bases 17 y 20 (opuestas).
        assertEquals(listOf(0, 3), MpSetup.selectBaseIndices(2))
        val state = MpSetup.initialState(2)
        assertEquals(listOf(17, 20), state.seats.map { it.baseId })
        assertEquals(2 * 4, state.pieces.size)
    }

    @Test
    fun threePlayers_useAlternatingBases() {
        assertEquals(listOf(0, 2, 4), MpSetup.selectBaseIndices(3))
        assertEquals(listOf(17, 21, 19), MpSetup.initialState(3).seats.map { it.baseId })
    }

    @Test
    fun selectedBases_areAlwaysDistinct() {
        (2..6).forEach { k ->
            val indices = MpSetup.selectBaseIndices(k)
            assertEquals(k, indices.size, "k=$k: $k índices")
            assertEquals(k, indices.toSet().size, "k=$k: índices distintos")
            assertTrue(indices.all { it in 0..5 }, "k=$k: índices en rango")
        }
    }

    @Test
    fun fewerThanTwoPlayers_throws() {
        assertFailsWith<IllegalArgumentException> { MpSetup.initialState(1) }
    }

    @Test
    fun moreThanSixPlayers_throws() {
        assertFailsWith<IllegalArgumentException> { MpSetup.initialState(7) }
    }
}
