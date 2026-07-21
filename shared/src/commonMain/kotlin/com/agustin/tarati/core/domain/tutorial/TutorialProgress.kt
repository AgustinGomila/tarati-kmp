package com.agustin.tarati.core.domain.tutorial

/**
 * Progreso 1-based de un recorrido de tutorial, para la barra y el contador de la burbuja.
 *
 * Tipo **compartido** por el tutorial de Tarati single ([TutorialManager]) y el multijugador
 * (`MpTutorialManager`): ambos recorridos exponen el mismo par índice/total, así que reusan este
 * modelo en lugar de duplicarlo.
 *
 * @property currentStepIndex índice del paso actual, 1-based (1 = primer paso).
 * @property totalSteps cantidad total de pasos del recorrido (0 mientras no hay recorrido cargado).
 */
data class TutorialProgress(
    val currentStepIndex: Int,
    val totalSteps: Int,
)

/**
 * `true` si el progreso alcanzó (o superó) el último paso de un recorrido con al menos un paso.
 *
 * El chequeo `totalSteps in 1..currentStepIndex` es robusto ante el estado inicial
 * (`totalSteps == 0` → `false`) y ante un índice que llegue justo al total.
 */
fun TutorialProgress.isCompleted(): Boolean = totalSteps in 1..currentStepIndex
