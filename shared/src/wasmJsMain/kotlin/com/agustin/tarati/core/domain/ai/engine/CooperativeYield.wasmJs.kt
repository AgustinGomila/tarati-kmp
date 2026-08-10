@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.core.domain.ai.engine

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** `true` si este bundle corre dentro de un Web Worker (sin UI que proteger). */
@JsFun("() => (typeof WorkerGlobalScope !== 'undefined' && typeof self !== 'undefined' && self instanceof WorkerGlobalScope)")
private external fun inWorkerContext(): Boolean

private val isWorkerContext: Boolean by lazy { inWorkerContext() }

/**
 * Yield cooperativo worker-safe para cómputos de fondo largos (búsqueda de la IA, análisis de partidas).
 *
 * - **Hilo principal** (sin worker / fallback): `delay(1ms)` agenda un macrotask (`setTimeout`) que
 *   deja correr `requestAnimationFrame` antes de reanudar el cómputo, evitando congelar la animación.
 * - **Web Worker**: **no-op**, por dos motivos. (1) No hay animación que proteger. (2) Reprogramar la
 *   corrutina dentro del worker rompe: el dispatcher de kotlinx.coroutines asume contexto de ventana y
 *   llama `postMessage` con firma de `Window` → `postMessage: Overload resolution failed` en un
 *   `DedicatedWorkerGlobalScope`. Además `delay` usa `setTimeout`, que el navegador estrangula a ~1s+
 *   en segundo plano. Sin reprogramar, el cómputo del worker corre sincrónico, a máxima velocidad y
 *   resiste el backgrounding (el progreso se reporta por `postMessage` explícito, que sí funciona).
 */
actual suspend fun cooperativeYield() {
    if (!isWorkerContext) delay(1.milliseconds)
}
