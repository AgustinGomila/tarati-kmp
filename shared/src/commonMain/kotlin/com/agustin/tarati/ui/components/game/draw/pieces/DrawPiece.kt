package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.ui.components.game.animation.AnimatedCob
import com.agustin.tarati.ui.components.game.draw.board.LightOfDay
import com.agustin.tarati.ui.components.game.draw.board.cobShapeFor
import com.agustin.tarati.ui.components.game.draw.common.MorphShape
import com.agustin.tarati.ui.components.game.draw.common.createPathMeasure
import com.agustin.tarati.ui.theme.BoardColors

/**
 * Dispatcher de dibujo de piezas.
 *
 * Consulta [PieceTypeManager.currentPieceType] y delega al renderer correcto:
 * - `sides == 1` (Círculo) → sistema circular original en DrawCob.kt.
 * - `sides  > 1` (Polígono) → DrawMorphCob.kt + animaciones poligonales de este archivo.
 *
 * ## SOLID
 * DrawCob.kt y DrawMorphCob.kt no se conocen entre sí. Este archivo es el único punto
 * de integración. Para añadir nuevos renderers basta extender los `when` de este archivo.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Constantes
// ─────────────────────────────────────────────────────────────────────────────

/** Grosor del canto poligonal. Mismo valor que COIN_EDGE_THICKNESS circular (0.22f). */
const val MORPH_EDGE_THICKNESS: Float = 0.22f

/**
 * Anillo de selección poligonal con cabeza animada.
 *
 * ## Capas
 * 1. **Anillo difuso** — halo semitransparente de [selectionIndicatorColor] (estático).
 * 2. **Outline fino** — trazo de [baseColor] sobre el perímetro (estático).
 * 3. **Segmento animado** — tramo del ≈12 % del perímetro que viaja continuamente,
 *    compuesto por un trazo difuso exterior y un trazo fino interior.
 * 4. **Punta PENCIL** — arrowhead en la cabeza del segmento, orientado según
 *    la tangente del perímetro en ese punto (via [android.graphics.PathMeasure]).
 * 5. **Punto de brillo** — círculo pequeño en la cabeza, igual que en [drawSelection].
 *
 * La posición de la cabeza se deriva de [selectionTimeMs] con un período de 2 s,
 * igual que `glowAngleDeg` en la versión circular.
 */
