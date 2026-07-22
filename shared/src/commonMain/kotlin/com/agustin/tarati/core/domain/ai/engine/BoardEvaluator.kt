package com.agustin.tarati.core.domain.ai.engine

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import com.agustin.tarati.core.domain.game.board.GameBoard.centerVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.homeBases
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState

class BoardEvaluator {

    fun evaluate(gameState: GameState, config: EvaluationConfig): Double {
        val m = evaluateMetrics(gameState, config)
        return with(config) {
            m.material.difference +
                    m.centerControl.difference * controlCenterScore +
                    m.mobility.difference * mobilityScore +
                    m.homeControl.difference * domesticControlScore +
                    m.opponentPressure.difference * opponentDomesticPressureScore +
                    m.upgradeOpportunities.difference * upgradeScore +
                    m.forcedPromotionOpportunities.difference * forcedPromotionScore +
                    m.conversionPotential.difference * conversionPotentialScore
        }
    }

    fun evaluateMetrics(gameState: GameState, config: EvaluationConfig): BoardMetrics =
        calculateBoardMetrics(gameState, config)

    private data class MutableMetrics(
        var material: Double = 0.0,
        var centerControl: Int = 0,
        var mobility: Int = 0,
        var homeControl: Int = 0,
        var opponentPressure: Int = 0,
        var upgradeOpportunities: Int = 0,
        var forcedPromotionOpportunities: Int = 0,
    )

    private fun calculateBoardMetrics(gameState: GameState, config: EvaluationConfig): BoardMetrics {
        val white = MutableMetrics()
        val black = MutableMetrics()

        for ((vertex, cob) in gameState.cobs) {
            val m = if (cob.color == WHITE) white else black
            val materialValue = if (cob.isUpgraded) config.rocScore else config.cobScore

            m.material += materialValue
            if (vertex in centerVertices) m.centerControl++
            m.mobility += cob.calculateMobility(gameState, vertex)
            if (vertex in homeBases[cob.color]!!) m.homeControl++
            if (vertex in homeBases[cob.color.opponent]!!) m.opponentPressure++
            if (cob.canUpgrade(vertex)) m.upgradeOpportunities++
            if (!cob.isUpgraded && gameState.isDeadCob(vertex, cob)) m.forcedPromotionOpportunities++
        }

        // Potencial de conversión de Rok — solo si el peso está activo (evita el costo del doble bucle
        // cuando el término está off, que es el default en producción).
        val conversion =
            if (config.conversionPotentialScore != 0.0) {
                ColorStat(conversionPotentialFor(gameState, WHITE), conversionPotentialFor(gameState, BLACK))
            } else {
                ColorStat(0.0, 0.0)
            }

        return BoardMetrics(
            material = ColorStat(white.material, black.material),
            centerControl = ColorStat(white.centerControl, black.centerControl),
            mobility = ColorStat(white.mobility, black.mobility),
            homeControl = ColorStat(white.homeControl, black.homeControl),
            opponentPressure = ColorStat(white.opponentPressure, black.opponentPressure),
            upgradeOpportunities = ColorStat(white.upgradeOpportunities, black.upgradeOpportunities),
            forcedPromotionOpportunities = ColorStat(
                white.forcedPromotionOpportunities,
                black.forcedPromotionOpportunities,
            ),
            conversionPotential = conversion,
        )
    }

    /**
     * Potencial de conversión de los Roks de [color]: suma, sobre cada Rok propio, de su **mejor**
     * amenaza en una jugada — las piezas enemigas que voltearía al moverse a su mejor destino libre,
     * respetando la pre-adyacencia (solo enemigos nuevos del destino, no los que ya lindaban el origen),
     * ponderadas por valor (un Rok amenazado pesa más que un Cob). Modela "1 Rok convierte todo". Barato:
     * por Rok, ≤6 destinos × ≤6 adyacentes. Solo Roks (los Cobs solo avanzan, sin este potencial).
     */
    private fun conversionPotentialFor(gameState: GameState, color: CobColor): Double {
        var total = 0.0
        for ((vertex, cob) in gameState.cobs) {
            if (cob.color != color || !cob.isUpgraded) continue
            val originAdjacents = adjacencyMap[vertex]?.toSet() ?: emptySet()
            var best = 0.0
            for (to in gameState.getPossiblesVertexForCob(cob, vertex)) {
                var threat = 0.0
                for (adj in adjacencyMap[to] ?: emptyList()) {
                    if (adj in originAdjacents) continue
                    val enemy = gameState.cobs[adj]
                    if (enemy != null && enemy.color != color) {
                        threat += if (enemy.isUpgraded) THREATENED_ROK_WEIGHT else THREATENED_COB_WEIGHT
                    }
                }
                if (threat > best) best = threat
            }
            total += best
        }
        return total
    }

    /**
     * Holds a white and black measurement for a single board metric.
     * [difference] is always white minus black, matching the engine's
     * maximizing-from-white convention.
     */
    data class ColorStat<T : Number>(val white: T, val black: T) {
        val difference: Double get() = white.toDouble() - black.toDouble()
    }

    data class BoardMetrics(
        val material: ColorStat<Double>,
        val centerControl: ColorStat<Int>,
        val mobility: ColorStat<Int>,
        val homeControl: ColorStat<Int>,
        val opponentPressure: ColorStat<Int>,
        val upgradeOpportunities: ColorStat<Int>,
        val forcedPromotionOpportunities: ColorStat<Int>,
        val conversionPotential: ColorStat<Double>,
    )

    companion object {
        // Peso relativo de una pieza enemiga amenazada por un Rok, en el potencial de conversión.
        // Un Rok amenazado vale más que un Cob (≈ su ratio de material). La magnitud global la controla
        // EvaluationConfig.conversionPotentialScore.
        private const val THREATENED_COB_WEIGHT = 1.0
        private const val THREATENED_ROK_WEIGHT = 2.0
    }
}