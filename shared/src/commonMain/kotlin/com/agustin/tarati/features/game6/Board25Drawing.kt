package com.agustin.tarati.features.game6

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.ui.components.game.draw.board.getLightOfDay
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import com.agustin.tarati.ui.theme.BoardColors

/**
 * Sombreado de las áreas del tablero `25` con la paleta activa (mismos tokens que single:
 * fondo de tablero + `boardPatternColor1/2/3`), sobre la geometría propia de Board25.
 */
internal fun DrawScope.drawBoard25Board(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    showRegions: Boolean,
    showPerimeter: Boolean,
    showEdges: Boolean,
) {
    if (!showRegions && !showPerimeter) return

    // Superficie base del tablero.
    val minX = screen.values.minOf { it.x }
    val maxX = screen.values.maxOf { it.x }
    val minY = screen.values.minOf { it.y }
    val maxY = screen.values.maxOf { it.y }
    // Margen más ancho que el semigrosor del perímetro → el RoundRect de fondo lo contiene.
    val margin = minOf(size.width, size.height) * 0.065f
    drawRoundRect(
        color = colors.boardBackground.copy(alpha = 0.6f),
        topLeft = Offset(minX - margin, minY - margin),
        size = Size(maxX - minX + 2 * margin, maxY - minY + 2 * margin),
        cornerRadius = CornerRadius(16f),
    )

    // Perímetro (bajo las áreas, como single).
    if (showPerimeter) drawBoard25Perimeter(screen, colors, showEdges)

    if (showRegions) drawBoard25RegionFills(screen, colors, drawBorders = true)

    // Textura de grano sobre toda la superficie del tablero, igual que single.
    with(NoiseTexture) {
        applyNoise(
            topLeft = Offset(minX - margin, minY - margin),
            size = Size(maxX - minX + 2 * margin, maxY - minY + 2 * margin),
            cornerRadius = CornerRadius(16f),
            alpha = 0.07f,
        )
    }
}

/** Perímetro cosmético del tablero `25` (borde grueso + luz/sombra), como `drawPerimeter` de single. */
private fun DrawScope.drawBoard25Perimeter(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    edgesVisible: Boolean,
) {
    val path = Path().apply {
        Board25.externalBoundary.forEachIndexed { i, vertex ->
            val p = screen.getValue(vertex)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    val bounds = path.getBounds()
    val vertexDistance = minOf(bounds.width, bounds.height) * 0.10f

    if (edgesVisible) {
        drawPath(
            path = path,
            color = colors.boardEdgeColor.copy(alpha = 0.2f),
            style = Stroke(width = vertexDistance * 1.15f, join = StrokeJoin.Round),
        )
    }
    drawPath(
        path = path,
        color = colors.boardPerimeterColor,
        style = Stroke(width = vertexDistance, join = StrokeJoin.Round),
    )

    // Bordes de luz (lado del sol) y sombra (opuesto), como single.
    val lightOfDay = getLightOfDay(hourOfDay = 12f, baseRadius = 32f)
    val lightPath = Path().apply {
        addPath(path)
        translate(Offset(lightOfDay.sunPosition.x * 2f, lightOfDay.sunPosition.y * 2f))
    }
    drawPath(lightPath, color = colors.boardPatternColor3.copy(alpha = 0.4f), style = Stroke(width = 4f))
    val shadowPath = Path().apply {
        addPath(path)
        translate(Offset(-lightOfDay.sunPosition.x * 4f, -lightOfDay.sunPosition.y * 4f))
    }
    drawPath(shadowPath, color = colors.boardVertexColor.copy(alpha = 0.4f), style = Stroke(width = 4f))
}

/**
 * Rellena las regiones del tablero `25` con la paleta (sin fondo base ni grano). Reutilizado por el
 * sombreado del tablero real y por la silueta del fondo decorativo ([drawBoard25Silhouette]).
 */
private fun DrawScope.drawBoard25RegionFills(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    drawBorders: Boolean,
) {
    val border = colors.boardPatternBorderColor
    Board25.centralRegions.forEachIndexed { i, region ->
        fillRegion(
            region,
            screen,
            if (i % 2 == 0) colors.boardPatternColor3 else colors.boardPatternColor2,
            border,
            drawBorders
        )
    }
    Board25.circumferenceRegions.forEachIndexed { i, region ->
        fillRegion(
            region,
            screen,
            if (i % 2 == 0) colors.boardPatternColor3 else colors.boardPatternColor1,
            border,
            drawBorders
        )
    }
    // Bandas C→D → color principal de las domésticas.
    Board25.bandRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor1, border, drawBorders)
    }
    // Cuadrados de base → color alternativo (alterna con la banda adyacente).
    Board25.baseSquareRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor2, border, drawBorders)
    }
    Board25.connectorSideRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor3, border, drawBorders)
    }
    // Triángulos puntiagudos del conector → color alternativo del centro.
    Board25.connectorTipRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor2, border, drawBorders)
    }
}

/** Silueta del tablero `25` para el fondo decorativo (solo rellenos de regiones, sin bordes). */
internal fun DrawScope.drawBoard25Silhouette(colors: BoardColors, canvasSize: Size) {
    val screen = Board25Geometry.fit(canvasSize)
    drawBoard25RegionFills(screen, colors, drawBorders = false)
}

private fun DrawScope.fillRegion(
    region: Region,
    screen: Map<Vertex, Offset>,
    fill: Color,
    border: Color,
    drawBorder: Boolean,
) {
    val path = Path().apply {
        region.vertices.forEachIndexed { i, vertex ->
            val p = screen.getValue(vertex)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(path = path, color = fill, style = Fill)
    if (drawBorder) {
        drawPath(path = path, color = border.copy(alpha = 0.2f), style = Stroke(width = 1f))
    }
}
