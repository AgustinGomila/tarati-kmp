package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Harness "Claude vs Champion": Claude juega un bando con jugadas guionadas
 * ([WHITE_SCRIPT] / [BLACK_SCRIPT], en notación "B6-B1" o promoción "C12=R");
 * el motor Champion (depth 7, config de producción) juega el otro con búsqueda real.
 *
 * En cada turno de Claude sin jugada guionada, imprime un PANEL DE DECISIÓN:
 *  - tablero ASCII + notación de posición,
 *  - jugadas legales ordenadas por un árbitro depth-2 estático (perspectiva de Claude),
 *  - la jugada que Champion elegiría en el asiento de Claude (su búsqueda depth-7).
 * y se detiene, para que Claude razone y agregue la próxima jugada al script.
 *
 * No es un test de aserción: es una herramienta de análisis. No forma parte de la
 * batería pesada, así que corre con `--tests "*ChampionMatchTest.game1_claudeWhite"`.
 */
class ChampionMatchTest {

    // ── Scripts editables entre corridas ──────────────────────────────────────
    // Jugadas de Claude, en orden. Vacío = pedir la primera decisión.

    private val WHITE_SCRIPT: List<String> = listOf(
        "C1-B1",
        "D1-C1",
        "C2-C3",
        "C1-C12",
        "D2-C2",
        "C12-C11",
        "C3-C4",
        "C2-C3",
        "B1-A1",
        "C3-B2",
        "A1-B4",
        "B4-C7",
        "C7-C6",
        "B3-B4",
        "B4-C8",
    )

    private val BLACK_SCRIPT: List<String> = listOf(
        "C7-C6",
        "C8-C9",
        "D3-C7",
        "C6-B3",
        "C9-B5",
        "D4-C8",
    )

    // Jugadas de Champion FIJADAS para reproducibilidad (su desempate entre jugadas
    // de igual score es aleatorio — MinimaxStrategy:249). Se registran las respuestas
    // reales observadas; si el guion se agota, Champion juega en vivo (y hay que
    // copiar esa jugada aquí antes de la próxima corrida). Cada jugada fijada sigue
    // siendo una elección depth-7 legítima.
    private val CHAMP_WHITE_G2: List<String> = listOf(
        "C2-B1",
        "C1-C12",
        "D1-C1",
        "B1-A1",
        "C12-B6",
        "C1-C12",
    )
    private val CHAMP_BLACK_G1: List<String> = listOf()

    // ── Rematch (game3): Claude = Blancas, Champion = Negras ───────────────────
    private val WHITE_SCRIPT_G3: List<String> = listOf(
        "C1-B1",
        "C2-C3",
        "D2-C2",
        "B1-A1",
        "C3-B2",
        "C2-C3",
    )
    private val CHAMP_BLACK_G3: List<String> = listOf(
        "C8-C9",
        "C7-B4",
        "D4-C8",
        "C9-B5",
        "B4-B3",
    )

    // ── Config ────────────────────────────────────────────────────────────────

