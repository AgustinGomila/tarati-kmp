package com.agustin.tarati.core.domain.ai.engine

/**
 * Yield cooperativo **worker-safe** para cómputos de fondo largos que corren en `Dispatchers.Default`
 * (búsqueda de la IA y análisis de partidas).
 *
 * - **Android / Desktop / Native**: un `yield()` — checkpoint de corrutina de costo casi nulo en el
 *   hilo de fondo; el hilo de UI nunca se bloquea.
 * - **WASM**: en el **hilo principal** cede al frame de animación (macrotask). **Dentro de un Web
 *   Worker es no-op**: no hay UI que proteger y, además, reprogramar la corrutina en el worker rompe
 *   el dispatcher de kotlinx.coroutines (asume contexto de ventana → `postMessage: Overload
 *   resolution failed`). Ver el actual de WASM.
 */
expect suspend fun cooperativeYield()
