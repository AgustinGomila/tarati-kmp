package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.getBoardRect
import com.agustin.tarati.core.domain.game.board.getBoardScale
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.time.ClockState
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import com.agustin.tarati.ui.components.game.draw.pieces.CenterMotif
import com.agustin.tarati.ui.components.game.draw.pieces.CobColorScheme
import com.agustin.tarati.ui.components.game.draw.pieces.CobShape
import com.agustin.tarati.ui.components.game.draw.pieces.PieceType
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypeManager
import com.agustin.tarati.ui.components.game.draw.pieces.ShapeColorScheme
import com.agustin.tarati.ui.components.game.draw.pieces.drawCobWithBorder
import com.agustin.tarati.ui.components.game.draw.pieces.drawMorphCob
import com.agustin.tarati.ui.theme.BoardColors
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import kotlin.math.roundToInt

/**
 * Badge circular e indicador de reloj para cada bando, en las esquinas del fondo del tablero.
 *
 * Cada jugador tiene dos elementos enfrentados en lados opuestos del tablero:
 * un badge con icono (Person/SmartToy) y, si hay control de tiempo activo,
 * una cápsula con el tiempo restante en los colores de la pieza del bando.
 *
 * El badge marca la esquina del jugador (superior-izquierda y su opuesta
 * inferior-derecha) y su reloj se apila junto a él hacia el centro vertical:
 * debajo del badge superior y encima del badge inferior. Así los relojes quedan
 * fuera de las esquinas inferiores, donde flota la barra de controles online.
 *
 * Sin control de tiempo ([TimeControlMode.Unlimited]) solo se dibujan los badges.
 */
@Composable
fun PlayerCornerIndicators(
    modifier: Modifier = Modifier,
    boardOrientation: BoardOrientation,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    clockState: ClockState = ClockState.initial(TimeControlMode.Unlimited),
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() },
    ) {
        if (containerSize == Size.Zero) return@Box

        // ── Rect del fondo visual del tablero ─────────────────────────────────
        val vertexRect = getBoardRect(GameBoard.vertices, containerSize, boardOrientation)
        val bgMarginPx = minOf(containerSize.width, containerSize.height) * BG_MARGIN_FACTOR

        val bgTopLeft = Offset(
            x = vertexRect.topLeft.x - bgMarginPx,
            y = vertexRect.topLeft.y - bgMarginPx,
        )
        val bgSize = Size(
            width = vertexRect.size.width + 2 * bgMarginPx,
            height = vertexRect.size.height + 2 * bgMarginPx,
        )

        // ── Tamaños del badge y la cápsula ────────────────────────────────────
        val insetPx = with(density) { INSET_DP.dp.roundToPx() }
        val pieceRadius = getBoardScale(containerSize, boardOrientation) * PIECE_SIZE_FACTOR
        val badgePx = (pieceRadius * 2f).roundToInt()

        val capsuleHeightPx = (badgePx * CAPSULE_HEIGHT_RATIO).roundToInt()
        val capsuleWidthPx = (capsuleHeightPx * CAPSULE_WIDTH_TO_HEIGHT_RATIO).roundToInt()

        // Separación entre el badge y la cápsula del reloj al apilarse.
        val clockBadgeGapPx = with(density) { CLOCK_BADGE_GAP_DP.dp.roundToPx() }

        // ── Asignación de colores a cada esquina ──────────────────────────────
        val topLeftColor = topLeftCobColor(boardOrientation)
        val bottomRightColor = if (topLeftColor == WHITE) BLACK else WHITE

        val topLeftIsAI = if (topLeftColor == WHITE) whiteIsAI else blackIsAI
        val bottomRightIsAI = if (bottomRightColor == WHITE) whiteIsAI else blackIsAI

        // ── Coordenadas (4 esquinas del bgRect) ───────────────────────────────
        val leftX = (bgTopLeft.x + insetPx).roundToInt()
        val topY = (bgTopLeft.y + insetPx).roundToInt()
        val rightXForBadge = (bgTopLeft.x + bgSize.width - badgePx - insetPx).roundToInt()
        val bottomY = (bgTopLeft.y + bgSize.height - badgePx - insetPx).roundToInt()

        val rightXForClock = (bgTopLeft.x + bgSize.width - capsuleWidthPx - insetPx).roundToInt()

        // El reloj se apila con su badge hacia el centro vertical, en cualquier
        // orientación: debajo del badge superior y encima del badge inferior. Así
        // los relojes no caen en las esquinas inferiores, donde flota la barra de
        // controles online, y cada uno queda alineado al borde exterior de su badge.
        val topLeftClockX = leftX
        val topLeftClockY = topY + badgePx + clockBadgeGapPx
        val bottomRightClockX = rightXForClock
        val bottomRightClockY = bottomY - capsuleHeightPx - clockBadgeGapPx

        // ── Badges ────────────────────────────────────────────────────────────
        PlayerBadge(
            cobColor = topLeftColor,
            isAI = topLeftIsAI,
            sizePx = badgePx,
            modifier = Modifier.offset { IntOffset(x = leftX, y = topY) },
        )

        PlayerBadge(
            cobColor = bottomRightColor,
            isAI = bottomRightIsAI,
            sizePx = badgePx,
            modifier = Modifier.offset { IntOffset(x = rightXForBadge, y = bottomY) },
        )

        // ── Cápsulas del reloj (solo si el modo tiene tiempo) ────────────────
        val showClock = clockState.mode !is TimeControlMode.Unlimited
        if (!showClock) return@Box

        val (topLeftRem, topLeftByo, topLeftPer) = playerClockInfo(clockState, topLeftColor)
        val (bottomRightRem, bottomRightByo, bottomRightPer) =
            playerClockInfo(clockState, bottomRightColor)

        PlayerClockCapsule(
            cobColor = topLeftColor,
            remainingMs = topLeftRem,
            inByoyomi = topLeftByo,
            periodsLeft = topLeftPer,
            widthPx = capsuleWidthPx,
            heightPx = capsuleHeightPx,
            textMeasurer = textMeasurer,
            modifier = Modifier.offset { IntOffset(x = topLeftClockX, y = topLeftClockY) },
        )

        PlayerClockCapsule(
            cobColor = bottomRightColor,
            remainingMs = bottomRightRem,
            inByoyomi = bottomRightByo,
            periodsLeft = bottomRightPer,
            widthPx = capsuleWidthPx,
            heightPx = capsuleHeightPx,
            textMeasurer = textMeasurer,
            modifier = Modifier.offset { IntOffset(x = bottomRightClockX, y = bottomRightClockY) },
        )
    }
}

