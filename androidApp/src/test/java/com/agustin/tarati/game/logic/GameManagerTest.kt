package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C9
import com.agustin.tarati.core.domain.game.manager.GameManager
import com.agustin.tarati.core.domain.game.manager.GameManagerState.Companion.createInitialUiState
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GameManager].
 *
 * Focuses on [GameManager.updateHistory] with both the standard initial state
 * and a custom one, covering the use case of games started from an edited board.
 *
 * ## Board topology reference
 *
 *             D3    D4
 *
 *             C7    C8
 *        C6              C9
 *                B4
 *     C5    B3        B5    C10
 *                A1
 *     C4    B2        B6    C11
 *                B1
 *        C3              C12
 *             C2    C1
 *
 *             D2    D1
 */
class GameManagerTest {

    private lateinit var manager: GameManager

    @Before
    fun setup() {
        manager = GameManager(createInitialUiState())
    }

    // ── updateHistory — standard path (default initialState) ──────────────────

    @Test
    fun `updateHistory with standard moves rebuilds history from initialGameState`() {
        val moves = listOf(Move(C1 to B1), Move(C7 to B4))

        manager.updateHistory(moves)

        assertEquals("History size matches move count", 2, manager.history.value.size)
        assertEquals("moveIndex points to last entry", 1, manager.moveIndex.value)
    }

    @Test
    fun `updateHistory each entry holds the state AFTER its move`() {
        val moves = listOf(Move(C1 to B1))

        manager.updateHistory(moves)

        val entryState = manager.history.value.getGameState(0)
        assertEquals("Entry 0 state has B1 = WHITE (state after move)", WHITE, entryState.cobs[B1]?.color)
        assertNull("Entry 0 state has C1 empty (piece moved)", entryState.cobs[C1])
    }

    @Test
    fun `updateHistory with empty list clears history`() {
        // Populate first, then clear
        manager.updateHistory(listOf(Move(C1 to B1)))
        manager.updateHistory(emptyList())

        assertEquals(
            "Empty history after updateHistory with empty list",
            0, manager.history.value.size
        )
        assertEquals("moveIndex is -1 after empty history", -1, manager.moveIndex.value)
    }

    @Test
    fun `updateHistory entries chain correctly - each state builds on the previous`() {
        val moves = listOf(Move(C1 to B1), Move(C7 to B4))

        manager.updateHistory(moves)

        val stateAfterMove0 = manager.history.value.getGameState(0)
        val stateAfterMove1 = manager.history.value.getGameState(1)

        // After move 0: C1→B1 applied
        assertEquals("After move 0: B1 = WHITE", WHITE, stateAfterMove0.cobs[B1]?.color)
        assertEquals("After move 0: C7 still = BLACK", BLACK, stateAfterMove0.cobs[C7]?.color)

        // After move 1: C7→B4 applied on top of move 0
        assertEquals("After move 1: B1 still = WHITE", WHITE, stateAfterMove1.cobs[B1]?.color)
        assertEquals("After move 1: B4 = BLACK", BLACK, stateAfterMove1.cobs[B4]?.color)
        assertNull("After move 1: C7 empty", stateAfterMove1.cobs[C7])
    }

    // ── updateHistory — custom initialState (edited board) ───────────────────
    //
    // WHITE at C3 (white half), BLACK at C9 (black half).
    // Moves C3→B1 and C9→B4 are forward moves that produce no captures.

    private val editedState = GameState(
        cobs = mapOf(
            C3 to Cob(WHITE),
            C9 to Cob(BLACK),
        ),
        currentTurn = WHITE,
    )

    @Test
    fun `updateHistory with custom initialState uses it as base`() {
        val moves = listOf(Move(C3 to B1))

        manager.updateHistory(moves, initialState = editedState)

        val entryState = manager.history.value.getGameState(0)
        assertEquals("B1 = WHITE — moved from edited start", WHITE, entryState.cobs[B1]?.color)
        assertNull("C3 empty after move", entryState.cobs[C3])
        // Standard opening pieces must NOT appear
        assertNull("C1 empty — no standard opening pieces", entryState.cobs[C1])
    }

