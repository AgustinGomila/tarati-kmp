package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifica la búsqueda superficial ([PositionAnalyzer.evaluateSearched]) y el
 * recorrido por-ply ([GameAnalyzer]) usado por el gráfico post-partida.
 */
class GameAnalyzerTest {

    /** Juega [n] medios-movimientos legales desde el inicio, devolviendo el estado tras cada uno. */
    private fun playedStates(n: Int): List<GameState> {
        var s = initialGameState()
        return buildList {
            repeat(n) {
                s = s.applyMove(s.allMovesForTurn().first())
                add(s)
            }
        }
    }

    @Test
    fun `la busqueda devuelve una probabilidad valida y una mejor jugada`(): TestResult = runTest {
        val analyzer = PositionAnalyzer()
        val eval = analyzer.evaluateSearched(initialGameState())
        assertTrue(eval.winProbWhite in 0f..1f)
        // La posición inicial no es terminal → hay una mejor jugada.
        assertNotNull(eval.bestMove)
        // Tablero espejado → el motor no debería ver ventaja clara para ninguno.
        assertTrue(eval.winProbWhite in 0.35f..0.65f, "esperado ~0.5, fue ${eval.winProbWhite}")
    }

    @Test
    fun `analyze produce un punto inicial mas uno por movimiento con progreso completo`(): TestResult = runTest {
        val states = playedStates(4)
        var lastProgress = 0f
        val analysis = GameAnalyzer.analyze(
            initialState = initialGameState(),
            perMoveStates = states,
            onProgress = { lastProgress = it },
        )

        assertEquals(states.size, analysis.perMove.size)
        assertEquals(states.size + 1, analysis.series.size)
        assertEquals(1f, lastProgress, absoluteTolerance = 1e-6f)
        assertTrue(analysis.series.all { it.winProbWhite in 0f..1f })
    }
}
