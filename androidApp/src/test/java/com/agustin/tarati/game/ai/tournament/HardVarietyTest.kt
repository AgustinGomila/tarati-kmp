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
 * Valida la **selección en la raíz** ([RootSelection]) de HARD: introduce variedad partida a partida en
 * HARD-vs-HARD sin debilitar el juego ni acercar el tier a CHAMPION. Espejo de [ChampionVarietyTest] al
 * depth de HARD (5).
 *
 * HARD adopta el mismo mecanismo que CHAMPION (keep-first determinista en las ramas profundas +
 * `rootSelection` con ventana de apertura). Antes usaba reservoir aleatorio: daba variedad pero uniforme
 * (reintroducía OBS-1, abrir con la jugada pasiva) y sin diversificar la apertura estratégica (solo los
 * reflejos espejo). `rootSelection` la mejora sesgando hacia la afilada y admitiendo la 2ª mejor apertura.
 *
 * Tres ejes:
 *  - **Variedad** — self-play HARD-vs-HARD debe producir transcripciones distintas. Se mide con la política
 *    activa vs. desactivada (control determinista → una sola partida).
 *  - **Fuerza neutra** — la variedad no puede costar habilidad: HARD(variedad) empareja ~50 % contra
 *    HARD(determinista).
 *  - **Gradiente** — la variedad no puede acercar HARD a CHAMPION: CHAMPION sigue sin perder el head-to-head.
 *
 * Tests lentos (depth 5). No forman parte de la batería rápida; correr puntualmente con
 * `--tests "*HardVarietyTest*"`.
 */
class HardVarietyTest {

    private fun log(message: String) = TestLog.info(message)

    // Config de producción de HARD (gambit + rootSelection habilitada, epsilon=0/temp=0.75 + ventana de apertura).
    private val hardProd = EvaluationConfig.getByDifficulty(Difficulty.HARD)

    // Control determinista: misma config pero sin selección en la raíz → dos HARD juegan idéntico
    // (deterministicTiebreak=true se conserva → keep-first puro).
    private val hardDeterministic = hardProd.copy(rootSelection = RootSelection())

    private val championProd = EvaluationConfig.getByDifficulty(Difficulty.CHAMPION)

    // ── Self-play helper ──────────────────────────────────────────────────────

    /**
     * Juega una partida HARD-vs-HARD con [config] en ambos bandos y devuelve la transcripción
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
     * Control: sin selección en la raíz, HARD-vs-HARD es determinista → una sola partida distinta.
     * Con la política de producción, aparecen múltiples partidas.
     */
    @Test
    fun test_root_selection_produces_variety() {
        val games = 12

        val (detFull, _) = distinctGames(hardDeterministic, games = 5)
        log("CONTROL (rootSelection off): $detFull partidas distintas de 5 → esperado 1")
        assertEquals(
            "Sin selección en la raíz, HARD-vs-HARD debe ser determinista (1 partida distinta)",
            1,
            detFull,
        )

        val (varFull, varOpen) = distinctGames(hardProd, games = games)
        log("PROD (rootSelection on, eps=0 temp=0.75 + apertura): $varFull partidas distintas / $varOpen aperturas distintas de $games")
        assertTrue(
            "La selección en la raíz debe producir variedad (más de 1 partida distinta en $games)",
            varFull > 1,
        )
    }

    // ── Fuerza ──────────────────────────────────────────────────────────────────

    /**
     * HARD(variedad) no debe perder fuerza frente a HARD(determinista): al alternar colores el match es de
     * igual habilidad → reparto equilibrado. Aserción lenient (no colapsa a <30 %).
     */
    @Test
    fun test_variety_hard_matches_deterministic_hard() {
        val tournament = TournamentRunner()
        val games = 24

        val result = tournament.runEngineMatch(
            engineA = personalityEngine("hard_variety"),
            engineB = personalityEngine("hard_det"),
            configA = hardProd,
            configB = hardDeterministic,
            tournamentConfig = TournamentConfig(gamesPerMatch = games, maxMovesPerGame = 100),
            logInfo = ::log,
        )

        val total = result.winsA + result.winsB + result.draws
        val rate = if (total > 0) result.winsA.toDouble() / total else 0.0
        log(
            "HARD(variedad) vs HARD(determinista): " +
                    "${"%.1f".format(rate * 100)}% (${result.winsA}-${result.winsB}-${result.draws})",
        )
        assertTrue(
            "La variedad no debe debilitar a HARD frente a sí mismo determinista " +
                    "(actual: ${result.winsA}-${result.winsB}-${result.draws})",
            result.winsA >= total * 0.30,
        )
    }

    /**
     * La variedad no debe acercar HARD a CHAMPION: CHAMPION debe seguir sin perder el head-to-head vs
     * HARD(variedad). Mismo criterio lenient que [RoundRobinTest.test_champion_dominates_hard_regression].
     */
    @Test
    fun test_champion_still_dominates_variety_hard() {
        val tournament = TournamentRunner()
        val result = tournament.runEngineMatch(
            engineA = personalityEngine("champion"),
            engineB = personalityEngine("hard_variety"),
            configA = championProd,
            configB = hardProd,
            tournamentConfig = TournamentConfig(gamesPerMatch = 30, maxMovesPerGame = 100),
            logInfo = ::log,
        )
        val total = result.winsA + result.winsB + result.draws
        val rate = if (total > 0) result.winsA.toDouble() / total else 0.0
        log(
            "CHAMPION vs HARD(variedad): ${"%.1f".format(rate * 100)}% " +
                    "(${result.winsA}-${result.winsB}-${result.draws})",
        )
        assertTrue(
            "CHAMPION no debe perder el head-to-head vs HARD(variedad) " +
                    "(actual: ${result.winsA}-${result.winsB}-${result.draws})",
            result.winsA >= result.winsB,
        )
    }

    // ── A/B de epsilon (log-only) ────────────────────────────────────────────────

    /**
     * Barre `epsilon` (con temperature fija) reportando, por valor: cuántas partidas/aperturas distintas
     * produce el self-play y cómo le va HARD(epsilon) contra HARD(determinista). epsilon=0 es costo cero
     * (solo empates exactos); epsilon>0 compra más variedad admitiendo cuasi-empates, a un costo que este
     * barrido cuantifica. No asevera — el valor está en los números.
     */
    @Test
    fun test_epsilon_ab_sweep() {
        val tournament = TournamentRunner()
        val varietyGames = 10
        val strengthGames = 16
        val temperature = 0.75
        val epsilons = listOf(0.0, 20.0, 60.0, 150.0)

        log("\n${"#".repeat(78)}")
        log("# A/B epsilon (temperature=$temperature) — variedad vs. fuerza en HARD")
        log("# self-play: $varietyGames partidas · fuerza vs HARD(det): $strengthGames partidas")
        log("#".repeat(78))
        log("%-8s | %-18s | %-18s | %s".format("epsilon", "partidas distintas", "aperturas distintas", "win% vs det"))
        log("-".repeat(78))

        for (eps in epsilons) {
            val cfg = hardProd.copy(
                rootSelection = RootSelection(enabled = true, epsilon = eps, temperature = temperature),
            )
            val (full, open) = distinctGames(cfg, games = varietyGames)

            val result = tournament.runEngineMatch(
                engineA = personalityEngine("hard_eps$eps"),
                engineB = personalityEngine("hard_det"),
                configA = cfg,
                configB = hardDeterministic,
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
