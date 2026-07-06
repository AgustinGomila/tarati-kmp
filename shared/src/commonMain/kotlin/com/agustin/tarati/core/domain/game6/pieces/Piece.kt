package com.agustin.tarati.core.domain.game6.pieces

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Una pieza (cubo) del juego multijugador. Un solo tipo de pieza: sin promoción, roks ni
 * piezas muertas (a diferencia de Tarati).
 *
 * @property owner color/jugador dueño actual (cambia al ser capturada por conversión).
 * @property hasLeftBase `true` una vez que la pieza abandonó el cuadrado de su base. Mientras es
 * `false`, la pieza solo puede moverse dentro de su base o salir por la radial `D → C` (no por el
 * anillo D). Las piezas convertidas quedan siempre con `hasLeftBase = true`.
 */
@Serializable
@Immutable
data class Piece(
    val owner: PlayerColor,
    val hasLeftBase: Boolean = false,
)
