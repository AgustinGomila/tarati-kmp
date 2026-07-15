@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.agustin.tarati.ui.components.game.draw.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Implementación Skiko de [NoiseTexture] (org.jetbrains.skia) — compartida por
 * Desktop (jvm), Web (wasmJs) e iOS (native).
 *
 * Paridad con androidMain: genera un bitmap de ruido procedural (xorshift, mismo
 * SEED) y lo usa como shader en tile REPEAT con [BlendMode.Overlay]. Android usa
 * `BitmapShader`; aquí se construye un [Image] raster y su [ImageShader] equivalente.
 */
actual object NoiseTexture {
    private const val SIZE = 128
    private const val SEED = 0x93D765DDL
    private val brush: ShaderBrush by lazy { buildBrush() }

    actual fun DrawScope.applyNoise(path: Path, alpha: Float) {
        drawPath(path, brush = brush, alpha = alpha, blendMode = BlendMode.Overlay)
    }

    actual fun DrawScope.applyNoise(center: Offset, radius: Float, alpha: Float) {
        drawCircle(brush = brush, center = center, radius = radius, alpha = alpha, blendMode = BlendMode.Overlay)
    }

    actual fun DrawScope.applyNoise(topLeft: Offset, size: Size, cornerRadius: CornerRadius, alpha: Float) {
        drawRoundRect(
            brush = brush,
            topLeft = topLeft,
            size = size,
            cornerRadius = cornerRadius,
            alpha = alpha,
            blendMode = BlendMode.Overlay,
        )
    }

    private fun buildBrush(): ShaderBrush {
        val bytes = ByteArray(SIZE * SIZE * 4)
        var state = SEED
        for (i in 0 until SIZE * SIZE) {
            state = state xor (state shl 13)
            state = state ushr 7
            state = state xor (state shl 17)
            val v = (state and 0xFFL).toByte()
            val o = i * 4
            bytes[o] = v          // R
            bytes[o + 1] = v      // G
            bytes[o + 2] = v      // B
            bytes[o + 3] = 0xFF.toByte() // A
        }
        val info = ImageInfo(SIZE, SIZE, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        val image = Image.makeRaster(info, bytes, SIZE * 4)
        val shader = ImageShader(image.toComposeImageBitmap(), TileMode.Repeated, TileMode.Repeated)
        return ShaderBrush(shader)
    }
}
