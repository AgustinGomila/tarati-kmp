package com.agustin.tarati.core.domain.ai.engine


import com.agustin.tarati.core.domain.ai.api.AIDiagnostics
import com.agustin.tarati.core.domain.ai.api.DrawContext
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.cache.TranspositionTable
import com.agustin.tarati.core.domain.ai.engine.TaratiAI.Companion.HALF_MOVE_CLOCK_MAX
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.strategy.IAIStrategy
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.math.exp
import kotlin.random.Random

/**
 * The AI engine for Tarati. Each instance owns its full search state:
 * evaluation config, position history, caches, and search strategy.
 * This makes instances safe to run concurrently in tournament play.
 *
 * For production use, [TaratiAI.instance] provides the app-wide singleton.
 * Tests and tournaments create independent instances via [TaratiAI()].
 */
class TaratiAI : IAIEngine {
    private val globalConfigRef: AtomicRef<EvaluationConfig> = atomic(EvaluationConfig())
    val evalConfig: EvaluationConfig get() = globalConfigRef.value

    override val positionHistory: MutableMap<String, Int> = mutableMapOf()

    // Componentes — cada instancia posee su propio estado de búsqueda
    private val cache = HybridEvaluationCache(positionHistory = positionHistory)
    private val boardEvaluator = BoardEvaluator()
    private val moveEvaluator = MoveEvaluator(cache)
    private val transpositionTable = TranspositionTable()
    private val aiStrategy: IAIStrategy =
        MinimaxStrategy(
            boardEvaluator = boardEvaluator,
            moveEvaluator = moveEvaluator,
            transpositionTable = transpositionTable,
            cache = cache,
            positionHistory = positionHistory,
            evalConfig = ::evalConfig,
        )

    // ==================== API Pública ====================

    override val name: String get() = "Engine Standard"

    override suspend fun getNextMove(gameState: GameState): MoveEval {
        val config = evalConfig
        if (config.randomMoveChance > 0.0 && Random.nextDouble() < config.randomMoveChance) {
            val randomMove = gameState.allMovesForTurn().randomOrNull()
            return MoveEval(move = randomMove, score = 0.0)
        }
        return getNextMove(gameState, config.difficulty)
    }

    /**
     * Decides whether to accept a draw offer using a probabilistic model that
     * combines four independent signals into a single acceptance probability.
     *
     * ## Signals
     *
     * ### 1. Board score (position)
     * Raw evaluation from [BoardEvaluator], always from White's perspective.
     * Flipped for Black so that positive = engine is winning.
     * A logistic curve maps [-∞, +∞] to (0, 1]:
     *   - strongly losing  → high acceptance chance (want the draw)
     *   - roughly equal    → ~0.5 (neutral)
     *   - clearly winning  → low acceptance chance (want to play on)
     *
     * ### 2. Half-move clock (game length without captures)
     * [GameState.halfMoveClock] counts consecutive half-moves without a Cob move
     * or promotion. High values signal a drawn-out, static game where pressing
     * for a win carries increasing risk. Linearly increases acceptance up to a
     * cap at [HALF_MOVE_CLOCK_MAX].
     *
     * ### 3. Rating differential (ELO gap)
     * Positive gap = bot is rated higher than the opponent.
     *   - Bot >> opponent: high expected win rate → decline draw.
     *   - Bot << opponent: underdog → accept draw more readily.
     * Scaled with a logistic so extreme gaps saturate gracefully.
     *
     * ### 4. Difficulty personality
     * Each difficulty has a base draw-acceptance bias reflecting its play style:
     *   - EASY: always accepts (can't convert winning positions reliably).
     *   - MEDIUM: slight draw-acceptance tilt.
     *   - HARD: slightly draw-averse; trusts the position evaluation.
     *   - CHAMPION: draw-averse; confident in its ability to press advantages.
     *
     * ## Combining signals
     * The four signals are averaged to produce p ∈ (0, 1). A single
     * `Random.nextDouble() < p` roll decides the outcome, ensuring the bot is
     * never perfectly predictable even in objectively equal positions.
     */
    override fun shouldAcceptDraw(context: DrawContext): Boolean {
        val config = evalConfig

        // EASY always accepts regardless of position — it won't reliably convert wins.
        if (config.difficulty == Difficulty.EASY) return true

        val (gameState, engineColor, botRating, opponentRating) = context

        // ── Signal 1: board score ─────────────────────────────────────────────
        val rawScore = boardEvaluator.evaluate(gameState, config)
        // Flip so that positive always means the engine is winning.
        val engineScore = if (engineColor == CobColor.WHITE) rawScore else -rawScore
        // Logistic: score=0 → 0.5 | +150 → ~0.27 (winning, decline) | -150 → ~0.73 (losing, accept)
        val positionSignal = 1.0 / (1.0 + exp(engineScore / SCORE_LOGISTIC_SCALE))

        // ── Signal 2: half-move clock ─────────────────────────────────────────
        // High values = long sequence without captures = drawish dynamic → accept.
        val halfMoves = gameState.halfMoveClock.coerceIn(0, HALF_MOVE_CLOCK_MAX)
        val clockSignal = halfMoves.toDouble() / HALF_MOVE_CLOCK_MAX

        // ── Signal 3: rating gap ──────────────────────────────────────────────
        // Positive gap = bot is stronger → bot expects to win → decline draw.
        val ratingGap = (botRating - opponentRating).toDouble()
        // Logistic: gap=0 → 0.5 | gap=+200 → ~0.27 (bot favored) | gap=-200 → ~0.73 (underdog)
        val ratingSignal = 1.0 / (1.0 + exp(ratingGap / RATING_LOGISTIC_SCALE))

        // ── Signal 4: difficulty personality bias ─────────────────────────────
        val difficultyBias = when (config.difficulty) {
            Difficulty.EASY -> 1.00  // handled above, unreachable
            Difficulty.MEDIUM -> 0.55  // slight draw-acceptance tilt
            Difficulty.HARD -> 0.45  // slight draw-aversion
            Difficulty.CHAMPION -> 0.30  // strongly draw-averse; presses every advantage
        }

        // ── Combine and sample ────────────────────────────────────────────────
        val acceptanceProbability = (positionSignal + clockSignal + ratingSignal + difficultyBias) / 4.0
        return Random.nextDouble() < acceptanceProbability
    }

