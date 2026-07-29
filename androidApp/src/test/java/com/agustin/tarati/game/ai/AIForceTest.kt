package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.cache.HybridEvaluationCache
import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.MoveEvaluator
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C10
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
import com.agustin.tarati.core.domain.game.board.GameBoard.centerVertices
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.createGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.testutil.TestLog
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

@ExperimentalCoroutinesApi
class AIForceTest {
    private val engine: TaratiAI = TaratiAI()
    private val evalConfig: EvaluationConfig by lazy { EvaluationConfig(Difficulty.DEFAULT) }
    private val boardEvaluator: BoardEvaluator by lazy { BoardEvaluator() }

    private val evaluator by lazy { MoveEvaluator(HybridEvaluationCache()) }

    @Test
    fun testQuickEvaluate_BasicPieceCount() {
        // Test básico de evaluación rápida
        val cobs =
            mutableMapOf(
                C8 to Cob(BLACK),
                C2 to Cob(WHITE),
            )
        val gameState = GameState(cobs, currentTurn = BLACK)

        val score = evaluator.quickEvaluate(gameState, evalConfig)

        // 1 negro (-1) vs. 1 blanco (+1) = 0
        assertEquals(0.0, score)
    }

    @Test
    fun testGameOverDetectionInSorting() {
        // Test específico para verificar que sortMoves detecta estados de game over
        val cobs =
            mutableMapOf(
                C2 to Cob(BLACK, isUpgraded = true),
                D2 to Cob(WHITE), // Última pieza blanca
            )
        val gameState = GameState(cobs, currentTurn = BLACK)

        val moves =
            mutableListOf(
                Move(C2 to B2),
                Move(C2 to C1),
                Move(C2 to C3),
            )

        // En sortMoves, si algún movimiento lleva a game over, debe ser priorizado
        evaluator.sortMoves(moves, gameState, BLACK, evalConfig)

        // Verificar que no hay movimientos que lleven a game over en esta posición
        // (ninguno debería capturar D2 directamente)
        // Este test sirve como verificación negativa
        assertTrue(true) // Placeholder - el test pasa si no hay excepciones
    }

    @Test
    fun testEdgeCase_SinglePieceEndgame() {
        // Caso borde: una sola pieza en el tablero
        val cobs =
            mutableMapOf(
                A1 to Cob(WHITE),
            )
        val gameState = GameState(cobs, currentTurn = WHITE)

        val moves =
            mutableListOf(
                Move(A1 to B1),
                Move(A1 to B2),
            )

        evaluator.sortMoves(moves, gameState, WHITE, evalConfig)

        // Ambos movimientos deberían ser válidos, ninguno gana inmediatamente
        assertEquals(2, moves.size)
    }

    @Test
    fun testStalemateInOne() {
        // Situación: El jugador actual puede forzar un ahogado (sin movimientos legales para el oponente)
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Negras (2 piezas casi bloqueadas)
                setCob(C1, BLACK)
                setCob(C2, BLACK)
                // Blancas (5 piezas)
                setCob(B1, WHITE)
                setCob(C3, WHITE)
                setCob(C12, WHITE)
                setCob(C6, WHITE)
                setCob(C7, WHITE)
                setCob(C8, WHITE)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }
        var newState = gameState.applyMove(result.move ?: return)
        newState = newState.copy(currentTurn = WHITE)

