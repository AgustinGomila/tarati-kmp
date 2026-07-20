package com.agustin.tarati.tools.openingminer

import com.agustin.tarati.core.domain.game.play.GameResult
import com.agustin.tarati.core.domain.game.play.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningExtractorTest {

    /** Prefijo de apertura de una partida real del corpus (18 jugadas, todas legales). */
    private val realPgn =
        "C2-C3 C8-B4 C1-C12 C7-C6 D1-C1 D4-C8 C1-B1 C8-C9 C12-B6 B4-A1 " +
                "C3-B2 C6-B3 D2-C2 D3-C7 C2-C3 C7-C6 C3-C4 C6-C5"

    private fun record(pgn: String = realPgn, result: GameResult = GameResult.WHITE_WIN) =
        GameRecord(
            pgn = pgn,
            result = result,
            endMethod = "mit",
            rated = true,
            timeControl = "rapid",
            whiteRating = 1900,
            blackRating = 1950,
            whiteIsBot = true,
            blackIsBot = true,
        )

    @Test
    fun `extract emits one observation per ply up to the horizon`() {
        assertEquals(10, OpeningExtractor.extract(record()).size)
        assertEquals(4, OpeningExtractor.extract(record(), horizon = 4).size)
    }

    @Test
    fun `first observation is the initial position and the canonical first move`() {
        val obs = OpeningExtractor.extract(record())
        val initial = GameState.initialGameState()
        // The initial position is self-symmetric, so its canonical hash equals its raw hash.
        assertEquals(initial.hashBoard(), obs.first().posHash)
        // The move is folded to its canonical mirror representative.
        val expected = initial.canonicalMove(OpeningExtractor.parsePgn("C2-C3").first()).second.name
        assertEquals(expected, obs.first().moveName)
    }

    @Test
    fun `outcome is relative to the side to move`() {
        // WHITE_WIN: White-to-move plies (0,2,4...) ganan; Black-to-move (1,3,5...) pierden.
        val obs = OpeningExtractor.extract(record(result = GameResult.WHITE_WIN))
        assertEquals(PlyOutcome.WIN, obs[0].outcome)
        assertEquals(PlyOutcome.LOSS, obs[1].outcome)
        assertEquals(PlyOutcome.WIN, obs[2].outcome)

        // DRAW: ambos lados empatan.
        val drawObs = OpeningExtractor.extract(record(result = GameResult.DRAW))
        assertTrue(drawObs.all { it.outcome == PlyOutcome.DRAW })
    }

    @Test
    fun `extraction is deterministic`() {
        assertEquals(OpeningExtractor.extract(record()), OpeningExtractor.extract(record()))
    }

    @Test
    fun `aggregator sums wins and losses across games from the same position`() {
        val aggregator = OpeningAggregator()
        aggregator.add(record(result = GameResult.WHITE_WIN))
        aggregator.add(record(result = GameResult.BLACK_WIN))

        val initialHash = GameState.initialGameState().hashBoard()
        val movesFromInitial = aggregator.stats.getValue(initialHash)
        assertEquals(1, movesFromInitial.size, "ambas partidas comparten la misma jugada canónica")

        val counts = movesFromInitial.values.first()
        assertEquals(2, counts.games)
        assertEquals(1, counts.wins)   // White ganó una (WHITE_WIN, White al turno)
        assertEquals(1, counts.losses) // White perdió la otra (BLACK_WIN, White al turno)
        assertEquals(0, counts.draws)
        assertEquals(2, aggregator.gamesProcessed)
    }

    @Test
    fun `parsePgn understands the in-place promotion token`() {
        val moves = OpeningExtractor.parsePgn("C1-B1 C7-C7")
        assertEquals(2, moves.size)
        assertTrue(moves[1].isPromotion())
        assertEquals("C7=R", moves[1].name)
        assertEquals("C1-B1", moves[0].name)
    }

    @Test
    fun `corrupt pgn yields no observations without throwing`() {
        assertTrue(OpeningExtractor.extract(record(pgn = "not-a-real-token garbage")).isEmpty())
        assertTrue(OpeningExtractor.extract(record(pgn = "")).isEmpty())
    }
}
