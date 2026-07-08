package com.agustin.tarati.features.online.game


import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.TournamentStandingDto
import com.agustin.tarati.network.protocol.PlayerInfo
import com.agustin.tarati.network.protocol.TimeControlInfo
import com.agustin.tarati.network.protocol.TimeRemaining
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato público del ViewModel de juego online
 *
 * Este ViewModel actúa como **puente** entre:
 * - [OnlineGameClient] (comunicación con servidor)
 * - [IGameModel] (gestión de estado local del juego)
 * - UI (GameScreen)
 *
 * ## Responsabilidades principales:
 *
 * ### 1. Orquestar flujo de matchmaking:
 * ```kotlin
 * viewModel.startMatchmaking("blitz", rated = true)
 * // Estado: Idle → Searching → MatchFound → GameStarted
 * ```
 *
 * ### 2. Sincronizar estado online ↔ local:
 * ```kotlin
 * // Servidor envía GameState actualizado
 * // → OnlineGameViewModel sincroniza con GameViewModel local
 * // → UI se actualiza reactivamente
 * ```
 *
 * ### 3. Gestionar movimientos online:
 * ```kotlin
 * viewModel.makeOnlineMove(move)
 * // → Envía al servidor
 * // → Servidor valida
 * // → Servidor envía GameStateUpdate
 * // → Sincroniza con GameViewModel local
 * ```
 *
 * ### 4. Manejo de fin de partida:
 * ```kotlin
 * // Servidor: GameEnded(result, reason, newRatings)
 * // → Actualiza UI con resultado
 * // → Muestra cambio de rating
 * // → Limpia estado de partida
 * ```
 */
interface IOnlineGameViewModel {

    /**
     * Partida online actual (null si no hay partida activa)
     *
     * Contiene información del oponente, color del jugador, estado del juego, etc.
     */
    val currentGame: StateFlow<OnlineGame?>

    /**
     * Estado del sistema de matchmaking
     *
     * - [MatchmakingState.Idle] - No buscando
     * - [MatchmakingState.Searching] - Buscando oponente
     * - [MatchmakingState.MatchFound] - Oponente encontrado
     */
    val matchmakingState: StateFlow<MatchmakingState>

    /**
     * Emits the userId who offered a draw to this player. Null when no pending offer.
     * Distinct from [pendingDrawSent]: this tracks offers RECEIVED, not sent.
     */
    val drawOffer: StateFlow<String?>

    /**
     * True mientras el jugador local tiene una oferta de tablas enviada al oponente
     * sin respuesta aún. Se limpia al recibir [DrawOfferEvent.Declined], al terminar
     * la partida, o al recibir una oferta del oponente (que la supera).
     */
    val pendingDrawSent: StateFlow<Boolean>

    /**
     * Eventos transitorios del sistema de tablas:
     * - [DrawOfferEvent.Declined]: el oponente rechazó nuestra oferta.
     */
    val drawOfferEvents: SharedFlow<DrawOfferEvent>

    /**
     * Si hay una partida online activa
     */
    val hasActiveOnlineGame: Boolean
        get() = currentGame.value != null

    /**
     * Si está buscando oponente activamente
     */
    val isSearchingMatch: Boolean
        get() = matchmakingState.value is MatchmakingState.Searching

    /**
     * Iniciar búsqueda de partida online
     *
     * Entra al sistema de matchmaking y busca un oponente con rating similar.
     *
     * @param timeControl Control de tiempo ("bullet", "blitz", "rapid", "classical")
     * @param rated Si la partida debe afectar el rating
     * @return Result.success con ticketId si se inició búsqueda correctamente
     */
    suspend fun startMatchmaking(
        timeControl: String,
        rated: Boolean = true,
        spectatingAllowed: Boolean = true,
    ): Result<String>

    /**
     * Cancelar búsqueda de partida
     *
     * Sale del matchmaking y vuelve al estado Idle.
     * Solo funciona si está en estado Searching.
     */
    suspend fun cancelMatchmaking()

    /**
     * Acepta directamente la búsqueda abierta de [targetUserId].
     *
     * A diferencia de [startMatchmaking], no pasa por el queue general ni espera
     * [MatchmakingState.Searching] — el servidor crea la partida de inmediato.
     * Ambos jugadores recibirán [MatchmakingState.MatchFound] directamente.
     *
     * Si la búsqueda ya no existe, el servidor envía un error que se propaga
     * al flujo de errores estándar del cliente.
     *
     * @return Result.success si el mensaje fue enviado al servidor.
     *         Result.failure si no hay conexión WS activa.
     */
    suspend fun joinOpenSearch(targetUserId: String, timeControl: String, rated: Boolean): Result<Unit>

    /**
     * Realizar un movimiento en la partida online.
     *
     * Envía el movimiento al servidor, que lo valida y propaga a ambos jugadores.
     * Si no hay partida activa, la llamada se ignora silenciosamente.
     *
     * @param move Movimiento a realizar.
     */
    suspend fun makeOnlineMove(move: Move)

