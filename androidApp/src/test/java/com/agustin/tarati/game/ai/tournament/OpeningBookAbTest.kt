package com.agustin.tarati.game.ai.tournament

import com.agustin.tarati.core.domain.ai.book.OpeningBook
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.game.ai.tournament.engine.base.personalityEngine
import com.agustin.tarati.game.ai.tournament.manager.TournamentConfig
import com.agustin.tarati.game.ai.tournament.manager.TournamentRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A/B del opening book: enfrenta el mismo motor con el book activado vs desactivado, a la misma
 * dificultad. Los colores alternan, así la fuerte ventaja del segundo jugador se reparte por igual y
 * la diferencia de wins aísla el efecto del book (que elige la mejor apertura empírica).
 *
 * Se corre a demanda (heavy). Imprime las tasas de victoria; no impone un umbral estricto para no
 * volverse flaky — el valor está en los números impresos.
 */
class OpeningBookAbTest {

    private fun logInfo(message: String) = println(message)

    private fun withNoise(config: EvaluationConfig, noise: Double): EvaluationConfig =
        config.copy(behavior = config.behavior.copy(evalNoise = noise))

    private fun runAb(label: String, base: EvaluationConfig, games: Int, maxMoves: Int = 120) {
        val bookConfig = base.copy(openingBookEnabled = true)
        val noBookConfig = base.copy(openingBookEnabled = false)

        val result = TournamentRunner().runEngineMatch(
            engineA = personalityEngine("book"),
            engineB = personalityEngine("nobook"),
            configA = bookConfig,
            configB = noBookConfig,
            tournamentConfig = TournamentConfig(
                gamesPerMatch = games,
                maxMovesPerGame = maxMoves,
            ),
            logInfo = {},
        )

        val total = result.winsA + result.winsB + result.draws
        val bookWr = 100.0 * result.winsA / total
        val noBookWr = 100.0 * result.winsB / total
        val decisive = result.winsA + result.winsB
        val bookDecisive = if (decisive > 0) 100.0 * result.winsA / decisive else 0.0

        logInfo("")
        logInfo("═══ A/B opening book — $label ($games games) ═══")
        logInfo("  book:   ${result.winsA} wins (${"%.1f".format(bookWr)}%)")
        logInfo("  nobook: ${result.winsB} wins (${"%.1f".format(noBookWr)}%)")
        logInfo("  draws:  ${result.draws}")
        logInfo("  book share of decisive games: ${"%.1f".format(bookDecisive)}%  (>50% = el book ayuda)")

        assertEquals("todas las partidas contabilizadas", games, total)
    }

    @Test
    fun ab_medium() {
        // MEDIUM trae evalNoise=4 → variedad de partidas sin tocar la config.
        runAb("MEDIUM", EvaluationConfig.getByDifficulty(Difficulty.MEDIUM), games = 200)
    }

    @Test
    fun ab_hard() {
        // HARD (config desplegada, book on por defecto) es determinista (evalNoise=0): se le agrega
        // ruido igual a ambos lados solo para muestrear una distribución de partidas.
        runAb("HARD+noise", withNoise(EvaluationConfig.getByDifficulty(Difficulty.HARD), 2.0), games = 60)
    }

    /**
     * Juega [games] partidas con **colores fijos** (sin alternar): el motor con [whiteConfig] siempre
     * lleva Blancas y el de [blackConfig] siempre Negras. Devuelve (whiteWin%, blackWin%, draw%).
     *
     * Con `alternateColors=false`, [TournamentRunner] asigna Blancas a engineB y Negras a engineA,
     * así que whiteWins = winsB y blackWins = winsA.
     */
    private data class ColorOutcome(
        val whiteWinPct: Double,
        val blackWinPct: Double,
        val drawPct: Double,
        val raw: String
    )

