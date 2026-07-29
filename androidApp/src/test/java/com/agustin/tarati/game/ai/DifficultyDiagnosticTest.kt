package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.MoveEvaluator
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C10
import com.agustin.tarati.core.domain.game.board.GameBoard.C11
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C4
import com.agustin.tarati.core.domain.game.board.GameBoard.C5
import com.agustin.tarati.core.domain.game.board.GameBoard.C6
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.C9
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import com.agustin.tarati.core.domain.game.board.GameBoard.centerVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.homeBases
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.game.ai.tournament.engine.base.standardEngine
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class DifficultyDiagnosticTest {
    private val evalConfig: EvaluationConfig by lazy { EvaluationConfig(Difficulty.DEFAULT) }
    private val boardEvaluator: BoardEvaluator by lazy { BoardEvaluator() }

    private val engine by lazy { standardEngine }
    private val evaluator by lazy { MoveEvaluator(HybridEvaluationCache()) }

    @Before
    fun setup() {
        clearAIHistory()
    }

    private fun setEvaluationConfig(config: EvaluationConfig) {
        engine.setConfig(config)
    }

    private fun clearAIHistory() {
        engine.clearHistory()
    }

    private fun recordRealMove(
        gameState: GameState,
        currentPlayer: CobColor,
    ) {
        engine.putState(gameState, currentPlayer)
    }

    private fun getNextBestMove(
        gameState: GameState,
        difficulty: Difficulty,
    ): MoveEval {
        engine.setConfig(EvaluationConfig.getByDifficulty(difficulty))
        return runBlocking { engine.getNextMove(gameState) }
    }

    /**
     * Test principal: reproduce la secuencia completa y detecta dónde la IA toma malas decisiones
     */
    @Test
    fun diagnoseChampionLosingSequence() {
        setEvaluationConfig(EvaluationConfig.CHAMPION)

        TestLog.info("=" * 80)
        TestLog.info("DIAGNOSTIC: CHAMPION Losing Sequence Analysis")
        TestLog.info("=" * 80)

        val championLosingMoves =
            listOf(
                Move(C2 to C3), // 1. Blancas
                Move(C7 to C6), // 1. Negras
                Move(C1 to B1), // 2. Blancas
                Move(C8 to B4), // 2. Negras - CRITICAL MOVE
                Move(D2 to C2), // 3. Blancas
                Move(D3 to C7), // 3. Negras
                Move(D1 to C1), // 4. Blancas
                Move(B4 to A1), // 4. Negras - CRITICAL MOVE
                Move(C3 to B2), // 5. Blancas
                Move(C6 to B3), // 5. Negras
                Move(C2 to C3), // 6. Blancas
                Move(A1 to B6), // 6. Negras
                Move(B2 to A1), // 7. Blancas - CAPTURE CENTER
                Move(D4 to C8), // 7. Negras
                Move(A1 to B4), // 8. Blancas - MIT
            )

        var gameState = initialGameState()
        var moveNumber = 1
        var criticalMovesFound = 0

        for (i in championLosingMoves.indices) {
            val move = championLosingMoves[i]
            val currentPlayer = gameState.currentTurn
            val isAIMove = currentPlayer == BLACK

            TestLog.info("\n" + "-" * 80)
            TestLog.info("Move $moveNumber: ${currentPlayer.name} (${move.from} -> ${move.to})")
            TestLog.info("-" * 80)

            if (!GameBoard.isValidMove(gameState, move)) {
                TestLog.info("ERROR: Invalid move in sequence!")
                printBoardState(gameState)
                return
            }

            if (isAIMove) {
                val divergence = analyzeAIDecision(gameState, move)
                if (divergence) criticalMovesFound++
            }

            // Apply move
            gameState = gameState.applyMove(move)
            gameState = gameState.copy(currentTurn = currentPlayer.opponent)
            recordRealMove(gameState, currentPlayer)

            printBoardState(gameState)
            printEvaluation(gameState)

            if (gameState.isGameOver(engine.positionHistory)) {
                TestLog.info("\nGAME OVER")
                val winner = gameState.getWinner(engine.positionHistory)
                TestLog.info("Winner: ${winner?.name ?: "DRAW"}")
                break
            }

            if (currentPlayer == BLACK) moveNumber++
        }

        TestLog.info("\n" + "=" * 80)
        TestLog.info("DIAGNOSIS COMPLETE")
        TestLog.info("Critical divergences found: $criticalMovesFound")
        TestLog.info("=" * 80)
    }

    /**
     * Análisis profundo del movimiento crítico #2 de Negras: C8 -> B4
     */
    @Test
    fun analyzeCriticalMove2() {
        TestLog.info("=" * 80)
        TestLog.info("DEEP ANALYSIS: Move 2 Black (C8 -> B4)")
        TestLog.info("=" * 80)

        val gameState = buildStateBeforeMove2Black()

        TestLog.info("\nBoard state before C8 -> B4:")
        printDetailedState(gameState)

        // Analizar con diferentes profundidades
        TestLog.info("\n" + "-" * 80)
        TestLog.info("DEPTH ANALYSIS:")
        TestLog.info("-" * 80)

        for (depth in 2..8 step 2) {
            val tempDifficulty = Difficulty.entries.firstOrNull { it.depth == depth } ?: Difficulty.CHAMPION
            val result = getNextBestMove(gameState, tempDifficulty)

            TestLog.info("\nDepth $depth:")
            TestLog.info("  Best move: ${result.move?.from} -> ${result.move?.to}")
            TestLog.info("  Score: ${result.score}")

            if (result.move?.from == C8 && result.move?.to == B4) {
                TestLog.info("  Status: AI chose the losing move")
            } else {
                TestLog.info("  Status: AI found different move")
            }
        }

        // Evaluar todas las alternativas
        TestLog.info("\n" + "-" * 80)
        TestLog.info("ALL POSSIBLE MOVES EVALUATION:")
        TestLog.info("-" * 80)

        val allMoves = gameState.allMovesForTurn()
        val evaluations =
            allMoves
                .map { move ->
                    val newState =
                        gameState
                            .applyMove(move)
                            .copy(currentTurn = gameState.currentTurn.opponent)

                    val eval = boardEvaluator.evaluate(newState, evalConfig)
                    val whiteResponse = getNextBestMove(newState, Difficulty.CHAMPION)

                    MoveEvaluation(
                        move = move,
                        immediateEval = eval,
                        whiteResponseMove = whiteResponse.move,
                        whiteResponseScore = whiteResponse.score,
                    )
                }.sortedByDescending { it.immediateEval }

        evaluations.forEachIndexed { index, eval ->
            val marker = if (eval.move.from == C8 && eval.move.to == B4) ">>>" else "   "
            TestLog.info("$marker ${index + 1}. ${eval.move.from} -> ${eval.move.to}")
            TestLog.info("       Immediate eval: ${eval.immediateEval}")
            TestLog.info("       White response: ${eval.whiteResponseMove?.from} -> ${eval.whiteResponseMove?.to}")
            TestLog.info("       After response: ${eval.whiteResponseScore}")
        }
    }

    /**
     * Análisis profundo del movimiento crítico #4 de Negras: B4 -> A1
     */
    @Test
    fun analyzeCriticalMove4() {
        TestLog.info("=" * 80)
        TestLog.info("DEEP ANALYSIS: Move 4 Black (B4 -> A1)")
        TestLog.info("=" * 80)

        val gameState = buildStateBeforeMove4Black()

        TestLog.info("\nBoard state before B4 -> A1:")
        printDetailedState(gameState)

        // Evaluar todas las alternativas con simulación de respuesta
        TestLog.info("\n" + "-" * 80)
        TestLog.info("MOVE COMPARISON WITH WHITE RESPONSES:")
        TestLog.info("-" * 80)

        val allMoves = gameState.allMovesForTurn()

        allMoves.forEach { move ->
            TestLog.info("\n${move.from} -> ${move.to}:")

            val afterMove =
                gameState
                    .applyMove(move)
                    .copy(currentTurn = gameState.currentTurn.opponent)

            printMoveDetails(gameState, move)

            val evalAfterMove = boardEvaluator.evaluate(afterMove, evalConfig)
            TestLog.info("  Eval after move: $evalAfterMove")

            // Simular respuesta de blancas
            val whiteResponse = getNextBestMove(afterMove, Difficulty.CHAMPION)
            if (whiteResponse.move != null) {
                TestLog.info("  White responds: ${whiteResponse.move?.from} -> ${whiteResponse.move?.to}")

                val afterWhiteResponse =
                    afterMove
                        .applyMove(
                            whiteResponse.move ?: return@forEach,
                        ).copy(currentTurn = afterMove.currentTurn.opponent)

                val finalEval = boardEvaluator.evaluate(afterWhiteResponse, evalConfig)
                TestLog.info("  Eval after response: $finalEval")
                TestLog.info("  Net change: ${finalEval - evalAfterMove}")

                if (afterWhiteResponse.isGameOver(engine.positionHistory)) {
                    val winner = afterWhiteResponse.getWinner(engine.positionHistory)
                    TestLog.info("  WARNING: Game ends! Winner: ${winner?.name}")
                }
            }
        }
    }

    /**
     * Compara los componentes de evaluación para diferentes movimientos
     */
    @Test
    fun compareEvaluationComponents() {
        TestLog.info("=" * 80)
        TestLog.info("EVALUATION COMPONENTS BREAKDOWN")
        TestLog.info("=" * 80)

        val gameState = buildStateBeforeMove2Black()

        val testMoves =
            listOf(
                Move(C8 to B4), // El movimiento que hace
                Move(C8 to C7), // Alternativa 1
                Move(D4 to C8), // Alternativa 2
                Move(C6 to B3), // Alternativa 3
            )

        testMoves.forEach { move ->
            TestLog.info("\n" + "=" * 60)
            TestLog.info("Move: ${move.from} -> ${move.to}")
            TestLog.info("=" * 60)

            val newState =
                gameState
                    .applyMove(move)
                    .copy(currentTurn = gameState.currentTurn.opponent)

            val config = EvaluationConfig.CHAMPION
            val metrics = boardEvaluator.evaluateMetrics(newState, config)

            TestLog.info("\nMaterial:")
            TestLog.info("  White: ${metrics.material.white} | Black: ${metrics.material.black}")
            TestLog.info("  Difference: ${metrics.material.difference}")

            TestLog.info("\nCenter Control:")
            TestLog.info("  White: ${metrics.centerControl.white} | Black: ${metrics.centerControl.black}")
            val centerScore = metrics.centerControl.difference * config.controlCenterScore
            TestLog.info("  Score contribution: $centerScore")

            TestLog.info("\nMobility:")
            TestLog.info("  White: ${metrics.mobility.white} | Black: ${metrics.mobility.black}")
            val mobilityScore = metrics.mobility.difference * config.mobilityScore
            TestLog.info("  Score contribution: $mobilityScore")

            TestLog.info("\nOpponent Base Pressure:")
            TestLog.info("  White: ${metrics.opponentPressure.white} | Black: ${metrics.opponentPressure.black}")
            val pressureScore = metrics.opponentPressure.difference * config.opponentDomesticPressureScore
            TestLog.info("  Score contribution: $pressureScore")

            TestLog.info("\nHome Base Control:")
            TestLog.info("  White: ${metrics.homeControl.white} | Black: ${metrics.homeControl.black}")
            val homeScore = metrics.homeControl.difference * config.domesticControlScore
            TestLog.info("  Score contribution: $homeScore")

            TestLog.info("\nUpgrade Opportunities:")
            TestLog.info("  White: ${metrics.upgradeOpportunities.white} | Black: ${metrics.upgradeOpportunities.black}")
            val upgradeScore = metrics.upgradeOpportunities.difference * config.upgradeScore
            TestLog.info("  Score contribution: $upgradeScore")

            val totalEval = boardEvaluator.evaluate(newState, config)
            TestLog.info("\nTOTAL EVALUATION: $totalEval")
        }
    }

    // ==================== Helper Functions ====================

    private fun analyzeAIDecision(
        gameState: GameState,
        actualMove: Move,
    ): Boolean {
        TestLog.info("\nAI DECISION ANALYSIS:")

        val allMoves = gameState.allMovesForTurn()
        TestLog.info("  Possible moves: ${allMoves.size}")

        val result = getNextBestMove(gameState, Difficulty.CHAMPION)
        val aiBestMove = result.move
        val aiScore = result.score

        TestLog.info("  AI chose: ${aiBestMove?.from} -> ${aiBestMove?.to} (score: $aiScore)")
        TestLog.info("  Actual was: ${actualMove.from} -> ${actualMove.to}")

        val divergence = aiBestMove?.from != actualMove.from || aiBestMove.to != actualMove.to

        if (divergence) {
            TestLog.info("  STATUS: DIVERGENCE DETECTED")

            var aiEval = 0.0
            if (aiBestMove != null) {
                val aiNewState =
                    gameState
                        .applyMove(aiBestMove)
                        .copy(currentTurn = gameState.currentTurn.opponent)
                aiEval = boardEvaluator.evaluate(aiNewState, evalConfig)
                TestLog.info("\n  AI preferred move evaluation:")
                TestLog.info("    Move: ${aiBestMove.from} -> ${aiBestMove.to}")
                TestLog.info("    Eval: $aiEval")
                printMoveDetails(gameState, aiBestMove)
            }

            val actualNewState =
                gameState
                    .applyMove(actualMove)
                    .copy(currentTurn = gameState.currentTurn.opponent)
            val actualEval = boardEvaluator.evaluate(actualNewState, evalConfig)
            TestLog.info("\n  Actual move evaluation:")
            TestLog.info("    Move: ${actualMove.from} -> ${actualMove.to}")
            TestLog.info("    Eval: $actualEval")
            printMoveDetails(gameState, actualMove)

            TestLog.info("\n  Evaluation difference: ${if (aiBestMove != null) aiEval - actualEval else "N/A"}")
        } else {
            TestLog.info("  STATUS: AI chose expected move")
        }

        return divergence
    }

    private fun printMoveDetails(
        gameState: GameState,
        move: Move,
    ) {
        val newState = gameState.applyMove(move)
        val movedCob = gameState.cobs[move.from] ?: return

        var capturedPieces = 0
        var upgradedCaptured = 0
        for (vertex in adjacencyMap[move.to] ?: emptyList()) {
            val cob = gameState.cobs[vertex]
            if (cob != null && cob.color != movedCob.color) {
                capturedPieces++
                if (cob.isUpgraded) upgradedCaptured++
            }
        }

        val enemyBase = homeBases[movedCob.color.opponent] ?: emptyList()
        val willUpgrade = move.to in enemyBase && !movedCob.isUpgraded

        TestLog.info("    Captures: $capturedPieces (Upgraded: $upgradedCaptured)")
        TestLog.info("    Upgrades: ${if (willUpgrade) "YES" else "NO"}")
        TestLog.info("    Center control: ${if (move.to in centerVertices) "YES" else "NO"}")

        val mobility = newState.copy(currentTurn = movedCob.color).allMovesForTurn().size
        TestLog.info("    Resulting mobility: $mobility moves")

        if (newState.isGameOver(engine.positionHistory)) {
            TestLog.info("    WARNING: GAME ENDS - Winner: ${newState.getWinner(engine.positionHistory)?.name}")
        }
    }

    private fun printBoardState(gameState: GameState) {
        TestLog.info("\nBoard state:")
        val whiteCobs = gameState.cobs.values.count { it.color == WHITE }
        val blackCobs = gameState.cobs.values.count { it.color == BLACK }
        val whiteUpgraded = gameState.cobs.values.count { it.color == WHITE && it.isUpgraded }
        val blackUpgraded = gameState.cobs.values.count { it.color == BLACK && it.isUpgraded }

        TestLog.info("  White: $whiteCobs pieces ($whiteUpgraded upgraded)")
        TestLog.info("  Black: $blackCobs pieces ($blackUpgraded upgraded)")

        val whitesInCenter =
            gameState.cobs.entries.count {
                it.key in centerVertices && it.value.color == WHITE
            }
        val blacksInCenter =
            gameState.cobs.entries.count {
                it.key in centerVertices && it.value.color == BLACK
            }
        TestLog.info("  Center: W:$whitesInCenter B:$blacksInCenter")
    }

    private fun printDetailedState(gameState: GameState) {
        TestLog.info("Current turn: ${gameState.currentTurn.name}")
        TestLog.info("Pieces on board:")
        gameState.cobs.entries
            .sortedBy { it.key.zone.name }
            .sortedBy { it.key.position }
            .forEach { (pos, cob) ->
                val upgraded = if (cob.isUpgraded) "[U]" else "   "
                val color = if (cob.color == WHITE) "W" else "B"
                TestLog.info("  $pos: $color $upgraded")
            }
        printBoardState(gameState)
    }

    private fun printEvaluation(gameState: GameState) {
        val eval = boardEvaluator.evaluate(gameState, evalConfig)
        val quickEval = evaluator.quickEvaluate(gameState, evalConfig)
        TestLog.info("\nFull evaluation: $eval")
        TestLog.info("Quick evaluation: $quickEval")

        val advantage =
            when {
                eval > 100 -> "White significant advantage"
                eval > 50 -> "White moderate advantage"
                eval > 10 -> "White slight advantage"
                eval > -10 -> "Balanced position"
                eval > -50 -> "Black slight advantage"
                eval > -100 -> "Black moderate advantage"
                else -> "Black significant advantage"
            }
        TestLog.info(advantage)
    }

    private fun buildStateBeforeMove2Black(): GameState {
        var state = initialGameState()

        val setupMoves =
            listOf(
                Move(C2 to C3),
                Move(C7 to C6),
                Move(C1 to B1),
            )

        for (move in setupMoves) {
            val currentPlayer = state.currentTurn
            state = state.applyMove(move)
            state = state.copy(currentTurn = currentPlayer.opponent)
            recordRealMove(state, currentPlayer)
        }

        return state
    }

    private fun buildStateBeforeMove4Black(): GameState {
        var state = initialGameState()

        val setupMoves =
            listOf(
                Move(C2 to C3),
                Move(C7 to C6),
                Move(C1 to B1),
                Move(C8 to B4),
                Move(D2 to C2),
                Move(D3 to C7),
                Move(D1 to C1),
            )

        for (move in setupMoves) {
            val currentPlayer = state.currentTurn
            state = state.applyMove(move)
            state = state.copy(currentTurn = currentPlayer.opponent)
            recordRealMove(state, currentPlayer)
        }

        return state
    }

    // ==================== Data Classes ====================

    private data class MoveEvaluation(
        val move: Move,
        val immediateEval: Double,
        val whiteResponseMove: Move?,
        val whiteResponseScore: Double,
    )

    // ==================== Extensions ====================

    private operator fun String.times(n: Int): String = this.repeat(n)

    /**
     * Test para analizar la partida donde el nivel MEDIUM pierde
     */
    @Test
    fun diagnoseMediumLosingSequence() {
        setEvaluationConfig(EvaluationConfig.MEDIUM)

        TestLog.info("=" * 80)
        TestLog.info("DIAGNOSTIC: MEDIUM Losing Sequence Analysis")
        TestLog.info("=" * 80)

        val mediumLosingMoves =
            listOf(
                Move(C2 to C3), // 1. Blancas
                Move(C7 to C6), // 1. Negras
                Move(D2 to C2), // 2. Blancas
                Move(C8 to C9), // 2. Negras - CRITICAL MOVE
                Move(C1 to B1), // 3. Blancas
                Move(D3 to C7), // 3. Negras
                Move(D1 to C1), // 4. Blancas
                Move(C6 to C5), // 4. Negras
                Move(C3 to C4), // 5. Blancas
                Move(C7 to C6), // 5. Negras
                Move(B1 to B6), // 6. Blancas
                Move(C9 to B5), // 6. Negras - CRITICAL MOVE
                Move(C1 to C12), // 7. Blancas
                Move(D4 to C8), // 7. Negras
                Move(C12 to C11), // 8. Blancas
                Move(C8 to B4), // 8. Negras
                Move(C11 to C10), // 9. Blancas
                Move(C6 to B3), // 9. Negras
                Move(B6 to A1), // 10. Blancas - CRITICAL MOVE
            )

        var gameState = initialGameState()
        var moveNumber = 1
        var criticalMovesFound = 0

        for (i in mediumLosingMoves.indices) {
            val move = mediumLosingMoves[i]
            val currentPlayer = gameState.currentTurn
            val isAIMove = currentPlayer == BLACK

            TestLog.info("\n" + "-" * 80)
            TestLog.info("Move $moveNumber: ${currentPlayer.name} (${move.from} -> ${move.to})")
            TestLog.info("-" * 80)

            if (!GameBoard.isValidMove(gameState, move)) {
                TestLog.info("ERROR: Invalid move in sequence!")
                printBoardState(gameState)
                return
            }

            if (isAIMove) {
                val divergence = analyzeAIDecision(gameState, move)
                if (divergence) criticalMovesFound++
            }

            // Apply move
            gameState = gameState.applyMove(move)
            gameState = gameState.copy(currentTurn = currentPlayer.opponent)
            recordRealMove(gameState, currentPlayer)

            printBoardState(gameState)
            printEvaluation(gameState)

            if (gameState.isGameOver(engine.positionHistory)) {
                TestLog.info("\nGAME OVER")
                val winner = gameState.getWinner(engine.positionHistory)
                TestLog.info("Winner: ${winner?.name ?: "DRAW"}")
                break
            }

            if (currentPlayer == BLACK) moveNumber++
        }

        TestLog.info("\n" + "=" * 80)
        TestLog.info("MEDIUM DIAGNOSIS COMPLETE")
        TestLog.info("Critical divergences found: $criticalMovesFound")
        TestLog.info("=" * 80)
    }

    @Test
    fun diagnoseRulesErrorSequence() {
        TestLog.info("=" * 80)
        TestLog.info("DIAGNOSTIC: Rules Error Sequence Analysis")
        TestLog.info("=" * 80)

        val errorInRulesMoves =
            listOf(
                Move(C1 to C12), // 1. Blancas
                Move(C7 to C6), // 1. Negras
                Move(C2 to C3), // 2. Blancas
                Move(C8 to B4), // 2. Negras
                Move(D2 to C2), // 3. Blancas
                Move(C6 to C5), // 3. Negras
                Move(C3 to C4), // 4. Blancas
                Move(B4 to B3), // 4. Negras
                Move(C2 to B1), // 5. Blancas
                Move(B3 to B2), // 5. Negras
                Move(C12 to B6), // 6. Blancas
                Move(C4 to C3), // 6. Negras
                Move(B1 to A1), // 7. Blancas
                Move(D3 to C7), // 7. Negras
                Move(A1 to B4), // 8. Blancas
                Move(D4 to C8), // 8. Negras
                Move(B6 to A1), // 9. Blancas
                Move(C5 to C4), // 9. Negras
                Move(A1 to B5), // 10. Blancas
                Move(B2 to B1), // 10. Negras
                Move(B5 to C9), // 11. Blancas
                Move(B1 to C1), // 11. Negras
                Move(C8 to D4), // 12. Blancas
                Move(C7 to C8), // 12. Negras - RULES ERROR MOVE
            )

        var gameState = initialGameState()
        var moveNumber = 1

        TestLog.info("\nSPECIAL ANALYSIS: Rules verification on move 24 (C7 -> C8)")
        TestLog.info("Expected captures: D4, C9, B4")
        TestLog.info("=" * 60)

        for (i in errorInRulesMoves.indices) {
            val move = errorInRulesMoves[i]
            val currentPlayer = gameState.currentTurn

            TestLog.info("\nMove ${i + 1}: ${currentPlayer.name} (${move.from} -> ${move.to})")

            if (!GameBoard.isValidMove(gameState, move)) {
                TestLog.info("ERROR: Invalid move in sequence!")
                printBoardState(gameState)
                return
            }

            // Special analysis for the rules error move
            if (i == 23) { // Move 24: C7 -> C8 (índice 23 en la lista)
                TestLog.info("\n" + "!" * 60)
                TestLog.info("RULES VERIFICATION - Move 24: C7 -> C8")
                TestLog.info("!" * 60)

                val beforeState = gameState
                TestLog.info("Board state BEFORE move:")
                printDetailedState(beforeState)

                // Analyze expected captures
                val expectedCaptures = listOf("D4", "C9", "B4")
                TestLog.info("\nExpected captures (flips): $expectedCaptures")

                val adjacentVertices = adjacencyMap[C8] ?: emptyList()
                TestLog.info("Adjacent vertices to C8: $adjacentVertices")

                adjacentVertices.forEach { vertex ->
                    val cob = beforeState.cobs[vertex]
                    if (cob != null && cob.color == WHITE) {
                        TestLog.info("Should flip $vertex (${cob.color.name} ${if (cob.isUpgraded) "Rok" else "Cob"})")
                    }
                }
            }

            // Apply move
            val previousState = gameState
            gameState = gameState.applyMove(move)
            gameState = gameState.copy(currentTurn = currentPlayer.opponent)
            recordRealMove(gameState, currentPlayer)

            // Check captures after move 24
            if (i == 23) {
                TestLog.info("\nBoard state AFTER move:")
                printDetailedState(gameState)

                val expectedCaptures = listOf(D4, C9, B4)

                TestLog.info("\nCAPTURE RESULTS:")

                // Check for flipped pieces (changed color)
                val flippedPieces =
                    expectedCaptures.filter { vertex ->
                        val beforeCob = previousState.cobs[vertex]
                        val afterCob = gameState.cobs[vertex]
                        beforeCob != null && afterCob != null && beforeCob.color != afterCob.color
                    }

                val notFlippedPieces =
                    expectedCaptures.filter { vertex ->
                        val beforeCob = previousState.cobs[vertex]
                        val afterCob = gameState.cobs[vertex]
                        beforeCob != null && (afterCob == null || beforeCob.color == afterCob.color)
                    }

                if (flippedPieces.isNotEmpty()) {
                    TestLog.info("SUCCESS: Pieces flipped (captured and converted): $flippedPieces")
                    flippedPieces.forEach { vertex ->
                        val beforeCob = previousState.cobs[vertex]
                        val afterCob = gameState.cobs[vertex] ?: return@forEach
                        TestLog.info("  $vertex: ${beforeCob?.color}->${afterCob.color} (${if (afterCob.isUpgraded) "Rok" else "Cob"})")
                    }
                }

                if (notFlippedPieces.isNotEmpty()) {
                    TestLog.info("ERROR: These pieces should be flipped but aren't: $notFlippedPieces")
                    notFlippedPieces.forEach { vertex ->
                        val beforeCob = previousState.cobs[vertex]
                        val afterCob = gameState.cobs[vertex]
                        TestLog.info("  $vertex: Before=${beforeCob?.color}->After=${afterCob?.color}")
                    }
                }

                // Verify the moving piece
                val movedPiece = gameState.cobs[C8]
                TestLog.info("Piece at C8 after move: ${movedPiece?.color?.name} ${if (movedPiece?.isUpgraded == true) "Rok" else "Cob"}")

                // Count the results
                val blackPieces = gameState.cobs.values.count { it.color == BLACK }
                val whitePieces = gameState.cobs.values.count { it.color == WHITE }
                TestLog.info("Piece count after move - Black: $blackPieces, White: $whitePieces")

                if (flippedPieces.size == expectedCaptures.size) {
                    TestLog.info("SUCCESS: All expected pieces were flipped!")
                } else {
                    TestLog.info("ISSUE: Only ${flippedPieces.size} out of ${expectedCaptures.size} pieces were flipped")
                }
            }

            if (gameState.isGameOver(engine.positionHistory)) {
                TestLog.info("\nGAME OVER at move ${i + 1}")
                val winner = gameState.getWinner(engine.positionHistory)
                TestLog.info("Winner: ${winner?.name ?: "DRAW"}")

                val blackPieces = gameState.cobs.values.count { it.color == BLACK }
                val whitePieces = gameState.cobs.values.count { it.color == WHITE }
                TestLog.info("Final piece count - Black: $blackPieces, White: $whitePieces")

                if (winner == BLACK && blackPieces > whitePieces) {
                    TestLog.info("Black wins as expected with piece advantage")
                } else {
                    TestLog.info("UNEXPECTED RESULT")
                }
                break
            }

            if (currentPlayer == BLACK) moveNumber++
        }

        TestLog.info("\n" + "=" * 80)
        TestLog.info("RULES VERIFICATION COMPLETE")
        TestLog.info("Final board state:")
        printDetailedState(gameState)

        // Final analysis
        val blackPieces = gameState.cobs.values.count { it.color == BLACK }
        val whitePieces = gameState.cobs.values.count { it.color == WHITE }
        TestLog.info("Final piece count - Black: $blackPieces, White: $whitePieces")
        TestLog.info("Black advantage: ${blackPieces - whitePieces} pieces")

        if (blackPieces > whitePieces + 2) {
            TestLog.info("SUCCESS: Triple capture working correctly!")
        } else {
            TestLog.info("ISSUE: Capture logic may still have problems")
        }
        TestLog.info("=" * 80)
    }
}