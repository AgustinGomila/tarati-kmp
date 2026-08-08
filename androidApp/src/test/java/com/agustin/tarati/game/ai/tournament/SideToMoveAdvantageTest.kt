package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.game.ai.tournament.engine.base.personalityEngine
import com.agustin.tarati.game.ai.tournament.manager.TournamentConfig
import com.agustin.tarati.game.ai.tournament.manager.TournamentRunner
import com.agustin.tarati.testutil.TestLog
import org.junit.Test

/**
 * ¿La "ventaja de Negras" observada en el self-play (Champion vs Champion) es una propiedad
 * del juego, o un artefacto del desempate keep-first de CHAMPION?
 *
 * **Conclusión (ver docs/internal/game_dynamics.md): es real, no un artefacto.** Persiste con
 * desempate ALEATORIO (~88% Negras a Champion, ~93% a Hard) y coincide con los datos de producción
 * bot-vs-bot (~87% Negras en Champion-vs-Champion). Es **dependiente de la profundidad**: a Medium
 * (depth-3) es ~parejo; emerge con la búsqueda profunda. El keep-first solo fija una línea concreta
 * (también ganada por Negras), no causa la ventaja.
 *
 * ## Diseño
 * Champion/Hard/Medium vs sí mismo, **sin alternar colores**, con desempate aleatorio y keep-first.
 * ⚠️ Ojo con el mapeo de colores del runner (ver [measure]).
 *
 * No asevera nada: el valor está en los números impresos. Correr con:
 *   `./gradlew :androidApp:testDebugUnitTest --tests "*SideToMoveAdvantageTest.*"`
 */
class SideToMoveAdvantageTest {

    private fun logInfo(m: String) = TestLog.info(m)

    /**
     * Corre [games] partidas Champion-vs-Champion con [config] en ambos, sin alternar colores.
     *
     * ⚠️ Con `alternateColors = false`, [TournamentRunner.runEngineMatch] toma siempre la rama else
     * y asigna **whiteEngine = engineB, blackEngine = engineA**. Por eso `winsA` = victorias de
     * **Negras** y `winsB` = victorias de **Blancas**. Se mapea explícitamente abajo para no confundir.
     */
    private fun measure(label: String, difficulty: Difficulty, deterministic: Boolean, games: Int) {
        val config = EvaluationConfig.getByDifficulty(difficulty)
            .copy(deterministicTiebreak = deterministic)

        val result = TournamentRunner().runEngineMatch(
            engineA = personalityEngine("A"),
            engineB = personalityEngine("B"),
            configA = config,
            configB = config,
            tournamentConfig = TournamentConfig(
                gamesPerMatch = games,
                maxMovesPerGame = 200,
                alternateColors = false,
                collectMetrics = false,
                showProgress = false,
            ),
            logInfo = {},
        )
        val blackWins = result.winsA // engineA = Negras (ver nota arriba)
        val whiteWins = result.winsB // engineB = Blancas
        val total = blackWins + whiteWins + result.draws
        val wPct = if (total > 0) whiteWins * 100.0 / total else 0.0
        val bPct = if (total > 0) blackWins * 100.0 / total else 0.0
        logInfo(
            "%-34s → Negras %2d (%.0f%%)  ·  Blancas %2d (%.0f%%)  ·  Tablas %d   [%d partidas]"
                .format(label, blackWins, bPct, whiteWins, wPct, result.draws, total)
        )
    }

    /**
     * ¿Cuánto sesga el color un torneo si no se balancea por enfrentamiento? Round-robin de 3
     * motores **idénticos** (mismo config Hard, desempate aleatorio): cualquier diferencia de
     * standings es puro artefacto de color, no de habilidad.
     *  - `alternateColors = true`  → cada par juega mitad y mitad → debería quedar ~parejo.
     *  - `alternateColors = false` → cada par con color fijo (engineB siempre Blancas) → el que
     *    queda de Negras en sus cruces domina, y el standings replica el orden de la lista.
     */
    @Test
    fun colorBalanceInTournaments() {
        val cfg = EvaluationConfig.getByDifficulty(Difficulty.HARD).copy(deterministicTiebreak = false)
        val configs = mapOf("E1" to cfg, "E2" to cfg, "E3" to cfg)

        fun run(alternate: Boolean): String {
            val engines = listOf(personalityEngine("E1"), personalityEngine("E2"), personalityEngine("E3"))
            val res = TournamentRunner().runEngineRoundRobin(
                engines = engines,
                configs = configs,
                tournamentConfig = TournamentConfig(
                    gamesPerMatch = 12, maxMovesPerGame = 200,
                    alternateColors = alternate, collectMetrics = false, showProgress = false,
                ),
                logInfo = {},
            )
            val byName = res.associateBy { it.engine.name }
            val line = listOf("E1", "E2", "E3").joinToString("  ") { n ->
                val p = byName[n]!!
                "%s %.0f%%".format(n, p.winRate * 100)
            }
            val spread = (res.maxOf { it.winRate } - res.minOf { it.winRate }) * 100
            return "$line   │ spread ${"%.0f".format(spread)} pts"
        }

        logInfo("\n${"=".repeat(80)}")
        logInfo("  BALANCEO DE COLOR EN TORNEOS — 3 motores IDÉNTICOS (Hard), round-robin")
        logInfo("=".repeat(80))
        logInfo("  alternateColors=TRUE  (balanceado)   → ${run(true)}")
        logInfo("  alternateColors=FALSE (color fijo)   → ${run(false)}")
        logInfo("=".repeat(80))
    }

    @Test
    fun sideToMoveAdvantage() {
        logInfo("\n${"=".repeat(80)}")
        logInfo("  VENTAJA DEL BANDO — Champion/Hard vs sí mismo, engineA=Blancas siempre")
        logInfo("=".repeat(80))

        // Régimen aleatorio: muestrea la tendencia real del juego bajo juego óptimo.
        measure("CHAMPION · desempate ALEATORIO", Difficulty.CHAMPION, deterministic = false, games = 16)
        measure("HARD · desempate ALEATORIO", Difficulty.HARD, deterministic = false, games = 40)
        measure("MEDIUM · desempate ALEATORIO", Difficulty.MEDIUM, deterministic = false, games = 60)

        // Régimen keep-first: una única línea fija y reproducible.
        measure("CHAMPION · KEEP-FIRST (línea fija)", Difficulty.CHAMPION, deterministic = true, games = 1)
        measure("HARD · KEEP-FIRST (línea fija)", Difficulty.HARD, deterministic = true, games = 1)

        logInfo("=".repeat(80))
    }
}
