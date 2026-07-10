package com.agustin.tarati.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La marca de distinción de George Spencer-Brown (*Laws of Form*): dos marcas anidadas
 * (borde superior + borde derecho, repetido hacia adentro).
 *
 * Se dibuja como vector (4 segmentos con cap cuadrado) en lugar de un raster, así escala
 * sin pérdida en todas las plataformas (Android/Desktop/WASM) y se tiñe según el tema.
 * Derivado de `screenshots/distinct-logo.svg` (viewBox cuadrado 73.487; el offset 3.9688 =
 * medio stroke, de modo que los caps cuadrados tocan exactamente el borde del lienzo).
 */
@Composable
fun DistinctLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(size)) {
        val k = this.size.minDimension / VIEWBOX
        val stroke = STROKE_WIDTH * k
        fun p(x: Float, y: Float) = Offset(x * k, y * k)

        // Marca exterior: borde superior + borde derecho.
        drawLine(color, p(3.9688f, 3.9688f), p(69.5183f, 3.9688f), stroke, StrokeCap.Square)
        drawLine(color, p(69.5183f, 3.9688f), p(69.5183f, 69.4737f), stroke, StrokeCap.Square)
        // Marca interior (anidada).
        drawLine(color, p(3.9688f, 20.1276f), p(53.2847f, 20.1276f), stroke, StrokeCap.Square)
        drawLine(color, p(53.2847f, 20.1276f), p(53.2847f, 69.4436f), stroke, StrokeCap.Square)
    }
}

private const val VIEWBOX = 73.48709f
private const val STROKE_WIDTH = 7.9375f