fun DrawScope.drawPolygonSelection(
    position: Offset,
    radius: Float,
    pieceType: PieceType,
    baseColor: Color,
    colors: BoardColors,
    selectionTimeMs: Long = 0L,
) {
    val selR = radius * 1.4f
    val selPath = pieceType.shape.createPath(Size(selR * 2f, selR * 2f))

    // ── Corrección de centroide ───────────────────────────────────────────────
    // Para polígonos asimétricos (triángulo, pentágono) el centroide visual
    // no coincide con el centro del bounding-box. La diferencia se escala con
    // (selScale - 1) para alinear el centroide de la selección con el de la pieza.
    //
    //   Triángulo (vertex-up): Δy ≈ +0.30r → corrección ≈ +0.12r (desplaza ring hacia abajo)
    //   Pentágono (vertex-up): Δy ≈ +0.10r → corrección ≈ +0.04r
    //   Resto de formas:       Δy = 0       → sin corrección
    val selScale = selR / radius   // = 1.4
    val pieceR = radius * pieceType.shape.sizeFrac
    val pieceCentroid = pieceType.shape.computeCentroid(radius, radius, pieceR, pieceR)
    val centroidDx = pieceCentroid.x - radius
    val centroidDy = pieceCentroid.y - radius
    // origin: el centroide de la selección queda sobre el centroide de la pieza
    val originX = position.x - selR - centroidDx * (selScale - 1f)
    val originY = position.y - selR - centroidDy * (selScale - 1f)

    // ── 1 + 2. Anillos estáticos ──────────────────────────────────────────────
    translate(left = originX, top = originY) {
        drawPath(
            selPath, color = colors.selectionIndicatorColor.copy(alpha = 0.1f),
            style = Stroke(width = radius * 0.5f)
        )
        drawPath(
            selPath, color = baseColor.copy(alpha = 0.75f),
            style = Stroke(width = radius * 0.10f)
        )
    }

    // ── 3 + 4 + 5. Cabeza animada ─────────────────────────────────────────────
    val measure = createPathMeasure(selPath)
    val totalLength = measure.length
    if (totalLength <= 0f) return

    val periodMs = 2000L
    val t = (selectionTimeMs % periodMs).toFloat() / periodMs
    val headDist = t * totalLength

    // Segmento: ~12 % del perímetro detrás de la cabeza
    val segLen = totalLength * 0.12f
    val segStart = ((headDist - segLen) + totalLength) % totalLength

    // Extraer el path del segmento (puede cruzar el 0)
    val segCompose = if (segStart < headDist) {
        // Segmento normal sin wrap
        measure.getSegment(segStart, headDist)
    } else {
        // Segmento con wrap: de segStart hasta el final + de 0 hasta headDist
        val part1 = measure.getSegment(segStart, totalLength)
        val part2 = measure.getSegment(0f, headDist)

        if (part1 != null && part2 != null) {
            Path().apply {
                addPath(part1)
                addPath(part2)
            }
        } else {
            part1 ?: part2
        }
    }

    // Si no se pudo obtener el segmento, usar fallback simple
    if (segCompose == null) {
        // Fallback para Desktop: anillo simple sin animación
        translate(left = originX, top = originY) {
            drawPath(
                selPath,
                color = baseColor.copy(alpha = 0.6f),
                style = Stroke(width = radius * 0.15f)
            )
        }
        return
    }

    translate(left = originX, top = originY) {
        // Trazo difuso exterior
        drawPath(
            segCompose,
            brush = SolidColor(baseColor.copy(alpha = 0.25f)),
            style = Stroke(width = radius * 0.3f)
        )
        // Trazo fino interior
        drawPath(
            segCompose,
            brush = SolidColor(baseColor.copy(alpha = 0.70f)),
            style = Stroke(width = radius * 0.10f)
        )
    }

    // Posición y tangente en la cabeza (en el espacio del path de selección)
    val posTan = measure.getPosTan(headDist)
    if (posTan == null) {
        // Fallback: solo punto brillante sin flecha
        val glowRadius = radius * 0.12f
        drawCircle(
            color = baseColor.copy(alpha = 0.8f),
            radius = glowRadius,
            center = Offset(originX + selR, originY + selR),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = glowRadius * 0.5f,
            center = Offset(originX + selR, originY + selR),
        )
        return
    }

    val headX = originX + posTan.position[0]
    val headY = originY + posTan.position[1]
    val headPos = Offset(headX, headY)

    // Punta PENCIL en la dirección de avance (tangente)
    val tipLen = radius * 0.18f
    drawLineWithArrowHead(
        start = Offset(headX - posTan.tangent[0] * tipLen, headY - posTan.tangent[1] * tipLen),
        end = headPos,
        brush = SolidColor(baseColor.copy(alpha = 0.90f)),
        strokeWidth = 0f,           // sin línea visible — solo la punta
        arrowSize = radius * 0.28f,
        arrowWidth = radius * 0.10f,
        arrowStyle = ArrowTipStyle.PENCIL,
    )

    // Punto de brillo
    drawCircle(
        color = baseColor.copy(alpha = 0.9f),
        center = headPos,
        radius = radius * 0.12f,
    )
}

/** Sobrecarga por [Cob] (single 2-color): deriva el `baseColor` del bando. */
fun DrawScope.drawPolygonSelection(
    position: Offset,
    radius: Float,
    pieceType: PieceType,
    cob: Cob,
    colors: BoardColors,
    selectionTimeMs: Long = 0L,
) = drawPolygonSelection(
    position = position,
    radius = radius,
    pieceType = pieceType,
    baseColor = if (cob.color == WHITE) colors.whiteCobSelectColor else colors.blackCobSelectColor,
    colors = colors,
    selectionTimeMs = selectionTimeMs,
)

// ─────────────────────────────────────────────────────────────────────────────
// Animaciones de conversión poligonales
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Onda de impacto poligonal. Port de `shockWaveEffect` de DrawCob.kt:
 * mismos parámetros de timing/alpha/radio, pero dibuja el outline del polígono
 * en lugar de un anillo circular.
 */
