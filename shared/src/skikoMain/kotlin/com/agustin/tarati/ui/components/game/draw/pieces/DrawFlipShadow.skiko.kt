package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import com.agustin.tarati.ui.components.game.draw.board.LightOfDay
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Implementación Skiko de [drawFlipShadow] (org.jetbrains.skia) — compartida por
 * Desktop (jvm), Web (wasmJs) e iOS (native).
 *
 * ## Diferencias respecto a androidMain
 *
 * | Android                                      | Skiko                                                  |
 * |----------------------------------------------|--------------------------------------------------------|
 * | `android.graphics.Paint`                     | `org.jetbrains.skia.Paint`                             |
 * | `android.graphics.BlurMaskFilter`            | `org.jetbrains.skia.MaskFilter.makeBlur(...)`          |
 * | `paint.alpha = 0..255`                       | alpha incluido en `paint.color` (ARGB Int)             |
 * | `canvas.drawOval(l,t,r,b, paint)`            | `canvas.drawOval(Rect.makeLTRB(l,t,r,b), paint)`       |
 *
 * ## Conversión BlurRadius → Sigma
 * En Android, `BlurMaskFilter(radius, NORMAL)` usa el radio directamente.
 * En Skiko, `MaskFilter.makeBlur(NORMAL, sigma)` recibe sigma gaussiano.
 * La equivalencia aproximada es `sigma = radius / 3f` (la desviación estándar
 * de la gaussiana es ~1/3 del radio visible). Para radios muy pequeños se usa
 * `coerceAtLeast(0.1f)` para evitar sigma=0 que anularía el blur.
 *
 * Los cálculos geométricos (posición, tamaño, ángulo) son idénticos a androidMain.
 */
actual fun DrawScope.drawFlipShadow(
    position: Offset,
    radius: Float,
    sinA: Float,
    lightOfDay: LightOfDay,
    cosFlip: Float,
    sinFlip: Float,
    shadowColor: Color,
) {
    val distMultiplier = 1f + sinA * 2.8f
    val sRy = radius * 1.2f
    val minSRx = radius * COIN_EDGE_THICKNESS * 0.8f

    val lightLen = sqrt(
        lightOfDay.shadowOffsetX * lightOfDay.shadowOffsetX +
                lightOfDay.shadowOffsetY * lightOfDay.shadowOffsetY
    ).coerceAtLeast(0.001f)
    val lxLocal = (lightOfDay.shadowOffsetX * cosFlip + lightOfDay.shadowOffsetY * sinFlip) / lightLen
    val lyLocal = (-lightOfDay.shadowOffsetX * sinFlip + lightOfDay.shadowOffsetY * cosFlip) / lightLen

    val sRxAtEdge = minSRx + (radius - minSRx) * abs(lxLocal)
    val sRx = ((1f - sinA) * sRy + sinA * sRxAtEdge).coerceAtLeast(minSRx)

    val worldOffX = lightOfDay.shadowOffsetX * distMultiplier
    val worldOffY = lightOfDay.shadowOffsetY * distMultiplier
    val cxFlat = position.x + worldOffX * cosFlip + worldOffY * sinFlip
    val cyFlat = position.y - worldOffX * sinFlip + worldOffY * cosFlip
    val cxEdge = position.x + sRy * lxLocal
    val cyEdge = position.y + sRy * lyLocal

    val cx = (1f - sinA) * cxFlat + sinA * cxEdge
    val cy = (1f - sinA) * cyFlat + sinA * cyEdge

    val shadowOvalAngle = -(atan2(lxLocal.toDouble(), lyLocal.toDouble()) * 180.0 / PI).toFloat()

    val baseAlpha = (255 * 0.30f * lightOfDay.shadowIntensity * (1f - sinA * 0.45f)).toInt()
    val umbraBlur = sRy * (0.01f + sinA * 0.12f)
    val penumbraBlur = sRy * (0.1f + sinA * 0.65f)

    // Skiko: el alpha va embebido en el color ARGB. Se construye el color con alpha
    // aplicado y se pasa a paint.color. sigma = radius/3 es la equivalencia estándar.
    fun skiaPaint(alpha: Int, blurRadius: Float): Paint = Paint().apply {
        isAntiAlias = true
        color = shadowColor.copy(alpha = (alpha / 255f).coerceIn(0f, 1f)).toArgb()
        maskFilter = MaskFilter.makeBlur(
            mode = FilterBlurMode.NORMAL,
            sigma = (blurRadius / 3f).coerceAtLeast(0.1f),
        )
    }

    rotate(degrees = shadowOvalAngle, pivot = Offset(cx, cy)) {
        drawContext.canvas.skiaCanvas.apply {
            // Umbra — óvalo base más opaco
            drawOval(
                r = Rect.makeLTRB(cx - sRx, cy - sRy, cx + sRx, cy + sRy),
                paint = skiaPaint(alpha = (baseAlpha * 0.80f).toInt(), blurRadius = umbraBlur),
            )
            // Penumbra — óvalo ligeramente más grande y difuso
            drawOval(
                r = Rect.makeLTRB(
                    cx - sRx * (1f + sinA * 0.15f),
                    cy - sRy,
                    cx + sRx * (1f + sinA * 0.15f),
                    cy + sRy * (1f + sinA * 0.35f),
                ),
                paint = skiaPaint(alpha = (baseAlpha * sinA * 0.40f).toInt(), blurRadius = penumbraBlur),
            )
        }
    }
}
