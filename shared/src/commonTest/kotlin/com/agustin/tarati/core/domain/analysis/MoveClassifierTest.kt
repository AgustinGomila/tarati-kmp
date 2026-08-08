package com.agustin.tarati.core.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifica la clasificación de jugadas por la caída de win% del que movió: umbrales,
 * perspectiva por color y la clamp de "mejoras" a [MoveQuality.BEST].
 */
class MoveClassifierTest {

    private fun ply(winWhite: Float) = PlyEval(winProbWhite = winWhite, scoreWhitePov = 0.0)

    /** Calidad de una única jugada [before]→[after] (winProbWhite), hecha por [white]. */
    private fun quality(before: Float, after: Float, white: Boolean): MoveQuality {
        val analysis = GameAnalysis(initial = ply(before), perMove = listOf(ply(after)))
        return MoveClassifier.classify(analysis) { white }.single().quality
    }

    @Test
    fun `umbrales sobre la caida de win% (Blancas)`() {
        // Márgenes claros dentro de cada banda para evitar flakiness de punto flotante.
        assertEquals(MoveQuality.BLUNDER, quality(0.90f, 0.69f, white = true)) // drop ~.21
        assertEquals(MoveQuality.MISTAKE, quality(0.90f, 0.79f, white = true)) // drop ~.11
        assertEquals(MoveQuality.INACCURACY, quality(0.90f, 0.84f, white = true)) // drop ~.06
        assertEquals(MoveQuality.GOOD, quality(0.90f, 0.87f, white = true)) // drop ~.03
        assertEquals(MoveQuality.BEST, quality(0.90f, 0.89f, white = true)) // drop ~.01
    }

    @Test
    fun `una mejora de la posicion se clasifica como BEST`() {
        // El win% del que movió sube → drop negativo, clampeado a 0.
        assertEquals(MoveQuality.BEST, quality(0.50f, 0.72f, white = true))
    }

    @Test
    fun `la perspectiva se invierte para Negras`() {
        // Para Negras, su win% = 1 - winProbWhite. Un salto de win% de Blancas es su blunder.
        assertEquals(MoveQuality.BLUNDER, quality(0.30f, 0.51f, white = false)) // black .70 → .49, drop ~.21
        assertEquals(MoveQuality.BEST, quality(0.30f, 0.10f, white = false)) // black .70 → .90, mejora → BEST
    }

    @Test
    fun `clasifica cada movimiento con alternancia desde Blancas`() {
        val analysis = GameAnalysis(
            initial = ply(0.50f), // series[0]
            perMove = listOf(
                ply(0.50f), // move0 Blancas: .50→.50, drop 0 → BEST
                ply(0.75f), // move1 Negras:  .50→.25 (perspectiva Negras), drop .25 → BLUNDER
                ply(0.68f), // move2 Blancas: .75→.68, drop .07 → INACCURACY
                ply(0.55f), // move3 Negras:  .32→.45 (mejora), drop 0 → BEST
                ply(0.42f), // move4 Blancas: .55→.42, drop .13 → MISTAKE
            ),
        )
        val result = MoveClassifier.classify(analysis) { it % 2 == 0 }

        assertEquals(5, result.size)
        assertEquals(listOf(0, 1, 2, 3, 4), result.map { it.moveIndex })
        assertEquals(
            listOf(
                MoveQuality.BEST,
                MoveQuality.BLUNDER,
                MoveQuality.INACCURACY,
                MoveQuality.BEST,
                MoveQuality.MISTAKE,
            ),
            result.map { it.quality },
        )
    }

    @Test
    fun `serie vacia o de un punto no produce clasificaciones`() {
        assertEquals(emptyList(), MoveClassifier.classify(GameAnalysis(ply(0.5f), emptyList())) { true })
    }
}
