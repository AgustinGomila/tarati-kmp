package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C5
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the forced-promotion / AI-double-turn bug.
 *
 * Root cause: [GameState.getForcedPromotions] was entering the "dead piece unlock"
 * branch without first verifying that the current player has no normal moves.
 * This caused a dead cob on the opponent's D-ring (e.g. a WHITE cob captured onto D4)
 * to be returned as a forced promotion even when the player still had other cobs with
 * valid forward moves.
 *
 * Consequence: the forced-promotion auto-apply LaunchedEffect in GameScreen applied
 * the erroneous D4→D4 self-move, flipping the turn back to BLACK and triggering the
 * AI for a second consecutive turn.
 *
 * Reproduction position: B6w/C12w/C2w/C5b/C7W/C8B/D1b/D4b w
 * After player C7→D3 then AI C8→C7.
 */
class ForcedPromotionRegressionTest {

    /**
     * The exact post-AI-move position that triggered the bug:
     *
     *   WHITE: B6 (cob), C12 (cob), C2 (cob), D4 (cob — captured, primary dead at D4)
     *   BLACK: C5 (cob), D1 (cob), C7 (rok), D3 (rok — captured white rok)
     *   Turn: WHITE
     *
     * D4 is in deadVertices[WHITE], making it a primary-dead white cob.
     * However, WHITE has three cobs (B6, C12, C2) with available forward moves,
     * so getForcedPromotions() must return empty.
     */
    @Test
    fun getForcedPromotions_returnsEmpty_whenPlayerHasNormalMoves_evenWithDeadCobOnOpponentDRing() {
        val state = GameState(
            cobs = mapOf(
                B6 to Cob(WHITE),  // has forward moves
                C12 to Cob(WHITE),  // has forward moves
                C2 to Cob(WHITE),  // has forward moves
                D4 to Cob(WHITE),  // primary dead — D4 ∈ deadVertices[WHITE]
                C5 to Cob(BLACK),
                D1 to Cob(BLACK),
                C7 to Cob(BLACK, true),   // black rok
                D3 to Cob(BLACK, true),   // black rok
            ),
            currentTurn = WHITE,
        )

        val forcedPromotions = state.getForcedPromotions()

        assertTrue(
            forcedPromotions.isEmpty(),
            "getForcedPromotions() must return empty when the player has normal moves, " +
                    "even if a dead cob exists. Got: $forcedPromotions",
        )
    }

