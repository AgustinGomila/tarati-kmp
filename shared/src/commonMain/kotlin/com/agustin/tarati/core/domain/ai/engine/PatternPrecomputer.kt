package com.agustin.tarati.core.domain.ai.engine

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.opponent

/**
 * Precomputer for per-vertex strategic values.
 *
 * Values depend only on board topology and a small subset of [EvaluationConfig]
 * weights (center, domestic, mobility). Results are cached per config (structural
 * equality of the data class) so that repeated calls with the same config pay
 * zero recomputation cost — the common case during a single search. A different
 * config (e.g. after [TaratiAI.setConfig]) triggers a fresh computation
 * automatically.
 *
 * Each [MoveEvaluator] owns its instance: the cache is single-threaded per
 * engine, with no mutable state shared between engines searching concurrently
 * (server bots).
 */
class PatternPrecomputer {
    // Keyed by EvaluationConfig (data class → structural equality): identical
    // configs share the cached entry regardless of tournament round or search depth.
    private val cache = mutableMapOf<EvaluationConfig, Map<Vertex, Double>>()

    private fun valuesFor(config: EvaluationConfig): Map<Vertex, Double> =
        cache.getOrPut(config) {
            GameBoard.vertices.associateWith { computeVertexValue(it, config) }
        }

    private fun computeVertexValue(
        vertex: Vertex,
        config: EvaluationConfig,
    ): Double {
        var value = 0.0

        if (vertex in GameBoard.centerVertices) {
            value += config.controlCenterScore * config.controlCenterMultiplier
            if (vertex.zone == GameBoard.ABSOLUTE) {
                value += config.controlCenterScore * config.controlCenterMultiplier * 0.5
            }
        }

        if (GameBoard.homeBases.values.any { base -> vertex in base }) {
            value += config.domesticControlScore
        }

        val connections = GameBoard.adjacencyMap[vertex]?.size ?: 0
        value += connections * config.mobilityScore * config.mobilityImprovementMultiplier

        return value
    }

    fun getStrategicValues(
        vertex: Vertex,
        playerColor: CobColor,
        config: EvaluationConfig,
    ): Double {
        val baseValue = valuesFor(config)[vertex] ?: 0.0
        return when (vertex) {
            in GameBoard.upgradeVertices[playerColor]!! ->
                baseValue + config.domesticControlScore

            in GameBoard.upgradeVertices[playerColor.opponent]!! ->
                baseValue - config.domesticControlScore

            else -> baseValue
        }
    }
}