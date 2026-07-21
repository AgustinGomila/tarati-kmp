package com.agustin.tarati.core.domain.game6.tutorial

import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.tutorial.StepCursor
import com.agustin.tarati.core.domain.tutorial.TutorialProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Orquestador del tutorial multijugador (tablero `25`). Paralelo al `TutorialManager` de single pero
 * **puro y sincrónico**: solo administra el paso actual y valida los movimientos esperados. No
 * maneja animaciones ni auto-avance (los pasos avanzan con los botones de la burbuja); el estado
 * visual post-movimiento de los pasos interactivos lo resuelve la capa de UI ([MpTutorialState]).
 *
 * La navegación por la lista de pasos (índice + topes + progreso) la delega en el [StepCursor]
 * compartido con el tutorial single.
 */
class MpTutorialManager {

    private val _state = MutableStateFlow<MpTutorialState>(MpTutorialState.Idle)
    val state: StateFlow<MpTutorialState> = _state.asStateFlow()

    private val cursor = StepCursor<MpTutorialStep>()

    /** Progreso 1-based del recorrido. */
    val progress: TutorialProgress get() = cursor.progress

    /** Carga los pasos del recorrido y muestra el primero. */
    fun load() {
        cursor.load(mpTutorialSteps())
        show()
    }

    /** Avanza al siguiente paso; si ya está en el último, termina el recorrido. */
    fun next() {
        if (cursor.advance()) show() else endTutorial()
    }

    /** Retrocede un paso (no-op en el primero). */
    fun previous() {
        if (cursor.back()) show()
    }

    /** Vuelve a mostrar el paso actual (reinicia su estado visual en la UI). */
    fun repeatCurrent() {
        show()
    }

    /** El paso actual, o `null` si el recorrido no está activo. */
    fun currentStep(): MpTutorialStep? = cursor.current

    /** `true` si el paso actual espera un movimiento del usuario. */
    fun isWaitingForMove(): Boolean = _state.value is MpTutorialState.WaitingForMove

    /**
     * Valida un movimiento del usuario contra el paso interactivo actual. Devuelve `true` si el
     * movimiento es uno de los esperados (la UI aplica el resultado); `false` en cualquier otro caso.
     */
    fun onUserMove(move: MpMove): Boolean =
        (_state.value as? MpTutorialState.WaitingForMove)?.step?.isExpectedMove(move) == true

    /** Marca el recorrido como completado (último paso o salto hasta el final). */
    fun endTutorial() {
        _state.update { MpTutorialState.Completed }
    }

    /** Cierra el tutorial sin marcarlo como completado (cancelación por el usuario). */
    fun close() {
        _state.update { MpTutorialState.Idle }
    }

    /** Descarta el recorrido: cierra y limpia los pasos. */
    fun reset() {
        close()
        cursor.reset()
    }

    private fun show() {
        val step = cursor.current ?: return
        _state.update {
            when (step) {
                is MpInteractiveTutorialStep -> MpTutorialState.WaitingForMove(step)
                else -> MpTutorialState.ShowingStep(step)
            }
        }
    }
}
