package com.agustin.tarati.core.domain.tutorial

/**
 * Cursor sobre los pasos de un recorrido de tutorial: mantiene la lista de pasos y el índice actual,
 * y ofrece navegación **acotada** + el [progress] 1-based.
 *
 * Núcleo **compartido** por el tutorial de Tarati single ([TutorialManager]) y el multijugador
 * (`MpTutorialManager`): ambos recorren una lista lineal de pasos con la misma aritmética de índice
 * (avanzar/retroceder con topes, progreso 1-based). Centralizar esa aritmética evita duplicar la
 * lógica de bordes — donde suelen aparecer los off-by-one — y deja a cada manager solo su parte
 * divergente (emisión de estado, animaciones, auto-avance).
 *
 * No es thread-safe ni observable: el estado observable (paso actual, WaitingForMove, etc.) lo emite
 * cada manager desde su propio `StateFlow`, derivándolo de este cursor.
 */
class StepCursor<S> {
    private var steps: List<S> = emptyList()
    private var index: Int = 0

    /** El paso actual, o `null` si el recorrido no está cargado (lista vacía). */
    val current: S? get() = steps.getOrNull(index)

    /** Progreso 1-based del recorrido (para la barra y el contador de la burbuja). */
    val progress: TutorialProgress
        get() = TutorialProgress(currentStepIndex = index + 1, totalSteps = steps.size)

    /** `true` si el índice está en el primer paso (o no hay pasos). */
    val atFirst: Boolean get() = index <= 0

    /** `true` si el índice está en el último paso (o no hay pasos). */
    val atLast: Boolean get() = index >= steps.size - 1

    /** Carga [newSteps] y posiciona el cursor en el primero. */
    fun load(newSteps: List<S>) {
        steps = newSteps
        index = 0
    }

    /** Avanza un paso. Devuelve `false` (sin moverse) si ya estaba en el último. */
    fun advance(): Boolean =
        if (index < steps.size - 1) {
            index++
            true
        } else {
            false
        }

    /** Retrocede un paso. Devuelve `false` (sin moverse) si ya estaba en el primero. */
    fun back(): Boolean =
        if (index > 0) {
            index--
            true
        } else {
            false
        }

    /** Descarta los pasos y vuelve al inicio. */
    fun reset() {
        steps = emptyList()
        index = 0
    }
}
