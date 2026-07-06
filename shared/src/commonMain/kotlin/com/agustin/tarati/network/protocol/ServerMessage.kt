package com.agustin.tarati.network.protocol


import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.serialization.Serializable

/**
 * Protocolo WebSocket para Tarati Online
 *
 * Define todos los mensajes que se pueden enviar entre cliente y servidor.
 * Usa kotlinx.serialization para serialización cross-platform.
 *
 * Arquitectura:
 * - ClientMessage: mensajes que el cliente envía al servidor
 * - ServerMessage: mensajes que el servidor envía al cliente
 */

/**
 * Mensajes que el cliente puede enviar al servidor
 */
@Serializable
sealed class ClientMessage {

    /**
     * Unirse a la cola de matchmaking
     *
     * @param timeControl "bullet", "blitz", "rapid", "classical"
     * @param rated Si true, la partida afecta el rating
     */
    @Serializable
    data class JoinMatchmaking(
        val timeControl: String,
        val rated: Boolean = true,
        val spectatingAllowed: Boolean = true,
    ) : ClientMessage()

    /**
     * Cancelar búsqueda de partida
     *
     * @param ticketId ID del ticket de matchmaking (devuelto por el servidor)
     */
    @Serializable
    data class CancelMatchmaking(
        val ticketId: String
    ) : ClientMessage()

    /**
     * Realizar un movimiento en una partida activa
     *
     * @param gameId ID de la partida
     * @param move Movimiento a realizar
     */
    @Serializable
    data class MakeMove(
        val gameId: String,
        val move: Move
    ) : ClientMessage()

    /**
     * Unirse a una sala específica
     *
     * @param roomId ID de la sala
     * @param password Contraseña si la sala es privada
     */
    @Serializable
    data class JoinRoom(
        private val roomId: String,
        val password: String? = null
    ) : ClientMessage()

    /**
     * Observar una partida como espectador
     *
     * @param gameId ID de la partida a observar
     */
    @Serializable
    data class SpectateGame(
        val gameId: String
    ) : ClientMessage()

    /**
     * Dejar de observar una partida
     *
     * @param gameId ID de la partida
     */
    @Serializable
    data class LeaveSpectating(
        val gameId: String
    ) : ClientMessage()

    /**
     * Enviar un mensaje de chat en una sala
     *
     * @param roomId ID de la sala
     * @param message Contenido del mensaje
     */
    @Serializable
    data class SendChatMessage(
        private val roomId: String,
        val message: String
    ) : ClientMessage()

    /**
     * Rendirse en la partida actual
     *
     * @param gameId ID de la partida
     */
    @Serializable
    data class Resign(
        val gameId: String
    ) : ClientMessage()

    /**
     * Ofrecer tablas al oponente
     *
     * @param gameId ID de la partida
     */
    @Serializable
    data class OfferDraw(
        val gameId: String
    ) : ClientMessage()

    /**
     * Responder a una oferta de tablas
     *
     * @param gameId ID de la partida
     * @param accept true para aceptar, false para rechazar
     */
    @Serializable
    data class RespondToDraw(
        val gameId: String,
        val accept: Boolean
    ) : ClientMessage()

    /**
     * Solicitar deshacer el último movimiento (takeback)
     * Solo en partidas casuales
     *
     * @param gameId ID de la partida
     */
    @Serializable
    data class RequestTakeback(
        val gameId: String
    ) : ClientMessage()

    /**
     * Responder a solicitud de takeback
     *
     * @param gameId ID de la partida
     * @param accept true para aceptar, false para rechazar
     */
    @Serializable
    data class RespondToTakeback(
        val gameId: String,
        val accept: Boolean
    ) : ClientMessage()

    /**
     * Ofrecer una revancha al oponente (mismas condiciones, colores invertidos).
     * Solo válido cuando la partida ya terminó (status FINISHED).
     *
     * @param gameId ID de la partida terminada
     */
    @Serializable
    data class OfferRematch(
        val gameId: String
    ) : ClientMessage()

    /**
     * Aceptar la oferta de revancha del oponente.
     *
     * @param gameId ID de la partida terminada
     */
    @Serializable
    data class AcceptRematch(
        val gameId: String
    ) : ClientMessage()

    /**
     * Rechazar la oferta de revancha del oponente.
     *
     * @param gameId ID de la partida terminada
     */
    @Serializable
    data class DeclineRematch(
        val gameId: String
    ) : ClientMessage()

    /**
     * Desafiar directamente a un jugador online.
     *
     * @param targetUserId ID del jugador desafiado
     * @param timeControl  Time control de la partida propuesta
     * @param rated        Si la partida será rated
     */
    @Serializable
    data class ChallengeUser(
        val targetUserId: String,
        val timeControl: String,
        val rated: Boolean = true,
    ) : ClientMessage()

