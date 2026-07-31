package com.agustin.tarati.core.domain.ai.evaluator

/**
 * Weights related to piece material value and capture bonuses.
 *
 * Used by [BoardEvaluator] (cobScore, rocScore) and [MoveEvaluator]
 * (flip bonuses and their multipliers).
 */
data class MaterialWeights(
    val cobScore: Double = 100.0,
    val rocScore: Double = 250.0,
    val flipCobBonus: Double = 40.0,
    val flipRocBonus: Double = 80.0,
    val cobFlipMultiplier: Double = 1.0,
    val rocFlipMultiplier: Double = 1.5,
) {
    fun scalePieces(cob: Double = 1.0, rok: Double = 1.0): MaterialWeights = copy(
        cobScore = cobScore * cob,
        rocScore = rocScore * rok,
    )

    fun scaleFlips(cob: Double = 1.0, rok: Double = 1.0): MaterialWeights = copy(
        flipCobBonus = flipCobBonus * cob,
        flipRocBonus = flipRocBonus * rok,
    )

    fun scaleFlipMultipliers(cob: Double = 1.0, rok: Double = 1.0): MaterialWeights = copy(
        cobFlipMultiplier = cobFlipMultiplier * cob,
        rocFlipMultiplier = rocFlipMultiplier * rok,
    )
}