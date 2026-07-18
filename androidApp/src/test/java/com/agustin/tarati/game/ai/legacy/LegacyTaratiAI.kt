package com.agustin.tarati.game.ai.legacy

import com.agustin.tarati.core.domain.ai.api.AIDiagnostics
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.cache.TranspositionTable
import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.MoveEvaluator
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.strategy.IAIStrategy
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Snapshot of [TaratiAI] wired to
 * [LegacyMinimaxStrategy] instead of the current
 * [MinimaxStrategy].
 *
 * Used in regression tests to pit the pre-change engine against the post-change
 * engine in a head-to-head tournament. Both implement [IAIEngine] so they drop
 * into [TournamentRunner] without
 * any other changes.
 *
 * Create instances via [legacyEngine] — don't use this class directly in tests.
 *
 * Do not edit: this file is a frozen snapshot. Changes belong in
 * [TaratiAI].
 */
internal class LegacyTaratiAI : IAIEngine {

    private val globalConfigRef: AtomicReference<EvaluationConfig> =
        AtomicReference(EvaluationConfig())

    val evalConfig: EvaluationConfig get() = globalConfigRef.get()

    override val positionHistory: MutableMap<String, Int> = mutableMapOf()

    private val cache = HybridEvaluationCache(positionHistory = positionHistory)
    private val boardEvaluator = BoardEvaluator()
    private val moveEvaluator = MoveEvaluator(cache)
    private val transpositionTable = TranspositionTable()

    // ── Única diferencia respecto a TaratiAI: usa LegacyMinimaxStrategy ──────
    private val aiStrategy: IAIStrategy =
        LegacyMinimaxStrategy(
            boardEvaluator = boardEvaluator,
            moveEvaluator = moveEvaluator,
            transpositionTable = transpositionTable,
            cache = cache,
            positionHistory = positionHistory,
            evalConfig = ::evalConfig,
        )

    override val name: String get() = "Legacy"

    override suspend fun getNextMove(gameState: GameState): MoveEval {
        val config = evalConfig
        if (config.randomMoveChance > 0.0 && Random.nextDouble() < config.randomMoveChance) {
            val randomMove = gameState.allMovesForTurn().randomOrNull()
            return MoveEval(move = randomMove, score = 0.0)
        }
        return getNextMove(gameState, config.difficulty)
    }

    override fun clearHistory() {
        transpositionTable.clear()
        positionHistory.clear()
        cache.clear()
    }

    override fun setConfig(config: EvaluationConfig) {
        val prevDifficulty = globalConfigRef.get().difficulty
        globalConfigRef.set(config)
        if (config.difficulty != prevDifficulty) {
            transpositionTable.clear()
        }
    }

    override fun getDiagnostics(): AIDiagnostics {
        val (nodes, cuts, hits) = (aiStrategy as? LegacyMinimaxStrategy)?.getStats()
            ?: Triple(0L, 0, 0)
        return AIDiagnostics(
            cacheStats = cache.getStats(),
            positionHistorySize = positionHistory.size,
            transpositionTableSize = transpositionTable.size(),
            nodesEvaluated = nodes,
            cutoffs = cuts,
            cacheHits = hits,
        )
    }

    override fun putState(gameState: GameState, moveBy: CobColor): CobColor? {
        val count = putState(gameState)
        return if (count >= 3) moveBy else null
    }

    override fun removeState(gameState: GameState) {
        val hash = gameState.hashBoard()
        val count = positionHistory[hash] ?: 0
        when (count) {
            0 -> return
            1 -> positionHistory.remove(hash)
            else -> positionHistory[hash] = count - 1
        }
    }

    private fun putState(gameState: GameState): Int {
        val hash = gameState.hashBoard()
        val count = (positionHistory[hash] ?: 0) + 1
        positionHistory[hash] = count
        return count
    }

    suspend fun getNextMove(gameState: GameState, difficulty: Difficulty): MoveEval =
        aiStrategy.getNextMove(gameState, difficulty)
}

/**
 * Creates a fresh [LegacyTaratiAI] wrapped in a [PersonalityEngine]-compatible
 * shell so it can be passed directly to [TournamentRunner.runEngineMatch].
 *
 * Usage:
 * ```kotlin
 * val result = tournament.runEngineMatch(
 *     engineA = newEngine().also { it.name = "New" },   // current TaratiAI
 *     engineB = legacyEngine(),                          // pre-change snapshot
 *     configA = EvaluationConfig.CHAMPION,
 *     configB = EvaluationConfig.CHAMPION,
 *     tournamentConfig = config,
 *     logInfo = ::println,
 * )
 * ```
 */
fun legacyEngine(name: String = "Legacy"): IAIEngine =
    object : IAIEngine by LegacyTaratiAI() {
        override val name: String = name
    }