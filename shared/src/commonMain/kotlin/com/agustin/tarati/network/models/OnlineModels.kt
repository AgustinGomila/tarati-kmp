package com.agustin.tarati.network.models


import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.network.protocol.PlayerInfo
import com.agustin.tarati.network.protocol.RatingUpdate
import com.agustin.tarati.network.protocol.TimeControlInfo
import kotlinx.serialization.Serializable

/**
 * Estado de una partida online en el cliente
 *
 * Este modelo representa el estado completo de una partida online
 * desde la perspectiva del cliente.
 *
 * @property gameId ID único de la partida
 * @property opponentInfo Información del oponente
 * @property yourColor Color del jugador ("white" o "black")
 * @property gameState Estado actual del juego (null si no ha iniciado)
 * @property lastMove Último movimiento recibido del servidor. Null en el estado inicial
 *   (GameStarted) y después de GameEnded.
 * @property status Estado de la partida
 * @property opponentConnected Si el oponente está conectado
 * @property spectatorCount Número de espectadores observando
 * @property timeControl Configuración de tiempo
 * @property isRated Si la partida afecta el rating
 * @property whiteTimeMs Tiempo restante de blancas en milisegundos. Se siembra con el tiempo
 *   inicial del control de tiempo al arrancar la partida (GameStarted) y luego se actualiza con
 *   cada [GameStateUpdate] del servidor. Null solo en partidas sin control de tiempo.
 * @property blackTimeMs Tiempo restante de negras en milisegundos. Misma semántica que [whiteTimeMs].
 * @property lastTimeUpdateMs Epoch ms del cliente en que se recibió el último update de tiempos.
 *   Usado para interpolar localmente sin esperar el siguiente mensaje del servidor.
 */
@Serializable
data class OnlineGame(
    val gameId: String,
    val opponentInfo: PlayerInfo,
    val yourColor: String,
    val gameState: GameState? = null,
    val lastMove: Move? = null,
    val status: OnlineGameStatus = OnlineGameStatus.Starting,
    val opponentConnected: Boolean = true,
    /** Epoch ms en el cliente cuando se recibió OpponentDisconnected. Null cuando el oponente está conectado. */
    val opponentDisconnectedAtMs: Long? = null,
    /** Segundos de gracia concedidos por el servidor antes de adjudicar victoria por abandono. */
    val gracePeriodSec: Int = 60,
    val spectatorCount: Int = 0,
    val timeControl: TimeControlInfo,
    val isRated: Boolean = true,
    val whiteTimeMs: Long? = null,
    val blackTimeMs: Long? = null,
    val lastTimeUpdateMs: Long = 0L,
    /** ID del torneo al que pertenece esta partida. Null para partidas regulares (matchmaking o challenge). */
    val tournamentId: String? = null,
    /** Nombre visible del torneo. Null para partidas regulares. */
    val tournamentName: String? = null,
    /** Número de ronda actual dentro del torneo. Null para partidas regulares. */
    val tournamentRound: Int? = null,
    /** Total de rondas del torneo. Null para partidas regulares. */
    val tournamentTotalRounds: Int? = null,
)

/**
 * Estados posibles de una partida online
 */
@Serializable
sealed class OnlineGameStatus {
    /**
     * Partida encontrada, esperando inicio
     */
    @Serializable
    data object Starting : OnlineGameStatus()

    /**
     * Partida en progreso
     */
    @Serializable
    data object InProgress : OnlineGameStatus()

    /**
     * Partida finalizada
     *
     * @property result Resultado ("white_wins", "black_wins", "draw")
     * @property reason Razón del fin de la partida
     * @property ratingUpdate Cambio de rating (null si casual)
     */
    @Serializable
    data class Finished(
        val result: String,
        val reason: String,
        val ratingUpdate: RatingUpdate?
    ) : OnlineGameStatus()
}

/**
 * Información de una sala de juego
 *
 * @property roomId ID único de la sala
 * @property name Nombre de la sala
 * @property type Tipo de sala
 * @property playerCount Número de jugadores en la sala
 * @property maxPlayers Máximo de jugadores permitidos
 * @property spectatorCount Número de espectadores
 * @property isPrivate Si la sala requiere contraseña
 * @property timeControl Configuración de tiempo
 * @property isRated Si las partidas en esta sala son rated
 * @property createdBy ID del creador de la sala
 */
@Serializable
data class RoomInfo(
    private val roomId: String,
    val name: String,
    val type: RoomType,
    private val playerCount: Int,
    val maxPlayers: Int,
    private val spectatorCount: Int = 0,
    private val isPrivate: Boolean = false,
    val timeControl: TimeControlInfo,
    val isRated: Boolean = true,
    private val createdBy: String
)

/**
 * Tipos de sala
 */
@Serializable
enum class RoomType {
    /**
     * Sala pública, cualquiera puede unirse
     */
    PUBLIC,

    /**
     * Sala privada, requiere contraseña
     */
    PRIVATE,

    /**
     * Sala de torneo
     */
    TOURNAMENT,

    /**
     * Sala de exhibición
     */
    EXHIBITION
}

/**
 * Ticket de matchmaking
 *
 * Representa la posición de un jugador en la cola de búsqueda
 *
 * @property ticketId ID único del ticket
 * @property timeControl Tipo de control de tiempo buscado
 * @property rated Si busca partida rated
 * @property estimatedWaitTime Tiempo estimado de espera en segundos
 * @property joinedAt Timestamp cuando se unió a la cola
 */
@Serializable
data class MatchmakingTicket(
    val ticketId: String,
    val timeControl: String,
    val rated: Boolean,
    val estimatedWaitTime: Int,
    val joinedAt: Long
)

/**
 * Estado del sistema de matchmaking
 */
@Serializable
sealed class MatchmakingState {
    /**
     * No está buscando partida
     */
    @Serializable
    data object Idle : MatchmakingState()

    /**
     * Buscando partida
     *
     * @property ticket Información del ticket de búsqueda
     */
    @Serializable
    data class Searching(
        val ticket: MatchmakingTicket
    ) : MatchmakingState()

    /**
     * Partida encontrada, esperando inicio
     *
     * @property game Información de la partida
     */
    @Serializable
    data class MatchFound(
        val game: OnlineGame
    ) : MatchmakingState()
}

/**
 * Información de un espectador
 *
 * @property userId ID del espectador
 * @property username Nombre del espectador
 * @property joinedAt Timestamp cuando se unió
 */
@Serializable
data class SpectatorInfo(
    val userId: String,
    val username: String,
    private val joinedAt: Long
)


/**
 * Estadísticas de un jugador
 *
 * @property userId ID del jugador
 * @property username Nombre del jugador
 * @property rating Rating actual
 * @property gamesPlayed Total de partidas jugadas
 * @property wins Victorias
 * @property losses Derrotas
 * @property draws Empates
 * @property winRate Porcentaje de victorias (0.0 a 1.0)
 */
@Serializable
data class PlayerStats(
    val userId: String,
    val username: String,
    val rating: Int,
    private val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    private val winRate: Float
)