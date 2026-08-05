package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.rules.MpRules

/**
 * Función de evaluación heurística compartida del juego multijugador. Puntúa un estado desde la
 * perspectiva de **un** color: la usan [MpGreedyBot] (greedy a 1 ply, como puntaje del estado que
 * deja cada jugada) y [MpMaxN] (como componente por jugador del vector de hoja).
 *
 * Términos (peso empírico del bot original):
 * - **Material**: piezas propias − ajenas (una conversión es +1 propia y −1 ajena).
 * - **Amenaza** (exposición): máx. piezas propias que un rival podría capturar en su respuesta
 *   inmediata; se penaliza.
 * - **Agresión**: suma de distancias (en el grafo) de las piezas propias al enemigo más cercano; se
 *   premia acercarse. Pesa poco: nunca invierte una captura ni una amenaza.
 */
object MpEvaluator {

    private const val MATERIAL_WEIGHT = 1.0
    private const val THREAT_WEIGHT = 0.6
    private const val AGGRESSION_WEIGHT = 0.05

    private const val WIN_SCORE = 1_000_000.0
    private const val SHARED_SCORE = 100_000.0
    private const val LOSE_SCORE = -1_000_000.0

    private const val UNREACHABLE = 999

    /** Puntaje de [color] en un estado **no terminal**. Mayor es mejor para [color]. */
    fun evaluate(state: MpGameState, color: PlayerColor, board: BoardGraph = Board25): Double {
        val mine = state.pieceCount(color)
        val others = state.pieces.size - mine
        val material = (mine - others).toDouble()
        val threat = maxThreatAgainst(state, color, board).toDouble()
        val aggression = sumDistanceToNearestEnemy(state, color, board).toDouble()

        return MATERIAL_WEIGHT * material -
                THREAT_WEIGHT * threat -
                AGGRESSION_WEIGHT * aggression
    }

    /**
     * Puntaje **posicional** de [color] (material + agresión, **sin** el término de amenaza). Es la
     * evaluación de hoja de la búsqueda [MpMaxN]: el material (propias − ajenas) es simétrico entre los
     * dos bandos de un enfrentamiento → max^n se comporta como minimax y el ladder queda monótono (más
     * profundidad ⇒ no más débil). El término de amenaza que usa el greedy es una lookahead de 1 ply
     * que la búsqueda ya subsume — incluirlo aquí rompe la simetría y distorsiona la búsqueda profunda.
     */
    fun positionalScore(state: MpGameState, color: PlayerColor, board: BoardGraph = Board25): Double {
        val mine = state.pieceCount(color)
        val others = state.pieces.size - mine
        val material = (mine - others).toDouble()
        val aggression = sumDistanceToNearestEnemy(state, color, board).toDouble()

        return MATERIAL_WEIGHT * material - AGGRESSION_WEIGHT * aggression
    }

    /** Puntaje **terminal** de [color] según el [result] de la partida. */
    fun terminalScore(result: MpResult, color: PlayerColor): Double = when {
        result.winners == listOf(color) -> WIN_SCORE
        color in result.winners -> SHARED_SCORE
        else -> LOSE_SCORE
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
