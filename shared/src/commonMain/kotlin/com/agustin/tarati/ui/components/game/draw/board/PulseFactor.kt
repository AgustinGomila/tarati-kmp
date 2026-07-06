package com.agustin.tarati.ui.components.game.draw.board

import kotlin.math.PI
import kotlin.math.sin

/**
 * Factor de pulso periódico en el rango `[base, base + amp]` derivado del tiempo (ciclo de 1 s).
 *
 * Compartido por los resaltados de Tarati (single) y del juego multijugador para no duplicar la
 * fórmula `base + amp·sin(t·2π)`. Los llamadores pasan el tiempo en milisegundos (típicamente
 * `Clock.System.now().toEpochMilliseconds()` o un tick de animación).
 */
fun pulseFactor(timeMs: Long, base: Float = 0.7f, amp: Float = 0.3f): Float =
    base + amp * sin((timeMs % 1000L) / 1000f * 2f * PI).toFloat()
