package com.agustin.tarati.core.domain.ai.runner

import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calcula la mejor jugada de la IA **fuera del hilo de UI**. Es el punto de extensión por
 * plataforma, espejo de [com.agustin.tarati.core.domain.analysis.AnalysisRunner]:
 * - [DefaultAiMoveRunner] (Android/Desktop/iOS y fallback): corre en [Dispatchers.Default] —
 *   hilos reales.
 * - En Web, un runner basado en **Web Worker** saca el minimax del hilo principal del navegador
 *   (donde [Dispatchers.Default] es el mismo event-loop y solo el hack cooperativo de yield evita
 *   el freeze).
 *
 * El job lleva un payload chico y serializable: [gameState], la [Difficulty] (con la que se
 * reconstruye la config canónica vía [EvaluationConfig.getByDifficulty]) y [positionHistory]
 * (hash→conteo) para la detección de triple repetición.
 */
interface AiMoveRunner {
    suspend fun bestMove(
        gameState: GameState,
        difficulty: Difficulty,
        positionHistory: Map<String, Int>,
    ): MoveEval
}

/**
 * Runner por defecto: aplica la config de [difficulty] al motor y busca en [Dispatchers.Default].
 *
 * Usa el **motor singleton inyectado**, cuyo [IAIEngine.positionHistory] es la fuente de verdad
 * (se construye incrementalmente en el flujo de juego). Por eso ignora el parámetro
 * [positionHistory]: está pensado para el runner de worker, que corre sobre una instancia aislada
 * sin ese historial y necesita recibirlo con cada job. En nativo el motor ya lo tiene.
 */
class DefaultAiMoveRunner(
    private val engine: IAIEngine,
) : AiMoveRunner {
    override suspend fun bestMove(
        gameState: GameState,
        difficulty: Difficulty,
        positionHistory: Map<String, Int>,
    ): MoveEval = withContext(Dispatchers.Default) {
        engine.setConfig(EvaluationConfig.getByDifficulty(difficulty))
        engine.getNextMove(gameState)
    }
}
