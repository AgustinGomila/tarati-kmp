package com.agustin.tarati.network.client


import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.online.game.ChallengeEvent
import com.agustin.tarati.features.online.game.ChallengeEvent.Declined
import com.agustin.tarati.features.online.game.ChallengeEvent.Expired
import com.agustin.tarati.features.online.game.ChallengeEvent.Received
import com.agustin.tarati.features.online.game.DrawOfferEvent
import com.agustin.tarati.features.online.game.RematchEvent
import com.agustin.tarati.features.online.game.ServerErrorEvent
import com.agustin.tarati.features.online.game.ServerErrorEvent.GenericError
import com.agustin.tarati.features.online.game.ServerErrorEvent.InvalidMove
import com.agustin.tarati.features.online.game.SpectatingGameEndedEvent
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.features.online.game.TournamentEvent
import com.agustin.tarati.network.client.TaratiWebSocketClient.ConnectionState
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.MatchmakingState.MatchFound
import com.agustin.tarati.network.models.MatchmakingState.Searching
import com.agustin.tarati.network.models.MatchmakingTicket
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.network.models.OnlineGameStatus.Finished
import com.agustin.tarati.network.protocol.ClientMessage
import com.agustin.tarati.network.protocol.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * API de alto nivel para interactuar con el sistema de juego online
 *
 * Abstrae el protocolo WebSocket y proporciona una interfaz simple
 * basada en Flows y suspend functions.
 *
 * Esta clase es la principal interfaz entre la UI y el sistema online.
 * Los ViewModels deben usar esta clase en lugar del WebSocketClient directamente.
 *
 * @param wsClient Cliente WebSocket configurado
 */
