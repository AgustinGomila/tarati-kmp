package com.agustin.tarati.features.game


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameResult
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.features.online.game.DrawOfferEvent
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.game.RematchEvent
import com.agustin.tarati.features.online.game.ServerErrorEvent
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.network.protocol.TimeControlInfo
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.clock.ClockViewModel
import com.agustin.tarati.services.clock.IClockService
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.ActionStyle
import com.agustin.tarati.services.notifications.MessageAction
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.decline
import com.agustin.tarati.shared.generated.resources.draw_offer_declined
import com.agustin.tarati.shared.generated.resources.notification_draw
import com.agustin.tarati.shared.generated.resources.notification_reason_agreement
import com.agustin.tarati.shared.generated.resources.notification_reason_mit
import com.agustin.tarati.shared.generated.resources.notification_reason_resignation
import com.agustin.tarati.shared.generated.resources.notification_reason_stalemit
import com.agustin.tarati.shared.generated.resources.notification_reason_timeout
import com.agustin.tarati.shared.generated.resources.notification_you_lost
import com.agustin.tarati.shared.generated.resources.notification_you_won
import com.agustin.tarati.shared.generated.resources.online_move_failed
import com.agustin.tarati.shared.generated.resources.rematch
import com.agustin.tarati.shared.generated.resources.rematch_declined
import com.agustin.tarati.shared.generated.resources.rematch_expired
import com.agustin.tarati.shared.generated.resources.rematch_offered_by_opponent
import com.agustin.tarati.shared.generated.resources.server_error
import com.agustin.tarati.shared.generated.resources.spectator_game_ended_draw
import com.agustin.tarati.shared.generated.resources.spectator_game_ended_wins
import com.agustin.tarati.ui.components.game.animation.AnimationCoordinator
import com.agustin.tarati.ui.components.game.animation.AnimationEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Todos los [LaunchedEffect]s y [DisposableEffect]s del flujo online en [GameScreen].
 *
 * Extrae las responsabilidades online para reducir la longitud de [GameScreen]:
 * - Cleanup al salir (partida en curso abandonada)
 * - Refresh periódico del access token
 * - Matchmaking automático inicial
 * - Sincronización de tablero espectado (historial, movimientos, reloj)
 * - Notificaciones de revancha y tablas
 * - Ciclo de vida de la partida propia (InProgress → Finished)
 * - Movimiento del oponente y reloj de partida propia
 *
 * @param onlinePlayerSideState Estado mutable del color del jugador local. Legible
 *   desde fuera (UI) via by-delegation; escribible aquí al iniciar/terminar partida.
 * @param setOnlineFinishedResult Setter para el resultado de partida terminada. Leído
 *   en el dispatch de diálogos que permanece en [GameScreen].
 * @param setShowMatchmakingModal Setter para mostrar/ocultar el modal de búsqueda.
 * @param startMatchmaking Función suspend que inicia el matchmaking (definida en [GameScreen]
 *   porque usa ensureConnected y el bus local).
 */