// ── Badge circular ────────────────────────────────────────────────────────────

/**
 * Insignia circular con estilo de pieza (cob).
 *
 * Capas de dibujo (mismas que `drawOrganicCob`):
 * 1. Sombra sutil desplazada hacia abajo.
 * 2. Relleno con el color base del bando.
 * 3. Borde con el color de borde del bando.
 * 4. Ícono con el color del bando contrario (máximo contraste).
 */
@Composable
private fun PlayerBadge(
    cobColor: CobColor,
    isAI: Boolean,
    sizePx: Int,
    modifier: Modifier = Modifier,
) {
    val colors = getBoardColors()
    val iconColor = if (cobColor == WHITE) colors.blackCobColor else colors.whiteCobColor
    val icon = if (isAI) TaratiIcons.SmartToy else TaratiIcons.Person

    val iconPainter = rememberVectorPainter(icon)
    val sizeDp = with(LocalDensity.current) { sizePx.toDp() }

    Box(modifier = modifier.size(sizeDp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawIndicatorPiece(
                position = center,
                radius = radius,
                cobColor = cobColor,
                colors = colors,
            )

            // drawMorphCob ya aplica noise para formas poligonales.
            // Para formas circulares (sides <= 1) lo aplicamos aquí.
            val shape = PieceTypeManager.currentPieceType.shape
            if (shape.sides <= 1) {
                with(NoiseTexture) { applyNoise(shape.createPath(size)) }
            }

            // Centroide — misma fórmula que el Rok en drawMorphCob.
            val pieceShape = PieceTypeManager.currentPieceType.shape
            val normalizedRadius = radius / pieceShape.sizeFrac.coerceAtLeast(1f)
            val centroidPath = pieceShape.computeCentroid(
                cx = normalizedRadius,
                cy = normalizedRadius,
                rx = normalizedRadius * pieceShape.sizeFrac,
                ry = normalizedRadius * pieceShape.sizeFrac,
            )
            val centroidBadge = Offset(
                x = centroidPath.x + (center.x - normalizedRadius),
                y = centroidPath.y + (center.y - normalizedRadius),
            )

            val iconSize = radius * 2f * ICON_TO_BADGE_RATIO
            translate(
                left = centroidBadge.x - iconSize / 2f,
                top = centroidBadge.y - iconSize / 2f,
            ) {
                with(iconPainter) {
                    draw(
                        size = Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(iconColor),
                    )
                }
            }
        }
    }
}

