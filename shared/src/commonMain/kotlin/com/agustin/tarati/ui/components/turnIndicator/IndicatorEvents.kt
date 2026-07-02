package com.agustin.tarati.ui.components.turnIndicator

import androidx.compose.runtime.Stable

/**
 * Callback holder del indicador de turno. Anotado [@Stable]: solo declara una
 * función (sin propiedades), por lo que la promesa de estabilidad es vacuamente
 * cierta. Permite que los composables que lo reciben como parámetro
 * ([TurnIndicator]) salte recomposición — siempre que el caller pase una
 * instancia estable (`remember`).
 */
@Stable
interface IndicatorEvents {
    fun onTouch()
}