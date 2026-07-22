package com.agustin.tarati.core.domain.ai.evaluator

/**
 * Weights related to positional evaluation: board control, mobility,
 * territory, promotion opportunities, and piece isolation.
 *
 * Used by [BoardEvaluator] and [MoveEvaluator] (positional scoring),
 * and by [PatternPrecomputer] (strategic vertex precomputation).
 */
data class PositionalWeights(
    val controlCenterScore: Double = 25.0,
    val controlCenterMultiplier: Double = 1.0,
    val mobilityScore: Double = 10.0,
    val mobilityImprovementMultiplier: Double = 1.0,
    val domesticControlScore: Double = 30.0,
    val opponentDomesticPressureScore: Double = 40.0,
    val upgradeScore: Double = 150.0,
    val upgradeBonusMultiplier: Double = 1.2,
    val forcedPromotionScore: Double = 150.0,
    val isolationPenalty: Double = 80.0,
    // Potencial de conversión de Rok (Fase A): peso de las piezas enemigas que los Roks propios amenazan
    // voltear en su próxima jugada (suma sobre Roks de su mejor amenaza). Modela "1 Rok convierte todo",
    // que el conteo de material contradice. Default 0.0 = término OFF (producción intacta; también evita
    // su costo en BoardEvaluator). Ver engine_strength_plan.md, Fase A.
    val conversionPotentialScore: Double = 0.0,
) {
    fun scaleCenter(score: Double = 1.0, multiplier: Double = 1.0): PositionalWeights = copy(
        controlCenterScore = controlCenterScore * score,
        controlCenterMultiplier = controlCenterMultiplier * multiplier,
    )

    fun scaleMobility(score: Double = 1.0, multiplier: Double = 1.0): PositionalWeights = copy(
        mobilityScore = mobilityScore * score,
        mobilityImprovementMultiplier = mobilityImprovementMultiplier * multiplier,
    )

    fun scaleDomestic(home: Double = 1.0, pressure: Double = 1.0): PositionalWeights = copy(
        domesticControlScore = domesticControlScore * home,
        opponentDomesticPressureScore = opponentDomesticPressureScore * pressure,
    )

    fun scaleUpgrade(score: Double = 1.0, multiplier: Double = 1.0): PositionalWeights = copy(
        upgradeScore = upgradeScore * score,
        upgradeBonusMultiplier = upgradeBonusMultiplier * multiplier,
    )

    fun scaleForced(factor: Double): PositionalWeights = copy(
        forcedPromotionScore = forcedPromotionScore * factor,
    )

    fun scaleIsolation(factor: Double): PositionalWeights = copy(
        isolationPenalty = isolationPenalty * factor,
    )
}