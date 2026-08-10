package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.CancellationException

/**
 * [AnalysisRunner] que ejecuta el análisis en el **engine worker** compartido
 * ([EngineWorkerClient]), fuera del hilo principal del navegador. Envía un job [EngineJobKind.ANALYZE]
 * y suspende hasta el resultado, propagando el progreso por-ply.
 *
 * Si el worker no está disponible o falla, cae de forma transparente a [fallback] (análisis en el
 * hilo principal) — la feature funciona igual, solo sin el offload.
 */
class WorkerAnalysisRunner(
    private val fallback: AnalysisRunner = DefaultAnalysisRunner(),
) : AnalysisRunner {

    override suspend fun run(
        initialBoardPosition: String,
        moves: List<Move>,
        onProgress: (Float) -> Unit,
    ): GameAnalysis {
        if (!EngineWorkerClient.available) {
            return fallback.run(initialBoardPosition, moves, onProgress)
        }
        return try {
            val reply = EngineWorkerClient.submit(
                buildJob = { id ->
                    EngineJob(
                        id = id,
                        kind = EngineJobKind.ANALYZE,
                        initialBoardPosition = initialBoardPosition,
                        moves = moves,
                    )
                },
                onProgress = onProgress,
            )
            reply.result ?: throw WorkerFailure()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // El worker falló (o se colgó) → reintenta en el hilo principal para no dejar al
            // usuario sin análisis.
            fallback.run(initialBoardPosition, moves, onProgress)
        }
    }
}