    /**
     * Rendirse en la partida online
     *
     * Termina la partida inmediatamente con derrota.
     * Solo funciona si hay partida activa.
     */
    suspend fun resign()

    /**
     * Ofrecer tablas al oponente
     *
     * El oponente puede aceptar o rechazar la oferta.
     */
    suspend fun offerDraw()

    /**
     * Responder a una oferta de tablas
     *
     * @param accept true para aceptar, false para rechazar
     */
    suspend fun respondToDraw(accept: Boolean)

    /**
     * Sincronizar estado online con el GameViewModel local
     *
     * Llamado automáticamente cuando se reciben actualizaciones del servidor.
     * También puede ser llamado manualmente si es necesario forzar sincronización.
     *
     * @param onlineState Estado del juego desde el servidor
     */
    fun syncOnlineStateToLocal(onlineState: GameState)

    /**
     * Limpia el estado de la partida identificada por [gameId].
     * Si [gameId] no coincide con la partida actual, es no-op.
     * Usar [clearOnlineGameUnconditionally] solo al cerrar la app.
     */
    fun clearOnlineGame(gameId: String)

    // ── Spectating ────────────────────────────────────────────────────────────

    /** Estado de la partida que se está observando como espectador. Null cuando no se espectea. */
    val spectatingState: StateFlow<SpectatingState?>

    /**
     * Emite WS SpectateGame y espera la respuesta del servidor.
     * @return true si el servidor confirmó el espectado; false si rechazó (partida no encontrada, ya terminada, etc.)
     */
    suspend fun spectateGame(gameId: String): Boolean

    /** Emite WS LeaveSpectating y limpia [spectatingState]. */
    suspend fun stopSpectating()

    /** Emite un evento cuando la partida espectada termina (antes de limpiar [spectatingState]). */
    val spectatingGameEnded: SharedFlow<SpectatingGameEndedEvent>

    /**
     * Limpia [spectatingState] localmente sin enviar WS LeaveSpectating.
     * Llamar desde el handler de [spectatingGameEnded] después de animar el último movimiento,
     * para no cancelar [LaunchedEffect]s de espectado antes de que terminen de ejecutarse.
     */
    fun clearSpectatingAfterGameEnded()

    // ── Rematch ───────────────────────────────────────────────────────────────

    /**
     * UserId del oponente que ofreció una revancha a este jugador.
     * Null cuando no hay oferta pendiente de respuesta.
     */
    val rematchOffer: StateFlow<String?>

    /**
     * Eventos transitorios del sistema de revanchas:
     * - [RematchEvent.Declined]: el oponente rechazó nuestra oferta.
     * - [RematchEvent.Expired]: nuestra oferta expiró por timeout (30s).
     */
    val rematchEvents: SharedFlow<RematchEvent>

    /**
     * Errores del servidor: movimiento inválido o error genérico.
     * Emite [ServerErrorEvent.InvalidMove] cuando el servidor rechaza un movimiento
     * y [ServerErrorEvent.GenericError] para cualquier otro error.
     */
    val serverErrors: SharedFlow<ServerErrorEvent>

    /**
     * Ofrece una revancha al oponente con las mismas condiciones y colores invertidos.
     * Solo válido cuando la partida actual terminó.
     *
     * @param gameId ID de la partida terminada, capturado en el momento en que se
     *   construye el toast de revancha, no en el momento del click.
     */
    suspend fun offerRematch(gameId: String)

    /**
     * Acepta la oferta de revancha del oponente.
     * Solo válido cuando [rematchOffer] no es null.
     */
    suspend fun acceptRematch()

    /**
     * Rechaza la oferta de revancha del oponente.
     * Solo válido cuando [rematchOffer] no es null.
     */
    suspend fun declineRematch()

    // ── Challenge ─────────────────────────────────────────────────────────────

    /**
     * Eventos de desafío directo:
     * - [ChallengeEvent.Received]: alguien nos desafía
     * - [ChallengeEvent.Declined]: el desafiado rechazó
     * - [ChallengeEvent.Expired]: el desafío expiró sin respuesta (30s)
     */
    val challengeEvents: SharedFlow<ChallengeEvent>

    /** Envía un desafío directo a otro jugador online. */
    suspend fun sendChallenge(targetUserId: String, timeControl: String, rated: Boolean)

    /** Responde al desafío recibido. */
    suspend fun respondToChallenge(challengeId: String, accept: Boolean)

    /** Cancela un desafío previamente enviado. */
    suspend fun cancelChallenge(challengeId: String)

    // ── Torneos ───────────────────────────────────────────────────────────────

    /**
     * Eventos del sistema de torneos recibidos por WebSocket.
     * La UI los usa para mostrar notificaciones y actualizar pantallas de torneo.
     */
    val tournamentEvents: SharedFlow<TournamentEvent>
}

