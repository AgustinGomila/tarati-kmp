@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
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

/** Envía un mensaje (string JSON) del worker al hilo principal. */
@JsFun("(msg) => { self.postMessage(msg); }")
private external fun workerPostToMain(msg: String)

private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val runner = DefaultAnalysisRunner()

/**
 * Punto de entrada del Web Worker de análisis. Se invoca desde `main()` cuando el bundle
 * corre en un worker ([isAnalysisWorkerContext]). Escucha [AnalysisJob], corre el análisis
 * en el hilo del worker (fuera del hilo principal del navegador) y responde progreso +
 * resultado por `postMessage`. Los trabajos se atienden por id.
 */
fun startAnalysisWorker() {
    setWorkerOnMessage { data ->
        val job = workerJson.decodeFromString<AnalysisJob>(data.toString())
        workerScope.launch {
            val analysis = runner.run(
                initialBoardPosition = job.initialBoardPosition,
                moves = job.moves,
                onProgress = { p ->
                    workerPostToMain(workerJson.encodeToString(WorkerReply(job.id, "progress", progress = p)))
                },
            )
            workerPostToMain(workerJson.encodeToString(WorkerReply(job.id, "result", result = analysis)))
        }
    }
}
