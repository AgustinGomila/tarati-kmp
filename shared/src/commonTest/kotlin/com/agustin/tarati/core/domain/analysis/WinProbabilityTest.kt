package com.agustin.tarati.core.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica el mapeo puro score → representación de usuario: simetría, monotonía,
 * clamp, equivalencia material y umbral decisivo.
 */
class WinProbabilityTest {

    @Test
    fun `score cero es cincuenta por ciento`() {
        assertEquals(0.5f, WinProbability.winProbabilityWhite(0.0), absoluteTolerance = 0.001f)
    }

    @Test
    fun `la probabilidad es simetrica alrededor de cero`() {
        // p(+x) + p(-x) == 1 en el rango sin clamp.
        for (x in listOf(50.0, 100.0, 200.0, 300.0)) {
            val sum = WinProbability.winProbabilityWhite(x) + WinProbability.winProbabilityWhite(-x)
            assertEquals(1.0f, sum, absoluteTolerance = 0.001f, message = "x=$x")
        }
    }

    @Test
    fun `la probabilidad es monotona creciente en el score`() {
        val p0 = WinProbability.winProbabilityWhite(0.0)
        val p100 = WinProbability.winProbabilityWhite(100.0)
        val p200 = WinProbability.winProbabilityWhite(200.0)
        val pNeg = WinProbability.winProbabilityWhite(-100.0)
        assertTrue(pNeg < p0, "más ventaja de Negras → menos win% de Blancas")
        assertTrue(p0 < p100)
        assertTrue(p100 < p200)
    }

    @Test
    fun `una ventaja de un Cob ronda el setenta por ciento`() {
        val p = WinProbability.winProbabilityWhite(AnalysisConfig.canonicalCobScore)
        assertTrue(p in 0.65f..0.73f, "esperado ~0.69, fue $p")
    }

    @Test
    fun `la probabilidad esta acotada en los extremos`() {
        assertTrue(WinProbability.winProbabilityWhite(1_000_000.0) <= 0.99f)
        assertTrue(WinProbability.winProbabilityWhite(-1_000_000.0) >= 0.01f)
    }

    @Test
    fun `el equivalente material divide por el valor de un Cob`() {
        assertEquals(1.0, WinProbability.materialEquivalent(AnalysisConfig.canonicalCobScore), absoluteTolerance = 1e-9)
        assertEquals(
            -2.0,
            WinProbability.materialEquivalent(-2 * AnalysisConfig.canonicalCobScore),
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun `el umbral decisivo distingue posiciones ganadas de las igualadas`() {
        val cfg = AnalysisConfig.evalConfig
        val decisiveScore = cfg.winningScore * cfg.winningThreshold
        assertTrue(WinProbability.isDecisive(decisiveScore))
        assertTrue(WinProbability.isDecisive(-decisiveScore))
        assertFalse(WinProbability.isDecisive(0.0))
        assertFalse(WinProbability.isDecisive(decisiveScore - 1.0))
    }
}
