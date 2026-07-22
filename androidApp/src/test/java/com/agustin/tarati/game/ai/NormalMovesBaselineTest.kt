package com.agustin.tarati.game.ai

import com.agustin.tarati.testutil.TestLog
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.C9
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Baseline metrics for AI search optimizations.
 *
 * Run BEFORE any optimization change, record the output, then run AFTER
 * and compare. Key invariant: [nodesEvaluated] must be identical — only
 * [searchTimeMs] should improve.
 *
 * ## Board topology
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
 *
 *   WHITE half (≤2 moves from D1/D2): D1, D2, C1, C2, C3, C12, B1
 *   BLACK half (≤2 moves from D3/D4): D3, D4, C6, C7, C8, C9, B4
 *   Neutral    (>2 moves from either): A1, B2, B3, B5, B6, C4, C5, C10, C11
 *
 * ## Positions (always exactly 4 WHITE + 4 BLACK = 8 pieces)
 *
 * - **Opening**: initial state, each side in their own half.
 * - **Mid-game**: no upgrades, both sides have advanced past the center line
 *   into neutral territory, actively contesting A1 area.
 * - **Final**: mostly roks, pieces fully interleaved across all zones.
 */
class NormalMovesBaselineTest {

    private lateinit var engine: TaratiAI

    @Before
    fun setup() {
        engine = TaratiAI()
        engine.clearHistory()
    }

    // ── Positions ────────────────────────────────────────────────────────────

    /** Standard initial position: WHITE at D1/D2/C1/C2, BLACK at D3/D4/C7/C8. */
    private val openingPosition: GameState = initialGameState()

    /**
     * Mid-game: 4+4 cobs, no upgrades, both sides in neutral territory.
     * WHITE advanced from their half into B2/B3 (neutral) and C4 (neutral).
     * BLACK advanced from their half into B5/B6 (neutral) and C11 (neutral).
     * Active contest around the center with realistic piece distribution.
     */
    private val midGamePosition: GameState = GameState(
        cobs = mapOf(
            // WHITE — departed home, crossing neutral toward BLACK
            C3 to Cob(WHITE), // still on white half, just advanced
            B1 to Cob(WHITE), // white half bridge
            B2 to Cob(WHITE), // neutral — pushing toward center
            B3 to Cob(WHITE), // neutral — threatening A1
            // BLACK — departed home, crossing neutral toward WHITE
            C9 to Cob(BLACK), // still on black half, just advanced
            B4 to Cob(BLACK), // black half bridge
            B5 to Cob(BLACK), // neutral — pushing toward center
            B6 to Cob(BLACK), // neutral — threatening A1
        ),
        currentTurn = WHITE,
    )

    /**
     * Final position: 4+4, mostly roks, fully interleaved across all zones.
     * WHITE has pieces deep in BLACK territory; BLACK has pieces deep in WHITE.
     * No clear front line — typical late-game endgame scramble.
     */
    private val finalPosition: GameState = GameState(
        cobs = mapOf(
            // WHITE — spread across the board, mostly upgraded
            A1 to Cob(WHITE, true),  // center rok
            B5 to Cob(WHITE, true),  // neutral rok
            C8 to Cob(WHITE, true),  // deep in BLACK half
            D3 to Cob(WHITE), // in BLACK home, still cob
            // BLACK — spread across the board, mostly upgraded
            B2 to Cob(BLACK, true),  // neutral rok
            C3 to Cob(BLACK, true),  // deep in WHITE half
            C12 to Cob(BLACK, true),  // deep in WHITE half
            D2 to Cob(BLACK), // in WHITE home, still cob
        ),
        currentTurn = BLACK,
    )

    // ── Benchmark helper ─────────────────────────────────────────────────────

    private data class SearchResult(
        val positionLabel: String,
        val difficulty: Difficulty,
        val nodesEvaluated: Long,
        val cutoffs: Int,
        val cacheHits: Int,
        val searchTimeMs: Long,
        val bestMove: Move?,
        val score: Double,
    )

