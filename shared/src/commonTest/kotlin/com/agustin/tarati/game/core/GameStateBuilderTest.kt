package com.agustin.tarati.game.core

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.helpers.GameStateBuilder
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameStateBuilderTest {
    @Test
    fun builder_defaultState_returnsInitialState() {
        val builder = GameStateBuilder()
        val state = builder.build()

        assertNotNull(state, "Builder should return a valid state")
        assertNotNull(state.cobs, "State should have cobs")
        assertNotNull(state.currentTurn, "State should have current turn")
    }

    @Test
    fun builder_setTurn_changesCurrentTurn() {
        val builder = GameStateBuilder()

        val stateWhite = builder.setTurn(CobColor.WHITE).build()
        assertEquals(CobColor.WHITE, stateWhite.currentTurn, "Turn should be WHITE")

        val stateBlack = builder.setTurn(CobColor.BLACK).build()
        assertEquals(CobColor.BLACK, stateBlack.currentTurn, "Turn should be BLACK")
    }

    @Test
    fun builder_setCob_addsNewCob() {
        val builder = GameStateBuilder()
        val state =
            builder
                .setCob(C3, CobColor.WHITE)
                .build()

        val cob = state.cobs[C3]
        assertNotNull(cob, "Cob should be added at C3")
        assertEquals(CobColor.WHITE, cob.color, "Cob color should be WHITE")
        assertFalse(cob.isUpgraded, "Cob should not be upgraded")
    }

    @Test
    fun builder_setCob_upgradesExistingCob() {
        val initialBuilder = GameStateBuilder()
        val initialState =
            initialBuilder
                .setCob(C3, CobColor.WHITE)
                .build()

        val builder = GameStateBuilder(initialState)
        val state =
            builder
                .setCob(C3, CobColor.WHITE, true)
                .build()

        val cob = state.cobs[C3]
        assertNotNull(cob, "Cob should exist at C3")
        assertTrue(cob.isUpgraded, "Cob should be upgraded")
    }

    @Test
    fun builder_setCob_changesColor() {
        val initialBuilder = GameStateBuilder()
        val initialState =
            initialBuilder
                .setCob(C3, CobColor.WHITE)
                .build()

        val builder = GameStateBuilder(initialState)
        val state =
            builder
                .setCob(C3, CobColor.BLACK)
                .build()

        val cob = state.cobs[C3]
        assertEquals(CobColor.BLACK, (cob ?: return).color, "Cob color should be BLACK")
    }

    @Test
    fun builder_removeCob_removesExistingCob() {
        val initialBuilder = GameStateBuilder()
        val initialState =
            initialBuilder
                .setCob(C3, CobColor.WHITE)
                .build()

        assertTrue(initialState.cobs.containsKey(C3), "Initial state should have cob at C3")

        val builder = GameStateBuilder(initialState)
        val state =
            builder
                .removeCob(C3)
                .build()

        assertFalse(state.cobs.containsKey(C3), "Cob should be removed from C3")
    }

    @Test
    fun builder_moveCob_movesToNewPosition() {
        val initialBuilder = GameStateBuilder()
        val initialState =
            initialBuilder
                .setCob(C1, CobColor.WHITE)
                .build()

        val builder = GameStateBuilder(initialState)
        val state =
            builder
                .moveCob(Move(C1 to B1))
                .build()

        assertFalse(state.cobs.containsKey(C1), "Original position should be empty")
        assertTrue(state.cobs.containsKey(B1), "New position should contain cob")

        val movedCob = state.cobs[B1]
        assertEquals(CobColor.WHITE, (movedCob ?: return).color, "Moved cob should retain color")
        assertFalse(movedCob.isUpgraded, "Moved cob should retain upgrade status")
    }

    @Test
    fun builder_chainMultipleOperations() {
        val state =
            GameStateBuilder()
                .setTurn(CobColor.BLACK)
                .setCob(C1, CobColor.WHITE)
                .setCob(C7, CobColor.BLACK, true)
                .moveCob(Move(C1 to B1))
                .removeCob(C7)
                .setCob(C8, CobColor.BLACK)
                .build()

        assertEquals(CobColor.BLACK, state.currentTurn, "Turn should be BLACK")
        assertFalse(state.cobs.containsKey(C1), "C1 should be empty")
        assertTrue(state.cobs.containsKey(B1), "B1 should have cob")
        assertFalse(state.cobs.containsKey(C7), "C7 should be removed")
        assertTrue(state.cobs.containsKey(C8), "C8 should have cob")

        val b1Cob = state.cobs[B1]
        assertEquals(CobColor.WHITE, (b1Cob ?: return).color, "B1 cob should be WHITE")

        val c8Cob = state.cobs[C8]
        assertEquals(CobColor.BLACK, (c8Cob ?: return).color, "C8 cob should be BLACK")
    }

    @Test
    fun builder_withCustomInitialState() {
        val customInitial =
            GameState(
                cobs = mapOf(A1 to Cob(CobColor.WHITE, true)),
                currentTurn = CobColor.BLACK,
            )

        val builder = GameStateBuilder(customInitial)
        val state =
            builder
                .setCob(B1, CobColor.BLACK)
                .build()

        assertTrue(state.cobs.containsKey(A1), "Should retain custom initial cob")
        assertTrue(state.cobs.containsKey(B1), "Should add new cob")
        assertEquals(CobColor.BLACK, state.currentTurn, "Should retain custom turn")
    }
}
