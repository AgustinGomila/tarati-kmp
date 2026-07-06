package com.agustin.tarati.features.game6

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agustin.tarati.ui.theme.getBoardColors
import kotlin.math.min

/**
 * Ícono del modo de juego (hexágono con peones) dibujado en [Canvas] — versión **theme-adaptativa**
 * de `game6_ic_single`/`game6_ic_multi`. A diferencia de los vector drawables (con `#000000` fijo),
 * aquí las líneas y contornos usan [lineColor] (que el llamador toma del color del tema, p. ej. el del
 * título del segmento) → se adaptan a claro/oscuro y a seleccionado/no.
 *
 * Los peones del **single** usan los cobs de la paleta activa (`whiteCobColor`/`blackCobColor`, ya
 * theme-aware); los del **multi** conservan sus 6 colores de identidad (Okabe-Ito, vívidos en cualquier
 * fondo). Geometría tomada de los SVG originales (viewBox en mm), escalada al tamaño del [Canvas].
 */
@Composable
fun GameModeIcon(
    mode: GameMode,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val boardColors = getBoardColors()
    val geom = if (mode == GameMode.SINGLE) SingleGeom else MultiGeom
    val pawnFills = if (mode == GameMode.SINGLE) {
        listOf(boardColors.blackCobColor, boardColors.whiteCobColor) // superior negro, inferior blanco
    } else {
        MultiPawnColors
    }

    Canvas(modifier) {
        val scale = min(size.width / geom.w, size.height / geom.h)
        val ox = (size.width - geom.w * scale) / 2f
        val oy = (size.height - geom.h * scale) / 2f
        fun at(p: Offset) = Offset(ox + p.x * scale, oy + p.y * scale)

        val strokePx = STROKE_W * scale
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Hexágono.
        val hexPath = Path().apply {
            geom.hex.forEachIndexed { i, p ->
                val s = at(p)
                if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
            }
            close()
        }
        drawPath(hexPath, color = lineColor, style = stroke)

        // Líneas internas (eje + diagonales).
        geom.lines.forEach { (a, b) ->
            drawLine(lineColor, at(a), at(b), strokeWidth = strokePx, cap = StrokeCap.Round)
        }

        // Peones: relleno + contorno adaptativo.
        val r = PAWN_R * scale
        geom.pawns.forEachIndexed { i, c ->
            val center = at(c)
            drawCircle(color = pawnFills[i], radius = r, center = center)
            drawCircle(color = lineColor, radius = r, center = center, style = Stroke(width = strokePx))
        }
    }
}

// ── Geometría (coordenadas del SVG, viewBox en mm) ──────────────────────────────

private const val STROKE_W = 4f
private const val PAWN_R = 5.1589103f

private class ModeGeom(
    val w: Float,
    val h: Float,
    val hex: List<Offset>,
    val lines: List<Pair<Offset, Offset>>,
    val pawns: List<Offset>,
)

private fun o(x: Float, y: Float) = Offset(x, y)

private val SingleGeom = ModeGeom(
    w = 55.03155f,
    h = 73.243965f,
    hex = listOf(
        o(27.515776f, 66.08505f), o(2.000005f, 51.35352f), o(2.000005f, 21.890442f),
        o(27.515776f, 7.158911f), o(53.03155f, 21.890442f), o(53.03155f, 51.35352f),
    ),
    lines = listOf(
        o(27.51577f, 7.158901f) to o(27.51577f, 66.08505f),
        o(2.0f, 51.35352f) to o(53.03154f, 21.890442f),
        o(2.0f, 21.890442f) to o(53.03155f, 51.35352f),
    ),
    pawns = listOf(o(27.515785f, 7.158909f), o(27.515785f, 66.08505f)),
)

private val MultiGeom = ModeGeom(
    w = 65.34936f,
    h = 73.243965f,
    hex = listOf(
        o(32.674686f, 66.08505f), o(7.158915f, 51.353523f), o(7.158915f, 21.890446f),
        o(32.674686f, 7.158915f), o(58.19046f, 21.890446f), o(58.19046f, 51.353523f),
    ),
    lines = listOf(
        o(32.67468f, 7.158905f) to o(32.67468f, 66.08505f),
        o(7.15891f, 51.353523f) to o(58.19045f, 21.890446f),
        o(7.15891f, 21.890446f) to o(58.19045f, 51.353516f),
    ),
    pawns = listOf(
        o(32.67469f, 7.15891f), o(32.67469f, 66.08505f),
        o(7.1589136f, 51.48628f), o(58.190453f, 51.48628f),
        o(7.1589136f, 21.757696f), o(58.190453f, 21.757696f),
    ),
)

/**
 * Colores de identidad de los 6 peones del multi, en el **orden posicional** del SVG original (por
 * vértice del hexágono, no por índice de jugador). Fuente única: [OkabeIto].
 */
private val MultiPawnColors = listOf(
    OkabeIto.Vermillion, OkabeIto.Orange, OkabeIto.ReddishPurple,
    OkabeIto.Green, OkabeIto.SkyBlue, OkabeIto.Blue,
)
