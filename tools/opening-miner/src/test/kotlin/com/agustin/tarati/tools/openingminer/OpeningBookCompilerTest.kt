package com.agustin.tarati.tools.openingminer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningBookCompilerTest {

    private fun stat(move: String, wins: Int, losses: Int, draws: Int = 0) =
        MoveStat(move, games = wins + losses + draws, wins = wins, losses = losses, draws = draws)

    // ── Wilson ────────────────────────────────────────────────────────────────

    @Test
    fun `wilson penalizes small samples`() {
        // Mismo win-rate (50%), más partidas → cota inferior más alta (más confianza).
        val few = Wilson.lowerBound(5.0, 10)
        val many = Wilson.lowerBound(500.0, 1000)
        assertTrue(few < many, "50% sobre 10 partidas debe puntuar menos que 50% sobre 1000")
    }

    @Test
    fun `wilson counts draws as half`() {
        // 8 victorias + 4 tablas sobre 12 = score 10/12; la cota debe reflejar ~0.83, no 8/12.
        val stat = stat("A1-B1", wins = 8, losses = 0, draws = 4)
        assertEquals(10.0 / 12.0, stat.score, 1e-9)
        assertTrue(stat.wilsonLowerBound() > 0.5)
    }

    // ── Selección ──────────────────────────────────────────────────────────────

    @Test
    fun `compile picks the move with the highest wilson lower bound`() {
        // freq: C1-B1 se jugó más, pero pierde; C2-C3 gana con amplia muestra → debe elegirse C2-C3.
        val stats = mapOf(
            "pos1" to listOf(
                stat("C1-B1", wins = 300, losses = 700),  // 30%, mucha muestra
                stat("C2-C3", wins = 700, losses = 300),  // 70%, mucha muestra
            )
        )
        val book = OpeningBookCompiler.compile(stats)
        assertEquals(1, book.size)
        assertEquals("C2-C3", book.first().move)
    }

    @Test
    fun `compile prefers a solid sample over a lucky small one`() {
        val stats = mapOf(
            "pos1" to listOf(
                stat("A1-B1", wins = 5, losses = 0),      // 100% pero solo 5 partidas
                stat("A1-B2", wins = 620, losses = 380),  // 62% sobre 1000 partidas
            )
        )
        val book = OpeningBookCompiler.compile(stats)
        assertEquals("A1-B2", book.first().move, "Wilson debe favorecer la muestra sólida")
    }

    // ── Poda por soporte ────────────────────────────────────────────────────────

    @Test
    fun `positions without a supported move are pruned`() {
        val stats = mapOf(
            "weak" to listOf(stat("A1-B1", wins = 30, losses = 20)),         // 50 < 100 → fuera
            "strong" to listOf(stat("C1-C12", wins = 80, losses = 40)),      // 120 >= 100 → entra
        )
        val book = OpeningBookCompiler.compile(stats)
        assertEquals(1, book.size)
        assertEquals("strong", book.first().posHash)
    }

    @Test
    fun `only supported candidate moves are considered within a position`() {
        val stats = mapOf(
            "pos1" to listOf(
                stat("A1-B1", wins = 40, losses = 5),     // 45 partidas, 89% pero < min-support
                stat("A1-B2", wins = 300, losses = 300),  // 600 partidas, 50%
            )
        )
        val book = OpeningBookCompiler.compile(stats)
        assertEquals("A1-B2", book.first().move, "la jugada sin soporte no debe elegirse aunque tenga mejor win-rate")
    }

    @Test
    fun `empty stats yields an empty book`() {
        assertTrue(OpeningBookCompiler.compile(emptyMap()).isEmpty())
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    @Test
    fun `parseOpeningStats groups rows by position`() {
        val tsv = sequenceOf(
            "pos_hash\tmove\tgames\twins\tlosses\tdraws",
            "aaa\tC1-C12\t120\t80\t40\t0",
            "aaa\tC1-B1\t100\t20\t80\t0",
            "bbb\tC7-C6\t200\t170\t30\t0",
            "\t\t\t\t\t",           // fila basura → se salta
        )
        val stats = parseOpeningStats(tsv)
        assertEquals(2, stats.size)
        assertEquals(2, stats.getValue("aaa").size)
        assertNull(stats["ccc"])
    }
}
