package com.agustin.tarati.ui.components.game.draw.pieces

/**
 * Preferencia del usuario para el tipo de animación al capturar piezas.
 *
 * Se persiste en DataStore y se resuelve a [ConversionAnimationType] en
 * [BoardAnimationViewModel].
 */
enum class ConversionAnimationStyle {
    /** FROM_CENTER o FROM_BORDER, elegido al azar por captura. */
    TRANSFORMATION,

    /** Volteo de moneda siempre. */
    FLIP,

    /** Elige al azar entre los tres tipos disponibles por cada captura. */
    SURPRISE,
}

/**
 * Resuelve el tipo concreto de animación de conversión para una captura, según la preferencia del
 * usuario. Compartido por el juego en vivo ([com.agustin.tarati.ui.components.game.animation.BoardAnimationViewModel]),
 * el tablero MP ([com.agustin.tarati.features.game6.Board25View]) y el replay single
 * ([com.agustin.tarati.ui.components.library.ReplayBoardRenderer]).
 */
fun ConversionAnimationStyle.resolveType(): ConversionAnimationType = when (this) {
    ConversionAnimationStyle.TRANSFORMATION ->
        listOf(ConversionAnimationType.FROM_CENTER, ConversionAnimationType.FROM_BORDER).random()

    ConversionAnimationStyle.FLIP -> ConversionAnimationType.FLIP
    ConversionAnimationStyle.SURPRISE -> ConversionAnimationType.entries.random()
}