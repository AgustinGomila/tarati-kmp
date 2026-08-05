package com.agustin.tarati.core.domain.ai.engine

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.cache.TranspositionTable
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.strategy.IAIStrategy
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.isMaximizingPlayer
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Motor de búsqueda Minimax con poda Alpha-Beta, Profundización Iterativa
 * y heurísticas de ordenamiento de movimientos.
 *
 * ## Algoritmo principal
 * La búsqueda utiliza **Iterative Deepening** (profundización iterativa): en lugar
 * de buscar directamente a la profundidad máxima, realiza búsquedas completas de
 * profundidad 1, 2, 3... hasta alcanzar [Difficulty.depth] o el límite de tiempo.
 * Esto garantiza que siempre hay un resultado disponible si el tiempo se agota,
 * y permite usar los resultados de niveles anteriores para ordenar movimientos en
 * el siguiente nivel — compensando el costo aparente de repetir la búsqueda.
 *
 * ## Poda Alpha-Beta
 * La poda estándar se extiende con un umbral de victoria configurable
 * ([EvaluationConfig.winningThreshold]): si un movimiento supera ese umbral,
 * se trata como ganador inmediato y se corta la búsqueda sin evaluar el resto
 * del árbol. Esto reduce drásticamente el número de nodos en finales de partida.
 *
 * ## Evitación de triple repetición
 * Cuando un movimiento causaría la 3ª ocurrencia de una posición (derrota
 * inmediata por la regla de triple repetición), ese movimiento **nunca** se
 * elige como mejor movimiento — a menos que todos los movimientos legales
 * lleven a la derrota por repetición. En ese caso de fuerza mayor, el primero
 * de los movimientos repetición se usa como último recurso.
 * Este comportamiento es independiente de la profundidad de búsqueda, por lo
 * que funciona correctamente incluso en EASY (depth=2) donde la búsqueda corta
 * no puede anticipar la regla por sí sola.
 *
 * ## Desempate determinista
 * Cuando dos movimientos tienen exactamente el mismo score, se conserva el
 * **primero** en el orden de [MoveEvaluator.sortMoves] (que ya prioriza calidad:
 * capturas, killer/history, bonus posicional). Es determinista y prefiere la mejor
 * jugada ordenada entre las empatadas. Reemplaza al reservoir sampling aleatorio
 * previo, que en aperturas simétricas (plagadas de empates) hacía elegir a veces la
 * jugada objetivamente más pasiva y rompía la reproducibilidad de los tests de fuerza.
 *
 * ## Quiescence en la hoja (gateada por dificultad)
 * Si [EvaluationConfig.quiescenceEnabled] (solo CHAMPION), en la hoja `depth==0` la
 * posición no se puntúa estáticamente si tiene capturas pendientes: [quiescence]
 * extiende la búsqueda sobre las jugadas **ruidosas** (que voltean piezas) hasta un
 * estado tranquilo o [EvaluationConfig.quiescenceMaxPlies]. En Tarati casi toda jugada
 * de contacto voltea piezas, así que la profundidad fija evalúa rutinariamente
 * posiciones a mitad de un intercambio (horizon effect); la quiescence lo corrige.
 *
 * ## Límite de tiempo
 * Un [SearchContext] con timestamp de inicio permite cortar la búsqueda si se
 * supera el 90% del tiempo máximo ([timeLimitMs]). El margen del 10% absorbe
 * la latencia del hilo y la serialización del resultado.
 *
 * ## LMR por branching de roks
 * Cuando un nodo tiene más de
 * [SearchWeights.lmrBranchingThreshold] movimientos
 * disponibles (típico en posiciones con muchos roks), los movimientos tardíos del
 * ordenamiento se buscan a
 * depth-[SearchWeights.lmrDepthReduction].
 * Si el resultado supera alpha se re-busca a profundidad completa. Esta técnica preserva la calidad
 * táctica (nunca se reduce el primer movimiento, capturas ni victorias inmediatas)
 * mientras reduce drásticamente los nodos en posiciones de alta movilidad.
 *
 * ## Cooperative scheduling (WASM)
 * [searchBestMove] calls [yieldForAnimation] after each root-level move when
 * [yieldIntervalMs] has elapsed. In WASM this dispatches a macrotask via
 * setTimeout, allowing requestAnimationFrame to fire between root moves and
 * keeping the animation smooth during deep searches.
 */
