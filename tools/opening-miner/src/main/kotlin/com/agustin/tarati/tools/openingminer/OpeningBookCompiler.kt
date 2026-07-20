package com.agustin.tarati.tools.openingminer

/**
 * Etapa 2 del pipeline: reduce la tabla cruda `opening_stats` (posición canónica → jugadas con su
 * conteo) al opening book. Por cada posición:
 *
 * 1. Descarta las jugadas con menos de [minSupport] partidas (muestra insuficiente).
 * 2. De las que quedan, elige la de mayor **cota inferior de Wilson** (no la de mayor frecuencia ni
 *    la de mayor win-rate crudo — ver [Wilson]).
 * 3. Si ninguna jugada supera el umbral, la posición no entra al book.
 *
 * Es puro y determinista: la política de selección (top-1) queda acá para poder testearla aislada de
 * la lectura/escritura de archivos.
 */
object OpeningBookCompiler {

    /** Mínimo de partidas para que una jugada sea candidata del book. */
    const val DEFAULT_MIN_SUPPORT: Int = 100

    /**
     * @param stats posición canónica → lista de jugadas observadas desde ahí.
     * @return una [BookEntry] por posición que tenga al menos una jugada con soporte, ordenadas por hash.
     */
    fun compile(
        stats: Map<String, List<MoveStat>>,
        minSupport: Int = DEFAULT_MIN_SUPPORT,
        z: Double = Wilson.Z_95,
    ): List<BookEntry> =
        stats.entries
            .mapNotNull { (posHash, moves) ->
                val best = moves
                    .filter { it.games >= minSupport }
                    .maxByOrNull { it.wilsonLowerBound(z) }
                    ?: return@mapNotNull null
                BookEntry(
                    posHash = posHash,
                    move = best.move,
                    games = best.games,
                    score = best.score,
                    wilson = best.wilsonLowerBound(z),
                )
            }
            .sortedBy { it.posHash }
}
