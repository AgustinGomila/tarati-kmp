package com.agustin.tarati.tools.openingminer

/**
 * Conteo acumulado de resultados de una jugada concreta desde una posición concreta, siempre desde
 * el punto de vista del bando que la juega. [games] es el total de ocurrencias.
 */
class MoveCounts(
    var wins: Int = 0,
    var losses: Int = 0,
    var draws: Int = 0,
) {
    val games: Int get() = wins + losses + draws

    fun record(outcome: PlyOutcome) {
        when (outcome) {
            PlyOutcome.WIN -> wins++
            PlyOutcome.LOSS -> losses++
            PlyOutcome.DRAW -> draws++
        }
    }
}

/**
 * Filtro de calidad sobre las partidas del corpus (defaults acordados en el plan). Contrarresta que
 * el corpus sea casi todo auto-juego de bots: descarta finales de bajo valor y partidas entre bots
 * débiles. Parámetros expuestos para re-tunear sin tocar código.
 *
 * @property excludeEndMethods Métodos de fin a descartar (por defecto `triple`: nadie superó a nadie).
 * @property minRating Rating mínimo exigido a **ambos** jugadores (por defecto 1600 = tier Hard).
 */
data class QualityFilter(
    val excludeEndMethods: Set<String> = setOf("triple"),
    val minRating: Int = 1600,
) {
    fun accepts(record: GameRecord): Boolean =
        record.endMethod !in excludeEndMethods &&
                record.whiteRating >= minRating &&
                record.blackRating >= minRating
}

/**
 * Acumula observaciones de apertura en la tabla cruda `opening_stats`:
 * `posHash → { moveName → MoveCounts }`.
 *
 * La agregación es en memoria: el espacio de posiciones de apertura está acotado por [horizon], así
 * que cabe holgado aunque el corpus tenga cientos de miles de partidas.
 */
class OpeningAggregator(
    private val horizon: Int = OpeningExtractor.DEFAULT_HORIZON_PLIES,
) {
    /** `posHash → moveName → conteos`. */
    val stats: MutableMap<String, MutableMap<String, MoveCounts>> = HashMap()

    var gamesProcessed: Int = 0
        private set
    var pliesRecorded: Int = 0
        private set

    /** Reproduce una partida y suma sus observaciones de apertura a [stats]. */
    fun add(record: GameRecord) {
        for (obs in OpeningExtractor.extract(record, horizon)) {
            val byMove = stats.getOrPut(obs.posHash) { HashMap() }
            byMove.getOrPut(obs.moveName) { MoveCounts() }.record(obs.outcome)
            pliesRecorded++
        }
        gamesProcessed++
    }

    fun addAll(records: Sequence<GameRecord>): Unit = records.forEach(::add)

    /** Cantidad de posiciones distintas registradas. */
    fun positionCount(): Int = stats.size
}
