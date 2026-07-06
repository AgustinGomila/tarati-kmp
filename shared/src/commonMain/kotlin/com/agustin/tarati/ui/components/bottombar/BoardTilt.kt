package com.agustin.tarati.ui.components.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

/**
 * "Cabeceo" inercial del tablero, compartido por el juego single y el multijugador. Se lee dentro de un
 * `graphicsLayer { }` (así la lectura de los valores animados ocurre en la fase de dibujo, sin
 * recomposición por frame):
 *
 * ```
 * Modifier.graphicsLayer {
 *     rotationX = tilt.rotationX
 *     rotationY = tilt.rotationY
 *     cameraDistance = 12f * density   // perspectiva sutil
 * }
 * ```
 */
@Stable
class BoardTilt internal constructor(
    private val tiltX: Animatable<Float, *>,
    private val tiltY: Animatable<Float, *>,
) {
    /** Inclinación vertical (al abrir/cerrar el panel de historial). */
    val rotationX: Float get() = tiltX.value

    /** Inclinación horizontal (al expandir/contraer el FAB). */
    val rotationY: Float get() = tiltY.value
}

/**
 * Anima el cabeceo del tablero: un **kick** corto (80 ms) + **rebote elástico** de vuelta a 0° cada vez
 * que cambia [isFabExpanded] (hunde el lado derecho, `rotationY`) o [isHistoryPanelOpen] (hunde la parte
 * inferior, `rotationX`). El guard de primera composición evita disparar la animación al entrar.
 */
@Composable
fun rememberBoardTilt(
    isFabExpanded: Boolean,
    isHistoryPanelOpen: Boolean,
): BoardTilt {
    val boardTiltY = remember { Animatable(0f) }
    val boardTiltX = remember { Animatable(0f) }
    var fabFirstRender by remember { mutableStateOf(true) }
    var historyFirstRender by remember { mutableStateOf(true) }

    val returnSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    // FAB: al abrir se hunde el lado derecho (rotationY positivo), rebote al lado izquierdo al cerrar.
    LaunchedEffect(isFabExpanded) {
        if (fabFirstRender) {
            fabFirstRender = false; return@LaunchedEffect
        }
        val kick = if (isFabExpanded) 6f else -4f
        boardTiltY.animateTo(kick, tween(durationMillis = 80))
        boardTiltY.animateTo(0f, returnSpec)
    }

    // Historial: al abrir se hunde la parte inferior (rotationX negativo), rebote hacia arriba al cerrar.
    LaunchedEffect(isHistoryPanelOpen) {
        if (historyFirstRender) {
            historyFirstRender = false; return@LaunchedEffect
        }
        val kick = if (isHistoryPanelOpen) -8f else 5f
        boardTiltX.animateTo(kick, tween(durationMillis = 80))
        boardTiltX.animateTo(0f, returnSpec)
    }

    return remember { BoardTilt(boardTiltX, boardTiltY) }
}
