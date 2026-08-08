package com.agustin.tarati.core.domain.analysis

/**
 * Calidad de una jugada según cuánto **empeoró** la posición para el bando que la hizo.
 *
 * Se mide por la caída de la probabilidad de victoria del que movió, entre la posición
 * antes y después de su jugada (ambas evaluadas con búsqueda, ver [GameAnalyzer]). La
 * eval de la posición *antes* ya asume la mejor jugada del que mueve, así que una caída
 * ≈0 significa que jugó (casi) lo mejor.
 *
 * No se distingue "brillante" (requeriría reconocer sacrificios); [BEST] es simplemente
 * "sin pérdida apreciable".
 */
enum class MoveQuality {
    BEST,
    GOOD,
    INACCURACY,
    MISTAKE,
    BLUNDER,
}

/**
 * Clasificación de una jugada del historial.
 *
 * @param moveIndex índice 0-based del movimiento en el historial.
 * @param quality categoría según la caída de win%.
 * @param winProbDrop caída de la probabilidad de victoria del que movió, en `[0,1]`
 *        (0 = sin pérdida; 0.24 = perdió 24 puntos de win%).
 */
data class MoveClassification(
    val moveIndex: Int,
    val quality: MoveQuality,
    val winProbDrop: Float,
)

/**
 * Deriva la calidad de cada jugada del [GameAnalysis] (la serie de win% por posición),
 * a costo ~0 — reutiliza el análisis ya computado.
 */
object MoveClassifier {

    /** Umbrales sobre la caída de win% del que movió (perspectiva del bando, `[0,1]`). */
    private const val BLUNDER_DROP: Float = 0.20f
    private const val MISTAKE_DROP: Float = 0.10f
    private const val INACCURACY_DROP: Float = 0.05f
    private const val GOOD_DROP: Float = 0.02f

    /**
     * Clasifica cada movimiento de [analysis].
     *
     * @param moverIsWhite dado el índice de movimiento, si lo hizo Blancas. Normalmente
     *        alternancia desde el turno inicial: `{ i -> (i % 2 == 0) == firstMoverWhite }`.
     */
    fun classify(
        analysis: GameAnalysis,
        moverIsWhite: (moveIndex: Int) -> Boolean,
    ): List<MoveClassification> {
        val series = analysis.series
        if (series.size < 2) return emptyList()

        val result = ArrayList<MoveClassification>(series.size - 1)
        for (i in 0 until series.size - 1) {
            val white = moverIsWhite(i)
            // Perspectiva del que movió: Blancas usan winProbWhite; Negras, su complemento.
            val before = series[i].winProbWhite.moverView(white)
            val after = series[i + 1].winProbWhite.moverView(white)
            val drop = (before - after).coerceAtLeast(0f)
            result.add(MoveClassification(moveIndex = i, quality = qualityFor(drop), winProbDrop = drop))
        }
        return result
    }

    private fun Float.moverView(white: Boolean): Float = if (white) this else 1f - this

    private fun qualityFor(drop: Float): MoveQuality = when {
        drop >= BLUNDER_DROP -> MoveQuality.BLUNDER
        drop >= MISTAKE_DROP -> MoveQuality.MISTAKE
        drop >= INACCURACY_DROP -> MoveQuality.INACCURACY
        drop >= GOOD_DROP -> MoveQuality.GOOD
        else -> MoveQuality.BEST
    }
}
