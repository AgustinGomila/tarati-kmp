package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.Move.Companion.parseMoveHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Move.name] / [Move.Companion.parseMoveHistory] round-trips.
 *
 * The parser must accept every token [Move.name] can produce: normal moves
 * ("C1-B1"), in-place promotions ("C12=R") and the legacy "→" separator kept
 * for games saved before the ASCII change.
 */
class MoveNotationTest {

    @Test
    fun `normal moves round-trip through name and parseMoveHistory`() {
        val moves = listOf(Move(C1 to B1), Move(C7 to B1))

        val serialized = moves.joinToString(",") { it.name }

        assertEquals("C1-B1,C7-B1", serialized)
        assertEquals(moves, parseMoveHistory(serialized))
    }

    @Test
    fun `promotion token round-trips through name and parseMoveHistory`() {
        val moves = listOf(Move(C1 to B1), Move(C12 to C12)) // second: in-place promotion

        val serialized = moves.joinToString(",") { it.name }

        assertEquals("C1-B1,C12=R", serialized)
        val parsed = parseMoveHistory(serialized)
        assertEquals(moves, parsed)
        assertTrue("Parsed promotion token must be a promotion move", parsed[1].isPromotion())
    }

    @Test
    fun `legacy arrow separator is still accepted`() {
        assertEquals(listOf(Move(C1 to B1)), parseMoveHistory("C1→B1"))
    }

    @Test
    fun `empty history parses to empty list`() {
        assertEquals(emptyList<Move>(), parseMoveHistory(""))
    }
}
