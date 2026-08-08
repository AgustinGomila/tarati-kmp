package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig

/**
 * Términos en que se descompone la evaluación estática del tablero. El orden
 * espeja la fórmula de [BoardEvaluator.evaluate].
 */
enum class EvalMetric {
    MATERIAL,
    CENTER,
    MOBILITY,
    HOME,
    PRESSURE,
    UPGRADE,
    FORCED_PROMOTION,
    CONVERSION,
}

/**
 * Aporte en puntos de un término al score total, en óptica de Blancas
 * (positivo = favorece a Blancas). Es la diferencia White−Black de la métrica
 * multiplicada por su peso en la [EvaluationConfig].
 */
data class MetricContribution(
    val metric: EvalMetric,
    val points: Double,
)

/**
 * Descompone el score estático en los aportes por término, replicando la suma
 * ponderada de [BoardEvaluator.evaluate]. La suma de [MetricContribution.points]
 * es exactamente el score de la posición — por eso [PositionAnalyzer] deriva el
 * score de acá, garantizando que el desglose y el número coinciden.
 */
fun evaluationContributions(
    metrics: BoardEvaluator.BoardMetrics,
    config: EvaluationConfig,
): List<MetricContribution> = with(config) {
    listOf(
        MetricContribution(EvalMetric.MATERIAL, metrics.material.difference),
        MetricContribution(EvalMetric.CENTER, metrics.centerControl.difference * controlCenterScore),
        MetricContribution(EvalMetric.MOBILITY, metrics.mobility.difference * mobilityScore),
        MetricContribution(EvalMetric.HOME, metrics.homeControl.difference * domesticControlScore),
        MetricContribution(EvalMetric.PRESSURE, metrics.opponentPressure.difference * opponentDomesticPressureScore),
        MetricContribution(EvalMetric.UPGRADE, metrics.upgradeOpportunities.difference * upgradeScore),
        MetricContribution(
            EvalMetric.FORCED_PROMOTION,
            metrics.forcedPromotionOpportunities.difference * forcedPromotionScore,
        ),
        MetricContribution(EvalMetric.CONVERSION, metrics.conversionPotential.difference * conversionPotentialScore),
    )
}
