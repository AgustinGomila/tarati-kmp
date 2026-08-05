package com.agustin.tarati.game.ai.legacy

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
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.game.ai.legacy.RegressionTest.Companion.NODE_COUNT_TOLERANCE_PCT
import com.agustin.tarati.game.ai.tournament.engine.base.newEngine
import com.agustin.tarati.game.ai.tournament.engine.base.personalityEngine
import com.agustin.tarati.game.ai.tournament.manager.TournamentConfig
import com.agustin.tarati.game.ai.tournament.manager.TournamentResult
import com.agustin.tarati.game.ai.tournament.manager.TournamentRunner
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test suite for AI engine changes.
 *
 * Run this suite BEFORE and AFTER any modification to
 * [MinimaxStrategy],
 * [MoveEvaluator], or
 * [TaratiAI] to detect quality or behavior regressions.
 *
 * ## Three layers
 *
 * **Layer 1 — Quality (self-play tournament)**
 * Pits the new engine against [LegacyTaratiAI] (frozen snapshot taken before
 * the change) over N games. Threshold: win rate >= 45%. If it fails, the change
 * degraded playing strength regardless of how fast it became.
 *
 * **Layer 2 — Move regression (fixed position suite)**
 * For each curated position, the baseline best move is hard-coded. The new engine
 * must reproduce it. A changed move forces a manual review — it may be better,
 * but that needs to be confirmed.
 *
 * **Layer 3 — Performance regression (node count invariant)**
 * [nodesEvaluated] must never increase. For pure optimizations it stays identical;
 * for branching reductions it decreases. An increase means the change added work.
 *
 * ## Updating baselines
 * After a change is accepted, update the expected moves in [fixedSuite] and
 * the node counts in [nodeBaselines] to reflect the new ground truth.
 */
class RegressionTest {

    // ── Tournament config ─────────────────────────────────────────────────────

    private val tournamentConfig = TournamentConfig(
        gamesPerMatch = 100,
        maxMovesPerGame = 150,
    )

    // ── Fixed positions ───────────────────────────────────────────────────────

    /**
     * Mid-game: both sides advanced into neutral, contesting A1.
     * Baseline: WHITE plays B2→A1 (center capture) at every depth.
     */
    private val midGamePosition: GameState = GameState(
        cobs = mapOf(
            C3 to Cob(WHITE),
            B1 to Cob(WHITE),
            B2 to Cob(WHITE),
            B3 to Cob(WHITE),
            C9 to Cob(BLACK),
            B4 to Cob(BLACK),
            B5 to Cob(BLACK),
            B6 to Cob(BLACK),
        ),
        currentTurn = WHITE,
    )

    /**
     * Final: mostly roks, fully interleaved. BLACK to move, losing position.
     * Baseline: BLACK plays C12→B6 at every depth.
     */
    private val finalPosition: GameState = GameState(
        cobs = mapOf(
            A1 to Cob(WHITE, true),
            B5 to Cob(WHITE, true),
            C8 to Cob(WHITE, true),
            D3 to Cob(WHITE),
            B2 to Cob(BLACK, true),
            C3 to Cob(BLACK, true),
            C12 to Cob(BLACK, true),
            D2 to Cob(BLACK),
        ),
        currentTurn = BLACK,
    )

    // ── Fixed position suite with baseline best moves ─────────────────────────

    private data class FixedPosition(
        val label: String,
        val position: GameState,
        val expectedFrom: Vertex,
        val expectedTo: Vertex,
    )

    /**
     * Expected moves recorded from [NormalMovesBaselineTest] before this change.
     * Update these values after each accepted optimization.
     */
    // La apertura se excluye como ancla por herencia histórica: sin opening book
    // (ver EvaluationConfig.openingBookEnabled) CHAMPION la resuelve por búsqueda sobre una posición
    // simétrica con varias mejores jugadas espejo empatadas. Con el **desempate determinista** (keep-first,
    // 2026-08-05) ya es reproducible y podría re-anclarse; se conservan solo Mid-game/Final por simplicidad.
    private val fixedSuite = listOf(
        FixedPosition("Mid-game", midGamePosition, B2, A1),  // stable across all depths
        FixedPosition("Final", finalPosition, C12, B6),  // stable across all depths
    )

    // ── Node baselines from last accepted run ─────────────────────────────────

    private data class NodeBaseline(
        val label: String,
        val position: GameState,
        /** Maximum allowed nodesEvaluated at CHAMPION after the change. */
        val maxNodesChampion: Long,
    )

    /**
     * Recorded from [NormalMovesBaselineTest] before this optimization.
     * A pure optimization leaves these unchanged.
     * A branching reduction lowers them — update after each accepted change.
     *
     * Actualizado 2026-08-05 al activar la quiescence en CHAMPION (quiescenceMaxPlies=4): la búsqueda de
     * hoja resuelve las capturas pendientes, lo que **sube** el conteo de nodos — sobre todo en el final
     * rico en Roks (Mid-game 2227→4282, ~1.9×; Final 52576→148350, ~2.8×), que es la fase donde la
     * quiescence extiende las cascadas de conversión. El cap de plies (4) acota el peor caso del final.
     */
    private val nodeBaselines = listOf(
        NodeBaseline("Mid-game", midGamePosition, 4_282L),
        NodeBaseline("Final", finalPosition, 148_350L),
    )

