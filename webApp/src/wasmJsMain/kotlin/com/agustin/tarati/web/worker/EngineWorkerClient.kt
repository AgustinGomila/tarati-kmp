@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.web.worker.EngineWorkerClient.broken
import com.agustin.tarati.web.worker.EngineWorkerClient.ensureWorker
import com.agustin.tarati.web.worker.EngineWorkerClient.markBroken
import com.agustin.tarati.web.worker.EngineWorkerClient.permanentlyUnavailable
import com.agustin.tarati.web.worker.EngineWorkerClient.submit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

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

/** `true` si la pestaña está oculta (segundo plano). En ese estado el navegador estrangula worker y timers. */
@JsFun("() => (typeof document !== 'undefined' && document.hidden === true)")
private external fun isDocumentHidden(): Boolean

/** Registra un listener de `visibilitychange`; [cb] se invoca en cada transición de visibilidad. */
@JsFun("(cb) => { if (typeof document !== 'undefined') document.addEventListener('visibilitychange', () => cb()); }")
private external fun onVisibilityChange(cb: () -> Unit)

/** El worker está roto/ausente: el runner debe caer directo al fallback sin intentar enviar. */
internal class WorkerUnavailable : RuntimeException("Engine worker unavailable")

/** Un trabajo puntual falló o el worker se colgó: el runner reintenta en el hilo principal. */
internal class WorkerFailure : RuntimeException("Engine worker failed")

/**
 * Dueño del **único** Web Worker del motor, compartido por todos los runners (análisis + IA en
 * vivo). Reusa el mismo bundle (`new Worker('tarati.js')`): `main()` detecta el contexto worker y
 * arranca [startEngineWorker]. Un solo worker = un solo bootstrap/shim/fallback y el bundle ya
 * cargado; los trabajos son secuenciales (la IA piensa una jugada por vez; el análisis, una
 * partida por vez), ruteados por id.
 *
 * **Robustez** (heredada del análisis): si el worker no se puede crear o falla (el bundle no carga
 * en un worker, error de runtime, o deja de responder), los runners caen de forma transparente al
 * hilo principal. La detección de "worker colgado" **no depende** de que el worker pueda
 * comunicarse: un watchdog de inactividad en el hilo principal lo cubre aunque su propio canal
 * `postMessage` esté roto (el modo de fallo observado en el análisis).
 */
internal object EngineWorkerClient {

    private var worker: JsAny? = null
    private var broken: Boolean = false

    /**
     * Se vuelve `true` de forma **permanente** solo si la creación del worker falla (Worker/Blob/URL no
     * soportados): ahí los runners caen siempre al fallback. Una **muerte** posterior del worker (wedge,
     * suspensión agresiva en segundo plano) NO lo apaga: se marca [broken] y el próximo [submit] lo
     * **recrea** vía [ensureWorker].
     */
    private var permanentlyUnavailable = false

    /** `true` si el motor de workers es utilizable (aunque haya que recrear el worker); guía el fallback. */
    val available: Boolean get() = !permanentlyUnavailable

    private var nextId = 0
    private val pending = mutableMapOf<Int, Pending>()

    /** Scope propio para el calentamiento fire-and-forget (no atado a ninguna corrutina de UI). */
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var warmed = false

    /**
     * Se pone en `true` en cuanto la pestaña pasa a segundo plano y se consume en el watchdog. Marca
     * que hubo backgrounding durante la ventana de espera: el estrangulamiento de worker/timers en
     * segundo plano no es un cuelgue real, así que el watchdog le perdona esa ventana en vez de matar
     * el worker. Se setea al **ocultarse** (siempre antes de volver a primer plano), evitando la
     * carrera con el disparo del watchdog al reactivarse los timers.
     */
    private var backgroundedRecently = false

    private class Pending(
        val onProgress: (Float) -> Unit,
        val deferred: CompletableDeferred<WorkerReply>,
        // Refrescado por cada reply del worker; el watchdog lo consume para detectar inactividad.
        var sawActivity: Boolean = true,
    )

    init {
        // Segundo plano: el navegador estrangula el worker y los timers; recordar el backgrounding para
        // que el watchdog no confunda ese silencio con un cuelgue real. Se registra una sola vez,
        // independiente del ciclo de vida del worker.
        onVisibilityChange {
            if (isDocumentHidden()) backgroundedRecently = true
        }
        // Creación temprana (mismo comportamiento que antes: el worker existe apenas se toca el objeto).
        ensureWorker()
    }

