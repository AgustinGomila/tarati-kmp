package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Test visual que reproduce partidas completas imprimiendo el estado del tablero
 * en ASCII después de cada movimiento. Útil para analizar el comportamiento del
 * engine a distintas dificultades sin necesidad de UI.
 *
 * Excluido de la build de release mediante el flag `releaseBuild` en build.gradle.
 *
 * Tablero ASCII:
 *
 *             D3    D4
 *
 *             C7    C8
 *        C6              C9
 *                B4
 *     C5    B3        B5    C10
 *                A1
 *     C4    B2        B6    C11
 *                B1
 *        C3              C12
 *             C2    C1
 *
 *             D2    D1
 */
class VisualGameTest {

    // ── ASCII board renderer ──────────────────────────────────────────────────

    /**
     * Renderiza el estado del tablero en ASCII.
     * Convención de símbolos:
     *   w = White Cob | W = White Rok (upgraded)
     *   b = Black Cob | B = Black Rok (upgraded)
     *   · = empty vertex
     */
    private fun renderBoard(state: GameState): String {
        fun display(v: Vertex): String {
            val cob = state.cobs[v] ?: return v.name.padEnd(3)
            return when (cob.color) {
                WHITE if !cob.isUpgraded -> "(w)"
                WHITE if true -> "(W)"
                BLACK if !cob.isUpgraded -> "(b)"
                else -> "(B)"
            }
        }

        val g = GameBoard
        return buildString {
            appendLine("            ${display(g.D3)}  ${display(g.D4)}")
            appendLine()
            appendLine("            ${display(g.C7)}  ${display(g.C8)}")
            appendLine("       ${display(g.C6)}              ${display(g.C9)}")
            appendLine("               ${display(g.B4)}")
            appendLine("    ${display(g.C5)}  ${display(g.B3)}        ${display(g.B5)}  ${display(g.C10)}")
            appendLine("               ${display(g.A1)}")
            appendLine("    ${display(g.C4)}  ${display(g.B2)}        ${display(g.B6)}  ${display(g.C11)}")
            appendLine("               ${display(g.B1)}")
            appendLine("       ${display(g.C3)}              ${display(g.C12)}")
            appendLine("            ${display(g.C2)}  ${display(g.C1)}")
            appendLine()
            appendLine("            ${display(g.D2)}  ${display(g.D1)}")
        }
    }

    // ── Game runner ───────────────────────────────────────────────────────────

    private fun playVisualGame(
        difficulty: Difficulty,
        maxMoves: Int = 100,
    ) {
        val engine = TaratiAI()
        val config = EvaluationConfig.getByDifficulty(difficulty)
        engine.setConfig(config)

        var state = initialGameState()
        var moveCount = 0
        val positionHistory = mutableMapOf<String, Int>()

        TestLog.info("\n${"═".repeat(50)}")
        TestLog.info("  GAME: AI vs AI | Difficulty: ${difficulty.name}")
        TestLog.info("═".repeat(50))
        TestLog.info("\nInitial position:")
        TestLog.info(renderBoard(state))

        while (moveCount < maxMoves && !state.isGameOver(positionHistory)) {
            val result = runBlocking { engine.getNextMove(state) }
            val move = result.move ?: break

            val newState = state.applyMove(move)
            val hash = newState.hashBoard()
            positionHistory[hash] = (positionHistory[hash] ?: 0) + 1

            moveCount++
            TestLog.info("─".repeat(50))
            TestLog.info(
                "  Move $moveCount: ${state.currentTurn} ${move.from.name} → ${move.to.name}  [score: ${
                    "%.2f".format(result.score)
                }]"
            )
            TestLog.info("  POS: ${newState.toPositionNotation()}")
            TestLog.info()
            TestLog.info(renderBoard(newState))
            state = newState
        }

        TestLog.info("═".repeat(50))
        val winner = state.getWinner(positionHistory)
        TestLog.info("  RESULT: ${winner?.name ?: "DRAW"} after $moveCount moves")
        TestLog.info("═".repeat(50))
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun visualGame_easyDifficulty() {
        playVisualGame(difficulty = Difficulty.EASY)
    }

    @Test
    fun visualGame_defaultDifficulty() {
        playVisualGame(difficulty = Difficulty.DEFAULT)
    }

    @Test
    fun visualGame_hardDifficulty() {
        playVisualGame(difficulty = Difficulty.HARD)
    }

    @Test
    fun visualGame_championDifficulty() {
        playVisualGame(difficulty = Difficulty.CHAMPION)
    }
}