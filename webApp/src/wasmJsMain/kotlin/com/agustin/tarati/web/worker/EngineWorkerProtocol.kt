package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game6.ai.MpBotLevel
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON compartido entre el lado main y el worker (misma configuración a ambos lados).
 *
 * `allowStructuredMapKeys = true` es **imprescindible**: el job `bestMove` serializa un [GameState],
 * cuyo `cobs` es un `Map<Vertex, Cob>` con **clave estructurada** (Vertex es una `class`). Sin el flag,
 * kotlinx lanza `JsonEncodingException` al encodear ese mapa → el job nunca sale y el runner cae al
 * fallback del hilo principal en cada jugada (la búsqueda de Champion vuelve a trabar la UI). Con el
 * flag, el mapa viaja como arreglo de pares clave/valor y round-trips en ambas direcciones.
 */
internal val workerJson: Json = Json {
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
}

/** Tipos de trabajo que atiende el engine worker (discriminador de [EngineJob.kind]). */
internal object EngineJobKind {
    const val ANALYZE = "analyze"
    const val BEST_MOVE = "bestMove"
    const val MP_BEST_MOVE = "mpBestMove"
}

/** Tipos de respuesta del worker al hilo principal (discriminador de [WorkerReply.kind]). */
internal object WorkerReplyKind {
    const val PROGRESS = "progress"        // avance incremental de ANALYZE
    const val RESULT = "result"            // resultado final de ANALYZE
    const val BEST_MOVE = "bestMove"       // resultado de BEST_MOVE (IA clásica)
    const val MP_BEST_MOVE = "mpBestMove"  // resultado de MP_BEST_MOVE (bots del multijugador)
    const val ERROR = "error"              // el worker atrapó una excepción del cómputo
}

/**
 * Trabajo enviado del hilo principal al worker. Un único envelope: [kind] discrimina el tipo y
 * los campos específicos de cada job son nulos para los demás. Payload chico y serializable.
 */
@Serializable
internal data class EngineJob(
    val id: Int = -1,
    val kind: String,
    // ── ANALYZE ──
    val initialBoardPosition: String? = null,
    val moves: List<Move>? = null,
    // ── BEST_MOVE ──
    val gameState: GameState? = null,
    val difficulty: Difficulty? = null,
    val positionHistory: Map<String, Int>? = null,
    // ── MP_BEST_MOVE ──
    val mpState: MpGameState? = null,
    val mpLevel: MpBotLevel? = null,
)

/**
 * Respuesta del worker al hilo principal: progreso incremental o resultado final.
 * Según [kind] usa [progress]/[result] (ANALYZE), [move]/[score] (BEST_MOVE) o [mpMove] (MP_BEST_MOVE).
 */
@Serializable
internal data class WorkerReply(
    val id: Int,
    val kind: String,
    val progress: Float = 0f,
    val result: GameAnalysis? = null,
    val move: Move? = null,
    val score: Double = 0.0,
    val mpMove: MpMove? = null,
)
