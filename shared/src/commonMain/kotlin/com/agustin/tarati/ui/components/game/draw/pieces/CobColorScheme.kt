package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.ui.graphics.Color
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.ui.theme.BoardColors

// ─────────────────────────────────────────────────────────────────────────────
// ShapeColors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Colores visuales resueltos para una pieza poligonal.
 *
 * @param fill        Color de relleno del cuerpo.
 * @param border      Color del borde inset (stripe interior al contorno del polígono).
 * @param center      Color de la forma central (polígono pequeño igual al cuerpo).
 *                    `null` = sin forma central.
 * @param lightColor  Color claro para el efecto de color orgánico (opcional).
 * @param shadowColor Color oscuro para el efecto de color orgánico (opcional).
 */
data class ShapeColors(
    val fill: Color,
    val border: Color,
    val center: Color?,
    val lightColor: Color? = null,
    val shadowColor: Color? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// ShapeColorScheme
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resuelve los [ShapeColors] para un [CobColor] y una [BoardColors] de paleta.
 */
fun interface ShapeColorScheme {
    fun resolve(cobColor: CobColor, boardColors: BoardColors): ShapeColors
}

// ─────────────────────────────────────────────────────────────────────────────
// CobColorScheme — schemes predefinidos
// ─────────────────────────────────────────────────────────────────────────────

object CobColorScheme {

    /**
     * Equivalente visual de los Cobs circulares:
     * fill/borde/light/shadow del bando, centro = color del bando contrario.
     */
    val Default: ShapeColorScheme = ShapeColorScheme { cobColor, colors ->
        when (cobColor) {
            CobColor.WHITE -> ShapeColors(
                fill = colors.whiteCobColor,
                border = colors.whiteCobBorderColor,
                center = colors.blackCobColor,
                lightColor = colors.whiteCobLightColor,
                shadowColor = colors.whiteCobShadowColor,
            )

            CobColor.BLACK -> ShapeColors(
                fill = colors.blackCobColor,
                border = colors.blackCobBorderColor,
                center = colors.whiteCobColor,
                lightColor = colors.blackCobLightColor,
                shadowColor = colors.blackCobShadowColor,
            )
        }
    }

    /**
     * Borde de highlight vívido, centro = neutralColor de la paleta.
     */
    val Vivid: ShapeColorScheme = ShapeColorScheme { cobColor, colors ->
        when (cobColor) {
            CobColor.WHITE -> ShapeColors(
                fill = colors.whiteCobColor,
                border = colors.highlightEdge1Color,
                center = colors.neutralColor,
                lightColor = colors.whiteCobLightColor,
                shadowColor = colors.whiteCobShadowColor,
            )

            CobColor.BLACK -> ShapeColors(
                fill = colors.blackCobColor,
                border = colors.highlightEdge2Color,
                center = colors.neutralColor,
                lightColor = colors.blackCobLightColor,
                shadowColor = colors.blackCobShadowColor,
            )
        }
    }

    /**
     * Fill semitransparente, borde estándar, sin forma central.
     */
    val Outlined: ShapeColorScheme = ShapeColorScheme { cobColor, colors ->
        when (cobColor) {
            CobColor.WHITE -> ShapeColors(
                fill = colors.whiteCobColor.copy(alpha = 0.55f),
                border = colors.whiteCobBorderColor,
                center = null,
            )

            CobColor.BLACK -> ShapeColors(
                fill = colors.blackCobColor.copy(alpha = 0.55f),
                border = colors.blackCobBorderColor,
                center = null,
            )
        }
    }

    /**
     * Colores de patrón del tablero. Integración visual con el tablero.
     */
    val PatternAccent: ShapeColorScheme = ShapeColorScheme { _, colors ->
        ShapeColors(
            fill = colors.boardPatternColor3,
            border = colors.boardPatternColor1,
            center = colors.boardVertexColor,
        )
    }

    /**
     * Scheme de color **fijo**: ignora el `CobColor` y devuelve siempre [shapeColors].
     * Para tableros N-color (game6), donde el color lo determina el jugador, no el bando.
     */
    fun forShapeColors(shapeColors: ShapeColors): ShapeColorScheme = ShapeColorScheme { _, _ -> shapeColors }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers de color explícito (compartidos por single y game6)
// ─────────────────────────────────────────────────────────────────────────────

/** [ShapeColors] equivalentes a un [PieceColor] (game6 no usa centro → `center = null`). */
fun PieceColor.toShapeColors(center: Color? = null): ShapeColors =
    ShapeColors(
        fill = baseColor,
        border = borderColor,
        center = center,
        lightColor = lightColor,
        shadowColor = shadowColor,
    )

/**
 * Congela este scheme resolviéndolo una vez para [cobColor]: el resultado devuelve siempre las
 * mismas [ShapeColors]. Permite pasar un color ya resuelto a funciones que reciben un scheme.
 */
fun ShapeColorScheme.frozenAt(cobColor: CobColor, boardColors: BoardColors): ShapeColorScheme {
    val resolved = resolve(cobColor, boardColors)
    return ShapeColorScheme { _, _ -> resolved }
}

/**
 * [CobShape] con su scheme **congelado** en [cobColor]: útil para pasar un shape cuyo color ya está
 * resuelto a funciones que llaman a `drawMorphCob` con un `cobColor` indiferente.
 */
fun CobShape.frozen(cobColor: CobColor, boardColors: BoardColors): CobShape =
    copy(colorScheme = colorScheme.frozenAt(cobColor, boardColors))