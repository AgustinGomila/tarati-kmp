package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.RootSelection
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.game.ai.tournament.engine.base.personalityEngine
import com.agustin.tarati.game.ai.tournament.manager.TournamentConfig
import com.agustin.tarati.game.ai.tournament.manager.TournamentRunner
import com.agustin.tarati.testutil.TestLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valida la **selección en la raíz** ([RootSelection]) de CHAMPION: introduce variedad partida a partida
 * en Champion-vs-Champion sin debilitar el juego.
 *
 * Dos ejes:
 *  - **Variedad** — self-play CHAMPION-vs-CHAMPION debe producir transcripciones distintas (antes: siempre
 *    la misma, por keep-first + `evalNoise=0`). Se mide con la política activa vs. desactivada (control).
 *  - **Fuerza** — la variedad no puede costar habilidad: CHAMPION(variedad) empareja ~50 % contra
 *    CHAMPION(determinista) y sigue dominando a HARD.
 *
 * Además, un A/B sobre `epsilon` (log-only) para elegir cuánta variedad admitir a qué costo.
 *
 * Tests lentos (depth 7). No forman parte de la batería rápida; correr puntualmente con
 * `--tests "*ChampionVarietyTest*"`.
 */
class ChampionVarietyTest {

    private fun log(message: String) = TestLog.info(message)

