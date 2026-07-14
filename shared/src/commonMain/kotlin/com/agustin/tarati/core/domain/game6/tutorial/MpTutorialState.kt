package com.agustin.tarati.core.domain.game6.tutorial

/**
 * Estado del tutorial del juego multijugador (tablero `25`). Paralelo al `TutorialState` del juego
 * single, pero sobre el dominio `game6` (posiciones `MpGameState`, movimientos `MpMove`).
 *
 * A diferencia del tutorial single, no orquesta un `AnimationCoordinator`: los pasos guiados
 * resaltan vértices reutilizando los cues del tablero (`selection`/`legalTargets`/`converted`) que
 * ya dibuja `Board25View`.
 */
sealed interface MpTutorialState {
    /** Tutorial inactivo (cerrado o nunca iniciado). */
    data object Idle : MpTutorialState

    /** Mostrando un paso guiado (no interactivo). */
    data class ShowingStep(val step: MpTutorialStep) : MpTutorialState

    /** Esperando que el usuario realice el movimiento del paso interactivo [step]. */
    data class WaitingForMove(val step: MpInteractiveTutorialStep) : MpTutorialState

    /** El usuario completó (o saltó hasta el final) el recorrido. */
    data object Completed : MpTutorialState
}

/** `true` mientras hay un paso visible (guiado o interactivo). */
val MpTutorialState.isActive: Boolean
    get() = this is MpTutorialState.ShowingStep || this is MpTutorialState.WaitingForMove

/** Progreso del recorrido, 1-based, para la barra y el contador de la burbuja. */
data class MpTutorialProgress(
    val currentStepIndex: Int,
    val totalSteps: Int,
)

/** `true` si el progreso alcanzó el último paso. */
fun MpTutorialProgress.isCompleted(): Boolean = totalSteps in 1..currentStepIndex
