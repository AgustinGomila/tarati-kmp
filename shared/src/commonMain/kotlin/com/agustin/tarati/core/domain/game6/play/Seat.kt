package com.agustin.tarati.core.domain.game6.play

import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import kotlinx.serialization.Serializable

/** Estado de un asiento en la mesa. */
@Serializable
enum class SeatStatus { ACTIVE, RETIRED }

/**
 * Un asiento de la partida: un jugador, su color y la base donde arrancó.
 *
 * Los asientos no se eliminan al retirarse un jugador; se marcan [SeatStatus.RETIRED] para
 * preservar el orden de turno y el conteo inicial de jugadores.
 *
 * @property baseId número Fig. 5 de la base asignada (17–22, ver `Board25.baseById`).
 */
@Serializable
@Immutable
data class Seat(
    val color: PlayerColor,
    val baseId: Int,
    val status: SeatStatus = SeatStatus.ACTIVE,
)
