package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity del término de potencial de conversión de Rok (Fase A) en [BoardEvaluator]. Guarda contra un
 * no-op silencioso (que invalidaría las mediciones): confirma que un Rok que puede voltear un enemigo
 * produce potencial > 0, respeta la pre-adyacencia, y que con peso 0 el término no aporta.
 */
class ConversionPotentialTest {

    private val evaluator = BoardEvaluator()

    // Rok en 'from' que, al moverse al vértice libre 'to', amenaza al enemigo en 'enemy' (vecino de 'to'
    // pero NO de 'from' → cuenta por pre-adyacencia). Derivado de la topología real vía adjacencyMap.
    private data class ThreatSetup(val from: Vertex, val to: Vertex, val enemy: Vertex)

    private fun findThreatSetup(): ThreatSetup {
        for ((to, neighbors) in GameBoard.adjacencyMap) {
            for (from in neighbors) {
                val fromAdj = GameBoard.adjacencyMap[from]?.toSet() ?: emptySet()
                val enemy = neighbors.firstOrNull { it != from && it != to && it !in fromAdj }
                if (enemy != null) return ThreatSetup(from, to, enemy)
            }
        }
        error("no threat setup found in board topology")
    }

    private fun configWithConversion(weight: Double): EvaluationConfig =
        EvaluationConfig.CHAMPION.copy(
            positional = EvaluationConfig.CHAMPION.positional.copy(conversionPotentialScore = weight),
        )

    @Test
    fun `a rok that can flip an enemy has positive conversion potential`() {
        val (from, to, enemy) = findThreatSetup()
        // Rok blanco en 'from', Cob negro en 'enemy'. 'to' queda libre → el Rok puede moverse ahí.
        val state = GameState(
            cobs = mapOf(from to Cob(WHITE, isUpgraded = true), enemy to Cob(BLACK)),
            currentTurn = WHITE,
        )

        val m = evaluator.evaluateMetrics(state, configWithConversion(50.0))
        assertTrue(
            "white rok at $from should threaten the black cob at $enemy via $to",
            m.conversionPotential.white > 0.0,
        )
        assertEquals("black has no roks → no conversion potential", 0.0, m.conversionPotential.black, 0.0)
    }

    @Test
    fun `conversion term is not computed when its weight is zero`() {
        val (from, to, enemy) = findThreatSetup()
        val state = GameState(
            cobs = mapOf(from to Cob(WHITE, isUpgraded = true), enemy to Cob(BLACK)),
            currentTurn = WHITE,
        )
        // Con peso 0 el término se saltea (guard de costo): la métrica queda en 0 aunque haya amenaza.
        val m = evaluator.evaluateMetrics(state, configWithConversion(0.0))
        assertEquals(0.0, m.conversionPotential.white, 0.0)

        // to no se usa fuera de la construcción del setup; referenciarlo evita el warning de unused.
        assertTrue(to in GameBoard.adjacencyMap.getValue(from))
    }

    @Test
    fun `a non-upgraded cob has no conversion potential`() {
        val (from, _, enemy) = findThreatSetup()
        // Misma posición pero con un Cob (no Rok) → el término solo cuenta Roks.
        val state = GameState(
            cobs = mapOf(from to Cob(WHITE), enemy to Cob(BLACK)),
            currentTurn = WHITE,
        )
        val m = evaluator.evaluateMetrics(state, configWithConversion(50.0))
        assertEquals("a non-upgraded cob contributes no conversion potential", 0.0, m.conversionPotential.white, 0.0)
    }
}
