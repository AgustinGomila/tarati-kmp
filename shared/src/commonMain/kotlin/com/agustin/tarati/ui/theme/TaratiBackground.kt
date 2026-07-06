package com.agustin.tarati.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalWindowInfo
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.ui.components.game.draw.board.drawBoardBackground
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
//  FONDO DE APLICACIÓN — gradiente + siluetas de tablero + resplandor radial
//
//  Adaptación de drawBackgroundLayer (PlayStoreScreenshotCommon) para pantallas
//  reales. Las posiciones de los tableros traslúcidos varían aleatoriamente entre
//  4 combinaciones simétricas: espejo horizontal, vertical, ambos, o ninguno.
// ══════════════════════════════════════════════════════════════════════════════

private data class BoardOverlaySpec(
    val tiltDeg: Float,
    val centerXFraction: Float,
    val centerYFraction: Float,
    val boardSizeFraction: Float,
    val alpha: Float = 0.16f,
)

// ── Posiciones base (variante sin espejo) ─────────────────────────────────────
//
//   Tablero principal (1.8×): bajo-centro — crea profundidad en la zona inferior.
//   Secundario 1   (1.1×): esquina superior derecha.
//   Secundario 2   (0.75×): esquina superior izquierda.
//
// Estas coordenadas coinciden con la variante BOTTOM de PlayStoreScreenshotCommon.

private val BASE_SPECS = listOf(
    BoardOverlaySpec(tiltDeg = -8f, centerXFraction = 0.38f, centerYFraction = 0.88f, boardSizeFraction = 1.8f),
    BoardOverlaySpec(tiltDeg = 10f, centerXFraction = 0.78f, centerYFraction = 0.38f, boardSizeFraction = 1.1f),
    BoardOverlaySpec(tiltDeg = -10f, centerXFraction = 0.18f, centerYFraction = 0.18f, boardSizeFraction = 0.75f),
)

// Centro Y del resplandor radial en la variante sin espejo vertical.
private const val GLOW_Y_FRACTION = 0.38f

// ── Generación de las 4 variantes ────────────────────────────────────────────
//
// Variante 0: sin espejo         Variante 1: espejo horizontal (X)
// Variante 2: espejo vertical (Y) Variante 3: espejo horizontal + vertical
//
// Espejo X: centerXFraction → 1 − x  |  tiltDeg → −tiltDeg
// Espejo Y: centerYFraction → 1 − y  |  tiltDeg → −tiltDeg
// Espejo XY: ambas transformaciones  |  tiltDeg → +tiltDeg (doble negación)

private data class BackgroundVariant(
    val specs: List<BoardOverlaySpec>,
    val glowYFraction: Float,
)

private fun buildVariant(flipX: Boolean, flipY: Boolean): BackgroundVariant {
    val tiltSign = if (flipX xor flipY) -1f else 1f
    val specs = BASE_SPECS.map { s ->
        s.copy(
            tiltDeg = s.tiltDeg * tiltSign,
            centerXFraction = if (flipX) 1f - s.centerXFraction else s.centerXFraction,
            centerYFraction = if (flipY) 1f - s.centerYFraction else s.centerYFraction,
        )
    }
    val glowY = if (flipY) 1f - GLOW_Y_FRACTION else GLOW_Y_FRACTION
    return BackgroundVariant(specs, glowY)
}

private val BACKGROUND_VARIANTS = listOf(
    buildVariant(flipX = false, flipY = false),
    buildVariant(flipX = true, flipY = false),
    buildVariant(flipX = false, flipY = true),
    buildVariant(flipX = true, flipY = true),
)

// ── Dibujo ────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBoardOverlay(
    boardColors: BoardColors,
    spec: BoardOverlaySpec,
    drawSilhouette: DrawScope.(BoardColors, Size) -> Unit,
) {
    val shortSide = minOf(size.width, size.height)
    val boardSize = shortSide * spec.boardSizeFraction
    val boardLeft = size.width * spec.centerXFraction - boardSize / 2f
    val boardTop = size.height * spec.centerYFraction - boardSize / 2f

    val subtleColors = boardColors.copy(
        boardPatternColor1 = boardColors.boardPatternColor1.copy(alpha = spec.alpha * 1.4f),
        boardPatternColor2 = boardColors.boardPatternColor2.copy(alpha = spec.alpha * 0.8f),
        boardPatternColor3 = boardColors.boardPatternColor3.copy(alpha = 0f),
    )

    clipRect(0f, 0f, size.width, size.height) {
        rotate(
            degrees = spec.tiltDeg,
            pivot = Offset(size.width / 2f, size.height * spec.centerYFraction),
        ) {
            translate(left = boardLeft, top = boardTop) {
                drawSilhouette(subtleColors, Size(boardSize, boardSize))
            }
        }
    }
}

