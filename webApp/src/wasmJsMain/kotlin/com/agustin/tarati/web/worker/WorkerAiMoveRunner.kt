package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.runner.AiMoveRunner
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState
import kotlinx.coroutines.CancellationException

/**
 * [AiMoveRunner] que calcula la jugada de la IA en el **engine worker** compartido
 * ([EngineWorkerClient]), sacando el minimax del hilo principal del navegador → interacción y
 * animaciones fluidas durante "el turno de la IA".
 *
 * Envía un job [EngineJobKind.BEST_MOVE] con el [GameState], la [Difficulty] (el worker reconstruye
 * la config canónica) y el `positionHistory` (para la triple repetición, igual que los bots del
 * servidor). Si el worker no está disponible o falla, cae de forma transparente a [fallback]
 * (búsqueda en el hilo principal, cooperativa con yield).
 */
class WorkerAiMoveRunner(
    private val fallback: AiMoveRunner,
) : AiMoveRunner {

    override suspend fun bestMove(
        gameState: GameState,
        difficulty: Difficulty,
        positionHistory: Map<String, Int>,
    ): MoveEval {
        if (!EngineWorkerClient.available) {
            return fallback.bestMove(gameState, difficulty, positionHistory)
        }
        return try {
            val reply = EngineWorkerClient.submit(
                buildJob = { id ->
                    EngineJob(
                        id = id,
                        kind = EngineJobKind.BEST_MOVE,
                        gameState = gameState,
                        difficulty = difficulty,
                        positionHistory = positionHistory,
                    )
                },
                // La IA no emite progreso: su ventana de inactividad debe superar el límite de
                // tiempo de búsqueda (CHAMPION ~10 s) para no dar falso "colgado".
                inactivityTimeoutMs = BEST_MOVE_TIMEOUT_MS,
            )
            MoveEval(score = reply.score, move = reply.move)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fallback.bestMove(gameState, difficulty, positionHistory)
        }
    }

    private companion object {
        const val BEST_MOVE_TIMEOUT_MS = 30_000L
    }
}
