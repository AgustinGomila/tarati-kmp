package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la construcción del estado inicial (MpSetup) — §2.1 del plan. */
class MpSetupTest {

    @Test
    fun sixPlayers_useAllBasesWith24Pieces() {
        val state = MpSetup.initialState(6)
        assertEquals(6, state.seats.size)
        assertEquals(6 * 4, state.pieces.size)
        assertTrue("Todos los asientos activos", state.seats.all { it.status == SeatStatus.ACTIVE })
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
                assertEquals("Base ${seat.baseId}: ${vertex.name} es de ${seat.color}", seat.color, piece?.owner)
                assertEquals("Piezas iniciales no salieron de base", false, piece?.hasLeftBase)
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
            assertEquals("k=$k: $k índices", k, indices.size)
            assertEquals("k=$k: índices distintos", k, indices.toSet().size)
            assertTrue("k=$k: índices en rango", indices.all { it in 0..5 })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fewerThanTwoPlayers_throws() {
        MpSetup.initialState(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreThanSixPlayers_throws() {
        MpSetup.initialState(7)
    }
}
