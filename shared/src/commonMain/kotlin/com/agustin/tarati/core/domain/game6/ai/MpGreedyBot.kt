package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game.board.Vertex
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
 * No aplica minimax (el motor de Tarati es suma-cero de 2 jugadores; aquí hay N ≥ 2 con capturas
 * por conversión). En su lugar puntúa cada movimiento por:
 *
 * - **Material** tras el movimiento (piezas propias − ajenas): premia capturar (una conversión es
 *   +1 propia y −1 ajena).
 * - **Amenaza** (exposición): la mayor cantidad de piezas propias que un rival podría capturar en
 *   su respuesta inmediata; se penaliza.
 * - **Agresión**: distancia (en el grafo) de las piezas propias al enemigo más cercano; se premia
 *   acercarse. Sin este término, cuando no hay capturas al alcance todos los movimientos empatan y
 *   el bot deambula sin forzar el contacto. Pesa poco: nunca invierte una captura ni una amenaza.
 * - Desenlaces terminales: ganar/compartir/perder tienen puntajes extremos.
 *
 * Es el bot del MVP (rellenar mesas, jugar en local). Una IA más fuerte (`max^n`/paranoid) queda
 * diferida (§8 del plan).
 */
object MpGreedyBot {

    private const val MATERIAL_WEIGHT = 1.0
    private const val THREAT_WEIGHT = 0.6
    private const val AGGRESSION_WEIGHT = 0.05

    private const val WIN_SCORE = 1_000_000.0
    private const val SHARED_SCORE = 100_000.0
    private const val LOSE_SCORE = -1_000_000.0

    private const val UNREACHABLE = 999

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

    private fun scoreMove(
        state: MpGameState,
        move: MpMove,
        color: PlayerColor,
        board: BoardGraph,
    ): Double {
        val next = MpRules.applyMove(state, move, board)

        next.result?.let { result ->
            return when {
                result.winners == listOf(color) -> WIN_SCORE
                color in result.winners -> SHARED_SCORE
                else -> LOSE_SCORE
            }
        }

        val mine = next.pieceCount(color)
        val others = next.pieces.size - mine
        val material = (mine - others).toDouble()
        val threat = maxThreatAgainst(next, color, board).toDouble()
        val aggression = sumDistanceToNearestEnemy(next, color, board).toDouble()

        return MATERIAL_WEIGHT * material -
                THREAT_WEIGHT * threat -
                AGGRESSION_WEIGHT * aggression
    }

    /**
     * Suma de las distancias (en aristas) de cada pieza de [color] al enemigo más cercano.
     * BFS multi-fuente desde todas las piezas enemigas (una sola pasada por evaluación). Menor es
     * mejor: incentiva cerrar distancia hacia el rival.
     */
    private fun sumDistanceToNearestEnemy(
        state: MpGameState,
        color: PlayerColor,
        board: BoardGraph,
    ): Int {
        val enemyVertices = state.pieces.filterValues { it.owner != color }.keys
        val myVertices = state.pieces.filterValues { it.owner == color }.keys
        if (enemyVertices.isEmpty() || myVertices.isEmpty()) return 0

        val dist = HashMap<Vertex, Int>()
        val queue = ArrayDeque<Vertex>()
        enemyVertices.forEach { dist[it] = 0; queue.add(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val d = dist.getValue(current)
            board.neighborsOf(current).forEach { neighbor ->
                if (neighbor !in dist) {
                    dist[neighbor] = d + 1
                    queue.add(neighbor)
                }
            }
        }
        return myVertices.sumOf { dist[it] ?: UNREACHABLE }
    }

    /** Máximo de piezas de [color] que algún rival podría capturar en su próximo movimiento. */
    private fun maxThreatAgainst(state: MpGameState, color: PlayerColor, board: BoardGraph): Int {
        var worst = 0
        state.activeSeats.forEach { seat ->
            if (seat.color == color) return@forEach
            MpRules.legalMovesFor(state, seat, board).forEach { move ->
                val captured = MpRules.captureTargets(state.pieces, move, board)
                    .count { state.pieces[it]?.owner == color }
                if (captured > worst) worst = captured
            }
        }
        return worst
    }
}
