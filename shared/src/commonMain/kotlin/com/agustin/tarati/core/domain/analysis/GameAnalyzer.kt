package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.engine.cooperativeYield
import com.agustin.tarati.core.domain.game.play.GameState
import kotlinx.serialization.Serializable

/**
 * Punto de evaluación de un ply del gráfico: probabilidad de victoria de Blancas
 * y el score (para tooltips). El índice en [GameAnalysis.perMove] coincide con el
 * índice del movimiento en el historial de la partida.
 */
@Serializable
data class PlyEval(
    val winProbWhite: Float,
    val scoreWhitePov: Double,
)

/** Análisis de una partida: el punto inicial + un punto por movimiento. */
@Serializable
data class GameAnalysis(
    val initial: PlyEval,
    val perMove: List<PlyEval>,
) {
    /** Serie continua para el gráfico: el punto inicial seguido del de cada movimiento. */
    val series: List<PlyEval> get() = buildList { add(initial); addAll(perMove) }
}

/**
 * Analiza una partida ply por ply con [PositionAnalyzer.evaluateSearched] (búsqueda
 * superficial, depth 3), produciendo la serie de probabilidades para el gráfico de
 * evaluación post-partida.
 *
 * Cede el hilo entre plies con [cooperativeYield] para no congelar la UI en el hilo principal
 * (fallback) ni el hilo de fondo en nativo; en un Web Worker es no-op (corre sincrónico, sin
 * reprogramar la corrutina — ver [cooperativeYield]). Informa el progreso `[0,1]` vía [onProgress].
 */
object GameAnalyzer {
    suspend fun analyze(
        initialState: GameState,
        perMoveStates: List<GameState>,
        analyzer: PositionAnalyzer = PositionAnalyzer(),
        onProgress: (Float) -> Unit = {},
    ): GameAnalysis {
        val total = perMoveStates.size + 1
        var done = 0

        fun tick() {
            done++
            onProgress(done.toFloat() / total)
        }

        val initial = analyzer.evaluateSearched(initialState).toPlyEval()
        tick()
        cooperativeYield()

        val perMove = ArrayList<PlyEval>(perMoveStates.size)
        for (state in perMoveStates) {
            perMove.add(analyzer.evaluateSearched(state).toPlyEval())
            tick()
            cooperativeYield()
        }
        return GameAnalysis(initial, perMove)
    }

    private fun SearchedEval.toPlyEval() = PlyEval(winProbWhite = winProbWhite, scoreWhitePov = scoreWhitePov)
}
