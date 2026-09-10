package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.cache.TranspositionTable
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.game.play.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for the config-awareness of [HybridEvaluationCache] and [TranspositionTable].
 *
 * Both caches are keyed only by board position, but the evaluations, move orderings and full search
 * results they store **depend on the active [com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig]**
 * (a position is worth a different score under MEDIUM than under HARD). When a single engine serves
 * more than one config — the web engine worker is a persistent, long-lived instance shared across
 * games, and an AI-vs-AI match uses two configs on the same engine — entries computed under one
 * config leaked into searches run under another, corrupting the result. The observed symptom was a
 * game that repeated identically until a difficulty was changed, then a different-but-again-fixed
 * game (the stale cross-config entries shifted). The fix namespaces every key by a config token; the
 * engine updates it on each `setConfig`.
 */
class EvaluationCacheConfigAwarenessTest {

    private val position = GameState.initialGameState()

    // ── HybridEvaluationCache ────────────────────────────────────────────────

    @Test
    fun `full evaluation does not leak across configs`() {
        val cache = HybridEvaluationCache()

        cache.setConfigToken("MEDIUM")
        cache.putFullEvaluation(position, 100.0)
        assertEquals(100.0, cache.getFullEvaluation(position), "Hit under the config it was stored with")

        cache.setConfigToken("HARD")
        assertNull(cache.getFullEvaluation(position), "No stale MEDIUM evaluation under HARD")

        cache.putFullEvaluation(position, 250.0)
        assertEquals(250.0, cache.getFullEvaluation(position), "HARD stores its own value")

        cache.setConfigToken("MEDIUM")
        assertEquals(100.0, cache.getFullEvaluation(position), "MEDIUM entry intact, not overwritten by HARD")
    }

    @Test
    fun `quick evaluation does not leak across configs`() {
        val cache = HybridEvaluationCache()

        cache.setConfigToken("MEDIUM")
        cache.putQuickEvaluation(position, 42.0)
        assertEquals(42.0, cache.getQuickEvaluation(position))

        cache.setConfigToken("HARD")
        assertNull(cache.getQuickEvaluation(position), "No stale MEDIUM quick-eval under HARD")
    }

    @Test
    fun `move ordering does not leak across configs`() {
        val cache = HybridEvaluationCache()
        val ordering = listOf("A1-B1", "B1-C1")

        cache.setConfigToken("MEDIUM")
        cache.putMoveOrdering(position, isMaximizing = true, moves = ordering, depth = 3)
        assertEquals(ordering, cache.getMoveOrdering(position, isMaximizing = true, depth = 3))

        cache.setConfigToken("HARD")
        assertNull(
            cache.getMoveOrdering(position, isMaximizing = true, depth = 3),
            "No stale MEDIUM move ordering under HARD",
        )
    }

    // ── TranspositionTable ───────────────────────────────────────────────────

    @Test
    fun `transposition entry does not leak across configs`() {
        val tt = TranspositionTable()
        val hash = position.hashBoard()

        tt.setConfigToken("MEDIUM")
        tt.put(hash, depth = 3, result = MoveEval(100.0, null))
        assertEquals(100.0, tt.get(hash, depth = 3)?.score, "Hit under the config it was stored with")

        tt.setConfigToken("HARD")
        assertNull(tt.get(hash, depth = 3), "No stale MEDIUM search result under HARD")

        tt.setConfigToken("MEDIUM")
        assertEquals(100.0, tt.get(hash, depth = 3)?.score, "MEDIUM entry survives the round trip")
    }
}
