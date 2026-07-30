package com.agustin.tarati.ui.components.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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

/**
 * Cabeceo inercial del tablero en las pantallas de **detalle/replay** al expandir/contraer los paneles
 * de Información (arriba) y Movimientos (abajo). Un `Animatable` acumula los kicks y luego spring-vuelve
 * a 0° (inercia), más un kick inicial de "aparición" al entrar. Solo usa `rotationX` (`rotationY` = 0).
 * Compartido por el detalle single ([com.agustin.tarati.features.detail.CreateCardBoard]) y el MP
 * ([com.agustin.tarati.features.game6.MpGameDetailScreen]).
 *
 * Se lee dentro de un `graphicsLayer { rotationX = tilt.rotationX; cameraDistance = 12f * density }`.
 */
@Composable
fun rememberPanelTilt(
    topPanelExpanded: Boolean,
    bottomPanelExpanded: Boolean,
): BoardTilt {
    val panelTiltX = remember { Animatable(0f) }
    val zeroY = remember { Animatable(0f) }
    var topFirstRender by remember { mutableStateOf(true) }
    var botFirstRender by remember { mutableStateOf(true) }

    val returnSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    // Al aparecer el tablero, "cae" levemente hacia adelante (mismo kick que un panel superior colapsando).
    LaunchedEffect(Unit) {
        panelTiltX.animateTo(8f, tween(durationMillis = 80))
        panelTiltX.animateTo(0f, returnSpec)
    }

    // Panel superior: expandir → bascula hacia atrás (−10°); colapsar → rebote hacia adelante (+8°).
    LaunchedEffect(topPanelExpanded) {
        if (topFirstRender) {
            topFirstRender = false; return@LaunchedEffect
        }
        val kick = if (topPanelExpanded) -10f else 8f
        panelTiltX.animateTo(kick, tween(durationMillis = 80))
        panelTiltX.animateTo(0f, returnSpec)
    }

    // Panel inferior: expandir → bascula hacia adelante (+10°); colapsar → rebote hacia atrás (−8°).
    LaunchedEffect(bottomPanelExpanded) {
        if (botFirstRender) {
            botFirstRender = false; return@LaunchedEffect
        }
        val kick = if (bottomPanelExpanded) 10f else -8f
        panelTiltX.animateTo(kick, tween(durationMillis = 80))
        panelTiltX.animateTo(0f, returnSpec)
    }

    return remember { BoardTilt(panelTiltX, zeroY) }
}
