package com.agustin.tarati.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import com.agustin.tarati.ui.theme.TaratiIcons

private const val HEART = "♥"
private const val HEART_ID = "heart"

/**
 * `Text` que renderiza el corazón (`♥`) como un ícono **dibujado** en lugar de como glifo de fuente.
 *
 * El glifo `♥` (U+2665) no está en el font bundle de Skiko WASM, así que en la web no se ve (queda
 * en blanco). Este composable lo reemplaza inline por [TaratiIcons.Favorite] vía [InlineTextContent],
 * conservando la posición exacta dentro del texto y funcionando igual en todas las plataformas. Es el
 * mismo criterio que `CobColorIndicator` (círculos dibujados) para los emojis ausentes en Skiko.
 *
 * Si el texto no contiene ningún `♥`, delega en un `Text` normal.
 */
@Composable
fun HeartText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    if (!text.contains(HEART)) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }

    val parts = text.split(HEART)
    val annotated = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            append(part)
            if (index < parts.size - 1) appendInlineContent(HEART_ID, HEART)
        }
    }
    val inlineContent = mapOf(
        HEART_ID to InlineTextContent(
            Placeholder(
                width = 1.1.em,
                height = 1.0.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Icon(imageVector = TaratiIcons.Favorite, contentDescription = null, tint = color)
        },
    )

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        inlineContent = inlineContent,
    )
}
