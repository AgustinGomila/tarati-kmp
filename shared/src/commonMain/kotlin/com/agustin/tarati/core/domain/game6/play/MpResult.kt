package com.agustin.tarati.core.domain.game6.play

import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import kotlinx.serialization.Serializable

/** Motivo por el que se determinó el resultado final. */
@Serializable
enum class MpEndReason {
    /** Quedó un solo jugador activo (todos los demás se retiraron). */
    LAST_STANDING,

    /** Se llegó al umbral de jugadores y uno tiene más piezas que el/los otros. */
    PIECE_MAJORITY,

    /** Empate en cantidad de piezas entre los finalistas → victoria compartida. */
    SHARED,
}

/**
 * Causa del **corte por estancamiento** cuando la partida se resolvió antes de la terminación natural
 * ("quedan 2"). El juego MP no tiene "tablas" (§2.6 del plan): un corte igual resuelve por mayoría de
 * piezas — con [MpEndReason.PIECE_MAJORITY] o, si el máximo está empatado, [MpEndReason.SHARED].
 * `null` en [MpResult.cut] = terminación natural (sin corte).
 */
@Serializable
enum class MpCutReason {
    /** La misma posición (piezas + turno) se repitió el nº de veces del umbral (triple repetición). */
    REPETITION,

    /** Se alcanzó el límite de jugadas sin ninguna captura/conversión (estancamiento silencioso). */
    NO_CAPTURE_LIMIT,
}

/**
 * Resultado final de una partida multijugador.
 *
 * @property winners uno o más colores ganadores (más de uno = victoria compartida).
 * @property reason motivo del desenlace.
 * @property finalPieceCounts piezas en el tablero por color al terminar (retirados = 0).
 * @property cut causa del corte por estancamiento si la partida no terminó de forma natural; `null`
 *   si terminó al quedar 2 (o por inmovilización total). Ver [MpCutReason] y §2.6 del plan.
 */
@Serializable
@Immutable
data class MpResult(
    val winners: List<PlayerColor>,
    val reason: MpEndReason,
    val finalPieceCounts: Map<PlayerColor, Int>,
    val cut: MpCutReason? = null,
)