class OnlineGameClient(
    private val wsClient: TaratiWebSocketClient,
    /** Scope para procesamiento de mensajes. Inyectable para tests; en producción se crea uno propio. */
    scope: CoroutineScope? = null,
) {
    private val logger = getLogger("OnlineGameClient")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentGame = MutableStateFlow<OnlineGame?>(null)

    private val _spectatingState = MutableStateFlow<SpectatingState?>(null)
    val spectatingState: StateFlow<SpectatingState?> = _spectatingState.asStateFlow()

    private val _spectatingGameEnded = MutableSharedFlow<SpectatingGameEndedEvent>(extraBufferCapacity = 1)
    val spectatingGameEnded: SharedFlow<SpectatingGameEndedEvent> = _spectatingGameEnded.asSharedFlow()

    // Parámetros del último JoinMatchmaking enviado. Se usan para rellenar
    // MatchmakingTicket y OnlineGame cuando llegan MatchmakingStarted / MatchFound,
    // ya que esos mensajes del servidor no incluyen timeControl ni isRated.
    private var pendingMatchmakingTimeControl: String = TimeControl.BLITZ.key
    private var pendingMatchmakingRated: Boolean = true

    // ── Draw offer state ──────────────────────────────────────────────────────

    /** UserId del oponente que nos ofreció tablas. Null cuando no hay oferta pendiente. */
    private val _drawOffer = MutableStateFlow<String?>(null)
    val drawOffer: StateFlow<String?> = _drawOffer

    /**
     * True mientras el jugador local tiene una oferta de tablas enviada al oponente
     * sin respuesta aún. Opuesto a [_drawOffer] que rastrea ofertas RECIBIDAS.
     */
    private val _pendingDrawSent = MutableStateFlow(false)
    val pendingDrawSent: StateFlow<Boolean> = _pendingDrawSent.asStateFlow()

    /** Eventos transitorios de tablas: [DrawOfferEvent.Declined]. */
    private val _drawOfferEvents = MutableSharedFlow<DrawOfferEvent>(extraBufferCapacity = 4)
    val drawOfferEvents: SharedFlow<DrawOfferEvent> = _drawOfferEvents.asSharedFlow()

    // ── Matchmaking / game state ──────────────────────────────────────────────

    private val _matchmakingState = MutableStateFlow<MatchmakingState>(MatchmakingState.Idle)

    // ── Rematch state ─────────────────────────────────────────────────────────

    /** UserId del oponente que nos ofreció una revancha. Null si no hay oferta pendiente. */
    private val _rematchOffer = MutableStateFlow<String?>(null)
    val rematchOffer: StateFlow<String?> = _rematchOffer.asStateFlow()

    /** Eventos transitorios: [RematchEvent.Declined] y [RematchEvent.Expired]. */
    private val _rematchEvents = MutableSharedFlow<RematchEvent>(extraBufferCapacity = 4)
    val rematchEvents: SharedFlow<RematchEvent> = _rematchEvents.asSharedFlow()

    // ── Server errors ─────────────────────────────────────────────────────────

    /** Errores del servidor: movimiento inválido y errores genéricos. */
    private val _serverErrors = MutableSharedFlow<ServerErrorEvent>(extraBufferCapacity = 4)
    val serverErrors: SharedFlow<ServerErrorEvent> = _serverErrors.asSharedFlow()

    // ── Challenge ─────────────────────────────────────────────────────────────

    /** Eventos de desafío directo: recibido, rechazado, expirado. */
    private val _challengeEvents = MutableSharedFlow<ChallengeEvent>(extraBufferCapacity = 4)
    val challengeEvents: SharedFlow<ChallengeEvent> = _challengeEvents.asSharedFlow()

    // ── Tournament ────────────────────────────────────────────────────────────

    /** Contexto de torneo pendiente de asociar al próximo MatchFound con el mismo gameId. */
    private var pendingTournamentContext: PendingTournamentCtx? = null

    private data class PendingTournamentCtx(
        val gameId: String,
        val tournamentId: String,
        val tournamentName: String,
        val round: Int,
        val totalRounds: Int,
    )

    /** Eventos del sistema de torneos: asignación de partida, nueva ronda, standings, cierre. */
    private val _tournamentEvents = MutableSharedFlow<TournamentEvent>(extraBufferCapacity = 8)
    val tournamentEvents: SharedFlow<TournamentEvent> = _tournamentEvents.asSharedFlow()

    /**
     * Estado actual de la partida online (null si no hay partida activa)
     */
    val currentGame: StateFlow<OnlineGame?> = _currentGame.asStateFlow()

    /**
     * Estado del sistema de matchmaking
     */
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    /**
     * Estado de la conexión WebSocket
     */
    val connectionState: StateFlow<ConnectionState> =
        wsClient.connectionState

    init {
        // Suscribirse a mensajes del servidor
        wsClient.messages
            .onEach { message -> handleServerMessage(message) }
            .launchIn(_scope)
    }

    // ============ Matchmaking API ============

    /**
     * Acepta directamente la búsqueda abierta de [targetUserId] sin pasar por el queue general.
     * El servidor responderá con [ServerMessage.MatchFound] si el target sigue en cola,
     * o con [ServerMessage.Error] (code = "search_not_found") si ya se fue.
     */
    suspend fun joinOpenSearch(targetUserId: String, timeControl: String, rated: Boolean) {
        logger.info("Joining open search of $targetUserId ($timeControl, rated=$rated)")
        pendingMatchmakingTimeControl = timeControl
        pendingMatchmakingRated = rated
        wsClient.send(ClientMessage.JoinOpenSearch(targetUserId, timeControl, rated))
    }

    /**
     * Inicia búsqueda de partida
     *
     * @param timeControl "bullet", "blitz", "rapid", "classical"
     * @param rated Si la partida debe ser rated
     */
    suspend fun joinMatchmaking(
        timeControl: String,
        rated: Boolean = true,
        spectatingAllowed: Boolean = true,
    ) {
        logger.info("Joining matchmaking: $timeControl, rated=$rated, spectating=$spectatingAllowed")
        pendingMatchmakingTimeControl = timeControl
        pendingMatchmakingRated = rated
        wsClient.send(ClientMessage.JoinMatchmaking(timeControl, rated, spectatingAllowed))
    }

    /**
     * Cancela la búsqueda de partida actual
     */
    suspend fun cancelMatchmaking() {
        val state = _matchmakingState.value
        if (state is Searching) {
            logger.info("Cancelling matchmaking: ${state.ticket.ticketId}")
            wsClient.send(ClientMessage.CancelMatchmaking(state.ticket.ticketId))
            _matchmakingState.value = MatchmakingState.Idle
        }
    }

    // ============ Gameplay API ============

    /**
     * Realiza un movimiento en la partida actual
     *
     * @param move Movimiento a realizar
     * @throws IllegalStateException si no hay partida activa
     */
    suspend fun makeMove(move: Move) {
        val gameId = _currentGame.value?.gameId
            ?: throw IllegalStateException("No active game")

        logger.info("Making move in game $gameId")
        wsClient.send(ClientMessage.MakeMove(gameId, move))
    }

    /**
     * Rendirse en la partida actual
     *
     * @throws IllegalStateException si no hay partida activa
     */
    suspend fun resign() {
        val gameId = _currentGame.value?.gameId
            ?: throw IllegalStateException("No active game")

        logger.info("Resigning game $gameId")
        wsClient.send(ClientMessage.Resign(gameId))
    }

    /**
     * Ofrecer tablas al oponente
     *
     * @throws IllegalStateException si no hay partida activa
     */
    suspend fun offerDraw() {
        val gameId = _currentGame.value?.gameId
            ?: throw IllegalStateException("No active game")

        logger.info("Offering draw in game $gameId")
        wsClient.send(ClientMessage.OfferDraw(gameId))
        _pendingDrawSent.value = true
    }

    /**
     * Responder a una oferta de tablas
     *
     * @param accept true para aceptar, false para rechazar
     * @throws IllegalStateException si no hay partida activa
     */
    suspend fun respondToDraw(accept: Boolean) {
        val gameId = _currentGame.value?.gameId
            ?: throw IllegalStateException("No active game")

        logger.info("Responding to draw: accept=$accept")
        wsClient.send(ClientMessage.RespondToDraw(gameId, accept))
    }

    // ============ Rematch API ============

    /**
     * Ofrece una revancha al oponente (mismas condiciones, colores invertidos).
     * Solo válido cuando la partida terminó.
     *
     * @param gameId ID de la partida terminada. Se pasa explícitamente para evitar
     *   que un cambio en [_currentGame] (nueva partida iniciada durante la ventana
     *   de revancha) referencie el ID incorrecto.
     */
    suspend fun offerRematch(gameId: String) {
        logger.info("Offering rematch for game $gameId")
        wsClient.send(ClientMessage.OfferRematch(gameId))
    }

    /**
     * Acepta la oferta de revancha del oponente.
     */
    suspend fun acceptRematch() {
        val gameId = _currentGame.value?.gameId ?: return
        _rematchOffer.value = null
        logger.info("Accepting rematch for game $gameId")
        wsClient.send(ClientMessage.AcceptRematch(gameId))
    }

    /**
     * Rechaza la oferta de revancha del oponente.
     */
    suspend fun declineRematch() {
        val gameId = _currentGame.value?.gameId ?: return
        _rematchOffer.value = null
        logger.info("Declining rematch for game $gameId")
        wsClient.send(ClientMessage.DeclineRematch(gameId))
    }

    // ============ Spectating API ============

    // Deferred resuelto cuando el servidor confirma (SpectatingStarted) o rechaza (Error spectate_error)
    private var pendingSpectate: CompletableDeferred<Boolean>? = null

    /**
     * Observar una partida como espectador.
     *
     * @return true si el servidor respondió con SpectatingStarted; false si devolvió error
     *         (partida no encontrada, ya terminada, etc.).
     */
    suspend fun spectateGame(gameId: String): Boolean {
        logger.info("Spectating game $gameId")
        val deferred = CompletableDeferred<Boolean>()
        pendingSpectate = deferred
        return try {
            wsClient.send(ClientMessage.SpectateGame(gameId))
            withTimeoutOrNull(5_000.milliseconds) { deferred.await() } ?: false
        } finally {
            pendingSpectate = null
        }
    }

    /** Limpia [spectatingState] localmente sin enviar WS — usado cuando la partida terminó sola. */
    fun clearSpectatingState() {
        _spectatingState.value = null
    }

    /** Dejar de observar el gameId especificado. Limpia el state localmente de inmediato. */
    suspend fun stopSpectating(gameId: String) {
        logger.info("Leaving spectating for game $gameId")
        if (_spectatingState.value?.gameId == gameId) {
            _spectatingState.value = null
        }
        wsClient.send(ClientMessage.LeaveSpectating(gameId))
    }

    // ============ Message Handling ============

    /**
     * Maneja mensajes recibidos del servidor
     *
     * Actualiza los estados internos según el tipo de mensaje.
     * Esta función es llamada automáticamente cuando se reciben mensajes.
     */
    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            // Matchmaking
            is ServerMessage.MatchmakingStarted -> {
                val ticket = MatchmakingTicket(
                    ticketId = message.ticketId,
                    timeControl = pendingMatchmakingTimeControl,
                    rated = pendingMatchmakingRated,
                    estimatedWaitTime = message.estimatedWaitTime,
                    joinedAt = Clock.System.now().toEpochMilliseconds()
                )
                _matchmakingState.value = Searching(ticket)
                logger.info("Matchmaking started: ${message.ticketId}")
            }

            is ServerMessage.MatchFound -> {
                // Consumir contexto de torneo si TournamentGameAssigned llegó antes para este gameId
                val tournamentCtx = pendingTournamentContext?.takeIf { it.gameId == message.gameId }
                pendingTournamentContext = null
                val game = OnlineGame(
                    gameId = message.gameId,
                    opponentInfo = message.opponentInfo,
                    yourColor = message.yourColor,
                    status = OnlineGameStatus.Starting,
                    timeControl = message.timeControl,
                    isRated = message.rated,
                    tournamentId = tournamentCtx?.tournamentId,
                    tournamentName = tournamentCtx?.tournamentName,
                    tournamentRound = tournamentCtx?.round,
                    tournamentTotalRounds = tournamentCtx?.totalRounds,
                )
                _currentGame.value = game
                _matchmakingState.value = MatchFound(game)
                logger.info(
                    "Match found: ${message.gameId}" +
                            if (tournamentCtx != null) " [tournament=${tournamentCtx.tournamentId} round=${tournamentCtx.round}]" else ""
                )
            }

            // Game lifecycle
            is ServerMessage.GameStarted -> {
                // Verificar que el GameStarted corresponde a la partida actual. Con múltiples
                // desafíos/revanchas en vuelo pueden llegar GameStarted de otro gameId; sin este
                // guard, el copy pisaría la partida en curso con el estado de otra.
                val current = _currentGame.value
                if (current == null || current.gameId != message.gameId) {
                    logger.warn("Ignoring GameStarted for ${message.gameId} — current game is ${current?.gameId}")
                    return
                }
                _currentGame.value = current.copy(
                    gameState = message.initialState,
                    lastMove = null,
                    status = OnlineGameStatus.InProgress,
                )
                logger.info("Game started: ${message.gameId}")
            }

            is ServerMessage.GameStateUpdate -> {
                // Spectating: update spectating state if this gameId matches
                if (_spectatingState.value?.gameId == message.gameId) {
                    val current = _spectatingState.value ?: return
                    _spectatingState.value = current.copy(
                        currentGameState = message.newState,
                        timeRemaining = message.timeLeft,
                        lastMove = message.lastMove,
                        // Acumular el movimiento en el historial para que el fallback de
                        // recarga completa (snap branch en OnlineGameSideEffects) tenga
                        // siempre el historial al día.
                        moveHistory = current.moveHistory + listOfNotNull(message.lastMove),
                    )
                    return
                }
                // Player's own game: ignorar si no es la partida actual o ya terminó
                val current = _currentGame.value ?: return
                if (current.gameId != message.gameId) return
                if (current.status is Finished) return
                _currentGame.value = current.copy(
                    // Un GameStateUpdate implica que la partida está viva: si el GameStarted se
                    // perdió o llegó desfasado por orden de mensajes, promover Starting → InProgress
                    // igualmente. De lo contrario el estado queda pegado en Starting y el gate de
                    // IA local nunca se apaga.
                    status = OnlineGameStatus.InProgress,
                    gameState = message.newState,
                    lastMove = message.lastMove,
                    whiteTimeMs = message.timeLeft.whiteMs,
                    blackTimeMs = message.timeLeft.blackMs,
                    lastTimeUpdateMs = Clock.System.now().toEpochMilliseconds(),
                )
                logger.info("Game state updated: ${message.gameId}")
            }

            is ServerMessage.GameEnded -> {
                // Spectating: emit ended event then clear state
                val watchedGame = _spectatingState.value
                if (watchedGame?.gameId == message.gameId) {
                    _spectatingGameEnded.tryEmit(
                        SpectatingGameEndedEvent(
                            result = message.result,
                            reason = message.reason,
                            whiteUsername = watchedGame.whitePlayer.username,
                            blackUsername = watchedGame.blackPlayer.username,
                            isRated = watchedGame.isRated,
                            lastMove = watchedGame.lastMove,
                            finalGameState = watchedGame.currentGameState,
                        )
                    )
                    // El handler de UI (OnlineGameSideEffects.spectatingGameEnded.collect)
                    // llama a clearSpectatingAfterGameEnded() tras animar el último movimiento.
                    // Este scope.launch es el fallback para cuando tryEmit no llega al colector
                    // (ej. buffer lleno, dispatcher delay en WASM, etc.).
                    val endedGameId = watchedGame.gameId
                    _scope.launch {
                        delay(2000.milliseconds)
                        if (_spectatingState.value?.gameId == endedGameId) {
                            _spectatingState.value = null
                        }
                    }
                    logger.info("Spectated game ended: ${message.result} (${message.reason})")
                    return
                }
                _drawOffer.value = null
                _pendingDrawSent.value = false
                _currentGame.value = _currentGame.value?.copy(
                    status = Finished(
                        result = message.result,
                        reason = message.reason,
                        ratingUpdate = message.newRatings
                    )
                )
                logger.info("Game ended: ${message.result} (${message.reason})")
            }

            // Connection status
            is ServerMessage.OpponentDisconnected -> {
                _currentGame.value = _currentGame.value?.copy(
                    opponentConnected = false,
                    opponentDisconnectedAtMs = Clock.System.now().toEpochMilliseconds(),
                    gracePeriodSec = message.gracePeriod,
                )
                logger.info("Opponent disconnected (grace: ${message.gracePeriod}s)")
            }

            is ServerMessage.OpponentReconnected -> {
                _currentGame.value = _currentGame.value?.copy(
                    opponentConnected = true,
                    opponentDisconnectedAtMs = null,
                )
                logger.info("Opponent reconnected")
            }

            // Draw/Takeback offers
            is ServerMessage.DrawOffered -> {
                // Si el oponente nos ofrece mientras nuestra oferta estaba pendiente,
                // su oferta supera a la nuestra en el servidor — limpiar nuestro estado.
                _pendingDrawSent.value = false
                _drawOffer.value = message.offeredBy
                logger.info("Draw offered by ${message.offeredBy}")
            }

            is ServerMessage.DrawDeclined -> {
                _drawOffer.value = null
                _pendingDrawSent.value = false
                _drawOfferEvents.tryEmit(DrawOfferEvent.Declined)
                logger.info("Draw declined for game ${message.gameId}")
            }

            is ServerMessage.TakebackRequested -> {
                logger.info("Takeback requested by ${message.requestedBy}")
            }

            // Chat (feature no implementada en UI — se recibe pero no se procesa)
            is ServerMessage.ChatMessage -> {
                logger.info("Chat message from ${message.senderName} (unhandled)")
            }

            // Errors
            is ServerMessage.InvalidMove -> {
                logger.warn("Invalid move: ${message.reason}")
                _serverErrors.tryEmit(InvalidMove(message.reason))
            }

            is ServerMessage.Error -> {
                logger.error("Server error: ${message.code} - ${message.message}")
                if (message.code == "spectate_error") {
                    pendingSpectate?.complete(false)
                } else {
                    _serverErrors.tryEmit(GenericError(message.code, message.message))
                }
            }

            // Heartbeat
            is ServerMessage.HeartbeatAck -> {
                // Heartbeat confirmado, conexión activa
            }

            // ── Spectating ──────────────────────────────────────────────────
            is ServerMessage.SpectatingStarted -> {
                pendingSpectate?.complete(true)
                _spectatingState.value = SpectatingState(
                    gameId = message.gameId,
                    whitePlayer = message.whitePlayer,
                    blackPlayer = message.blackPlayer,
                    currentGameState = message.gameState,
                    timeRemaining = message.timeLeft,
                    spectatorCount = message.spectatorCount,
                    timeControlLabel = message.timeControlLabel,
                    isRated = message.isRated,
                    moveHistory = message.moveHistory,
                    timeControl = message.timeControl,
                )
                logger.info("Spectating started: ${message.gameId} (${message.moveHistory.size} moves)")
            }

            is ServerMessage.SpectatorJoined -> {
                if (_currentGame.value?.gameId == message.gameId) {
                    _currentGame.value = _currentGame.value?.copy(spectatorCount = message.spectatorCount)
                }
                if (_spectatingState.value?.gameId == message.gameId) {
                    _spectatingState.value = _spectatingState.value?.copy(spectatorCount = message.spectatorCount)
                }
                logger.info("Spectator joined game ${message.gameId} (total: ${message.spectatorCount})")
            }

            is ServerMessage.SpectatorLeft -> {
                if (_currentGame.value?.gameId == message.gameId) {
                    _currentGame.value = _currentGame.value?.copy(spectatorCount = message.spectatorCount)
                }
                if (_spectatingState.value?.gameId == message.gameId) {
                    _spectatingState.value = _spectatingState.value?.copy(spectatorCount = message.spectatorCount)
                }
                logger.info("Spectator left game ${message.gameId} (total: ${message.spectatorCount})")
            }

            // ── Rematch ─────────────────────────────────────────────────────
            is ServerMessage.RematchOffered -> {
                _rematchOffer.value = message.offeredBy
                logger.info("Rematch offered by ${message.offeredBy} for game ${message.gameId}")
            }

            is ServerMessage.RematchAccepted -> {
                _rematchOffer.value = null
                val current = _currentGame.value
                if (current != null) {
                    _currentGame.value = current.copy(
                        gameId = message.newGameId,
                        yourColor = message.yourColor,
                        gameState = null,
                        lastMove = null,
                        status = OnlineGameStatus.Starting,
                        whiteTimeMs = null,
                        blackTimeMs = null,
                        lastTimeUpdateMs = 0L,
                        opponentConnected = true,
                        spectatorCount = 0,
                    )
                }
                logger.info("Rematch accepted: ${message.oldGameId} → ${message.newGameId} (color=${message.yourColor})")
            }

            is ServerMessage.RematchDeclined -> {
                _rematchOffer.value = null
                _rematchEvents.tryEmit(RematchEvent.Declined)
                logger.info("Rematch declined for game ${message.gameId}")
            }

            is ServerMessage.RematchExpired -> {
                _rematchOffer.value = null
                _rematchEvents.tryEmit(RematchEvent.Expired)
                logger.info("Rematch offer expired for game ${message.gameId}")
            }

            // ── Challenge ────────────────────────────────────────────────────
            is ServerMessage.ChallengeReceived -> {
                _challengeEvents.tryEmit(
                    Received(
                        challengeId = message.challengeId,
                        challengerInfo = message.challengerInfo,
                        timeControl = message.timeControl,
                        rated = message.rated,
                    )
                )
                logger.info("Challenge received: ${message.challengeId} from ${message.challengerInfo.userId}")
            }

            is ServerMessage.ChallengeDeclined -> {
                _challengeEvents.tryEmit(Declined(message.challengeId))
                logger.info("Challenge declined: ${message.challengeId}")
            }

            is ServerMessage.ChallengeExpired -> {
                _challengeEvents.tryEmit(Expired(message.challengeId))
                logger.info("Challenge expired: ${message.challengeId}")
            }

            // ── Torneos ──────────────────────────────────────────────────────
            is ServerMessage.TournamentGameAssigned -> {
                // Almacenar contexto para enriquecer el MatchFound que llegará a continuación
                pendingTournamentContext = PendingTournamentCtx(
                    gameId = message.gameId,
                    tournamentId = message.tournamentId,
                    tournamentName = message.tournamentName,
                    round = message.round,
                    totalRounds = message.totalRounds,
                )
                _tournamentEvents.tryEmit(
                    TournamentEvent.GameAssigned(
                        tournamentId = message.tournamentId,
                        tournamentName = message.tournamentName,
                        gameId = message.gameId,
                        round = message.round,
                        totalRounds = message.totalRounds,
                    )
                )
                logger.info("Tournament game assigned: ${message.gameId} (${message.tournamentName}, round ${message.round}/${message.totalRounds})")
            }

            is ServerMessage.TournamentRoundStarted -> {
                _tournamentEvents.tryEmit(
                    TournamentEvent.RoundStarted(
                        tournamentId = message.tournamentId,
                        round = message.round,
                        totalRounds = message.totalRounds,
                    )
                )
                logger.info("Tournament round started: ${message.round}/${message.totalRounds} in ${message.tournamentId}")
            }

            is ServerMessage.TournamentStandingsUpdated -> {
                _tournamentEvents.tryEmit(
                    TournamentEvent.StandingsUpdated(
                        tournamentId = message.tournamentId,
                        standings = message.standings,
                    )
                )
                logger.info("Tournament standings updated: ${message.tournamentId} (${message.standings.size} players)")
            }

            is ServerMessage.TournamentFinished -> {
                _tournamentEvents.tryEmit(
                    TournamentEvent.Finished(
                        tournamentId = message.tournamentId,
                        finalStandings = message.finalStandings,
                    )
                )
                logger.info("Tournament finished: ${message.tournamentId} — winner: ${message.finalStandings.firstOrNull()?.username}")
            }

            is ServerMessage.TournamentCancelled -> {
                _tournamentEvents.tryEmit(TournamentEvent.Cancelled(message.tournamentId))
                logger.info("Tournament cancelled: ${message.tournamentId}")
            }

            // ── Multijugador ─────────────────────────────────────────────────
            // El protocolo MP (mesas 2–6) lo consume su propio cliente (M7). En el cliente
            // 2-jugadores se ignora: mantiene el juego nuevo aislado del publicado.
            is ServerMessage.Mp -> Unit
        }
    }

    // ============ Challenge API ============

    suspend fun sendChallenge(targetUserId: String, timeControl: String, rated: Boolean) {
        wsClient.send(ClientMessage.ChallengeUser(targetUserId, timeControl, rated))
    }

    suspend fun respondToChallenge(challengeId: String, accept: Boolean) {
        wsClient.send(ClientMessage.RespondToChallenge(challengeId, accept))
    }

    suspend fun cancelChallenge(challengeId: String) {
        wsClient.send(ClientMessage.CancelChallenge(challengeId))
    }

    /**
     * Limpia el estado de la partida identificada por [gameId].
     *
     * Si [gameId] no coincide con la partida actual (por ejemplo porque ya empezó
     * una nueva), la llamada es no-op. Esto evita que el delay post-partida en
     * [GameScreen] borre una nueva partida que el servidor ya inició.
     *
     * @param gameId ID de la partida que terminó y debe limpiarse.
     */
    fun clearCurrentGame(gameId: String) {
        if (_currentGame.value?.gameId == gameId) {
            _currentGame.value = null
            _matchmakingState.value = MatchmakingState.Idle
            _drawOffer.value = null
            _pendingDrawSent.value = false
            _rematchOffer.value = null
        }
    }

    /**
     * Limpia incondicionalmente el estado. Solo usar al cerrar la aplicación
     * o al desconectar explícitamente el WebSocket.
     */
    fun clearCurrentGameUnconditionally() {
        _currentGame.value = null
        _matchmakingState.value = MatchmakingState.Idle
        _drawOffer.value = null
        _pendingDrawSent.value = false
        _rematchOffer.value = null
    }

    /**
     * Libera recursos
     *
     * Debe ser llamado cuando ya no se necesite el cliente
     */
    fun dispose() {
        _scope.cancel()
        clearCurrentGameUnconditionally()
        wsClient.disconnect()
    }
}

/**
 * Factory para crear instancias de OnlineGameClient
 */
object OnlineGameClientFactory {

    /**
     * Crea un cliente de juego online configurado
     *
     * @param serverUrl URL del servidor
     * @param authRepository Repositorio para obtener token JWT
     * @return Cliente de juego online listo para usar
     */
    fun create(
        serverUrl: String,
        authRepository: AuthRepository
    ): OnlineGameClient {
        val wsClient = TaratiWebSocketClientFactory.create(serverUrl, authRepository)
        return OnlineGameClient(wsClient)
    }
}