    /**
     * Devuelve un worker vivo, (re)creándolo si hace falta. Devuelve `null` solo si el entorno no
     * soporta workers ([permanentlyUnavailable]), en cuyo caso el runner cae al fallback. **Recupera**
     * de un [markBroken] previo: tras una muerte del worker (p. ej. suspensión agresiva en segundo
     * plano) la próxima llamada crea uno nuevo, lo cablea y limpia [broken]. La (re)creación es
     * síncrona; en el modelo mono-hilo de wasm no hay carrera entre `submit`s concurrentes.
     */
    private fun ensureWorker(): JsAny? {
        if (permanentlyUnavailable) return null
        val current = worker
        if (current != null && !broken) return current

        val fresh = runCatching { createWorker(WORKER_URL) }.getOrNull()
        if (fresh == null) {
            permanentlyUnavailable = true
            consoleWarn("[engine-worker] Worker no disponible → cómputo en el hilo principal (fallback)")
            return null
        }
        wireWorker(fresh)
        worker = fresh
        broken = false
        if (current != null) {
            // El worker nuevo arranca frío (recarga del bundle + compilación WASM); el primer job tras
            // la recreación paga ese cold-start, aceptable por lo raro del caso.
            consoleWarn("[engine-worker] worker recreado tras un fallo previo")
        }
        return fresh
    }

    /** Cablea los handlers de un worker recién creado (respuestas, error, mensaje ilegible). */
    private fun wireWorker(w: JsAny) {
        workerSetOnMessage(w) { data -> onReply(data.toString()) }
        workerSetOnError(w) { detail ->
            consoleWarn("[engine-worker] error del worker → fallback al hilo principal :: $detail")
            markBroken()
        }
        workerSetOnMessageError(w) {
            consoleWarn("[engine-worker] mensaje ilegible del worker → fallback al hilo principal")
            markBroken()
        }
    }

    /**
     * Pre-crea el worker y lo deja **listo** antes de que se necesite: dispara la carga del bundle +
     * compilación WASM en el worker y corre un search barato (EASY sobre la posición inicial) para
     * calentar el motor. Así la **primera jugada real** no paga el cold-start (~segundos), que de otro
     * modo aparece como una demora notable en el primer movimiento de una partida vs IA / IA-vs-IA.
     *
     * Idempotente y fire-and-forget; los errores se ignoran (si el worker no está disponible, los
     * runners ya caen al fallback del hilo principal). Pensado para llamarse al arrancar la app,
     * diferido para no competir con el propio arranque. El search de calentamiento deja el motor del
     * worker configurado en EASY con historial vacío; el primer job real fija su propia config/historial.
     */
    fun warmUp() {
        if (warmed || !available) return
        warmed = true
        warmScope.launch {
            runCatching {
                submit(
                    buildJob = { id ->
                        EngineJob(
                            id = id,
                            kind = EngineJobKind.BEST_MOVE,
                            gameState = initialGameState(),
                            difficulty = Difficulty.EASY,
                            positionHistory = emptyMap(),
                        )
                    },
                    inactivityTimeoutMs = WARMUP_TIMEOUT_MS,
                )
            }
        }
    }