    private fun playFixedColors(
        whiteConfig: EvaluationConfig,
        blackConfig: EvaluationConfig,
        games: Int,
        maxMoves: Int = 120,
    ): ColorOutcome {
        val result = TournamentRunner().runEngineMatch(
            engineA = personalityEngine("black"),
            engineB = personalityEngine("white"),
            configA = blackConfig,
            configB = whiteConfig,
            tournamentConfig = TournamentConfig(
                gamesPerMatch = games,
                maxMovesPerGame = maxMoves,
                alternateColors = false
            ),
            logInfo = {},
        )
        val total = result.winsA + result.winsB + result.draws
        return ColorOutcome(
            whiteWinPct = 100.0 * result.winsB / total,
            blackWinPct = 100.0 * result.winsA / total,
            drawPct = 100.0 * result.draws / total,
            raw = "W ${result.winsB} - B ${result.winsA} - ${result.draws}d",
        )
    }

    /**
     * Descompone el efecto del book **por color** a bajo ruido: baseline (ambos sin book) vs book solo
     * en Blancas vs book solo en Negras. Aísla cuánto aporta el book a cada bando (esperado: mucho a
     * Blancas —corrige su peor apertura—, poco a Negras —ya va ganando).
     */
    @Test
    fun ab_hard_color_split_low_noise() {
        val noise = 1.0
        val base = withNoise(EvaluationConfig.getByDifficulty(Difficulty.HARD), noise)
        val bookOn = base.copy(openingBookEnabled = true)
        val bookOff = base.copy(openingBookEnabled = false)
        val games = 40

        val baseline = playFixedColors(whiteConfig = bookOff, blackConfig = bookOff, games = games)
        val whiteBook = playFixedColors(whiteConfig = bookOn, blackConfig = bookOff, games = games)
        val blackBook = playFixedColors(whiteConfig = bookOff, blackConfig = bookOn, games = games)

        logInfo("")
        logInfo("═══ A/B por color — HARD (evalNoise=$noise, $games partidas/batería) ═══")
        logInfo("  baseline (sin book):   white ${"%.1f".format(baseline.whiteWinPct)}%  black ${"%.1f".format(baseline.blackWinPct)}%   [${baseline.raw}]")
        logInfo("  book solo en BLANCAS:  white ${"%.1f".format(whiteBook.whiteWinPct)}%   (Δ ${"%+.1f".format(whiteBook.whiteWinPct - baseline.whiteWinPct)} pts)   [${whiteBook.raw}]")
        logInfo("  book solo en NEGRAS:   black ${"%.1f".format(blackBook.blackWinPct)}%   (Δ ${"%+.1f".format(blackBook.blackWinPct - baseline.blackWinPct)} pts)   [${blackBook.raw}]")
    }

    /**
     * ¿El book cambia lo que HARD **determinista** (sin ruido) ya jugaría? Si la búsqueda de HARD
     * coincide con el book, éste es redundante (neutro) en deployment; si difiere, el book override
     * la búsqueda con una jugada empíricamente mejor. Recorre la línea principal del book.
     */
    @Test
    fun ab_root_move_diagnostic() {
        val engine = TaratiAI()
        engine.setConfig(EvaluationConfig.getByDifficulty(Difficulty.HARD).copy(openingBookEnabled = false))

        logInfo("")
        logInfo("═══ book vs búsqueda determinista de HARD (línea principal) ═══")
        var state = initialGameState()
        var ply = 0
        var agreements = 0
        var comparisons = 0
        while (ply < 10) {
            val booked = OpeningBook.lookup(state) ?: break
            val searched = runBlocking { engine.getNextMove(state) }.move
            val agree = searched?.name == booked.name
            if (agree) agreements++
            comparisons++
            logInfo("  ply $ply (${state.currentTurn}): book=${booked.name}  HARD=${searched?.name}  ${if (agree) "=" else "≠ (book override)"}")
            state = state.applyMove(booked)
            ply++
        }
        logInfo("  coincidencias: $agreements/$comparisons  (menos coincidencias = el book aporta más)")
    }
}
