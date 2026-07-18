package com.agustin.tarati.core.domain.game.time

import kotlinx.serialization.Serializable

/**
 * TimeControl - Tipos de time control
 *
 * Clasificación estándar de ajedrez:
 * - BULLET: < 3 minutos
 * - BLITZ: 3-10 minutos
 * - RAPID: 10-30 minutos
 * - CLASSICAL: > 30 minutos
 */
@Serializable
enum class TimeControl {
    BULLET,
    BLITZ,
    RAPID,
    CLASSICAL;

    /**
     * Convierte a string para BD
     */
    val key: String
        get() = name.lowercase()

    /**
     * Control de tiempo predefinido
     */
    val timeControl: Pair<Int, Int>
        get() = when (this) {
            BULLET -> 60 to 0 // 1+0
            BLITZ -> 180 to 2 // 3+2
            RAPID -> 600 to 0 // 10+0
            CLASSICAL -> 1800 to 30 // 30+30
        }

    val description: String
        get() = key.replaceFirstChar(Char::titlecase)

    companion object {
        /**
         * Determina el tipo de time control basado en el tiempo total
         *
         * @param initialSeconds Tiempo inicial en segundos
         * @param increment Incremento en segundos
         * @return Tipo de time control
         */
        fun fromTime(initialSeconds: Int, increment: Int): TimeControl {
            val estimatedTotalSeconds = initialSeconds + (increment * 40) // 40 movimientos estimados
            val totalMinutes = estimatedTotalSeconds / 60

            return when {
                totalMinutes < 3 -> BULLET
                totalMinutes < 10 -> BLITZ
                totalMinutes < 30 -> RAPID
                else -> CLASSICAL
            }
        }

        /**
         * Convierte desde string de BD
         */
        fun fromKey(value: String): TimeControl =
            valueOf(value.uppercase())

        fun list(): List<String> = entries.map { it.key }
    }
}