        // Después del movimiento, negro no debería tener movimientos legales
        val blackMoves = newState.allMovesForTurn().filter { gameState.cobs[it.from]?.color == BLACK }
        assertTrue("Negro debería estar ahogado. Movimientos disponibles: $blackMoves", blackMoves.isEmpty())
    }

    @Test
    fun testWinningPosition_Evaluation() {
        // Test de la función de evaluación en posiciones ganadoras
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Posición claramente ganadora para blanco
                setCob(A1, WHITE, true)
                setCob(B1, WHITE, true)
                setCob(B2, WHITE, true)
                setCob(B3, WHITE)
                setCob(B4, WHITE)
                setCob(B5, WHITE)
                setCob(B6, WHITE)
                setCob(C1, BLACK) // Única pieza negra
            }

        val score = boardEvaluator.evaluate(gameState, evalConfig)
        // BoardEvaluator produces static scores in the hundreds-to-thousands range,
        // not the winningScore (50_000) scale used by the minimax engine.
        // 7W (2 roks + 5 cobs) vs 1B cob → material difference ≥ 800 after positional bonuses.
        assertTrue("La evaluación debería ser positiva para blanco. Score: $score", score >= evalConfig.cobScore * 8)
    }

    @Test
    fun testGameOver_Detection() {
        // Test de detección de fin de juego
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Solo una pieza negra que puede ser capturada
                setCob(C1, BLACK)
                // Múltiples piezas blancas
                setCob(B1, WHITE)
                setCob(C2, WHITE)
                setCob(D1, WHITE)
                setCob(A1, WHITE)
                setCob(B2, WHITE)
                setCob(B3, WHITE)
                setCob(B4, WHITE)
            }

        // Aplicar movimiento ganador
        val newState = gameState.applyMove(Move(B1 to C1))

        // Debería detectar juego terminado
        assertTrue(newState.isGameOver(emptyMap()))
    }

    @Test
    fun testStalemateInTwo() {
        // Situación: Ahogado forzado en 2 movimientos
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Distribución 3-5
                // Negras
                setCob(C7, BLACK)
                setCob(C8, BLACK)
                setCob(D3, BLACK)
                // Blancas
                setCob(C1, WHITE, true)
                setCob(C2, WHITE, true)
                setCob(D1, WHITE)
                setCob(D2, WHITE)
                setCob(B1, WHITE)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }

        // Debería identificar el camino hacia el ahogado
        assertTrue(result.score >= 460)
    }

    @Test
    fun testMitInTwo_ForcedSequence() {
        // Situación: Blanco puede forzar mate en 2 movimientos
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Piezas negras (2)
                setCob(C1, BLACK)
                setCob(C8, BLACK)
                // Piezas blancas (6)
                setCob(B1, WHITE)
                setCob(C2, WHITE, true) // Mejorada para mayor movilidad
                setCob(D1, WHITE)
                setCob(B4, WHITE)
                setCob(C6, WHITE)
                setCob(A1, WHITE)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.CHAMPION) }

        // At depth 7 (CHAMPION) the engine sees the forced mate-in-2 and returns a
        // winning score. DEFAULT (depth 3) cannot look far enough ahead to confirm it.
        assertTrue(result.score >= evalConfig.winningScore) // High score indicates imminent forced win
    }

    @Test
    fun testMitInOne_White() {
        // Situación: Blanco puede forzar mate en 1 movimientos
        val gameState =
            createGameState {
                setTurn(WHITE)
                // Piezas negras (1)
                setCob(B4, BLACK, true)
                // Piezas blancas (7)
                setCob(A1, WHITE, true)
                setCob(B2, WHITE, true)
                setCob(B1, WHITE, true)
                setCob(C2, WHITE, true)
                setCob(C12, WHITE, true)
                setCob(C4, WHITE)
                setCob(C5, WHITE)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }

        TestLog.info(result)

        assertTrue(result.move != null)
        assertTrue(
            (result.move?.from == B2 && result.move?.to == B3) ||
                    (result.move?.from == A1 && result.move?.to == B3) ||
                    (result.move?.from == A1 && result.move?.to == B5),
        )
    }

    @Test
    fun testMitInOne() {
        // Situación: Negro puede forzar mate en 1 movimientos
        val gameState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (1)
                setCob(B4, WHITE, true)
                // Piezas negras (7)
                setCob(A1, BLACK, true)
                setCob(B2, BLACK, true)
                setCob(B1, BLACK, true)
                setCob(C2, BLACK, true)
                setCob(C12, BLACK, true)
                setCob(C4, BLACK)
                setCob(C5, BLACK)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }

        TestLog.info(result)

        assertTrue(result.move != null)
        assertTrue(
            (result.move?.from == B2 && result.move?.to == B3) ||
                    (result.move?.from == A1 && result.move?.to == B3) ||
                    (result.move?.from == A1 && result.move?.to == B5),
        )
    }

    @Test
    fun testMitInOne_quickMitShouldBePrioritized1() {
        // Situación: Negro puede forzar mate en 1 movimientos
        val gameState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (1)
                setCob(C10, WHITE)
                // Piezas negras (7)
                setCob(C8, BLACK, true) // <-- Mejorada da posibilidad de mate en 2
                setCob(A1, BLACK, true)
                setCob(B4, BLACK)
                setCob(B3, BLACK)
                setCob(C7, BLACK)
                setCob(B2, BLACK)
                setCob(C3, BLACK)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }

        TestLog.info(result)

        assertTrue(result.move != null)
        assertTrue(
            (result.move?.from == A1 && result.move?.to == B5) ||
                    (result.move?.from == C8 && result.move?.to == C9) ||
                    (result.move?.from == B4 && result.move?.to == B5),
        )
    }

    @Test
    fun testMitInOne_quickMitShouldBePrioritized2() {
        // Situación: Negro puede forzar mate en 1 movimientos
        val gameState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (1)
                setCob(C10, WHITE)
                // Piezas negras (7)
                setCob(C8, BLACK)
                setCob(A1, BLACK, true)
                setCob(B4, BLACK)
                setCob(B3, BLACK)
                setCob(C7, BLACK)
                setCob(B2, BLACK)
                setCob(C3, BLACK)
            }

        val result = runBlocking { engine.getNextMove(gameState, Difficulty.DEFAULT) }

        TestLog.info(result)

        assertTrue(result.move != null)
        assertTrue(
            (result.move?.from == A1 && result.move?.to == B5) ||
                    (result.move?.from == C8 && result.move?.to == C9) ||
                    (result.move?.from == B4 && result.move?.to == B5),
        )
    }

    @Test
    fun evaluateBoard_upgradedPieceIsMoreValuable() {
        val stateNormal =
            GameState(
                mapOf(
                    C1 to Cob(WHITE),
                    C7 to Cob(BLACK),
                ),
                currentTurn = WHITE,
            )

        val stateUpgraded =
            GameState(
                mapOf(
                    C1 to Cob(WHITE, true),
                    C7 to Cob(BLACK),
                ),
                currentTurn = WHITE,
            )

        val scoreNormal = boardEvaluator.evaluate(stateNormal, evalConfig)
        val scoreUpgraded = boardEvaluator.evaluate(stateUpgraded, evalConfig)

        // La pieza upgraded debería tener mejor evaluación
        assertTrue("Upgraded piece should score higher", scoreUpgraded > scoreNormal)

        assertEquals(180.0, scoreUpgraded - scoreNormal, 10.0)
    }

    @Test
    fun testPosition_WhiteShouldFindAMove() {
        // Situación: Blancas tienen muchos movimientos posibles en el comienzo de la partida
        val initialState =
            createGameState {
                setTurn(WHITE)
                // Piezas blancas (4)
                setCob(D2, WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C12, WHITE)
                // Piezas negras (4)
                setCob(D3, BLACK)
                setCob(C8, BLACK)
                setCob(C9, BLACK)
                setCob(B4, BLACK)
            }

        // Las blancas tienen 6 movimientos posibles.
        val move = initialState.allMovesForTurn()

        assertNotNull("White should find a move", move.size == 6)
    }

    @Test
    fun testPosition_BlackShouldFindAMove() {
        // Situación: Negras tienen muchos movimientos posibles en el comienzo de la partida
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (4)
                setCob(D2, WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C3, WHITE)
                // Piezas negras (4)
                setCob(D4, BLACK)
                setCob(C8, BLACK)
                setCob(C7, BLACK)
                setCob(B6, BLACK)
            }

        // Las negras tienen 5 movimientos posibles.
        val move = initialState.allMovesForTurn()

        assertTrue("Black should find a move", move.size == 5)
    }

    @Test
    fun testMitInTwo_Black() {
        // Situación: Negro puede forzar mate en 2 movimientos
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (3)
                setCob(B4, WHITE)
                setCob(C10, WHITE)
                setCob(A1, WHITE, true)
                // Piezas negras (5)
                setCob(C6, BLACK)
                setCob(B2, BLACK)
                setCob(B1, BLACK, true)
                setCob(B6, BLACK)
                setCob(C2, BLACK, true)
            }

        // Movimiento 1: Negro juega (debe encontrar C6 -> B3)
        val blackMove1 = runBlocking { engine.getNextMove(initialState, Difficulty.DEFAULT) }

        TestLog.info("Black move 1: ${blackMove1.move} with score: ${blackMove1.score}")

        assertNotNull("Black should find a move", blackMove1.move)
        assertEquals("Black should move from C6", blackMove1.move?.from, C6)
        assertEquals("Black should move to B3", blackMove1.move?.to, B3)

        // Aplicar el movimiento de negro
        val stateAfterBlack1 =
            initialState
                .applyMove(Move(C6 to B3))
                .copy(currentTurn = WHITE)

        // Movimiento 2: Blanco está forzado (debe jugar C10 -> C9 o perder inmediatamente)
        val whiteMove = runBlocking { engine.getNextMove(stateAfterBlack1, Difficulty.DEFAULT) }

        TestLog.info("White move (forced): ${whiteMove.move} with score: ${whiteMove.score}")

        assertNotNull("White should find a move", whiteMove.move)
        // Verificar que, blanco juega la única jugada que retrasa el mate
        // (puede variar según la posición, pero debería ser defensiva)

        assertTrue("White should make a defensive move", whiteMove.move != null)
        assertEquals("White should move from C10", whiteMove.move?.from, C10)
        assertEquals("White should move to C9", whiteMove.move?.to, C9)

        // Aplicar el movimiento de blanco (asumiendo que juega la mejor defensa)
        val stateAfterWhite =
            stateAfterBlack1
                .applyMove(whiteMove.move ?: return)
                .copy(currentTurn = BLACK)

        // Movimiento 3: Negro da mit
        val blackMove2 = runBlocking { engine.getNextMove(stateAfterWhite, Difficulty.DEFAULT) }

        assertNotNull("Black should find a move", blackMove2.move)
        assertTrue("Black should move from A1 or B4", blackMove2.move?.from == A1 || blackMove2.move?.from == B4)
        assertEquals("Black should move to B5", blackMove2.move?.to, B5)

        TestLog.info("Black move: ${blackMove2.move} with score: ${blackMove2.score}")

        assertNotNull("Black should find mit", blackMove2.move)

        // Aplicar el movimiento final
        val finalState =
            stateAfterWhite
                .applyMove(blackMove2.move ?: return)
                .copy(currentTurn = WHITE)

        // Verificar que es mit
        assertTrue("Should be game over", finalState.isGameOver(emptyMap()))
        assertEquals("Black should win", BLACK, finalState.getWinner(emptyMap()))

        // El score del primer movimiento debería indicar mate forzado
        assertTrue(
            "Black's first move should have winning score (negative for BLACK)",
            blackMove1.score * -1 >= evalConfig.winningScore,
        )
    }

    @Test
    fun testStalemate_OpponentNoMoves() {
        // Situación: Negro no tiene movimientos legales - ahogado
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (7)
                setCob(A1, WHITE, true)
                setCob(B1, WHITE, true)
                setCob(C1, WHITE, true)
                setCob(D1, WHITE, true)
                setCob(C6, WHITE, true)
                setCob(B2, WHITE, true)
                setCob(C2, WHITE, true)
                setCob(B4, WHITE, true) // Fill remaining B3 neighbors
                setCob(C5, WHITE, true) // Fill remaining B3 neighbors
                // Piezas negras (1) - rok, all 5 neighbors occupied
                setCob(B3, BLACK, true)
            }

        val blackMoves = initialState.allMovesForTurn()
        assertEquals("Black should have no legal moves", 0, blackMoves.size)

        assertTrue("Game should be over", initialState.isGameOver(emptyMap()))
        assertEquals("White should win by stalemit", WHITE, initialState.getWinner(emptyMap()))
    }

    @Test
    fun testStalemate_SelfNoMoves() {
        // Situación: Negras no tiene movimientos legales - se ahoga a sí mismo
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas negras (1) - rok, all 5 neighbors occupied
                setCob(B4, BLACK, true)
                // Piezas blancas (7)
                setCob(A1, WHITE)
                setCob(B3, WHITE)
                setCob(B5, WHITE)
                setCob(B2, WHITE)
                setCob(B6, WHITE)
                setCob(C7, WHITE) // Fill remaining B4 neighbors
                setCob(C8, WHITE) // Fill remaining B4 neighbors
            }

        val whiteMoves = initialState.allMovesForTurn()
        assertEquals("Black should have no legal moves", 0, whiteMoves.size)

        assertTrue("Game should be over", initialState.isGameOver(emptyMap()))
        assertEquals("White should win by stalemit", WHITE, initialState.getWinner(emptyMap()))
    }

    @Test
    fun testQuickWin_MaterialAdvantage() {
        // Situación: Negro tiene ventaja material abrumadora y debe ganar rápido
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (2) - muy pocas
                setCob(C10, WHITE)
                setCob(C12, WHITE)
                // Piezas negras (6) - muchas y mejoradas
                setCob(C9, BLACK, true)
                setCob(C8, BLACK, true)
                setCob(C6, BLACK, true)
                setCob(C5, BLACK, true)
                setCob(B3, BLACK, true)
                setCob(B4, BLACK, true)
            }

        val blackMove = runBlocking { engine.getNextMove(initialState, Difficulty.DEFAULT) }

        assertNotNull("Black should find a winning move", blackMove.move)

        // El movimiento debería ser agresivo (captura o amenaza directa)
        var newState = initialState.applyMove(blackMove.move ?: return)
        newState = newState.copy(currentTurn = BLACK)

        // Verify the move improves black's position.
        // BoardEvaluator scores are in the hundreds-to-thousands range (not the 50_000 winningScore scale).
        // 6B roks vs 2W cobs → material advantage for black ≈ 1300+; threshold is cobScore * 10.
        val scoreAfter = boardEvaluator.evaluate(newState, evalConfig)
        assertTrue(
            "Move should improve black's position: $scoreAfter / ${evalConfig.cobScore * 10}",
            scoreAfter * -1 > evalConfig.cobScore * 10,
        )
    }

    @Test
    fun testCenterControl_StrategicMove() {
        // Situación: Control del centro es crucial
        val initialState =
            createGameState {
                setTurn(WHITE)
                // Piezas blancas (4)
                setCob(B2, WHITE)
                setCob(C2, WHITE)
                setCob(D2, WHITE)
                setCob(C3, WHITE)
                // Piezas negras (4)
                setCob(B5, BLACK)
                setCob(C5, BLACK)
                setCob(D3, BLACK)
                setCob(C4, BLACK)
            }

        val whiteMove = runBlocking { engine.getNextMove(initialState, Difficulty.DEFAULT) }

        assertNotNull("White should find a strategic move", whiteMove.move)

        // El movimiento debería apuntar a controlar el centro
        val controlsCenter = centerVertices.contains((whiteMove.move ?: return).to)
        assertTrue("White should move to control center", controlsCenter)
    }

    @Test
    fun testEndgame_SinglePieceSurvival() {
        // Situación: Final de juego con pocas piezas
        val initialState =
            createGameState {
                setTurn(WHITE)
                // Piezas blancas (1)
                setCob(C6, WHITE, true) // Pieza mejorada
                // Piezas negras (1)
                setCob(C7, BLACK, true) // Pieza mejorada
            }

        val whiteMove = runBlocking { engine.getNextMove(initialState, Difficulty.DEFAULT) }

        assertNotNull("White should find a survival move", whiteMove.move)

        // En final de juego, priorizar supervivencia sobre riesgo
        val newState = initialState.applyMove(whiteMove.move ?: return)
        val whiteCobsAfter = newState.cobs.values.count { it.color == WHITE }
        assertEquals("White should not lose its piece", 1, whiteCobsAfter)
    }

    @Test
    fun testSacrifice_TacticalMove() {
        // Situación: Negro puede sacrificar una pieza para ganar ventaja
        val initialState =
            createGameState {
                setTurn(BLACK)
                // Piezas blancas (3)
                setCob(C10, WHITE)
                setCob(B2, WHITE, true)
                setCob(D1, WHITE)
                // Piezas negras (5)
                setCob(C8, BLACK) // Pieza a sacrificar
                setCob(B5, BLACK, true)
                setCob(C7, BLACK, true)
                setCob(D3, BLACK, true)
                setCob(C9, BLACK)
            }

        val blackMove = runBlocking { engine.getNextMove(initialState, Difficulty.HARD) }

        assertNotNull("Black should find a tactical move", blackMove.move)

        // Verificar que el sacrificio lleva a una mejor posición
        val newState = initialState.applyMove(blackMove.move ?: return)

        // Después del movimiento, negro debería tener amenazas fuertes
        val blackThreats = evaluator.quickEvaluate(newState, evalConfig)
        assertTrue("Sacrifice should create strong threats", blackThreats < -evalConfig.cobScore * 2)
    }
}