private fun DrawScope.drawMorphShockWave(
    position: Offset,
    radius: Float,
    progress: Float,
    shape: MorphShape,
    waveColor: Color,
) {
    if (progress > 0.5f && progress < 0.9f) {
        val waveProgress = (progress - 0.5f) / 0.4f
        val waveAlpha = (1f - waveProgress) * 0.6f
        val waveRadius = radius * (1.1f + waveProgress * 0.4f)
        val wavePath = shape.createPath(Size(waveRadius * 2f, waveRadius * 2f))
        translate(left = position.x - waveRadius, top = position.y - waveRadius) {
            drawPath(
                wavePath, color = waveColor.copy(alpha = waveAlpha),
                style = Stroke(width = radius * 0.08f)
            )
        }
    }
}

/** Color de onda de conversión según el bando de la pieza que se convierte. */
private fun BoardColors.convertingWaveColor(cobColor: CobColor): Color =
    if (cobColor == WHITE) whiteConvertingWaveColor else blackConvertingWaveColor

// ── Volteo (FLIP) ──────────────────────────────────────────────────────────────

/**
 * Volteo poligonal con **colores explícitos** origen→destino (núcleo N-color compartido por single
 * y game6). El eje se deriva de [flipSeed] con la fórmula áurea (`seed × 137.508 % 360`). El scheme
 * mapea la cara frontal (WHITE) a [sourceColors] y la trasera (BLACK) a [targetColors] → el volteo
 * revela el color destino, sin depender del esquema de 2 bandos.
 */
fun DrawScope.drawMorphConversionFlip(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    shape: MorphShape,
    borderPattern: BorderPattern,
    centerMotif: CenterMotif,
    sourceColors: ShapeColors,
    targetColors: ShapeColors,
    flipSeed: Int,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val flipAngleDeg = (flipSeed * 137.508f) % 360f
    val projection = MorphShapeProjection(shape = shape, axisAngleDeg = flipAngleDeg)
    val scheme = ShapeColorScheme { cobColor, _ -> if (cobColor == WHITE) sourceColors else targetColors }

    drawMorphFlip(
        position = position,
        radius = radius,
        projection = projection,
        flipProgress = conversionProgress,
        cobShape = CobShape(
            shape = shape,
            colorScheme = scheme,
            borderPattern = borderPattern,
            centerMotif = centerMotif
        ),
        cobColor = WHITE,
        boardColors = boardColors,
        hourOfDay = hourOfDay,
    )
}

/** Sobrecarga por [AnimatedCob] (single 2-color): delega en el núcleo con los colores del bando. */
fun DrawScope.drawMorphConversionFlip(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    pieceType: PieceType,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val cob = animatedCob.cob
    val cobShape = cobShapeFor(pieceType, cob)
    drawMorphConversionFlip(
        position = position,
        radius = radius,
        conversionProgress = animatedCob.conversionProgress,
        shape = pieceType.shape,
        borderPattern = cobShape.borderPattern,
        centerMotif = cobShape.centerMotif,
        sourceColors = cobShape.colorScheme.resolve(cob.color, boardColors),
        targetColors = cobShape.colorScheme.resolve(cob.color.opponent, boardColors),
        flipSeed = animatedCob.vertex.hashCode(),
        boardColors = boardColors,
        hourOfDay = hourOfDay,
    )
}

// ── Desde el centro (FROM_CENTER) ────────────────────────────────────────────────

/**
 * Conversión desde el centro con **CobShapes explícitos** (núcleo N-color): el [targetShape] crece
 * desde el centroide cubriendo el [sourceShape]. Ambos shapes traen su color ya resuelto (scheme
 * fijo, ver [frozen]), así que el `cobColor` que reciben es indiferente.
 *
 * 1. **Original** ([sourceShape]) completo en `position`.
 * 2. **Objetivo** ([targetShape]) crece desde `centroid` (p=0) hasta `position` (p=1).
 * 3. Onda de impacto desde el centroide.
 */
