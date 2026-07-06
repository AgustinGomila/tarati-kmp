package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.ui.components.game.animation.AnimatedCob
import com.agustin.tarati.ui.components.game.draw.board.LightOfDay
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import com.agustin.tarati.ui.theme.BoardColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Espesor del canto como fracción del radio. Representa el grosor físico de la ficha.
internal const val COIN_EDGE_THICKNESS = 0.22f

/**
 * Volteo de moneda: la pieza rota 180° revelando el color convertido.
 *
 * El desplazamiento lateral [shiftedPos] compensa el salto del [sideSign] en
 * [drawFlipEdge]: cuando cosA cruza cero, el canto queda centrado en [position].x
 * en ambos lados de la transición, haciéndola imperceptible.
 * El Rok se dibuja internamente para recibir el mismo escalado de perspectiva.
 */
fun DrawScope.drawCoinFlip(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    hourOfDay: Float,
    lightOfDay: LightOfDay,
    colors: BoardColors,
) {
    val cob = animatedCob.cob
    val theta = animatedCob.conversionProgress * PI.toFloat()
    val cosA = cos(theta)
    val sinA = sin(theta)
    val faceScaleX = abs(cosA)
    val showingBack = cosA < 0f

    val frontColors = getPieceColors(cob, colors)
    val backColors = getPieceColors(cob.copy(color = cob.color.opponent), colors)
    val visibleColors = if (showingBack) backColors else frontColors
    val visibleFill = createOrganicColor(visibleColors, hourOfDay, colors)
    val rokColor = if (showingBack) frontColors.baseColor else backColors.baseColor

    val rimW = radius * COIN_EDGE_THICKNESS * sinA
    val sideSign = if (cosA >= 0f) -1f else 1f
    val shiftedPos = Offset(position.x - sideSign * rimW / 2f, position.y)

    val flipAngleDeg = (animatedCob.vertex.hashCode() * 137.508f) % 360f
    val flipRad = (flipAngleDeg * PI / 180.0).toFloat()
    val cosFlip = cos(flipRad)
    val sinFlip = sin(flipRad)

    rotate(degrees = flipAngleDeg, pivot = position) {
        drawFlipShadow(position, radius, sinA, lightOfDay, cosFlip, sinFlip, colors.boardVertexColor)
        if (sinA > 0.01f) {
            drawFlipEdge(shiftedPos, radius, faceScaleX, sinA, cosA, frontColors, backColors)
        }
        if (faceScaleX > 0.01f) {
            drawFlipFace(
                position = shiftedPos,
                radius = radius,
                faceScaleX = faceScaleX,
                fillColor = visibleFill,
                borderColor = visibleColors.borderColor,
                isUpgraded = cob.isUpgraded,
                rokColor = rokColor,
            )
        }
    }
}

/**
 * Volteo de moneda con colores de origen ([sourceColors], cara frontal) y destino ([targetColors],
 * cara trasera) **explícitos**, y semilla de ángulo [flipSeed] (reemplaza `vertex.hashCode()`), para
 * reutilizar el volteo en tableros con más de dos colores (p. ej. el multijugador). [conversionProgress]
 * 0→1 rota 180°. [isUpgraded] agrega el motivo central de Rok (siempre `false` en multijugador).
 */
fun DrawScope.drawCoinFlip(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    hourOfDay: Float,
    lightOfDay: LightOfDay,
    sourceColors: PieceColor,
    targetColors: PieceColor,
    flipSeed: Int,
    colors: BoardColors,
    isUpgraded: Boolean = false,
) {
    val theta = conversionProgress * PI.toFloat()
    val cosA = cos(theta)
    val sinA = sin(theta)
    val faceScaleX = abs(cosA)
    val showingBack = cosA < 0f

    val visibleColors = if (showingBack) targetColors else sourceColors
    val visibleFill = createOrganicColor(visibleColors, hourOfDay, colors)
    val rokColor = if (showingBack) sourceColors.baseColor else targetColors.baseColor

    val rimW = radius * COIN_EDGE_THICKNESS * sinA
    val sideSign = if (cosA >= 0f) -1f else 1f
    val shiftedPos = Offset(position.x - sideSign * rimW / 2f, position.y)

    val flipAngleDeg = (flipSeed * 137.508f) % 360f
    val flipRad = (flipAngleDeg * PI / 180.0).toFloat()
    val cosFlip = cos(flipRad)
    val sinFlip = sin(flipRad)

    rotate(degrees = flipAngleDeg, pivot = position) {
        drawFlipShadow(position, radius, sinA, lightOfDay, cosFlip, sinFlip, colors.boardVertexColor)
        if (sinA > 0.01f) {
            drawFlipEdge(shiftedPos, radius, faceScaleX, sinA, cosA, sourceColors, targetColors)
        }
        if (faceScaleX > 0.01f) {
            drawFlipFace(
                position = shiftedPos,
                radius = radius,
                faceScaleX = faceScaleX,
                fillColor = visibleFill,
                borderColor = visibleColors.borderColor,
                isUpgraded = isUpgraded,
                rokColor = rokColor,
            )
        }
    }
}

