package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.createGameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameStateHelpersTest {
    @Test
    fun modifyCob_addNewCob() {
        val initialState = GameState(emptyMap(), currentTurn = CobColor.WHITE)
        val newState = initialState.modifyCob(C1, CobColor.WHITE, false)

        val cob = newState.cobs[C1]
        assertNotNull(cob, "Should add new cob")
        assertEquals(CobColor.WHITE, cob.color, "Cob color should be WHITE")
        assertFalse(cob.isUpgraded, "Cob should not be upgraded")
    }

    @Test
    fun modifyCob_updateExistingCob() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        val newState = initialState.modifyCob(C1, CobColor.BLACK, true)

        val cob = newState.cobs[C1]
        assertNotNull(cob, "Cob should exist")
        assertEquals(CobColor.BLACK, cob.color, "Cob color should be updated")
        assertTrue(cob.isUpgraded, "Cob should be upgraded")
    }

    @Test
    fun modifyCob_partialUpdate() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        // Only update color
        val state1 = initialState.modifyCob(C1, CobColor.BLACK)
        val cob1 = state1.cobs[C1]
        assertEquals(CobColor.BLACK, (cob1 ?: return).color, "Color should be updated")
        assertFalse(cob1.isUpgraded, "Upgrade status should remain")

        // Only update upgrade status
        val state2 = initialState.modifyCob(C1, isUpgraded = true)
        val cob2 = state2.cobs[C1]
        assertEquals(CobColor.WHITE, (cob2 ?: return).color, "Color should remain")
        assertTrue(cob2.isUpgraded, "Upgrade status should be updated")
    }

    @Test
    fun modifyCob_removeCob() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        val newState = initialState.modifyCob(C1)

        assertFalse(newState.cobs.containsKey(C1), "Cob should be removed")
    }

    @Test
    fun moveCob_successfulMove() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        val newState = initialState.moveCob(Move(C1 to B1))

        assertFalse(newState.cobs.containsKey(C1), "Original position should be empty")
        assertTrue(newState.cobs.containsKey(B1), "New position should have cob")

        val movedCob = newState.cobs[B1]
        assertEquals(CobColor.WHITE, (movedCob ?: return).color, "Cob should retain color")
        assertFalse(movedCob.isUpgraded, "Cob should retain upgrade status")
    }

    @Test
    fun moveCob_nonExistentCob() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        // Try to move non-existent cob
        val newState = initialState.moveCob(Move(C2 to B1))

        // State should remain unchanged
        assertEquals(initialState, newState, "State should be unchanged")
    }

    @Test
    fun withTurn_changesTurn() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        val newState = initialState.withTurn(CobColor.BLACK)

        assertEquals(CobColor.BLACK, newState.currentTurn, "Turn should be BLACK")
        assertEquals(initialState.cobs, newState.cobs, "Cobs should remain the same")
    }

    @Test
    fun createGameState_withBuilderPattern() {
        val state =
            createGameState {
                setTurn(CobColor.BLACK)
                setCob(C1, CobColor.WHITE)
                setCob(C7, CobColor.BLACK, true)
                moveCob(Move(C1 to B1))
            }

        assertEquals(CobColor.BLACK, state.currentTurn, "Turn should be BLACK")
        assertFalse(state.cobs.containsKey(C1), "C1 should be empty")
        assertTrue(state.cobs.containsKey(B1), "B1 should have cob")
        assertTrue(state.cobs.containsKey(C7), "C7 should have upgraded cob")

        val b1Cob = state.cobs[B1]
        assertEquals(CobColor.WHITE, (b1Cob ?: return).color, "B1 cob should be WHITE")

        val c7Cob = state.cobs[C7]
        assertTrue((c7Cob ?: return).isUpgraded, "C7 cob should be upgraded")
    }

    @Test
    fun createGameState_emptyBuilder() {
        val state =
            createGameState {
                // No operations
            }

        assertNotNull(state, "Should create valid state")
        assertNotNull(state.cobs, "Should have cobs")
        assertNotNull(state.currentTurn, "Should have current turn")
    }

    @Test
    fun helpers_areImmutable() {
        val initialState =
            GameState(
                mapOf(C1 to Cob(CobColor.WHITE)),
                currentTurn = CobColor.WHITE,
            )

        // Apply multiple operations
        val state1 = initialState.modifyCob(C2, CobColor.BLACK, false)
        val state2 = state1.moveCob(Move(C1 to B1))
        val state3 = state2.withTurn(CobColor.BLACK)

        // Original state should remain unchanged
        assertEquals(
            mapOf(C1 to Cob(CobColor.WHITE)),
            initialState.cobs,
            "Original state should be unchanged",
        )
        assertEquals(
            CobColor.WHITE,
            initialState.currentTurn,
            "Original turn should be unchanged",
        )

        // New states should have the changes
        assertTrue(state1.cobs.containsKey(C2), "State1 should have new cob")
        assertFalse(state2.cobs.containsKey(C1), "State2 should have moved cob")
        assertEquals(CobColor.BLACK, state3.currentTurn, "State3 should have new turn")
    }
}
