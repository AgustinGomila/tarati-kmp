@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.web.worker.EngineWorkerClient.COOLDOWN
import com.agustin.tarati.web.worker.EngineWorkerClient.LIVENESS_MAX_UNANSWERED
import com.agustin.tarati.web.worker.EngineWorkerClient.MAX_CONSECUTIVE_FAILURES
import com.agustin.tarati.web.worker.EngineWorkerClient.WATCHDOG_POLL_MS
import com.agustin.tarati.web.worker.EngineWorkerClient.awaitForeground
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
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

/** Termina el worker (libera su instancia WASM ~18 MB + event-loop). No falla si ya estaba muerto. */
@JsFun("(worker) => { try { worker.terminate(); } catch(_) {} }")
private external fun workerTerminate(worker: JsAny)

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

    /**
     * Suspende hasta que la pestaña vuelva a primer plano; retorna de inmediato si ya está visible.
     *
     * En segundo plano el navegador estrangula timers y worker (clamp de `setTimeout` a ≥1 s): seguir
     * disparando búsquedas de IA martilla un hilo principal throttled y puede dejar el worker sin
     * recuperación (el watchdog no logra recrearlo mientras los ticks llegan tarde). Los runners
     * esperan aquí antes de calcular, así la IA se pausa al ocultar la pestaña y se reanuda al volver.
     * Es cancelable: `AIViewModel.cancelThinking` (partida nueva) corta la espera.
     */
    suspend fun awaitForeground() {
        if (!hidden.value) return
        hidden.first { !it }
    }

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

    /**
     * Circuit-breaker. Un worker que falla se recrea al vuelo, pero si sigue fallando (p. ej. presión
     * de memoria de WASM, cuya memoria lineal solo crece) la recreación por-jugada degenera en un
     * storm. Tras [MAX_CONSECUTIVE_FAILURES] fallos seguidos se abre el breaker: durante [COOLDOWN]
     * los runners van directo al fallback (sin recrear ni esperar el watchdog). Pasado el cooldown se
     * hace **una** prueba (medio-abierto): si responde, se cierra; si vuelve a fallar, reabre. Un reply
     * terminal exitoso resetea el conteo.
     */
    private var consecutiveFailures = 0
    private var cooldownMark: TimeMark? = null
    private var halfOpen = false

    /**
     * Marca del último reply del worker (de CUALQUIER job: progreso o terminal). Sirve para no matar
     * un worker sano: si un job se queda sin respuesta pero el worker contestó otros hace poco, el
     * problema es de ese job (un análisis abandonado que quedó pendiente, un search patológico), no del
     * worker → se falla solo ese trabajo (fallback) en vez de terminar el worker y tumbar los demás.
     */
    private var lastWorkerReplyMark: TimeMark? = null

    /**
     * Envíos consecutivos sin ninguna respuesta del worker. Se resetea con cualquier reply. Al superar
     * [LIVENESS_MAX_UNANSWERED] el worker se presume muerto y se recrea en el próximo [submit] —
     * recuperación **independiente del watchdog** per-job (que necesita el hilo principal ocioso con
     * ticks puntuales, condición que un IA-vs-IA caído al fallback nunca cumple).
     */
    private var submitsSinceReply = 0

    /** `true` mientras la pestaña está oculta; lo consume [awaitForeground] para no calcular en segundo plano. */
    private val hidden = MutableStateFlow(isDocumentHidden())

    private class Pending(
        val onProgress: (Float) -> Unit,
        val deferred: CompletableDeferred<WorkerReply>,
        // Momento de envío; un job pendiente por más de [ZOMBIE_MAX_WALL] es un zombie (nunca lo recibió
        // el worker), no un cuelgue reciente → jamás debe tumbar el worker.
        val submitMark: TimeMark = TimeSource.Monotonic.markNow(),
        // Refrescado por cada reply del worker; el watchdog lo consume para detectar inactividad.
        var sawActivity: Boolean = true,
    )

    init {
        // Segundo plano: el navegador estrangula el worker y los timers; recordar el backgrounding para
        // que el watchdog no confunda ese silencio con un cuelgue real. Se registra una sola vez,
        // independiente del ciclo de vida del worker.
        onVisibilityChange {
            val isHidden = isDocumentHidden()
            hidden.value = isHidden
            if (isHidden) backgroundedRecently = true
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
        // Circuit-breaker: si el worker viene fallando en cadena, no lo recreamos en cada jugada
        // (evita el storm). Durante el cooldown vamos directo al fallback; al vencer, una única prueba.
        cooldownMark?.let { mark ->
            if (mark.elapsedNow() < COOLDOWN) throw WorkerUnavailable()
            cooldownMark = null
            halfOpen = true
        }

        // Liveness (recuperación independiente del watchdog): si el worker existe y no está marcado
        // roto pero acumuló demasiados envíos sin ninguna respuesta, se lo presume muerto/colgado y se
        // fuerza su recreación. El watchdog per-job no basta: necesita el hilo principal ocioso con
        // ticks puntuales, y un IA-vs-IA caído al fallback lo satura sin descanso.
        if (worker != null && !broken && submitsSinceReply >= LIVENESS_MAX_UNANSWERED) {
            consoleWarn("[engine-worker] worker mudo ($submitsSinceReply envíos sin respuesta) → recreación por liveness")
            markBroken()
        }

        // ensureWorker recrea el worker si murió en un fallo previo (recuperable); solo devuelve null
        // si el entorno no soporta workers → fallback definitivo.
        val w = ensureWorker() ?: throw WorkerUnavailable()
        val id = nextId++
        val deferred = CompletableDeferred<WorkerReply>()
        val entry = Pending(onProgress, deferred)
        pending[id] = entry
        submitsSinceReply++
        workerPost(w, workerJson.encodeToString(buildJob(id)).toJsString())
        return try {
            coroutineScope {
                val watchdog = launch {
                    // Solo se declara muerto al worker tras acumular una ventana **contigua** de silencio
                    // con el hilo principal demostrablemente ocioso. Cualquier señal de que el main estuvo
                    // ocupado —tick que llega tarde (bloqueo o saturación de render), segundo plano, o una
                    // respuesta del worker— reinicia el acumulador: mientras el hilo principal trabaja, el
                    // silencio del worker no es informativo (su `onmessage` corre en ese mismo hilo), así que
                    // matarlo sería un falso positivo. En cambio, con el main ocioso los ticks llegan
                    // puntuales y el silencio real se acumula hasta el corte.
                    var silentHealthyMs = 0L
                    while (isActive) {
                        val mark = TimeSource.Monotonic.markNow()
                        delay(WATCHDOG_POLL_MS.milliseconds)
                        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

                        // El worker respondió (progreso o resultado) → está vivo, reinicia el conteo.
                        if (entry.sawActivity) {
                            entry.sawActivity = false
                            silentHealthyMs = 0
                            continue
                        }
                        // Segundo plano: el navegador estrangula worker/timers; el silencio no cuenta.
                        if (isDocumentHidden() || backgroundedRecently) {
                            backgroundedRecently = false
                            silentHealthyMs = 0
                            continue
                        }
                        // Tick tardío ⇒ el hilo principal estuvo ocupado (bloqueo o saturación de
                        // animación/GC): durante ese lapso su pump de mensajes estuvo hambriento, así que el
                        // silencio del worker es inconcluso → intervalo descartado.
                        if (elapsedMs > WATCHDOG_POLL_MS + WATCHDOG_DRIFT_MARGIN_MS) {
                            silentHealthyMs = 0
                            continue
                        }
                        // Tick puntual y sin actividad: silencio genuino con el main ocioso. Se acumula; al
                        // superar la ventana el worker está realmente colgado → fallback + recreación en el
                        // próximo submit.
                        silentHealthyMs += elapsedMs
                        if (silentHealthyMs >= inactivityTimeoutMs) {
                            val workerAlive =
                                lastWorkerReplyMark?.let { it.elapsedNow() < inactivityTimeoutMs.milliseconds } == true
                            // Zombie de arranque en frío: solo es benigno si el worker NUNCA contestó
                            // (lastWorkerReplyMark == null) y el job es muy viejo → es el warmUp #0 pagando
                            // la carga del bundle + compilación WASM. Si el worker YA contestó antes y ahora
                            // está mudo, un job viejo es evidencia de MUERTE, no de cold-start → recrear.
                            val coldStartZombie = lastWorkerReplyMark == null &&
                                    entry.submitMark.elapsedNow() > ZOMBIE_MAX_WALL
                            if (workerAlive || coldStartZombie) {
                                // El worker sigue vivo (contestó otros jobs) o es el warmUp en frío: falla
                                // solo ESTE trabajo (fallback) sin tumbar el worker ni los demás.
                                consoleWarn(
                                    "[engine-worker] job sin respuesta " +
                                            "(${if (coldStartZombie) "cold-start" else "worker vivo"}) → fallback de ese job",
                                )
                                pending.remove(id)
                                deferred.completeExceptionally(WorkerFailure())
                                break
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
        // Cualquier reply prueba que el worker está vivo (aunque sea de otro job).
        lastWorkerReplyMark = TimeSource.Monotonic.markNow()
        submitsSinceReply = 0
        val entry = pending[reply.id] ?: return
        entry.sawActivity = true
        when (reply.kind) {
            WorkerReplyKind.PROGRESS -> entry.onProgress(reply.progress)
            WorkerReplyKind.RESULT, WorkerReplyKind.BEST_MOVE, WorkerReplyKind.MP_BEST_MOVE -> {
                pending.remove(reply.id)
                // El worker respondió un resultado → sano: cierra el breaker.
                consecutiveFailures = 0
                cooldownMark = null
                halfOpen = false
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
        // Idempotente: watchdog y onerror/onmessageerror pueden dispararse para el mismo fallo.
        if (broken) return
        broken = true
        // El worker recreado arranca su propio conteo de liveness.
        submitsSinceReply = 0
        // Termina el worker colgado/muerto: libera su instancia WASM y evita workers huérfanos vivos
        // (que se acumulaban en cada recreación, compitiendo por CPU/memoria y agravando el fallo).
        worker?.let { runCatching { workerTerminate(it) } }
        consecutiveFailures++
        // Abre el breaker si acumuló fallos, o si ya venía medio-abierto y la prueba también falló.
        if (halfOpen || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            cooldownMark = TimeSource.Monotonic.markNow()
            halfOpen = false
        }
        val entries = pending.values.toList()
        pending.clear()
        entries.forEach { it.deferred.completeExceptionally(WorkerFailure()) }
    }

    private const val WORKER_URL = "tarati.js"

    /** Fallos seguidos del worker tras los que se abre el breaker (los runners van al fallback). */
    private const val MAX_CONSECUTIVE_FAILURES = 3

    /** Ventana en que el breaker abierto no toca el worker; al vencer hace una única prueba. */
    private val COOLDOWN = 60.seconds

    /**
     * Antigüedad tras la cual un job pendiente se considera **zombie**. Solo es benigno en el arranque
     * en frío (el worker nunca contestó): es el warmUp #0 pagando la carga del bundle + compilación
     * WASM. Si el worker ya contestó antes, un zombie es evidencia de muerte y dispara la recreación.
     */
    private val ZOMBIE_MAX_WALL = 45.seconds

    /**
     * Envíos consecutivos sin respuesta tras los que el worker se presume muerto y se recrea en el
     * próximo [submit] (recuperación independiente del watchdog). En operación sana cada envío recibe
     * su reply y el conteo vuelve a 0, así que solo trepa cuando el worker deja de contestar de veras;
     * dos turnos sin respuesta bastan para recrearlo.
     */
    private const val LIVENESS_MAX_UNANSWERED = 2

    /** Ventana por defecto sin señales del worker tras la cual se lo considera colgado. */
    private const val WATCHDOG_TIMEOUT_MS = 15_000L

    /** Ventana amplia para el calentamiento: incluye la carga del bundle + compilación WASM. */
    private const val WARMUP_TIMEOUT_MS = 60_000L

    /**
     * Cadencia del sondeo del watchdog. Fino (sub-segundo) para muestrear seguido la salud del hilo
     * principal: cuanto más corto, antes se detecta un tick tardío (main ocupado) y se reinicia el
     * acumulador, evitando falsos positivos durante animaciones/render.
     */
    private const val WATCHDOG_POLL_MS = 500L

    /**
     * Margen sobre [WATCHDOG_POLL_MS] a partir del cual un tick se considera **tardío** → el hilo
     * principal estuvo ocupado y el silencio del worker es inconcluso. Bajo, para que el jitter de una
     * app animando (frames pesados, GC, transiciones) reinicie el conteo; pero por encima del jitter
     * normal de timers en reposo, para no descartar el silencio genuino con el main ocioso.
     */
    private const val WATCHDOG_DRIFT_MARGIN_MS = 150L
}
