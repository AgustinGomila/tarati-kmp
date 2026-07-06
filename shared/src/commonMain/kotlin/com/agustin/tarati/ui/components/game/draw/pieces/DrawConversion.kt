package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.ui.components.game.animation.AnimatedCob
import com.agustin.tarati.ui.components.game.draw.board.LightOfDay
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import com.agustin.tarati.ui.theme.BoardColors
import kotlin.math.PI
import kotlin.math.sin

// ── Conversiones (FROM_CENTER / FROM_BORDER) ──────────────────────────────────

internal fun DrawScope.drawConversionFromCenter(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    hourOfDay: Float,
    lightOfDay: LightOfDay,
    waveColor: Color,
    pieceColors: PieceColor,
    colors: BoardColors,
) {
    val cob = animatedCob.cob
    val targetColors = getPieceColors(Cob(cob.color.opponent, cob.isUpgraded), colors)
    drawConversionFromCenter(
        position = position,
        radius = radius,
        conversionProgress = animatedCob.conversionProgress,
        hourOfDay = hourOfDay,
        lightOfDay = lightOfDay,
        waveColor = waveColor,
        sourceColors = pieceColors,
        targetColors = targetColors,
        colors = colors,
    )
}

/**
 * Conversión "desde el centro" con colores de origen y destino **explícitos** (no derivados de
 * `CobColor.opponent`), para reutilizar la misma transformación en tableros con más de dos colores
 * (p. ej. el juego multijugador). El [conversionProgress] va de 0 (pieza original) a 1 (color
 * convertido).
 */
internal fun DrawScope.drawConversionFromCenter(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    hourOfDay: Float,
    lightOfDay: LightOfDay,
    waveColor: Color,
    sourceColors: PieceColor,
    targetColors: PieceColor,
    colors: BoardColors,
) {
    // 1. Base completa (color de origen) sin textura propia.
    drawOrganicCob(position, radius, hourOfDay, lightOfDay, sourceColors, colors, withTexture = false)

    // 2. Nuevo color expandiéndose desde el centro.
    if (conversionProgress > 0f) {
        val expansionRadius = radius * conversionProgress
        val organicTargetColor = createOrganicColor(targetColors, hourOfDay, colors)

        data class CircleSpec(
            val color: Color,
            val style: DrawStyle = Fill,
        )

        listOf(
            CircleSpec(organicTargetColor),
            CircleSpec(
                targetColors.borderColor,
                Stroke(width = radius * 0.3f * minOf(1f, conversionProgress * 1.5f)),
            ),
        ).forEach { spec ->
            drawCircle(color = spec.color, center = position, radius = expansionRadius, style = spec.style)
        }
    }

    // 3. Textura única sobre base + expansión.
    with(NoiseTexture) { applyNoise(position, radius) }

    // 4. Onda durante la expansión.
    shockWaveEffect(conversionProgress, position, radius, waveColor)
}

internal fun DrawScope.drawConversionFromBorder(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    waveColor: Color,
    hourOfDay: Float,
    colors: BoardColors,
) {
    val conversionProgress = animatedCob.conversionProgress
    val cob = animatedCob.cob
    val targetColor = cob.color.opponent

    // 1. Color objetivo como base completa.
    val targetColors = getPieceColors(Cob(targetColor, cob.isUpgraded), colors)
    val organicTargetColor = createOrganicColor(targetColors, hourOfDay, colors)
    drawCobWithBorder(position, radius, organicTargetColor, targetColors.borderColor)

    // 2. Pieza original contrayéndose desde el borde.
    if (conversionProgress < 1f) {
        val shrinkingRadius = radius * (1f - conversionProgress)
        val originalColors = getPieceColors(cob, colors)
        val organicOriginalColor = createOrganicColor(originalColors, hourOfDay, colors)
        val shrinkingBorderWidth = radius * 0.3f * (shrinkingRadius / radius)
        drawCobWithBorder(
            position,
            shrinkingRadius,
            organicOriginalColor,
            originalColors.borderColor,
            shrinkingBorderWidth
        )
    }

    // 3. Textura única sobre ambas capas.
    with(NoiseTexture) { applyNoise(position, radius) }

    // 4. Onda durante la expansión.
    shockWaveEffect(conversionProgress, position, radius, waveColor)
}

/**
 * Conversión "desde el borde" con colores de origen y destino **explícitos** (no derivados de
 * `CobColor.opponent`), para reutilizar la transformación en tableros con más de dos colores
 * (p. ej. el multijugador). [conversionProgress] va de 0 (pieza original) a 1 (color convertido).
 */