/**
 * Sombra oval que se aleja y difumina mientras la pieza se eleva durante el volteo.
 *
 * Implementada con `expect/actual` porque usa blur nativo de la plataforma:
 * - **androidMain**: `android.graphics.BlurMaskFilter` + `android.graphics.Paint` + `nativeCanvas`
 * - **jvmMain** (Desktop): `org.jetbrains.skia.MaskFilter` + `org.jetbrains.skia.Paint` + `nativeCanvas` (Skiko)
 *
 * Los cálculos geométricos (posición, tamaño, ángulo del óvalo) son idénticos en
 * ambas plataformas y están documentados en la implementación Android.
 */
expect fun DrawScope.drawFlipShadow(
    position: Offset,
    radius: Float,
    sinA: Float,
    lightOfDay: LightOfDay,
    cosFlip: Float,
    sinFlip: Float,
    shadowColor: Color,
)

/**
 * Canto de la ficha.
 */
fun DrawScope.drawFlipEdge(
    position: Offset,
    radius: Float,
    faceScaleX: Float,
    sinA: Float,
    cosA: Float,
    frontColors: PieceColor,
    backColors: PieceColor,
) {
    val rimW = (radius * COIN_EDGE_THICKNESS * sinA).coerceAtLeast(0f)
    if (rimW < 0.5f) return

    val borderInset = radius * 0.15f
    val outerR = radius + borderInset
    val edgeColor = blendColors(frontColors.borderColor, backColors.borderColor, 0.5f).copy(alpha = 0.90f)
    val sideSign = if (cosA >= 0f) -1f else 1f

    val k = 4f / 3f
    val bx = position.x + sideSign * rimW
    val innerCtrlX = position.x + sideSign * faceScaleX * outerR * k
    val outerCtrlX = innerCtrlX + sideSign * rimW

    val path = Path().apply {
        moveTo(position.x, position.y - outerR)
        cubicTo(
            innerCtrlX, position.y - outerR,
            innerCtrlX, position.y + outerR,
            position.x, position.y + outerR,
        )
        lineTo(bx, position.y + outerR)
        cubicTo(
            outerCtrlX, position.y + outerR,
            outerCtrlX, position.y - outerR,
            bx, position.y - outerR,
        )
        lineTo(position.x, position.y - outerR)
        close()
    }
    drawPath(path, color = edgeColor)
    with(NoiseTexture) { applyNoise(path, 0.07f) }
}

/**
 * Cara de la ficha como óvalo perspectivado.
 */
fun DrawScope.drawFlipFace(
    position: Offset,
    radius: Float,
    faceScaleX: Float,
    fillColor: Color,
    borderColor: Color,
    isUpgraded: Boolean,
    rokColor: Color,
) {
    data class OvalSpec(val radiusMultiplier: Float, val color: Color, val condition: Boolean = true)

    val ovals = listOf(
        OvalSpec(1.15f, borderColor),
        OvalSpec(0.85f, fillColor),
        OvalSpec(0.20f, rokColor, isUpgraded),
    )

    ovals.filter { it.condition }.forEach { spec ->
        val r = radius * spec.radiusMultiplier
        drawOval(
            color = spec.color,
            topLeft = Offset(position.x - r * faceScaleX, position.y - r),
            size = Size(r * faceScaleX * 2f, r * 2f),
        )
    }

    // El noise debe ocupar el área visible de la cara, que se achata en X por
    // faceScaleX durante el volteo. Un round-rect con corner = semiejes es una
    // elipse exacta; con faceScaleX = 1 (pieza plana) coincide con el círculo previo.
    val noiseRx = radius * faceScaleX
    with(NoiseTexture) {
        applyNoise(
            topLeft = Offset(position.x - noiseRx, position.y - radius),
            size = Size(noiseRx * 2f, radius * 2f),
            cornerRadius = CornerRadius(noiseRx, radius),
        )
    }
}