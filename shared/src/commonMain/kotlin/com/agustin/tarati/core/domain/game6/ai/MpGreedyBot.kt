package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import kotlin.random.Random

/**
 * IA heurística (greedy con defensa a 1 ply) para el juego multijugador.
 *
 * No aplica minimax de 2 jugadores (aquí hay N ≥ 2 con capturas por conversión). Puntúa cada jugada
 * por el estado que deja, con la evaluación compartida [MpEvaluator] (material − amenaza − agresión, +
 * desenlaces terminales). Es el **tier Fácil** del ladder: la búsqueda profunda (max^n) vive en
 * [MpMaxN] y se selecciona por [MpBotLevel] desde [MpBot].
 *
 * Se usa para rellenar mesas y jugar en local. Ante empate de puntaje, desempata al azar con `random`.
 */
object MpGreedyBot {

    /**
     * Elige el mejor movimiento para el jugador al que le toca mover, o `null` si no hay
     * movimientos legales. Ante empate de puntaje, desempata al azar con [random].
     */
    fun chooseMove(
        state: MpGameState,
        board: BoardGraph = Board25,
        random: Random = Random.Default,
    ): MpMove? {
        val moves = MpRules.legalMoves(state, board)
        if (moves.isEmpty()) return null

        val color = state.currentSeat.color
        var bestScore = Double.NEGATIVE_INFINITY
        val best = mutableListOf<MpMove>()
        moves.forEach { move ->
            val score = scoreMove(state, move, color, board)
            when {
                score > bestScore -> {
                    bestScore = score
                    best.clear()
                    best.add(move)
                }

                score == bestScore -> best.add(move)
            }
        }
        return best.random(random)
    }

    /** Puntaje del estado que deja [move] para [color]: terminal si cierra la partida, si no la heurística. */
    private fun scoreMove(
        state: MpGameState,
        move: MpMove,
        color: PlayerColor,
        board: BoardGraph,
    ): Double {
        val next = MpRules.applyMove(state, move, board)
        next.result?.let { return MpEvaluator.terminalScore(it, color) }
        return MpEvaluator.evaluate(next, color, board)
    }
}
