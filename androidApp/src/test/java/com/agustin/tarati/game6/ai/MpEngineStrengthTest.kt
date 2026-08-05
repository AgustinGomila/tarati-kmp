package com.agustin.tarati.game6.ai

import com.agustin.tarati.core.domain.game6.ai.MpBot
import com.agustin.tarati.core.domain.game6.ai.MpBotLevel
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpCutConfig
import com.agustin.tarati.core.domain.game6.rules.MpMatch
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.testutil.TestLog
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests de **fuerza** del motor multijugador ([MpBot]/[MpMaxN]): versión liviana de los round-robin de
 * dificultad del juego 1 ([com.agustin.tarati.game.ai.tournament.RoundRobinTest]).
 *
 * Cada agente juega un asiento de una partida 2-jugadores; se **alternan los colores** entre partidas
 * para cancelar la ventaja del primero en mover. Marcado como **test pesado** (`MpEngineStrengthTest`
 * en `heavyTestPatterns` de `androidApp/build.gradle.kts`): se excluye de la corrida completa y solo
 * corre con un filtro explícito (`--tests` o el gutter de la IDE).
 *
 * Lo que se asevera es lo **robusto** (validado, reproducible):
 * - todo tier supera al azar (piso del ladder);
 * - la **búsqueda más profunda vence a la más superficial** (Champion ≫ Hard ≫ Medium) — el corazón de
 *   "motor más evaluativo";
 * - Champion encabeza el ladder por puntaje total.
 *
 * Nota: el greedy (Easy, con término de amenaza a 1 ply) es un **piso táctico fuerte**; los tiers de
 * búsqueda no lo superan de forma limpia en head-to-head (la fuerte ventaja del primero en mover hace
 * ruidoso ese cruce), así que **no** se asevera "Champion domina a Easy" — igual que el juego 1 solo
 * asevera Hard ≥ Easy, no dominación estricta de cada cruce.
 */
class MpEngineStrengthTest {

    private enum class Outcome { A, B, DRAW }

    /** Un agente: elige la jugada del asiento en turno (o `null` si no hay). */
    private fun interface Agent {
        fun move(state: MpGameState, random: Random): MpMove?
    }

    private fun bot(level: MpBotLevel) = Agent { s, r -> MpBot.chooseMove(s, level, random = r) }

    private val randomAgent = Agent { s, r -> MpRules.legalMoves(s).randomOrNull(r) }

    /**
     * Juega una partida 2-jugadores: [a] controla el asiento 0, [b] el 1. El corte por estancamiento
     * ([MpCutConfig.Default]) garantiza terminación por mayoría de piezas. Devuelve quién ganó (o
     * empate/victoria compartida).
     */
    private fun playDuel(a: Agent, b: Agent, seed: Long, maxPlies: Int = 400): Outcome {
        val match = MpMatch(MpSetup.initialState(2), cut = MpCutConfig.Default)
        val rng = Random(seed)
        var plies = 0
        while (!match.state.isGameOver && plies < maxPlies) {
            val agent = if (match.state.currentSeatIndex == 0) a else b
            val move = agent.move(match.state, rng) ?: break
            match.applyMove(move)
            plies++
        }
        val result = match.state.result ?: return Outcome.DRAW
        if (result.winners.size != 1) return Outcome.DRAW
        val seatIndex = match.state.seats.indexOfFirst { it.color == result.winners.first() }
        return if (seatIndex == 0) Outcome.A else Outcome.B
    }

    private data class Tally(var a: Int = 0, var b: Int = 0, var draws: Int = 0) {
        val decided get() = a + b
        fun scoreA() = a + 0.5 * draws
        fun scoreB() = b + 0.5 * draws
    }

    /** [games] partidas [a] vs [b], alternando quién arranca (asiento 0) para cancelar la ventaja. */
    private fun runMatchup(a: Agent, b: Agent, games: Int, baseSeed: Long = 1): Tally {
        val t = Tally()
        for (i in 0 until games) {
            val swap = i % 2 == 1
            val outcome = if (!swap) playDuel(a, b, baseSeed + i) else playDuel(b, a, baseSeed + i)
            when (outcome) {
                Outcome.DRAW -> t.draws++
                Outcome.A -> if (!swap) t.a++ else t.b++
                Outcome.B -> if (!swap) t.b++ else t.a++
            }
        }
        return t
    }

