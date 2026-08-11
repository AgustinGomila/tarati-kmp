package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C10
import com.agustin.tarati.core.domain.game.board.GameBoard.C11
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C4
import com.agustin.tarati.core.domain.game.board.GameBoard.C5
import com.agustin.tarati.core.domain.game.board.GameBoard.C6
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.C9
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for the AI-vs-AI "stuck after forced promotion" bug.
 *
 * ## Root cause
 * [GameScreenSideEffects]' auto-apply
 * LaunchedEffect used `!aiEnabled || currentTurn == playerSide` to decide
 * whether to auto-apply a forced promotion. In AI vs AI with
 * `playerSide = WHITE`, this evaluates to `true` on White's turn, causing
 * BOTH the LaunchedEffect AND the AI engine to apply the same forced
 * promotion move simultaneously, leaving the ViewModel in an inconsistent
 * state from which the AI never recovers.
 *
 * ## Fix
 * Changed the condition to:
 * ```kotlin
 * val isCurrentTurnHuman =
 *     (currentTurn == WHITE && !screenState.whiteIsAI) ||
 *     (currentTurn == BLACK && !screenState.blackIsAI)
 * ```
 * This ensures the auto-apply only fires for genuinely human turns. The AI
 * engine already handles forced promotions via [GameState.allMovesForTurn],
 * so no special case is needed.
 *
 * ## Reproduced game (from bug report)
 * ```
 * 1.  C2→C3   C8→B4
 * 2.  C3→C4   B4→B5
 * 3.  D2→C2   C7→B4
 * 4.  C2→C3   D3→C7
 * 5.  C1→C12  B5→B6
 * 6.  D1→C1   B6→B1
 * 7.  C12→B6  B4→A1
 * 8.  C3→B2   C7→B4
 * 9.  B2→B3   D4→C8
 * 10. C4→C5   C1→D1
 * 11. B1→B2   B6→B1
 * 12. C5→C6   C8→C7
 * 13. B3=R    (forced promotion — White's last Cob)
 * ```
 * Final position (White to move with only a Rok):
 * `A1b/B1b/B2b/B3W/B4b/C6b/C7b/D1B w`
 */
class ForcedPromotionTurnTest {

    private val engine = TaratiAI().also {
        it.setConfig(it.evalConfig.copy(difficulty = Difficulty.EASY))
    }

    // ── 1. Full game replay ───────────────────────────────────────────────────

    /**
     * Replays all 12 regular moves from [initialGameState] and asserts that
     * the resulting position has White with a single Cob at B3, no normal
     * moves, and exactly one forced promotion available — reproducing the
     * state that triggered the bug.
     */
    @Test
    fun gameReplay_reachesExpectedPrePromotionPosition() {
        val moves: List<Pair<Move, Move?>> = listOf(
            Move(C2 to C3) to Move(C8 to B4),
            Move(C3 to C4) to Move(B4 to B5),
            Move(D2 to C2) to Move(C7 to B4),
            Move(C2 to C3) to Move(D3 to C7),
            Move(C1 to C12) to Move(B5 to B6),
            Move(D1 to C1) to Move(B6 to B1),
            Move(C12 to B6) to Move(B4 to A1),
            Move(C3 to B2) to Move(C7 to B4),
            Move(B2 to B3) to Move(D4 to C8),
            Move(C4 to C5) to Move(C1 to D1),
            Move(B1 to B2) to Move(B6 to B1),
            Move(C5 to C6) to Move(C8 to C7),
        )

        var state = initialGameState(WHITE)
        for ((whiteMove, blackMove) in moves) {
            state = state.applyMove(whiteMove)
            if (blackMove != null) state = state.applyMove(blackMove)
        }

        assertEquals(WHITE, state.currentTurn, "White must be to move after move 12")

        val whitePieces = state.cobs.filter { it.value.color == WHITE }
        assertEquals(1, whitePieces.size, "White has exactly one piece remaining after move 12")

        val (whiteVertex, whiteCob) = whitePieces.entries.first()
        assertEquals(B3, whiteVertex, "White's sole piece is at B3")
        assertFalse(whiteCob.isUpgraded, "White's piece at B3 is still a Cob (not yet promoted)")

        assertTrue(
            state.normalMovesForTurn().isEmpty(),
            "White has no normal moves — forced-promotion state reached",
        )

        val forcedPromotions = state.getForcedPromotions()
        assertEquals(1, forcedPromotions.size, "Exactly one forced promotion available for White")
        assertEquals(
            Move(B3 to B3),
            forcedPromotions.first(),
            "Forced promotion is an in-place move at B3",
        )
    }

