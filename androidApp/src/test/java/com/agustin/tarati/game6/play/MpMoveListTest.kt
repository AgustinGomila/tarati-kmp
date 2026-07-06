package com.agustin.tarati.game6.play

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.Zone
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpMoveCell
import com.agustin.tarati.core.domain.game6.play.MpMoveList
import com.agustin.tarati.core.domain.game6.play.MpMoveRow
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests del armado de la lista de movimientos columnar (§D7 del plan). */
class MpMoveListTest {

    private val dummy = MpMove(Vertex(Zone('D'), 1), Vertex(Zone('C'), 1))

    private fun mv(color: PlayerColor) = PlayerMove(color, dummy)

    private fun seats(n: Int, retired: Set<PlayerColor> = emptySet()): List<Seat> =
        (0 until n).map {
            val color = PlayerColor.fromIndex(it)
            Seat(color, baseId = 17 + it, status = if (color in retired) SeatStatus.RETIRED else SeatStatus.ACTIVE)
        }

    private val A = PlayerColor.P1
    private val B = PlayerColor.P2
    private val C = PlayerColor.P3

    @Test
    fun emptyHistory_returnsNoRows() {
        assertEquals(emptyList<MpMoveRow>(), MpMoveList.build(emptyList(), seats(6)))
    }

    @Test
    fun singleRound_sixPlayers_isOneRowAllPlayed() {
        val history = (0 until 6).map { mv(PlayerColor.fromIndex(it)) }
        val rows = MpMoveList.build(history, seats(6))
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].number)
        assertTrue(rows[0].cells.all { it is MpMoveCell.Played })
    }

    @Test
    fun repeatedPlayer_opensNewRow() {
        val history = listOf(mv(A), mv(B), mv(A), mv(B))
        val rows = MpMoveList.build(history, seats(2))
        assertEquals(2, rows.size)
        assertEquals(listOf(1, 2), rows.map { it.number })
        rows.forEach { row -> assertTrue(row.cells.all { it is MpMoveCell.Played }) }
    }

    @Test
    fun columnMatchesSeatOrder() {
        // Solo mueve C (columna 2); A y B quedan pendientes en la ronda en curso.
        val rows = MpMoveList.build(listOf(mv(C)), seats(3))
        assertEquals(1, rows.size)
        val cells = rows[0].cells
        assertTrue(cells[0] is MpMoveCell.Empty)
        assertTrue(cells[1] is MpMoveCell.Empty)
        assertTrue(cells[2] is MpMoveCell.Played)
    }

    @Test
    fun pendingCell_inLastRow_isEmptyNotDash() {
        // A movió, B aún no (activo) → celda de B en blanco, no "-".
        val rows = MpMoveList.build(listOf(mv(A)), seats(2))
        assertTrue(rows[0].cells[1] is MpMoveCell.Empty)
    }

    @Test
    fun retiredPlayer_showsDashFromRetirementOnward() {
        // C juega la ronda 1 y luego se retira; ronda 2 solo A y B.
        val history = listOf(mv(A), mv(B), mv(C), mv(A), mv(B))
        val rows = MpMoveList.build(history, seats(3, retired = setOf(C)))
        assertEquals(2, rows.size)
        // Ronda 1: C jugó.
        assertTrue(rows[0].cells[2] is MpMoveCell.Played)
        // Ronda 2: C retirado → "-".
        assertTrue(rows[1].cells[2] is MpMoveCell.Retired)
    }

    @Test
    fun retiredWithoutMoving_showsDashInEveryRow() {
        // C se retira por inmovilización sin mover: nunca aparece en el historial.
        val history = listOf(mv(A), mv(B), mv(A), mv(B))
        val rows = MpMoveList.build(history, seats(3, retired = setOf(C)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.cells[2] is MpMoveCell.Retired })
    }
}