    private val champConfig = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION)
    private val boardEval = BoardEvaluator()

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun game1_claudeWhite(): Unit =
        runMatch(claudeColor = WHITE, claudeScript = WHITE_SCRIPT, champScript = CHAMP_BLACK_G1)

    @Test
    fun game2_claudeBlack(): Unit =
        runMatch(claudeColor = BLACK, claudeScript = BLACK_SCRIPT, champScript = CHAMP_WHITE_G2)

    @Test
    fun game3_rematch(): Unit =
        runMatch(
            claudeColor = WHITE,
            claudeScript = WHITE_SCRIPT_G3,
            champScript = CHAMP_BLACK_G3,
            autoFinish = true, // tras el guion, ambos bandos juegan a fuerza-Champion hasta el final
        )

    // ── Runner ───────────────────────────────────────────────────────────────

    private fun runMatch(
        claudeColor: CobColor,
        claudeScript: List<String>,
        champScript: List<String>,
        autoFinish: Boolean = false,
    ) {
        val champColor = claudeColor.opponentColor()
        val champ = TaratiAI().apply { setConfig(champConfig) }
        val claudeAuto = TaratiAI().apply { setConfig(champConfig) }

        var state = initialGameState() // Blancas mueven primero
        val history = mutableMapOf<String, Int>()
        var ply = 0
        var scriptIdx = 0
        var champIdx = 0

        header(claudeColor)
        TestLog.info("Posición inicial:")
        TestLog.info(render(state))

        while (ply < MAX_PLIES && !state.isGameOver(history)) {
            val mover = state.currentTurn
            if (mover == claudeColor) {
                if (scriptIdx >= claudeScript.size) {
                    if (!autoFinish) {
                        decisionPanel(state, claudeColor, champColor, ply + 1, history)
                        return
                    }
                    claudeAuto.replaceHistory(history)
                    val move = runBlocking { claudeAuto.getNextMove(state, Difficulty.CHAMPION) }.move ?: break
                    state = advance(state, move, history, ++ply, tag = "CLAUDE* ")
                    continue
                }
                val wanted = claudeScript[scriptIdx++]
                val legal = state.allMovesForTurn()
                val move = legal.firstOrNull { it.name == wanted }
                    ?: run {
                        TestLog.info("‼️  Jugada ilegal en el script (Claude): '$wanted'. Legales: ${legal.joinToString { it.name }}")
                        return
                    }
                state = advance(state, move, history, ++ply, tag = "CLAUDE  ")
            } else {
                champ.replaceHistory(history)
                val move = if (champIdx < champScript.size) {
                    val wanted = champScript[champIdx++]
                    val legal = state.allMovesForTurn()
                    legal.firstOrNull { it.name == wanted } ?: run {
                        TestLog.info("‼️  Jugada ilegal en el script (Champion): '$wanted'. Legales: ${legal.joinToString { it.name }}")
                        return
                    }
                } else {
                    // En vivo: registrar esta jugada en el champScript antes de la próxima corrida.
                    val live = runBlocking { champ.getNextMove(state, Difficulty.CHAMPION) }.move ?: break
                    TestLog.info("‹live champ→ ${live.name} ›  (fijar en champScript)")
                    live
                }
                state = advance(state, move, history, ++ply, tag = "CHAMPION")
            }
        }

        footer(state, history, ply)
    }

    // ── Aplicar + imprimir una media-jugada ───────────────────────────────────

    private fun advance(
        state: GameState,
        move: Move,
        history: MutableMap<String, Int>,
        ply: Int,
        tag: String,
    ): GameState {
        val next = state.applyMove(move)
        val hash = next.hashBoard()
        history[hash] = (history[hash] ?: 0) + 1
        val flips = move.countFlipsByType(state, next) // (rok, cob)
        val flipStr = if (flips.first + flips.second > 0) "  captura +${flips.first + flips.second}" else ""
        val evalW = boardEval.evaluate(next, champConfig)
        TestLog.info("─".repeat(60))
        TestLog.info("Ply $ply  [$tag ${state.currentTurn}]  ${move.name}$flipStr   evalW=${fmt(evalW)}")
        TestLog.info("POS: ${next.toPositionNotation()}")
        TestLog.info(render(next))
        return next
    }

    // ── Panel de decisión ──────────────────────────────────────────────────────

    private fun decisionPanel(
        state: GameState,
        claude: CobColor,
        champ: CobColor,
        moveNo: Int,
        history: Map<String, Int>,
    ) {
        TestLog.info("═".repeat(60))
        TestLog.info("  DECISIÓN — CLAUDE ($claude) juega  ·  ply $moveNo")
        TestLog.info("  POS: ${state.toPositionNotation()}")
        TestLog.info("═".repeat(60))
        TestLog.info(render(state))

        val legal = state.allMovesForTurn()
        val ranked = legal
            .map { it to refereeValue(state, it, claude) }
            .sortedByDescending { it.second }

        TestLog.info("Jugadas legales (árbitro depth-2 estático, + = mejor para $claude):")
        ranked.forEachIndexed { i, (m, v) ->
            val after = state.applyMove(m)
            val flips = m.countFlipsByType(state, after)
            val cap = if (flips.first + flips.second > 0) " cap+${flips.first + flips.second}" else ""
            TestLog.info("  ${(i + 1).toString().padStart(2)}. ${m.name.padEnd(8)} ref=${fmt(v).padStart(9)}$cap")
        }

        // Qué jugaría Champion en el asiento de Claude (búsqueda real depth-7).
        val advisor = TaratiAI().apply { setConfig(champConfig); replaceHistory(history) }
        val champPick = runBlocking { advisor.getNextMove(state, Difficulty.CHAMPION) }
        TestLog.info()
        TestLog.info("★ Champion (depth-7) jugaría: ${champPick.move?.name}  (search score=${fmt(champPick.score)})")
        val rankOfPick = ranked.indexOfFirst { it.first.name == champPick.move?.name }
        if (rankOfPick >= 0) {
            TestLog.info("  → ranking del árbitro para esa jugada: #${rankOfPick + 1} de ${ranked.size}")
        }
        TestLog.info("═".repeat(60))
    }

    /**
     * Árbitro depth-2 estático desde la perspectiva de [claude]: aplica la jugada
     * candidata y asume que Champion responde con la peor réplica para Claude según
     * el evaluador estático (config Champion). Aproxima "¿cuelga algo esta jugada?".
     */
    private fun refereeValue(state: GameState, move: Move, claude: CobColor): Double {
        val after = state.applyMove(move)
        val persp = { s: GameState -> boardEval.evaluate(s, champConfig).let { if (claude == WHITE) it else -it } }
        if (after.cobs.values.none { it.color == claude.opponentColor() }) return WIN
        val replies = after.allMovesForTurn()
        if (replies.isEmpty()) return persp(after)
        return replies.minOf { r -> persp(after.applyMove(r)) }
    }

    // ── Presentación ───────────────────────────────────────────────────────────

    private fun header(claude: CobColor) {
        TestLog.info("\n${"═".repeat(60)}")
        TestLog.info("  CLAUDE ($claude) vs CHAMPION (${claude.opponentColor()})  ·  depth 7  ·  sin reloj")
        TestLog.info("═".repeat(60))
    }

    private fun footer(state: GameState, history: Map<String, Int>, ply: Int) {
        TestLog.info("═".repeat(60))
        val winner = state.getWinner(history)
        val reason = state.getMatchState(history).gameEndReason
        TestLog.info("  RESULTADO: ${winner?.name ?: "TABLAS"}  ·  $reason  ·  $ply plies")
        TestLog.info("═".repeat(60))
    }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()

    private fun CobColor.opponentColor(): CobColor = if (this == WHITE) BLACK else WHITE

    /**
     * Render ASCII del tablero (misma convención que VisualGameTest):
     *   (w)=White Cob (W)=White Rok · (b)=Black Cob (B)=Black Rok · nombre=vacío
     */
    private fun render(state: GameState): String {
        fun d(v: Vertex): String {
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
            appendLine("            ${d(g.D3)}  ${d(g.D4)}")
            appendLine()
            appendLine("            ${d(g.C7)}  ${d(g.C8)}")
            appendLine("       ${d(g.C6)}              ${d(g.C9)}")
            appendLine("               ${d(g.B4)}")
            appendLine("    ${d(g.C5)}  ${d(g.B3)}        ${d(g.B5)}  ${d(g.C10)}")
            appendLine("               ${d(g.A1)}")
            appendLine("    ${d(g.C4)}  ${d(g.B2)}        ${d(g.B6)}  ${d(g.C11)}")
            appendLine("               ${d(g.B1)}")
            appendLine("       ${d(g.C3)}              ${d(g.C12)}")
            appendLine("            ${d(g.C2)}  ${d(g.C1)}")
            appendLine()
            appendLine("            ${d(g.D2)}  ${d(g.D1)}")
        }
    }

    private companion object {
        const val MAX_PLIES = 200
        const val WIN = 1_000_000.0
    }
}
