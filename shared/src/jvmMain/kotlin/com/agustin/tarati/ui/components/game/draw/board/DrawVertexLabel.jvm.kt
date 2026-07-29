package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine

actual fun DrawScope.drawVertexLabel(
    label: String,
    position: Offset,
    textSize: Float,
    color: Color,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color.toArgb()
        }
        val font = Font(null, textSize)
        canvas.skiaCanvas.drawTextLine(
            line = TextLine.make(label, font),
            x = position.x - textSize * 0.8f,
            y = position.y - textSize * 0.8f,
            paint = paint,
        )
    }
}
