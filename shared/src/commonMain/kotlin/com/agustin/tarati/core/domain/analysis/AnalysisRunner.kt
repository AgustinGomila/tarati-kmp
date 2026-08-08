package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ejecuta el análisis de una partida **fuera del hilo de UI**. Es el punto de
 * extensión por plataforma:
 * - [DefaultAnalysisRunner] (Android/Desktop/iOS y fallback): corre en
 *   [Dispatchers.Default] — hilos reales.
 * - En Web, un runner basado en **Web Worker** (siguiente iteración) reemplazará
 *   este binding para sacar el cómputo del hilo principal del navegador.
 *
 * Recibe `initialBoardPosition` + `moves` (payload chico y serializable, ideal para
 * cruzar la frontera de un worker) y reconstruye las posiciones internamente.
 */
interface AnalysisRunner {
    suspend fun run(
        initialBoardPosition: String,
        moves: List<Move>,
        onProgress: (Float) -> Unit,
    ): GameAnalysis
}

/**
 * Runner por defecto: reconstruye las posiciones y corre [GameAnalyzer] en
 * [Dispatchers.Default]. En Web el dispatcher es el mismo event-loop, pero
 * [GameAnalyzer] cede el hilo entre plies para no congelar la UI.
 */
class DefaultAnalysisRunner : AnalysisRunner {
    override suspend fun run(
        initialBoardPosition: String,
        moves: List<Move>,
        onProgress: (Float) -> Unit,
    ): GameAnalysis = withContext(Dispatchers.Default) {
        val initial = runCatching { parseBoardNotation(initialBoardPosition) }.getOrElse { initialGameState() }
        var state = initial
        val perMoveStates = moves.map { move ->
            state = state.applyMove(move)
            state
        }
        GameAnalyzer.analyze(initialState = initial, perMoveStates = perMoveStates, onProgress = onProgress)
    }
}
