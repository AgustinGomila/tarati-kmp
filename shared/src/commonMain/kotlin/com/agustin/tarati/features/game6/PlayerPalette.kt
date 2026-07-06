package com.agustin.tarati.features.game6

import androidx.compose.ui.graphics.Color
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.ui.components.game.draw.pieces.PieceColor

/**
 * Los 6 colores de la paleta accesible **Okabe–Ito** (daltónico-friendly), fuente única para el juego
 * multijugador. [PlayerPalette] los asigna a `P1..P6`; [GameModeIcon] los usa en su orden posicional.
 */
object OkabeIto {
    val Vermillion: Color = Color(0xFFD55E00)
    val Blue: Color = Color(0xFF0072B2)
    val Green: Color = Color(0xFF009E73)
    val Orange: Color = Color(0xFFE69F00)
    val ReddishPurple: Color = Color(0xFFCC79A7)
    val SkyBlue: Color = Color(0xFF56B4E9)
}

/**
 * Paleta de hasta 6 colores para el juego multijugador, elegida para ser distinguible también con
 * daltonismo (basada en la paleta accesible de Okabe–Ito). Cada [PlayerColor] tiene un color de
 * relleno y un borde más oscuro para contraste sobre el tablero.
 */
object PlayerPalette {

    private val fills: Map<PlayerColor, Color> = mapOf(
        PlayerColor.P1 to OkabeIto.Vermillion,
        PlayerColor.P2 to OkabeIto.Blue,
        PlayerColor.P3 to OkabeIto.Green,
        PlayerColor.P4 to OkabeIto.Orange,
        PlayerColor.P5 to OkabeIto.ReddishPurple,
        PlayerColor.P6 to OkabeIto.SkyBlue,
    )

    /** Color de relleno de la pieza del jugador [color]. */
    fun fill(color: PlayerColor): Color = fills.getValue(color)

    /** Borde (más oscuro) de la pieza del jugador [color]. */
    fun border(color: PlayerColor): Color = fill(color).darken()

    /**
     * Los 4 colores del jugador [color] en el formato que consume el dibujo de cob de Tarati
     * (`drawOrganicCob`/`createOrganicColor`), para reutilizar la pieza real en el juego MP.
     */
    fun pieceColor(color: PlayerColor): PieceColor {
        val base = fill(color)
        return PieceColor(
            baseColor = base,
            borderColor = base.darken(),
            lightColor = base.lighten(0.45f),
            shadowColor = base.darken(0.45f),
        )
    }

    private fun Color.darken(factor: Float = 0.65f): Color =
        Color(red * factor, green * factor, blue * factor, alpha)

    private fun Color.lighten(amount: Float): Color =
        Color(
            red + (1f - red) * amount,
            green + (1f - green) * amount,
            blue + (1f - blue) * amount,
            alpha,
        )
}