// ── Cápsula del reloj ─────────────────────────────────────────────────────────

/**
 * Cápsula stadium con reloj digital, usando los colores de la pieza del bando:
 * fill = `cobColor`, borde = `cobBorderColor`, texto = color contrario.
 *
 * Colores del texto: normal por debajo de 30 s → amber, por debajo de 10 s → rojo pulsante.
 * Formato: `M:SS` normal, `M:SS.T` en los últimos 10 s, `H:MM:SS` para partidas ≥ 1 h.
 * En byoyomi el texto sube levemente y aparecen puntos de períodos en la mitad inferior.
 */
@Composable
private fun PlayerClockCapsule(
    cobColor: CobColor,
    remainingMs: Long,
    inByoyomi: Boolean,
    periodsLeft: Int,
    widthPx: Int,
    heightPx: Int,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val colors = getBoardColors()
    val density = LocalDensity.current

    val fillColor = if (cobColor == WHITE) colors.whiteCobColor else colors.blackCobColor
    val borderColor =
        if (cobColor == WHITE) colors.whiteCobBorderColor else colors.blackCobBorderColor
    val contentBaseColor =
        if (cobColor == WHITE) colors.blackCobColor else colors.whiteCobColor

    val isCritical = remainingMs < CRITICAL_MS
    val timeColor = timeColorFor(remainingMs, contentBaseColor)

    val infiniteTransition = rememberInfiniteTransition(label = "ClockPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ClockPulseAlpha",
    )

    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val timeText = formatTimeDigital(remainingMs)

    val fontSize = with(density) { (heightPx * FONT_SIZE_RATIO).toSp() }
    val textStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = if (isCritical) timeColor.copy(alpha = pulseAlpha) else timeColor,
        textAlign = TextAlign.Center,
    )
    val textLayout = textMeasurer.measure(timeText, textStyle)

    Box(modifier = modifier.size(widthDp, heightDp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerRadius = size.height / 2f

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )
            with(NoiseTexture) {
                applyNoise(
                    topLeft = Offset(0f, 0f),
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = size.height * BORDER_STROKE_RATIO),
            )

            val textYOffset =
                if (inByoyomi && periodsLeft > 0) -size.height * 0.09f else 0f

            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    x = (size.width - textLayout.size.width) / 2f,
                    y = (size.height - textLayout.size.height) / 2f + textYOffset,
                ),
            )

            if (inByoyomi && periodsLeft > 0) {
                val dotRadius = size.height * BYOYOMI_DOT_RADIUS_RATIO
                val dotGap = size.height * BYOYOMI_DOT_GAP_RATIO
                val unitWidth = dotRadius * 2f + dotGap
                val dotsTotalW = unitWidth * periodsLeft - dotGap
                val dotsY = size.height * 0.78f
                val dotsStartX = size.width / 2f - dotsTotalW / 2f + dotRadius

                repeat(periodsLeft) { i ->
                    drawCircle(
                        color = contentBaseColor,
                        radius = dotRadius,
                        center = Offset(
                            x = dotsStartX + i * unitWidth,
                            y = dotsY,
                        ),
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun playerClockInfo(state: ClockState, color: CobColor): Triple<Long, Boolean, Int> =
    if (color == WHITE) {
        Triple(state.whiteRemainingMs, state.whiteInByoyomi, state.whiteByoyomiPeriodsLeft)
    } else {
        Triple(state.blackRemainingMs, state.blackInByoyomi, state.blackByoyomiPeriodsLeft)
    }

private fun formatTimeDigital(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return when {
        safe < 10_000L -> {
            val tenths = (safe % 1000L) / 100L
            "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
        }

        totalSeconds >= 3600L -> {
            val hours = totalSeconds / 3600L
            val mins = (totalSeconds % 3600L) / 60L
            "$hours:${mins.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }

        else -> "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun timeColorFor(remainingMs: Long, normalColor: Color): Color = when {
    remainingMs < CRITICAL_MS -> Color(0xFFE53935)
    remainingMs < WARNING_MS -> Color(0xFFFFA000)
    else -> normalColor
}

private fun topLeftCobColor(orientation: BoardOrientation): CobColor = when (orientation) {
    BoardOrientation.PORTRAIT_WHITE -> BLACK
    BoardOrientation.LANDSCAPE_WHITE -> WHITE
    BoardOrientation.PORTRAIT_BLACK -> WHITE
    BoardOrientation.LANDSCAPE_BLACK -> BLACK
}

// ─────────────────────────────────────────────────────────────────────────────
// drawIndicatorPiece — reemplazo de drawCobWithBorder en indicadores
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drop-in replacement de [drawCobWithBorder] para indicadores de pieza
 * (esquinas del tablero, historial de movimientos, editor de tablero).
 */
fun DrawScope.drawIndicatorPiece(
    position: Offset,
    radius: Float,
    cobColor: CobColor,
    colors: BoardColors,
    hourOfDay: Float = 12f,
) {
    val pieceType = PieceTypeManager.currentPieceType
    if (pieceType.shape.sides <= 1) {
        val adjustedRadius = radius * pieceType.shape.sizeFrac
        val fillColor = if (cobColor == WHITE) colors.whiteCobColor else colors.blackCobColor
        val borderColor = if (cobColor == WHITE) colors.whiteCobBorderColor else colors.blackCobBorderColor
        drawCobWithBorder(position, adjustedRadius, fillColor, borderColor)
        return
    }

    // ── Polígono sin marca de Rok ──────────────────────────────────────────
    drawMorphCob(
        position = position, radius = radius,
        cobShape = CobShape(pieceType.shape, SchemeNoCenter),
        cobColor = cobColor, boardColors = colors, hourOfDay = hourOfDay
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers privados
// ─────────────────────────────────────────────────────────────────────────────

private val SchemeWithCenter: ShapeColorScheme = CobColorScheme.Default
private val SchemeNoCenter: ShapeColorScheme = ShapeColorScheme { cobColor, boardColors ->
    CobColorScheme.Default.resolve(cobColor, boardColors).copy(center = null)
}

fun cobShapeFor(pieceType: PieceType, cob: Cob): CobShape =
    CobShape(
        shape = pieceType.shape,
        colorScheme = if (cob.isUpgraded) SchemeWithCenter else SchemeNoCenter,
        borderPattern = pieceType.borderPattern,
        // El motivo central solo se activa para Roks (piezas mejoradas).
        // CenterMotif.None garantiza que los Cobs (no mejorados) no muestren centro.
        centerMotif = if (cob.isUpgraded) pieceType.centerMotif else CenterMotif.None,
    )

// ── Constantes ────────────────────────────────────────────────────────────────

/** Factor del margen del fondo del tablero. Debe coincidir con DrawBoard.kt (0.1f). */
private const val BG_MARGIN_FACTOR = 0.1f

/** Distancia en dp desde el borde del fondo hasta el borde exterior del elemento. */
private const val INSET_DP = 8

/** Separación en dp entre el badge y la cápsula del reloj al apilarse. */
private const val CLOCK_BADGE_GAP_DP = 4

private const val PIECE_SIZE_FACTOR = 0.08f
private const val ICON_TO_BADGE_RATIO = 0.4f

/** Altura de la cápsula como fracción del diámetro del badge. */
private const val CAPSULE_HEIGHT_RATIO = 0.55f

/** Ancho de la cápsula como múltiplo de su altura. */
private const val CAPSULE_WIDTH_TO_HEIGHT_RATIO = 2.8f

private const val FONT_SIZE_RATIO = 0.46f

/** Grosor del borde como fracción de la altura de la cápsula. */
private const val BORDER_STROKE_RATIO = 0.13f

private const val BYOYOMI_DOT_RADIUS_RATIO = 0.07f
private const val BYOYOMI_DOT_GAP_RATIO = 0.07f

private const val WARNING_MS = 30_000L
private const val CRITICAL_MS = 10_000L