@Composable
fun OnlineGameSideEffects(
    viewModel: IGameModel,
    onlineGameViewModel: IOnlineGameViewModel,
    connectionViewModel: IConnectionViewModel,
    authViewModel: IAuthViewModel,
    clockService: IClockService = koinViewModel<ClockViewModel>(),
    events: GameEvents,
    gameManagerState: GameManagerState,
    currentOnlineGame: OnlineGame?,
    spectatingState: SpectatingState?,
    rematchOffer: String?,
    onlinePlayerSideState: MutableState<CobColor?>,
    initialMatchmaking: Pair<String, Boolean>?,
    setOnlineFinishedResult: (OnlineGameStatus.Finished?) -> Unit,
    setShowMatchmakingModal: (Boolean) -> Unit,
    startMatchmaking: suspend (String, Boolean, Boolean) -> Unit,
    animationCoordinator: AnimationCoordinator,
) {
    val scope = rememberCoroutineScope()
    val bus: UIMessageBus = koinInject()
    val achievementsManager: IAchievementsManager = koinInject()
    var onlinePlayerSide by onlinePlayerSideState

    // ── Strings ───────────────────────────────────────────────────────────────
    // rememberUpdatedState garantiza que los colectores LaunchedEffect(Unit) lean
    // el valor actualizado aunque los recursos carguen tras la primera composición.

    val notifYouWon by rememberUpdatedState(stringResource(Res.string.notification_you_won))
    val notifYouLost by rememberUpdatedState(stringResource(Res.string.notification_you_lost))
    val notifDraw by rememberUpdatedState(stringResource(Res.string.notification_draw))
    val notifResignation by rememberUpdatedState(stringResource(Res.string.notification_reason_resignation))
    val notifTimeout by rememberUpdatedState(stringResource(Res.string.notification_reason_timeout))
    val notifAgreement by rememberUpdatedState(stringResource(Res.string.notification_reason_agreement))
    val notifMit by rememberUpdatedState(stringResource(Res.string.notification_reason_mit))
    val notifStalemit by rememberUpdatedState(stringResource(Res.string.notification_reason_stalemit))
    val rematchLabel by rememberUpdatedState(stringResource(Res.string.rematch))
    val cancelLabel by rememberUpdatedState(localizedString(Res.string.cancel))
    val rematchDeclinedMsg by rememberUpdatedState(stringResource(Res.string.rematch_declined))
    val rematchExpiredMsg by rememberUpdatedState(stringResource(Res.string.rematch_expired))
    val rematchOfferedByOpponentMsg by rememberUpdatedState(stringResource(Res.string.rematch_offered_by_opponent))
    val drawOfferDeclinedMsg by rememberUpdatedState(stringResource(Res.string.draw_offer_declined))
    val onlineMoveFailedMsg by rememberUpdatedState(stringResource(Res.string.online_move_failed))
    val serverErrorMsg by rememberUpdatedState(stringResource(Res.string.server_error))
    val spectEndWins by rememberUpdatedState(stringResource(Res.string.spectator_game_ended_wins))
    val spectEndDraw by rememberUpdatedState(stringResource(Res.string.spectator_game_ended_draw))

    // ── Cleanup al salir ──────────────────────────────────────────────────────

    // Si el usuario navega fuera de la pantalla durante el delay del handler de Finished,
    // el LaunchedEffect se cancela antes de llamar clearOnlineGame().
    // Este DisposableEffect garantiza que _currentGame se limpie al salir, evitando
    // que al re-entrar se dispare gameOver() sobre un GameViewModel fresco.
    DisposableEffect(Unit) {
        onDispose {
            val game = onlineGameViewModel.currentGame.value
            if (game != null && game.status is OnlineGameStatus.Finished) {
                onlineGameViewModel.clearOnlineGame(game.gameId)
            }
        }
    }

    // ── Token refresh proactivo ───────────────────────────────────────────────

    LaunchedEffect(Unit) {
        val logger = getLogger("OnlineGameSideEffects")
        while (true) {
            delay(60_000L.milliseconds)
            if (authViewModel.isTokenExpiringSoon()) {
                logger.debug("Access token expiring soon — refreshing proactively")
                val result = authViewModel.refreshToken()
                if (result.isSuccess) {
                    if (connectionViewModel.isConnected) {
                        val newToken = result.getOrNull() ?: return@LaunchedEffect
                        connectionViewModel.connectToServer(devServerUrl, newToken)
                    }
                } else {
                    logger.debug("Proactive refresh failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    // ── Matchmaking automático inicial ────────────────────────────────────────

    LaunchedEffect(initialMatchmaking) {
        val (tc, rated) = initialMatchmaking ?: return@LaunchedEffect
        startMatchmaking(tc, rated, true)
    }

    // ── Espectador: carga inicial del historial ───────────────────────────────

    // Key = gameId: dispara exactamente una vez por partida, independientemente de los
    // GameStateUpdate que puedan llegar antes de que el efecto anterior termine de ejecutar.
    // Esto evita la race condition donde currentGameState ya fue actualizado (lastMove != null)
    // antes de que el cuerpo del efecto hubiera alcanzado la rama lastMove == null.
    LaunchedEffect(spectatingState?.gameId) {
        val serverState = spectatingState?.currentGameState ?: return@LaunchedEffect

        // Auto-orientar a perspectiva de blancas al entrar en modo espectador.
        // El espectador no tiene un "lado" propio — PORTRAIT_WHITE es la vista estándar.
        viewModel.updateBoardOrientation(BoardOrientation.PORTRAIT_WHITE)

        val moves = spectatingState.moveHistory
        if (moves.isNotEmpty()) {
            viewModel.updateHistory(moves)
            viewModel.moveToCurrentState()
        } else {
            viewModel.setGame(serverState)
        }
    }

    // ── Espectador: movimientos incrementales ─────────────────────────────────

    // Si los hashes ya coinciden → nada. Si hay lastMove legal → animar. Si no → snap.
    // lastMove == null (snapshot inicial) ya lo maneja LaunchedEffect(gameId).
    LaunchedEffect(spectatingState?.currentGameState) {
        val serverState = spectatingState?.currentGameState ?: return@LaunchedEffect
        val localState = viewModel.gameState.value

        if (localState.hashBoard() == serverState.hashBoard()) return@LaunchedEffect

        val lastMove = spectatingState.lastMove
        if (lastMove != null && localState.allMovesForTurn().contains(lastMove)) {
            events.applyMove(lastMove, localState)
        } else if (lastMove != null) {
            // El estado local está más de un movimiento atrás del servidor
            // (p. ej. varios GameStateUpdates llegaron antes de que LaunchedEffect(gameId)
            // terminara de cargar el historial, o el cliente se quedó temporalmente atrás).
            // Recargar desde el historial completo (que OnlineGameClient mantiene al día
            // acumulando cada lastMove) para sincronizar tablero e historial en un solo paso.
            val moveHistory = spectatingState.moveHistory
            if (moveHistory.isNotEmpty()) {
                viewModel.updateHistory(moveHistory)
                viewModel.moveToCurrentState()
            } else {
                viewModel.updateGameState(serverState)
            }
        }
        // lastMove == null: snapshot inicial — LaunchedEffect(gameId) ya lo maneja.
    }

    // ── Espectador: reloj ─────────────────────────────────────────────────────

    LaunchedEffect(spectatingState?.timeRemaining) {
        val timeRemaining = spectatingState?.timeRemaining ?: return@LaunchedEffect
        if (timeRemaining.whiteMs <= 0L && timeRemaining.blackMs <= 0L) return@LaunchedEffect
        val activeTurn = spectatingState.currentGameState.currentTurn
        val fallbackMode = spectatingState.timeControl.toTimeControlMode()
            ?: TimeControlMode.SuddenDeath(maxOf(timeRemaining.whiteMs, timeRemaining.blackMs))
        clockService.syncFromServer(
            whiteMs = timeRemaining.whiteMs,
            blackMs = timeRemaining.blackMs,
            activeTurn = activeTurn,
            fallbackMode = fallbackMode,
        )
    }

    // ── Espectador: fin de partida ────────────────────────────────────────────

    LaunchedEffect(Unit) {
        onlineGameViewModel.spectatingGameEnded.collect { event ->
            // Aplicar el último movimiento si el tablero no llegó a mostrarlo.
            // GameEnded cancela el LaunchedEffect(spectatingState?.currentGameState) que
            // estaba animando el movimiento, así que lo completamos aquí con el mismo delay
            // que usa el handler Finished de partidas propias.
            val lastMove = event.lastMove
            val finalState = event.finalGameState
            if (lastMove != null && finalState != null &&
                viewModel.gameState.value.hashBoard() != finalState.hashBoard()
            ) {
                events.applyMove(lastMove, viewModel.gameState.value)
                delay(650.milliseconds)
            }

            val result = when (event.result) {
                GameResult.WHITE_WIN.key -> spectEndWins.replace($$"%1$s", event.whiteUsername)
                GameResult.BLACK_WIN.key -> spectEndWins.replace($$"%1$s", event.blackUsername)
                else -> spectEndDraw
            }
            val reason = when (event.reason) {
                GameEndReason.RESIGNATION.key -> notifResignation
                GameEndReason.TIMEOUT.key -> notifTimeout
                GameEndReason.DRAW_AGREEMENT.key -> notifAgreement
                GameEndReason.MIT.key -> notifMit
                GameEndReason.STALEMIT.key -> notifStalemit
                else -> null
            }
            val msg = if (reason != null) "$result · $reason" else result
            bus.toast(UIMessage.Toast(message = msg, duration = 5.seconds))
        }
    }

    // ── Espectador: detener reloj al salir ───────────────────────────────────
    // Activo solo mientras se espectea. onDispose para el reloj cuando spectatingState
    // pasa a null (ya sea por GameEnded o por salida manual del espectador).
    if (spectatingState != null) {
        DisposableEffect(spectatingState.gameId) {
            onDispose { clockService.stopClock() }
        }
    }

    // ── Revancha: oferta recibida ─────────────────────────────────────────────

    // Se usa Alert en lugar de Toast para que sea visible aunque el toast de resultado
    // o el de revancha propia estén activos.
    LaunchedEffect(rematchOffer) {
        if (currentOnlineGame?.status !is OnlineGameStatus.Finished) return@LaunchedEffect
        bus.alert { dismiss ->
            AlertDialog(
                onDismissRequest = {
                    dismiss()
                    scope.launch { onlineGameViewModel.declineRematch() }
                },
                title = { Text(rematchOfferedByOpponentMsg) },
                confirmButton = {
                    TextButton(onClick = {
                        dismiss()
                        scope.launch { onlineGameViewModel.acceptRematch() }
                    }) {
                        Text(rematchLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dismiss()
                        scope.launch { onlineGameViewModel.declineRematch() }
                    }) {
                        Text(stringResource(Res.string.decline))
                    }
                },
            )
        }
    }

    // ── Eventos de tablas rechazadas ──────────────────────────────────────────

    LaunchedEffect(Unit) {
        onlineGameViewModel.drawOfferEvents.collect { event ->
            when (event) {
                is DrawOfferEvent.Declined -> bus.toast(UIMessage.Toast(message = drawOfferDeclinedMsg))
            }
        }
    }

    // ── Eventos de revancha (rechazada / expirada) ────────────────────────────

    LaunchedEffect(Unit) {
        onlineGameViewModel.rematchEvents.collect { event ->
            val msg = when (event) {
                is RematchEvent.Declined -> rematchDeclinedMsg
                is RematchEvent.Expired -> rematchExpiredMsg
            }
            bus.toast(UIMessage.Toast(message = msg, duration = 4.seconds))
        }
    }

    // ── Errores del servidor ──────────────────────────────────────────────────

    // rememberUpdatedState: el colector es LaunchedEffect(Unit) de larga vida; leer el param
    // directo lo congelaría en la primera composición. (Además viewModel.gameManagerState tiene un
    // getter @Composable, no invocable dentro de la corrutina.)
    val latestGameManagerState by rememberUpdatedState(gameManagerState)

    LaunchedEffect(Unit) {
        onlineGameViewModel.serverErrors.collect { event ->
            val msg = when (event) {
                is ServerErrorEvent.InvalidMove -> {
                    // Revertir la aplicación optimista: handleMove aplica la jugada al historial local
                    // antes de mandarla. Si el servidor la rechaza y sigue siendo la última del
                    // historial, truncarla para no dejar el tablero/lista desincronizados. Preciso: si
                    // otra actualización del rival llegó después, la última ya no coincide y no se toca
                    // nada (el sync incremental converge).
                    val rejected = event.move
                    val moves = latestGameManagerState.history.getMoves()
                    if (rejected != null && moves.lastOrNull() == rejected) {
                        viewModel.updateHistory(moves.dropLast(1), initialGameState())
                        viewModel.moveToCurrentState()
                    }
                    onlineMoveFailedMsg.replace($$"%1$s", event.reason)
                }

                is ServerErrorEvent.GenericError ->
                    serverErrorMsg.replace($$"%1$s", event.message)
            }
            bus.toast(UIMessage.Toast(message = msg))
        }
    }

    // ── Ciclo de vida de la partida propia ────────────────────────────────────

    LaunchedEffect(currentOnlineGame?.status) {
        when (val status = currentOnlineGame?.status) {
            OnlineGameStatus.InProgress -> {
                // Si estábamos espectando, dejar la partida antes de iniciar la propia.
                onlineGameViewModel.stopSpectating()
                val onlineColor = cobColorByDescription(currentOnlineGame.yourColor) ?: BLACK
                onlinePlayerSide = onlineColor
                setShowMatchmakingModal(false)
                bus.clearAlert()
                // Cancelar de inmediato los toasts de revancha/resultado de la partida anterior:
                // al arrancar una partida nueva (matchmaking o revancha aceptada) el status ya es
                // InProgress —no Finished—, así que el clear por reset de tablero no los cubre.
                bus.clearAllToasts()
                setOnlineFinishedResult(null)
                viewModel.startGame(onlineColor)
                // Orientar el tablero para que las piezas del jugador humano queden
                // siempre abajo (portrait) o a la izquierda (landscape).
                val currentOrientation = viewModel.boardOrientation.value
                val isLandscape = currentOrientation == BoardOrientation.LANDSCAPE_WHITE ||
                        currentOrientation == BoardOrientation.LANDSCAPE_BLACK
                viewModel.updateBoardOrientation(
                    if (isLandscape) {
                        if (onlineColor == WHITE) BoardOrientation.LANDSCAPE_WHITE
                        else BoardOrientation.LANDSCAPE_BLACK
                    } else {
                        if (onlineColor == WHITE) BoardOrientation.PORTRAIT_WHITE
                        else BoardOrientation.PORTRAIT_BLACK
                    },
                )
                // StopHighlights + SyncState forces the board to redraw from scratch,
                // clearing any animated pieces left over from the previous game.
                animationCoordinator.handleEvent(AnimationEvent.StopHighlights)
                animationCoordinator.handleEvent(AnimationEvent.SyncState)
                viewModel.suppressLogoTransition()
                // Apply the full server state if moves arrived before startGame() ran
                // (e.g. device rotation mid-game or bot moving before GameStarted is processed).
                val serverState = currentOnlineGame.gameState
                if (serverState != null &&
                    serverState.hashBoard() != initialGameState().hashBoard()
                ) {
                    viewModel.updateGameState(serverState)
                }
            }

            is OnlineGameStatus.Finished -> {
                val finishedGameId = currentOnlineGame.gameId
                val move = currentOnlineGame.lastMove
                val finalState = currentOnlineGame.gameState
                if (move != null && finalState != null &&
                    gameManagerState.gameState.hashBoard() != finalState.hashBoard()
                ) {
                    events.applyMove(move, gameManagerState.gameState)
                    delay(650.milliseconds)
                }
                // Resignation has no lastMove — set resignedColor so getMatchState() returns RESIGNATION.
                if (status.reason == GameEndReason.RESIGNATION.key) {
                    val loser = when (status.result) {
                        GameResult.WHITE_WIN.key -> BLACK
                        GameResult.BLACK_WIN.key -> WHITE
                        else -> null
                    }
                    if (loser != null) {
                        val currentState = gameManagerState.gameState
                        if (currentState.resignedColor == null) {
                            viewModel.updateGameState(currentState.copy(resignedColor = loser))
                        }
                    }
                }
                // Draw agreement has no lastMove — set drawAgreed so getMatchState() returns DRAW_AGREEMENT.
                if (status.reason == GameEndReason.DRAW_AGREEMENT.key) {
                    val currentState = gameManagerState.gameState
                    if (!currentState.drawAgreed) {
                        viewModel.updateGameState(currentState.copy(drawAgreed = true))
                    }
                }
                setOnlineFinishedResult(status)

                // Disparar logros de fin de partida online.
                // difficulty = null indica que no hay IA — los logros de dificultad no aplican.
                val finalMatchState = gameManagerState.gameState.getMatchState()
                val capturedSide = onlinePlayerSide
                if (capturedSide != null) {
                    scope.launch {
                        achievementsManager.onGameOver(
                            matchState = finalMatchState,
                            playerSide = capturedSide,
                            difficulty = null,
                        )
                    }
                }

                bus.toast(
                    UIMessage.Toast(
                        message = buildFinalNotifMessage(
                            status = status,
                            onlinePlayerSide = onlinePlayerSide,
                            notifYouWon = notifYouWon,
                            notifYouLost = notifYouLost,
                            notifDraw = notifDraw,
                            notifResignation = notifResignation,
                            notifTimeout = notifTimeout,
                            notifAgreement = notifAgreement,
                            notifMit = notifMit,
                            notifStalemit = notifStalemit,
                        ),
                        duration = 5.seconds,
                    )
                )
                viewModel.gameOver()
                // Canal de señal para cancelación anticipada (botón Cancelar).
                // "Revancha" auto-descarta el toast pero NO señaliza este canal,
                // por lo que el wait continúa esperando la respuesta del servidor.
                val cancelChannel = Channel<Unit>(capacity = Channel.CONFLATED)
                // Ofrecer revancha durante la ventana del servidor (30s).
                // El toast de resultado ocupa los primeros 5s; el de revancha los siguientes 23s.
                // Si el oponente acepta, RematchAccepted cambia currentOnlineGame?.status
                // a Starting, lo que cancela este LaunchedEffect antes de que termine el wait.
                bus.toast(
                    UIMessage.Toast(
                        message = rematchLabel,
                        duration = 23.seconds,
                        actions = listOf(
                            MessageAction(
                                label = rematchLabel,
                                style = ActionStyle.PRIMARY,
                                onClick = { scope.launch { onlineGameViewModel.offerRematch(finishedGameId) } },
                            ),
                            MessageAction(
                                label = cancelLabel,
                                style = ActionStyle.SECONDARY,
                                onClick = { cancelChannel.trySend(Unit) },
                            ),
                        ),
                    )
                )
                // Esperar 30s, o hasta que el usuario pulse Cancelar (lo primero).
                // Si RematchAccepted llega antes, el LaunchedEffect se cancela externamente.
                withTimeoutOrNull(30_000.milliseconds) {
                    cancelChannel.receive()
                }
                // Solo limpiar si no se inició una nueva partida (la revancha aceptada
                // ya habría cambiado el status a Starting y cancelado este coroutine).
                if (onlineGameViewModel.currentGame.value?.gameId == finishedGameId) {
                    bus.clearAlert()
                    onlinePlayerSide = null
                    onlineGameViewModel.clearOnlineGame(finishedGameId)
                    viewModel.stopGame()
                }
            }

            // Starting → manejado por LaunchedEffect(status=Starting) en GameScreen
            OnlineGameStatus.Starting -> Unit
            null -> Unit
        }
    }

    // ── Limpieza de toasts de revancha al iniciar partida local ──────────────
    // Si el usuario pulsa "Nueva partida" mientras hay un juego online en Finished,
    // el tablero vuelve a la posición inicial. Detectamos ese cambio y descartamos
    // cualquier toast de resultado/revancha que aún esté visible o encolado.
    LaunchedEffect(gameManagerState.gameState) {
        val boardIsReset = gameManagerState.gameState.hashBoard() ==
                initialGameState().hashBoard()
        if (boardIsReset && currentOnlineGame?.status is OnlineGameStatus.Finished) {
            bus.clearAllToasts()
        }
    }

    // ── Movimiento del oponente ───────────────────────────────────────────────

    // La key incluye onlinePlayerSide para re-ejecutar el efecto una vez que
    // startGame() inicializó el tablero. El updateGameState() en InProgress ya
    // aplica el estado completo del servidor; este efecto maneja solo los movimientos
    // incrementales posteriores durante la partida.
    LaunchedEffect(currentOnlineGame?.gameState, onlinePlayerSideState.value) {
        val game = currentOnlineGame ?: return@LaunchedEffect
        val move = game.lastMove ?: return@LaunchedEffect
        if (game.status != OnlineGameStatus.InProgress) return@LaunchedEffect
        val serverState = game.gameState ?: return@LaunchedEffect
        if (onlinePlayerSide == null) return@LaunchedEffect
        // Skip si el estado local ya coincide con el servidor.
        if (gameManagerState.gameState.hashBoard() == serverState.hashBoard()) return@LaunchedEffect
        events.applyMove(move, gameManagerState.gameState)
    }

    // ── Reloj de la partida propia ────────────────────────────────────────────

    // activeTurn is read from the server gameState, not the local one — the local
    // LaunchedEffect may not have run yet, which would cause the wrong clock to tick.
    LaunchedEffect(currentOnlineGame?.whiteTimeMs, currentOnlineGame?.blackTimeMs) {
        val game = currentOnlineGame ?: return@LaunchedEffect
        val white = game.whiteTimeMs ?: return@LaunchedEffect
        val black = game.blackTimeMs ?: return@LaunchedEffect
        if (game.status != OnlineGameStatus.InProgress) return@LaunchedEffect
        val serverCurrentTurn = game.gameState?.currentTurn ?: return@LaunchedEffect
        clockService.syncFromServer(
            whiteMs = white,
            blackMs = black,
            activeTurn = serverCurrentTurn,
            fallbackMode = game.timeControl.toTimeControlMode(),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Construye el mensaje de notificación del resultado de la partida, relativo al jugador local.
 * Incluye el delta de rating si corresponde.
 */
private fun buildFinalNotifMessage(
    status: OnlineGameStatus.Finished,
    onlinePlayerSide: CobColor?,
    notifYouWon: String,
    notifYouLost: String,
    notifDraw: String,
    notifResignation: String,
    notifTimeout: String,
    notifAgreement: String,
    notifMit: String,
    notifStalemit: String,
): String {
    val resultPrefix = when {
        status.result == GameResult.DRAW.key -> notifDraw
        onlinePlayerSide == WHITE && status.result == GameResult.WHITE_WIN.key -> notifYouWon
        onlinePlayerSide == BLACK && status.result == GameResult.BLACK_WIN.key -> notifYouWon
        else -> notifYouLost
    }
    val reasonSuffix = when (status.reason) {
        GameEndReason.RESIGNATION.key -> notifResignation
        GameEndReason.TIMEOUT.key -> notifTimeout
        GameEndReason.DRAW_AGREEMENT.key -> notifAgreement
        GameEndReason.MIT.key -> notifMit
        GameEndReason.STALEMIT.key -> notifStalemit
        else -> null
    }
    val ratingDelta = status.ratingUpdate?.change?.let { delta ->
        if (delta >= 0) " (+$delta)" else " ($delta)"
    } ?: ""
    return if (reasonSuffix != null) "$resultPrefix · $reasonSuffix$ratingDelta"
    else "$resultPrefix$ratingDelta"
}

/**
 * Convierte [TimeControlInfo] (protocolo de red) a [TimeControlMode] (reloj local).
 * Usa Fischer si hay incremento, SuddenDeath si no.
 * Devuelve null para partidas sin tiempo (initial == 0).
 */
internal fun TimeControlInfo.toTimeControlMode(): TimeControlMode? {
    if (initial <= 0) return null
    val initialMs = initial * 1_000L
    return if (increment > 0) {
        TimeControlMode.Fischer(initialMs, increment * 1_000L)
    } else {
        TimeControlMode.SuddenDeath(initialMs)
    }
}
