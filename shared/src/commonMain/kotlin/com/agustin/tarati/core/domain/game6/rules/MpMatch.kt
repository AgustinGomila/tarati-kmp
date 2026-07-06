package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpCutReason
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpNotation

/**
 * Runner **con estado** de una partida multijugador que aplica el **corte por estancamiento** completo
 * (§2.6 del plan) sobre el motor puro [MpRules]:
 *
 * - **N jugadas sin conversión** ([MpCutConfig.maxMovesWithoutCapture]): lo resuelve [MpRules.applyMove]
 *   al pasarle el [cut] (depende solo de `movesSinceCapture`, que es parte del estado).
 * - **Triple repetición** ([MpCutConfig.repetitionLimit]): requiere **historial de posiciones**, que es
 *   lo que este runner mantiene (un multiconjunto de FEN `posición + turno`). Al reaparecer una posición
 *   el nº de veces del umbral, corta por [MpCutReason.REPETITION].
 *
 * En ambos casos la resolución es por mayoría de piezas (nunca "tablas"; ver [MpRules.resolveByCut]).
 *
 * Reutilizable en local (lo usa `MpLocalGameViewModel`) y en el servidor (M6). Como es mutable, cada
 * partida nueva se representa con una instancia nueva; [rotate] rota la perspectiva y reinicia el
 * historial de repeticiones (las claves FEN cambian bajo rotación).
 */
class MpMatch(
    initial: MpGameState,
    private val board: BoardGraph = Board25,
    private val cut: MpCutConfig = MpCutConfig.Default,
) {
    /** Multiconjunto de posiciones vistas (FEN `posición + turno`) → cantidad de apariciones. */
    private val positionCounts = HashMap<String, Int>()

    /** Estado actual de la partida. */
    var state: MpGameState = initial
        private set

    init {
        recordPosition(initial)
    }

    /** Movimientos legales del jugador en turno (vacío si la partida terminó). */
    fun legalMoves(): List<MpMove> = MpRules.legalMoves(state, board)

    /** `true` si [move] es legal para el jugador en turno. */
    fun isLegal(move: MpMove): Boolean = MpRules.isLegal(state, move, board)

    /**
     * Aplica [move] (que debe ser legal), actualiza [state] y aplica el corte por estancamiento: el de
     * N jugadas sin conversión lo hace [MpRules.applyMove]; si la partida sigue en curso, además cuenta
     * la posición resultante y corta por triple repetición al alcanzar el umbral.
     */
    fun applyMove(move: MpMove): MpGameState {
        var next = MpRules.applyMove(state, move, board, cut)
        if (!next.isGameOver) {
            val occurrences = recordPosition(next)
            if (occurrences >= cut.repetitionLimit) {
                next = MpRules.resolveByCut(next, MpCutReason.REPETITION)
            }
        }
        state = next
        return next
    }

    /**
     * Retira forzadamente al jugador [color] (desconexión/timeout) vía [MpRules.retire] y actualiza
     * [state]. Registra la posición resultante para la detección de repetición (el retiro reduce
     * piezas, no es "progreso" ni captura, así que no dispara el corte por no-conversión).
     */
    fun retire(color: PlayerColor): MpGameState {
        val next = MpRules.retire(state, color, board)
        if (next !== state && !next.isGameOver) recordPosition(next)
        state = next
        return next
    }

    /**
     * Rota la perspectiva 60° ([MpTransforms.rotate60]) y **reinicia** el historial de repeticiones
     * (bajo rotación cambian los vértices, y con ellos las claves FEN). No es una jugada.
     */
    fun rotate(): MpGameState {
        state = MpTransforms.rotate60(state)
        positionCounts.clear()
        recordPosition(state)
        return state
    }

    /** Registra la posición de [s] y devuelve cuántas veces se ha visto (incluyendo ésta). */
    private fun recordPosition(s: MpGameState): Int {
        val key = MpNotation.toPositionNotation(s)
        val occurrences = (positionCounts[key] ?: 0) + 1
        positionCounts[key] = occurrences
        return occurrences
    }
}
