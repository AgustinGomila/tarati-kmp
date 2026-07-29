package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.isValidMove
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.game.ai.tournament.engine.base.newEngine
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameIntegrationTest {

    // Each test gets a fresh isolated engine — no shared singleton state,
    // so no @Before clearHistory() is needed.
    private val engine = newEngine()

    @Test
    fun completeGameFlow() {
        // Start with initial state
        var state =
            GameState(
                mapOf(
                    C1 to Cob(CobColor.WHITE),
                    C7 to Cob(CobColor.BLACK),
                ),
                currentTurn = CobColor.WHITE,
            )

        // White makes a move
        val whiteMove = runBlocking { engine.getNextMove(state, Difficulty.MIN) }
        assertNotNull("White should have a valid move", whiteMove.move)

        // Apply white move
        state = state.applyMove(whiteMove.move ?: return)
        state = state.copy(currentTurn = CobColor.BLACK)

        // Black makes a move
        val blackMove = runBlocking { engine.getNextMove(state, Difficulty.MIN) }
        assertNotNull("Black should have a valid move", blackMove.move)

        // Apply black move
        state = state.applyMove(blackMove.move ?: return)
        state = state.copy(currentTurn = CobColor.WHITE)

        // Game should not be over yet
        assertFalse(
            "Game should not be over after first moves",
            state.isGameOver(emptyMap()),
        )
    }

    @Test
    fun aiDepthPerformance() {
        val state =
            GameState(
                mapOf(
                    C1 to Cob(CobColor.WHITE),
                    C2 to Cob(CobColor.WHITE),
                    C7 to Cob(CobColor.BLACK),
                    C8 to Cob(CobColor.BLACK),
                ),
                currentTurn = CobColor.WHITE,
            )

        // Test different depths
        val depths = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.CHAMPION)

        depths.forEach { depth ->
            val startTime = System.currentTimeMillis()
            val result = runBlocking { engine.getNextMove(state, depth) }
            val endTime = System.currentTimeMillis()

            assertNotNull("AI should return a move at depth $depth", result.move)
            assertTrue(
                "Move should be valid",
                isValidMove(state, result.move ?: return@forEach),
            )

            TestLog.info("Depth $depth took ${endTime - startTime}ms")
        }
    }
}