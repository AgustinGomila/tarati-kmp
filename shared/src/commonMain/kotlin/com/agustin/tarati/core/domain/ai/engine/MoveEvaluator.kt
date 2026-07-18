package com.agustin.tarati.core.domain.ai.engine

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.GameBoard.centerVertices
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.isMaximizingPlayer
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move

class MoveEvaluator(
    private val cache: HybridEvaluationCache,
) {
    // Instancia propia: el precomputador cachea valores por config y no debe
    // compartir estado mutable entre motores que buscan en paralelo (bots del servidor).
    // Las heurísticas killer/history viven en el SearchContext de cada búsqueda.
    private val patternPrecomputer = PatternPrecomputer()

    fun sortMoves(
        moves: List<Move>,
        gameState: GameState,
        currentPlayer: CobColor,
        config: EvaluationConfig,
        depth: Int = 0,
        context: SearchContext? = null,
    ): List<Move> {
        val isMaximizing = currentPlayer.isMaximizingPlayer()

        val cachedOrder = cache.getMoveOrdering(gameState, isMaximizing, depth)
        if (cachedOrder != null) {
            val reconstructed = reconstructMovesFromCache(moves, cachedOrder)
            // Guard: si el orden cacheado no cubre todos los movimientos actuales,
            // descartarlo y reordenar en fresco — nunca excluir movimientos de la búsqueda.
            if (reconstructed.size == moves.size) return reconstructed
        }

        // Local cache scoped to this sortMoves call — avoids recomputing isolation
        // for the same (vertex, position, color) within a single move-ordering pass,
        // while preventing unbounded growth across the entire minimax search tree.
        val localIsolationCache = HashMap<String, Boolean>(moves.size * 2)

        val evaluatedMoves =
            moves.map { move ->
                evaluateMoveWithHeuristics(move, gameState, config, depth, context, localIsolationCache)
            }

        val sortedMoves =
            when {
                isMaximizing -> evaluatedMoves.sortedWith(maximizingComparator)
                else -> evaluatedMoves.sortedWith(minimizingComparator)
            }.map { it.move }

        cache.putMoveOrdering(
            gameState = gameState,
            isMaximizing = isMaximizing,
            moves = sortedMoves.map { it.name },
            depth = depth,
        )

        return sortedMoves
    }

    private fun evaluateMoveWithHeuristics(
        move: Move,
        gameState: GameState,
        config: EvaluationConfig,
        depth: Int,
        context: SearchContext?,
        isolationCache: HashMap<String, Boolean>,
    ): AdvancedMoveEvaluation {
        val basicEvaluation = evaluateBasicMove(move, gameState, config, isolationCache)
        val heuristicBonus = calculateHeuristicBonus(move, basicEvaluation, config, depth, context)

        return basicEvaluation.copy(
            heuristicScore = heuristicBonus,
            totalScore = basicEvaluation.totalScore + heuristicBonus,
        )
    }

    private fun evaluateBasicMove(
        move: Move,
        gameState: GameState,
        config: EvaluationConfig,
        isolationCache: HashMap<String, Boolean>,
    ): AdvancedMoveEvaluation {
        val newState = gameState.applyMove(move)

        val cob = gameState.getCobAtVertex(move.from)
        val leadsToUpgrade = move.isPromotion() || cob?.canUpgrade(move.to) ?: false

        val wouldCauseRepetition = newState.checkIfWouldCauseRepetition(cache.positionHistory)
        val (rocFlips, cobFlips) = move.countFlipsByType(gameState, newState)

        val isImmediateWin =
            newState.isGameOver(cache.positionHistory) && newState.getWinner(cache.positionHistory) == gameState.currentTurn

        val baseScore = calculateBaseScore(newState, wouldCauseRepetition, config)
        val tacticalScore = calculateTacticalScore(rocFlips, cobFlips, leadsToUpgrade, isImmediateWin, config)
        val positionalScore =
            calculatePositionalScore(move, gameState, newState, gameState.currentTurn, config, isolationCache)

        val totalScore = baseScore + tacticalScore + positionalScore

        return AdvancedMoveEvaluation(
            move = move,
            baseScore = baseScore,
            tacticalScore = tacticalScore,
            positionalScore = positionalScore,
            heuristicScore = 0.0,
            totalScore = totalScore,
            flipsRoc = rocFlips,
            flipsCob = cobFlips,
            leadsToUpgrade = leadsToUpgrade,
            isWinningMove = !isImmediateWin && leadsToWinningPosition(newState, gameState.currentTurn, config),
            isImmediateWin = isImmediateWin,
            isImmediateLoss = wouldCauseRepetition,
            mobilityChange = calculateMobilityChange(gameState, newState),
        )
    }

    fun quickEvaluate(gameState: GameState, config: EvaluationConfig): Double =
        gameState.cobs.entries.sumOf { (vertex, cob) ->
            val sign = if (cob.color.isMaximizingPlayer()) 1.0 else -1.0
            val materialValue = if (cob.isUpgraded) config.rocScore else config.cobScore

            val threatScore = cob.countThreats(gameState, vertex) * config.quickThreatWeight
            val (maxRocFlips, maxCobFlips) = cob.countMaxFlipsByType(gameState, vertex)
            val attackScore =
                (maxRocFlips * config.flipRocBonus + maxCobFlips * config.flipCobBonus) * config.quickAttackWeight

            sign * (materialValue - threatScore + attackScore)
        }

    private fun calculateBaseScore(
        newState: GameState,
        wouldCauseRepetition: Boolean,
        config: EvaluationConfig,
    ): Double {
        val quickScore =
            cache.getQuickEvaluation(newState) ?: quickEvaluate(newState, config).also {
                cache.putQuickEvaluation(newState, it)
            }

        val repetitionPenalty =
            if (wouldCauseRepetition) -config.winningScore * config.repetitionPenaltyMultiplier else 0.0

        val stallingPenalty =
            if (newState.halfMoveClock > config.stallingThreshold) {
                val sign = if (newState.currentTurn.opponent.isMaximizingPlayer()) 1.0 else -1.0
                -sign * config.stallingPenalty * (newState.halfMoveClock - config.stallingThreshold)
            } else 0.0

        return quickScore + repetitionPenalty + stallingPenalty
    }

    private fun calculateTacticalScore(
        rocFlips: Int,
        cobFlips: Int,
        leadsToUpgrade: Boolean,
        isImmediateWin: Boolean,
        config: EvaluationConfig,
    ): Double =
        rocFlips * config.flipRocBonus * config.rocFlipMultiplier +
                cobFlips * config.flipCobBonus * config.cobFlipMultiplier +
                (if (leadsToUpgrade) config.upgradeScore * config.upgradeBonusMultiplier else 0.0) +
                (if (isImmediateWin) config.winningScore * config.immediateWinBonusMultiplier else 0.0)

    private fun calculatePositionalScore(
        move: Move,
        gameState: GameState,
        newState: GameState,
        playerColor: CobColor,
        config: EvaluationConfig,
        isolationCache: HashMap<String, Boolean>,
    ): Double {
        var score = 0.0

        val strategicTo = patternPrecomputer.getStrategicValues(move.to, playerColor, config)
        val strategicFrom = patternPrecomputer.getStrategicValues(move.from, playerColor, config)
        score += strategicTo - strategicFrom

        if (move.to in centerVertices) {
            score += config.controlCenterScore * config.controlCenterMultiplier
        }

        val fromMobility = calculateVertexMobility(move.from, gameState)
        val toMobility = calculateVertexMobility(move.to, newState)
        score += (toMobility - fromMobility) * config.mobilityScore * config.mobilityImprovementMultiplier

        score += calculateIsolationPenalty(move, newState, playerColor, config, isolationCache)

        return score
    }

    private fun calculateIsolationPenalty(
        move: Move,
        newState: GameState,
        playerColor: CobColor,
        config: EvaluationConfig,
        isolationCache: HashMap<String, Boolean>,
    ): Double = when {
        newState.cobs[move.to] == null -> 0.0
        isPieceIsolated(move.to, newState, playerColor, isolationCache) -> -config.isolationPenalty
        else -> 0.0
    }

    private fun isPieceIsolated(
        vertex: Vertex,
        gameState: GameState,
        playerColor: CobColor,
        isolationCache: HashMap<String, Boolean>,
    ): Boolean {
        val cacheKey = "${vertex.hashCode()}:${gameState.hashBoard()}:$playerColor"
        return isolationCache.getOrPut(cacheKey) {
            !hasAlliesWithinDistance(vertex, gameState, playerColor) &&
                    hasThreateningEnemies(vertex, gameState, playerColor)
        }
    }

    private fun hasAlliesWithinDistance(
        startVertex: Vertex,
        gameState: GameState,
        playerColor: CobColor,
    ): Boolean {
        val distance1 = GameBoard.adjacencyMap[startVertex] ?: emptyList()
        val distance2 = distance1.flatMap { GameBoard.adjacencyMap[it] ?: emptyList() }.toSet()
        return (distance1 + distance2).any { vertex ->
            vertex != startVertex && gameState.cobs[vertex]?.color == playerColor
        }
    }

    private fun hasThreateningEnemies(
        vertex: Vertex,
        gameState: GameState,
        playerColor: CobColor,
    ): Boolean {
        val enemyColor = playerColor.opponent
        return GameBoard.adjacencyMap[vertex]?.any { adjacentVertex ->
            val enemyCob = gameState.cobs[adjacentVertex]
            enemyCob?.color == enemyColor && enemyCob.canReachVertex(adjacentVertex, vertex)
        } == true
    }

    private fun calculateHeuristicBonus(
        move: Move,
        evaluation: AdvancedMoveEvaluation,
        config: EvaluationConfig,
        depth: Int,
        context: SearchContext?,
    ): Double {
        var bonus = 0.0

        if (isKillerMove(move, depth, context)) {
            bonus += config.killerMoveBaseBonus * (depth + 1)
        }

        val historyScore = context?.historyTable?.get(move.name) ?: 0
        bonus += historyScore * config.historyHeuristicMultiplier

        if (depth > config.lateMoveReductionDepth &&
            !evaluation.isImmediateWin && (evaluation.flipsRoc == 0 && evaluation.flipsCob == 0)
        ) {
            bonus -= config.lateMoveReductionPenalty * depth
        }

        return bonus
    }

    private fun calculateMobilityChange(
        oldState: GameState,
        newState: GameState,
    ): Int = newState.allMovesForTurn().size - oldState.allMovesForTurn().size

    private fun calculateVertexMobility(
        vertex: Vertex,
        gameState: GameState,
    ): Int {
        val cob = gameState.cobs[vertex] ?: return 0
        return cob.calculateMobility(gameState, vertex)
    }

    private fun leadsToWinningPosition(
        gameState: GameState,
        movingPlayer: CobColor,
        config: EvaluationConfig,
    ): Boolean {
        if (gameState.allMovesForTurn().isEmpty()) {
            return gameState.getWinner(cache.positionHistory) == movingPlayer
        }
        val score = quickEvaluate(gameState, config)
        val threshold = config.winningScore * config.winningPositionThreshold
        return if (movingPlayer.isMaximizingPlayer()) score > threshold else score < -threshold
    }

    fun recordKillerMove(
        move: Move,
        depth: Int,
        context: SearchContext?,
    ) {
        context?.killerMoves?.getOrPut(depth) { mutableSetOf() }?.add(move.name)
        context?.killerMoves?.get(depth)?.let { killers ->
            if (killers.size > 2) killers.remove(killers.first())
        }
    }

    fun recordHistoryMove(
        move: Move,
        depth: Int,
        context: SearchContext?,
    ) {
        context?.historyTable?.let { table ->
            val currentScore = table[move.name] ?: 0
            table[move.name] = currentScore + depth * depth
        }
    }

    private fun isKillerMove(
        move: Move,
        depth: Int,
        context: SearchContext?,
    ): Boolean = context?.killerMoves?.get(depth)?.contains(move.name) == true

    private fun reconstructMovesFromCache(
        moves: List<Move>,
        cachedOrder: List<String>,
    ): List<Move> {
        val moveMap = moves.associateBy { it.name }
        return cachedOrder.mapNotNull { moveMap[it] }
    }

    private val maximizingComparator =
        compareByDescending<AdvancedMoveEvaluation> { it.isImmediateWin }
            .thenByDescending { !it.isImmediateLoss }
            .thenByDescending { it.isWinningMove }
            .thenByDescending { it.flipsRoc }
            .thenByDescending { it.flipsCob }
            .thenByDescending { it.leadsToUpgrade }
            .thenByDescending { it.mobilityChange }
            .thenByDescending { it.totalScore }

    private val minimizingComparator =
        compareByDescending<AdvancedMoveEvaluation> { it.isImmediateWin }
            .thenByDescending { !it.isImmediateLoss }
            .thenByDescending { it.isWinningMove }
            .thenBy { it.flipsRoc }
            .thenBy { it.flipsCob }
            .thenBy { it.leadsToUpgrade }
            .thenBy { it.mobilityChange }
            .thenBy { it.totalScore }

    private data class AdvancedMoveEvaluation(
        val move: Move,
        val baseScore: Double,
        val tacticalScore: Double,
        val positionalScore: Double,
        val heuristicScore: Double,
        val totalScore: Double,
        val flipsRoc: Int,
        val flipsCob: Int,
        val leadsToUpgrade: Boolean,
        val isWinningMove: Boolean,
        val isImmediateWin: Boolean,
        val isImmediateLoss: Boolean,
        val mobilityChange: Int,
    )
}