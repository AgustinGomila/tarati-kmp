package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.rules.MpPreMove.isReady

/**
 * Máquina de estados **pura** del pre-movimiento del juego multijugador: mientras es el turno de otro
 * (un bot en local, o un rival en online), un jugador humano puede pre-seleccionar una de sus piezas y
 * fijar un destino; cuando le vuelve el turno, el pre-movimiento se ejecuta si sigue siendo legal.
 *
 * Es el análogo de `handlePreMoveTap` de Tarati, pero sobre los tipos de `game6` ([MpGameState],
 * [PlayerColor], [MpMove]). Se mantiene **sin estado ni dependencias de UI** para que la compartan el
 * ViewModel local (`MpLocalGameViewModel`) y la pantalla online (`MpOnlineGameScreen`), que sólo
 * difieren en dónde guardan el pre-movimiento pendiente y cómo lo ejecutan.
 *
 * Los destinos se proyectan sobre el estado **actual** (antes de que jueguen los demás asientos) y se
 * revalidan con [isReady] al momento de ejecutar: si dejó de ser legal, se descarta en silencio.
 */
object MpPreMove {

    /** Resultado de interpretar un tap durante el turno ajeno. El llamador lo aplica a su estado. */
    sealed interface TapResult {
        /** Pre-seleccionar [from]; mostrar [targets] y descartar cualquier pendiente previo. */
        data class PreSelect(val from: Vertex, val targets: Set<Vertex>) : TapResult

        /** Fijar el pre-movimiento [move]; la fase de pre-selección terminó (limpiar hints). */
        data class SetPending(val move: MpMove) : TapResult

        /** Cancelar todo el pre-movimiento (pre-selección + pendiente). */
        data object Clear : TapResult

        /** Dejar el estado como está (tap irrelevante). */
        data object Ignore : TapResult
    }

    /**
     * Interpreta un tap sobre [to] dado el origen [preMoveFrom] ya pre-seleccionado (o `null` si aún no
     * se eligió pieza). [humanColor] es el color del asiento que pre-mueve. Fases idénticas a Tarati:
     *
     * - Sin pre-selección: tap en pieza propia → [TapResult.PreSelect]; en otra cosa → [TapResult.Ignore].
     * - Con pre-selección: tap en la misma pieza → [TapResult.Clear]; en otra pieza propia →
     *   [TapResult.PreSelect] (cambia de selección); en pieza ajena → [TapResult.Clear]; en casilla
     *   libre → [TapResult.SetPending] si el movimiento es legal para el asiento, si no [TapResult.Clear].
     */
    fun onTap(
        state: MpGameState,
        humanColor: PlayerColor,
        preMoveFrom: Vertex?,
        to: Vertex,
        board: BoardGraph = Board25,
    ): TapResult {
        val seat = state.seats.firstOrNull { it.color == humanColor } ?: return TapResult.Clear

        // Fase 1: sin pre-selección previa.
        if (preMoveFrom == null) {
            val piece = state.pieces[to]
            return if (piece != null && piece.owner == humanColor) {
                TapResult.PreSelect(to, targetsOf(state, seat, to, board))
            } else {
                TapResult.Ignore
            }
        }

        // Tap sobre la pieza pre-seleccionada → cancelar.
        if (to == preMoveFrom) return TapResult.Clear

        // La pieza pre-seleccionada ya no es propia (fue capturada) → cancelar.
        if (state.pieces[preMoveFrom]?.owner != humanColor) return TapResult.Clear

        val toPiece = state.pieces[to]
        return when {
            // Cambiar la pre-selección a otra pieza propia.
            toPiece != null && toPiece.owner == humanColor ->
                TapResult.PreSelect(to, targetsOf(state, seat, to, board))

            // Pieza ajena: no es un destino válido → cancelar.
            toPiece != null -> TapResult.Clear

            // Casilla libre: fijar si es un destino legal del asiento; si no, cancelar.
            else -> {
                val move = MpMove(preMoveFrom, to)
                if (move in MpRules.legalMovesFor(state, seat, board)) {
                    TapResult.SetPending(move)
                } else {
                    TapResult.Clear
                }
            }
        }
    }

    /**
     * `true` si [pending] puede ejecutarse ahora: la partida sigue, es el turno de [humanColor] y el
     * movimiento continúa siendo legal (revalidación contra el estado actual).
     */
    fun isReady(
        state: MpGameState,
        humanColor: PlayerColor,
        pending: MpMove,
        board: BoardGraph = Board25,
    ): Boolean =
        !state.isGameOver &&
                state.currentSeat.color == humanColor &&
                MpRules.isLegal(state, pending, board)

    private fun targetsOf(
        state: MpGameState,
        seat: Seat,
        from: Vertex,
        board: BoardGraph,
    ): Set<Vertex> =
        MpRules.legalMovesFor(state, seat, board)
            .filter { it.from == from }
            .map { it.to }
            .toSet()
}
