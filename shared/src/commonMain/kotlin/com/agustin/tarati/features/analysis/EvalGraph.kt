package com.agustin.tarati.features.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val GraphWhite = Color(0xFFF5F5F5)
private val GraphBlack = Color(0xFF2B2B2B)

/**
 * Gráfico de evaluación de la partida (estilo ajedrez): eje X = número de punto
 * (posición inicial + una por movimiento), eje Y = probabilidad de victoria de
 * Blancas. El área blanca ocupa la parte **inferior**, debajo de la curva del win%
 * (igual que la [com.agustin.tarati.ui.components.game.EvaluationBar], "Blancas desde
 * abajo"): más win% de Blancas → la curva sube → más blanco. El resto (arriba) es
 * territorio de Negras. Una línea vertical marca [currentIndex]; tocar el gráfico
 * salta al punto más cercano vía [onSelectIndex].
 *
 * [series] son los `winProbWhite` en `[0,1]`, uno por punto. Índices de la serie:
 * `0` = posición inicial; `i+1` = posición tras el movimiento `i`.
 */
@Composable
fun EvalGraph(
    series: List<Float>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    markers: Map<Int, Color> = emptyMap(),
) {
    if (series.size < 2) return
    val lastIndex = series.lastIndex
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(lastIndex) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSelectIndex((frac * lastIndex).roundToInt())
                }
            },
    ) {
        val w = size.width
        val h = size.height
        fun xAt(i: Int) = w * i / lastIndex
        fun yAt(p: Float) = h * (1f - p.coerceIn(0f, 1f))

        // Territorio de Negras (fondo completo).
        drawRect(GraphBlack)

        // Área de Blancas: desde la curva del win% hacia ABAJO (Blancas ocupan la parte
        // inferior, igual que la EvaluationBar). Con Negras ganando (win% bajo) la curva
        // queda abajo → casi nada de blanco; con Blancas ganando, la curva sube → todo blanco.
        val whiteArea = Path().apply {
            moveTo(0f, h)
            series.forEachIndexed { i, p -> lineTo(xAt(i), yAt(p)) }
            lineTo(w, h)
            close()
        }
        drawPath(whiteArea, GraphWhite)

        // Línea media (50/50).
        drawLine(GraphBlack.copy(alpha = 0.3f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)

        // Curva del win% resaltada.
        val curve = Path().apply {
            series.forEachIndexed { i, p ->
                val x = xAt(i)
                val y = yAt(p)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(curve, lineColor, style = Stroke(width = 2f))

        // Marcadores de calidad (blunder/mistake/inaccuracy) sobre el punto correspondiente,
        // con halo blanco para contraste sobre el área/curva.
        markers.forEach { (i, color) ->
            if (i in 0..lastIndex) {
                val center = Offset(xAt(i), yAt(series[i]))
                drawCircle(color = GraphWhite, radius = 5f, center = center)
                drawCircle(color = color, radius = 4f, center = center)
            }
        }

        // Marcador del punto actual.
        val mx = xAt(currentIndex.coerceIn(0, lastIndex))
        drawLine(markerColor, Offset(mx, 0f), Offset(mx, h), strokeWidth = 2f)
    }
}
