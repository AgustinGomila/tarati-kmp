package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.ai.book.OpeningBook
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the compiled [OpeningBook] over the committed [OPENING_BOOK_ENTRIES].
 *
 * Verify the book is loaded, that lookups resolve to legal moves in the real orientation, that
 * off-book positions fall through, and —crucially— that mirror positions yield mirror-image
 * recommendations (the canonicalization round-trip).
 */
class OpeningBookTest {

    @Test
    fun `the book is compiled and non-empty`() {
        assertTrue(OpeningBook.size > 100, "OPENING_BOOK_ENTRIES should be populated")
    }

    @Test
    fun `the initial position resolves to a legal booked move`() {
        val initial = initialGameState()
        val move = OpeningBook.lookup(initial)
        assertNotNull(move, "The initial position must be in the book")
        assertTrue(move in initial.allMovesForTurn(), "The book move must be legal")
    }

    @Test
    fun `an off-book position returns null`() {
        // A sparse hand-built position that cannot be a real opening after 10 plies.
        val offBook = GameState(cobs = mapOf(B1 to Cob(WHITE), B4 to Cob(BLACK)), currentTurn = WHITE)
        assertNull(OpeningBook.lookup(offBook))
    }

    @Test
    fun `mirror positions yield mirror-image book moves`() {
        // 1.C1-C12 is the most-played white opening; play it and its mirror from the (self-symmetric)
        // initial position. One orientation is the canonical representative, the other is its mirror,
        // so this exercises both branches of the lookup's canonicalization.
        val afterMainLine = initialGameState().applyMove(Move(C1 to C12))
        val afterMirrorLine = initialGameState().applyMove(Move(C1 to C12).mirrored())

        val reply = OpeningBook.lookup(afterMainLine)
        val mirrorReply = OpeningBook.lookup(afterMirrorLine)

        assertNotNull(reply, "Black's reply after 1.C1-C12 should be booked")
        assertNotNull(mirrorReply, "Black's reply after the mirror of 1.C1-C12 should be booked")
        assertEquals(
            reply.mirrored(),
            mirrorReply,
            "Mirror positions must recommend mirror-image moves",
        )
        // Both recommended replies must be legal in their own orientation.
        assertTrue(reply in afterMainLine.allMovesForTurn())
        assertTrue(mirrorReply in afterMirrorLine.allMovesForTurn())
    }

    @Test
    fun `lookup is deterministic`() {
        val initial = initialGameState()
        assertEquals(OpeningBook.lookup(initial), OpeningBook.lookup(initial))
    }
}