    override fun clearHistory() {
        transpositionTable.clear()
        positionHistory.clear()
        cache.clear()
    }

    /**
     * Updates the active EvaluationConfig. The transposition table is cleared
     * when difficulty changes because cached entries are depth-tagged: a shallow
     * entry from a lower difficulty would be incorrectly reused as a valid deep
     * result for a higher difficulty, corrupting the search.
     */
    override fun setConfig(config: EvaluationConfig) {
        val prevDifficulty = globalConfigRef.value.difficulty
        globalConfigRef.value = config
        if (config.difficulty != prevDifficulty) {
            transpositionTable.clear()
        }
    }

    override fun getDiagnostics(): AIDiagnostics {
        val stats = (aiStrategy as? MinimaxStrategy)?.getStats()
        return AIDiagnostics(
            cacheStats = cache.getStats(),
            positionHistorySize = positionHistory.size,
            transpositionTableSize = transpositionTable.size(),
            nodesEvaluated = stats?.nodesEvaluated ?: 0L,
            cutoffs = stats?.cutoffs ?: 0,
            cacheHits = stats?.cacheHits ?: 0,
        )
    }

    override fun putState(
        gameState: GameState,
        moveBy: CobColor,
    ): CobColor? {
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

    // ================ Funciones auxiliares ================

    private fun putState(gameState: GameState): Int {
        val hash = gameState.hashBoard()
        val count = (positionHistory[hash] ?: 0) + 1
        positionHistory[hash] = count
        return count
    }

    fun getRepetitionCount(gameState: GameState): Int = positionHistory[gameState.hashBoard()] ?: 0

    suspend fun getNextMove(
        gameState: GameState,
        difficulty: Difficulty,
    ): MoveEval = aiStrategy.getNextMove(gameState, difficulty)

    companion object {
        /**
         * App-wide singleton — used by production code (Koin, GameEvents, GameEffects,
         * AIViewModel, GameState). Tests and tournaments that need isolation should
         * create independent instances via [TaratiAI()].
         */
        val instance: TaratiAI = TaratiAI()

        // ── Draw acceptance constants ─────────────────────────────────────────

        /**
         * Logistic scale for the board-score signal in [shouldAcceptDraw].
         * Controls how quickly the curve transitions from "accept" to "decline"
         * around a score of 0. Lower values = sharper transition.
         * At k=100: a +100 score (roughly half a cob) already shifts acceptance
         * probability meaningfully below 0.5.
         */
        private const val SCORE_LOGISTIC_SCALE = 100.0

        /**
         * Logistic scale for the rating-gap signal in [shouldAcceptDraw].
         * At k=200: a +200 ELO gap shifts the signal to ~0.27.
         * Matches the typical meaning of a 200-point rating difference.
         */
        private const val RATING_LOGISTIC_SCALE = 200.0

        /**
         * Half-move clock value considered "fully drawn-out" for the clock signal.
         * Equal to 60 half-moves = 30 full moves without any Cob move or capture.
         * Beyond this the clock signal is capped at 1.0 (maximum acceptance pressure).
         * The 50-move rule triggers at 100 half-moves, so this is well within range.
         */
        private const val HALF_MOVE_CLOCK_MAX = 60
    }
}