/** Silueta del tablero de Tarati (juego 1) para el fondo decorativo. */
fun DrawScope.drawTaratiSilhouette(colors: BoardColors, canvasSize: Size) {
    drawBoardBackground(
        canvasSize = canvasSize,
        orientation = BoardOrientation.PORTRAIT_WHITE,
        regionsVisible = true,
        perimeterVisible = false,
        bordersVisible = false,
        baseBoardVisible = false,
        noiseVisible = false,
        colors = colors,
    )
}

/**
 * Rota las posiciones de los tableros 90° en sentido horario:
 * (cx, cy) → (cy, 1 − cx). El ángulo decorativo de inclinación no cambia —
 * es pequeño (~8–10°) y tiene sentido en cualquier orientación.
 */
private fun BackgroundVariant.rotateCW(): BackgroundVariant {
    val rotatedSpecs = specs.map { s ->
        s.copy(
            centerXFraction = s.centerYFraction,
            centerYFraction = 1f - s.centerXFraction,
        )
    }
    return BackgroundVariant(specs = rotatedSpecs, glowYFraction = 0.5f)
}

/** Alpha del velo oscuro que se aplica sobre toda la capa de fondo. */
private const val BACKGROUND_DARKEN_ALPHA = 0.28f

/**
 * Capa de fondo decorativa para las pantallas de la app.
 *
 * En landscape las posiciones y ángulos de los tableros traslúcidos se rotan
 * 90° en sentido horario para que el layout coincida con la orientación visual
 * de la pantalla. El glow queda centrado verticalmente.
 */
fun DrawScope.drawAppBackground(
    boardColors: BoardColors,
    variant: Int = 0,
    isLandscape: Boolean = false,
    drawSilhouette: DrawScope.(BoardColors, Size) -> Unit = { c, sz -> drawTaratiSilhouette(c, sz) },
) {
    val base = BACKGROUND_VARIANTS[variant.coerceIn(0, BACKGROUND_VARIANTS.lastIndex)]
    val v = if (isLandscape) base.rotateCW() else base

    drawRect(
        brush = Brush.verticalGradient(
            listOf(boardColors.boardPerimeterColor, boardColors.boardBackground),
        ),
    )

    drawRect(color = Color.Black.copy(alpha = BACKGROUND_DARKEN_ALPHA))

    v.specs.forEach { drawBoardOverlay(boardColors, it, drawSilhouette) }

    // Pase único de textura de grano sobre todo el espacio de fondo. Los tableros
    // traslúcidos no aplican su grano individual (noiseVisible = false): si lo
    // hicieran, las zonas no cubiertas por ningún tablero quedarían sin textura.
    with(NoiseTexture) {
        applyNoise(
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(0f),
            alpha = 0.07f,
        )
    }

    val glowR = minOf(size.width, size.height) * 0.75f
    // En landscape el glow rota: X pasa a ser la fracción glowYFraction del ancho
    // (que era la posición vertical en portrait), Y queda centrado (0.5).
    val glowX = if (isLandscape) size.width * base.glowYFraction else size.width / 2f
    val glowCenter = Offset(glowX, size.height * v.glowYFraction)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.07f),
            1f to Color.Transparent,
            center = glowCenter,
            radius = glowR,
        ),
        radius = glowR,
        center = glowCenter,
    )
}

/**
 * Contenedor que pinta el fondo decorativo de Tarati debajo de su [content].
 *
 * Elige una variante de posicionamiento de los tableros traslúcidos al azar
 * entre las 4 disponibles (espejos horizontal, vertical, ambos o ninguno).
 * La elección se realiza una sola vez por composición con [remember], por lo
 * que es estable durante la sesión, pero puede variar entre navegaciones.
 *
 * El [content] recibe un [BoxScope], por lo que los hijos pueden usar
 * modificadores de alineación como `.align(Alignment.BottomCenter)`.
 *
 * Los composables hijos que deban mostrar el fondo usan
 * `containerColor = Color.Transparent` (Scaffold) o simplemente omiten un
 * modificador `.background(...)` propio.
 *
 * ```kotlin
 * TaratiBackground {
 *     Scaffold(containerColor = Color.Transparent) { ... }
 *     SnackbarHost(modifier = Modifier.align(Alignment.BottomCenter))
 * }
 * ```
 */
@Composable
fun TaratiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AppBackground(
        drawSilhouette = { colors, sz -> drawTaratiSilhouette(colors, sz) },
        modifier = modifier,
        content = content,
    )
}

/**
 * Versión genérica del fondo decorativo: idéntica a [TaratiBackground] salvo por el dibujante de
 * la silueta de tablero ([drawSilhouette]), lo que permite fondos con el tablero de Tarati o con el
 * tablero multijugador sin duplicar el gradiente, el grano ni el resplandor.
 */
@Composable
fun AppBackground(
    drawSilhouette: DrawScope.(BoardColors, Size) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val boardColors = getBoardColors()
    val variant = remember { Random.nextInt(BACKGROUND_VARIANTS.size) }

    val windowInfo = LocalWindowInfo.current
    val isLandscape = windowInfo.containerSize.width > windowInfo.containerSize.height

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAppBackground(boardColors, variant, isLandscape, drawSilhouette)
        }
        content()
    }
}