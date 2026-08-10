@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.game6.ai.MpBot
import com.agustin.tarati.core.domain.game6.ai.MpBotLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** `true` si el bundle corre dentro de un Web Worker (no en la página principal). */
@JsFun("() => (typeof WorkerGlobalScope !== 'undefined' && typeof self !== 'undefined' && self instanceof WorkerGlobalScope)")
external fun isEngineWorkerContext(): Boolean

/** Instala el handler de mensajes del worker; [cb] recibe el `data` (string JSON). */
@JsFun("(cb) => { self.onmessage = (e) => cb(e.data); }")
private external fun setWorkerOnMessage(cb: (JsString) -> Unit)

/**
 * Envía un mensaje (string JSON) del worker al hilo principal. Se pasa [JsString] explícito
 * (no `String` de Kotlin) para no depender de la conversión automática en el cruce — el
 * sospechoso del fallo `postMessage: Overload resolution failed` con el JSON grande del resultado.
 */
@JsFun("(msg) => { self.postMessage(msg); }")
private external fun workerPostToMain(msg: JsString)

private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runner de análisis (mismo dispatcher del worker: aquí el event-loop es propio, sin UI). */
private val analysisRunner = DefaultAnalysisRunner()

/**
 * Motor persistente del worker para la IA en vivo. A diferencia del análisis (one-shot), se
 * conserva entre jugadas: así la **transposition table** sobrevive y acelera búsquedas sucesivas.
 * El [positionHistory][EngineJob.positionHistory] autoritativo llega con cada job (el flujo de
 * juego lo construye en el hilo principal), y se inyecta con [TaratiAI.replaceHistory] antes de
 * buscar — exactamente como hacen los bots del servidor.
 */
private val aiEngine = TaratiAI()

/**
 * Punto de entrada del **engine worker**. Se invoca desde `main()` cuando el bundle corre en un
 * worker ([isEngineWorkerContext]). Escucha [EngineJob], despacha por [EngineJob.kind] y responde
 * por `postMessage`. Los trabajos se atienden por id, de a uno (los cómputos del juego son
 * secuenciales por naturaleza).
 *
 * **Nunca deja escapar una excepción de la corrutina**: un fallo del cómputo se reporta como reply
 * [WorkerReplyKind.ERROR] (para que el hilo principal caiga al fallback de ese trabajo), y cada
 * `postMessage` se envuelve para que un fallo del canal no tumbe la corrutina ni deje un rechazo
 * sin atrapar. Si el propio `postMessage` está roto, el watchdog del hilo principal recupera igual.
 */
fun startEngineWorker() {
    setWorkerOnMessage { data ->
        workerScope.launch {
            val job = try {
                workerJson.decodeFromString<EngineJob>(data.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Trabajo ilegible: sin id no hay a quién avisar; el watchdog del hilo
                // principal lo cubre. Se descarta silenciosamente.
                return@launch
            }
            try {
                when (job.kind) {
                    EngineJobKind.ANALYZE -> runAnalyze(job)
                    EngineJobKind.BEST_MOVE -> runBestMove(job)
                    EngineJobKind.MP_BEST_MOVE -> runMpBestMove(job)
                    else -> postSafely(WorkerReply(job.id, WorkerReplyKind.ERROR))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                postSafely(WorkerReply(job.id, WorkerReplyKind.ERROR))
            }
        }
    }
}

private suspend fun runAnalyze(job: EngineJob) {
    val analysis = analysisRunner.run(
        initialBoardPosition = job.initialBoardPosition.orEmpty(),
        moves = job.moves.orEmpty(),
        onProgress = { p -> postSafely(WorkerReply(job.id, WorkerReplyKind.PROGRESS, progress = p)) },
    )
    postSafely(WorkerReply(job.id, WorkerReplyKind.RESULT, result = analysis))
}

private suspend fun runBestMove(job: EngineJob) {
    val gameState = job.gameState ?: run {
        postSafely(WorkerReply(job.id, WorkerReplyKind.ERROR))
        return
    }
    aiEngine.setConfig(EvaluationConfig.getByDifficulty(job.difficulty ?: Difficulty.DEFAULT))
    aiEngine.replaceHistory(job.positionHistory ?: emptyMap())
    val eval = aiEngine.getNextMove(gameState)
    postSafely(WorkerReply(job.id, WorkerReplyKind.BEST_MOVE, move = eval.move, score = eval.score))
}

/**
 * Bot del multijugador (game6). [MpBot.chooseMove] es síncrono y autosuficiente (el [MpGameState] trae
 * todo lo que necesita), así que no requiere motor persistente ni historial. Desempata con
 * `Random.Default` en el worker: la variedad del desempate es aleatoria en cualquier lado.
 */
private fun runMpBestMove(job: EngineJob) {
    val state = job.mpState ?: run {
        postSafely(WorkerReply(job.id, WorkerReplyKind.ERROR))
        return
    }
    val move = MpBot.chooseMove(state, job.mpLevel ?: MpBotLevel.DEFAULT)
    postSafely(WorkerReply(job.id, WorkerReplyKind.MP_BEST_MOVE, mpMove = move))
}

/** Postea al hilo principal tragando cualquier fallo del canal (no debe tumbar la corrutina). */
private fun postSafely(reply: WorkerReply) {
    runCatching { workerPostToMain(workerJson.encodeToString(reply).toJsString()) }
}
