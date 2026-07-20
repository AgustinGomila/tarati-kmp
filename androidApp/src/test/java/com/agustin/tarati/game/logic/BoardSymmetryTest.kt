package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.BoardSymmetry
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.vertices
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BoardSymmetry] and the canonicalization helpers on GameState/Move.
 *
 * The Tarati board is bilaterally symmetric across the vertical axis joining both bases (unlike
 * chess, whose king/queen break it). These tests verify the mirror permutation is a well-formed
 * involution that preserves color and turn, and that (position, move) canonicalization folds mirror
 * pairs into a single representative.
 */
class BoardSymmetryTest {

    // ── Mirror permutation ───────────────────────────────────────────────────

    @Test
    fun `mirror is an involution over every vertex`() {
        for (v in vertices) {
            assertEquals("mirror(mirror($v)) must be $v", v, BoardSymmetry.mirror(BoardSymmetry.mirror(v)))
        }
    }

    @Test
    fun `mirror is a bijection over the board`() {
        val images = vertices.map { BoardSymmetry.mirror(it) }.toSet()
        assertEquals("mirror must map the 23 vertices onto themselves", vertices.toSet(), images)
    }

    @Test
    fun `the center vertex is fixed by the mirror`() {
        assertEquals(A1, BoardSymmetry.mirror(A1))
    }

    @Test
    fun `mirror swaps the white base circumference vertices`() {
        // C1 and C2 are white's two circumference base vertices, symmetric about the axis.
        assertEquals(C2, BoardSymmetry.mirror(C1))
        assertEquals(C1, BoardSymmetry.mirror(C2))
    }

    @Test
    fun `mirror preserves piece color (home base maps to itself)`() {
        for ((color, base) in GameBoard.homeBases) {
            for (vertex in base) {
                assertTrue(
                    "mirror of a $color home vertex must stay in $color's base",
                    BoardSymmetry.mirror(vertex) in base,
                )
            }
        }
    }

    // ── Canonicalization ─────────────────────────────────────────────────────

    @Test
    fun `the initial position is self-symmetric`() {
        val initial = initialGameState()
        assertEquals(
            "The initial position equals its own mirror",
            initial.hashBoard(),
            initial.mirroredHashBoard(),
        )
        assertEquals(initial.hashBoard(), initial.canonicalHash())
    }

    @Test
    fun `a move and its mirror canonicalize to the same representative`() {
        val initial = initialGameState()
        val move = Move(C2 to C3)
        val mirrored = move.mirrored()
        assertNotEquals("C2-C3 and its mirror must be distinct moves", move, mirrored)

        val a = initial.canonicalMove(move)
        val b = initial.canonicalMove(mirrored)
        assertEquals("Mirror moves must share the canonical hash", a.first, b.first)
        assertEquals("Mirror moves must fold to the same canonical move", a.second, b.second)
    }

    @Test
    fun `canonicalHash is stable under mirroring for an asymmetric position`() {
        // After white plays C2->C3 the position is no longer self-symmetric, yet the position and
        // its mirror must yield the same canonical hash.
        val state = initialGameState().applyMove(Move(C2 to C3))
        assertNotEquals(state.hashBoard(), state.mirroredHashBoard())

        // Build the mirror by replaying the mirrored move from the (self-symmetric) initial position.
        val mirrorState = initialGameState().applyMove(Move(C2 to C3).mirrored())
        assertEquals(
            "A position and its mirror must share the canonical hash",
            state.canonicalHash(),
            mirrorState.canonicalHash(),
        )
    }

    @Test
    fun `mirror of a promotion stays a promotion`() {
        val promotion = Move(B1 to B1) // from == to
        assertTrue(promotion.mirrored().isPromotion())
    }
}
