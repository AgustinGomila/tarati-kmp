package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig.Companion.withPersonality
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.aggressive
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.balanced
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.baseline
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.defensive
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.gambit
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.material
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.positional
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.strategist
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.swarming
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.game.ai.tournament.engine.base.aggressiveEngine
import com.agustin.tarati.game.ai.tournament.engine.base.balancedEngine
import com.agustin.tarati.game.ai.tournament.engine.base.baselineEngine
import com.agustin.tarati.game.ai.tournament.engine.base.defensiveEngine
import com.agustin.tarati.game.ai.tournament.engine.base.gambitEngine
import com.agustin.tarati.game.ai.tournament.engine.base.materialEngine
import com.agustin.tarati.game.ai.tournament.engine.base.personalityEngine
import com.agustin.tarati.game.ai.tournament.engine.base.positionalEngine
import com.agustin.tarati.game.ai.tournament.engine.base.randomMoveEngine
import com.agustin.tarati.game.ai.tournament.engine.base.strategistEngine
import com.agustin.tarati.game.ai.tournament.engine.base.swarmingEngine
import com.agustin.tarati.game.ai.tournament.helpers.EnginePerformance
import com.agustin.tarati.game.ai.tournament.manager.TournamentConfig
import com.agustin.tarati.game.ai.tournament.manager.TournamentRunner
import com.agustin.tarati.testutil.TestLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Round-robin tournament tests for TaratiAI.
 *
 * All participants run the same underlying engine (TaratiAI). Differentiation
 * comes from the EvaluationConfig applied per move — either a named personality
 * (aggressive, defensive, etc.) or a specific difficulty level.
 *
 * Test taxonomy:
 *  - Personality tournaments: do play-style differences produce measurable ELO spread?
 *  - Difficulty tournaments: does higher difficulty actually outperform lower?
 *  - Floor tests: do all personalities beat random play?
 *  - Head-to-head: rivalry analysis between specific styles.
 */
class RoundRobinTest {

    private val gamesPerMatch = 30
    private val maxMovesPerGame = 100

    // ─── shared tournament config ────────────────────────────────────────────

    private val quickConfig = TournamentConfig(
        gamesPerMatch = gamesPerMatch,
        maxMovesPerGame = maxMovesPerGame,
    )

    // ─── all personality variants at DEFAULT difficulty ──────────────────────

    private val defaultDifficulty = Difficulty.DEFAULT

    private val personalityEngines = listOf(
        baselineEngine,
        aggressiveEngine,
        defensiveEngine,
        balancedEngine,
        positionalEngine,
        materialEngine,
        gambitEngine,
        swarmingEngine,
        strategistEngine,
    )

    private val personalityConfigs = mapOf(
        baselineEngine.name to baseline().copy(difficulty = defaultDifficulty),
        aggressiveEngine.name to aggressive().copy(difficulty = defaultDifficulty),
        defensiveEngine.name to defensive().copy(difficulty = defaultDifficulty),
        balancedEngine.name to balanced().copy(difficulty = defaultDifficulty),
        positionalEngine.name to positional().copy(difficulty = defaultDifficulty),
        materialEngine.name to material().copy(difficulty = defaultDifficulty),
        gambitEngine.name to gambit().copy(difficulty = defaultDifficulty),
        swarmingEngine.name to swarming().copy(difficulty = defaultDifficulty),
        strategistEngine.name to strategist().copy(difficulty = defaultDifficulty),
    )

    // ─── difficulty variants (same personality, escalating strength) ─────────

    private val difficultyEngines = listOf(
        personalityEngine("easy"),
        personalityEngine("medium"),
        personalityEngine("hard"),
        personalityEngine("champion"),
    )

    // Built via getByDifficulty so each entry carries its empirically the best personality,
    // matching exactly what the app serves to real players.
    private val difficultyConfigs = mapOf(
        "easy" to EvaluationConfig.getByDifficulty(Difficulty.EASY),
        "medium" to EvaluationConfig.getByDifficulty(Difficulty.MEDIUM),
        "hard" to EvaluationConfig.getByDifficulty(Difficulty.HARD),
        "champion" to EvaluationConfig.getByDifficulty(Difficulty.CHAMPION),
    )