// ── Server error types ───────────────────────────────────────────────────────

/** Errores del servidor propagados a la UI como notificaciones. */
sealed class ServerErrorEvent {
    /** El servidor rechazó el movimiento (ilegal o fuera de turno). */
    data class InvalidMove(val reason: String) : ServerErrorEvent()

    /** El servidor reportó un error genérico. */
    data class GenericError(val code: String, val message: String) : ServerErrorEvent()
}

// ── Draw offer types ──────────────────────────────────────────────────────────

/** Eventos transitorios emitidos por el sistema de tablas. */
sealed class DrawOfferEvent {
    /** El oponente rechazó nuestra oferta de tablas. */
    object Declined : DrawOfferEvent()
}

// ── Rematch types ─────────────────────────────────────────────────────────────

/** Eventos transitorios emitidos por el sistema de revancha. */
sealed class RematchEvent {
    /** El oponente rechazó nuestra oferta de revancha. */
    object Declined : RematchEvent()

    /** Nuestra oferta de revancha expiró sin respuesta (30s). */
    object Expired : RematchEvent()
}

// ── Spectating types ──────────────────────────────────────────────────────────

/** Datos del fin de una partida observada como espectador. */
data class SpectatingGameEndedEvent(
    val result: String,          // "white_wins" | "black_wins" | "draw"
    val reason: String,          // "resignation" | "timeout" | "agreement" | "mit" | ...
    val whiteUsername: String,
    val blackUsername: String,
    val isRated: Boolean,
    /**
     * Último movimiento jugado en la partida. Null en resignaciones (no hay movimiento final).
     * El colector lo usa para animarlo antes de mostrar el toast de resultado.
     */
    val lastMove: Move? = null,
    /** Estado final del tablero. Permite verificar si la animación ya fue aplicada. */
    val finalGameState: GameState? = null,
)

// ── Challenge types ───────────────────────────────────────────────────────────

/** Eventos transitorios emitidos por el sistema de desafíos directos. */
sealed class ChallengeEvent {
    /** Otro jugador nos desafía. */
    data class Received(
        val challengeId: String,
        val challengerInfo: PlayerInfo,
        val timeControl: String,
        val rated: Boolean,
    ) : ChallengeEvent()

    /** El jugador desafiado rechazó nuestra oferta. */
    data class Declined(val challengeId: String) : ChallengeEvent()

    /** El desafío expiró sin respuesta (30s) o fue cancelado. */
    data class Expired(val challengeId: String) : ChallengeEvent()
}

// ── Tournament types ──────────────────────────────────────────────────────────

/** Eventos del sistema de torneos propagados desde el servidor vía WebSocket. */
sealed class TournamentEvent {
    /**
     * El servidor asignó una partida de torneo a este jugador.
     * Llega justo antes de [ServerMessage.MatchFound] para el mismo [gameId],
     * de modo que el cliente puede mostrar el contexto del torneo antes de navegar al tablero.
     */
    data class GameAssigned(
        val tournamentId: String,
        val tournamentName: String,
        val gameId: String,
        val round: Int,
        val totalRounds: Int,
    ) : TournamentEvent()

    /** Una nueva ronda del torneo ha comenzado. */
    data class RoundStarted(
        val tournamentId: String,
        val round: Int,
        val totalRounds: Int,
    ) : TournamentEvent()

    /** Los standings del torneo se actualizaron tras finalizar una partida. */
    data class StandingsUpdated(
        val tournamentId: String,
        val standings: List<TournamentStandingDto>,
    ) : TournamentEvent()

    /** El torneo ha finalizado. Incluye la clasificación final. */
    data class Finished(
        val tournamentId: String,
        val finalStandings: List<TournamentStandingDto>,
    ) : TournamentEvent()

    /** El torneo fue cancelado por su creador. */
    data class Cancelled(val tournamentId: String) : TournamentEvent()
}

@Immutable
data class SpectatingState(
    val gameId: String,
    val whitePlayer: PlayerInfo,
    val blackPlayer: PlayerInfo,
    val currentGameState: GameState,
    val timeRemaining: TimeRemaining,
    val spectatorCount: Int,
    /** Etiqueta del time control: "3+2 Blitz", "10+5 Rapid", etc. */
    val timeControlLabel: String = "",
    val isRated: Boolean = false,
    /** Último movimiento recibido del servidor. Null en el snapshot inicial (SpectatingStarted). */
    val lastMove: Move? = null,
    /**
     * Historial completo de movimientos de la partida hasta el momento de unirse.
     * Solo presente en el snapshot inicial (SpectatingStarted); los estados posteriores
     * llegan como actualizaciones incrementales via [lastMove].
     */
    val moveHistory: List<Move> = emptyList(),
    /** Configuración de tiempo de la partida. Necesario para inicializar el reloj local. */
    val timeControl: TimeControlInfo = TimeControlInfo(0, 0, ""),
)