    // Config de producción (gambit + quiescence + rootSelection habilitada, epsilon=0/temp=0.75).
    private val championProd = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION)

    // Control determinista: misma config pero sin selección en la raíz → dos CHAMPION juegan idéntico.
    private val championDeterministic = championProd.copy(rootSelection = RootSelection())

    private val hardProd = EvaluationConfig.getByDifficulty(Difficulty.HARD)

    // ── Self-play helper ──────────────────────────────────────────────────────

    /**
     * Juega una partida CHAMPION-vs-CHAMPION con [config] en ambos bandos y devuelve la transcripción
     * (nombres de jugada en orden). Inyecta la historia real cada turno para respetar la triple repetición.
     */
    private fun selfPlayTranscript(config: EvaluationConfig, maxPlies: Int = 120): List<String> {
        val white = TaratiAI().apply { setConfig(config) }
        val black = TaratiAI().apply { setConfig(config) }
        var state: GameState = initialGameState()
        val history = mutableMapOf<String, Int>()
        val transcript = mutableListOf<String>()
        var plies = 0

        while (plies < maxPlies && !state.isGameOver(history)) {
            val engine = if (state.currentTurn == WHITE) white else black
            engine.replaceHistory(history)
            val move = runBlocking { engine.getNextMove(state) }.move ?: break
            transcript.add(move.name)
            val next = state.applyMove(move)
            val hash = next.hashBoard()
            val count = (history[hash] ?: 0) + 1
            history[hash] = count
            if (count >= 3) break
            state = next
            plies++
        }
        return transcript
    }

    private fun distinctGames(config: EvaluationConfig, games: Int, maxPlies: Int = 120): Pair<Int, Int> {
        val full = HashSet<String>()
        val openings = HashSet<String>()
        repeat(games) {
            val t = selfPlayTranscript(config, maxPlies)
            full.add(t.joinToString(","))
            openings.add(t.take(8).joinToString(","))
        }
        return full.size to openings.size
    }

    // ── Variedad ──────────────────────────────────────────────────────────────

    /**
     * Control: sin selección en la raíz, CHAMPION-vs-CHAMPION es determinista → una sola partida distinta.
     * Con la política de producción, aparecen múltiples partidas.
     */
    @Test
    fun test_root_selection_produces_variety() {
        val games = 12

        val (detFull, _) = distinctGames(championDeterministic, games = 5)
        log("CONTROL (rootSelection off): $detFull partidas distintas de 5 → esperado 1")
        assertEquals(
            "Sin selección en la raíz, CHAMPION-vs-CHAMPION debe ser determinista (1 partida distinta)",
            1,
            detFull,
        )

        val (varFull, varOpen) = distinctGames(championProd, games = games)
        log("PROD (rootSelection on, eps=0 temp=0.75): $varFull partidas distintas / $varOpen aperturas distintas de $games")
        assertTrue(
            "La selección en la raíz debe producir variedad (más de 1 partida distinta en $games)",
            varFull > 1,
        )
    }

    // ── Fuerza ──────────────────────────────────────────────────────────────────

    /**
     * CHAMPION(variedad) no debe perder fuerza frente a CHAMPION(determinista): al alternar colores el
     * match es de igual habilidad → reparto equilibrado. Aserción lenient (no colapsa a <30 %).
     */
    @Test
    fun test_variety_champion_matches_deterministic_champion() {
        val tournament = TournamentRunner()
        val games = 24

        val result = tournament.runEngineMatch(
            engineA = personalityEngine("champion_variety"),
            engineB = personalityEngine("champion_det"),
            configA = championProd,
            configB = championDeterministic,
            tournamentConfig = TournamentConfig(gamesPerMatch = games, maxMovesPerGame = 100),
            logInfo = ::log,
        )

        val total = result.winsA + result.winsB + result.draws
        val rate = if (total > 0) result.winsA.toDouble() / total else 0.0
        log(
            "CHAMPION(variedad) vs CHAMPION(determinista): " +
                    "${"%.1f".format(rate * 100)}% (${result.winsA}-${result.winsB}-${result.draws})",
        )
        assertTrue(
            "La variedad no debe debilitar a CHAMPION frente a sí mismo determinista " +
                    "(actual: ${result.winsA}-${result.winsB}-${result.draws})",
            result.winsA >= total * 0.30,
        )
    }

    /**
     * CHAMPION(variedad) debe seguir dominando a HARD — la variedad se toma entre óptimos, no debilita.
     * Mismo criterio lenient que [RoundRobinTest.test_champion_dominates_hard_regression].
     */
    @Test
    fun test_variety_champion_still_dominates_hard() {
        val tournament = TournamentRunner()
        val result = tournament.runEngineMatch(
            engineA = personalityEngine("champion_variety"),
            engineB = personalityEngine("hard"),
            configA = championProd,
            configB = hardProd,
            tournamentConfig = TournamentConfig(gamesPerMatch = 30, maxMovesPerGame = 100),
            logInfo = ::log,
        )
        val total = result.winsA + result.winsB + result.draws
        val rate = if (total > 0) result.winsA.toDouble() / total else 0.0
        log(
            "CHAMPION(variedad) vs HARD: ${"%.1f".format(rate * 100)}% " +
                    "(${result.winsA}-${result.winsB}-${result.draws})",
        )
        assertTrue(
            "CHAMPION(variedad) no debe perder el head-to-head vs HARD " +
                    "(actual: ${result.winsA}-${result.winsB}-${result.draws})",
            result.winsA >= result.winsB,
        )
    }

    // ── A/B de epsilon (log-only) ────────────────────────────────────────────────

    /**
     * Barre `epsilon` (con temperature fija) reportando, por valor: cuántas partidas/aperturas distintas
     * produce el self-play y cómo le va CHAMPION(epsilon) contra CHAMPION(determinista). epsilon=0 es
     * costo cero (solo empates exactos); epsilon>0 compra más variedad admitiendo cuasi-empates, a un
     * costo que este barrido cuantifica. No asevera — el valor está en los números.
     */
    @Test
    fun test_epsilon_ab_sweep() {
        val tournament = TournamentRunner()
        val varietyGames = 10
        val strengthGames = 16
        val temperature = 0.75
        val epsilons = listOf(0.0, 20.0, 60.0, 150.0)

        log("\n${"#".repeat(78)}")
        log("# A/B epsilon (temperature=$temperature) — variedad vs. fuerza en CHAMPION")
        log("# self-play: $varietyGames partidas · fuerza vs CHAMPION(det): $strengthGames partidas")
        log("#".repeat(78))
        log("%-8s | %-18s | %-18s | %s".format("epsilon", "partidas distintas", "aperturas distintas", "win% vs det"))
        log("-".repeat(78))

        for (eps in epsilons) {
            val cfg = championProd.copy(
                rootSelection = RootSelection(enabled = true, epsilon = eps, temperature = temperature),
            )
            val (full, open) = distinctGames(cfg, games = varietyGames)

            val result = tournament.runEngineMatch(
                engineA = personalityEngine("champion_eps$eps"),
                engineB = personalityEngine("champion_det"),
                configA = cfg,
                configB = championDeterministic,
                tournamentConfig = TournamentConfig(gamesPerMatch = strengthGames, maxMovesPerGame = 100),
                logInfo = {},
            )
            val total = result.winsA + result.winsB + result.draws
            val rate = if (total > 0) result.winsA.toDouble() / total * 100 else 0.0
            log(
                "%-8.0f | %-18s | %-18s | %.1f%% (%d-%d-%d)".format(
                    eps, "$full/$varietyGames", "$open/$varietyGames",
                    rate, result.winsA, result.winsB, result.draws,
                ),
            )
        }
        log("=".repeat(78))
    }
}
