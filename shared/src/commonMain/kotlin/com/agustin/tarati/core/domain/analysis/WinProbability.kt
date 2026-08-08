package com.agustin.tarati.core.domain.analysis

import kotlin.math.abs
import kotlin.math.exp

/**
 * Traduce una evaluación de tablero cruda (siempre óptica de Blancas, positivo =
 * Blancas mejor) a las representaciones que ve el usuario en la UI de análisis.
 *
 * El mapeo a probabilidad es **simétrico**: `winProbabilityWhite(+x) +
 * winProbabilityWhite(-x) == 1`. La ventaja estructural de Negras del juego (ver
 * `docs/internal/game_dynamics.md`) **no** se hornea aquí — se documenta aparte;
 * la barra refleja el juicio del motor sobre *esta* posición, como el eval bar
 * del ajedrez.
 */
object WinProbability {
    /**
     * Cota de la probabilidad para que la barra nunca se vacíe del todo ni se
     * llene del todo (deja siempre un hilo del color perdedor visible).
     */
    private const val MIN_PROB = 0.01f
    private const val MAX_PROB = 0.99f

    /** Probabilidad de victoria de Blancas en `[MIN_PROB, MAX_PROB]`. */
    fun winProbabilityWhite(scoreWhitePov: Double): Float {
        val p = 1.0 / (1.0 + exp(-AnalysisConfig.WIN_PROB_K * scoreWhitePov))
        return p.toFloat().coerceIn(MIN_PROB, MAX_PROB)
    }

    /** Evaluación expresada como número equivalente de Cobs (análogo a los peones). */
    fun materialEquivalent(scoreWhitePov: Double): Double =
        scoreWhitePov / AnalysisConfig.canonicalCobScore

    /** `true` cuando el score alcanza o supera el umbral decisivo (ganado/perdido) del motor. */
    fun isDecisive(scoreWhitePov: Double): Boolean {
        val cfg = AnalysisConfig.evalConfig
        return abs(scoreWhitePov) >= cfg.winningScore * cfg.winningThreshold
    }
}
