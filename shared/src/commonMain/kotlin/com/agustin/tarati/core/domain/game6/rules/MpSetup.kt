package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.Seat
import kotlin.math.roundToInt

/**
 * Construcción del estado inicial de una partida multijugador.
 *
 * Con menos de 6 jugadores, elige las bases **lo más separadas posible** entre las 6 del anillo
 * (2 → opuestas, 3 → alternadas, etc.). Coloca 4 piezas por jugador en los 4 puntos de su base
 * (cuadrado `{E, E, D, D}`), todas con `hasLeftBase = false`.
 */
object MpSetup {

    const val MIN_PLAYERS: Int = 2
    const val MAX_PLAYERS: Int = 6

    /**
     * Estado inicial para [playerCount] jugadores (2–6). Los colores se asignan `P1..Pn` en el
     * orden de las bases elegidas, que es también el orden de turno.
     */
    fun initialState(playerCount: Int): MpGameState {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "playerCount debe estar entre $MIN_PLAYERS y $MAX_PLAYERS (fue $playerCount)"
        }

        val baseIndices = selectBaseIndices(playerCount)
        val seats =
            baseIndices.mapIndexed { i, baseIdx ->
                Seat(color = PlayerColor.fromIndex(i), baseId = Board25.bases[baseIdx].id)
            }

        val pieces = mutableMapOf<Vertex, Piece>()
        seats.forEach { seat ->
            val base = Board25.baseById(seat.baseId)
            base.startSquare.forEach { vertex ->
                pieces[vertex] = Piece(owner = seat.color)
            }
        }

        return MpGameState(
            pieces = pieces.toMap(),
            seats = seats,
            currentSeatIndex = 0,
        )
    }

    /**
     * Índices de las [k] bases más separadas dentro de las 6 del anillo (repartidas de forma
     * uniforme). Los índices son distintos y en orden ascendente.
     */
    fun selectBaseIndices(k: Int): List<Int> =
        (0 until k).map { (it.toDouble() * Board25.bases.size / k).roundToInt() % Board25.bases.size }
}