    /**
     * Responder a un desafío recibido.
     *
     * @param challengeId ID del desafío
     * @param accept      true = aceptar, false = rechazar
     */
    @Serializable
    data class RespondToChallenge(
        val challengeId: String,
        val accept: Boolean,
    ) : ClientMessage()

    /**
     * Cancelar un desafío enviado (antes de que expire o sea respondido).
     *
     * @param challengeId ID del desafío a cancelar
     */
    @Serializable
    data class CancelChallenge(
        val challengeId: String,
    ) : ClientMessage()

    /**
     * Unirse directamente a la búsqueda abierta de un jugador específico.
     *
     * A diferencia de [JoinMatchmaking] (que entra al queue y espera al mejor rival),
     * este mensaje crea la partida inmediatamente con [targetUserId].
     * El servidor valida que [targetUserId] siga en cola, lo elimina del queue y
     * crea la sesión con [MatchmakingEngine.createDirectMatch].
     *
     * @param targetUserId ID del jugador cuya búsqueda se quiere aceptar.
     * @param timeControl  Time control de la búsqueda (debe coincidir con el del anfitrión).
     * @param rated        Si la partida debe ser rated (debe coincidir con el del anfitrión).
     */
    @Serializable
    data class JoinOpenSearch(
        val targetUserId: String,
        val timeControl: String,
        val rated: Boolean,
    ) : ClientMessage()

    /**
     * Mensaje de heartbeat para mantener la conexión viva
     * El servidor responderá con HeartbeatAck
     */
    @Serializable
    data object Heartbeat : ClientMessage()

    /**
     * Envoltorio del protocolo del juego **multijugador** (mesas 2–6, tablero `25`).
     * Mantiene todo el protocolo MP aislado en [MpClientMessage] sin sumar variantes al de 2 jugadores.
     */
    @Serializable
    data class Mp(val payload: MpClientMessage) : ClientMessage()
}

/**
 * Mensajes que el servidor puede enviar al cliente
 */
@Serializable
sealed class ServerMessage {

    /**
     * Confirmación de que se ha iniciado la búsqueda de partida
     *
     * @param ticketId ID único del ticket de matchmaking
     * @param estimatedWaitTime Tiempo estimado de espera en segundos
     */
    @Serializable
    data class MatchmakingStarted(
        val ticketId: String,
        val estimatedWaitTime: Int
    ) : ServerMessage()

    /**
     * Se encontró un oponente
     *
     * @param gameId ID único de la partida
     * @param opponentInfo Información del oponente
     * @param yourColor Color asignado al jugador ("white" o "black")
     * @param timeControl Configuración de tiempo de la partida
     */
    @Serializable
    data class MatchFound(
        val gameId: String,
        val opponentInfo: PlayerInfo,
        val yourColor: String,
        val timeControl: TimeControlInfo,
        val rated: Boolean = true,
    ) : ServerMessage()

    /**
     * La partida ha iniciado
     * Incluye el estado inicial del tablero
     *
     * @param gameId ID de la partida
     * @param initialState Estado inicial del juego
     */
    @Serializable
    data class GameStarted(
        val gameId: String,
        val initialState: GameState
    ) : ServerMessage()

    /**
     * Actualización del estado del juego después de un movimiento
     *
     * @param gameId ID de la partida
     * @param newState Nuevo estado del juego
     * @param lastMove Último movimiento realizado
     * @param timeLeft Tiempo restante de cada jugador en milisegundos
     */
    @Serializable
    data class GameStateUpdate(
        val gameId: String,
        val newState: GameState,
        val lastMove: Move,
        val timeLeft: TimeRemaining
    ) : ServerMessage()

    /**
     * La partida ha terminado
     *
     * @param gameId ID de la partida
     * @param result Resultado: "white_wins", "black_wins", "draw"
     * @param reason Razón: "mit", "resignation", "timeout", "draw_agreement", "insufficient_material"
     * @param newRatings Actualización de ratings (null si partida casual)
     */
    @Serializable
    data class GameEnded(
        val gameId: String,
        val result: String,
        val reason: String,
        val newRatings: RatingUpdate? = null
    ) : ServerMessage()

    /**
     * El oponente se ha desconectado
     *
     * @param gameId ID de la partida
     * @param gracePeriod Tiempo de gracia en segundos antes de victoria por abandono
     */
    @Serializable
    data class OpponentDisconnected(
        val gameId: String,
        val gracePeriod: Int
    ) : ServerMessage()

    /**
     * El oponente se ha reconectado
     *
     * @param gameId ID de la partida
     */
    @Serializable
    data class OpponentReconnected(
        val gameId: String
    ) : ServerMessage()