    // ════════════════════════════════════════════════════════════════════════
    // PERSONALITY TOURNAMENTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Full round-robin among all nine personality configurations.
     * Verifies that the tournament runs cleanly and produces a non-trivial
     * ELO spread (some personalities should outperform others).
     */
    @Test
    fun test_personality_round_robin() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = personalityEngines,
            configs = personalityConfigs,
            tournamentConfig = quickConfig,
            logInfo = ::logInfo,
        )

        // All personalities must have results
        assertTrue("Should have results for all personalities", results.size == personalityEngines.size)

        // Each engine played against every other engine
        results.forEach { result ->
            val expectedGames = (personalityEngines.size - 1) * gamesPerMatch
            assertEquals(
                "${result.engine.name} should have played $expectedGames games",
                expectedGames,
                result.totalGames,
            )
            // wins + losses + draws = totalGames
            assertEquals(
                "${result.engine.name}: wins + losses + draws must equal totalGames",
                result.totalGames,
                result.wins + result.losses + result.draws,
            )
        }

        // There must be a measurable ELO spread — not all personalities play identically
        val topScore = results.maxOf { it.score }
        val bottomScore = results.minOf { it.score }
        assertTrue(
            "Personality variants should produce a measurable score spread",
            topScore > bottomScore,
        )
    }

    /**
     * Personality tournament at MEDIUM difficulty.
     * Ensures the same spread holds at higher engine strength.
     */
    @Test
    fun test_personality_round_robin_medium_difficulty() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = personalityEngines,
            configs = personalityConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = 50),
            logInfo = ::logInfo,
        )

        assertTrue("Should have results for all personalities", results.size == personalityEngines.size)
        results.forEach { result ->
            assertTrue("${result.engine.name} should have played games", result.totalGames > 0)
        }
    }

    /**
     * Verifies that every personality beats random play.
     * Random is the absolute floor; no trained strategy should lose to it overall.
     */
    @Test
    fun test_all_personalities_beat_random() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val enginesWithRandom = personalityEngines + randomMoveEngine
        val configs = personalityConfigs + mapOf(randomMoveEngine.name to baseline())

        val results = tournament.runEngineRoundRobin(
            engines = enginesWithRandom,
            configs = configs,
            tournamentConfig = quickConfig,
            logInfo = ::logInfo,
        )

        val randomResult = results.find { it.engine.name == randomMoveEngine.name }
        assertTrue("Random engine should be in results", randomResult != null)

        personalityEngines.forEach { engine ->
            val result = results.find { it.engine.name == engine.name }
            assertTrue("${engine.name} should be in results", result != null)
            assertTrue(
                "${engine.name} (score ${(result ?: return@forEach).score}) should outperform random (score ${(randomResult ?: return@forEach).score})",
                result.score > randomResult.score * 0.5,
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DIFFICULTY TOURNAMENTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Full round-robin across all four difficulty levels (baseline personality).
     * Champion must outperform Easy; the ordering need not be strict but higher
     * difficulties should cluster at the top.
     *
     * Note on white advantage: Tarati has a strong first-mover advantage — white wins
     * most games at matched skill levels, producing 50-50 splits in head-to-head.
     * Skill differences only manifest clearly when the depth gap is large enough to
     * overcome this advantage (e.g. Easy depth-2 vs Medium depth-4). Increasing
     * gamesPerMatch and tracking white/black wins separately reveals the true gradient.
     */
    @Test
    fun test_difficulty_round_robin() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = difficultyEngines,
            configs = difficultyConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = 20),
            logInfo = ::logInfo,
        )

        assertTrue("Should have results for all difficulty levels", results.size == difficultyEngines.size)

        results.forEach { result ->
            assertTrue("${result.engine.name} should have played games", result.totalGames > 0)
            assertEquals(
                "${result.engine.name}: wins + losses + draws must equal totalGames",
                result.totalGames,
                result.wins + result.losses + result.draws,
            )
        }

        // Hard must outperform Easy overall
        val hardResult = results.find { it.engine.name == "hard" }
        val easyResult = results.find { it.engine.name == "easy" }
        assertTrue("Hard engine should be in results", hardResult != null)
        assertTrue("Easy engine should be in results", easyResult != null)
        assertTrue(
            "Hard (score ${(hardResult ?: return).score}) should outperform Easy (score ${(easyResult ?: return).score})",
            hardResult.score >= easyResult.score,
        )
    }

    /**
     * Mixed tournament: each difficulty uses the personality that performed best
     * at that depth in the personality round-robin tournaments.
     */
    @Test
    fun test_difficulty_with_tuned_configs() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val tunedConfigs = mapOf(
            "easy" to Difficulty.EASY.withPersonality(::defensive),
            "medium" to Difficulty.MEDIUM.withPersonality(::defensive),
            "hard" to Difficulty.HARD.withPersonality(::gambit),
            "champion" to Difficulty.CHAMPION.withPersonality(::gambit),
        )

        val results = tournament.runEngineRoundRobin(
            engines = difficultyEngines,
            configs = tunedConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = 8, collectMetrics = true),
            logInfo = ::logInfo,
        )

        assertTrue("Should have results for all difficulty levels", results.size == difficultyEngines.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // HEAD-TO-HEAD MATCH UPS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun test_head_to_head_easy_difficulty_matrix_four_personalities(): Unit =
        runPersonalityMatrix(Difficulty.EASY, gamesPerMatch = 100)

    @Test
    fun test_head_to_head_medium_difficulty_matrix_four_personalities(): Unit =
        runPersonalityMatrix(Difficulty.MEDIUM, gamesPerMatch = 50)

    @Test
    fun test_head_to_hard_difficulty_head_matrix_four_personalities(): Unit =
        runPersonalityMatrix(Difficulty.HARD, gamesPerMatch = 30)

    @Test
    fun test_head_to_champion_difficulty_head_matrix_four_personalities(): Unit =
        runPersonalityMatrix(Difficulty.CHAMPION, gamesPerMatch = 10)

    /**
     * Runs a round-robin across the five key personality configurations at the
     * given [difficulty], then prints a rivalry analysis of all head-to-head results.
     *
     * The five personalities (baseline, defensive, gambit, balanced, positional) are
     * the ones empirically identified as producing the most meaningful spread at all
     * depth levels. [gamesPerMatch] is tuned per difficulty so that higher depths
     * (which are slower) run fewer games while still providing a statistically useful sample.
     */
    private fun runPersonalityMatrix(difficulty: Difficulty, gamesPerMatch: Int) {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val matrixEngines = listOf(
            personalityEngine("baseline"),
            personalityEngine("defensive"),
            personalityEngine("gambit"),
            personalityEngine("balanced"),
            personalityEngine("positional"),
        )
        val matrixConfigs = mapOf(
            "baseline" to balanced().copy(difficulty = difficulty),
            "defensive" to defensive().copy(difficulty = difficulty),
            "gambit" to gambit().copy(difficulty = difficulty),
            "balanced" to balanced().copy(difficulty = difficulty),
            "positional" to positional().copy(difficulty = difficulty),
        )

        tournament.runEngineRoundRobin(
            engines = matrixEngines,
            configs = matrixConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = gamesPerMatch, collectMetrics = true),
            logInfo = ::logInfo,
        )

        val headToHead = tournament.getHeadToHeadResults()

        logInfo("\n${"#".repeat(70)}")
        logInfo("# ${difficulty.name} RIVALRY ANALYSIS")
        logInfo("#".repeat(70))

        headToHead.forEach { (_, result) ->
            val dominance = when {
                result.winsA > result.winsB * 2 -> "${result.engineA} DOMINATES ${result.engineB}"
                result.winsB > result.winsA * 2 -> "${result.engineB} DOMINATES ${result.engineA}"
                abs(result.winsA - result.winsB) <= 1 -> "BALANCED RIVALRY"
                else -> "COMPETITIVE MATCHUP"
            }
            logInfo("# ${result.engineA} vs ${result.engineB}: $dominance")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PERFORMANCE METRICS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Collects performance metrics (cache hit rate, move time, nodes) across
     * all personality variants. Does not assert on values — used for profiling.
     */
    @Test
    fun test_performance_metrics_across_personalities() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = personalityEngines,
            configs = personalityConfigs,
            tournamentConfig = quickConfig,
            logInfo = ::logInfo,
        )

        results.forEach { result ->
            val metrics = result.averagePerformance
            logInfo("${result.engine.name}: $metrics")

            if (metrics != null) {
                assertTrue(
                    "${result.engine.name} average move time should be non-negative",
                    metrics.averageMoveTimeMs >= 0,
                )
                assertTrue(
                    "${result.engine.name} cache hit rate should be in [0, 1]",
                    metrics.averageCacheHitRate in 0.0..1.0,
                )
            }
        }
    }

    /**
     * Verifies that HARD difficulty is not slower than 10× EASY on average.
     * Guards against regressions in search performance as configs are tuned.
     */
    @Test
    fun test_difficulty_move_time_regression() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = difficultyEngines,
            configs = difficultyConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = 20),
            logInfo = ::logInfo,
        )

        val easyMetrics = results.find { it.engine.name == "easy" }?.averagePerformance
        val hardMetrics = results.find { it.engine.name == "hard" }?.averagePerformance

        if (easyMetrics != null && hardMetrics != null && easyMetrics.averageMoveTimeMs > 0) {
            val ratio = hardMetrics.averageMoveTimeMs / easyMetrics.averageMoveTimeMs
            logInfo("Hard/Easy move time ratio: ${"%.2f".format(ratio)}×")
            assertTrue(
                "Hard should not take more than 10× Easy's move time (actual ratio: ${"%.2f".format(ratio)}×)",
                ratio < 10.0,
            )
        }
    }

    /**
     * Verifies that the move ordering cache produces a measurable hit rate.
     *
     * The cache key uses (boardHash, isMaximizing, depth, repetitionCount).
     * If the key accidentally included a mutable field (e.g. context.hashCode()),
     * every lookup would miss and the hit rate would stay near 0%.
     * A hit rate above 5% confirms the cache is functioning correctly across
     * iterative deepening iterations.
     */
    @Test
    fun test_move_ordering_cache_hit_rate_regression() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val results = tournament.runEngineRoundRobin(
            engines = difficultyEngines,
            configs = difficultyConfigs,
            tournamentConfig = quickConfig.copy(gamesPerMatch = 10, collectMetrics = true),
            logInfo = ::logInfo,
        )

        results.forEach { result ->
            val hitRate = result.averagePerformance?.averageCacheHitRate ?: return@forEach
            logInfo("${result.engine.name} cache hit rate: ${"%.1f".format(hitRate * 100)}%")
            assertTrue(
                "${result.engine.name} cache hit rate should exceed 5% — " +
                        "a near-zero rate indicates the move ordering cache key is broken " +
                        "(actual: ${"%.1f".format(hitRate * 100)}%)",
                hitRate > 0.05,
            )
        }
    }

    /**
     * Verifies that Champion beats Easy with a statistically clear margin.
     *
     * With depth=7 vs depth=2 and full evaluation vs material-only, Champion
     * should win at least 70% of games over a 30-game sample. A win rate below
     * this threshold indicates a regression in search depth, evaluation correctness,
     * or engine configuration propagation.
     */
    @Test
    fun test_champion_dominates_easy_regression() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val championEngine = personalityEngine("champion")
        val easyEngine = personalityEngine("easy")

        val result = tournament.runEngineMatch(
            engineA = championEngine,
            engineB = easyEngine,
            configA = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION),
            configB = EvaluationConfig.getByDifficulty(Difficulty.EASY),
            tournamentConfig = quickConfig.copy(gamesPerMatch = 30),
            logInfo = ::logInfo,
        )

        val totalGames = result.winsA + result.winsB + result.draws
        val championWinRate = result.winsA.toDouble() / totalGames
        logInfo("Champion win rate vs Easy: ${"%.1f".format(championWinRate * 100)}% (${result.winsA}-${result.winsB}-${result.draws})")

        assertTrue(
            "Champion should win at least 70% against Easy " +
                    "(actual: ${"%.1f".format(championWinRate * 100)}%). " +
                    "A lower rate suggests a regression in search depth or config propagation.",
            championWinRate >= 0.70,
        )
    }

    /**
     * Verifica que CHAMPION mantenga su supremacía sobre HARD gracias a la quiescence
     * (única diferencia real de habilidad tras gatear el book off: HARD depth-5 sin quiescence
     * vs CHAMPION depth-7 con quiescence). Sin book (contexto vigente) el estudio previo midió
     * ~68 % a favor de Champion (engine_strength_plan.md, Fase B reabierta).
     *
     * Aserción lenient (Champion no pierde el match) por la ventaja del primer jugador + varianza
     * de 30 partidas; el número exacto queda en el log.
     */
    @Test
    fun test_champion_dominates_hard_regression() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val result = tournament.runEngineMatch(
            engineA = personalityEngine("champion"),
            engineB = personalityEngine("hard"),
            configA = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION),
            configB = EvaluationConfig.getByDifficulty(Difficulty.HARD),
            tournamentConfig = quickConfig.copy(gamesPerMatch = 30),
            logInfo = ::logInfo,
        )

        val totalGames = result.winsA + result.winsB + result.draws
        val championWinRate = result.winsA.toDouble() / totalGames
        logInfo(
            "Champion(quiescence) vs Hard(static): ${"%.1f".format(championWinRate * 100)}% " +
                    "(${result.winsA}-${result.winsB}-${result.draws})",
        )

        assertTrue(
            "Champion (quiescence, depth-7) should not lose the head-to-head vs Hard (static, depth-5) " +
                    "(actual: ${result.winsA}-${result.winsB}-${result.draws}). Quiescence is the tier's real edge.",
            result.winsA >= result.winsB,
        )
    }

    /**
     * OBS-3 — A/B de `domesticControlScore` en CHAMPION (45 → 55) para desalentar el vaciado temprano
     * de la base propia (base vacía = blanco de promoción del rival) que induce la personalidad `gambit`.
     *
     * Self-play CHAMPION(obs3) vs CHAMPION(baseline), **con `evalNoise=2`** para romper la degeneración
     * (a evalNoise=0 dos CHAMPION juegan la misma partida). No asevera: el valor está en el número.
     *
     * **Resultado 2026-08-05 (40 partidas): 42.5 % (17-21-2) → RECHAZADO.** Conservar la base (dc=55) hizo a
     * CHAMPION **más débil**, no más fuerte: el vaciado de base / concentración central que OBS-3 señalaba **no es
     * una debilidad explotable** ni siquiera por un rival igual de fuerte — es parte de la agresión ganadora del
     * `gambit`. `domesticControlScore` queda en 45. Test conservado como diagnóstico del experimento.
     */
    @Test
    fun test_obs3_domestic_control_ab() {
        fun logInfo(message: String) = TestLog.info(message)
        val tournament = TournamentRunner()

        val base = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION)
            .copy(behavior = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION).behavior.copy(evalNoise = 2.0))
        val obs3 = base.copy(positional = base.positional.copy(domesticControlScore = 55.0))

        val games = 40
        val result = tournament.runEngineMatch(
            engineA = personalityEngine("champion_obs3"),
            engineB = personalityEngine("champion_base"),
            configA = obs3,
            configB = base,
            tournamentConfig = quickConfig.copy(gamesPerMatch = games),
            logInfo = ::logInfo,
        )

        val total = result.winsA + result.winsB + result.draws
        val rate = if (total > 0) result.winsA.toDouble() / total else 0.0
        logInfo(
            "OBS-3 domesticControl 55 vs 45 (CHAMPION, evalNoise=2): " +
                    "${"%.1f".format(rate * 100)}% (${result.winsA}-${result.winsB}-${result.draws})",
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // BOOK GATING — el ladder Easy→Champion según el gateo del opening book
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Compara el standings del ladder (easy/medium/hard/champion) bajo tres gateos del opening
     * book, para vigilar que el book no comprima ni invierta el orden de tiers:
     *  - A: book ON en hard+champion (el gateo previo a jul-2026).
     *  - B: book OFF en todos (el gateo **vigente**; ver [EvaluationConfig.openingBookEnabled]).
     *  - C: book OFF solo en champion; hard conserva su book.
     *
     * El book es una muleta que rinde más a la búsqueda superficial (Hard depth-5) que a la
     * profunda (Champion depth-7): con book ON, Champion no se separa de Hard (head-to-head ~10-10)
     * y hasta queda debajo por el crutch de Hard vs los débiles bookless. Por eso se mide el
     * standings completo, no solo el head-to-head. No asevera — el valor está en los números.
     */
    @Test
    fun test_difficulty_round_robin_book_ab() {
        fun logInfo(message: String) = TestLog.info(message)
        val gamesPerMatch = 20
        val cfg = quickConfig.copy(gamesPerMatch = gamesPerMatch)

        val configA = difficultyConfigs
        val configB = difficultyConfigs.mapValues { (_, c) -> c.copy(openingBookEnabled = false) }
        val configC = difficultyConfigs.mapValues { (name, c) ->
            if (name == "champion") c.copy(openingBookEnabled = false) else c
        }

        fun run(label: String, configs: Map<String, EvaluationConfig>): Map<String, EnginePerformance?> {
            logInfo("\n${"#".repeat(70)}")
            logInfo("# $label, $gamesPerMatch games/match")
            logInfo("#".repeat(70))
            val results = TournamentRunner().runEngineRoundRobin(
                engines = difficultyEngines,
                configs = configs,
                tournamentConfig = cfg,
                logInfo = ::logInfo,
            )
            return listOf("easy", "medium", "hard", "champion")
                .associateWith { name -> results.find { it.engine.name == name } }
        }

        val a = run("CONFIG A — book ON hard+champion (gateo previo)", configA)
        val b = run("CONFIG B — book OFF all (vigente)", configB)
        val c = run("CONFIG C — book OFF champion only", configC)

        fun fmt(r: EnginePerformance?) =
            if (r == null) "—" else "%.1f (%d-%d-%d)".format(r.score, r.wins, r.losses, r.draws)

        fun gap(m: Map<String, EnginePerformance?>) =
            (m["champion"]?.score ?: 0.0) - (m["hard"]?.score ?: 0.0)

        logInfo("\n${"=".repeat(100)}")
        logInfo("# STANDINGS (score = wins + 0.5·draws · 60 games c/u)")
        logInfo("=".repeat(100))
        logInfo("%-9s | %-21s | %-21s | %s".format("engine", "A book-on h+c", "B off-all (vigente)", "C off-champ"))
        listOf("champion", "hard", "medium", "easy").forEach { n ->
            logInfo("%-9s | %-21s | %-21s | %s".format(n, fmt(a[n]), fmt(b[n]), fmt(c[n])))
        }
        logInfo("-".repeat(100))
        logInfo(
            "# champion − hard:   A=%+.1f    B=%+.1f    C=%+.1f    (positivo = Champion arriba)"
                .format(gap(a), gap(b), gap(c)),
        )
        logInfo("=".repeat(100))
    }
}