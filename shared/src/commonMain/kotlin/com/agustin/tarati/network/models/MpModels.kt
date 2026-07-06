package com.agustin.tarati.network.models

import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import kotlinx.serialization.Serializable

/**
 * DTOs del lobby del juego multijugador (mesas 2–6, tablero `25`) — M6 del plan.
 *
 * Las mesas son **efímeras** (viven en memoria del servidor mientras están abiertas). La asignación
 * definitiva de colores (`A..F`) ocurre al iniciar la partida, cuando los asientos vacíos se
 * descartan; en el lobby el asiento se identifica solo por su [MpSeatDto.index].
 */

/** Estado de una mesa en el lobby. */
@Serializable
enum class MpTableStatus {
    /** Aceptando jugadores; visible en la lista pública. */
    OPEN,

    /** El host la inició; ya no admite cambios (transición a partida — M6 sub-paso 2). */
    STARTING,
}

/** Un asiento de la mesa. Ocupante humano ([occupantId] no nulo) o bot ([isBot]) o vacío. */
@Serializable
data class MpSeatDto(
    val index: Int,
    val occupantId: String? = null,
    val occupantName: String? = null,
    val isBot: Boolean = false,
)

/**
 * Una mesa del lobby multijugador.
 *
 * @property id identificador único de la mesa.
 * @property hostId usuario que la creó y controla (agrega bots / inicia).
 * @property playerCount cantidad de asientos configurada (2–6).
 * @property status ver [MpTableStatus].
 * @property seats asientos en orden; `seats.size == playerCount`.
 */
@Serializable
data class MpTableDto(
    val id: String,
    val hostId: String,
    val playerCount: Int,
    val status: MpTableStatus,
    val seats: List<MpSeatDto>,
)

/**
 * Ocupante de un color (`A..F`) en una partida ya iniciada. Un asiento vacío de la mesa no llega a
 * ser jugador (se descarta al iniciar), así que aquí siempre hay ocupante: humano ([userId] no nulo)
 * o bot.
 */
@Serializable
data class MpPlayerDto(
    val color: PlayerColor,
    val userId: String? = null,
    val name: String,
    val isBot: Boolean = false,
)

/**
 * Una partida multijugador **en curso**, para la pestaña "En Vivo" del lobby (M7.5). A diferencia de
 * las mesas ([MpTableDto], que solo listan mientras están abiertas), estas son partidas ya iniciadas
 * que se pueden **observar** (espectador).
 *
 * @property players ocupantes por color (`A..F`), humanos y bots, en orden de turno.
 * @property currentTurn color al que le toca mover.
 * @property positionNotation FEN de `game6` del estado actual → permite renderizar una **miniatura**.
 * @property startedAtMs epoch millis de inicio (para ordenar / mostrar antigüedad).
 */
@Serializable
data class MpLiveGameDto(
    val gameId: String,
    val players: List<MpPlayerDto>,
    val playerCount: Int,
    val moveCount: Int,
    val currentTurn: PlayerColor,
    val positionNotation: String = "",
    val startedAtMs: Long = 0,
)

/**
 * Estado agregado (cliente) de una partida MP **online** en curso — lo que la UI observa. Combina el
 * `MpGameState` del servidor con el mapeo de jugadores y el último movimiento/conversiones (para
 * animar). No viaja por el cable (lo arma el cliente a partir de `MpServerMessage`).
 *
 * @property lastMoveFrom / lastMoveTo vértices del último movimiento (null si el cambio fue un retiro).
 * @property converted vértices que la última jugada volteó (para animar la captura).
 */
@Immutable
data class MpOnlineGame(
    val gameId: String,
    val state: MpGameState,
    val players: List<MpPlayerDto>,
    val lastMoveFrom: String? = null,
    val lastMoveTo: String? = null,
    /** Vértice volteado → dueño previo (para animar el flip de la captura). */
    val converted: Map<String, PlayerColor> = emptyMap(),
    /** Tiempo por turno para el countdown del cliente (`0` = sin timer). */
    val turnTimeoutMs: Long = 0,
    /** Historial de jugadas acumulado en el cliente (para la lista de movimientos del sidebar). */
    val history: List<PlayerMove> = emptyList(),
    /** `true` si soy **espectador** (partida read-only, sin poder jugar). */
    val spectating: Boolean = false,
)