    /**
     * El oponente ofrece tablas
     *
     * @param gameId ID de la partida
     * @param offeredBy ID del jugador que ofrece
     */
    @Serializable
    data class DrawOffered(
        val gameId: String,
        val offeredBy: String
    ) : ServerMessage()

    /**
     * El oponente rechazó la oferta de tablas.
     * @param gameId ID de la partida
     */
    @Serializable
    data class DrawDeclined(
        val gameId: String
    ) : ServerMessage()

    /**
     * El oponente solicita deshacer movimiento
     *
     * @param gameId ID de la partida
     * @param requestedBy ID del jugador que solicita
     */
    @Serializable
    data class TakebackRequested(
        val gameId: String,
        val requestedBy: String
    ) : ServerMessage()

    /**
     * Mensaje de chat recibido
     *
     * @param roomId ID de la sala
     * @param senderId ID del remitente
     * @param senderName Nombre del remitente
     * @param message Contenido del mensaje
     * @param timestamp Timestamp en milisegundos desde epoch
     */
    @Serializable
    data class ChatMessage(
        val roomId: String,
        val senderId: String,
        val senderName: String,
        val message: String,
        val timestamp: Long
    ) : ServerMessage()

    /**
     * Confirmación enviada al espectador con el estado completo actual de la partida.
     * Permite mostrar el tablero inmediatamente al unirse mid-game.
     */
    @Serializable
    data class SpectatingStarted(
        val gameId: String,
        val whitePlayer: PlayerInfo,
        val blackPlayer: PlayerInfo,
        val gameState: GameState,
        val timeLeft: TimeRemaining,
        val spectatorCount: Int,
        val timeControlLabel: String = "",
        val isRated: Boolean = false,
        /** Lista completa de movimientos jugados hasta el momento. Permite al espectador
         *  reconstruir el historial de la partida al unirse mid-game. */
        val moveHistory: List<Move> = emptyList(),
        /** Configuración de tiempo de la partida (initial, increment, label). Necesario
         *  para inicializar el reloj local del espectador con el modo correcto. */
        val timeControl: TimeControlInfo = TimeControlInfo(0, 0, ""),
    ) : ServerMessage()

    /**
     * Un espectador se unió a la partida
     *
     * @param gameId ID de la partida
     * @param spectatorCount Número total de espectadores
     */
    @Serializable
    data class SpectatorJoined(
        val gameId: String,
        val spectatorCount: Int
    ) : ServerMessage()

    /**
     * Un espectador dejó la partida
     *
     * @param gameId ID de la partida
     * @param spectatorCount Número total de espectadores restantes
     */
    @Serializable
    data class SpectatorLeft(
        val gameId: String,
        val spectatorCount: Int
    ) : ServerMessage()

    /**
     * El movimiento enviado es inválido
     *
     * @param gameId ID de la partida
     * @param reason Razón por la cual es inválido
     */
    @Serializable
    data class InvalidMove(
        val gameId: String,
        val reason: String
    ) : ServerMessage()

    /**
     * Error general
     *
     * @param code Código de error
     * @param message Mensaje descriptivo
     */
    @Serializable
    data class Error(
        val code: String,
        val message: String
    ) : ServerMessage()

    /**
     * Respuesta al heartbeat
     * Confirma que la conexión está activa
     */
    @Serializable
    data object HeartbeatAck : ServerMessage()

    // ── Rematch ───────────────────────────────────────────────────────────────

    /**
     * El oponente ofrece una revancha (mismas condiciones, colores invertidos).
     * Enviado únicamente al receptor de la oferta.
     *
     * @param gameId ID de la partida terminada
     * @param offeredBy UserId del jugador que ofrece
     */
    @Serializable
    data class RematchOffered(
        val gameId: String,
        val offeredBy: String,
    ) : ServerMessage()

    /**
     * La oferta de revancha fue aceptada. Se crea una nueva partida.
     * Enviado a cada jugador por separado (yourColor refleja su color en la nueva partida).
     *
     * @param oldGameId ID de la partida terminada
     * @param newGameId ID de la nueva partida
     * @param yourColor Color del receptor en la nueva partida ("white" o "black")
     */
    @Serializable
    data class RematchAccepted(
        val oldGameId: String,
        val newGameId: String,
        val yourColor: String,
    ) : ServerMessage()

    /**
     * El oponente rechazó la oferta de revancha.
     * Enviado únicamente al jugador que ofreció.
     *
     * @param gameId ID de la partida terminada
     */
    @Serializable
    data class RematchDeclined(
        val gameId: String,
    ) : ServerMessage()

