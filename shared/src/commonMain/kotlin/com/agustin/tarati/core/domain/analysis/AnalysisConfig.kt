package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.analysis.AnalysisConfig.GRAPH_SEARCH_DEPTH
import com.agustin.tarati.core.domain.analysis.AnalysisConfig.canonicalCobScore
import com.agustin.tarati.core.domain.analysis.AnalysisConfig.evalConfig

/**
 * Configuración fija e independiente del rival usada por todo el análisis de
 * posición: la barra de evaluación en vivo, el panel de análisis y el gráfico
 * post-partida.
 *
 * El análisis debe leerse igual sin importar la dificultad a la que juegue el
 * oponente, así que **nunca** usa la [EvaluationConfig] del usuario: siempre usa
 * [evalConfig] como única vara canónica. Se elige la config de CHAMPION por ser
 * el tier con los pesos más completos (material + posicional + conversión).
 */
object AnalysisConfig {
    /** Pesos de evaluación canónicos para el análisis (tier más fuerte). */
    val evalConfig: EvaluationConfig = EvaluationConfig.CHAMPION

    /**
     * Valor de un Cob bajo [evalConfig]. Se usa para expresar la evaluación como
     * ventaja material equivalente ("+1.5 Cobs"), análogo a los peones del ajedrez.
     */
    val canonicalCobScore: Double = evalConfig.cobScore

    /**
     * Pendiente logística que mapea un score de tablero (óptica de Blancas,
     * positivo = Blancas mejor) a una probabilidad de victoria. **Simétrica**
     * alrededor de 0 (score 0 → 50%). Ajustada a mano para que una ventaja de un
     * Cob (~[canonicalCobScore] puntos) se lea como ~69%. A calibrar contra el
     * corpus de partidas en una etapa posterior.
     */
    const val WIN_PROB_K: Double = 0.004

    /** Profundidad de búsqueda del gráfico de evaluación post-partida (Etapa 3). */
    const val GRAPH_SEARCH_DEPTH: Int = 3

    /**
     * Versión del análisis persistido. **Incrementar** cuando cambie algo que
     * altere los resultados (pesos de [evalConfig], [WIN_PROB_K], profundidad,
     * fórmula de evaluación) para invalidar las cachés viejas en disco.
     */
    const val ANALYSIS_VERSION: Int = 1

    /**
     * Nivel usado para la búsqueda del gráfico: el que iguala [GRAPH_SEARCH_DEPTH]
     * (la profundidad del minimax se controla por [Difficulty.depth]; los pesos los
     * fija [evalConfig], no el nivel). Depth 3 = [Difficulty.MEDIUM].
     */
    val graphDifficulty: Difficulty =
        Difficulty.entries.firstOrNull { it.depth == GRAPH_SEARCH_DEPTH } ?: Difficulty.MEDIUM
}
