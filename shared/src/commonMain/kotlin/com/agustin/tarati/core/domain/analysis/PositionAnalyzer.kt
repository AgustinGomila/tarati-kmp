package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move

/**
 * Evaluación de solo lectura y sin efectos secundarios de un único [GameState]
 * para la UI de análisis.
 *
 * Usa su **propio** [BoardEvaluator] y una [config] fija, de modo que **nunca**
 * toca las cachés ni el historial de posiciones del motor de juego, y siempre
 * evalúa con la misma vara sin importar la dificultad del rival.
 *
 * La Etapa 0/1 provee la evaluación estática ([evaluate]), que es lo que usa la
 * barra de evaluación en vivo en todos los modos de juego. La evaluación con
 * búsqueda para el gráfico post-partida se agrega en la Etapa 3.
 */
class PositionAnalyzer(
    private val config: EvaluationConfig = AnalysisConfig.evalConfig,
) {
    private val evaluator = BoardEvaluator()

    /**
     * Motor propio para la evaluación con búsqueda ([evaluateSearched]). Se crea
     * perezosamente: la barra en vivo y el panel (que solo usan [evaluate] estática)
     * no lo instancian. Config canónica fija; nunca es [TaratiAI.instance].
     */
    private val searchEngine: TaratiAI by lazy { TaratiAI().also { it.setConfig(config) } }

    /**
     * Evaluación estática de [state]. Instantánea; ciega a la táctica que exceda
     * la posición actual (no ve el Mit a dos jugadas). Suficiente para la barra
     * en vivo, donde el costo debe ser nulo para no competir con la IA ni con la
     * animación.
     */
    fun evaluate(state: GameState): PositionEval {
        val metrics = evaluator.evaluateMetrics(state, config)
        val contributions = evaluationContributions(metrics, config)
        // El score se deriva de la suma de aportes → el número y el desglose siempre coinciden
        // (equivale a boardEvaluator.evaluate, que hace la misma suma ponderada).
        val score = contributions.sumOf { it.points }
        return PositionEval(
            scoreWhitePov = score,
            winProbWhite = WinProbability.winProbabilityWhite(score),
            materialEquiv = WinProbability.materialEquivalent(score),
            decisive = WinProbability.isDecisive(score),
            metrics = metrics,
            contributions = contributions,
        )
    }

    /**
     * Evaluación con **búsqueda superficial** promediando dos paridades de profundidad
     * ([AnalysisConfig.graphDifficulty] y [AnalysisConfig.graphDifficultyAlt], depth 3 y 2):
     * corre el minimax con los pesos canónicos y devuelve el score promedio + la mejor jugada.
     * Ve táctica que la evaluación estática no (p. ej. un Mit a dos jugadas), por eso la usa el
     * gráfico post-partida.
     *
     * **Por qué el promedio**: el score de un minimax de un solo lado no es *turn-independent*
     * en un juego de conversión (una profundidad impar favorece al que mueve, una par al rival);
     * sin promediar, la curva del gráfico oscila ~±20 puntos de win% **en cada ply**, haciendo
     * que toda jugada parezca un error. Promediar par+impar cancela ese sesgo de tempo.
     *
     * La **mejor jugada** se toma de la búsqueda [AnalysisConfig.graphDifficulty] (la más profunda).
     * Cada llamada resetea el historial del motor para que las posiciones no se contaminen entre sí.
     * Limitación aceptada para el gráfico: no reconstruye el historial real de repeticiones.
     */
    suspend fun evaluateSearched(state: GameState): SearchedEval {
        searchEngine.clearHistory()
        val primary = searchEngine.getNextMove(state, AnalysisConfig.graphDifficulty)
        searchEngine.clearHistory()
        val alt = searchEngine.getNextMove(state, AnalysisConfig.graphDifficultyAlt)
        val score = (primary.score + alt.score) / 2.0
        return SearchedEval(
            scoreWhitePov = score,
            winProbWhite = WinProbability.winProbabilityWhite(score),
            bestMove = primary.move,
        )
    }
}

/**
 * Resultado de analizar una posición. Todas las cifras en óptica de Blancas
 * (positivo = Blancas mejor).
 *
 * No se anota `@Immutable`: contiene [BoardEvaluator.BoardMetrics], que Compose
 * infiere inestable. La barra en vivo consume solo primitivos ([winProbWhite],
 * [decisive]), así que la estabilidad de este holder no afecta su recomposición;
 * el panel de análisis (Etapa 2) es quien consume [metrics].
 */
data class PositionEval(
    val scoreWhitePov: Double,
    val winProbWhite: Float,
    val materialEquiv: Double,
    val decisive: Boolean,
    val metrics: BoardEvaluator.BoardMetrics,
    /** Desglose del score por término (óptica de Blancas). Suma == [scoreWhitePov]. */
    val contributions: List<MetricContribution>,
)

/**
 * Resultado de [PositionAnalyzer.evaluateSearched]: score con búsqueda (óptica de
 * Blancas, positivo = Blancas mejor), su probabilidad de victoria y la mejor jugada
 * hallada por el motor.
 */
data class SearchedEval(
    val scoreWhitePov: Double,
    val winProbWhite: Float,
    val bestMove: Move?,
)
