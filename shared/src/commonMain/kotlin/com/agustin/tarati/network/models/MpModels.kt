package com.agustin.tarati.network.models

import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpResult
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

/**
 * Política de arranque de una mesa (elegida al crearla).
 *
 * - [HOST_MANUAL]: el host inicia la partida cuando quiere (≥2 ocupados). Comportamiento clásico.
 * - [VOTE]: los ocupantes humanos marcan "listo"; la partida arranca sola cuando **todos** están
 *   listos (con ≥2 ocupados). El host conserva el inicio manual como escape ante un ausente.
 */
@Serializable
enum class MpStartPolicy {
    HOST_MANUAL,
    VOTE,
}

/**
 * Un asiento de la mesa. Ocupante humano ([occupantId] no nulo) o bot ([isBot]) o vacío.
 *
 * @property ready en mesas [MpStartPolicy.VOTE], si el ocupante humano marcó "listo". Los bots y
 *   los asientos vacíos no participan del quórum de arranque.
 */
@Serializable
data class MpSeatDto(
    val index: Int,
    val occupantId: String? = null,
    val occupantName: String? = null,
    val isBot: Boolean = false,
    val ready: Boolean = false,
)

/**
 * Una mesa del lobby multijugador.
 *
 * @property id identificador único de la mesa.
 * @property hostId usuario que la controla (agrega bots / invita / inicia). Puede **migrar** a otro
 *   ocupante si el host original abandona (los controles de host se derivan de este campo, no del índice).
 * @property playerCount cantidad de asientos configurada (2–6).
 * @property status ver [MpTableStatus].
 * @property startPolicy ver [MpStartPolicy].
 * @property seats asientos en orden; `seats.size == playerCount`.
 */
