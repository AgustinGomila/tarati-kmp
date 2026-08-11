package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.groupByTurns
import com.agustin.tarati.core.domain.game.play.moveIndexToGroupIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [groupByTurns]: the move-history grouping that keeps White/Black columns aligned even
 * when a forced promotion makes the same player play two consecutive half-moves (promote the cob,
 * then move the new rok) within a single turn.
 *
 * The reference game is a real casual 3+2 vs bot_teo where White (Teo) had two forced promotions.
 * Confirms the renderer itself aligns correctly — the misalignment seen in that game came from a
 * polluted local move list, not from this grouping.
 */
class TurnGroupTest {

    /** Real game (agustin vs bot_teo). Teo (White) promotes at moves B6=R and B2=R. */
    private val realGame = Move.parseMoveHistory(
        "C2-B1,C7-C6,D2-C2,D3-C7,C1-C12,C8-B4,C12-C11,B4-A1,D1-C1,D4-C8,C11-C10,C8-C9," +
                "C1-C12,A1-B6,C2-C1,C10-C11,B1-A1,C9-B5,C1-B1,C7-B4,B1-B2,C6-B3," +
                "B6-B6,B6-B1,B5-B6,B2-B2,B2-C4,B3-B2",
    )

    @Test
    fun `strict alternation with no promotions produces one group per move`() {
        val moves = Move.parseMoveHistory("C2-B1,C7-C6,D2-C2,D3-C7")
        val groups = moves.groupByTurns()
        assertEquals(4, groups.size)
        assertEquals(listOf(WHITE, BLACK, WHITE, BLACK), groups.map { it.color })
        assertTrue(groups.all { it.moves.size == 1 })
    }

    @Test
    fun `a forced promotion and its follow-up move form a single turn`() {
        val groups = realGame.groupByTurns()

        // 28 moves, two 2-move promotion turns → 26 turn groups.
        assertEquals(26, groups.size)

        // Colors still alternate one flip per turn (a promotion turn is a single turn).
        groups.forEachIndexed { i, group ->
            assertEquals(if (i % 2 == 0) WHITE else BLACK, group.color, "group $i color")
        }

        // Exactly the two White (Teo) forced promotions, each = [promotion, rok move].
        val promotions = groups.filter { it.hasPromotion }
        assertEquals(2, promotions.size)
        promotions.forEach {
            assertEquals(WHITE, it.color)
            assertEquals(2, it.moves.size)
        }
        assertEquals("B6=R", promotions[0].moves[0].name)
        assertEquals("B6-B1", promotions[0].moves[1].name)
        assertEquals("B2=R", promotions[1].moves[0].name)
        assertEquals("B2-C4", promotions[1].moves[1].name)
    }

    @Test
    fun `the rok move after a promotion stays in the promoting player's column`() {
        val groups = realGame.groupByTurns()
        // The promotion turns are at group indices 22 and 24 — both even → White column.
        // (If the rok move leaked to the next turn, one of these would be a lone Black group.)
        assertTrue(groups[22].hasPromotion)
        assertEquals(WHITE, groups[22].color)
        assertTrue(groups[24].hasPromotion)
        assertEquals(WHITE, groups[24].color)
        // The group right after the first promotion is Black's normal reply, a single move.
        assertEquals(BLACK, groups[23].color)
        assertFalse(groups[23].hasPromotion)
        assertEquals("B5-B6", groups[23].moves.single().name)
    }

    @Test
    fun `moveIndexToGroupIndex maps both promotion half-moves to the same group`() {
        // Flat indices: 22 = B6-B6 (promotion), 23 = B6-B1 (rok move) → same group (22).
        assertEquals(22, realGame.moveIndexToGroupIndex(22))
        assertEquals(22, realGame.moveIndexToGroupIndex(23))
        // The move just before (21 = C6-B3, Black) is its own group.
        assertEquals(21, realGame.moveIndexToGroupIndex(21))
        // Out of range.
        assertEquals(-1, realGame.moveIndexToGroupIndex(28))
    }
}
