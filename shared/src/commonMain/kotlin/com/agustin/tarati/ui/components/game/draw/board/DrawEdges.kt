package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.GameBoard.edges
import com.agustin.tarati.core.domain.game.board.VisualPositionCache
import com.agustin.tarati.core.domain.game.board.getVisualPosition
import com.agustin.tarati.ui.components.game.BoardState
import com.agustin.tarati.ui.components.game.draw.pieces.ArrowTipStyle
import com.agustin.tarati.ui.components.game.draw.pieces.drawLineWithArrowHead
import com.agustin.tarati.ui.components.game.highlights.base.DynamicEdgeHighlight
import com.agustin.tarati.ui.components.game.highlights.base.EdgeHighlight
import com.agustin.tarati.ui.theme.BoardColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Clock

fun DrawScope.drawEdges(
    canvasSize: Size,
    orientation: BoardOrientation,
    boardState: BoardState,
    colors: BoardColors,
    positionCache: VisualPositionCache? = null,
) {
    val edgesVisible = boardState.boardVisualState.edgesVisibles

    if (edgesVisible) {
        val strokeWidth: Float = minOf(canvasSize.width, canvasSize.height) * edgesStrokeFactor

        edges.forEach { (edge) ->
            // OPT: positionCache elimina 2 × 34 = 68 llamadas a getVisualPosition
            // por redibujado del canvas estático, evitando las multiplicaciones
            // matriciales correspondientes.
            val fromPos = positionCache?.get(edge.first)
                ?: getVisualPosition(vertex = edge.first, size = canvasSize, orientation = orientation)
            val toPos = positionCache?.get(edge.second)
                ?: getVisualPosition(vertex = edge.second, size = canvasSize, orientation = orientation)

            drawLine(
                color = colors.boardEdgeColor.copy(alpha = edgesStrokeAlpha),
                start = fromPos,
                end = toPos,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

fun DrawScope.drawDynamicFireballEdgeHighlight(
    highlight: DynamicEdgeHighlight,
    canvasSize: Size,
    colors: BoardColors,
) {
    val fromPos = highlight.from
    val toPos = highlight.to
    val strokeWidth: Float = minOf(canvasSize.width, canvasSize.height) * fireballStrokeFactor

    drawFireballEdgeHighlight(fromPos, toPos, strokeWidth, highlight.pulse, colors)
}

fun DrawScope.drawArrowEdgeHighlightFromVertex(
    highlight: EdgeHighlight,
    canvasSize: Size,
    orientation: BoardOrientation,
    colors: BoardColors,
) {
    val fromPos = getVisualPosition(highlight.edge.from, canvasSize, orientation)
    val toPos = getVisualPosition(highlight.edge.to, canvasSize, orientation)
    val shortSide = minOf(canvasSize.width, canvasSize.height)
    val pieceRadius = shortSide * 0.08f / 2f

    // Recortar extremos para que la flecha arranque en el borde de la pieza origen
    // y la punta no llegue al centro del vértice destino.
    val dx = toPos.x - fromPos.x
    val dy = toPos.y - fromPos.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val startPad = pieceRadius * arrowStartPadFactor
    val endPad = pieceRadius * arrowEndPadFactor
    val start = Offset(fromPos.x + ux * startPad, fromPos.y + uy * startPad)
    val end = Offset(toPos.x - ux * endPad, toPos.y - uy * endPad)

    val strokeWidth = shortSide * arrowStokeFactor
    val arrowSize = pieceRadius * arrowSizeFactor
    val arrowWidth = arrowSize * arrowWidthFactor

    val pulseAlpha = if (highlight.pulse) {
        val t = (Clock.System.now().toEpochMilliseconds() % 900L) / 900f
        sin(t * 2 * PI).toFloat() * arrowPulseAlpha
    } else arrowPulseAlpha

    drawLineWithArrowHead(
        start = start,
        end = end,
        brush = SolidColor(colors.highlightEdge1Color),
        strokeWidth = strokeWidth,
        arrowSize = arrowSize,
        arrowWidth = arrowWidth,
        arrowStyle = ArrowTipStyle.ARROW,
        alpha = pulseAlpha,
    )
}

fun DrawScope.drawFireballEdgeHighlightFromVertex(
    highlight: EdgeHighlight,
    canvasSize: Size,
    orientation: BoardOrientation,
    colors: BoardColors,
) {
    val fromPos =
        getVisualPosition(
            highlight.edge.from,
            canvasSize,
            orientation,
        )
    val toPos =
        getVisualPosition(
            highlight.edge.to,
            canvasSize,
            orientation,
        )
    val strokeWidth: Float = minOf(canvasSize.width, canvasSize.height) * fireballStrokeFactor

    drawFireballEdgeHighlight(fromPos, toPos, strokeWidth, highlight.pulse, colors)
}

// ── Force Arc highlight: expanding crescent arcs O ))))) D ──────────────────
//
// Each of the [arcCount] arc positions holds a triplet of nested crescent paths
// (A, B, C) built by [buildCrescent]. The triplet is drawn back-to-front so that
// each successive crescent sits visually inside the previous one:
//
//   A  (highlightEdge3Color) — outermost halo, semi-transparent
//   B  (highlightEdge2Color) — mid layer, shifted toward D → sits inside A's body
//   C  (highlightEdge1Color) — core,      shifted further  → sits inside B's body
//
// Shifting B and C toward D by a fraction of [H] (the base half-height) exploits
// the crescent's convex bulge: the smaller shifted shape lands exactly inside the
// wider region of the larger one, producing a nested "glowing wave" appearance.
//
// Arc positions grow geometrically with [ratio], giving a natural
// "accelerating away" appearance as the wave travels from O toward D.
fun DrawScope.drawForceArcDynamicHighlight(highlight: DynamicEdgeHighlight, colors: BoardColors) {
    val progress = (Clock.System.now().toEpochMilliseconds() % highlight.duration) / highlight.duration.toFloat()
    drawForceArcDynamicHighlight(highlight.from, highlight.to, progress, colors, highlight.pulse)
}

/**
 * Variante con posiciones ([from]→[to]) y [progress] (0→1) **explícitos**: la onda de arcos viaja de
 * [from] a [to] según [progress]. Para reutilizar el efecto de captura en tableros con más de dos
 * colores (p. ej. el multijugador), donde el progreso lo maneja la animación del movimiento y no el reloj.
 */
fun DrawScope.drawForceArcDynamicHighlight(
    from: Offset,
    to: Offset,
    progress: Float,
    colors: BoardColors,
    pulse: Boolean = false,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

    // Unit vectors: axisDir points O→D; perpDir is the perpendicular (tip direction).
    val axisDir = Offset(dx / dist, dy / dist)
    val perpDir = Offset(-dy / dist, dx / dist)

    // 7 elements with ratio 1.25 give gaps of 0.065·dist…0.200·dist (from O to D),
    // much denser than the previous ratio=1.6 which produced gaps up to 0.375·dist.
    val arcCount = 7
    val ratio = 1.25f
    var denom = 1f
    repeat(arcCount - 1) { denom *= ratio }

    val pulseTime = (Clock.System.now().toEpochMilliseconds() % 1000L) / 1000f
    val pulseFactor = if (pulse) (0.8f + 0.2f * sin(pulseTime * 2 * PI).toFloat()) else 1f

    repeat(arcCount) { i ->
        var ratioI = 1f
        repeat(i) { ratioI *= ratio }

        val arcRadius = dist * progress * ratioI / denom
        if (arcRadius < 2f) return@repeat

        val distFraction = (arcRadius / dist).coerceIn(0f, 1f)
        val fade = (progress * (1f - distFraction)).coerceIn(0f, 1f)
        if (fade <= 0.02f) return@repeat

        // Centre of A along the O→D axis. B and C are offset from here toward D.
        val baseCenter = Offset(
            from.x + axisDir.x * arcRadius,
            from.y + axisDir.y * arcRadius,
        )

        // Base half-height: grows with arcRadius so further arcs are taller,
        // matching the expanding wave feel. Pulse modulates size, not alpha.
        // halfHeight grows 3× from O to D: near O stays compact, near D opens wide.
        val h = arcRadius * 0.22f * (1f + 2f * distFraction) * pulseFactor

        // Shared bulge ratios — both CPs on the D side (see buildCrescent KDoc).
        //   convexRatio: outer arc CP. Actual visible midpoint = 0.5·convexRatio·H → 0.9·H toward D.
        //   concaveRatio: inner arc CP (< convexRatio). Body thickness = 0.5·(1.8−0.15)·H = 0.825·H.
        //
        // Centering: axial centre of a crescent = 0.25·convexRatio·H from its center.
        // Each layer's shift is chosen so its centre aligns with the outer layer's centre:
        //   shift = 0.25·convexRatio·(1 − scale)·H
        //
        // Nesting check: shift + 0.5·scale·convexRatio ≤ 0.5·convexRatio
        //   A: 0.000 + 0.900 = 0.900 ≤ 0.900 ✓
        //   B: 0.189 + 0.522 = 0.711 ≤ 0.900 ✓
        //   C: 0.302 + 0.297 = 0.599 ≤ 0.711 ✓

        // Layer descriptors: (scale, axisShift, color, alphaFactor, isCore).
        // Drawn back-to-front (A first) so each inner layer paints over the outer.
        // isCore = true → alpha is always 1f regardless of fade.
        data class Layer(
            val scale: Float,
            val shift: Float,
            val color: Color,
            val alphaFactor: Float,
            val isCore: Boolean = false,
        )
        listOf(
            Layer(scale = 1.00f, shift = 0.00f, color = colors.highlightEdge3Color, alphaFactor = 0.55f),
            Layer(scale = 0.58f, shift = 0.19f, color = colors.highlightEdge2Color, alphaFactor = 0.80f),
            Layer(scale = 0.33f, shift = 0.30f, color = colors.highlightEdge1Color, alphaFactor = 1.00f, isCore = true),
        ).forEach { (scale, shift, color, alphaFactor, isCore) ->
            val center = Offset(
                baseCenter.x + axisDir.x * h * shift,
                baseCenter.y + axisDir.y * h * shift,
            )
            drawPath(
                path = buildCrescent(
                    center = center,
                    axisDir = axisDir,
                    perpDir = perpDir,
                    halfHeight = h * scale,
                    convexBulge = h * scale * arcConvexRatioFactor,
                    concaveBulge = h * scale * arcConcaveRatioFactor,
                ),
                color = color.copy(alpha = if (isCore) 1f else (fade * alphaFactor).coerceIn(0f, 1f)),
                style = Fill,
            )
        }
    }
}

/**
 * Builds a filled crescent (medialuna) path with geometrically sharp tips.
 *
 * Both arcs bow toward D (+[axisDir]); the outer arc ([convexBulge]) bows more
 * strongly than the inner arc ([concaveBulge]). The D-side reads as convex
 * (full outward belly) and the O-side reads as concave (the hollow opening).
 *
 * ```
 *        cpConcave · · · · · · cpConvex
 *     (toward D, small)  (toward D, large)
 *
 *  tipA ────────── outer arc ───────── tipA
 *        ╲                          ╱
 *         ╲   inner arc (hollow)  ╱
 *    tipB ── ─────────────────── ── tipB
 *    O ◁ open/concave ....... convex ▷ D
 * ```
 *
 * **Sharp tips** — at [tipA] the departure tangent of the outer arc is
 * `cpConvex − tipA` (angled toward D and down) while the arrival tangent of
 * the inner arc is `tipA − cpConcave` (angled away from D and up). These are
 * not parallel → C0 cusp → visually acute tip. Opening angle ≈ 128° with the
 * default ratios (convexRatio = 1.8, concaveRatio = 0.15).
 *
 * @param center       Midpoint between [tipA] and [tipB]; anchor of the axis.
 * @param axisDir      Unit vector pointing O → D (emission → target).
 * @param perpDir      Unit vector perpendicular to [axisDir]; points toward [tipA].
 * @param halfHeight   Distance from [center] to each tip along [perpDir].
 * @param convexBulge  How far the outer arc's CP is pushed toward D. Large value.
 * @param concaveBulge How far the inner arc's CP is pushed toward D. Small value
 *                     (must be < [convexBulge] to preserve crescent body thickness).
 */
private fun buildCrescent(
    center: Offset,
    axisDir: Offset,
    perpDir: Offset,
    halfHeight: Float,
    convexBulge: Float,
    concaveBulge: Float,
): Path {
    val tipA = Offset(center.x + perpDir.x * halfHeight, center.y + perpDir.y * halfHeight)
    val tipB = Offset(center.x - perpDir.x * halfHeight, center.y - perpDir.y * halfHeight)

    // Both CPs are on the D side. The outer arc bows strongly toward D (large CP);
    // the inner arc bows gently toward D (small CP). The gap between them is the
    // crescent body. The O-side opens like a hollow — the concave face.
    val cpConvex = Offset(center.x + convexBulge * axisDir.x, center.y + convexBulge * axisDir.y) // outer → D
    val cpConcave = Offset(center.x + concaveBulge * axisDir.x, center.y + concaveBulge * axisDir.y) // inner → D (less)

    return Path().apply {
        moveTo(tipA.x, tipA.y)
        quadraticTo(cpConvex.x, cpConvex.y, tipB.x, tipB.y)  // outer (convex) face → D
        quadraticTo(cpConcave.x, cpConcave.y, tipA.x, tipA.y)  // inner (concave) face ← hollow toward O
        close()
    }
}

// ── DynamicForceArcImpact: concentric ring burst at B when force arcs arrive ((B)) ──────
//
// [arcStrokeFactor] and [arcFadeFactor] drive all 5 luminous passes.
fun DrawScope.drawForceArcImpactHighlight(highlight: DynamicEdgeHighlight, colors: BoardColors) {
    val progress = (Clock.System.now().toEpochMilliseconds() % highlight.duration) / highlight.duration.toFloat()
    drawForceArcImpactHighlight(highlight.from, progress, colors, highlight.pulse)
}

/**
 * Variante con [center] y [progress] (0→1) **explícitos**: el estallido de anillos en [center] según
 * [progress]. Para reutilizar en tableros con más de dos colores (p. ej. el multijugador).
 */
fun DrawScope.drawForceArcImpactHighlight(
    center: Offset,
    progress: Float,
    colors: BoardColors,
    pulse: Boolean = false,
) {
    val baseRadius = minOf(size.width, size.height) * arcBaseRadiusFactor

    val pulseTime = (Clock.System.now().toEpochMilliseconds() % 1000L) / 1000f
    val pulseFactor = if (pulse) (0.8f + 0.2f * sin(pulseTime * 2 * PI).toFloat()) else 1f

    // (color, fadeMultiplier, strokeMultiplier, isCore)
    // isCore = true → the central ring is drawn fully opaque (alpha 1f)
    // so the impact burst stays clearly visible regardless of fade.
    data class RingPass(val color: Color, val fadeMulti: Float, val strokeMulti: Float, val isCore: Boolean = false)

    val passes = listOf(
        RingPass(colors.highlightEdge3Color, 1f, 4.00f),
        RingPass(colors.highlightEdge3Color, 2f, 2.00f),
        RingPass(colors.highlightEdge2Color, 3f, 4.00f),
        RingPass(colors.highlightEdge2Color, 4f, 0.50f),
        RingPass(colors.highlightEdge1Color, 6f, 0.25f, isCore = true),
    )

    val ringCount = 3
    repeat(ringCount) { i ->
        val offset = i / ringCount.toFloat()
        val localP = ((progress - offset + 1f) % 1f)
        val fade = ((1f - localP) * pulseFactor).coerceIn(0f, 1f)
        if (fade <= 0.02f) return@repeat
        val ringRadius = baseRadius * (0.5f + localP * 2.0f)

        passes.forEach { (color, fadeMulti, strokeMulti, isCore) ->
            drawCircle(
                color = color.copy(
                    // Core ring is fully opaque so the impact burst stays clearly
                    // visible. Halo passes keep their fade gradient.
                    alpha = if (isCore) 1f else (fade * arcFadeFactor * fadeMulti).coerceIn(0f, 1f),
                ),
                radius = ringRadius,
                center = center,
                style = Stroke(width = baseRadius * arcStrokeFactor * strokeMulti, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawFireballEdgeHighlight(
    fromPos: Offset,
    toPos: Offset,
    strokeWidth: Float,
    pulse: Boolean,
    colors: BoardColors,
) {
    val pulseFactor =
        if (pulse) {
            val pulseTime = Clock.System.now().toEpochMilliseconds() % 1000L / 1000f
            (0.8f + 0.2f * sin(pulseTime * 2 * PI).toFloat())
        } else {
            1f
        }

    val direction = fromPos - toPos
    val circleRadius = 45f * pulseFactor
    val triangleWidth = 25f * pulseFactor

    // (scaleFactor, color, useStroke, alpha)
    data class StellaPass(
        val scale: Float,
        val color: Color,
        val stroke: Boolean,
        val alpha: Float
    )
    listOf(
        StellaPass(1.00f, colors.highlightEdge1Color, false, 0.7f),
        StellaPass(1.30f, colors.highlightEdge2Color, true, 0.3f),
    ).forEach { (scale, color, stroke, alpha) ->
        drawPath(
            path = createStellaPath(
                toPos,
                fromPos,
                direction,
                triangleWidth * scale,
                triangleWidth * scale
            ),
            color = color,
            style = if (stroke) Stroke(strokeWidth, join = StrokeJoin.Round) else Fill,
            alpha = alpha,
        )
    }

    // (radiusMulti, color, alpha)
    listOf(
        Triple(1.3f, colors.highlightEdge3Color, 0.2f),
        Triple(0.4f, colors.highlightEdge2Color, 0.8f),
    ).forEach { (multi, color, alpha) ->
        drawCircle(
            color = color,
            center = toPos,
            radius = circleRadius * multi,
            alpha = alpha,
        )
    }
}

private val pathCache = mutableMapOf<String, Path>()

private fun createCachedPath(
    key: String,
    createPath: () -> Path,
): Path = pathCache.getOrPut(key) { createPath() }

private fun getEdgeKey(
    fromPos: Offset,
    toPos: Offset,
    radius: Float,
    triangleWidth: Float,
): String = "${fromPos.x},${fromPos.y}_${toPos.x},${toPos.y}_${radius}_$triangleWidth"

private fun createStellaPath(
    toPos: Offset,
    fromPos: Offset,
    direction: Offset,
    radius: Float,
    triangleWidth: Float,
): Path {
    val key = getEdgeKey(fromPos, toPos, radius, triangleWidth)

    return createCachedPath(key) {
        val angle = atan2(direction.y, direction.x)

        // Base del triángulo (en toPos)
        val basePoint1 =
            Offset(
                x = toPos.x + triangleWidth * sin(angle),
                y = toPos.y - triangleWidth * cos(angle),
            )

        val basePoint2 =
            Offset(
                x = toPos.x - triangleWidth * sin(angle),
                y = toPos.y + triangleWidth * cos(angle),
            )

        // Punta del triángulo (en fromPos)
        val tipPoint = fromPos

        Path().apply {
            // Rect que contiene el círculo centrado en toPos
            val circleRect =
                Rect(
                    left = toPos.x - radius,
                    top = toPos.y - radius,
                    right = toPos.x + radius,
                    bottom = toPos.y + radius,
                )

            // Ángulos (en grados) de los puntos base relativos al centro (toPos)
            fun toDeg(rad: Double) = (rad * 180.0 / PI).toFloat()
            val angleBase1 = toDeg(atan2((basePoint1.y - toPos.y).toDouble(), (basePoint1.x - toPos.x).toDouble()))
            val angleBase2 = toDeg(atan2((basePoint2.y - toPos.y).toDouble(), (basePoint2.x - toPos.x).toDouble()))

            // Calculamos dos opciones de sweep (cw positivo)
            val start = angleBase2
            val sweepCW = ((angleBase1 - start + 360f) % 360f)
            val sweepCCW = sweepCW - 360f

            // función para obtener punto medio del arco dado start+sweep
            fun midPointForSweep(
                startAngle: Float,
                sweep: Float,
            ): Offset {
                val midAngleRad = (startAngle + sweep / 2.0) * PI / 180.0
                return Offset(
                    x = toPos.x + (radius * cos(midAngleRad)).toFloat(),
                    y = toPos.y + (radius * sin(midAngleRad)).toFloat(),
                )
            }

            // Vector "direction": fromPos - toPos
            // Elegimos el sweep que deje la mitad del arco apuntando *en sentido opuesto* a la punta
            val midCW = midPointForSweep(start, sweepCW)
            // val midCCW = midPointForSweep(start, sweepCCW)

            val dotCW = (midCW.x - toPos.x) * (direction.x) + (midCW.y - toPos.y) * (direction.y)
            // val dotCCW = (midCCW.x - toPos.x) * (direction.x) + (midCCW.y - toPos.y) * (direction.y)

            // Queremos que el punto medio del arco tenga proyección NEGATIVA sobre `direction`
            // (es decir: que apunte hacia el lado opuesto a la punta). Si dotCW < 0, escogemos sweepCW.
            val chosenSweep = if (dotCW < 0f) sweepCW else sweepCCW

            // Dibujamos la gota:
            moveTo(basePoint1.x, basePoint1.y)
            lineTo(tipPoint.x, tipPoint.y)
            lineTo(basePoint2.x, basePoint2.y)

            // Arc desde basePoint2 hasta basePoint1 (start = angleBase2)
            arcTo(
                rect = circleRect,
                startAngleDegrees = start,
                sweepAngleDegrees = chosenSweep,
                forceMoveTo = false,
            )

            close()
        }
    }
}

fun DrawScope.drawDynamicEdgeElectricHighlight(
    highlight: DynamicEdgeHighlight,
    variationFactor: Float,
    randomSegments: Int,
    colors: BoardColors,
): Unit = drawDynamicEdgeElectricHighlight(
    highlight.from, highlight.to, variationFactor, randomSegments, colors, highlight.pulse,
)

/**
 * Variante con posiciones ([from]→[to]) **explícitas** del rayo eléctrico. Para reutilizar el efecto
 * en tableros con más de dos colores (p. ej. el multijugador). El rayo se redibuja con
 * [variationFactor]/[randomSegments] nuevos por frame (parpadeo); no depende de un progreso.
 */
fun DrawScope.drawDynamicEdgeElectricHighlight(
    from: Offset,
    to: Offset,
    variationFactor: Float,
    randomSegments: Int,
    colors: BoardColors,
    pulse: Boolean = false,
) {
    drawEdgeElectricHighlight(variationFactor, randomSegments, from, to, pulse, colors)
}

/**
 * Vertex-to-vertex Force Arc highlight: resolves [ui.components.game.highlights.base.EdgeHighlight.edge] vertices
 * to screen positions, then delegates to [drawForceArcDynamicHighlight].
 * Same pattern as [drawElectricEdgeHighlightFromVertex].
 */
fun DrawScope.drawForceArcEdgeHighlight(
    highlight: EdgeHighlight,
    canvasSize: Size,
    orientation: BoardOrientation,
    colors: BoardColors,
) {
    val fromPos = getVisualPosition(highlight.edge.from, canvasSize, orientation)
    val toPos = getVisualPosition(highlight.edge.to, canvasSize, orientation)
    val dynamic = DynamicEdgeHighlight(
        from = fromPos,
        to = toPos,
        pulse = highlight.pulse,
        duration = highlight.duration,
    )
    drawForceArcDynamicHighlight(dynamic, colors)
}

fun DrawScope.drawElectricEdgeHighlightFromVertex(
    highlight: EdgeHighlight,
    variationFactor: Float,
    randomSegments: Int,
    canvasSize: Size,
    orientation: BoardOrientation,
    colors: BoardColors,
) {
    val fromPos =
        getVisualPosition(
            highlight.edge.from,
            canvasSize,
            orientation,
        )
    val toPos =
        getVisualPosition(
            highlight.edge.to,
            canvasSize,
            orientation,
        )

    drawEdgeElectricHighlight(variationFactor, randomSegments, fromPos, toPos, highlight.pulse, colors)
}

private fun DrawScope.drawEdgeElectricHighlight(
    variationFactor: Float,
    randomSegments: Int,
    fromPos: Offset,
    toPos: Offset,
    pulse: Boolean,
    colors: BoardColors,
) {
    val pulseFactor =
        if (pulse) {
            val pulseTime = Clock.System.now().toEpochMilliseconds() % 1000L / 1000f
            (0.8f + 0.2f * sin(pulseTime * 2 * PI).toFloat())
        } else {
            1f
        }

    val lightningPoints = calculateLightningPoints(fromPos, toPos, randomSegments, variationFactor)
    // Build path once; the original called createPathFromPoints on every drawPath pass.
    val path = createPathFromPoints(lightningPoints)

    // (color, strokeWidth, alpha) — outer halo -> bright core
    listOf(
        Triple(colors.highlightEdge3Color, 32f, 0.1f),
        Triple(colors.highlightEdge3Color, 24f, 0.3f),
        Triple(colors.highlightEdge2Color, 12f, 0.3f),
        Triple(colors.highlightEdge2Color, 3f, 0.6f),
        Triple(colors.highlightEdge2Color, 4f, 0.8f),
    ).forEach { (color, width, alpha) ->
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = width * pulseFactor,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            alpha = alpha,
        )
    }

    // (color, centre, radius, alpha) — endpoint glow dots
    data class Dot(
        val color: Color,
        val center: Offset,
        val radius: Float,
        val alpha: Float,
    )
    listOf(
        Dot(colors.highlightEdge2Color, fromPos, 18f, 0.1f),
        Dot(colors.highlightEdge2Color, toPos, 12f, 0.3f),
        Dot(colors.highlightEdge3Color, toPos, 32f, 0.2f),
    ).forEach { (color, center, radius, alpha) ->
        drawCircle(
            color = color,
            center = center,
            radius = radius * pulseFactor,
            alpha = alpha,
        )
    }
}

private fun calculateLightningPoints(
    fromPos: Offset,
    toPos: Offset,
    segments: Int,
    variationFactor: Float,
): List<Offset> {
    val points = mutableListOf<Offset>()
    points.add(fromPos)

    val dx = toPos.x - fromPos.x
    val dy = toPos.y - fromPos.y

    // Calcular la distancia total
    val distance = sqrt(dx * dx + dy * dy)
    if (distance == 0f) return listOf(fromPos, toPos)

    // Vector unitario en la dirección de la línea
    val unitX = dx / distance
    val unitY = dy / distance

    // Vector perpendicular
    val perpX = -unitY
    val perpY = unitX

    // Asegurar mínimo de segmentos
    val actualSegments = maxOf(2, segments)
    val segmentLength = distance / actualSegments

    // Factor de desviación base
    val baseMaxDeviation = distance * 0.2f
    val maxDeviation = baseMaxDeviation * variationFactor

    // Usar una semilla determinística basada en las posiciones para consistencia
    val seed = (fromPos.x * 1000 + fromPos.y * 100 + toPos.x * 10 + toPos.y).toLong()
    val random = Random(seed)

    for (i in 1 until actualSegments) {
        val progress = i.toFloat() / actualSegments
        val baseX = fromPos.x + unitX * segmentLength * i
        val baseY = fromPos.y + unitY * segmentLength * i

        // Patrón de zigzag más orgánico
        val zigZagPattern =
            when (i % 4) {
                0 -> 1.0f
                1 -> -0.8f
                2 -> 0.6f
                else -> -0.4f
            }

        // Variación aleatoria
        val randomVariation = (random.nextFloat() - 0.5f) * 2.0f
        val combinedVariation = zigZagPattern + randomVariation * 0.3f

        // Desviación final con progresión no lineal
        val progressionFactor = sin(progress * PI).toFloat()
        val finalDeviation = maxDeviation * combinedVariation * progressionFactor

        val pointX = baseX + perpX * finalDeviation
        val pointY = baseY + perpY * finalDeviation

        points.add(Offset(pointX, pointY))
    }

    points.add(toPos)
    return points
}

private fun createPathFromPoints(points: List<Offset>): Path {
    val path = Path()
    if (points.isNotEmpty()) {
        path.moveTo(points[0].x, points[0].y)

        // Usar líneas rectas entre puntos para mantener el aspecto de rayo eléctrico
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
    }
    return path
}

private const val edgesStrokeFactor = 0.008f
private const val edgesStrokeAlpha = 0.8f
private const val fireballStrokeFactor = 0.01f

private const val arcStrokeFactor = 0.28f
private const val arcFadeFactor = 0.15f
private const val arcConvexRatioFactor = 1.8f
private const val arcConcaveRatioFactor = 0.15f
private const val arcBaseRadiusFactor = 0.04f

private const val arrowPulseAlpha = 0.9f
private const val arrowStokeFactor = 0.03f
private const val arrowSizeFactor = 1.6f
private const val arrowWidthFactor = 1.6f
private const val arrowStartPadFactor = 0.9f
private const val arrowEndPadFactor = 1.15f