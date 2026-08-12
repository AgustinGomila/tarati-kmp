package com.agustin.tarati.features.game6

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Overlay con un indicador por asiento, ubicado **afuera de su base** (empujado desde el punto
 * medio de las puntas E hacia el exterior). Muestra el color del jugador, si es Humano o IA, y su
 * cantidad de piezas en el tablero; resalta el turno actual y atenúa a los retirados.
 */
@Composable
internal fun BaseIndicators(
    state: MpGameState,
    seatIsAI: List<Boolean>,
    positions: Map<Vertex, Offset>,
) {
    val center = positions[Board25.A1] ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        state.seats.forEachIndexed { index, seat ->
            val base = Board25.baseById(seat.baseId)
            val e0 = positions[base.eTips[0]] ?: return@forEachIndexed
            val e1 = positions[base.eTips[1]] ?: return@forEachIndexed
            val eMid = (e0 + e1) / 2f
            val dir = eMid - center
            val len = hypot(dir.x, dir.y)
            // `anchor` es el punto (afuera de la base) donde apoya el **borde interior** de la cápsula;
            // `outward` es la dirección hacia el exterior por la que ésta se desplaza para no invadir
            // los vértices (ver [SeatIndicator]).
            val anchor: Offset
            val outward: Offset
            if (len == 0f) {
                anchor = eMid
                outward = Offset.Zero
            } else {
                val r = dir / len          // radial unitario (centro → base)
                val layer = len / 4f        // la punta E está a 4 capas del centro
                // Colocación en el margen más holgado según la orientación de la base:
                //  N/S (verticales) → al costado; NW/NE (arriba) → arriba; SW/SE (abajo) → abajo.
                val (push, dist) = when {
                    abs(r.x) < 0.5f -> Offset(1f, 0f) to layer * 1.25f
                    r.y < 0f -> Offset(0f, -1f) to layer
                    else -> Offset(0f, 1f) to layer
                }
                anchor = eMid + push * dist
                outward = push
            }
            SeatIndicator(
                color = seat.color,
                isAI = seatIsAI.getOrElse(index) { false },
                count = state.pieceCount(seat.color),
                retired = seat.status == SeatStatus.RETIRED,
                isCurrent = index == state.currentSeatIndex && !state.isGameOver,
                anchor = anchor,
                outward = outward,
            )
        }
    }
}

@Composable
private fun SeatIndicator(
    color: PlayerColor,
    isAI: Boolean,
    count: Int,
    retired: Boolean,
    isCurrent: Boolean,
    anchor: Offset,
    outward: Offset,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    SeatChip(
        color = color,
        isAI = isAI,
        count = count,
        retired = retired,
        isCurrent = isCurrent,
        modifier = Modifier
            .absoluteOffset {
                // Se apoya el **borde interior** de la cápsula sobre `anchor` (en lugar de centrarla),
                // corriéndola media dimensión en la dirección `outward`. Así la cápsula queda entera
                // afuera de la base y no se superpone con los vértices del tablero, sin depender de su
                // ancho dinámico (texto del contador).
                val halfW = boxSize.width / 2f
                val halfH = boxSize.height / 2f
                IntOffset(
                    (anchor.x - halfW + outward.x * halfW).roundToInt(),
                    (anchor.y - halfH + outward.y * halfH).roundToInt(),
                )
            }
            .onSizeChanged { boxSize = it },
    )
}

/**
 * Cápsula de un asiento: círculo de color del jugador + ícono Humano/IA + contador de piezas. Resalta
 * (borde grueso del color) al asiento en turno y se atenúa si el jugador está retirado. La usan tanto
 * los indicadores in-board ([SeatIndicator], con posicionamiento absoluto) como la leyenda fuera del
 * tablero del detalle en portrait (`MpSeatLegend`), pasando su propio [modifier] de layout.
 */
@Composable
internal fun SeatChip(
    color: PlayerColor,
    isAI: Boolean,
    count: Int,
    retired: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = PlayerPalette.fill(color)
    val borderCol = PlayerPalette.border(color)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .alpha(if (retired) 0.4f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) fill else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2f
            drawCircle(fill, radius = r, center = Offset(r, r))
            drawCircle(borderCol, radius = r, center = Offset(r, r), style = Stroke(width = r * 0.2f))
        }
        Icon(
            imageVector = if (isAI) TaratiIcons.SmartToy else TaratiIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun VertexLabels(
    positions: Map<Vertex, Offset>,
    textSizePx: Float,
    color: Color,
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        positions.forEach { (vertex, pos) ->
            val x = with(density) { (pos.x - textSizePx * 1.2f).toDp() }
            val y = with(density) { (pos.y - textSizePx * 1.2f).toDp() }
            val fontSize = with(density) { textSizePx.toSp() }
            Text(
                text = vertex.name,
                color = color,
                fontSize = fontSize,
                lineHeight = fontSize,
                modifier = Modifier.absoluteOffset(x = x, y = y),
            )
        }
    }
}