    /**
     * Envía el job construido por [buildJob] (que recibe el id asignado) y suspende hasta su reply
     * terminal, propagando cada "progress" a [onProgress]. Los mensajes se entregan en el
     * event-loop del hilo principal, así que [onProgress] puede tocar estado Compose sin marshaling.
     *
     * [inactivityTimeoutMs] es la ventana sin señales del worker tras la cual se lo da por colgado
     * (el análisis emite progreso frecuente; la IA no emite progreso, así que su ventana es mayor
     * que su límite de tiempo de búsqueda).
     *
     * @throws WorkerUnavailable si el worker está roto/ausente (el runner cae al fallback).
     * @throws WorkerFailure si el worker falla o se cuelga durante este trabajo.
     */
    suspend fun submit(
        buildJob: (Int) -> EngineJob,
        onProgress: (Float) -> Unit = {},
        inactivityTimeoutMs: Long = WATCHDOG_TIMEOUT_MS,
    ): WorkerReply {
        // ensureWorker recrea el worker si murió en un fallo previo (recuperable); solo devuelve null
        // si el entorno no soporta workers → fallback definitivo.
        val w = ensureWorker() ?: throw WorkerUnavailable()
        val id = nextId++
        val deferred = CompletableDeferred<WorkerReply>()
        val entry = Pending(onProgress, deferred)
        pending[id] = entry
        workerPost(w, workerJson.encodeToString(buildJob(id)).toJsString())
        return try {
            coroutineScope {
                var forgivenStalls = 0
                val watchdog = launch {
                    while (isActive) {
                        entry.sawActivity = false
                        val mark = TimeSource.Monotonic.markNow()
                        delay(inactivityTimeoutMs.milliseconds)
                        if (!entry.sawActivity) {
                            // Segundo plano: el navegador estrangula worker/timers y el worker retoma al
                            // volver a foreground → se perdona **indefinidamente** (no es un cuelgue).
                            if (isDocumentHidden() || backgroundedRecently) {
                                backgroundedRecently = false
                                continue
                            }
                            // Stall del event-loop en foreground (animaciones/GC): un `delay` sano se
                            // entrega casi puntual, así que una ventana real mucho mayor que la pedida
                            // indica que el hilo principal no pudo procesar el reply. Se perdona, pero
                            // **acotado**: si persiste, lo más probable es que el worker haya muerto (p. ej.
                            // recarga de HMR en dev, descarte de pestaña) y hay que recrearlo, no colgar el
                            // job indefinidamente.
                            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                            val stalled = elapsedMs > inactivityTimeoutMs + STALL_MARGIN_MS
                            if (stalled && forgivenStalls < MAX_STALL_FORGIVES) {
                                forgivenStalls++
                                continue
                            }
                            consoleWarn("[engine-worker] el worker dejó de responder → fallback al hilo principal")
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
        }
    }

    private fun onReply(text: String) {
        val reply = runCatching { workerJson.decodeFromString<WorkerReply>(text) }.getOrNull() ?: return
        val entry = pending[reply.id] ?: return
        entry.sawActivity = true
        when (reply.kind) {
            WorkerReplyKind.PROGRESS -> entry.onProgress(reply.progress)
            WorkerReplyKind.RESULT, WorkerReplyKind.BEST_MOVE, WorkerReplyKind.MP_BEST_MOVE -> {
                pending.remove(reply.id)
                entry.deferred.complete(reply)
            }
            // El worker atrapó una excepción del cómputo: cae al fallback de ESTE trabajo,
            // sin marcar el worker roto (sigue sirviendo otros).
            WorkerReplyKind.ERROR -> {
                pending.remove(reply.id)
                entry.deferred.completeExceptionally(WorkerFailure())
            }
        }
    }

    /**
     * Marca el worker actual como muerto y falla los trabajos en curso (caerán al fallback de ESTE
     * job). **No es permanente**: el próximo [submit] lo recrea vía [ensureWorker]. Solo
     * [permanentlyUnavailable] (creación imposible) apaga el worker de forma definitiva.
     */
    private fun markBroken() {
        broken = true
        val entries = pending.values.toList()
        pending.clear()
        entries.forEach { it.deferred.completeExceptionally(WorkerFailure()) }
    }

    private const val WORKER_URL = "tarati.js"

    /** Ventana por defecto sin señales del worker tras la cual se lo considera colgado. */
    private const val WATCHDOG_TIMEOUT_MS = 15_000L

    /** Ventana amplia para el calentamiento: incluye la carga del bundle + compilación WASM. */
    private const val WARMUP_TIMEOUT_MS = 60_000L

    /**
     * Margen sobre la ventana pedida a partir del cual se considera que el event-loop del hilo
     * principal estuvo bloqueado (no el worker): un `delay` sano se entrega casi puntual, así que un
     * exceso de segundos indica un stall del main, no un cuelgue del worker.
     */
    private const val STALL_MARGIN_MS = 2_000L

    /**
     * Cuántas ventanas seguidas de stall en foreground se perdonan antes de dar el worker por muerto.
     * Acota el peor caso ante un worker realmente caído bajo stalls repetidos (p. ej. recargas de HMR
     * en dev): sin la cota, el watchdog re-armaría indefinidamente y el job quedaría colgado.
     */
    private const val MAX_STALL_FORGIVES = 2
}
