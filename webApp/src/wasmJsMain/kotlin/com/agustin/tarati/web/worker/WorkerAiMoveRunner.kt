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
                // tiempo de búsqueda para no dar falso "colgado" — pero EASY/MEDIUM buscan <1 s, así
                // que su ventana es mucho menor que la de HARD/CHAMPION (búsqueda profunda ~10 s).
                // Ventana más chica = un worker muerto/colgado se detecta y recupera antes.
                inactivityTimeoutMs = timeoutFor(difficulty),
            )
            MoveEval(score = reply.score, move = reply.move)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fallback.bestMove(gameState, difficulty, positionHistory)
        }
    }

    private fun timeoutFor(difficulty: Difficulty): Long = when (difficulty) {
        // Búsqueda superficial (<1 s): margen amplio sobre eso + el cold-start del worker recreado (~1-3 s).
        Difficulty.EASY, Difficulty.MEDIUM -> FAST_TIMEOUT_MS
        // Búsqueda profunda con `timeLimitMs` ~10 s (MinimaxStrategy): ventana holgada para no falsear.
        Difficulty.HARD, Difficulty.CHAMPION -> DEEP_TIMEOUT_MS
    }

    private companion object {
        const val FAST_TIMEOUT_MS = 10_000L
        const val DEEP_TIMEOUT_MS = 30_000L
    }
}
