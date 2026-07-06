package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.VisualPositionCache
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.ui.components.game.draw.pieces.ArrowTipStyle
import com.agustin.tarati.ui.components.game.draw.pieces.drawLineWithArrowHead
import com.agustin.tarati.ui.theme.BoardColors
import kotlin.math.hypot

// ── Constantes visuales ───────────────────────────────────────────────────────

/** Alpha de todos los elementos de pre-movimiento. */
private const val PRE_MOVE_ALPHA = 0.45f

private const val ARROW_STROKE_RATIO = 0.30f
private const val ARROW_TIP_SIZE_RATIO = 1.0f

/** Ancho de la base de la punta como fracción de su longitud. */
private const val ARROW_TIP_WIDTH_RATIO = 0.75f

private const val TARGET_DOT_RADIUS_RATIO = 0.45f

/** Acortamiento del extremo origen: el trazo arranca en el borde visual de la pieza. */
private const val ARROW_START_PADDING_RATIO = 0.9f

/**
 * Acortamiento del extremo destino. Debe superar [ARROW_TIP_SIZE_RATIO] para que
 * la punta no llegue al centro del vértice.
 */
private const val ARROW_END_PADDING_RATIO = 1.15f

private const val PULSE_PERIOD_MS = 1200L
private const val PULSE_ALPHA_MIN = 0.30f
private const val PULSE_ALPHA_MAX = 0.65f
private const val HALO_STROKE_RATIO = 0.22f
private const val HALO_RADIUS_RATIO = 1.45f

// ── Entry points ──────────────────────────────────────────────────────────────

/**
 * Dibuja el estado de pre-selección: halo pulsante sobre la pieza elegida
 * y dots sobre los destinos válidos. No-op si [preMoveFromVertex] es null.
 *
 * Núcleo compartido basado en una función de posición ([positionOf]) para que lo reutilicen
 * tanto el tablero de Tarati (vía la sobrecarga de [VisualPositionCache]) como el del juego
 * multijugador (`Board25`, con su `Map<Vertex, Offset>`). El aspecto visual es idéntico.
 */
fun DrawScope.drawPreMoveSelection(
    preMoveFromVertex: Vertex?,
    preMoveValidTargets: Collection<Vertex>,
    positionOf: (Vertex) -> Offset,
    pieceRadius: Float,
    colors: BoardColors,
    tickMs: Long,
) {
    if (preMoveFromVertex == null) return

    val fromPos = positionOf(preMoveFromVertex)
    val accent = colors.neutralColor

    // Dots sobre targets válidos — debajo del halo para evitar solapes.
    preMoveValidTargets.forEach { target ->
        val targetPos = positionOf(target)
        drawCircle(
            color = accent,
            center = targetPos,
            radius = pieceRadius * TARGET_DOT_RADIUS_RATIO,
            alpha = PRE_MOVE_ALPHA,
        )
        drawCircle(
            color = accent,
            center = targetPos,
            radius = pieceRadius * TARGET_DOT_RADIUS_RATIO,
            style = Stroke(width = pieceRadius * 0.08f),
            alpha = (PRE_MOVE_ALPHA + 0.2f).coerceAtMost(1f),
        )
    }

    // Halo pulsante sobre la pieza pre-seleccionada.
    val phase = (tickMs % PULSE_PERIOD_MS).toFloat() / PULSE_PERIOD_MS
    val pulse = pulseAlpha(phase)
    drawCircle(
        color = accent,
        center = fromPos,
        radius = pieceRadius * HALO_RADIUS_RATIO,
        style = Stroke(width = pieceRadius * HALO_STROKE_RATIO),
        alpha = pulse,
    )
}

/**
 * Dibuja la flecha del pre-movimiento confirmado (de [from] a [to]). No-op si alguno es null.
 *
 * Núcleo compartido basado en [positionOf] (ver [drawPreMoveSelection]); los vértices se pasan
 * sueltos para no acoplar el tipo de movimiento (Tarati `Move` vs. `game6` `MpMove`).
 */
fun DrawScope.drawPreMoveArrow(
    from: Vertex?,
    to: Vertex?,
    positionOf: (Vertex) -> Offset,
    pieceRadius: Float,
    colors: BoardColors,
) {
    if (from == null || to == null) return

    val fromPos = positionOf(from)
    val toPos = positionOf(to)
    val (start, end) = shortenEndpoints(
        from = fromPos,
        to = toPos,
        startPadding = pieceRadius * ARROW_START_PADDING_RATIO,
        endPadding = pieceRadius * ARROW_END_PADDING_RATIO,
    )

    drawLineWithArrowHead(
        start = start,
        end = end,
        brush = SolidColor(colors.neutralColor),
        strokeWidth = pieceRadius * ARROW_STROKE_RATIO,
        arrowSize = pieceRadius * ARROW_TIP_SIZE_RATIO,
        arrowWidth = pieceRadius * ARROW_TIP_SIZE_RATIO * ARROW_TIP_WIDTH_RATIO,
        arrowStyle = ArrowTipStyle.ARROW,
        alpha = PRE_MOVE_ALPHA,
    )
}

// ── Sobrecargas de Tarati (VisualPositionCache / Move) ──────────────────────────
//
// Preservan las firmas que usa el tablero de Tarati y delegan en el núcleo compartido, de modo que
// el juego publicado no cambia y el dibujo del pre-move es el mismo para single y multijugador.

/** @see drawPreMoveSelection */
fun DrawScope.drawPreMoveSelection(
    preMoveFromVertex: Vertex?,
    preMoveValidTargets: List<Vertex>,
    positionCache: VisualPositionCache,
    pieceRadius: Float,
    colors: BoardColors,
    tickMs: Long,
): Unit = drawPreMoveSelection(
    preMoveFromVertex = preMoveFromVertex,
    preMoveValidTargets = preMoveValidTargets,
    positionOf = positionCache::get,
    pieceRadius = pieceRadius,
    colors = colors,
    tickMs = tickMs,
)

/** @see drawPreMoveArrow */
fun DrawScope.drawPreMoveArrow(
    pendingPreMove: Move?,
    positionCache: VisualPositionCache,
    pieceRadius: Float,
    colors: BoardColors,
): Unit = drawPreMoveArrow(
    from = pendingPreMove?.from,
    to = pendingPreMove?.to,
    positionOf = positionCache::get,
    pieceRadius = pieceRadius,
    colors = colors,
)

// ── Helpers privados ──────────────────────────────────────────────────────────

/** Pulso triangular: phase 0 → min, 0.5 → max, 1 → min. */
private fun pulseAlpha(phase: Float): Float {
    val triangle = 1f - kotlin.math.abs(0.5f - phase) * 2f
    return PULSE_ALPHA_MIN + (PULSE_ALPHA_MAX - PULSE_ALPHA_MIN) * triangle
}

/** Recorta los extremos del segmento con paddings independientes para origen y destino. */
private fun shortenEndpoints(
    from: Offset,
    to: Offset,
    startPadding: Float,
    endPadding: Float,
): Pair<Offset, Offset> {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)

    if (length <= startPadding + endPadding + 1f) return from to to

    val ux = dx / length
    val uy = dy / length
    return Offset(from.x + ux * startPadding, from.y + uy * startPadding) to
            Offset(to.x - ux * endPadding, to.y - uy * endPadding)
}