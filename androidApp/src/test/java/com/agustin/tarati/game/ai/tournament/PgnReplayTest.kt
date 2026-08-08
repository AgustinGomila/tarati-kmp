package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.engine.BoardEvaluator
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.testutil.TestLog
import org.junit.Test

/**
 * Reproduce PGNs de partidas reales (del corpus de bots de producción) para analizarlas ply a ply:
 * tablero ASCII, capturas y conteo de piezas. El PGN de prod separa jugadas por **espacio** y usa
 * "-" (o "→" en partidas viejas). Sirve para diseccionar los casos raros donde ganan las Blancas.
 */
class PgnReplayTest {

    private val boardEval = BoardEvaluator()
    private val champConfig = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION)

    // Victoria de Blancas por MIT en 21 plies — bot_vera (W) vs bot_zara (B), classical.
    private val WHITE_WIN_MIT =
        "C2-C3 C7-C6 C1-C12 C8-B4 D2-C2 C6-C5 C2-B1 C5-C4 B1-B2 B4-A1 C12-B6 B2-B1 C3-B2 " +
                "D3-C7 A1-B4 B6-C12 B1-B6 D4-D3 B2-A1 C7-C6 B4-C7"

    @Test
    fun replay_whiteWinByMit(): Unit = replay("WHITE-WIN por MIT · vera(W) vs zara(B)", WHITE_WIN_MIT)

    /**
     * Analiza el **ply de decisión** de una muestra de partidas Champion-vs-Champion reales
     * (exportadas a `build/analysis/champ_games.tsv`, formato `result|end_method|pgn`).
     *
     * Para cada partida reproduce el PGN, calcula `evalW` (óptica Blancas) tras cada ply, y define
     * el **ply de decisión** = el último ply en que el eval estuvo a favor del bando que NO ganó
     * (el "punto de no retorno"; +1 = decidida desde el arranque). Reporta la distribución por
     * resultado — la hipótesis es que el juego se decide en el see-saw central temprano.
     */
    @Test
    fun analyzeDivergence() {
        val file = listOf(
            "build/analysis/champ_games.tsv",
            "androidApp/build/analysis/champ_games.tsv",
        ).map { java.io.File(it) }.firstOrNull { it.isFile }
            ?: run { TestLog.info("‼️ no encontré champ_games.tsv — exportar primero"); return }

        data class Row(
            val whiteWon: Boolean,
            val endMethod: String,
            val decisionPly: Int,
            val plies: Int,
            val evalAt: (Int) -> Double?
        )

        val rows = mutableListOf<Row>()
        file.forEachLine { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@forEachLine
            val whiteWon = parts[0] == "white_wins"
            val endMethod = parts[1]
            val tokens = parts[2].trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEachLine

            val evals = ArrayList<Double>(tokens.size)
            var state = initialGameState()
            val ok = tokens.all { tok ->
                val mv = runCatching { parse(tok) }.getOrNull() ?: return@all false
                state = state.applyMove(mv)
                evals.add(boardEval.evaluate(state, champConfig))
                true
            }
            if (!ok || evals.isEmpty()) return@forEachLine

            val winnerSign = if (whiteWon) 1.0 else -1.0
            // último índice donde el eval NO estaba a favor del ganador → decisión = ese +1 (1-based +1).
            var lastAgainst = -1
            evals.forEachIndexed { i, e -> if (winnerSign * e <= 0.0) lastAgainst = i }
            val decisionPly = lastAgainst + 2 // +1 (0→1-based) +1 (primer ply ya a favor)
            rows.add(
                Row(
                    whiteWon,
                    endMethod,
                    decisionPly.coerceAtMost(evals.size),
                    evals.size
                ) { k -> evals.getOrNull(k - 1) })
        }

        fun stats(sel: List<Row>, label: String) {
            if (sel.isEmpty()) {
                TestLog.info("$label: (sin datos)"); return
            }
            val dec = sel.map { it.decisionPly }.sorted()
            val len = sel.map { it.plies }
            val decFrac = sel.map { it.decisionPly.toDouble() / it.plies }
            fun med(l: List<Int>) = l[l.size / 2]
            TestLog.info(
                "%-14s n=%-3d │ plies med=%-3d prom=%.0f │ ply-decisión med=%-3d prom=%.1f (p25=%d p75=%d) │ decisión@%.0f%% de la partida"
                    .format(
                        label, sel.size, med(len.sorted()), len.average(),
                        med(dec), dec.average(), dec[dec.size / 4], dec[dec.size * 3 / 4],
                        decFrac.average() * 100,
                    )
            )
        }

        TestLog.info("\n${"═".repeat(96)}")
        TestLog.info("  PLY DE DECISIÓN — Champion-vs-Champion (${rows.size} partidas, sin timeouts)")
        TestLog.info("═".repeat(96))
        stats(rows.filter { it.whiteWon }, "White gana")
        stats(rows.filter { !it.whiteWon }, "Black gana")
        TestLog.info("─".repeat(96))
        // Por método de fin (dentro de White-wins, que es donde hay variedad de métodos).
        listOf("mit", "stalemit", "triple").forEach { m ->
            stats(rows.filter { it.whiteWon && it.endMethod == m }, "White·$m")
        }
        // Eval promedio por ply (trayectoria) en checkpoints, por resultado.
        TestLog.info("─".repeat(96))
        listOf(6, 10, 14, 18, 24).forEach { k ->
            val w = rows.filter { it.whiteWon }.mapNotNull { it.evalAt(k) }
            val b = rows.filter { !it.whiteWon }.mapNotNull { it.evalAt(k) }
            if (w.isNotEmpty() && b.isNotEmpty())
                TestLog.info(
                    "  evalW promedio @ply %-2d  │ White-wins %+7.0f │ Black-wins %+7.0f".format(
                        k,
                        w.average(),
                        b.average()
                    )
                )
        }
        TestLog.info("═".repeat(96))
    }

    private fun replay(title: String, pgn: String) {
        TestLog.info("\n${"═".repeat(64)}")
        TestLog.info("  $title")
        TestLog.info("═".repeat(64))
        var state = initialGameState()
        val history = mutableMapOf<String, Int>()
        val moves = pgn.trim().split(Regex("[\\s,]+")).filter { it.isNotBlank() }

        moves.forEachIndexed { i, token ->
            val mover = state.currentTurn
            val move = parse(token)
            val next = state.applyMove(move)
            val hash = next.hashBoard()
            history[hash] = (history[hash] ?: 0) + 1
            val (r, c) = move.countFlipsByType(state, next)
            val (wc, bc) = next.getPieceCounts()
            val cap = if (r + c > 0) "  cap+${r + c}" else ""
            TestLog.info("─".repeat(64))
            TestLog.info(
                "Ply ${i + 1} [$mover] $token$cap   piezas W=$wc B=$bc   evalW=${
                    fmt(
                        boardEval.evaluate(
                            next,
                            champConfig
                        )
                    )
                }"
            )
            TestLog.info(render(next))
            state = next
        }
        val winner = state.getWinner(history)
        val reason = state.getMatchState(history).gameEndReason
        TestLog.info("═".repeat(64))
        TestLog.info("  RESULTADO: ${winner?.name ?: "—"} · $reason · ${moves.size} plies")
        TestLog.info("═".repeat(64))
    }

    private fun parse(token: String): Move {
        if (token.endsWith("=R")) {
            val v = vertex(token.removeSuffix("=R"))
            return Move(v, v)
        }
        val p = token.split("-", "→")
        return Move(vertex(p[0]), vertex(p[1]))
    }

    private fun vertex(name: String): Vertex =
        GameBoard.vertices.first { it.name.equals(name.trim(), ignoreCase = true) }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()

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
}
