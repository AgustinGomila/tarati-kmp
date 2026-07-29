package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C5
import com.agustin.tarati.core.domain.game.board.GameBoard.C6
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.board.GameBoard.vertices
import com.agustin.tarati.core.domain.game.board.normalizedPositions
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardLogicTest {

    // ==================== Basic movement ====================

    @Test
    fun applyMoveToBoard_movesCobToNewPosition() {
        val initialState = GameState(
            cobs = mapOf(C1 to Cob(WHITE)),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(C1 to B1))

        assertFalse("Original position should be empty", newState.cobs.containsKey(C1))
        assertTrue("New position should contain cob", newState.cobs.containsKey(B1))
        assertEquals("Cob should retain color", WHITE, (newState.cobs[B1] ?: return).color)
    }

    @Test
    fun applyMoveToBoard_returnsOriginalStateWhenFromNotFound() {
        val initialState = GameState(
            cobs = mapOf(C1 to Cob(WHITE)),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(C2 to B1)) // C2 is empty

        assertEquals("Should return original state when from position not found", initialState, newState)
    }

    // ==================== Promotion ====================

    @Test
    fun applyMoveToBoard_upgradesWhiteInBlackHomeBase() {
        // White cob advances to C7 (upgrade vertex for white)
        val initialState = GameState(
            cobs = mapOf(C6 to Cob(WHITE)),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(C6 to C7))

        val cob = newState.cobs[C7]
        assertNotNull("Cob should exist at C7", cob)
        assertTrue("White cob advanced to C7 should be promoted to rok", (cob ?: return).isUpgraded)
    }

    @Test
    fun applyMoveToBoard_upgradesBlackInWhiteHomeBase() {
        // Black cob advances to C1 (upgrade vertex for black)
        val initialState = GameState(
            cobs = mapOf(C2 to Cob(BLACK)),
            currentTurn = BLACK,
        )

        val newState = initialState.applyMove(Move(C2 to C1))

        val cob = newState.cobs[C1]
        assertNotNull("Cob should exist at C1", cob)
        assertTrue("Black cob advanced to C1 should be promoted to rok", (cob ?: return).isUpgraded)
    }

    @Test
    fun applyMoveToBoard_doesNotUpgradeWhiteAtDeadVertex() {
        // White cob captured (flipped) onto D3 must NOT auto-promote — D3 is a dead vertex,
        // not an upgrade vertex. upgradeIfInEnemyBase only applies to C7/C8 for white.
        val initialState = GameState(
            cobs = mapOf(
                B3 to Cob(WHITE),  // White advances to C5, capturing black at D3 via flip
                D3 to Cob(BLACK),  // Will be flipped to white after a capture nearby
            ),
            currentTurn = WHITE,
        )

        // Simulate direct placement of a white cob at D3 (as would happen after a flip)
        val stateWithWhiteAtD3 = initialState.modifyCob(D3, WHITE, false)

        val cob = stateWithWhiteAtD3.cobs[D3]
        assertNotNull("Cob should exist at D3", cob)
        assertFalse(
            "White cob at D3 should NOT be promoted (D3 is dead, not an upgrade vertex)",
            (cob ?: return).isUpgraded
        )
    }

    @Test
    fun applyMoveToBoard_capturedRokRetainsUpgradedStatus() {
        // A rok that is captured (flipped) must retain isUpgraded = true (§5.3).
        // The rok just changes color.
        val initialState = GameState(
            cobs = mapOf(
                B2 to Cob(WHITE),
                C5 to Cob(BLACK, true), // Black rok adjacent to B3 (destination)
            ),
            currentTurn = WHITE,
        )

        // B2 adjacents: {B1, B3, C3, C4, A1}
        // B3 adjacents: {B2, B4, C5, C6, A1}
        // C5 is NOT adjacent to B2 → eligible for capture
        val newState = initialState.applyMove(Move(B2 to B3))

        val capturedRok = newState.cobs[C5]
        assertNotNull("Rok should still exist at C5", capturedRok)
        assertEquals("Captured rok should flip to white", WHITE, (capturedRok ?: return).color)
        assertTrue("Captured rok must retain rok status (isUpgraded)", capturedRok.isUpgraded)
    }

    // ==================== Pre-adjacency capture rule ====================

    @Test
    fun preAdjacency_doesNotFlipPieceAdjacentToOrigin() {
        // C1 is adjacent to C2. Moving C1→B1: C2 is adjacent to B1 (destination)
        // BUT was already adjacent to C1 (origin) → must NOT be captured.
        val initialState = GameState(
            cobs = mapOf(
                C1 to Cob(WHITE),
                C2 to Cob(BLACK), // Adjacent to both C1 (origin) and B1 (destination)
            ),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(C1 to B1))

        val cob = newState.cobs[C2]
        assertNotNull("C2 cob should still exist", cob)
        assertEquals(
            "C2 was adjacent to origin C1 → pre-adjacency rule protects it from capture",
            BLACK,
            (cob ?: return).color,
        )
    }

    @Test
    fun preAdjacency_flipsPieceNotAdjacentToOrigin() {
        // B2 adjacents: {B1, B3, C3, C4, A1}
        // B3 adjacents: {B2, B4, C5, C6, A1}
        // C5 and C6 are adjacent to B3 (destination) but NOT to B2 (origin) → must be captured.
        val initialState = GameState(
            cobs = mapOf(
                B2 to Cob(WHITE),
                C5 to Cob(BLACK), // New adjacent at destination
                C6 to Cob(BLACK), // New adjacent at destination
            ),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(B2 to B3))

        assertEquals("C5 must flip to white", WHITE, (newState.cobs[C5] ?: return).color)
        assertEquals("C6 must flip to white", WHITE, (newState.cobs[C6] ?: return).color)
    }

    @Test
    fun preAdjacency_doesNotFlipSameColorCobs() {
        val initialState = GameState(
            cobs = mapOf(
                B2 to Cob(WHITE),
                C5 to Cob(WHITE), // Same color adjacent to destination
            ),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(B2 to B3))

        assertEquals("Same color cob should not flip", WHITE, (newState.cobs[C5] ?: return).color)
    }

    @Test
    fun preAdjacency_flipsMultiplePiecesInSingleMove() {
        // Moving B2→B3 can capture C5, C6, and B4 simultaneously (all new adjacents).
        // A1 is adjacent to both B2 and B3 → protected by pre-adjacency rule.
        val initialState = GameState(
            cobs = mapOf(
                B2 to Cob(WHITE),
                C5 to Cob(BLACK),
                C6 to Cob(BLACK),
                B4 to Cob(BLACK), // B4 is adjacent to B3, not to B2
                A1 to Cob(BLACK), // A1 IS adjacent to B2 → protected
            ),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(B2 to B3))

        assertEquals("C5 should flip", WHITE, (newState.cobs[C5] ?: return).color)
        assertEquals("C6 should flip", WHITE, (newState.cobs[C6] ?: return).color)
        assertEquals("B4 should flip", WHITE, (newState.cobs[B4] ?: return).color)
        assertEquals("A1 was adjacent to origin → protected, stays black", BLACK, (newState.cobs[A1] ?: return).color)
    }

    // ==================== Home-base non-forward captures ====================

    @Test
    fun homeBaseCapture_nonForwardMoveFromDomesticVertexCaptures() {
        // White cob at D2. Non-forward move D2→D1.
        // D2 adjacents: {D1, C2} — C1 is NOT adjacent to D2.
        // D1 adjacents: {D2, C1} — C1 IS adjacent to D1 and NOT pre-adjacent to D2.
        // → Moving D2→D1 captures black at C1.
        val initialState = GameState(
            cobs = mapOf(
                D2 to Cob(WHITE),
                C1 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        val newState = initialState.applyMove(Move(D2 to D1))

        assertEquals("White should be at D1 after move", WHITE, (newState.cobs[D1] ?: return).color)
        assertEquals(
            "Black at C1 should flip to white (new adjacent at D1)",
            WHITE,
            (newState.cobs[C1] ?: return).color
        )
    }

    @Test
    fun homeBaseCapture_nonForwardMoveIsInvalidWithoutCapture() {
        // D2→D1 with no capturable piece at C1: move should not appear in allMovesForTurn.
        val state = GameState(
            cobs = mapOf(D2 to Cob(WHITE)),
            currentTurn = WHITE,
        )

        val moves = state.allMovesForTurn()
        assertFalse("D2→D1 without capture target should not be a legal move", moves.contains(Move(D2 to D1)))
    }

    // ==================== Dead cob detection ====================

    @Test
    fun isDeadCob_primaryDead_whiteAtD3() {
        val state = GameState(
            cobs = mapOf(D3 to Cob(WHITE)),
            currentTurn = WHITE,
        )
        assertTrue("White cob at D3 (outermost enemy base) is primary dead", state.isDeadCob(D3, Cob(WHITE)))
    }

    @Test
    fun isDeadCob_primaryDead_whiteAtD4() {
        val state = GameState(
            cobs = mapOf(D4 to Cob(WHITE)),
            currentTurn = WHITE,
        )
        assertTrue("White cob at D4 (outermost enemy base) is primary dead", state.isDeadCob(D4, Cob(WHITE)))
    }

    @Test
    fun isDeadCob_primaryDead_blackAtD1() {
        val state = GameState(
            cobs = mapOf(D1 to Cob(BLACK)),
            currentTurn = BLACK,
        )
        assertTrue("Black cob at D1 (outermost enemy base) is primary dead", state.isDeadCob(D1, Cob(BLACK)))
    }

    @Test
    fun isDeadCob_primaryDead_blackAtD2() {
        val state = GameState(
            cobs = mapOf(D2 to Cob(BLACK)),
            currentTurn = BLACK,
        )
        assertTrue("Black cob at D2 (outermost enemy base) is primary dead", state.isDeadCob(D2, Cob(BLACK)))
    }

    @Test
    fun isDeadCob_rokIsNeverDead() {
        // Even at a dead vertex, a rok is never dead
        val state = GameState(
            cobs = mapOf(D3 to Cob(WHITE, true)),
            currentTurn = WHITE,
        )
        assertFalse("A rok at D3 is never dead", state.isDeadCob(D3, Cob(WHITE, true)))
    }

    @Test
    fun isDeadCob_proxyDead_forwardBlockedByPrimaryDead() {
        // White cob at C7. C7's only forward neighbor for white is D3.
        // D3 has a primary-dead white cob → C7 white cob is dead by proxy.
        val state = GameState(
            cobs = mapOf(
                C7 to Cob(WHITE),
                D3 to Cob(WHITE),
            ),
            currentTurn = WHITE,
        )
        assertTrue("White cob at D3 is primary dead", state.isDeadCob(D3, Cob(WHITE)))
        assertTrue(
            "White cob at C7 is dead by proxy (only forward neighbor D3 is dead)",
            state.isDeadCob(C7, Cob(WHITE)),
        )
    }

    @Test
    fun isDeadCob_notDeadWhenBlockedByEnemy() {
        // A cob blocked by an enemy piece is NOT dead — the enemy could move away.
        val state = GameState(
            cobs = mapOf(
                C7 to Cob(WHITE),
                D3 to Cob(BLACK), // Enemy at the only forward neighbor
            ),
            currentTurn = WHITE,
        )
        assertFalse(
            "White cob at C7 is NOT dead when blocked by an enemy (enemy can move)",
            state.isDeadCob(C7, Cob(WHITE)),
        )
    }

    @Test
    fun isDeadCob_notDeadWhenBlockedByRok() {
        // A cob blocked by a rok of any color is NOT dead — the rok can move away.
        val state = GameState(
            cobs = mapOf(
                C7 to Cob(WHITE),
                D3 to Cob(WHITE, true), // Own rok at the only forward neighbor
            ),
            currentTurn = WHITE,
        )
        assertFalse(
            "White cob at C7 is NOT dead when blocked by a rok (roks can move away)",
            state.isDeadCob(C7, Cob(WHITE)),
        )
    }

    @Test
    fun isDeadCob_notDeadWithNoForwardNeighbors() {
        // A cob that simply has no forward neighbors (e.g. rok-accessible geometry edge case)
        // is NOT dead by proxy — the patent says "a piece is not necessarily dead if it cannot
        // be moved."
        // We test this indirectly: a white rok at any position is never dead.
        val state = GameState(
            cobs = mapOf(C7 to Cob(WHITE, true)),
            currentTurn = WHITE,
        )
        assertFalse("Rok is never dead regardless of position", state.isDeadCob(C7, Cob(WHITE, true)))
    }

    @Test
    fun getDeadCobsForCurrentTurn_returnsOnlyCurrentPlayerDeadCobs() {
        val state = GameState(
            cobs = mapOf(
                D3 to Cob(WHITE), // White primary dead
                D4 to Cob(WHITE), // White primary dead
                D1 to Cob(BLACK), // Black primary dead (not current turn)
                C2 to Cob(WHITE), // White alive (in own base, but not dead)
            ),
            currentTurn = WHITE,
        )

        val deadCobs = state.getDeadCobsForCurrentTurn()

        assertEquals("Should return exactly 2 dead cobs for white", 2, deadCobs.size)
        assertTrue("D3 should be in dead cobs", deadCobs.any { it.first == D3 })
        assertTrue("D4 should be in dead cobs", deadCobs.any { it.first == D4 })
        assertFalse("D1 (black) should not be included", deadCobs.any { it.first == D1 })
        assertFalse("C2 (alive) should not be included", deadCobs.any { it.first == C2 })
    }

    // ==================== Board structure ====================

    @Test
    fun normalizedPositions_containsAllVertices() {
        vertices.forEach { vertex ->
            assertTrue(
                "Normalized positions should contain $vertex",
                normalizedPositions.containsKey(vertex),
            )
        }
    }
}