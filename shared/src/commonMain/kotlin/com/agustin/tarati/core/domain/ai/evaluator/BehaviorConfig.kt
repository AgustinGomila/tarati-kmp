package com.agustin.tarati.core.domain.ai.evaluator

/**
 * Parameters that govern engine behavior: win detection thresholds,
 * draw avoidance, stalling prevention, and difficulty-level handicaps.
 *
 * Used by [MinimaxStrategy] (winning thresholds, repetition penalty)
 * and [MoveEvaluator] (stalling penalty, repetition penalty, winning position detection).
 * [TaratiAI] reads [randomMoveChance] directly before delegating to the strategy.
 */
data class BehaviorConfig(
    val winningScore: Double = 50_000.0,
    val winningThreshold: Double = 0.8,
    val winningPositionThreshold: Double = 0.8,
    val repetitionPenaltyMultiplier: Double = 0.5,
    val immediateWinBonusMultiplier: Double = 1.2,
    val stallingPenalty: Double = 5.0,
    val stallingThreshold: Int = 80,
    val randomMoveChance: Double = 0.0,
    /**
     * Amplitude of random noise added to the base evaluation score after caching.
     * Noise is applied per retrieval so that different search paths through the same
     * position sample independent perturbations, breaking symmetry without polluting
     * the deterministic cached value. 0.0 = no noise (default for MEDIUM+).
     */
    val evalNoise: Double = 0.0,
) {
    fun scaleWinning(threshold: Double = 1.0, positionThreshold: Double = 1.0): BehaviorConfig = copy(
        winningThreshold = winningThreshold * threshold,
        winningPositionThreshold = winningPositionThreshold * positionThreshold,
    )

    fun scaleRepetition(factor: Double): BehaviorConfig = copy(
        repetitionPenaltyMultiplier = repetitionPenaltyMultiplier * factor,
    )

    fun scaleStalling(penalty: Double = 1.0, threshold: Int = stallingThreshold): BehaviorConfig = copy(
        stallingPenalty = stallingPenalty * penalty,
        stallingThreshold = threshold,
    )
}