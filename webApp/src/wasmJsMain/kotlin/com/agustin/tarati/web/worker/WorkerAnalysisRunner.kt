@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

/**
 * Envía un mensaje (string JSON) del hilo principal al worker. Se pasa [JsString] explícito
 * (no `String` de Kotlin) para no depender de la conversión automática en el cruce — el
 * sospechoso del fallo `postMessage: Overload resolution failed` con payloads grandes.
 */
@JsFun("(worker, msg) => { worker.postMessage(msg); }")
private external fun workerPost(worker: JsAny, msg: JsString)

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

/** Instala el handler de mensaje ilegible (structured clone fallido) del worker. */
@JsFun("(worker, cb) => { worker.onmessageerror = () => cb(); }")
private external fun workerSetOnMessageError(worker: JsAny, cb: () -> Unit)

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
 * error de runtime, o deja de responder), cae de forma transparente a [fallback] (análisis
 * en el hilo principal) — la feature funciona igual, solo sin el offload. La detección de
 * "worker colgado" **no depende** de que el worker pueda comunicarse: un watchdog de
 * inactividad en el hilo principal lo cubre aunque su propio canal `postMessage` esté roto
 * (que es justamente el modo de fallo observado).
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
        // Refrescado por cada reply del worker; el watchdog lo consume para detectar inactividad.
        var sawActivity: Boolean = true,
    )

    init {
        val w = worker
        if (w != null) {
            workerSetOnMessage(w) { data -> onReply(data.toString()) }
            workerSetOnError(w) { detail ->
                consoleWarn("[analysis] error del worker → fallback al hilo principal :: $detail")
                markBroken()
            }
            workerSetOnMessageError(w) {
                consoleWarn("[analysis] mensaje ilegible del worker → fallback al hilo principal")
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
        val entry = Pending(onProgress, deferred)
        pending[id] = entry
        workerPost(w, workerJson.encodeToString(AnalysisJob(id, initialBoardPosition, moves)).toJsString())
        return try {
            coroutineScope {
                // Watchdog de inactividad: el análisis emite progreso por-ply de forma
                // frecuente, así que un worker vivo refresca `sawActivity` mucho antes del
                // vencimiento. Si no llega ninguna señal en toda la ventana, el worker está
                // colgado (p. ej. su `postMessage` reventó) → se lo da por muerto.
                val watchdog = launch {
                    while (isActive) {
                        entry.sawActivity = false
                        delay(WATCHDOG_TIMEOUT_MS.milliseconds)
                        if (!entry.sawActivity) {
                            consoleWarn("[analysis] el worker dejó de responder → fallback al hilo principal")
                            markBroken()
                            break
                        }
                    }
                }
                try {
                    deferred.await()
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (e: CancellationException) {
            pending.remove(id)
            throw e
        } catch (_: Throwable) {
            // El worker falló (o se colgó) → reintenta en el hilo principal para no dejar al
            // usuario sin análisis. No marca el worker roto: eso lo decide `markBroken` solo
            // ante fallos del worker en sí (watchdog / onerror), no ante un trabajo puntual.
            consoleWarn("[analysis] el worker falló → análisis en el hilo principal (fallback)")
            pending.remove(id)
            fallback.run(initialBoardPosition, moves, onProgress)
        }
    }

    private fun onReply(text: String) {
        val reply = runCatching { workerJson.decodeFromString<WorkerReply>(text) }.getOrNull() ?: return
        val entry = pending[reply.id] ?: return
        entry.sawActivity = true
        when (reply.kind) {
            "progress" -> entry.onProgress(reply.progress)
            "result" -> {
                pending.remove(reply.id)
                reply.result?.let { entry.deferred.complete(it) }
                    ?: entry.deferred.completeExceptionally(WorkerFailure())
            }
            // El worker atrapó una excepción del análisis: cae al fallback de ESTE trabajo,
            // sin marcar el worker roto (sigue sirviendo otras partidas).
            "error" -> {
                pending.remove(reply.id)
                entry.deferred.completeExceptionally(WorkerFailure())
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

        /** Ventana sin señales del worker tras la cual se lo considera colgado. */
        const val WATCHDOG_TIMEOUT_MS = 15_000L
    }
}
