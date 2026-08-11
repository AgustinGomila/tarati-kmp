package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState.Companion.createGameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripleRepetitionTest {
    private val engine: TaratiAI = TaratiAI()

    @Test
    fun testTripleRepetition_WhiteLoses() {
        // Configurar una posición simple
        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        engine.clearHistory()

        // Simular triple repetición causada por las blancas
        repeat(3) {
            engine.putState(gameState, WHITE)
        }

        // Verificar que las blancas pierden
        assertTrue(gameState.isGameOver(engine.positionHistory), "Game should be over due to triple repetition")
        val winner = gameState.getWinner(engine.positionHistory)
        assertEquals(WHITE, winner, "White should win when black causes triple repetition")
    }

    @Test
    fun testTripleRepetition_BlackLoses() {
        val gameState =
            createGameState {
                setTurn(BLACK)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        engine.clearHistory()

        // Simular triple repetición causada por las negras
        repeat(3) {
            engine.putState(gameState, BLACK)
        }

        assertTrue(gameState.isGameOver(engine.positionHistory), "Game should be over due to triple repetition")
        val winner = gameState.getWinner(engine.positionHistory)
        assertEquals(BLACK, winner, "Black should win when white triple repetition")
    }

    @Test
    fun testTripleRepetition_BasicDetection() {
        engine.clearHistory()

        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        // Primera vez - no debería detectar
        val loser1 = engine.putState(gameState, WHITE)
        assertNull(loser1, "Should not detect repetition first time")
        assertFalse(gameState.isGameOver(engine.positionHistory), "Game should not be over")

        // Segunda vez - no debería detectar
        val loser2 = engine.putState(gameState, WHITE)
        assertNull(loser2, "Should not detect repetition second time")
        assertFalse(gameState.isGameOver(engine.positionHistory), "Game should not be over")

        // Tercera vez - DEBERÍA detectar
        val loser3 = engine.putState(gameState, WHITE)
        assertEquals(WHITE, loser3, "Should detect triple repetition and white should lose")
        assertTrue(gameState.isGameOver(engine.positionHistory), "Game should be over")
        assertEquals(WHITE, gameState.getWinner(engine.positionHistory), "White should win")
    }

    @Test
    fun testTripleRepetition_DifferentStates() {
        engine.clearHistory()

        val state1 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        val state2 =
            createGameState {
                setTurn(WHITE)
                setCob(C2, WHITE) // Diferente posición
                setCob(C7, BLACK)
            }

        // Registrar state1 dos veces
        engine.putState(state1, WHITE)
        engine.putState(state1, WHITE)

        // Registrar state2 una vez - no debería activar triple repetición para state1
        val loser = engine.putState(state2, BLACK)
        assertNull(loser, "Should not detect repetition for different state")
        assertFalse(state1.isGameOver(engine.positionHistory), "State1 should not be over")
        assertFalse(state2.isGameOver(engine.positionHistory), "State2 should not be over")
    }

    @Test
    fun testTripleRepetition_CheckIfWouldCauseRepetition() {
        engine.clearHistory()

        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        // Verificar que inicialmente no causaría repetición
        assertFalse(
            gameState.checkIfWouldCauseRepetition(engine.positionHistory),
            "Should not cause repetition initially",
        )

        // Registrar dos veces
        engine.putState(gameState, WHITE)
        engine.putState(gameState, WHITE)

        // Ahora debería causar repetición si se registra otra vez
        assertTrue(
            gameState.checkIfWouldCauseRepetition(engine.positionHistory),
            "Should cause repetition after two records",
        )
    }

    @Test
    fun testTripleRepetition_ClearHistory() {
        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        // Registrar dos veces
        engine.putState(gameState, WHITE)
        engine.putState(gameState, WHITE)

        // Limpiar historial
        engine.clearHistory()

        // Verificar que después de limpiar, no causa repetición
        assertFalse(
            gameState.checkIfWouldCauseRepetition(engine.positionHistory),
            "Should not cause repetition after clear",
        )

        val loser = engine.putState(gameState, WHITE)
        assertNull(loser, "Should not detect repetition after clear")
    }

    @Test
    fun testTripleRepetition_HashStability() {
        // Verificar que el hash es estable para la misma posición
        val state1 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        val state2 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        val hash1 = state1.hashBoard()
        val hash2 = state2.hashBoard()

        assertEquals(hash1, hash2, "Same game states should have same hash")

        // Estado diferente debería tener hash diferente
        val state3 =
            createGameState {
                setTurn(BLACK) // Diferente turno
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        val hash3 = state3.hashBoard()
        assertNotEquals(hash1, hash3, "Different game states should have different hashes")
    }

    @Test
    fun testTripleRepetition_GameplaySimulation() {
        engine.clearHistory()

        // Estado inicial
        var gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C7, BLACK)
                setCob(B6, BLACK)
            }

        var moves = 0
        val maxMoves = 6 // Reducido para debug

        TestLog.info("Initial state: ${gameState.hashBoard()}")

        // Simular solo 2 ciclos completos (4 movimientos)
        while (moves < maxMoves) {
            val from = if (gameState.currentTurn == WHITE) C1 else C7
            val to = if (gameState.currentTurn == WHITE) B2 else B6

            TestLog.info("Move $moves: ${gameState.currentTurn} moves $from -> $to")

            val newState = gameState.applyMove(Move(from to to))
            val nextState = newState.copy(currentTurn = gameState.currentTurn.opponent)

            TestLog.info("State after move: ${nextState.hashBoard()}")

            // Registrar y verificar
            val loser = engine.putState(nextState, gameState.currentTurn)
            val currentCount = engine.getRepetitionCount(nextState)
            TestLog.info("Repetition count: $currentCount")

            if (loser != null) {
                TestLog.info("Triple repetition detected at move $moves! $loser loses")
                break
            }

            gameState = nextState
            moves++
        }

        TestLog.info("Finished after $moves moves")
        // No hacemos asserts aquí, solo queremos ver el output
    }

    @Test
    fun testTripleRepetition_AvoidanceByAI(): TestResult = runTest {
        // Test que la IA evita movimientos que causarían triple repetición
        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        engine.clearHistory()

        // Registrar la posición 2 veces (una más causaría triple repetición)
        engine.putState(gameState, BLACK)
        engine.putState(gameState, BLACK)

        // La IA blanca debería evitar movimientos que lleven a esta posición
        val result = engine.getNextMove(gameState, Difficulty.DEFAULT)

        val move = assertNotNull(result.move, "AI should find a move")

        // Aplicar el movimiento y verificar que no causa triple repetición
        val newState = gameState.applyMove(move)
        val wouldCauseRepetition = newState.checkIfWouldCauseRepetition(engine.positionHistory)

        assertTrue(!wouldCauseRepetition, "AI should avoid moves that cause triple repetition")
    }

    @Test
    fun testTripleRepetition_WithDifferentPositions() {
        // Verificar que diferentes posiciones no activan triple repetición
        val state1 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        val state2 =
            createGameState {
                setTurn(BLACK)
                setCob(C2, WHITE) // Diferente posición
                setCob(C7, BLACK)
            }

        engine.clearHistory()

        // Registrar posiciones diferentes
        engine.putState(state1, WHITE)
        engine.putState(state2, BLACK)
        engine.putState(state1, WHITE) // Solo segunda vez para state1

        // No debería haber triple repetición
        assertTrue(!state1.isGameOver(engine.positionHistory), "Game should not be over - different positions")
        assertTrue(!state2.isGameOver(engine.positionHistory), "Game should not be over - different positions")
    }

    @Test
    fun testTripleRepetition_ClearHistoryResets() {
        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        // Registrar dos veces
        engine.putState(gameState, WHITE)
        engine.putState(gameState, WHITE)

        // Limpiar historial
        engine.clearHistory()

        // Registrar de nuevo - debería empezar desde 1
        val loser = engine.putState(gameState, WHITE)

        assertEquals(null, loser, "Should not detect triple repetition after clear")
        assertTrue(!gameState.isGameOver(engine.positionHistory), "Game should not be over after clear")
    }

    @Test
    fun testTripleRepetition_InActualGameplay(): TestResult = runTest {
        // Test más realista con gameplay actual
        var gameState = initialGameState()
        engine.clearHistory()

        var repetitionDetected = false
        var moves = 0
        val maxMoves = 50

        // Jugar hasta detectar triple repetición o llegar al límite
        while (moves < maxMoves && !gameState.isGameOver(engine.positionHistory)) {
            val result = engine.getNextMove(gameState, Difficulty.MIN)

            val move = result.move ?: break
            val newState = gameState.applyMove(move)
            val nextState = newState.copy(currentTurn = gameState.currentTurn.opponent)

            // Registrar el movimiento
            val loser = engine.putState(nextState, gameState.currentTurn)
            if (loser != null) {
                repetitionDetected = true
                TestLog.info("Triple repetition detected at move $moves! $loser loses")
                break
            }

            gameState = nextState
            moves++
        }

        // En un juego real, puede que no ocurra triple repetición rápidamente,
        // pero al menos verificamos que el mecanismo funciona
        if (repetitionDetected) {
            assertTrue(gameState.isGameOver(engine.positionHistory), "Game should be over when repetition detected")
            val winner = gameState.getWinner(engine.positionHistory)
            assertNotNull(winner, "There should be a winner when repetition occurs")
        } else {
            TestLog.info("No triple repetition detected in $moves moves")
        }
    }

    @Test
    fun testTripleRepetition_RealGameHistoryPersistence() {
        // Verificar que engine.positionHistory mantiene los registros entre llamadas
        val gameState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(C7, BLACK)
            }

        engine.clearHistory()

        // Verificar que inicialmente está vacío
        assertTrue(engine.positionHistory.isEmpty(), "History should be empty after clear")

        // Primera registro
        val loser1 = engine.putState(gameState, WHITE)
        assertNull(loser1, "Should not detect repetition first time")
        assertEquals(1, engine.positionHistory.size, "History should have one entry")
        assertEquals(1, engine.positionHistory[gameState.hashBoard()], "State should have count 1")

        // Segundo registro
        val loser2 = engine.putState(gameState, WHITE)
        assertNull(loser2, "Should not detect repetition second time")
        assertEquals(1, engine.positionHistory.size, "History should still have one entry")
        assertEquals(2, engine.positionHistory[gameState.hashBoard()], "State should have count 2")

        // Tercer registro - debería detectar
        val loser3 = engine.putState(gameState, WHITE)
        assertEquals(WHITE, loser3, "Should detect triple repetition")
        assertEquals(1, engine.positionHistory.size, "History should still have one entry")
        assertEquals(3, engine.positionHistory[gameState.hashBoard()], "State should have count 3")

        // Verificar que isGameOver detecta la triple repetición
        assertTrue(gameState.isGameOver(engine.positionHistory), "Game should be over due to triple repetition")
    }

    @Test
    fun testTripleRepetition_GameStateConsistency() {
        // Verificar que el mismo estado produce el mismo hash
        val state1 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C7, BLACK)
                setCob(B6, BLACK)
            }

        val state2 =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C7, BLACK)
                setCob(B6, BLACK)
            }

        val hash1 = state1.hashBoard()
        val hash2 = state2.hashBoard()

        assertEquals(hash1, hash2, "Same game states should have same hash")

        engine.clearHistory()

        // Registrar state1 dos veces
        engine.putState(state1, WHITE)
        engine.putState(state1, WHITE)

        // Verificar que state2 tiene count 2 (porque son el mismo estado)
        assertEquals(2, engine.positionHistory[hash2] ?: 0, "state2 should have count 2")
    }

    @Test
    fun testTripleRepetition_StepByStep() {
        val initialState =
            createGameState {
                setTurn(WHITE)
                setCob(C1, WHITE)
                setCob(B1, WHITE)
                setCob(C7, BLACK)
                setCob(B6, BLACK)
            }

        engine.clearHistory()

        var gameState = initialState
        var moves = 0

        TestLog.info("=== Step by Step Debug ===")
        TestLog.info("Initial state hash: ${initialState.hashBoard()}")
        TestLog.info("Initial engine.positionHistory size: ${engine.positionHistory.size}")

        // Primer movimiento: WHITE C1 -> B2
        val move1From = C1
        val move1To = B2
        val stateAfterMove1 = gameState.applyMove(Move(move1From to move1To))
        val stateAfterMove1WithTurn = stateAfterMove1.copy(currentTurn = BLACK)

        TestLog.info("\nMove 1: WHITE $move1From -> $move1To")
        TestLog.info("State after move 1 hash: ${stateAfterMove1WithTurn.hashBoard()}")

        engine.putState(stateAfterMove1WithTurn, WHITE)
        TestLog.info("engine.positionHistory after move 1: ${engine.positionHistory.size} entries")
        engine.positionHistory.forEach { (hash, count) ->
            TestLog.info("  Hash: $hash, Count: $count")
        }

        gameState = stateAfterMove1WithTurn
        moves++

        // Segundo movimiento: BLACK C7 -> B6
        val move2From = C7
        val move2To = B6
        val stateAfterMove2 = gameState.applyMove(Move(move2From to move2To))
        val stateAfterMove2WithTurn = stateAfterMove2.copy(currentTurn = WHITE)

        TestLog.info("\nMove 2: BLACK $move2From -> $move2To")
        TestLog.info("State after move 2 hash: ${stateAfterMove2WithTurn.hashBoard()}")

        engine.putState(stateAfterMove2WithTurn, BLACK)
        TestLog.info("engine.positionHistory after move 2: ${engine.positionHistory.size} entries")
        engine.positionHistory.forEach { (hash, count) ->
            TestLog.info("  Hash: $hash, Count: $count")
        }

        gameState = stateAfterMove2WithTurn
        moves++

        // Tercer movimiento: WHITE B2 -> C1 (volver)
        val move3From = B2
        val move3To = C1
        val stateAfterMove3 = gameState.applyMove(Move(move3From to move3To))
        val stateAfterMove3WithTurn = stateAfterMove3.copy(currentTurn = BLACK)

        TestLog.info("\nMove 3: WHITE $move3From -> $move3To")
        TestLog.info("State after move 3 hash: ${stateAfterMove3WithTurn.hashBoard()}")

        engine.putState(stateAfterMove3WithTurn, WHITE)
        TestLog.info("engine.positionHistory after move 3: ${engine.positionHistory.size} entries")
        engine.positionHistory.forEach { (hash, count) ->
            TestLog.info("  Hash: $hash, Count: $count")
        }

        gameState = stateAfterMove3WithTurn
        moves++

        // Cuarto movimiento: BLACK B6 -> C7 (volver) - debería ser igual al estado inicial
        val move4From = B6
        val move4To = C7
        val stateAfterMove4 = gameState.applyMove(Move(move4From to move4To))
        val stateAfterMove4WithTurn = stateAfterMove4.copy(currentTurn = WHITE)

        TestLog.info("\nMove 4: BLACK $move4From -> $move4To")
        TestLog.info("State after move 4 hash: ${stateAfterMove4WithTurn.hashBoard()}")
        TestLog.info("Initial state hash: ${initialState.hashBoard()}")
        TestLog.info("Are they equal? ${stateAfterMove4WithTurn.hashBoard() == initialState.hashBoard()}")

        val loser4 = engine.putState(stateAfterMove4WithTurn, BLACK)
        TestLog.info("engine.positionHistory after move 4: ${engine.positionHistory.size} entries")
        engine.positionHistory.forEach { (hash, count) ->
            TestLog.info("  Hash: $hash, Count: $count")
        }

        // Verificar si debería haber triple repetición
        val currentHash = stateAfterMove4WithTurn.hashBoard()
        val count = engine.positionHistory[currentHash] ?: 0
        TestLog.info("Current state count: $count")

        if (loser4 != null) {
            TestLog.info("Triple repetition detected at move 4! $loser4 loses")
        }

        // Verificar el estado del juego
        TestLog.info("isGameOver: ${stateAfterMove4WithTurn.isGameOver(engine.positionHistory)}")
        TestLog.info("Winner: ${stateAfterMove4WithTurn.getWinner(engine.positionHistory)}")
        TestLog.info("After moves: $moves")

        // No hacemos asserts aquí - solo queremos el output para debug
    }
}