    /**
     * La oferta de revancha expiró por timeout (30 s sin respuesta).
     * Enviado únicamente al jugador que ofreció.
     *
     * @param gameId ID de la partida terminada
     */
    @Serializable
    data class RematchExpired(
        val gameId: String,
    ) : ServerMessage()

    // ── Challenge ─────────────────────────────────────────────────────────────

    /**
     * Otro jugador te desafía a una partida directa.
     * Enviado al jugador desafiado.
     *
     * @param challengeId   ID único del desafío (para responder o cancelar)
     * @param challengerInfo Información del jugador que desafía
     * @param timeControl   Time control propuesto
     * @param rated         Si la partida sería rated
     */
    @Serializable
    data class ChallengeReceived(
        val challengeId: String,
        val challengerInfo: PlayerInfo,
        val timeControl: String,
        val rated: Boolean,
    ) : ServerMessage()

    /**
     * El jugador desafiado rechazó el desafío.
     * Enviado al jugador que desafió.
     *
     * @param challengeId ID del desafío rechazado
     */
    @Serializable
    data class ChallengeDeclined(
        val challengeId: String,
    ) : ServerMessage()

    /**
     * El desafío expiró (30s sin respuesta) o fue cancelado por el retador.
     * Enviado a ambos jugadores.
     *
     * @param challengeId ID del desafío expirado
     */
    @Serializable
    data class ChallengeExpired(
        val challengeId: String,
    ) : ServerMessage()

    // ── Torneos ───────────────────────────────────────────────────────────────

    /**
     * El servidor asignó al jugador una partida dentro de un torneo.
     * Enviado antes de [MatchFound] para que el cliente muestre el contexto del torneo.
     *
     * @param tournamentId   ID del torneo
     * @param tournamentName Nombre visible del torneo (ej. "Bot Cup 6P Round Robin")
     * @param gameId         ID de la partida asignada (igual al gameId de [MatchFound])
     * @param round          Número de ronda actual
     * @param totalRounds    Total de rondas del torneo
     */
    @Serializable
    data class TournamentGameAssigned(
        val tournamentId: String,
        val tournamentName: String,
        val gameId: String,
        val round: Int,
        val totalRounds: Int,
    ) : ServerMessage()

    /**
     * Una nueva ronda del torneo ha comenzado.
     * Enviado a todos los participantes al iniciar cada ronda.
     */
    @Serializable
    data class TournamentRoundStarted(
        val tournamentId: String,
        val round: Int,
        val totalRounds: Int,
    ) : ServerMessage()

    /**
     * Los standings del torneo se actualizaron (partida terminada).
     * Enviado a todos los participantes al terminar cada partida del torneo.
     */
    @Serializable
    data class TournamentStandingsUpdated(
        val tournamentId: String,
        val standings: List<com.agustin.tarati.network.models.TournamentStandingDto>,
    ) : ServerMessage()

    /**
     * El torneo ha finalizado. Incluye la clasificación final.
     * Enviado a todos los participantes.
     */
    @Serializable
    data class TournamentFinished(
        val tournamentId: String,
        val finalStandings: List<com.agustin.tarati.network.models.TournamentStandingDto>,
    ) : ServerMessage()

    /**
     * El torneo fue cancelado por su creador (solo desde estado REGISTERING).
     * Enviado a todos los participantes inscritos.
     */
    @Serializable
    data class TournamentCancelled(
        val tournamentId: String,
    ) : ServerMessage()

    // ── Multijugador (mesas 2–6, tablero 25) ─────────────────────────────────────

    /**
     * Envoltorio del protocolo del juego **multijugador**. Mantiene todo el protocolo MP aislado en
     * [MpServerMessage] → el cliente 2-jugadores maneja un único caso `is ServerMessage.Mp`.
     */
    @Serializable
    data class Mp(val payload: MpServerMessage) : ServerMessage()
}

/**
 * Información de un jugador
 */
@Serializable
data class PlayerInfo(
    val userId: String,
    val username: String,
    val rating: Int,
    val country: String? = null,
    val title: String? = null  // "GM", "IM", "FM", etc.
)

/**
 * Configuración de control de tiempo
 */
@Serializable
data class TimeControlInfo(
    val initial: Int,       // Tiempo inicial en segundos
    val increment: Int,     // Incremento por movimiento en segundos
    val label: String       // Etiqueta legible: "3+2 Blitz"
)

/**
 * Tiempo restante de cada jugador
 */
@Serializable
data class TimeRemaining(
    val whiteMs: Long,      // Tiempo restante de blancas en milisegundos
    val blackMs: Long       // Tiempo restante de negras en milisegundos
)

/**
 * Actualización de rating después de una partida
 */
@Serializable
data class RatingUpdate(
    val oldRating: Int,
    val newRating: Int,
    val change: Int         // Cambio neto (puede ser negativo)
)
