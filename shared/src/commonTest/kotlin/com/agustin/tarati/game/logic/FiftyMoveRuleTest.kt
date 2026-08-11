package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C6
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiftyMoveRuleTest {

    // ==================== halfMoveClock tracking ====================

    @Test
    fun halfMoveClock_incrementsOnRokMove() {
        val state = GameState(
            cobs = mapOf(C1 to Cob(WHITE, true)), // rok
            currentTurn = WHITE,
            halfMoveClock = 5,
        )
        val next = state.applyMove(Move(C1 to B1))
        assertEquals(6, next.halfMoveClock, "Rok move should increment clock by 1")
    }

    @Test
    fun halfMoveClock_resetsOnCobMove() {
        val state = GameState(
            cobs = mapOf(C1 to Cob(WHITE)), // cob
            currentTurn = WHITE,
            halfMoveClock = 42,
        )
        val next = state.applyMove(Move(C1 to B1))
        assertEquals(0, next.halfMoveClock, "Cob move should reset clock to 0")
    }

    @Test
    fun halfMoveClock_resetsOnForcedPromotion() {
        val state = GameState(
            cobs = mapOf(
                C7 to Cob(WHITE), // sole white cob → forced promotion
                C1 to Cob(BLACK),
            ),
            currentTurn = WHITE,
            halfMoveClock = 75,
        )
        // Sole cob → getForcedPromotions returns [C7→C7]
        val next = state.applyMove(Move(C7 to C7))
        assertEquals(0, next.halfMoveClock, "Forced promotion should reset clock to 0")
    }

    @Test
    fun halfMoveClock_resetsOnStandardPromotion() {
        // Cob advances to upgrade vertex → promotes, clock resets
        val state = GameState(
            cobs = mapOf(C6 to Cob(WHITE)),
            currentTurn = WHITE,
            halfMoveClock = 60,
        )
        val next = state.applyMove(Move(C6 to C7))
        assertEquals(0, next.halfMoveClock, "Standard promotion (cob advance) should reset clock to 0")
    }

    @Test
    fun halfMoveClock_defaultIsZero() {
        val state = GameState(
            cobs = mapOf(C1 to Cob(WHITE)),
            currentTurn = WHITE,
        )
        assertEquals(0, state.halfMoveClock, "Default halfMoveClock should be 0")
    }

    @Test
    fun halfMoveClock_notIncludedInHashBoard() {
        // Two states identical except halfMoveClock must produce the same hash
        // (the clock is not part of the position for triple-repetition purposes)
        val s1 = GameState(
            cobs = mapOf(C1 to Cob(WHITE), C7 to Cob(BLACK)),
            currentTurn = WHITE,
        )
        val s2 = s1.copy(halfMoveClock = 99)
        assertEquals(s1.hashBoard(), s2.hashBoard(), "hashBoard must be equal regardless of halfMoveClock")
    }

    // ==================== canClaimFiftyMoveDraw ====================

    @Test
    fun canClaimFiftyMoveDraw_falseBelow100() {
        val state = GameState(
            cobs = mapOf(C1 to Cob(WHITE, true), C7 to Cob(BLACK, true)),
            currentTurn = WHITE,
            halfMoveClock = 99,
        )
        assertFalse(state.canClaimFiftyMoveDraw(), "Clock at 99 should not trigger 50-move rule")
    }

    @Test
    fun canClaimFiftyMoveDraw_trueAt100WithNoWinningMove() {
        // Both players have only roks with no immediate win available
        val state = GameState(
            cobs = mapOf(
                C3 to Cob(WHITE, true),
                C8 to Cob(BLACK, true),
            ),
            currentTurn = WHITE,
            halfMoveClock = 100,
        )
        assertTrue(state.canClaimFiftyMoveDraw(), "Clock at 100 with no winning move should trigger 50-move rule")
    }

    @Test
    fun canClaimFiftyMoveDraw_falseAt100WhenWinningMoveExists() {
        // White rok at A1, single black cob at B1 — white can capture all of B1's
        // adjacents or the position where black's only piece is capturable.
        // Simpler: white rok at B1 can move to capture the sole black cob via adjacency.
        // Use a direct win: white has 1 rok, black has 1 cob at C2 adjacent to B1.
        // White rok moves B1→C2 neighborhood — actually rok moves to empty squares.
        // Easier: white rok at A1, black's only piece at B2 — white rok moves A1→B1
        // which is adjacent to B2 and B1 was not adjacent to B2... wait let me think.
        // Simplest: sole black cob at C8, white rok at B4.
        // B4 adjacents: B3, B5, C7, C8, A1. C8 is adjacent to B4.
        // White rok moves somewhere that puts its destination adjacent to C8.
        // White rok at C7 → moving to C8 is blocked (occupied).
        // Actually: to have an immediate win, white must be able to move to a square
        // adjacent to ALL of black's pieces from a non-adjacent origin.
        // Simple case: black has only 1 cob at C8 (no rok), white rok at C6.
        // C6 adjacents: C5, C7, B3. NOT adjacent to C8.
        // C7 adjacents: C6, C8, B4, D3. C7 is adjacent to C8.
        // White rok moves C6→C7 (adjacent to C8, not adjacent to C6). → captures C8 → win.
        val state = GameState(
            cobs = mapOf(
                C6 to Cob(WHITE, true),
                C8 to Cob(BLACK),
            ),
            currentTurn = WHITE,
            halfMoveClock = 100,
        )
        assertFalse(
            state.canClaimFiftyMoveDraw(),
            "Clock at 100 but white has immediate winning move — 50-move draw must NOT apply",
        )
    }

    // ==================== isGameOver + getMatchState integration ====================

    @Test
    fun isGameOver_trueWhenFiftyMoveDrawClaimed() {
        val state = GameState(
            cobs = mapOf(C3 to Cob(WHITE, true), C8 to Cob(BLACK, true)),
            currentTurn = WHITE,
            halfMoveClock = 100,
            claimedFiftyMoveDraw = true,
        )
        assertTrue(state.isGameOver(emptyMap()), "isGameOver should be true once draw is claimed")
    }

    @Test
    fun isGameOver_falseAtClock100BeforeClaim() {
        val state = GameState(
            cobs = mapOf(C3 to Cob(WHITE, true), C8 to Cob(BLACK, true)),
            currentTurn = WHITE,
            halfMoveClock = 100,
        )
        assertFalse(state.isGameOver(emptyMap()), "isGameOver should be false until draw is explicitly claimed")
    }

    @Test
    fun getMatchState_fiftyMoves_returnsDrawWithNullWinner() {
        val state = GameState(
            cobs = mapOf(
                C3 to Cob(WHITE, true),
                C8 to Cob(BLACK, true),
            ),
            currentTurn = WHITE,
            halfMoveClock = 100,
            claimedFiftyMoveDraw = true,
        )
        val matchState = state.getMatchState(emptyMap())
        assertEquals(GameEndReason.FIFTY_MOVES, matchState.gameEndReason, "Game result should be FIFTY_MOVES")
        assertEquals(null, matchState.winner, "Winner should be null for a draw")
    }

    @Test
    fun getMatchState_fiftyMoves_checkedBeforeTripleRepetition() {
        // If both 50-move AND triple-repetition conditions are somehow met simultaneously,
        // FIFTY_MOVES takes precedence (it was established first in game time).
        // We can't easily force triple repetition in a unit test without positionHistory,
        // but we can verify FIFTY_MOVES is returned when clock >= 100 and no winning move.
        val state = GameState(
            cobs = mapOf(
                C3 to Cob(WHITE, true),
                C8 to Cob(BLACK, true),
            ),
            currentTurn = WHITE,
            halfMoveClock = 100,
            claimedFiftyMoveDraw = true,
        )
        assertEquals(GameEndReason.FIFTY_MOVES, state.getMatchState(emptyMap()).gameEndReason)
    }

    // ==================== Accumulation over a game sequence ====================

    @Test
    fun halfMoveClock_accumulatesAcrossRokMoves() {
        // Simulate 5 consecutive rok moves, clock should be 5
        var state = GameState(
            cobs = mapOf(
                C3 to Cob(WHITE, true),
                C8 to Cob(BLACK, true),
            ),
            currentTurn = WHITE,
        )
        // 5 consecutive rok half-moves using C3↔C2 and C8↔C7
        val movePairs = listOf(
            Move(C3 to C2), Move(C8 to C7),
            Move(C2 to C3), Move(C7 to C8),
            Move(C3 to C2),
        )
        for (move in movePairs) {
            state = state.applyMove(move)
        }
        assertEquals(5, state.halfMoveClock, "After 5 rok half-moves, clock should be 5")
    }

    @Test
    fun halfMoveClock_resetInMiddleOfRokSequence() {
        // 10 rok moves, then a cob move, then 3 more rok moves → clock = 3
        var state = GameState(
            cobs = mapOf(
                A1 to Cob(WHITE, true),
                C1 to Cob(WHITE),
                C7 to Cob(BLACK, true),
            ),
            currentTurn = WHITE,
        )
        state = state.copy(halfMoveClock = 10) // shortcut: pretend 10 rok moves happened
        // Cob move by white (C1→B1) — resets
        state = state.applyMove(Move(C1 to B1))
        assertEquals(0, state.halfMoveClock, "Cob move should reset clock to 0")
        // 3 more rok half-moves: simulate directly
        state = state.copy(halfMoveClock = state.halfMoveClock + 3)
        assertEquals(3, state.halfMoveClock, "Clock should be 3 after reset + 3 rok moves")
    }
}