    /**
     * Sanity check: forced promotion IS returned when the player truly has no normal moves
     * and a dead cob that would gain mobility after promotion.
     */
    @Test
    fun getForcedPromotions_returnsDeadCob_whenNoNormalMovesExist() {
        // Only white piece is a dead cob at D4 (primary dead). No other white pieces.
        // After promotion D4 becomes a rok that can move anywhere.
        val state = GameState(
            cobs = mapOf(
                D4 to Cob(WHITE),  // sole white piece, primary dead
                C5 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        val forcedPromotions = state.getForcedPromotions()

        // This falls under "sole remaining cob" rule (size == 1), which is unconditional.
        assertEquals(1, forcedPromotions.size, "Sole remaining dead cob must be returned as forced promotion")
        assertEquals(Move(D4 to D4), forcedPromotions.first(), "Forced promotion must be an in-place self-move")
    }

    /**
     * Dead piece unlock: two cobs, one dead, no normal moves → the dead one is promoted.
     */
    @Test
    fun getForcedPromotions_returnsDeadCob_whenAllOtherCobsAreAlsoBlocked() {
        // D4 is primary dead (can't move forward from there).
        // D3 is also primary dead for WHITE.
        // With no other white pieces and no normal moves available, the dead cob that
        // gains moves after promotion must be returned.
        //
        // Use D4 (dead) + another dead-by-proxy cob blocked only by dead cobs.
        // Simplest: just D4 alone with a second white piece blocked by D4.
        // Since D4 has no forward neighbors reachable for a cob at D3 either
        // (D3 is also primary dead), put both dead cobs:
        val state = GameState(
            cobs = mapOf(
                D3 to Cob(WHITE),   // primary dead
                D4 to Cob(WHITE),   // primary dead
                C5 to Cob(BLACK),
                D1 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        // Both D3 and D4 are primary dead → no normal moves for white
        val normalMoves = state.getForcedPromotions()
        // At least one of D3/D4 should be a forced promotion candidate
        // (whichever gains a move after being promoted to rok)
        assertFalse(
            normalMoves.isEmpty(),
            "When all white pieces are dead and no normal moves exist, " +
                    "getForcedPromotions() must not return empty",
        )
        normalMoves.forEach { move ->
            assertTrue(
                move.isPromotion(),
                "Each forced promotion must be an in-place self-move (from == to)",
            )
        }
    }

    /**
     * Confirms the exact sequence from the bug report does NOT produce a double AI turn.
     *
     * Simulates:
     *   Starting position: B6w/C12w/C2w/C5b/C7W/C8B/D1b/D4b w
     *   Player move: C7→D3 (white rok captures D4)
     *   AI move:    C8→C7 (black rok captures D3)
     *
     * After the AI move, it must be WHITE's turn and getForcedPromotions() must be empty,
     * which means the AI should NOT get a second turn.
     */
    @Test
    fun afterAiMove_noForcedPromotionTriggered_inBugReportPosition() {
        // Parse starting position: B6w/C12w/C2w/C5b/C7W/C8B/D1b/D4b w
        val start = GameState.parseBoardNotation("B6w/C12w/C2w/C5b/C7W/C8B/D1b/D4b w")

        // Player: C7→D3
        val afterPlayerMove = start.applyMove(Move(C7 to D3))
        assertEquals(BLACK, afterPlayerMove.currentTurn, "After player's move, it should be BLACK's turn")

        // AI: C8→C7
        val afterAiMove = afterPlayerMove.applyMove(Move(C8 to C7))
        assertEquals(WHITE, afterAiMove.currentTurn, "After AI's move, it should be WHITE's turn")

        // Core assertion: forced promotions must be empty — no second AI trigger
        val forced = afterAiMove.getForcedPromotions()
        assertTrue(
            forced.isEmpty(),
            "After AI's move, getForcedPromotions() for WHITE must be empty " +
                    "because WHITE still has normal moves. Got: $forced",
        )

        // Also confirm WHITE does have normal moves (B6, C12, C2 can move forward)
        val whiteCobs = afterAiMove.cobs.filter { it.value.color == WHITE }
        assertEquals(4, whiteCobs.size, "White should have 4 pieces after both moves")
    }

    // ──────────────────────────────────────────────────────────────────────
    // detectUpgrades: Roks moving to empty squares must NOT fire upgrade anim
    // ──────────────────────────────────────────────────────────────────────

    /**
     * A Rok moving to an empty vertex must NOT be detected as an upgrade.
     * Before the fix, detectUpgrades fired on any `oldCob == null && newCob.isUpgraded`
     * vertex, which included every empty square a Rok moved into.
     */
    @Test
    fun detectUpgrades_doesNotFire_whenRokMovesToEmptySquare() {
        val before = GameState(
            cobs = mapOf(
                C8 to Cob(BLACK, true),  // black rok at C8
                C5 to Cob(BLACK),
                C2 to Cob(WHITE),
            ),
            currentTurn = BLACK,
        )

        // Black Rok moves C8→C7 (C7 is empty in this state)
        val after = before.applyMove(Move(C8 to C7))
        val upgrades = before.detectUpgrades(after)

        assertTrue(
            upgrades.isEmpty(),
            "A Rok moving to an empty square must NOT produce an upgrade animation. " +
                    "Got upgrades: $upgrades",
        )
    }

    /**
     * A Cob moving onto an upgrade vertex (C7/C8 for white, C1/C2 for black)
     * MUST still be detected as an upgrade.
     */
    @Test
    fun detectUpgrades_fires_whenCobReachesUpgradeVertex() {
        val before = GameState(
            cobs = mapOf(
                B4 to Cob(WHITE),  // white cob about to move to C7 (upgrade vertex)
                D1 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        // White Cob moves B4→C7 (C7 is WHITE's upgrade vertex)
        val after = before.applyMove(Move(B4 to C7))
        val upgrades = before.detectUpgrades(after)

        assertEquals(
            1,
            upgrades.size,
            "A Cob reaching an upgrade vertex must produce exactly one upgrade animation",
        )
        assertEquals(C7, upgrades.first().first, "The upgrade must be at the destination vertex C7")
        assertTrue(upgrades.first().second.isUpgraded, "The upgraded piece must be a Rok")
    }

    /**
     * Regression: sole white cob on D2 (own home base, forward path free) must NOT be
     * promoted automatically.
     *
     * Position: A1b/B1b/B5b/B6b/C12b/D2w/D3b/D4b w
     *
     * Root cause: [GameState.getForcedPromotions] reached the "sole remaining cob" branch
     * before checking [normalMovesForTurn], so it returned D2→D2 as a forced promotion
     * even though D2 can advance to C2 (free forward neighbor).
     *
     * Fix: [normalMovesForTurn] is now checked first; if any normal move exists, both
     * promotion branches are skipped and the function returns empty.
     */
    @Test
    fun getForcedPromotions_returnsEmpty_forSoleCobWithForwardMoveAvailable() {
        // A1b/B1b/B5b/B6b/C12b/D2w/D3b/D4b w
        // D2 is in white's own home base. Its only forward neighbor is C2, which is free.
        val state = GameState(
            cobs = mapOf(
                A1 to Cob(BLACK),
                B1 to Cob(BLACK),
                B5 to Cob(BLACK),
                B6 to Cob(BLACK),
                C12 to Cob(BLACK),
                D2 to Cob(WHITE),   // sole white piece — has a forward move to C2
                D3 to Cob(BLACK),
                D4 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        // D2 is NOT in deadVertices[WHITE] (those are D3/D4).
        // C2 is free → D2 has a normal forward move → no promotion must be offered.
        val forcedPromotions = state.getForcedPromotions()
        assertTrue(
            forcedPromotions.isEmpty(),
            "A sole white cob on D2 with C2 free must NOT trigger forced promotion. " +
                    "Got: $forcedPromotions",
        )

        // Confirm the normal move D2→C2 is present so the test premise is sound.
        val normalMoves = state.allMovesForTurn()
        assertTrue(
            normalMoves.contains(Move(D2 to C2)),
            "D2→C2 must be a legal normal move in this position",
        )
    }

    /**
     * An in-place forced promotion (D4→D4) must also be detected as an upgrade.
     */
    @Test
    fun detectUpgrades_fires_forInPlaceForcedPromotion() {
        val before = GameState(
            cobs = mapOf(
                D4 to Cob(WHITE),  // sole white cob — forced promotion
                C5 to Cob(BLACK),
            ),
            currentTurn = WHITE,
        )

        val after = before.applyPromotion(Move(D4 to D4))
        val upgrades = before.detectUpgrades(after)

        assertEquals(
            1,
            upgrades.size,
            "An in-place promotion must produce exactly one upgrade animation",
        )
        assertEquals(D4, upgrades.first().first, "The upgrade must be at D4")
        assertTrue(upgrades.first().second.isUpgraded, "The promoted piece must be a Rok")
    }
}