@Serializable
data class MpTableDto(
    val id: String,
    val hostId: String,
    val playerCount: Int,
    val status: MpTableStatus,
    val seats: List<MpSeatDto>,
    val startPolicy: MpStartPolicy = MpStartPolicy.HOST_MANUAL,
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
 * Una partida multijugador **terminada**, para la pestaña "Mis Partidas" del lobby MP. La lista se
 * filtra por participación del usuario autenticado (tabla puente `mp_game_participants`).
 *
 * El resultado propio (victoria / derrota / victoria compartida) lo computa el cliente cruzando su
 * `userId` con [players] (para hallar su color) y [MpResult.winners].
 *
 * @property players ocupantes por color (humanos y bots), como quedaron al terminar.
 * @property result resultado final (ganadores, motivo, conteos, corte).
 * @property endedAtMs epoch millis de fin (para ordenar / mostrar fecha).
 */
@Serializable
data class MpGameHistoryDto(
    val gameId: String,
    val playerCount: Int,
    val players: List<MpPlayerDto>,
    val result: MpResult,
    val moveCount: Int,
    val endedAtMs: Long,
)

/**
 * Detalle completo de una partida multijugador **terminada**, para el visor de replay (`GET
 * /api/mp/games/:id`). A diferencia de [MpGameHistoryDto] (fila de lista), incluye [history] — las
 * jugadas serializadas (`a:D1-C1,b:...`) — para reconstruir jugada a jugada cada estado del tablero
 * con `MpSetup.initialState(playerCount)` + `MpNotation.parseHistory(history)`.
 *
 * Los retiros por timeout/desconexión no están en [history] (el servidor solo serializa jugadas); las
 * eliminaciones naturales sí se reproducen al reaplicar. El [result] final siempre es fiel.
 *
 * @property history jugadas serializadas separadas por comas (vacío si no hubo jugadas).
 * @property startedAtMs / endedAtMs epoch millis (inicio / fin).
 */
@Serializable
data class MpGameDetailDto(
    val gameId: String,
    val playerCount: Int,
    val players: List<MpPlayerDto>,
    val result: MpResult,
    val history: String,
    val moveCount: Int,
    val startedAtMs: Long,
    val endedAtMs: Long,
)

/**
 * Una entrada del feed social MP: una partida terminada en la que participó un jugador **seguido**
 * por el usuario autenticado, mostrada desde la perspectiva de ese jugador (el "sujeto").
 *
 * @property game datos de la partida.
 * @property subjectUserId jugador seguido cuya perspectiva se muestra (uno de [MpGameHistoryDto.players]).
 * @property subjectName nombre visible del jugador seguido.
 */
@Serializable
data class MpFeedGameDto(
    val game: MpGameHistoryDto,
    val subjectUserId: String,
    val subjectName: String,
)

/**
 * Una entrada de la tabla de clasificación multijugador (Tarati Six).
 * Respuesta del endpoint GET /api/mp/leaderboard. Bucket único (sin time control): un solo rating MP.
 * MP no tiene empates → [shared] (victorias compartidas) en lugar de draws.
 */
@Immutable
@Serializable
data class MpLeaderboardEntryDto(
    val rank: Int,
    val id: String,
    val username: String,
    val displayName: String?,
    val country: String?,
    val avatarUrl: String?,
    val rating: Int,
    val games: Int,
    val wins: Int,
    val shared: Int,
    val losses: Int,
    /** True si el jugador es supporter — habilita badge + color de nombre (C4). */
    val isSupporter: Boolean = false,
)

/**
 * Estado de un torneo MP (Arena) para la lista y el detalle del lobby.
 *
 * MP solo soporta el formato **Arena**: ventana de tiempo fija durante la cual se agrupan los
 * participantes ociosos en mesas de [tableSize] jugadores; al terminar cada mesa se puntúa y se
 * reagrupa. No hay rondas ni brackets.
 *
 * @property tableSize jugadores por mesa (2–6); todas las mesas del torneo son de este tamaño.
 * @property durationMinutes duración de la ventana Arena.
 * @property participantCount inscritos actuales.
 * @property startsAtMs / endsAtMs epoch millis (null mientras REGISTERING).
 */
@Immutable
@Serializable
data class MpTournamentDto(
    val id: String,
    val name: String,
    val creatorId: String,
    val creatorName: String,
    val status: TournamentStatus,
    val tableSize: Int,
    val turnTimeoutMs: Long,
    val minPlayers: Int,
    val maxPlayers: Int,
    val durationMinutes: Int,
    val spectatingAllowed: Boolean,
    val participantCount: Int,
    val standings: List<MpTournamentStandingDto> = emptyList(),
    val startsAtMs: Long? = null,
    val endsAtMs: Long? = null,
)

/**
 * Request para crear un torneo MP (Arena). El emisor queda como creador e inscrito.
 *
 * @property tableSize jugadores por mesa (2–6).
 * @property turnTimeoutMs tiempo por turno de las mesas del torneo (`0` = sin timer).
 * @property minPlayers mínimo de inscritos para arrancar (≥ [tableSize]).
 * @property maxPlayers tope de inscritos.
 * @property durationMinutes duración de la ventana Arena.
 */
@Serializable
data class CreateMpTournamentRequest(
    val name: String,
    val tableSize: Int = 4,
    val turnTimeoutMs: Long = 60_000L,
    val minPlayers: Int = 4,
    val maxPlayers: Int = 16,
    val durationMinutes: Int = 20,
    val spectatingAllowed: Boolean = true,
)

/**
 * Posición de un jugador en la clasificación de un torneo MP. Puntuación estilo Arena
 * (victoria=2 + bono de racha, victoria compartida=1, derrota=0). MP no tiene empates → [shared]
 * (victorias compartidas) en lugar de draws.
 */
@Immutable
@Serializable
data class MpTournamentStandingDto(
    val rank: Int,
    val userId: String,
    val username: String,
    val score: Int,
    val wins: Int,
    val shared: Int,
    val losses: Int,
    val streak: Int,
    val games: Int,
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
    /** Deltas de rating MP por `userId` al terminar (de `GameEnded`); vacío si casual/en curso. */
    val ratingDeltas: Map<String, Int> = emptyMap(),
)
