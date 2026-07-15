package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.agustin.tarati.ui.components.game.draw.common.MorphShape
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder

/**
 * Implementación Skiko de [morphShadow] (org.jetbrains.skia) — compartida por
 * Desktop (jvm), Web (wasmJs) e iOS (native).
 *
 * Paridad con androidMain: blur gaussiano sobre el contorno exacto del polígono
 * vía [MaskFilter.makeBlur]. `blurRadius * 1.5f` replica el factor de Android;
 * `sigma = radius / 3f` es la equivalencia radio→sigma estándar (ver [drawFlipShadow]).
 */
actual fun Modifier.morphShadow(
    shape: MorphShape,
    elevation: Dp,
    color: Color,
    offsetX: Dp,
    offsetY: Dp,
): Modifier = drawBehind {
    val blurRadius = elevation.toPx() * 1.5f
    val shadowSkia = PathBuilder()
        .addPath(shape.createPath(size).asSkiaPath(), offsetX.toPx(), offsetY.toPx())
        .detach()

    drawContext.canvas.skiaCanvas.drawPath(
        shadowSkia,
        Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = MaskFilter.makeBlur(
                mode = FilterBlurMode.NORMAL,
                sigma = (blurRadius / 3f).coerceAtLeast(0.1f),
            )
        },
    )
}
