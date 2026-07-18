package com.agustin.tarati.core.domain.game.play

import kotlinx.serialization.Serializable

/**
 * GameResult - Resultado de la partida
 */
@Serializable
enum class GameResult {
    WHITE_WIN,
    BLACK_WIN,
    DRAW;

    /**
     * Convierte a string para BD
     */
    val key: String
        get() = when (this) {
            WHITE_WIN -> "white_wins"
            BLACK_WIN -> "black_wins"
            DRAW -> "draw"
        }

    companion object {
        /**
         * Convierte desde string de BD
         */
        fun fromKey(value: String): GameResult = when (value) {
            "white_wins" -> WHITE_WIN
            "black_wins" -> BLACK_WIN
            "draw" -> DRAW
            else -> throw IllegalArgumentException("Invalid game result: $value")
        }
    }
}