class MinimaxStrategy(
    private val boardEvaluator: BoardEvaluator,
    private val moveEvaluator: MoveEvaluator,
    private val transpositionTable: TranspositionTable,
    private val cache: HybridEvaluationCache,
    private val positionHistory: Map<String, Int>,
    private val evalConfig: () -> EvaluationConfig,
) : IAIStrategy {
    private val timeLimitMs = 10_000L

    // Minimum elapsed time (ms) between animation yields at the root search level.
    private val yieldIntervalMs = 12L

    // Contexto de la última búsqueda — sus contadores alimentan [getStats].
    private var lastContext: SearchContext = SearchContext()

    override suspend fun getNextMove(
        gameState: GameState,
        difficulty: Difficulty,
    ): MoveEval = iterativeDeepening(gameState, difficulty)

    private suspend fun iterativeDeepening(
        gameState: GameState,
        difficulty: Difficulty,
    ): MoveEval {
        val config = evalConfig()
        val maxDepth = min(difficulty.depth, Difficulty.MAX.depth)
        var bestResult = MoveEval(0.0, null)

        val context = SearchContext(maxTimeMs = timeLimitMs)
        lastContext = context

        for (depth in 1..maxDepth) {
            val currentResult =
                minimax(
                    gameState = gameState,
                    depth = depth,
                    alpha = Double.NEGATIVE_INFINITY,
                    beta = Double.POSITIVE_INFINITY,
                    config = config,
                    context = context,
                    isRoot = true,
                )

            bestResult = currentResult

            if (isWinningScore(bestResult.score, config)) break
            if (shouldStopSearch(context)) break
        }

        return bestResult
    }

    private suspend fun minimax(
        gameState: GameState,
        depth: Int,
        alpha: Double,
        beta: Double,
        config: EvaluationConfig,
        context: SearchContext,
        isRoot: Boolean = false,
    ): MoveEval {
        context.nodesEvaluated++

        // Fin de partida (a cualquier profundidad): score desde la óptica del jugador al turno,
        // igual que antes (el nodo padre lo interpreta vía su rama de victoria inmediata).
        if (gameState.isGameOver(positionHistory)) {
            return MoveEval(terminalScoreToMove(gameState, config), null)
        }
        // Hoja: quiescence (solo si está gateada) o eval estático directo.
        if (depth == 0) {
            val leafScore =
                if (config.quiescenceEnabled) {
                    quiescence(gameState, alpha, beta, config, context, config.quiescenceMaxPlies)
                } else {
                    getCachedEvaluation(gameState, config)
                }
            return MoveEval(leafScore, null)
        }

        val boardHash = gameState.hashBoard()
        transpositionTable.get(boardHash, depth)?.let {
            context.cacheHits++
            return it
        }

        val moves = gameState.allMovesForTurn()
        if (moves.isEmpty()) {
            val score = getCachedEvaluation(gameState, config)
            return MoveEval(score, null)
        }

        val sortedMoves =
            moveEvaluator.sortMoves(moves, gameState, gameState.currentTurn, config, depth, context)

        // Winning-move detection is handled inside searchBestMove: the first
        // move that satisfies isGameOver() && winner == currentTurn triggers
        // an early return, avoiding a separate O(N) applyMove pass here.
        return searchBestMove(gameState, sortedMoves, depth, alpha, beta, config, context, isRoot)
            .also { result ->
                transpositionTable.put(boardHash, depth, result)
            }
    }

    private suspend fun searchBestMove(
        gameState: GameState,
        moves: List<Move>,
        depth: Int,
        alphaInit: Double,
        betaInit: Double,
        config: EvaluationConfig,
        context: SearchContext,
        isRoot: Boolean = false,
    ): MoveEval {
        val isMaximizing = gameState.currentTurn.isMaximizingPlayer()
        var bestMove: Move? = null
        var bestScore = if (isMaximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        var alpha = alphaInit
        var beta = betaInit
        // Last-resort move used only when every legal move causes triple-repetition defeat.
        var repetitionFallback: Move? = null

        for ((index, move) in moves.withIndex()) {
            val newState = gameState.applyMove(move)

            // Immediate win: return without recursing.
            // Score is from the parent's perspective (isMaximizing), not the child's,
            // because terminalScoreToMove scores from the TO-MOVE player's viewpoint
            // (which after the winning move is the opponent -- wrong sign for the parent).
            if (newState.isGameOver(positionHistory) && newState.getWinner(positionHistory) == gameState.currentTurn) {
                val score = if (isMaximizing) config.winningScore else -config.winningScore
                return MoveEval(score, move)
            }

            val newHash = newState.hashBoard()
            val futureCount = (positionHistory[newHash] ?: 0) + 1

            if (futureCount >= 3) {
                // Definitive loss: the moving player caused the 3rd repetition and loses
                // immediately. Never prefer this over any evaluated move — store it only
                // as a last resort in case every legal move leads to repetition defeat.
                if (repetitionFallback == null) repetitionFallback = move
                continue
            }

            // Branching LMR: reduce depth for late moves in high-branching nodes.
            // Conditions where we NEVER reduce:
            //   - first [lmrMoveIndexThreshold] moves (highest-ranked by ordering)
            //   - captures (flipsCob or flipsRoc > 0) — already checked via isGameOver above
            //   - depth <= 2 (close to leaves, reduction would skip too much)
            //   - the node has few moves (not a high-branching position)
            val lmr = config.lmrBranchingThreshold
            val canReduce = depth > 2
                    && moves.size > lmr
                    && index >= config.lmrMoveIndexThreshold
            val searchDepth = if (canReduce) depth - 1 - config.lmrDepthReduction else depth - 1

            var result = minimax(newState, searchDepth, alpha, beta, config, context)

            // Re-search at full depth if the reduced search beat alpha — it may be
            // a genuinely good move that the reduction underestimated.
            if (canReduce && result.score > alpha) {
                result = minimax(newState, depth - 1, alpha, beta, config, context)
            }

            // Penalizar movimientos que acercan a la 2ª repetición para desalentar
            // ciclos que podrían forzar luego la 3ª derrota.
            val score = if (futureCount == 2) {
                val penalty = config.winningScore * config.repetitionPenaltyMultiplier
                if (isMaximizing) result.score - penalty else result.score + penalty
            } else result.score

            val causesCutoff = shouldPrune(score, alpha, beta, isMaximizing, config)
            if (causesCutoff && index < 3) {
                moveEvaluator.recordKillerMove(move, depth, context)
            }

            // Desempate determinista: se conserva el PRIMER movimiento que alcanza el mejor
            // score (los empatados posteriores no lo reemplazan). Como los movimientos ya vienen
            // ordenados por calidad heurística, el primero es el mejor ordenado entre los empatados.
            val isNewBest = if (isMaximizing) score > bestScore else score < bestScore
            if (isNewBest) {
                bestScore = score
                bestMove = move

                if (!causesCutoff) {
                    moveEvaluator.recordHistoryMove(move, depth, context)
                }
            }

            when {
                isMaximizing -> alpha = max(alpha, bestScore)
                else -> beta = min(beta, bestScore)
            }

            if (causesCutoff) {
                context.cutoffs++
                break
            }

            // Cooperative scheduling: yield to the browser/OS event loop after
            // each root-level move if enough time has elapsed. This allows
            // requestAnimationFrame to fire between subtree evaluations,
            // preventing animation freeze during deep searches.
            if (isRoot) {
                val now = Clock.System.now().toEpochMilliseconds()
                if (now - context.lastYieldTimeMs >= yieldIntervalMs) {
                    yieldForAnimation()
                    context.lastYieldTimeMs = Clock.System.now().toEpochMilliseconds()
                }
            }
        }

        // Si no hubo ningún movimiento evaluado (todos causaban 3ª repetición),
        // marcar esta posición como DERROTA — no como ±infinito (que confundiría
        // al nodo padre haciéndole creer que es una posición ganadora).
        val finalScore = if (bestMove == null && repetitionFallback != null) {
            if (isMaximizing) -config.winningScore else config.winningScore
        } else {
            bestScore
        }
        return MoveEval(finalScore, bestMove ?: repetitionFallback ?: moves.firstOrNull())
    }

    private fun getCachedEvaluation(gameState: GameState, config: EvaluationConfig): Double {
        val base = cache.getFullEvaluation(gameState) ?: boardEvaluator.evaluate(gameState, config).also {
            cache.putFullEvaluation(gameState, it)
        }
        val noise = config.evalNoise
        return if (noise > 0.0) base + (Random.nextDouble() - 0.5) * noise else base
    }

    /**
     * Score de una posición terminal desde la óptica del **jugador al turno**:
     * +winningScore si el al-turno ganó, −winningScore si perdió, eval estático en tablas.
     * (Convención heredada; el nodo padre la resuelve con su chequeo de victoria inmediata.)
     */
    private fun terminalScoreToMove(gameState: GameState, config: EvaluationConfig): Double =
        when (gameState.getWinner(positionHistory)) {
            gameState.currentTurn -> config.winningScore
            gameState.currentTurn.opponent -> -config.winningScore
            else -> getCachedEvaluation(gameState, config)
        }

    /**
     * Quiescence search: resuelve las capturas pendientes en la hoja antes de puntuar.
     *
     * Solo expande jugadas **ruidosas** (que voltean ≥[EvaluationConfig.quiescenceMinCobFlips]
     * cobs o cualquier rok), hasta [pliesLeft] == 0 o un estado tranquilo. Devuelve un score
     * **absoluto** (perspectiva maximizante de Blancas), consistente con [getCachedEvaluation]
     * al que reemplaza en la hoja.
     *
     * Usa **stand-pat** como cota: el bando al turno puede "no capturar" (asume que tiene alguna
     * jugada tranquila) y garantizar al menos el eval estático; luego prueba las capturas para
     * mejorarlo. Caveat de Tarati (sin pase): si *todas* sus jugadas son ruidosas, el stand-pat es
     * optimista — limitación estándar aceptada, rara en la práctica.
     */
    private fun quiescence(
        gameState: GameState,
        alphaInit: Double,
        betaInit: Double,
        config: EvaluationConfig,
        context: SearchContext,
        pliesLeft: Int,
    ): Double {
        context.nodesEvaluated++

        if (gameState.isGameOver(positionHistory)) {
            return absoluteTerminalScore(gameState, config)
        }

        val standPat = getCachedEvaluation(gameState, config)
        if (pliesLeft <= 0) return standPat

        val isMaximizing = gameState.currentTurn.isMaximizingPlayer()
        var alpha = alphaInit
        var beta = betaInit

        // Cota stand-pat.
        if (isMaximizing) {
            if (standPat >= beta) return standPat
            if (standPat > alpha) alpha = standPat
        } else {
            if (standPat <= alpha) return standPat
            if (standPat < beta) beta = standPat
        }

        var best = standPat
        for (move in gameState.allMovesForTurn()) {
            val child = gameState.applyMove(move)
            val (rokFlips, cobFlips) = move.countFlipsByType(gameState, child)
            val noisy = rokFlips >= 1 || cobFlips >= config.quiescenceMinCobFlips
            if (!noisy) continue

            val score = quiescence(child, alpha, beta, config, context, pliesLeft - 1)
            if (isMaximizing) {
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            } else {
                if (score < best) best = score
                if (best < beta) beta = best
                if (beta <= alpha) break
            }
        }
        return best
    }

    /**
     * Score terminal **absoluto** (perspectiva de Blancas): +winningScore si ganan Blancas,
     * −winningScore si ganan Negras, eval estático en tablas. Usado por [quiescence], cuyo
     * eval de hoja también es absoluto.
     */
    private fun absoluteTerminalScore(gameState: GameState, config: EvaluationConfig): Double =
        when (gameState.getWinner(positionHistory)) {
            CobColor.WHITE -> config.winningScore
            CobColor.BLACK -> -config.winningScore
            else -> getCachedEvaluation(gameState, config)
        }

    private fun shouldPrune(
        bestScore: Double,
        alpha: Double,
        beta: Double,
        isMaximizing: Boolean,
        config: EvaluationConfig,
    ): Boolean {
        val winThreshold = config.winningScore * config.winningThreshold
        return (isMaximizing && bestScore >= winThreshold) ||
                (!isMaximizing && bestScore <= -winThreshold) ||
                beta <= alpha
    }

    private fun isWinningScore(
        score: Double,
        config: EvaluationConfig,
    ): Boolean {
        val winThreshold = config.winningScore * config.winningThreshold
        return score >= winThreshold || score <= -winThreshold
    }

    private fun shouldStopSearch(context: SearchContext): Boolean {
        val elapsed = Clock.System.now().toEpochMilliseconds() - context.startTimeMs
        return elapsed > context.maxTimeMs * 0.9
    }

    /** Contadores de la última búsqueda (del [SearchContext] más reciente). */
    fun getStats(): SearchStats = SearchStats(
        nodesEvaluated = lastContext.nodesEvaluated,
        cutoffs = lastContext.cutoffs,
        cacheHits = lastContext.cacheHits,
    )
}
