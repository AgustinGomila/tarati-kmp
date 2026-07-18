package com.agustin.tarati.core.domain.game.time

import com.agustin.tarati.core.domain.game.time.TimeControlMode.Companion.deserialize
import com.agustin.tarati.core.domain.game.time.TimeControlMode.Companion.serialize
import kotlinx.serialization.Serializable

/**
 * Modo de control de tiempo para una partida.
 *
 * Aplica simétricamente a ambos bandos — un único [TimeControlMode] gobierna el
 * reloj de White y el de Black. Las variantes cubren los formatos más comunes
 * en juegos de tablero:
 *
 * - [Unlimited]: sin reloj. Las partidas existentes sin control de tiempo usan
 *   este modo; es el default de [SettingsRepository].
 * - [SuddenDeath]: un único baño de tiempo, sin incremento. Al agotarse → pérdida por tiempo.
 * - [Fischer]: tiempo base + incremento fijo añadido *después* de cada medio-movimiento.
 * - [Bronstein]: tiempo base + delay. El tiempo del medio-movimiento se descuenta solo
 *   si excede el delay (elapsed − delay, nunca negativo).
 * - [Byoyomi]: tiempo base + N períodos de duración fija. Al agotar la base, cada
 *   medio-movimiento dispone de un período completo; si se excede, se consume un
 *   período y sigue. Se pierde al agotar el último período.
 *
 * ## Serialización
 * [serialize] produce strings compactas tipo `"fischer:180000:2000"` para persistir
 * en DataStore y en el campo `timeControl` del PGN. [deserialize] es tolerante:
 * cualquier string mal formada o id desconocido cae a [Unlimited]. La jerarquía
 * es además [Serializable] (kotlinx) para viajar embebida en otros estados.
 */
@Serializable
sealed class TimeControlMode {

    /** Identificador estable usado por [serialize] / [deserialize]. */
    abstract val id: String

    /**
     * Sin reloj. Las partidas se juegan sin límite de tiempo.
     */
    @Serializable
    object Unlimited : TimeControlMode() {
        override val id: String get() = ID

        const val ID: String = "unlimited"
    }

    /**
     * Un único baño de tiempo, sin incremento. Al agotarse → pérdida por bandera caída.
     *
     * @param totalMs Tiempo total disponible por bando, en milisegundos.
     */
    @Serializable
    data class SuddenDeath(val totalMs: Long) : TimeControlMode() {
        override val id: String get() = ID

        companion object {
            const val ID: String = "sudden"
        }
    }

    /**
     * Tiempo base más incremento fijo añadido tras cada medio-movimiento del jugador.
     *
     * El incremento se acredita *después* de aplicar el movimiento, por lo que un
     * jugador nunca puede ganar tiempo sin mover.
     *
     * @param baseMs      Tiempo inicial por bando, en milisegundos.
     * @param incrementMs Incremento añadido tras cada medio-movimiento.
     */
    @Serializable
    data class Fischer(
        val baseMs: Long,
        val incrementMs: Long,
    ) : TimeControlMode() {
        override val id: String get() = ID

        companion object {
            const val ID: String = "fischer"
        }
    }

    /**
     * Tiempo base con delay. El tiempo consumido por cada medio-movimiento es
     * `max(0, elapsed − delayMs)` — los primeros [delayMs] no descuentan.
     *
     * @param baseMs  Tiempo inicial por bando, en milisegundos.
     * @param delayMs Delay por medio-movimiento en milisegundos.
     */
    @Serializable
    data class Bronstein(
        val baseMs: Long,
        val delayMs: Long,
    ) : TimeControlMode() {
        override val id: String get() = ID

        companion object {
            const val ID: String = "bronstein"
        }
    }

    /**
     * Tiempo base más [periods] períodos de [periodMs] milisegundos cada uno.
     *
     * Cuando la base se agota, cada medio-movimiento del jugador dispone de un
     * período completo; si el jugador excede el período, se consume uno y el
     * siguiente medio-movimiento dispone de nuevo del período completo. Se pierde
     * por tiempo solo al agotar el último período.
     *
     * @param baseMs   Tiempo inicial principal por bando, en milisegundos.
     * @param periodMs Duración de cada período de byoyomi.
     * @param periods  Cantidad de períodos disponibles por bando.
     */
    @Serializable
    data class Byoyomi(
        val baseMs: Long,
        val periodMs: Long,
        val periods: Int,
    ) : TimeControlMode() {
        override val id: String get() = ID

        companion object {
            const val ID: String = "byoyomi"
        }
    }

    companion object {
        /** Separador usado en la representación serializada. */
        private const val SEP: String = ":"

        /**
         * Serializa [mode] a una string estable de la forma `"id[:param...]"`.
         * Ejemplos:
         *  - `"unlimited"`
         *  - `"sudden:300000"`
         *  - `"fischer:180000:2000"`
         *  - `"bronstein:300000:3000"`
         *  - `"byoyomi:300000:30000:3"`
         */
        fun serialize(mode: TimeControlMode): String = when (mode) {
            is Unlimited -> Unlimited.ID
            is SuddenDeath -> "${SuddenDeath.ID}$SEP${mode.totalMs}"
            is Fischer -> "${Fischer.ID}$SEP${mode.baseMs}$SEP${mode.incrementMs}"
            is Bronstein -> "${Bronstein.ID}$SEP${mode.baseMs}$SEP${mode.delayMs}"
            is Byoyomi -> "${Byoyomi.ID}$SEP${mode.baseMs}$SEP${mode.periodMs}$SEP${mode.periods}"
        }

        /**
         * Inversa de [serialize]. Strings mal formadas, ids desconocidos o errores
         * de parseo numérico caen silenciosamente a [Unlimited].
         */
        fun deserialize(raw: String): TimeControlMode = runCatching {
            val parts = raw.split(SEP)
            when (parts.firstOrNull()) {
                Unlimited.ID -> Unlimited
                SuddenDeath.ID -> SuddenDeath(
                    totalMs = parts[1].toLong(),
                )

                Fischer.ID -> Fischer(
                    baseMs = parts[1].toLong(),
                    incrementMs = parts[2].toLong(),
                )

                Bronstein.ID -> Bronstein(
                    baseMs = parts[1].toLong(),
                    delayMs = parts[2].toLong(),
                )

                Byoyomi.ID -> Byoyomi(
                    baseMs = parts[1].toLong(),
                    periodMs = parts[2].toLong(),
                    periods = parts[3].toInt(),
                )

                else -> Unlimited
            }
        }.getOrDefault(Unlimited)
    }
}