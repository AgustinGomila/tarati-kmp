package com.agustin.tarati.ui.components.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Color de relleno por defecto del lado de Blancas. */
private val DefaultWhite = Color(0xFFF5F5F5)

/** Color de fondo por defecto del lado de Negras. */
private val DefaultBlack = Color(0xFF2B2B2B)

/**
 * Barra de evaluación vertical estilo ajedrez: el lado de Blancas se llena desde
 * abajo en proporción a [winProbWhite] (0..1); el resto queda del color de Negras.
 * El punto medio (50/50) queda marcado con una línea de referencia.
 *
 * El componente consume **solo primitivos** — es agnóstico de la fuente del
 * score. El llamador provee [winProbWhite] (ver
 * [com.agustin.tarati.core.domain.analysis.WinProbability]) y, opcionalmente, un
 * [label] ya formateado ("72%", "+1.5", "Mit").
 *
 * El [modifier] debe fijar el tamaño de la barra (p. ej.
 * `Modifier.width(14.dp).fillMaxHeight()`).
 *
 * @param winProbWhite Proporción del lado de Blancas, en `[0, 1]`.
 * @param label        Texto opcional a superponer (win%, ventaja material o "Mit").
 * @param showLabel    Si se muestra [label] cuando no es `null`.
 * @param whiteColor   Color del lado de Blancas.
 * @param blackColor   Color del lado de Negras.
 * @param cornerRadius Radio de las esquinas redondeadas de la barra.
 */
@Composable
fun EvaluationBar(
    winProbWhite: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    showLabel: Boolean = true,
    whiteColor: Color = DefaultWhite,
    blackColor: Color = DefaultBlack,
    cornerRadius: Dp = 4.dp,
) {
    val animatedWhite by animateFloatAsState(
        targetValue = winProbWhite.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "eval_bar_fill",
    )
    val whiteLeading = animatedWhite >= 0.5f

    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        // La etiqueta se ancla del lado del bando que va ganando (abajo=Blancas, arriba=Negras).
        contentAlignment = if (whiteLeading) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Fondo completo del lado de Negras.
            drawRect(color = blackColor)
            // El lado de Blancas crece desde abajo según su proporción de victoria.
            val whiteHeight = size.height * animatedWhite
            drawRect(
                color = whiteColor,
                topLeft = Offset(0f, size.height - whiteHeight),
                size = Size(size.width, whiteHeight),
            )
            // Marca del punto medio (referencia 50/50).
            val midY = size.height / 2f
            drawLine(
                color = blackColor.copy(alpha = 0.35f),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1f,
            )
        }

        if (showLabel && label != null) {
            // Rotado 90°: el texto corre a lo largo de la barra (sentido vertical), de modo
            // que entra aunque la barra sea muy angosta. El `rotate` no cambia el tamaño de
            // layout, así que el `clip` del Box recorta cualquier sobrante.
            Text(
                text = label,
                color = if (whiteLeading) blackColor else whiteColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .rotate(-90f)
                    .padding(vertical = 2.dp),
            )
        }
    }
}
