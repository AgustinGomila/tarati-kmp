package com.agustin.tarati.game.ai.tournament.engine.base

import com.agustin.tarati.core.domain.ai.api.AIDiagnostics
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState

/**
 * A fresh independent engine instance for use in tests and diagnostics.
 * Each call to [newEngine] produces an isolated instance — no shared state
 * with the production singleton registered in Koin.
 */
val standardEngine: IAIEngine get() = newEngine()

/**
 * Creates a fresh, independent [TaratiAI] instance.
 * Tournament tests use this so each participant owns its own search state
 * and can run concurrently without sharing config, caches, or history.
 */
fun newEngine(): TaratiAI = TaratiAI()

/**
 * A lightweight wrapper that gives a [TaratiAI] instance a distinct name for
 * tournament reporting. Every method delegates to the underlying [engine];
 * only [name] is overridden so the leaderboard and head-to-head tables show
 * meaningful labels.
 */
class PersonalityEngine(
    override val name: String,
    private val engine: IAIEngine,
) : IAIEngine {
    // Tracks the last config applied so cloneForMatch() can carry it to the new instance.
    var currentConfig: EvaluationConfig = EvaluationConfig()
        private set

    override val positionHistory: Map<String, Int> get() = engine.positionHistory
    override suspend fun getNextMove(gameState: GameState): MoveEval = engine.getNextMove(gameState)
    override fun clearHistory(): Unit = engine.clearHistory()
    override fun putState(gameState: GameState, moveBy: CobColor): CobColor? = engine.putState(gameState, moveBy)
    override fun removeState(gameState: GameState): Unit = engine.removeState(gameState)
    override fun setConfig(config: EvaluationConfig) {
        currentConfig = config
        engine.setConfig(config)
    }

    override fun getDiagnostics(): AIDiagnostics? = engine.getDiagnostics()
}

/**
 * Creates a [PersonalityEngine] with the given [name] wrapping a fresh engine instance.
 * Each call produces an independent engine — safe for concurrent tournament use.
 */
fun personalityEngine(name: String): PersonalityEngine = PersonalityEngine(name, newEngine())

/**
 * Creates a [PersonalityEngine] with the given [name] wrapping an existing [engine].
 * Use when you need to control which engine instance is wrapped (e.g. in specific tests).
 */
fun customEngine(name: String, engine: IAIEngine): PersonalityEngine = PersonalityEngine(name, engine)

// Pre-built personality variants — each owns an independent TaratiAI instance.
// The name is used only for tournament display; the EvaluationConfig is
// what actually differentiates their play style.
val baselineEngine: IAIEngine = personalityEngine("baseline")
val aggressiveEngine: IAIEngine = personalityEngine("aggressive")
val defensiveEngine: IAIEngine = personalityEngine("defensive")
val balancedEngine: IAIEngine = personalityEngine("balanced")
val positionalEngine: IAIEngine = personalityEngine("positional")
val materialEngine: IAIEngine = personalityEngine("material")
val gambitEngine: IAIEngine = personalityEngine("gambit")
val swarmingEngine: IAIEngine = personalityEngine("swarming")
val strategistEngine: IAIEngine = personalityEngine("strategist")

/** A random-move engine useful as a baseline floor. */
val randomMoveEngine: IAIEngine =
    object : IAIEngine {
        override val name: String = "random"
        override val positionHistory: MutableMap<String, Int> = mutableMapOf()

        override suspend fun getNextMove(gameState: GameState): MoveEval {
            val move = gameState.allMovesForTurn().randomOrNull()
            return MoveEval(move = move, score = 0.0)
        }

        override fun clearHistory() = positionHistory.clear()
        override fun putState(gameState: GameState, moveBy: CobColor): CobColor? = null
        override fun removeState(gameState: GameState) = Unit
        override fun setConfig(config: EvaluationConfig) = Unit
        override fun getDiagnostics() = null
    }

/**
 * Creates an independent clone of this engine for use in a single tournament match.
 * The clone owns a fresh [TaratiAI] instance with the same config pre-applied,
 * so concurrent matches never share mutable search state.
 * [randomMoveEngine] clones itself as a stateless object — it has no config to carry.
 */
fun IAIEngine.cloneForMatch(): IAIEngine =
    when (this) {
        randomMoveEngine -> randomMoveEngine
        else -> PersonalityEngine(name, newEngine().also { it.setConfig(evalConfigOrDefault()) })
    }

private fun IAIEngine.evalConfigOrDefault(): EvaluationConfig =
    (this as? PersonalityEngine)?.currentConfig ?: EvaluationConfig()