package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder

/**
 * Implementación Skiko de [drawMorphFlipShadow] (org.jetbrains.skia) — compartida por
 * Desktop (jvm), Web (wasmJs) e iOS (native).
 *
 * Paridad con androidMain: transformación afín direccional + blur gaussiano de
 * umbra y penumbra. La [MorphFlipShadowParams.transformMatrix] es un FloatArray(9)
 * row-major idéntico al formato de `android.graphics.Matrix`, directamente compatible
 * con [Matrix33] de Skia (mismo orden: scaleX, skewX, transX, skewY, scaleY, transY,
 * persp0..2).
 *
 * ## Capas de renderizado
 * 1. **Umbra** (sombra principal): blur pequeño, alpha base
 * 2. **Penumbra** (halo exterior): blur grande, alpha menor (solo si showPenumbra)
 *
 * ## Conversión BlurRadius → Sigma
 * `sigma = radius / 3f` (ver [drawFlipShadow]); `coerceAtLeast(0.1f)` evita sigma=0.
 */
actual fun DrawScope.drawMorphFlipShadow(params: MorphFlipShadowParams) {
    val matrix = Matrix33(*params.transformMatrix)

    // Copia del path para no mutar el backing del Path de Compose original.
    val shadowSkia = PathBuilder()
        .addPath(params.shadowPath.asSkiaPath(), matrix)
        .detach()

    fun skiaPaint(alpha: Float, blurRadius: Float): Paint = Paint().apply {
        isAntiAlias = true
        color = params.shadowColor.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        maskFilter = MaskFilter.makeBlur(
            mode = FilterBlurMode.NORMAL,
            sigma = (blurRadius / 3f).coerceAtLeast(0.1f),
        )
    }

    translate(left = params.position.x - params.radius, top = params.position.y - params.radius) {
        drawContext.canvas.skiaCanvas.apply {
            // Capa 1: Umbra (sombra principal)
            drawPath(shadowSkia, skiaPaint(alpha = params.umbraAlpha, blurRadius = params.umbraBlur))

            // Capa 2: Penumbra (halo exterior, solo visible cerca del canto)
            if (params.showPenumbra) {
                drawPath(shadowSkia, skiaPaint(alpha = params.penumbraAlpha, blurRadius = params.penumbraBlur))
            }
        }
    }
}