fun DrawScope.drawMorphConversionFromCenter(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    shape: MorphShape,
    sourceShape: CobShape,
    targetShape: CobShape,
    waveColor: Color,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val rx = radius * shape.sizeFrac
    val centroid = shape.computeCentroid(position.x, position.y, rx, rx)

    // 1. Original completo como base
    drawMorphCob(position, radius, sourceShape, WHITE, boardColors, hourOfDay)

    // 2. Objetivo expandiéndose: nace en centroid (p=0), llega a position (p=1).
    if (conversionProgress > 0f) {
        val growPos = Offset(
            centroid.x + (position.x - centroid.x) * conversionProgress,
            centroid.y + (position.y - centroid.y) * conversionProgress,
        )
        drawMorphCob(growPos, radius * conversionProgress, targetShape, WHITE, boardColors, hourOfDay)
    }

    // 3. Onda desde el centroide
    drawMorphShockWave(centroid, radius, conversionProgress, shape, waveColor)
}

/** Sobrecarga por [AnimatedCob] (single 2-color): delega con los shapes del bando resueltos. */
fun DrawScope.drawMorphConversionFromCenter(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    pieceType: PieceType,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val cob = animatedCob.cob
    drawMorphConversionFromCenter(
        position = position,
        radius = radius,
        conversionProgress = animatedCob.conversionProgress,
        shape = pieceType.shape,
        sourceShape = cobShapeFor(pieceType, cob).frozen(cob.color, boardColors),
        targetShape = cobShapeFor(pieceType, Cob(cob.color.opponent, cob.isUpgraded))
            .frozen(cob.color.opponent, boardColors),
        waveColor = boardColors.convertingWaveColor(cob.color),
        boardColors = boardColors,
        hourOfDay = hourOfDay,
    )
}

// ── Desde el borde (FROM_BORDER) ─────────────────────────────────────────────────

/**
 * Conversión desde el borde con **CobShapes explícitos** (núcleo N-color): el [sourceShape] se
 * contrae hacia el centroide revelando el [targetShape].
 *
 * 1. **Objetivo** ([targetShape]) completo en `position` (base).
 * 2. **Original** ([sourceShape]) contrayéndose desde `position` (p=0) hasta `centroid` (p=1).
 * 3. Onda de impacto desde el centroide.
 */
fun DrawScope.drawMorphConversionFromBorder(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    shape: MorphShape,
    sourceShape: CobShape,
    targetShape: CobShape,
    waveColor: Color,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val rx = radius * shape.sizeFrac
    val centroid = shape.computeCentroid(position.x, position.y, rx, rx)

    // 1. Objetivo completo como base
    drawMorphCob(position, radius, targetShape, WHITE, boardColors, hourOfDay)

    // 2. Original contrayéndose: parte en position (p=0), llega al centroide (p=1).
    if (conversionProgress < 1f) {
        val shrinkPos = Offset(
            position.x + (centroid.x - position.x) * conversionProgress,
            position.y + (centroid.y - position.y) * conversionProgress,
        )
        drawMorphCob(shrinkPos, radius * (1f - conversionProgress), sourceShape, WHITE, boardColors, hourOfDay)
    }

    // 3. Onda desde el centroide
    drawMorphShockWave(centroid, radius, conversionProgress, shape, waveColor)
}

