package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON compartido entre el lado main y el worker (misma configuración a ambos lados). */
internal val workerJson: Json = Json { ignoreUnknownKeys = true }

/** Trabajo enviado del hilo principal al worker. Payload chico y serializable. */
@Serializable
internal data class AnalysisJob(
    val id: Int,
    val initialBoardPosition: String,
    val moves: List<Move>,
)

/**
 * Respuesta del worker al hilo principal: progreso incremental o el resultado final.
 * [kind] = "progress" (usa [progress]) o "result" (usa [result]).
 */
@Serializable
internal data class WorkerReply(
    val id: Int,
    val kind: String,
    val progress: Float = 0f,
    val result: GameAnalysis? = null,
)