    // ── Print helper ──────────────────────────────────────────────────────────

    private fun printMatchSummary(result: TournamentResult, tag: String) {
        val total = result.winsA + result.winsB + result.draws
        val winRate = if (total > 0) result.winsA.toDouble() / total * 100 else 0.0
        val line = "─".repeat(70)
        TestLog.info("\n$line\n  $tag\n$line")
        TestLog.info("  ${result.engineA.name} vs ${result.engineB.name}")
        TestLog.info(
            "  ${result.winsA} – ${result.winsB} – ${result.draws}  " +
                    "(W-L-D)  win rate: ${"%.1f".format(winRate)}%"
        )
        result.performanceMetrics?.let { m ->
            TestLog.info(
                "  Avg move time: ${"%.1f".format(m.averageMoveTimeMs)}ms  " +
                        "Avg nodes: ${"%.0f".format(m.averageNodesPerMove)}"
            )
        }
        TestLog.info(line)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAYER 1 — QUALITY (self-play tournament)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * New engine vs Legacy at CHAMPION — 100 games, alternating colors.
     *
     * Win rate >= 45%: new engine is at least as strong as legacy.
     * Win rate >= 55%: the change also improved play quality.
     */
    @Test
    fun quality_regression_champion() {
        val tournament = TournamentRunner()

        val result = tournament.runEngineMatch(
            engineA = personalityEngine("New"),
            engineB = legacyEngine(),
            configA = EvaluationConfig.CHAMPION,
            configB = EvaluationConfig.CHAMPION,
            tournamentConfig = tournamentConfig,
            logInfo = TestLog::info,
        )

        printMatchSummary(result, "Quality regression — CHAMPION (${tournamentConfig.gamesPerMatch} games)")

        val total = result.winsA + result.winsB + result.draws
        val winRate = result.winsA.toDouble() / total

        assertTrue(
            "New engine win rate vs Legacy: ${"%.1f".format(winRate * 100)}% " +
                    "(threshold: >= 45%). The change degraded playing strength.",
            winRate >= 0.45,
        )
    }

    /**
     * New engine vs Legacy at HARD — 100 games.
     * Catches regressions that appear at mid-depth but not at CHAMPION.
     *
     * Threshold is 40% (lower than CHAMPION's 45%) because HARD games are
     * shorter and noisier — a 30-game sample at this depth has a ±18% CI,
     * making 45% statistically unreliable. 100 games narrows the CI to ±10%.
     */
    @Test
    fun quality_regression_hard() {
        val tournament = TournamentRunner()

        val result = tournament.runEngineMatch(
            engineA = personalityEngine("New"),
            engineB = legacyEngine(),
            configA = EvaluationConfig.getByDifficulty(Difficulty.HARD),
            configB = EvaluationConfig.getByDifficulty(Difficulty.HARD),
            tournamentConfig = tournamentConfig,   // 100 games, same as CHAMPION
            logInfo = TestLog::info,
        )

        printMatchSummary(result, "Quality regression — HARD (${tournamentConfig.gamesPerMatch} games)")

        val total = result.winsA + result.winsB + result.draws
        val winRate = result.winsA.toDouble() / total

        assertTrue(
            "New engine win rate vs Legacy at HARD: ${"%.1f".format(winRate * 100)}% " +
                    "(threshold: >= 40%). With 100 games the CI is ±10%, so <40% is a real regression.",
            winRate >= 0.40,
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAYER 2 — MOVE REGRESSION (fixed position suite)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Best move must match baseline at CHAMPION depth for every position.
     * A changed move fails the test and requires manual review.
     */
    @Test
    fun move_regression_champion() {
        val engine = newEngine()
        engine.setConfig(EvaluationConfig.CHAMPION)

        val line = "─".repeat(72)
        TestLog.info("\n$line\n  Move regression — CHAMPION\n$line")
        TestLog.info(
            "  %-12s | %-10s | %-10s | %s".format(
                "Position", "Expected", "Actual", "Status"
            )
        )
        TestLog.info(line)

        fixedSuite.forEach { pos ->
            engine.clearHistory()
            val result = runBlocking { engine.getNextMove(pos.position) }
            val move = result.move
            val ok = move?.from == pos.expectedFrom && move.to == pos.expectedTo
            TestLog.info(
                "  %-12s | %-10s | %-10s | %s".format(
                    pos.label,
                    "${pos.expectedFrom.name}→${pos.expectedTo.name}",
                    "${move?.from?.name}→${move?.to?.name}",
                    if (ok) "OK" else "CHANGED — review required",
                )
            )

            assertEquals(
                "${pos.label}: expected from=${pos.expectedFrom.name}, " +
                        "got ${move?.from?.name}. " +
                        "If the new move is better, update fixedSuite.",
                pos.expectedFrom, move?.from,
            )
            assertEquals(
                "${pos.label}: expected to=${pos.expectedTo.name}, " +
                        "got ${move?.to?.name}.",
                pos.expectedTo, move?.to,
            )
        }
        TestLog.info(line)
    }

    /**
     * Informational depth sweep: prints the best move at each difficulty level.
     *
     * No assertions are made here. At lower depths (EASY, MEDIUM) a different
     * move may be objectively better than the CHAMPION baseline — shorter search
     * trees find different optimal paths. This is expected, not a regression.
     *
     * Use this output to spot unexpected instability (e.g. the move flipping
     * between two unrelated options at every depth) rather than to enforce exact
     * move reproduction. Assertions are only in [move_regression_champion].
     */
    @Test
    fun move_regression_all_depths() {
        val engine = newEngine()

        val line = "─".repeat(80)
        TestLog.info("\n$line\n  Move survey — depth sweep (informational, no assertions)\n$line")
        TestLog.info(
            "  %-12s | %-10s | %-12s | %s".format(
                "Position", "Difficulty", "Best move", "Score"
            )
        )
        TestLog.info(line)

        fixedSuite.forEach { pos ->
            Difficulty.entries.forEach { difficulty ->
                engine.clearHistory()
                engine.setConfig(EvaluationConfig.getByDifficulty(difficulty))
                val result = runBlocking { engine.getNextMove(pos.position) }
                val move = result.move
                TestLog.info(
                    "  %-12s | %-10s | %-12s | %.0f".format(
                        pos.label,
                        difficulty.name,
                        "${move?.from?.name}→${move?.to?.name}",
                        result.score,
                    )
                )
            }
        }
        TestLog.info(line)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAYER 3 — PERFORMANCE REGRESSION (node count invariant)
    // ════════════════════════════════════════════════════════════════════════

    companion object {
        /**
         * Acceptable node count variance between runs at identical code.
         * Con el desempate **determinista** (keep-first) el conteo es reproducible entre corridas;
         * el 5 % queda como colchón ante variaciones benignas (orden de iteración de estructuras,
         * futuros micro-cambios de ordenamiento) — ya no absorbe un tiebreak aleatorio.
         */
        private const val NODE_COUNT_TOLERANCE_PCT = 0.05
    }

    /**
     * [nodesEvaluated] at CHAMPION must not grow significantly after a change.
     *
     * Con el desempate determinista (keep-first en [searchBestMove]) el conteo es
     * reproducible; la tolerancia de [NODE_COUNT_TOLERANCE_PCT]% (5%) queda como colchón
     * ante variaciones benignas de ordenamiento, no ante un tiebreak aleatorio.
     *
     * Interpretation:
     *   actual <= baseline * (1 + tolerance) → OK        (noise or improvement)
     *   actual  > baseline * (1 + tolerance) → REGRESSION (real increase in work)
     *
     * For branching reductions the actual count should drop well below the
     * tolerance band. Update [nodeBaselines] after each accepted optimization.
     */
    @Test
    fun performance_regression_nodes() {
        val engine = newEngine()
        engine.setConfig(EvaluationConfig.CHAMPION)

        val line = "─".repeat(80)
        TestLog.info("\n$line\n  Performance regression — node count @ CHAMPION (tolerance ±${(NODE_COUNT_TOLERANCE_PCT * 100).toInt()}%)\n$line")
        TestLog.info(
            "  %-12s | %12s | %12s | %10s | %8s | %s".format(
                "Position", "Baseline", "Actual", "Delta", "Delta%", "Status"
            )
        )
        TestLog.info(line)

        nodeBaselines.forEach { nb ->
            engine.clearHistory()
            runBlocking { engine.getNextMove(nb.position) }
            val actual = engine.getDiagnostics().nodesEvaluated
            val delta = actual - nb.maxNodesChampion
            val deltaPct = delta.toDouble() / nb.maxNodesChampion * 100
            val threshold = (nb.maxNodesChampion * (1 + NODE_COUNT_TOLERANCE_PCT)).toLong()
            val ok = actual <= threshold
            TestLog.info(
                "  %-12s | %12d | %12d | %+10d | %+7.1f%% | %s".format(
                    nb.label, nb.maxNodesChampion, actual, delta, deltaPct,
                    if (ok) "OK" else "REGRESSION — nodes grew beyond tolerance",
                )
            )

            assertTrue(
                "${nb.label}: nodesEvaluated = $actual exceeds baseline ${nb.maxNodesChampion} " +
                        "by ${"%.1f".format(deltaPct)}% (tolerance: ${(NODE_COUNT_TOLERANCE_PCT * 100).toInt()}%). " +
                        "Update nodeBaselines only if the increase is intentional.",
                actual <= threshold,
            )
        }
        TestLog.info(line)
    }
}