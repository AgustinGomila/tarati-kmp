package com.agustin.tarati.tools.openingminer

import kotlin.math.sqrt

/**
 * Estadística acumulada de una jugada desde una posición canónica (fila de `opening_stats`), siempre
 * desde el punto de vista del bando que la juega.
 */
data class MoveStat(
    val move: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
) {
    /** Puntaje relativo (victoria=1, tabla=½, derrota=0) sobre el total de partidas. */
    val score: Double get() = if (games > 0) (wins + 0.5 * draws) / games else 0.0

    /** Cota inferior de Wilson del [score]: penaliza muestras chicas para no premiar rachas de azar. */
    fun wilsonLowerBound(z: Double = Wilson.Z_95): Double = Wilson.lowerBound(wins + 0.5 * draws, games, z)
}

/**
 * Una entrada del opening book: para una posición canónica, la jugada recomendada al bando al turno,
 * con la estadística que la respalda.
 */
data class BookEntry(
    val posHash: String,
    val move: String,
    val games: Int,
    val score: Double,
    val wilson: Double,
)

/**
 * Cota inferior del intervalo de confianza de Wilson para una proporción binomial. Rankear por esta
 * cota (en vez de por el win-rate crudo) evita que una jugada con pocas partidas y buena racha supere
 * a una con muchas partidas y buen rendimiento sostenido.
 *
 * Las tablas se modelan como media victoria ([successes] = wins + ½·draws), aproximación estándar
 * para puntajes; en la apertura las tablas son ~0, así que el sesgo es despreciable.
 */
object Wilson {
    /** z para un intervalo de confianza del 95 %. */
    const val Z_95: Double = 1.96

    fun lowerBound(successes: Double, total: Int, z: Double = Z_95): Double {
        if (total <= 0) return 0.0
        val n = total.toDouble()
        val p = (successes / n).coerceIn(0.0, 1.0)
        val z2 = z * z
        val denominator = 1.0 + z2 / n
        val center = p + z2 / (2 * n)
        val margin = z * sqrt(p * (1 - p) / n + z2 / (4 * n * n))
        return (center - margin) / denominator
    }
}