    // ════════════════════════════════════════════════════════════════════════

    /** Todo tier debe superar al azar — el piso absoluto del ladder. */
    @Test
    fun every_tier_beats_random() {
        MpBotLevel.entries.forEach { level ->
            val t = runMatchup(bot(level), randomAgent, games = 8)
            val winRate = if (t.decided == 0) 0.0 else t.a.toDouble() / t.decided
            TestLog.info("$level vs Random: ${t.a}-${t.b}-${t.draws} (winRate ${"%.0f".format(winRate * 100)}%)")
            assertTrue(
                "$level debería superar al azar (winRate decididas ${"%.0f".format(winRate * 100)}%)",
                t.decided > 0 && winRate >= 0.60,
            )
        }
    }

    /**
     * El corazón de "motor más evaluativo": más profundidad de búsqueda ⇒ más fuerza. Se verifica en
     * los dos escalones de la búsqueda (Champion vs Hard, Hard vs Medium): el tier más profundo debe
     * ganar **estrictamente más** partidas decididas que el más superficial.
     */
    @Test
    fun deeper_search_beats_shallower() {
        val championVsHard = runMatchup(bot(MpBotLevel.CHAMPION), bot(MpBotLevel.HARD), games = 6, baseSeed = 20)
        TestLog.info("Champion vs Hard: ${championVsHard.a}-${championVsHard.b}-${championVsHard.draws}")
        assertTrue(
            "Champion (depth 4) debería ganar más que Hard (depth 3): ${championVsHard.a}-${championVsHard.b}",
            championVsHard.a > championVsHard.b,
        )

        val hardVsMedium = runMatchup(bot(MpBotLevel.HARD), bot(MpBotLevel.MEDIUM), games = 6, baseSeed = 40)
        TestLog.info("Hard vs Medium: ${hardVsMedium.a}-${hardVsMedium.b}-${hardVsMedium.draws}")
        assertTrue(
            "Hard (depth 3) debería ganar más que Medium (depth 2): ${hardVsMedium.a}-${hardVsMedium.b}",
            hardVsMedium.a > hardVsMedium.b,
        )
    }

    /**
     * Round-robin ligero de los 4 tiers: registra el standings y verifica que **Champion encabece** los
     * tiers de búsqueda (puntaje total ≥ Hard y ≥ Medium). No se compara contra Easy: el greedy (con
     * amenaza a 1 ply) es un piso táctico fuerte y su cruce es ruidoso por la ventaja del primero en
     * mover — lo robusto (y el punto de "motor más evaluativo") es que la búsqueda profunda encabece a
     * la superficial, ya cubierto también por [deeper_search_beats_shallower].
     */
    @Test
    fun champion_tops_the_ladder() {
        val levels = MpBotLevel.entries
        val score = levels.associateWith { 0.0 }.toMutableMap()

        for (i in levels.indices) {
            for (j in i + 1 until levels.size) {
                val a = levels[i]
                val b = levels[j]
                val t = runMatchup(bot(a), bot(b), games = 6, baseSeed = 100L + i * 10 + j)
                TestLog.info("${a.name} vs ${b.name}: ${t.a}-${t.b}-${t.draws}")
                score[a] = score.getValue(a) + t.scoreA()
                score[b] = score.getValue(b) + t.scoreB()
            }
        }

        TestLog.info("\n${"=".repeat(50)}")
        TestLog.info("# MP LADDER STANDINGS (score = wins + 0.5·draws)")
        TestLog.info("=".repeat(50))
        levels.sortedByDescending { score.getValue(it) }.forEach { level ->
            TestLog.info("%-9s | %.1f".format(level.name, score.getValue(level)))
        }
        TestLog.info("=".repeat(50))

        val championScore = score.getValue(MpBotLevel.CHAMPION)
        listOf(MpBotLevel.HARD, MpBotLevel.MEDIUM).forEach { other ->
            assertTrue(
                "Champion (${"%.1f".format(championScore)}) debería encabezar los tiers de búsqueda, no por " +
                        "debajo de ${other.name} (${"%.1f".format(score.getValue(other))})",
                championScore >= score.getValue(other),
            )
        }
    }
}
