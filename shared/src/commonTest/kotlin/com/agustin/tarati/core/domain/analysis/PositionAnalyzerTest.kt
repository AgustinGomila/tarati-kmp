package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Verifica que [PositionAnalyzer] evalúa con signo consistente con
 * [com.agustin.tarati.core.domain.ai.engine.BoardEvaluator], es determinista y no
 * ensucia el estado global del motor de juego.
 */
class PositionAnalyzerTest {

    private val analyzer = PositionAnalyzer()

    @Test
    fun `la posicion inicial es simetrica y neutra`() {
        val eval = analyzer.evaluate(initialGameState())
        assertEquals(
            0.0,
            eval.scoreWhitePov,
            absoluteTolerance = 1e-6,
            message = "el tablero inicial es espejado → score 0"
        )
        assertEquals(0.5f, eval.winProbWhite, absoluteTolerance = 0.001f)
        assertEquals(0.0, eval.metrics.material.difference, absoluteTolerance = 1e-6)
        assertFalse(eval.decisive)
    }

    @Test
    fun `la evaluacion es determinista`() {
        val a = analyzer.evaluate(initialGameState())
        val b = analyzer.evaluate(initialGameState())
        assertEquals(a.scoreWhitePov, b.scoreWhitePov)
        assertEquals(a.winProbWhite, b.winProbWhite)
    }

    @Test
    fun `el analisis no toca el historial de posiciones del motor de juego`() {
        val before = TaratiAI.instance.positionHistory.size
        repeat(5) { analyzer.evaluate(initialGameState()) }
        assertEquals(
            before,
            TaratiAI.instance.positionHistory.size,
            "el analizador usa su propio estado, no el singleton"
        )
    }

    @Test
    fun `expone el desglose de metricas para el panel`() {
        val eval = analyzer.evaluate(initialGameState())
        // Presente y coherente: en la posición inicial toda diferencia White−Black es 0.
        assertEquals(eval.metrics.mobility.difference, 0.0)
        assertEquals(eval.metrics.centerControl.difference, 0.0)
    }

    @Test
    fun `el score es la suma de los aportes del desglose`() {
        // Sobre una posición asimétrica (tras una jugada) para que los aportes no sean todos 0.
        val start = initialGameState()
        val state = start.applyMove(start.allMovesForTurn().first())
        val eval = analyzer.evaluate(state)

        assertEquals(EvalMetric.entries.size, eval.contributions.size)
        assertEquals(eval.scoreWhitePov, eval.contributions.sumOf { it.points }, absoluteTolerance = 1e-9)
        // Y coincide con la evaluación directa del BoardEvaluator con la misma config canónica.
        val direct = BoardEvaluator().evaluate(state, AnalysisConfig.evalConfig)
        assertEquals(direct, eval.scoreWhitePov, absoluteTolerance = 1e-9)
    }
}