    @Test
    fun `updateHistory custom initialState chains entries correctly`() {
        val moves = listOf(Move(C3 to B1), Move(C9 to B4))

        manager.updateHistory(moves, initialState = editedState)

        val stateAfterMove0 = manager.history.value.getGameState(0)
        val stateAfterMove1 = manager.history.value.getGameState(1)

        // After move 0: C3→B1
        assertEquals("After move 0: B1 = WHITE", WHITE, stateAfterMove0.cobs[B1]?.color)
        assertEquals("After move 0: C9 still = BLACK", BLACK, stateAfterMove0.cobs[C9]?.color)

        // After move 1: C9→B4
        assertEquals("After move 1: B1 still = WHITE", WHITE, stateAfterMove1.cobs[B1]?.color)
        assertEquals("After move 1: B4 = BLACK", BLACK, stateAfterMove1.cobs[B4]?.color)
        assertNull("After move 1: C9 empty", stateAfterMove1.cobs[C9])
    }

    @Test
    fun `updateHistory default and custom initialState produce different entry states`() {
        val moves = listOf(Move(C1 to B1))

        val managerStandard = GameManager(createInitialUiState())
        val managerCustom = GameManager(createInitialUiState())

        managerStandard.updateHistory(moves)
        managerCustom.updateHistory(moves, initialState = editedState)

        val standardEntry = managerStandard.history.value.getGameState(0)
        val customEntry = managerCustom.history.value.getGameState(0)

        // Standard: C1→B1 from 8-piece opening
        assertEquals("Standard entry: B1 = WHITE", WHITE, standardEntry.cobs[B1]?.color)
        assertEquals("Standard entry: 8 pieces", 8, standardEntry.cobs.size)

        // Custom: C1 has no piece in edited state — move is a no-op, state is editedState
        assertEquals(
            "Custom entry: 2 pieces (C1 move invalid from edited start)",
            2, customEntry.cobs.size
        )
        assertEquals(
            "Custom entry: C3 still WHITE (no piece at C1 to move)",
            WHITE, customEntry.cobs[C3]?.color
        )
    }

    // ── undo / navigation back to the initial position ────────────────────────

    @Test
    fun `undoMove past the first move restores the custom initial state`() {
        manager.updateHistory(listOf(Move(C3 to B1)), initialState = editedState)

        manager.undoMove()

        assertEquals("moveIndex is -1 after undoing the only move", -1, manager.moveIndex.value)
        assertEquals(
            "Board shows the edited start, not the standard opening",
            editedState, manager.gameState.value,
        )
    }

    @Test
    fun `updateHistory adopts the custom initialState as the game's initial position`() {
        manager.updateHistory(listOf(Move(C3 to B1)), initialState = editedState)

        assertEquals(
            "initialGameState tracks the state history was rebuilt from",
            editedState, manager.initialGameState,
        )
    }

    @Test
    fun `clearHistory adopts the given state as the game's initial position`() {
        manager.clearHistory(editedState)

        assertEquals("initialGameState tracks the cleared-to state", editedState, manager.initialGameState)
        assertEquals("Board shows the given state", editedState, manager.gameState.value)
        assertEquals("moveIndex is -1", -1, manager.moveIndex.value)
    }

    // ── setInitialGameState ───────────────────────────────────────────────────

    @Test
    fun `setInitialGameState stores the given state`() {
        manager.setInitialGameState(editedState)

        assertEquals("initialGameState reflects the custom state", editedState, manager.initialGameState)
    }

    @Test
    fun `initialGameState defaults to standard opening`() {
        assertEquals(
            "Default initialGameState is standard opening",
            initialGameState(), manager.initialGameState
        )
    }
}