    private fun measure(
        label: String,
        position: GameState,
        difficulty: Difficulty,
        warmupRuns: Int = 1,
    ): SearchResult {
        engine.clearHistory()
        engine.setConfig(EvaluationConfig.getByDifficulty(difficulty))

        // Warmup: lets JIT compile the hot path before measuring
        repeat(warmupRuns) {
            engine.clearHistory()
            runBlocking { engine.getNextMove(position) }
        }

        // Measured run
        engine.clearHistory()
        val t0 = System.currentTimeMillis()
        val result = runBlocking { engine.getNextMove(position) }
        val elapsed = System.currentTimeMillis() - t0

        val diag = engine.getDiagnostics()
        return SearchResult(
            positionLabel = label,
            difficulty = difficulty,
            nodesEvaluated = diag.nodesEvaluated,
            cutoffs = diag.cutoffs,
            cacheHits = diag.cacheHits,
            searchTimeMs = elapsed,
            bestMove = result.move,
            score = result.score,
        )
    }

    private fun printHeader(title: String) {
        val line = "─".repeat(90)
        TestLog.info("\n$line")
        TestLog.info("  $title")
        TestLog.info(line)
        TestLog.info(
            "%-12s | %-10s | %14s | %12s | %10s | %10s | %s".format(
                "Position", "Difficulty", "nodesEvaluated", "cutoffs", "cacheHits", "time(ms)", "bestMove"
            )
        )
        TestLog.info(line)
    }

    private fun printRow(r: SearchResult) {
        TestLog.info(
            "%-12s | %-10s | %14d | %12d | %10d | %10d | %s->%s (%.0f)".format(
                r.positionLabel,
                r.difficulty.name,
                r.nodesEvaluated,
                r.cutoffs,
                r.cacheHits,
                r.searchTimeMs,
                r.bestMove?.from?.name ?: "null",
                r.bestMove?.to?.name ?: "null",
                r.score,
            )
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Core baseline: MEDIUM (depth=3) and CHAMPION (depth=7) for all positions.
     * Expected post-optimization: nodesEvaluated unchanged, searchTimeMs lower.
     */
    @Test
    fun baselineAllPositions() {
        val positions = listOf(
            "Opening" to openingPosition,
            "Mid-game" to midGamePosition,
            "Final" to finalPosition,
        )

        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.CHAMPION)) {
            printHeader("Baseline — $difficulty (depth=${difficulty.depth})")
            for ((label, pos) in positions) {
                printRow(measure(label, pos, difficulty))
            }
        }
        TestLog.info()
    }

    /**
     * Depth sweep on mid-game — shows how nodesEvaluated scales with depth.
     */
    @Test
    fun depthSweepMidGame() {
        printHeader("Depth sweep — mid-game position")
        for (difficulty in Difficulty.entries) {
            printRow(measure("Mid-game", midGamePosition, difficulty))
        }
        TestLog.info()
    }

    /**
     * Depth sweep on final — emphasizes terminal-state and rok-branching paths.
     */
    @Test
    fun depthSweepFinalPosition() {
        printHeader("Depth sweep — final position")
        for (difficulty in Difficulty.entries) {
            printRow(measure("Final", finalPosition, difficulty))
        }
        TestLog.info()
    }

    /**
     * Transposition hit rate on mid-game at CHAMPION depth.
     */
    @Test
    fun transpositionHitRateBaseline() {
        val line = "─".repeat(50)
        TestLog.info("\n$line")
        TestLog.info("  Transposition hit rate — mid-game @ CHAMPION")
        TestLog.info(line)

        engine.clearHistory()
        engine.setConfig(EvaluationConfig.CHAMPION)

        val result = runBlocking { engine.getNextMove(midGamePosition) }
        val diag = engine.getDiagnostics()

        val hitRate = if (diag.nodesEvaluated > 0)
            diag.cacheHits.toDouble() / diag.nodesEvaluated * 100.0
        else 0.0

        TestLog.info("Nodes evaluated   : ${diag.nodesEvaluated}")
        TestLog.info("Transposition hits: ${diag.cacheHits}")
        TestLog.info("Hit rate          : %.1f%%".format(hitRate))
        TestLog.info("Alpha-beta cutoffs: ${diag.cutoffs}")
        TestLog.info("Best move         : ${result.move?.from?.name} -> ${result.move?.to?.name}")
        TestLog.info(line)
        TestLog.info()
    }
}