    // ── 2. Post-promotion state ───────────────────────────────────────────────

    /**
     * After applying the forced promotion at B3, verifies that:
     * - The turn stays with White (patent §6.3: promotion does not consume the turn).
     * - White's piece at B3 is now a Rok.
     * - White has at least one normal move available (game must continue).
     * - The game is not over.
     */
    @Test
    fun afterForcedPromotion_whiteRokHasMoves_andTurnRemainsWhite() {
        // "B3w" = White Cob at B3 (pre-promotion); "D1B" = Black Rok at D1.
        val prePromotion = parseBoardNotation("A1b/B1b/B2b/B3w/B4b/C6b/C7b/D1B w")

        val promotions = prePromotion.getForcedPromotions()
        assertEquals(1, promotions.size, "One forced promotion for White")
        assertEquals(Move(B3 to B3), promotions.first(), "In-place promotion at B3")

        val postPromotion = prePromotion.applyPromotion(promotions.first())

        assertEquals(
            WHITE,
            postPromotion.currentTurn,
            "Turn must remain WHITE after forced promotion (patent §6.3)",
        )

        val rok = postPromotion.cobs[B3]
        assertNotNull(rok, "White's Rok must exist at B3 after promotion")
        assertEquals(WHITE, rok.color, "Piece at B3 belongs to White")
        assertTrue(rok.isUpgraded, "Piece at B3 is now a Rok (upgraded = true)")

        val normalMoves = postPromotion.normalMovesForTurn()
        assertTrue(
            normalMoves.isNotEmpty(),
            "White's Rok must have at least one legal normal move. Available: $normalMoves",
        )

        assertFalse(
            postPromotion.isGameOver(emptyMap()),
            "Game must not be over after the forced promotion",
        )
    }

    /**
     * After promotion, [GameState.getForcedPromotions] must return empty —
     * White already holds a Rok with legal moves, so no further promotion is
     * required.
     */
    @Test
    fun afterForcedPromotion_noSubsequentForcedPromotion() {
        // "B3W" = White Rok (post-promotion, uppercase W).
        val postPromotion = parseBoardNotation("A1b/B1b/B2b/B3W/B4b/C6b/C7b/D1B w")

        assertTrue(
            postPromotion.getForcedPromotions().isEmpty(),
            "getForcedPromotions() must be empty once White holds a Rok with moves",
        )
    }

    // ── 3. AI engine — forced promotion detection ─────────────────────────────

    /**
     * Verifies that [GameState.allMovesForTurn] exposes the forced promotion as
     * a legal move so the AI engine can select it.
     */
    @Test
    fun allMovesForTurn_includesForcedPromotion_whenItIsTheSoleLegalMove() {
        val prePromotion = parseBoardNotation("A1b/B1b/B2b/B3w/B4b/C6b/C7b/D1B w")

        val allMoves = prePromotion.allMovesForTurn()
        assertEquals(
            listOf(Move(B3 to B3)),
            allMoves,
            "allMovesForTurn() must return exactly the forced-promotion move",
        )
    }

