package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import kotlin.random.Random

/**
 * Motor de búsqueda **max^n** (Luckhardt-Irani) para el juego multijugador de N jugadores.
 *
 * A diferencia del minimax de 2 jugadores suma-cero (juego 1, [com.agustin.tarati.core.domain.ai]),
 * en MP cada asiento es egoísta y una jugada puede convertir piezas de varios rivales a la vez. max^n
 * generaliza el minimax: cada nodo devuelve un **vector de puntajes** (uno por color activo en la
 * raíz) y el jugador al que le toca mover elige el hijo que maximiza **su propia** componente. La
 * evaluación de hoja es la de [MpEvaluator].
 *
 * Detalles:
 * - **Profundidad** = plies (un ply = una jugada de un asiento), fijada por [MpBotLevel.depth].
 * - **Ordenamiento de jugadas** por la componente inmediata del que mueve → las mejores líneas se
 *   exploran primero, lo que mejora la calidad cuando se agota el presupuesto de nodos.
 * - **Presupuesto de nodos** ([NODE_BUDGET]): cota de tiempo del peor caso — max^n poda muy poco, así
 *   que en aperturas de alto factor de ramificación la búsqueda profunda se trunca a una evaluación de
 *   hoja. Los tiers profundos completan la búsqueda en finales (menos piezas, menor ramificación).
 *
 * Estado de búsqueda encapsulado en una instancia [Search] por llamada: el servidor corre bots
 * concurrentes y un `object` con contadores mutables sería una condición de carrera.
 */
object MpMaxN {

    /** Tope de nodos por jugada. Cota del peor caso (max^n apenas poda). Calibrable con los tests de fuerza. */
    private const val NODE_BUDGET = 50_000

    /**
     * Elige el mejor movimiento para el jugador en turno buscando [MpBotLevel.depth] plies, o `null`
     * si no hay movimientos legales. Ante empate, desempata al azar con [random].
     */
    fun chooseMove(
        state: MpGameState,
        level: MpBotLevel,
        board: BoardGraph = Board25,
        random: Random = Random.Default,
    ): MpMove? {
        val moves = MpRules.legalMoves(state, board)
        if (moves.isEmpty()) return null
        return Search(board, level.depth).choose(state, moves, random)
    }

    /** Una búsqueda concreta: contadores de estado por llamada (seguro para bots concurrentes). */
    private class Search(
        private val board: BoardGraph,
        private val maxDepth: Int,
    ) {
        private var nodes = 0

        fun choose(state: MpGameState, moves: List<MpMove>, random: Random): MpMove {
            val mover = state.currentSeat.color
            val rootColors = state.activeSeats.map { it.color }

            var bestScore = Double.NEGATIVE_INFINITY
            val best = mutableListOf<MpMove>()
            orderedChildren(state, moves).forEach { (move, child) ->
                val vector = search(child, maxDepth - 1, rootColors)
                val score = vector.getValue(mover)
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

        /**
         * Vector de puntajes (por color de [rootColors]) de la mejor línea desde [state], mirando
         * [depthRemaining] plies más. Terminal o presupuesto agotado → evaluación de hoja.
         */
        private fun search(
            state: MpGameState,
            depthRemaining: Int,
            rootColors: List<PlayerColor>,
        ): Map<PlayerColor, Double> {
            nodes++
            state.result?.let { result ->
                return rootColors.associateWith { MpEvaluator.terminalScore(result, it) }
            }
            if (depthRemaining == 0 || nodes >= NODE_BUDGET) return leafVector(state, rootColors)

            val mover = state.currentSeat.color
            val moves = MpRules.legalMoves(state, board)
            if (moves.isEmpty()) return leafVector(state, rootColors)

            var best: Map<PlayerColor, Double>? = null
            orderedChildren(state, moves).forEach { (_, child) ->
                val vector = search(child, depthRemaining - 1, rootColors)
                if (best == null || vector.getValue(mover) > best.getValue(mover)) best = vector
                if (nodes >= NODE_BUDGET) return@forEach
            }
            return best ?: leafVector(state, rootColors)
        }

        /**
         * Hijos de [state] (jugada + estado resultante) ordenados por nº de capturas (barato: solo
         * mira los vecinos del destino). Explorar primero las jugadas que convierten piezas mejora la
         * calidad cuando se agota el presupuesto de nodos. El `applyMove` se hace una sola vez por
         * jugada y se reutiliza en la recursión.
         */
        private fun orderedChildren(
            state: MpGameState,
            moves: List<MpMove>,
        ): List<Pair<MpMove, MpGameState>> =
            moves.map { move ->
                val captures = MpRules.captureTargets(state.pieces, move, board).size
                Triple(move, MpRules.applyMove(state, move, board), captures)
            }.sortedByDescending { it.third }.map { it.first to it.second }

        private fun leafVector(state: MpGameState, rootColors: List<PlayerColor>): Map<PlayerColor, Double> =
            rootColors.associateWith { component(state, it) }

        /** Componente de [color]: terminal si la partida cerró, si no la evaluación posicional (sin amenaza). */
        private fun component(state: MpGameState, color: PlayerColor): Double =
            state.result?.let { MpEvaluator.terminalScore(it, color) }
                ?: MpEvaluator.positionalScore(state, color, board)
    }
}
