@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Crea el worker reusando el mismo bundle, pero lo arranca desde un **bootstrap** que
 * shimea `document`/`window` **antes** de `importScripts` del bundle: el runtime de
 * webpack referencia `document.baseURI`/`document.currentScript` en su init (que en un
 * worker no existen → ReferenceError). Con el shim eso sobrevive; después `main()`
 * bifurca a modo worker antes de Compose/skiko, así que no se necesita DOM real.
 */
@JsFun(
    """
    (url) => {
      const abs = new URL(url, self.location.href).href;
      const boot = "self.window = self; self.document = { baseURI: '" + abs + "', currentScript: null }; importScripts('" + abs + "');";
      const blob = new Blob([boot], { type: 'application/javascript' });
      return new Worker(URL.createObjectURL(blob));
    }
    """,
)
private external fun createWorker(url: String): JsAny

/** Envía un mensaje (string JSON) del hilo principal al worker. */
@JsFun("(worker, msg) => { worker.postMessage(msg); }")
private external fun workerPost(worker: JsAny, msg: String)

/** Instala el handler de respuestas del worker; [cb] recibe el `data` (string JSON). */
@JsFun("(worker, cb) => { worker.onmessage = (e) => cb(e.data); }")
private external fun workerSetOnMessage(worker: JsAny, cb: (JsString) -> Unit)

/** Instala el handler de error del worker; [cb] recibe un detalle del error (mensaje/archivo/línea). */
@JsFun(
    "(worker, cb) => { worker.onerror = (e) => { try { e.preventDefault(); } catch(_) {} " +
        "var d = (e && ((e.message || String(e)) + ' @ ' + (e.filename||'?') + ':' + (e.lineno||'?'))) || 'unknown'; " +
        "cb(d); }; }",
)
private external fun workerSetOnError(worker: JsAny, cb: (JsString) -> Unit)

/** Aviso en consola solo cuando el worker no está disponible o falla (cae al fallback). */
@JsFun("(msg) => { console.warn(msg); }")
private external fun consoleWarn(msg: String)

private class WorkerFailure : RuntimeException("Analysis worker failed")

/**
 * [AnalysisRunner] que ejecuta el análisis en un **Web Worker** (fuera del hilo principal
 * del navegador). Reusa el mismo bundle (`new Worker('tarati.js')`): `main()` detecta el
 * contexto worker y arranca [startAnalysisWorker].
 *
 * Un único worker atiende trabajos secuenciales, ruteados por id: [run] envía un
 * [AnalysisJob] y suspende hasta el [WorkerReply] "result", propagando cada "progress"
 * a `onProgress`. Los mensajes se entregan en el event-loop del hilo principal, así que
 * `onProgress` puede tocar estado Compose sin marshaling adicional.
 *
 * **Robustez**: si el worker no se puede crear o falla (el bundle no carga en un worker,
 * error de runtime), cae de forma transparente a [fallback] (análisis en el hilo
 * principal) — la feature funciona igual, solo sin el offload.
 */
class WorkerAnalysisRunner(
    private val fallback: AnalysisRunner = DefaultAnalysisRunner(),
) : AnalysisRunner {

    private val worker: JsAny? = runCatching { createWorker(WORKER_URL) }.getOrNull()
    private var broken: Boolean = worker == null
    private var nextId = 0
    private val pending = mutableMapOf<Int, Pending>()

    private class Pending(
        val onProgress: (Float) -> Unit,
        val deferred: CompletableDeferred<GameAnalysis>,
    )

    init {
        val w = worker
        if (w != null) {
            workerSetOnMessage(w) { data -> onReply(data.toString()) }
            workerSetOnError(w) { detail ->
                consoleWarn("[analysis] error del worker → fallback al hilo principal :: $detail")
                markBroken()
            }
        } else {
            consoleWarn("[analysis] Worker no disponible → análisis en el hilo principal (fallback)")
        }
    }

    override suspend fun run(
        initialBoardPosition: String,
        moves: List<Move>,
        onProgress: (Float) -> Unit,
    ): GameAnalysis {
        val w = worker
        if (broken || w == null) {
            return fallback.run(initialBoardPosition, moves, onProgress)
        }
        val id = nextId++
        val deferred = CompletableDeferred<GameAnalysis>()
        pending[id] = Pending(onProgress, deferred)
        workerPost(w, workerJson.encodeToString(AnalysisJob(id, initialBoardPosition, moves)))
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            pending.remove(id)
            throw e
        } catch (_: Throwable) {
            // El worker falló → reintenta en el hilo principal para no dejar al usuario sin análisis.
            consoleWarn("[analysis] el worker falló → análisis en el hilo principal (fallback)")
            broken = true
            fallback.run(initialBoardPosition, moves, onProgress)
        }
    }

    private fun onReply(text: String) {
        val reply = runCatching { workerJson.decodeFromString<WorkerReply>(text) }.getOrNull() ?: return
        val entry = pending[reply.id] ?: return
        when (reply.kind) {
            "progress" -> entry.onProgress(reply.progress)
            "result" -> {
                pending.remove(reply.id)
                reply.result?.let { entry.deferred.complete(it) }
            }
        }
    }

    /** Marca el worker como inservible y falla los trabajos en curso (caerán al fallback). */
    private fun markBroken() {
        broken = true
        val entries = pending.values.toList()
        pending.clear()
        entries.forEach { it.deferred.completeExceptionally(WorkerFailure()) }
    }

    private companion object {
        const val WORKER_URL = "tarati.js"
    }
}
