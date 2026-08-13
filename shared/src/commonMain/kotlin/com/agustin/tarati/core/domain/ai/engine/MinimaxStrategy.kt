package com.agustin.tarati.core.domain.ai.engine

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.cache.TranspositionTable
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.evaluator.RootSelection
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.strategy.IAIStrategy
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.isMaximizingPlayer
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.math.exp
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
 * ## Desempate (variedad orgánica vs. determinismo)
 * Cuando dos movimientos tienen exactamente el mismo score, el modo lo fija
 * [EvaluationConfig.deterministicTiebreak]:
 *  - **aleatorio** (default, EASY/MEDIUM/HARD): reservoir sampling de un elemento entre las empatadas →
 *    **variedad orgánica** partida a partida (sorpresa, mejor experiencia) sin costo de fuerza (todas las
 *    empatadas son igual de buenas para el motor; a diferencia de `evalNoise`, no debilita).
 *  - **keep-first** (`deterministicTiebreak=true`, **solo CHAMPION**): se conserva el primero del orden de
 *    [MoveEvaluator.sortMoves] (que ya prioriza calidad) → juego afilado reproducible, sin abrir con la jugada
 *    pasiva (OBS-1). Champion es el único nivel exento de la variación.
 *
 * ## Selección en la raíz (variedad controlada, [EvaluationConfig.rootSelection])
 * Independiente del desempate de arriba y aplicada **solo en el nodo raíz**: cuando la política está activa
 * (CHAMPION), la jugada que se juega se elige entre las cuasi-óptimas de la raíz (dentro de un `epsilon` del
 * mejor score) con un softmax sesgado hacia la más afilada ([selectRootMove]). Así dos CHAMPION no juegan
 * siempre la misma partida sin debilitar la búsqueda: el *valor* (mejor score) no se toca y las ramas profundas
 * siguen deterministas. Se omite si la posición ya está ganada (se juega la contundente). Ver [RootSelection].
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
 * [searchBestMove] calls [cooperativeYield] after each root-level move when
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
        var tiedCount = 0
        // Last-resort move used only when every legal move causes triple-repetition defeat.
        var repetitionFallback: Move? = null

        // Selección en la raíz: recolecta (jugada, score efectivo, orden de calidad) de cada jugada
        // evaluada para elegir entre las cuasi-óptimas al final (variedad controlada). Solo en la raíz
        // y solo si la política está activa; en el resto del árbol es null (sin costo).
        val rs = config.rootSelection
        val rootCandidates: MutableList<RootCandidate>? =
            if (isRoot && rs.enabled) ArrayList(moves.size) else null
        // Para que los cuasi-empates (epsilon>0) lleguen con score preciso, la raíz no sube alpha/beta
        // hasta el mejor sino hasta mejor∓epsilon → menos poda solo en la raíz.
        val rootRelax = rootCandidates?.let { rs.epsilon } ?: 0.0

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

            // Raíz: registrar la jugada con su score efectivo y su orden de calidad (index de sortMoves).
            rootCandidates?.add(RootCandidate(move, score, index))

            val causesCutoff = shouldPrune(score, alpha, beta, isMaximizing, config)
            if (causesCutoff && index < 3) {
                moveEvaluator.recordKillerMove(move, depth, context)
            }

            // Desempate: keep-first determinista (CHAMPION) o aleatorio (resto).
            // - deterministicTiebreak: se conserva el PRIMER movimiento del mejor score (los empatados
            //   posteriores no lo reemplazan). Como vienen ordenados por calidad heurística, es el mejor
            //   ordenado entre los empatados → juego afilado reproducible, sin abrir con la jugada pasiva.
            // - aleatorio (default): reservoir sampling de un elemento entre los empatados → variedad
            //   orgánica sin costo de fuerza (todas las empatadas son igual de buenas para el motor).
            val isNewBest = if (isMaximizing) score > bestScore else score < bestScore
            val isTied = score == bestScore
            if (isNewBest) {
                bestScore = score
                bestMove = move
                tiedCount = 1

                if (!causesCutoff) {
                    moveEvaluator.recordHistoryMove(move, depth, context)
                }
            } else if (isTied && !config.deterministicTiebreak) {
                tiedCount++
                if (Random.nextDouble() < 1.0 / tiedCount) {
                    bestMove = move
                }
            }

            when {
                isMaximizing -> alpha = max(alpha, bestScore - rootRelax)
                else -> beta = min(beta, bestScore + rootRelax)
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
                    cooperativeYield()
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

        // Selección en la raíz: si hay más de una jugada cuasi-óptima, jugar una elegida con sesgo hacia
        // la afilada (variedad controlada). Se omite cuando la posición ya es ganada (jugar la contundente,
        // no diversificar hacia una peor) o cuando no hubo mejor jugada real (fallback de repetición).
        val chosen = if (
            rootCandidates != null &&
            bestMove != null &&
            !isWinningScore(bestScore, config)
        ) {
            selectRootMove(rootCandidates, bestScore, isMaximizing, rs)
        } else {
            bestMove ?: repetitionFallback ?: moves.firstOrNull()
        }
        return MoveEval(finalScore, chosen)
    }

    /**
     * Elige la jugada a jugar entre las candidatas de la raíz dentro de [RootSelection.epsilon] del mejor
     * score. Ordena por score (óptica del bando al turno) y luego por el orden de calidad de `sortMoves`
     * (la más afilada primero), y muestrea por softmax sobre la posición con [RootSelection.temperature]:
     *  - `temperature <= 0` → keep-first determinista (la más afilada),
     *  - `temperature > 0` → la afilada domina pero las siguientes óptimas aparecen → variedad.
     */
    private fun selectRootMove(
        candidates: List<RootCandidate>,
        bestScore: Double,
        isMaximizing: Boolean,
        selection: RootSelection,
    ): Move {
        val eligible = candidates.filter {
            if (isMaximizing) it.score >= bestScore - selection.epsilon
            else it.score <= bestScore + selection.epsilon
        }
        if (eligible.size <= 1) return eligible.firstOrNull()?.move ?: candidates.first().move

        val ranked = eligible.sortedWith(
            compareByDescending<RootCandidate> { if (isMaximizing) it.score else -it.score }
                .thenBy { it.orderIndex },
        )

        val t = selection.temperature
        if (t <= 0.0) return ranked.first().move

        val weights = ranked.indices.map { pos -> exp(-pos.toDouble() / t) }
        var r = Random.nextDouble() * weights.sum()
        for (i in ranked.indices) {
            r -= weights[i]
            if (r <= 0.0) return ranked[i].move
        }
        return ranked.last().move
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

    /**
     * Candidata de la raíz para la selección con variedad ([selectRootMove]).
     * [orderIndex] es la posición en `sortMoves` (menor = más afilada por la heurística de ordenamiento).
     */
    private data class RootCandidate(val move: Move, val score: Double, val orderIndex: Int)
}
