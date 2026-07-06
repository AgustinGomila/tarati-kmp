package com.agustin.tarati.core.domain.game6.pieces

import kotlinx.serialization.Serializable

/**
 * Identidad de jugador/color del juego multijugador (una cara del cubo de la patente).
 *
 * A diferencia del `CobColor` binario de Tarati (WHITE/BLACK), hay hasta **6** colores
 * (`0..5` vía [index]). No lleva semántica de "oponente" única: cualquier color puede capturar
 * a cualquier otro. La paleta visual concreta (daltónico-friendly) se resuelve en la UI (M5).
 */
@Serializable
enum class PlayerColor {
    P1, P2, P3, P4, P5, P6;

    /** Índice `0..5` del color (útil para notación/serialización, §10 del plan). */
    val index: Int get() = ordinal

    /** Letra `A..F` del jugador, usada en la notación de texto (§10 del plan). */
    val letter: Char get() = 'A' + ordinal

    companion object {
        /** El color en el [index] `0..5`. */
        fun fromIndex(index: Int): PlayerColor = entries[index]

        /** El color de la letra `A..F` (insensible a mayúsculas). */
        fun fromLetter(letter: Char): PlayerColor {
            val index = letter.uppercaseChar() - 'A'
            require(index in entries.indices) { "Letra de jugador inválida: $letter" }
            return entries[index]
        }
    }
}