internal fun DrawScope.drawConversionFromBorder(
    position: Offset,
    radius: Float,
    conversionProgress: Float,
    waveColor: Color,
    hourOfDay: Float,
    sourceColors: PieceColor,
    targetColors: PieceColor,
    colors: BoardColors,
) {
    // 1. Color objetivo como base completa.
    val organicTargetColor = createOrganicColor(targetColors, hourOfDay, colors)
    drawCobWithBorder(position, radius, organicTargetColor, targetColors.borderColor)

    // 2. Pieza original contrayéndose desde el borde.
    if (conversionProgress < 1f) {
        val shrinkingRadius = radius * (1f - conversionProgress)
        val organicOriginalColor = createOrganicColor(sourceColors, hourOfDay, colors)
        val shrinkingBorderWidth = radius * 0.3f * (shrinkingRadius / radius)
        drawCobWithBorder(
            position,
            shrinkingRadius,
            organicOriginalColor,
            sourceColors.borderColor,
            shrinkingBorderWidth,
        )
    }

    // 3. Textura única sobre ambas capas.
    with(NoiseTexture) { applyNoise(position, radius) }

    // 4. Onda durante la expansión.
    shockWaveEffect(conversionProgress, position, radius, waveColor)
}

// ── Upgrades (FROM_CENTER / FROM_BORDER) ─────────────────────────────────────

internal fun DrawScope.drawUpgradeFromCenter(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    colors: BoardColors,
) {
    val upgradeProgress = animatedCob.upgradeProgress
    val animationColors = getAnimationColors(animatedCob.cob, colors)
    val bounceFactor = calculateBounce(upgradeProgress)

    drawCircle(color = animationColors.upgradeColor, center = position, radius = radius * 0.2f * bounceFactor)

    if (upgradeProgress < 0.8f) {
        val waveProgress = upgradeProgress / 0.8f
        val waveRadius = radius * (0.3f + waveProgress * 0.7f)
        val waveAlpha = (1f - waveProgress) * 0.4f
        drawCircle(
            color = animationColors.upgradeColor.copy(alpha = waveAlpha),
            center = position,
            radius = waveRadius,
            style = Stroke(width = radius * 0.05f),
        )
    }

    shockWaveEffect(upgradeProgress, position, radius, animationColors.waveColor)
}

internal fun DrawScope.drawUpgradeFromBorder(
    position: Offset,
    radius: Float,
    animatedCob: AnimatedCob,
    colors: BoardColors,
) {
    val upgradeProgress = animatedCob.upgradeProgress
    val animationColors = getAnimationColors(animatedCob.cob, colors)
    val borderProgress = upgradeProgress.coerceIn(0f, 1f)
    val shrinkingRadius = radius * (1f - borderProgress * 0.8f)

    drawCircle(
        color = animationColors.upgradeColor,
        center = position,
        radius = shrinkingRadius,
        style = Stroke(width = radius * 0.15f * borderProgress),
    )

    if (upgradeProgress > 0.7f) {
        val centerProgress = (upgradeProgress - 0.7f) / 0.3f
        drawCircle(
            color = animationColors.upgradeColor,
            center = position,
            radius = radius * 0.2f * calculateBounce(centerProgress),
        )
    }

    shockWaveEffect(upgradeProgress, position, radius, animationColors.waveColor)
}

// ── Helpers privados ──────────────────────────────────────────────────────────

private fun DrawScope.shockWaveEffect(
    progress: Float,
    position: Offset,
    radius: Float,
    color: Color,
) {
    if (progress > 0.5f && progress < 0.9f) {
        val waveProgress = (progress - 0.5f) / 0.4f
        drawCircle(
            color = color.copy(alpha = (1f - waveProgress) * 0.6f),
            center = position,
            radius = radius * (1.1f + waveProgress * 0.4f),
            style = Stroke(width = radius * 0.08f),
        )
    }
}

private fun calculateBounce(progress: Float): Float =
    when {
        progress < 0.3f -> progress / 0.3f
        progress < 0.6f -> 1f + 0.4f * sin((progress - 0.3f) * 3f * PI.toFloat())
        progress < 0.8f -> 0.95f + 0.2f * sin((progress - 0.6f) * 5f * PI.toFloat())
        else -> 1f + 0.1f * sin((progress - 0.8f) * 10f * PI.toFloat())
    }.coerceAtLeast(0.1f)

private fun getAnimationColors(cob: Cob, colors: BoardColors): AnimationColors =
    when (cob.color) {
        CobColor.WHITE -> AnimationColors(
            upgradeColor = colors.blackCobColor,
            waveColor = colors.whiteUpgradingWaveColor,
        )

        CobColor.BLACK -> AnimationColors(
            upgradeColor = colors.whiteCobColor,
            waveColor = colors.blackUpgradingWaveColor,
        )
    }

internal data class AnimationColors(
    val upgradeColor: Color,
    val waveColor: Color,
)