    /**
     * The AI engine must select the forced-promotion move when it is the only
     * legal option available.
     */
    @Test
    fun aiEngine_selectsForcedPromotion_whenItIsTheOnlyLegalMove(): TestResult = runTest {
        val prePromotion = parseBoardNotation("A1b/B1b/B2b/B3w/B4b/C6b/C7b/D1B w")

        val result = engine.getNextMove(prePromotion)

        assertNotNull(result.move, "AI must return a non-null move")
        assertEquals(
            Move(B3 to B3),
            result.move,
            "AI must select the forced promotion — the only legal move",
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Second game case — forced promotion at D1 (move 9)
    //
    // 1. C2→B1  C8→C9    2. D2→C2  C9→C10   3. B1→B2  D4→C8
    // 4. C2→B1  C8→C9    5. B2→A1  C7→B4    6. B1→B2  A1→B1
    // 7. B2→B3  C9→B5    8. B3→C6  D3→C7    9. D1=R
    //
    // Key flip in move 5: when Black plays C7→B4, White's Cob at A1 is
    // adjacent to B4 but NOT adjacent to C7 → pre-adjacency rule flips A1
    // to Black, giving Black the piece to play A1→B1 in move 6.
    // After move 8 White retains only D1 (Cob), all other white pieces were
    // successively flipped. Final FEN: B1b/B4b/B5b/C1b/C10b/C6b/C7b/D1W w
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Replays the second game sequence from [initialGameState] (8 regular moves)
     * and asserts that White is left with a sole Cob at D1 with no normal moves,
     * requiring a forced promotion.
     */
    @Test
    fun gameReplay_secondCase_reachesPrePromotionAtD1() {
        val moves: List<Pair<Move, Move?>> = listOf(
            Move(C2 to B1) to Move(C8 to C9),
            Move(D2 to C2) to Move(C9 to C10),
            Move(B1 to B2) to Move(D4 to C8),
            Move(C2 to B1) to Move(C8 to C9),
            Move(B2 to A1) to Move(C7 to B4),
            Move(B1 to B2) to Move(A1 to B1),
            Move(B2 to B3) to Move(C9 to B5),
            Move(B3 to C6) to Move(D3 to C7),
        )

        var state = initialGameState(WHITE)
        for ((whiteMove, blackMove) in moves) {
            state = state.applyMove(whiteMove)
            if (blackMove != null) state = state.applyMove(blackMove)
        }

        assertEquals(WHITE, state.currentTurn, "White must be to move after move 8")

        val whitePieces = state.cobs.filter { it.value.color == WHITE }
        assertEquals(1, whitePieces.size, "White has exactly one piece remaining after move 8")

        val (whiteVertex, whiteCob) = whitePieces.entries.first()
        assertEquals(D1, whiteVertex, "White's sole piece is at D1")
        assertFalse(whiteCob.isUpgraded, "White's piece at D1 is still a Cob (not yet promoted)")

        assertTrue(
            state.normalMovesForTurn().isEmpty(),
            "White has no normal moves — forced-promotion state reached",
        )

        val forcedPromotions = state.getForcedPromotions()
        assertEquals(1, forcedPromotions.size, "Exactly one forced promotion available for White")
        assertEquals(
            Move(D1 to D1),
            forcedPromotions.first(),
            "Forced promotion is an in-place move at D1",
        )
    }

    /**
     * After applying White's forced promotion at D1, verifies that:
     * - The turn stays with White (patent §6.3).
     * - White's piece at D1 is now a Rok.
     * - White has at least one normal move (D1→D2 is the only adjacent empty vertex).
     * - The game is not over.
     */
    @Test
    fun afterForcedPromotion_atD1_whiteRokHasMoves_andTurnRemainsWhite() {
        // "D1w" = White Cob at D1 (pre-promotion), Black has 7 Cobs surrounding.
        val prePromotion = parseBoardNotation("B1b/B4b/B5b/C1b/C10b/C6b/C7b/D1w w")

        val promotions = prePromotion.getForcedPromotions()
        assertEquals(1, promotions.size, "One forced promotion for White at D1")
        assertEquals(Move(D1 to D1), promotions.first(), "In-place promotion at D1")

        val postPromotion = prePromotion.applyPromotion(promotions.first())

        assertEquals(
            WHITE,
            postPromotion.currentTurn,
            "Turn must remain WHITE after forced promotion (patent §6.3)",
        )

        val rok = postPromotion.cobs[D1]
        assertNotNull(rok, "White's Rok must exist at D1 after promotion")
        assertEquals(WHITE, rok.color, "Piece at D1 belongs to White")
        assertTrue(rok.isUpgraded, "Piece at D1 is now a Rok (upgraded = true)")

        val normalMoves = postPromotion.normalMovesForTurn()
        assertTrue(
            normalMoves.isNotEmpty(),
            "White's Rok at D1 must have at least one normal move (D1→D2). Available: $normalMoves",
        )

        // The only available move should be D1→D2 (C1 is occupied by Black).
        assertTrue(
            Move(D1 to D2) in normalMoves,
            "D1→D2 must be in the list of valid moves for White's Rok",
        )

        assertFalse(
            postPromotion.isGameOver(emptyMap()),
            "Game must not be over immediately after the forced promotion",
        )
    }

    /**
     * After promotion, [GameState.getForcedPromotions] must return empty —
     * White holds a Rok at D1 that can move to D2.
     */
    @Test
    fun afterForcedPromotion_atD1_noSubsequentForcedPromotion() {
        val postPromotion = parseBoardNotation("B1b/B4b/B5b/C1b/C10b/C6b/C7b/D1W w")

        assertTrue(
            postPromotion.getForcedPromotions().isEmpty(),
            "getForcedPromotions() must be empty once White holds a Rok with valid moves",
        )
    }

    // ── 4. AI engine — regression: continues after forced promotion ───────────

    /**
     * Core regression test: reproduces the exact scenario from the bug report.
     *
     * After White's Rok is the only remaining piece (post-promotion), the AI
     * engine must be able to select a **normal** (non-promotion) move and the
     * game must continue with Black to move.
     *
     * Before the fix, the AI got stuck here and never requested another move
     * because the double-apply from the LaunchedEffect left the ViewModel in
     * an inconsistent state.
     */
    @Test
    fun aiEngine_continuesPlayingAfterForcedPromotion_regressionTest(): TestResult = runTest {
        // Post-promotion: White has only a Rok at B3 (B3W), Black has 5 Cobs + 1 Rok.
        val postPromotion = parseBoardNotation("A1b/B1b/B2b/B3W/B4b/C6b/C7b/D1B w")

        val result = engine.getNextMove(postPromotion)

        val move = assertNotNull(
            result.move,
            "AI must return a non-null move from the post-promotion position",
        )
        assertFalse(
            move.isPromotion(),
            "AI's move must NOT be an in-place promotion (piece is already a Rok)",
        )

        val legalMoves = postPromotion.allMovesForTurn()
        assertTrue(
            move in legalMoves,
            "AI's selected move $move must be among the legal moves: $legalMoves",
        )

        // Apply the move and verify the game continues normally
        val nextState = postPromotion.applyMove(move)
        assertEquals(
            BLACK,
            nextState.currentTurn,
            "After White's Rok moves, it must be Black's turn",
        )
        assertFalse(
            nextState.isGameOver(),
            "Game must not be over after White's Rok move",
        )
    }

    /**
     * Reproduces the D1 regression scenario: after forced promotion at D1,
     * the AI must select D1→D2 (the only legal Rok move) and the game must
     * continue with Black to move.
     */
    @Test
    fun aiEngine_continuesPlayingFromD1Rok_regressionTest(): TestResult = runTest {
        // Post-promotion: White has only a Rok at D1 (D1W), Black has 7 Cobs.
        // D1's only valid target is D2 (C1 is occupied by Black's Cob).
        val postPromotion = parseBoardNotation("B1b/B4b/B5b/C1b/C10b/C6b/C7b/D1W w")

        val legalMoves = postPromotion.allMovesForTurn()
        assertEquals(
            listOf(Move(D1 to D2)),
            legalMoves,
            "White's Rok at D1 must have exactly one legal move (D1→D2)",
        )

        val result = engine.getNextMove(postPromotion)

        val move = assertNotNull(
            result.move,
            "AI must return a non-null move from the D1 post-promotion position",
        )
        assertFalse(
            move.isPromotion(),
            "AI's move must NOT be an in-place promotion (piece is already a Rok)",
        )

        assertEquals(
            Move(D1 to D2),
            move,
            "AI must select D1→D2 — the only legal move for White's Rok",
        )

        val nextState = postPromotion.applyMove(move)
        assertEquals(
            BLACK,
            nextState.currentTurn,
            "After White's Rok moves D1→D2, it must be Black's turn",
        )
        assertFalse(
            nextState.isGameOver(),
            "Game must not be over after White's D1→D2 move",
        )
    }
// ═══════════════════════════════════════════════════════════════════════════
    // Third game case — forced promotion at C10, then Rok plays, Black wins
    //
    // 1.  C2→C3   C8→B4    2.  D2→C2   B4→B5    3.  C3→C4   C7→B4
    // 4.  C2→C3   D3→C7    5.  C4→C5   C7→C6    6.  C3→C4   B4→B3
    // 7.  C1→C12  B3→B2    8.  C12→B6  B2→B1    9.  D1→C1   B6→C12
    // 10. B1→B6   C1→D1    11. C12→C11 D1→D2    12. C11→C10 D2→C2
    // 13. B6→A1   C2→B1    14. B5→C9   D4→C8
    // 15. C10=R → C10→C11  A1→B6  (0-1: Black flips White's C11 → win)
    //
    // Key flip in move 14: Black D4→C8 — C9 is adjacent to C8 but NOT
    // adjacent to D4, so White's Cob at C9 (moved there same turn) gets
    // flipped back to Black. White is left with only C10.
    // Key flip in move 15 Black: A1→B6 — C11 is adjacent to B6 but NOT
    // adjacent to A1 (A1 only reaches B1-B6), so White's Rok at C11 is
    // flipped to Black → all White pieces gone → Black wins (MIT).
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Replays the third game (14 full moves) and asserts that White is left
     * with a sole Cob at C10 with no normal moves, requiring a forced promotion.
     */
    @Test
    fun gameReplay_thirdCase_reachesPrePromotionAtC10() {
        val moves: List<Pair<Move, Move?>> = listOf(
            Move(C2 to C3) to Move(C8 to B4),
            Move(D2 to C2) to Move(B4 to B5),
            Move(C3 to C4) to Move(C7 to B4),
            Move(C2 to C3) to Move(D3 to C7),
            Move(C4 to C5) to Move(C7 to C6),
            Move(C3 to C4) to Move(B4 to B3),
            Move(C1 to C12) to Move(B3 to B2),
            Move(C12 to B6) to Move(B2 to B1),
            Move(D1 to C1) to Move(B6 to C12),
            Move(B1 to B6) to Move(C1 to D1),
            Move(C12 to C11) to Move(D1 to D2),
            Move(C11 to C10) to Move(D2 to C2),
            Move(B6 to A1) to Move(C2 to B1),
            Move(B5 to C9) to Move(D4 to C8),
        )

        var state = initialGameState(WHITE)
        for ((whiteMove, blackMove) in moves) {
            state = state.applyMove(whiteMove)
            if (blackMove != null) state = state.applyMove(blackMove)
        }

        assertEquals(WHITE, state.currentTurn, "White must be to move after move 14")

        val whitePieces = state.cobs.filter { it.value.color == WHITE }
        assertEquals(1, whitePieces.size, "White has exactly one piece remaining after move 14")

        val (whiteVertex, whiteCob) = whitePieces.entries.first()
        assertEquals(C10, whiteVertex, "White's sole piece is at C10")
        assertFalse(whiteCob.isUpgraded, "White's piece at C10 is still a Cob (not yet promoted)")

        assertTrue(
            state.normalMovesForTurn().isEmpty(),
            "White has no normal moves — forced-promotion state reached",
        )

        val forcedPromotions = state.getForcedPromotions()
        assertEquals(1, forcedPromotions.size, "Exactly one forced promotion available for White")
        assertEquals(
            Move(C10 to C10),
            forcedPromotions.first(),
            "Forced promotion is an in-place move at C10",
        )
    }

    /**
     * After White's forced promotion at C10, the Rok must have at least one
     * normal move (C10→C11 should be available) and the turn must remain White.
     */
    @Test
    fun afterForcedPromotion_atC10_whiteRokHasMoves_andTurnRemainsWhite() {
        // Pre-promotion position. "C11w" = White Cob at C11.
        val prePromotion = parseBoardNotation("B1B/B6b/C10b/C11w/C4b/C5b/C6b/C8b w")

        val promotions = prePromotion.getForcedPromotions()
        assertEquals(1, promotions.size, "One forced promotion for White at C11")
        assertEquals(Move(C11 to C11), promotions.first(), "In-place promotion at C11")

        val postPromotion = prePromotion.applyPromotion(promotions.first())

        assertEquals(
            WHITE,
            postPromotion.currentTurn,
            "Turn must remain WHITE after forced promotion (patent §6.3)",
        )

        val rok = postPromotion.cobs[C11]
        assertNotNull(rok, "White's Rok must exist at C11 after promotion")
        assertTrue(rok.isUpgraded, "Piece at C11 must be a Rok (upgraded)")

        val normalMoves = postPromotion.normalMovesForTurn()
        assertTrue(
            normalMoves.isNotEmpty(),
            "White's Rok at C11 must have at least one normal move. Available: $normalMoves",
        )
        assertTrue(
            Move(C11 to C12) in normalMoves,
            "C11→C12 must be a valid move for White's Rok (this is the move played in the real game)",
        )

        assertFalse(
            postPromotion.isGameOver(emptyMap()),
            "Game must not be over immediately after the forced promotion",
        )
    }

    /**
     * AI regression: after the forced promotion at C10, the engine must select
     * a valid non-promotion move and the game must continue.
     */
    @Test
    fun aiEngine_continuesPlayingFromC10Rok_regressionTest(): TestResult = runTest {
        val postPromotion = parseBoardNotation("B1B/B5b/B6b/C4b/C5b/C6b/C8b/C10W w")

        val result = engine.getNextMove(postPromotion)

        val move = assertNotNull(
            result.move,
            "AI must return a non-null move from the C10 post-promotion position",
        )
        assertFalse(
            move.isPromotion(),
            "AI's move must NOT be an in-place promotion (piece is already a Rok)",
        )

        val legalMoves = postPromotion.allMovesForTurn()
        assertTrue(
            move in legalMoves,
            "AI's selected move $move must be among the legal moves: $legalMoves",
        )

        val nextState = postPromotion.applyMove(move)
        assertEquals(
            BLACK,
            nextState.currentTurn,
            "After White's Rok moves, it must be Black's turn",
        )
        assertFalse(
            nextState.isGameOver(),
            "Game must not be over after White's Rok move",
        )
    }

    /**
     * Full-sequence test: replays the entire game including the two-step
     * White move 15 (forced promotion + Rok move) and Black's winning reply,
     * then asserts that Black wins by MIT (all White pieces flipped).
     */
    @Test
    fun fullSequence_thirdCase_blackWinsAfterPromotionSequence() {
        val regularMoves: List<Pair<Move, Move?>> = listOf(
            Move(C2 to C3) to Move(C8 to B4),
            Move(D2 to C2) to Move(B4 to B5),
            Move(C3 to C4) to Move(C7 to B4),
            Move(C2 to C3) to Move(D3 to C7),
            Move(C4 to C5) to Move(C7 to C6),
            Move(C3 to C4) to Move(B4 to B3),
            Move(C1 to C12) to Move(B3 to B2),
            Move(C12 to B6) to Move(B2 to B1),
            Move(D1 to C1) to Move(B6 to C12),
            Move(B1 to B6) to Move(C1 to D1),
            Move(C12 to C11) to Move(D1 to D2),
            Move(C11 to C10) to Move(D2 to C2),
            Move(B6 to A1) to Move(C2 to B1),
            Move(B5 to C9) to Move(D4 to C8),
        )

        var state = initialGameState(WHITE)
        for ((whiteMove, blackMove) in regularMoves) {
            state = state.applyMove(whiteMove)
            if (blackMove != null) state = state.applyMove(blackMove)
        }

        // Move 15 White — step 1: forced promotion (keeps same turn)
        val promotions = state.getForcedPromotions()
        assertEquals(
            1, promotions.size,
            "White must have exactly one forced promotion before move 15",
        )
        state = state.applyPromotion(promotions.first())

        assertEquals(
            WHITE, state.currentTurn,
            "Turn must remain WHITE after the forced promotion",
        )

        // Move 15 White — step 2: Rok moves C10→C11 (as in the real game)
        assertTrue(
            Move(C10 to C11) in state.allMovesForTurn(),
            "C10→C11 must be legal for White's Rok after the promotion",
        )
        state = state.applyMove(Move(C10 to C11))

        assertEquals(BLACK, state.currentTurn, "After White's Rok move it must be Black's turn")
        assertFalse(state.isGameOver(), "Game must not be over yet before Black's final move")

        // Move 15 Black — A1→B6 (winning move: flips White's C11)
        state = state.applyMove(Move(A1 to B6))

        // White must have no pieces left → Black wins by MIT
        val whitePiecesLeft = state.cobs.values.count { it.color == WHITE }
        assertEquals(
            0, whitePiecesLeft,
            "All White pieces must have been flipped after A1→B6: White has $whitePiecesLeft remaining",
        )

        assertTrue(state.isGameOver(), "Game must be over after Black's A1→B6")

        val matchState = state.getMatchState()
        assertEquals(
            GameEndReason.MIT,
            matchState.gameEndReason,
            "Black must win (MIT) after flipping White's last Rok",
        )
        assertEquals(BLACK, matchState.winner, "Winner must be Black")
    }
}