/** Sobrecarga por [AnimatedCob] (single 2-color): delega con los shapes del bando resueltos. */
fun DrawScope.drawMorphConversionFromBorder(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    pieceType: PieceType,
    boardColors: BoardColors,
    hourOfDay: Float,
) {
    val cob = animatedCob.cob
    drawMorphConversionFromBorder(
        position = position,
        radius = radius,
        conversionProgress = animatedCob.conversionProgress,
        shape = pieceType.shape,
        sourceShape = cobShapeFor(pieceType, cob).frozen(cob.color, boardColors),
        targetShape = cobShapeFor(pieceType, Cob(cob.color.opponent, cob.isUpgraded))
            .frozen(cob.color.opponent, boardColors),
        waveColor = boardColors.convertingWaveColor(cob.color),
        boardColors = boardColors,
        hourOfDay = hourOfDay,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// drawPiece — reemplazo de drawCob
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drop-in replacement de [drawCob] con dispatch por tipo de pieza activo.
 *
 * Para el círculo, [MorphShape.sizeFrac] se aplica escalando el radio antes de
 * delegar porque [drawCob] no usa [MorphShape.createPath].
 *
 * @param tiltDeg Inclinación orgánica en grados. Solo se aplica a piezas poligonales
 *   (sides > 1); el círculo es invariante a rotación. Para ángulos ≤ 10° el efecto
 *   sobre la sombra es visualmente inapreciable.
 */
fun DrawScope.drawPiece(
    position: Offset,
    radius: Float,
    vertex: Vertex,
    selectedVertex: Vertex?,
    cob: Cob,
    hourOfDay: Float = 12f,
    selectionTimeMs: Long = 0L,
    tiltDeg: Float = 0f,
    colors: BoardColors,
    precomputedLight: LightOfDay? = null,
    precomputedOrganicColors: Map<CobColor, Color>? = null,
) {
    val pieceType = PieceTypeManager.currentPieceType
    if (pieceType.shape.sides <= 1) {
        val adjustedRadius = radius * pieceType.shape.sizeFrac
        drawCob(
            position, adjustedRadius, selectedVertex, vertex, cob,
            hourOfDay, selectionTimeMs, colors, precomputedLight, precomputedOrganicColors
        )
        return
    }

    // ── Polígono: rotar el canvas alrededor del centro de la pieza ─────────
    rotate(degrees = tiltDeg, pivot = position) {
        drawMorphCob(
            position, radius,
            cobShape = cobShapeFor(pieceType, cob), cobColor = cob.color,
            boardColors = colors, hourOfDay = hourOfDay
        )
        if (vertex == selectedVertex)
            drawPolygonSelection(position, radius, pieceType, cob, colors, selectionTimeMs)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// drawAnimatedPiece — reemplazo de drawAnimatedCob
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drop-in replacement de [drawAnimatedCob] con dispatch por tipo de pieza activo.
 *
 * ## Conversiones poligonales
 * El [ConversionAnimationType] se respeta igual que en el sistema circular:
 * - **FLIP**         → volteo con eje áureo determinista por vértice.
 * - **FROM_CENTER**  → objetivo crece desde el centro.
 * - **FROM_BORDER**  → original se contrae desde el borde.
 *
 * La animación de upgrade (`upgradeProgress`) no tiene equivalente poligonal aún
 * — el indicador central aparece instantáneamente, lo cual es visual coherente.
 *
 * @param tiltDeg Ángulo de inclinación interpolado entre [AnimatedCob.fromTiltDeg] y
 *   [AnimatedCob.toTiltDeg] en el llamador. Solo se aplica a piezas poligonales.
 */
fun DrawScope.drawAnimatedPiece(
    position: Offset,
    radius: Float,
    vertex: Vertex,
    selectedVertex: Vertex?,
    animatedCob: AnimatedCob,
    hourOfDay: Float = 12f,
    selectionTimeMs: Long = 0L,
    tiltDeg: Float = 0f,
    animationType: ConversionAnimationType = ConversionAnimationType.FROM_CENTER,
    colors: BoardColors,
    precomputedLight: LightOfDay? = null,
) {
    val pieceType = PieceTypeManager.currentPieceType
    if (pieceType.shape.sides <= 1) {
        val adjustedRadius = radius * pieceType.shape.sizeFrac
        drawAnimatedCob(
            position, adjustedRadius, vertex, selectedVertex, animatedCob,
            hourOfDay, selectionTimeMs, animationType, colors,
            precomputedLight
        )
        return
    }

    // ── Polígono: rotar el canvas alrededor del centro de la pieza ─────────
    rotate(degrees = tiltDeg, pivot = position) {
        if (animatedCob.isConverting) {
            when (animationType) {
                ConversionAnimationType.FLIP ->
                    drawMorphConversionFlip(position, radius, animatedCob, pieceType, colors, hourOfDay)

                ConversionAnimationType.FROM_CENTER ->
                    drawMorphConversionFromCenter(position, radius, animatedCob, pieceType, colors, hourOfDay)

                ConversionAnimationType.FROM_BORDER ->
                    drawMorphConversionFromBorder(position, radius, animatedCob, pieceType, colors, hourOfDay)
            }
        } else {
            drawMorphCob(
                position, radius,
                cobShape = cobShapeFor(pieceType, animatedCob.cob),
                cobColor = animatedCob.cob.color, boardColors = colors, hourOfDay = hourOfDay
            )
        }

        if (vertex == selectedVertex)
            drawPolygonSelection(position, radius, pieceType, animatedCob.cob, colors, selectionTimeMs)
    }
}