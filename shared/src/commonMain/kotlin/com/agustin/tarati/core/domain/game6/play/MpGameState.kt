package com.agustin.tarati.core.domain.game6.play

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.pieces.Piece
import kotlinx.serialization.Serializable

/**
 * Estado inmutable de una partida multijugador (tablero `25`, 2–6 jugadores).
 *
 * @property pieces piezas en el tablero por vértice ocupado.
 * @property seats asientos en orden de turno; nunca se eliminan (los retirados quedan marcados).
 * @property currentSeatIndex índice en [seats] del jugador al que le toca mover.
 * @property moveCount medias jugadas aplicadas desde el inicio.
 * @property movesSinceCapture medias jugadas consecutivas sin ninguna captura/conversión; se resetea
 *   a 0 en cuanto una jugada convierte piezas. Alimenta el corte por estancamiento (§2.6 del plan).
 * @property result resultado final si la partida terminó; `null` mientras está en curso.
 */
@Stable
@Serializable
data class MpGameState(
    val pieces: Map<Vertex, Piece>,
    val seats: List<Seat>,
    val currentSeatIndex: Int,
    val moveCount: Int = 0,
    val movesSinceCapture: Int = 0,
    val result: MpResult? = null,
) {
    /** El asiento al que le toca mover. */
    val currentSeat: Seat get() = seats[currentSeatIndex]

    /** `true` si la partida ya terminó. */
    val isGameOver: Boolean get() = result != null

    /** Asientos aún en juego. */
    val activeSeats: List<Seat> get() = seats.filter { it.status == SeatStatus.ACTIVE }

    /** Cantidad de piezas en el tablero del color [color]. */
    fun pieceCount(color: PlayerColor): Int = pieces.values.count { it.owner == color }
}
