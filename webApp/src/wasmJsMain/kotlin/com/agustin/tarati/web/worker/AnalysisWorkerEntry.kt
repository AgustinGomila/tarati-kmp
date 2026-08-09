@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** `true` si el bundle corre dentro de un Web Worker (no en la página principal). */
@JsFun("() => (typeof WorkerGlobalScope !== 'undefined' && typeof self !== 'undefined' && self instanceof WorkerGlobalScope)")
external fun isAnalysisWorkerContext(): Boolean

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
private val runner = DefaultAnalysisRunner()

/**
 * Punto de entrada del Web Worker de análisis. Se invoca desde `main()` cuando el bundle
 * corre en un worker ([isAnalysisWorkerContext]). Escucha [AnalysisJob], corre el análisis
 * en el hilo del worker (fuera del hilo principal del navegador) y responde progreso +
 * resultado por `postMessage`. Los trabajos se atienden por id.
 *
 * **Nunca deja escapar una excepción de la corrutina**: un fallo del análisis se reporta
 * como reply "error" (para que el hilo principal caiga al fallback de ese trabajo), y cada
 * `postMessage` se envuelve para que un fallo del canal no tumbe la corrutina ni deje un
 * rechazo sin atrapar. Si el propio `postMessage` está roto, el watchdog del hilo principal
 * detecta el silencio y recupera igual.
 */
fun startAnalysisWorker() {
    setWorkerOnMessage { data ->
        workerScope.launch {
            val job = try {
                workerJson.decodeFromString<AnalysisJob>(data.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Trabajo ilegible: sin id no hay a quién avisar; el watchdog del hilo
                // principal lo cubre. Se descarta silenciosamente.
                return@launch
            }
            try {
                val analysis = runner.run(
                    initialBoardPosition = job.initialBoardPosition,
                    moves = job.moves,
                    onProgress = { p ->
                        postSafely(WorkerReply(job.id, "progress", progress = p))
                    },
                )
                postSafely(WorkerReply(job.id, "result", result = analysis))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                postSafely(WorkerReply(job.id, "error"))
            }
        }
    }
}

/** Postea al hilo principal tragando cualquier fallo del canal (no debe tumbar la corrutina). */
private fun postSafely(reply: WorkerReply) {
    runCatching { workerPostToMain(workerJson.encodeToString(reply).toJsString()) }
}
