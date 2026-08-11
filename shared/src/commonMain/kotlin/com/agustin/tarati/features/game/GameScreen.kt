package com.agustin.tarati.features.game


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.services.displayNameRes
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.core.utils.FeatureFlags
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.ui.MatchmakingModal
import com.agustin.tarati.features.online.ui.OnlineGameBar
import com.agustin.tarati.features.seasonal.ISpecialEventManager
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.ai.AIViewModel
import com.agustin.tarati.services.ai.IAIService
import com.agustin.tarati.services.clock.ClockViewModel
import com.agustin.tarati.services.clock.IClockService
import com.agustin.tarati.services.dialogs.AboutDialog
import com.agustin.tarati.services.dialogs.GameOverDialog
import com.agustin.tarati.services.dialogs.NewGameDialog
import com.agustin.tarati.services.dialogs.buildGameOverMessage
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.services.sound.LocalSoundService
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.connect_to_server_first
import com.agustin.tarati.shared.generated.resources.could_not_connect
import com.agustin.tarati.shared.generated.resources.game_saved
import com.agustin.tarati.shared.generated.resources.matchmaking_failed
import com.agustin.tarati.shared.generated.resources.move_history_copied_to_clipboard
import com.agustin.tarati.shared.generated.resources.online_move_failed
import com.agustin.tarati.shared.generated.resources.player_ai
import com.agustin.tarati.shared.generated.resources.player_human
import com.agustin.tarati.ui.components.game.BoardEvents
import com.agustin.tarati.ui.components.game.animation.AnimationCoordinator
import com.agustin.tarati.ui.components.game.animation.BoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.BoardGeometryViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardGeometryViewModel
import com.agustin.tarati.ui.components.game.behaviors.BoardSelectionViewModel
import com.agustin.tarati.ui.components.game.behaviors.IBoardSelectionViewModel
import com.agustin.tarati.ui.components.navigation.injectGameViewModel
import com.agustin.tarati.ui.components.sidebar.SidebarContent
import com.agustin.tarati.ui.components.turnIndicator.TurnIndicatorState
import com.agustin.tarati.ui.components.tutorial.ITutorialService
import com.agustin.tarati.ui.components.tutorial.TutorialService
import com.agustin.tarati.ui.components.tutorial.TutorialViewModel
import com.agustin.tarati.ui.layout.CompanionPanelDestination
import com.agustin.tarati.ui.layout.LocalCompanionPanelController
import com.agustin.tarati.ui.layout.LocalScreenLayout
import com.agustin.tarati.ui.layout.ScreenLayout
import com.agustin.tarati.ui.theme.TaratiBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: IGameModel = injectGameViewModel(),
    animationViewModel: IBoardAnimationViewModel = koinViewModel<BoardAnimationViewModel>(),
    geometryViewModel: IBoardGeometryViewModel = koinViewModel<BoardGeometryViewModel>(),
    selectViewModel: IBoardSelectionViewModel = koinViewModel<BoardSelectionViewModel>(),
    settingsViewModel: ISettingsViewModel = koinViewModel<SettingsViewModel>(),
    aiViewModel: IAIService = koinViewModel<AIViewModel>(),
    connectionViewModel: IConnectionViewModel = koinInject(),
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onGamesLibrary: () -> Unit,
    onOnlineLobby: () -> Unit = {},
    onSaveGame: (match: MatchDto) -> Unit,
    /**
     * Abre el análisis completo de la partida actual (detalle con gráfico por-ply + clasificación).
     * [offlineMatch] es un [MatchDto] temporal armado desde el estado en memoria (partida offline);
     * [onlineGameId] no-null indica preferir el detalle persistente remoto. El caller resuelve la
     * navegación (companion en Expanded / ruta en portrait). Ver `openGameAnalysis`.
     */
    onNavigateToAnalysisDetails: (offlineMatch: MatchDto, onlineGameId: String?) -> Unit = { _, _ -> },
    /** Navega a LoginScreen. [postLoginAction] es una suspend lambda que NavGraph ejecuta tras el login. */
    onNavigateToLogin: (postLoginAction: suspend () -> Unit) -> Unit = {},
    /**
     * Si no-null, inicia matchmaking automáticamente al entrar a la pantalla.
     * Usado cuando el usuario toca "Unirse" en una búsqueda del lobby:
     * el TC y rated ya están definidos por el anfitrión de la búsqueda.
     */
    initialMatchmaking: Pair<String, Boolean>? = null,
) {
    val scope = rememberCoroutineScope()
    val bus: UIMessageBus = koinInject()
    val screenLayout = LocalScreenLayout.current
    val companion = LocalCompanionPanelController.current
    val companionDestination = companion.destination
    // Portrait: overlay colapsable del análisis. En Expanded el análisis va al panel lateral.
    var showAnalysisPortrait by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // ── Services ─────────────────────────────────────────────────────────────
    val services = rememberGameScreenServices(animationViewModel)

    // ── Tutorial ──────────────────────────────────────────────────────────────
    val tutorialAnimationCoordinator = remember { AnimationCoordinator(animationViewModel) }
    val tutorialViewModel: TutorialViewModel = koinViewModel(
        parameters = { parametersOf(tutorialAnimationCoordinator) },
    )
    val tutorialService = remember(tutorialViewModel) { TutorialService(tutorialViewModel) }
    val tutorialState by tutorialViewModel.tutorialState.collectAsState()

    // ── Online state (hoisted — needed before rememberGameScreenState) ───────────
    val currentOnlineGame by onlineGameViewModel.currentGame.collectAsState()
    val isOnlineGame = currentOnlineGame?.status == OnlineGameStatus.InProgress
    val spectatingState by onlineGameViewModel.spectatingState.collectAsState()
    val gameManagerState by viewModel.gameManagerState
    // onlinePlayerSide usa MutableState explícito para poder pasarlo a OnlineGameSideEffects.
    val onlinePlayerSideState = rememberSaveable { mutableStateOf<CobColor?>(null) }
    val onlinePlayerSide by onlinePlayerSideState
    val isWaitingForOpponent = isOnlineGame &&
            onlinePlayerSide != null &&
            gameManagerState.gameState.currentTurn != onlinePlayerSide

    // ── Derived state ─────────────────────────────────────────────────────────
    val screenState = rememberGameScreenState(
        viewModel = viewModel,
        aiViewModel = aiViewModel,
        tutorialViewModel = tutorialViewModel,
        isWaitingForOpponent = isWaitingForOpponent,
        isSpectating = spectatingState != null,
    )

    // ── ViewModel state ───────────────────────────────────────────────────────
    val isEditing by viewModel.isEditing.collectAsState()
    val editBoardOrientation by viewModel.editBoardOrientation.collectAsState()
    val aiEnabled by viewModel.aIEnabled.collectAsState(true)
    val playerSide by viewModel.playerSide.collectAsState(WHITE)
    val boardOrientation by viewModel.boardOrientation.collectAsState()
    val isManuallyRotated by viewModel.isManuallyRotated.collectAsState()
    val showLogoTransition by viewModel.showLogoTransition.collectAsState()
    val boardSize by geometryViewModel.boardSize.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()

    // ── Online state ──────────────────────────────────────────────────────────
    val connectionState by connectionViewModel.connectionState.collectAsState()
    var onlineFinishedResult by remember { mutableStateOf<OnlineGameStatus.Finished?>(null) }
    var showMatchmakingModal by rememberSaveable { mutableStateOf(false) }
    val matchmakingState by onlineGameViewModel.matchmakingState.collectAsState()
    val drawOfferFrom by onlineGameViewModel.drawOffer.collectAsState()
    val pendingDrawSent by onlineGameViewModel.pendingDrawSent.collectAsState()
    val rematchOffer by onlineGameViewModel.rematchOffer.collectAsState()

    // ── Clipboard / save actions ──────────────────────────────────────────────
    val gameSavedMessage = stringResource(Res.string.game_saved)
    val aiLabel = stringResource(Res.string.player_ai)
    val humanLabel = stringResource(Res.string.player_human)
    val whiteDifficultyLabel = stringResource(screenState.evalConfigWhite.difficulty.displayNameRes)
    val blackDifficultyLabel = stringResource(screenState.evalConfigBlack.difficulty.displayNameRes)
    // Labels de cada banda usando el contexto de localización y el estado de configuración
    // (whiteIsAI, blackIsAI, dificultad, nombre). El ViewModel recibe los strings ya resueltos
    // para no acoplarse a Context. Reusados por guardar y por el análisis completo offline.
    val whitePlayerLabel = buildPlayerLabel(
        aiLabel = aiLabel,
        humanLabel = humanLabel,
        difficultyLabel = whiteDifficultyLabel,
        isAI = screenState.whiteIsAI,
        isCurrentUser = playerSide == WHITE,
        userName = settingsState.userName,
    )
    val blackPlayerLabel = buildPlayerLabel(
        aiLabel = aiLabel,
        humanLabel = humanLabel,
        difficultyLabel = blackDifficultyLabel,
        isAI = screenState.blackIsAI,
        isCurrentUser = playerSide == CobColor.BLACK,
        userName = settingsState.userName,
    )
    val saveCurrentGame: () -> Unit = {
        scope.launch {
            onSaveGame(viewModel.exportGameToMatchDto(whitePlayerLabel, blackPlayerLabel))
            bus.toast(UIMessage.Toast(message = gameSavedMessage))
        }
    }

    val moveHistoryCopiedMessage = localizedString(Res.string.move_history_copied_to_clipboard)
    val copyHistoryToClipboard: (List<Move>) -> Unit = { moves ->
        scope.launch {
            val success = services.clipboardHelper.copyMoveHistory(
                moves = moves,
                gameState = gameManagerState.gameState,
                playerSide = playerSide,
                aiEnabled = aiEnabled,
                positionHistory = aiViewModel.positionHistory,
            )
            if (success) {
                bus.toast(UIMessage.Toast(message = moveHistoryCopiedMessage))
            }
        }
    }

    // ── Online handlers ───────────────────────────────────────────────────────
    val connectToServerFirstMsg = localizedString(Res.string.connect_to_server_first)
    val couldNotConnectMsg = localizedString(Res.string.could_not_connect)

    // Waits for an in-progress connection rather than opening a second one in parallel.
    val ensureConnected: suspend () -> Result<Unit> = {
        when {
            connectionViewModel.isConnected -> Result.success(Unit)

            connectionViewModel.isConnecting -> {
                try {
                    withTimeout(5_000L.milliseconds) {
                        connectionViewModel.connectionState
                            .first {
                                it is ConnectionState.Online ||
                                        it is ConnectionState.Error ||
                                        it is ConnectionState.Offline
                            }
                    }
                    if (connectionViewModel.isConnected) Result.success(Unit)
                    else Result.failure(Exception(couldNotConnectMsg))
                } catch (e: TimeoutCancellationException) {
                    // El timeout de 5s es esperado: el servidor no respondió a tiempo.
                    Result.failure(Exception(couldNotConnectMsg))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Result.failure(Exception(couldNotConnectMsg))
                }
            }

            else -> {
                if (authViewModel.isTokenExpiringSoon()) authViewModel.refreshToken()
                val token = authViewModel.accessToken ?: authViewModel.getStoredToken()
                if (token == null) {
                    Result.failure(Exception(connectToServerFirstMsg))
                } else {
                    connectionViewModel.connectToServer(devServerUrl, token).map { }
                }
            }
        }
    }

    val handleNavigateToOnlineLobby: () -> Unit = {
        // Siempre ir directo al Lobby — la auto-conexión como invitado sucede allí.
        onOnlineLobby()
    }

    /** Flujo completo del botón único: connect → modal de creación de búsqueda. */
    val handleCreateSearch: () -> Unit = {
        scope.launch {
            val r = ensureConnected()
            if (r.isSuccess) showMatchmakingModal = true
            else bus.toast(UIMessage.Toast(r.exceptionOrNull()?.message ?: couldNotConnectMsg))
        }
    }

    val matchmakingFailedMsg = localizedString(Res.string.matchmaking_failed)
    suspend fun startMatchmaking(timeControl: String, rated: Boolean, spectatingAllowed: Boolean = true) {
        val connResult = ensureConnected()
        if (connResult.isFailure) {
            bus.toast(UIMessage.Toast(connResult.exceptionOrNull()?.message ?: couldNotConnectMsg))
            return
        }
        val result = onlineGameViewModel.startMatchmaking(timeControl, rated, spectatingAllowed)
        if (result.isFailure) {
            bus.toast(
                UIMessage.Toast(
                    matchmakingFailedMsg.replace(
                        $$"%1$s",
                        result.exceptionOrNull()?.message.orEmpty()
                    )
                )
            )
        }
    }

    val handleStartMatchmaking: (String, Boolean, Boolean) -> Unit = { timeControl, rated, spectatingAllowed ->
        scope.launch {
            if (matchmakingState is MatchmakingState.Searching) {
                onlineGameViewModel.cancelMatchmaking()
            }
            startMatchmaking(timeControl, rated, spectatingAllowed)
        }
    }

    val handleCancelMatchmaking: () -> Unit = {
        scope.launch {
            onlineGameViewModel.cancelMatchmaking()
        }
    }

    val handleResignOnlineGame: () -> Unit = {
        scope.launch {
            onlineGameViewModel.resign()
        }
    }

    val handleOfferDraw: () -> Unit = {
        scope.launch {
            onlineGameViewModel.offerDraw()
        }
    }

    // ── Clock ────────────────────────────────────────────────────────────────
    val clockService: IClockService = koinViewModel<ClockViewModel>()

    // ── Events ────────────────────────────────────────────────────────────────
    val events = rememberMainScreenEvents(
        drawerState = drawerState,
        gameService = viewModel,
        difficulty = { viewModel.difficultyWhite.value },
        playerSide = { viewModel.playerSide.value },
        whiteIsAI = { viewModel.whiteIsAI.value },
        blackIsAI = { viewModel.blackIsAI.value },
        onGamesLibrary = onGamesLibrary,
        onSaveGame = saveCurrentGame,
        onCopyMoveHistoryToClipboard = copyHistoryToClipboard,
        animationCoordinator = services.animationCoordinator,
        tutorialService = tutorialService,
        clockService = clockService,
        timeControlProvider = { settingsState.timeControl },
    )

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    val undoRedo = rememberUndoRedoState(
        gameManagerState = gameManagerState,
        screenState = screenState,
        events = events,
        viewModel = viewModel,
        isOnlineGame = isOnlineGame,
        onGameOverDialogReset = { bus.clearAlert() },
    )

    // ── Move handling ─────────────────────────────────────────────────────────
    val onlineMoveFailedMsg = localizedString(Res.string.online_move_failed)
    val handleMove: (Move) -> Unit = { move ->
        when {
            screenState.isTutorialActive ->
                handleTutorialMove(events, gameManagerState, move, tutorialService)

            else -> {
                handleGameMove(events, gameManagerState, move, viewModel)

                // ── Online sync ───────────────────────────────────────────
                if (currentOnlineGame != null) {
                    scope.launch {
                        try {
                            onlineGameViewModel.makeOnlineMove(move)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            bus.toast(UIMessage.Toast(onlineMoveFailedMsg.replace($$"%1$s", e.message.orEmpty())))
                        }
                    }
                }
            }
        }
    }

    // ── Side effects ──────────────────────────────────────────────────────────
    GameScreenSideEffects(
        gameManagerState = gameManagerState,
        screenState = screenState,
        services = services,
        events = events,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        animationViewModel = animationViewModel,
        onMoveHandled = handleMove,
        isOnlineGame = currentOnlineGame != null,
        onlinePlayerSide = onlinePlayerSide,
        isSpectating = spectatingState != null,
    )

    // ── AI / orientation / difficulty effects ─────────────────────────────────
    GameEffects(
        drawerState = drawerState,
        isLandscape = screenState.isLandscape,
        isManuallyRotated = isManuallyRotated,
        gameState = gameManagerState.gameState,
        aiThinkingDependencies = createAiThinkingDependencies(
            gameStatus = gameManagerState.gameStatus,
            currentTurn = gameManagerState.gameState.currentTurn,
            // Durante el tutorial ambos bandos son siempre humanos: se ignora la
            // configuración real para que AiThinkingDependencies.isAITurn nunca
            // sea true y el motor no dispare movimientos automáticos.
            // currentOnlineGame != null (no isOnlineGame, que exige status==InProgress): apaga la
            // IA local ante CUALQUIER partida online, incluida la ventana Starting. Con el predicado
            // estrecho, si el estado quedaba en Starting la IA heredada de la última partida offline
            // seguía jugando y desincronizaba el tablero.
            whiteIsAI = if (screenState.isTutorialActive || currentOnlineGame != null || spectatingState != null) false else screenState.whiteIsAI,
            blackIsAI = if (screenState.isTutorialActive || currentOnlineGame != null || spectatingState != null) false else screenState.blackIsAI,
            evalConfigWhite = screenState.evalConfigWhite,
            evalConfigBlack = screenState.evalConfigBlack,
            isEditing = isEditing,
            boardHash = gameManagerState.gameState.hashBoard(),
            orientationSide = onlinePlayerSide,
        ),
        onAITurn = { gameState, difficulty -> aiViewModel.requestAIMove(gameState, difficulty) },
        onBoardOrientationChanged = viewModel::updateBoardOrientation,
        isTutorialActive = screenState.isTutorialActive,
        tutorialState = tutorialState,
        onTutorialEnd = events::resetTutorial,
    )

    // ── Online effects ────────────────────────────────────────────────────────
    OnlineGameSideEffects(
        viewModel = viewModel,
        onlineGameViewModel = onlineGameViewModel,
        connectionViewModel = connectionViewModel,
        authViewModel = authViewModel,
        clockService = clockService,
        events = events,
        gameManagerState = gameManagerState,
        currentOnlineGame = currentOnlineGame,
        spectatingState = spectatingState,
        rematchOffer = rematchOffer,
        onlinePlayerSideState = onlinePlayerSideState,
        initialMatchmaking = initialMatchmaking,
        setOnlineFinishedResult = { onlineFinishedResult = it },
        setShowMatchmakingModal = { showMatchmakingModal = it },
        startMatchmaking = ::startMatchmaking,
        animationCoordinator = services.animationCoordinator,
    )

    // ── Dialog dispatch ───────────────────────────────────────────────────────

    val aiEngine: IAIEngine = koinInject()
    val latestGameManagerState by rememberUpdatedState(gameManagerState)
    LaunchedEffect(Unit) {
        events.dialogRequest.collect { request ->
            when (request) {
                is DialogRequest.GameOver -> {
                    // En partidas online el servidor es la autoridad del fin de partida.
                    // GameScreenSideEffects ya bloquea el evento en la fuente (isOnlineGame guard).
                    // Este check cubre la ruta de OnlineGameSideEffects donde onlineFinishedResult
                    // se asigna antes de emitir el evento.
                    if (onlineFinishedResult != null) return@collect
                    bus.alert { dismiss ->
                        val matchState = latestGameManagerState.gameState
                            .getMatchState(aiEngine.positionHistory)
                        GameOverDialog(
                            gameOverMessage = buildGameOverMessage(matchState),
                            onConfirmed = {
                                dismiss()
                                events.startNewGame(scope, request.playerSide)
                            },
                            onDismissed = {
                                dismiss()
                                events.stopGame()
                            },
                        )
                    }
                }

                is DialogRequest.NewGame -> {
                    bus.alert { dismiss ->
                        NewGameDialog(
                            onConfirmed = {
                                dismiss()
                                // Si estábamos espectando, interrumpir antes de comenzar la partida local.
                                val spectatingGameId = onlineGameViewModel.spectatingState.value?.gameId
                                if (spectatingGameId != null) scope.launch { onlineGameViewModel.stopSpectating() }
                                events.startNewGame(scope, request.initialColor)
                            },
                            onDismissed = {
                                dismiss()
                                events.stopGame()
                            },
                        )
                    }
                }

                DialogRequest.About -> {
                    bus.alert { dismiss ->
                        AboutDialog(
                            onDismiss = dismiss,
                            onShowTutorial = {
                                dismiss()
                                events.startTutorial(scope)
                            },
                        )
                    }
                }
            }
        }
    }

    TaratiBackground {
        // ── Undo warning dialog ───────────────────────────────────────────────
        if (undoRedo.showUndoWarning) {
            UndoWarningDialog(
                onConfirm = undoRedo.onUndoWarningConfirmed,
                onDismiss = undoRedo.onUndoWarningDismissed,
            )
        }

        // ── Matchmaking modal ─────────────────────────────────────────────────
        if (showMatchmakingModal) {
            MatchmakingModal(
                matchmakingState = matchmakingState,
                onStartSearch = handleStartMatchmaking,
                onCancelSearch = handleCancelMatchmaking,
                onDismiss = { showMatchmakingModal = false },
                isPlayerTurn = !isWaitingForOpponent,
                currentOnlineGame = if (isOnlineGame) currentOnlineGame else null,
                onResign = handleResignOnlineGame,
                onOfferDraw = handleOfferDraw,
            )
        }

        // El Sidebar permanente (Expanded) recompondría en cada movimiento de IA si
        // recibiera gameManagerState directamente. La actualización se difiere al
        // LaunchedEffect para que el panel solo recomponga una vez por frame visible,
        // no una vez por movimiento generado.
        var sidebarGameManagerState by remember { mutableStateOf(gameManagerState) }
        LaunchedEffect(gameManagerState) {
            sidebarGameManagerState = gameManagerState
        }

        // Sidebar content — idéntico en drawer y en panel permanente.
        val sidebarSlot: @Composable () -> Unit = {
            SidebarContent(
                gameManagerState = sidebarGameManagerState,
                playerSide = playerSide,
                evalConfigWhite = screenState.evalConfigWhite,
                evalConfigBlack = screenState.evalConfigBlack,
                aiEnabled = aiEnabled,
                boardOrientation = boardOrientation,
                events = events,
                viewModel = viewModel,
                aiEngine = aiEngine,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToAchievements = onNavigateToAchievements,
                onUndo = undoRedo.handleUndo,
                onlineGame = currentOnlineGame,
                spectatingState = spectatingState,
                onOnlineLobby = handleNavigateToOnlineLobby,
            )
        }

        // Contenido principal — idéntico independientemente del contenedor.
        val mainSlot: @Composable () -> Unit = {
            MainContent(
                drawerState = drawerState,
                isLandscape = screenState.isLandscape,
                boardSize = boardSize,
                boardOrientation = boardOrientation,
                editBoardOrientation = editBoardOrientation,
                gameState = gameManagerState.gameState,
                moveIndex = gameManagerState.moveIndex,
                history = gameManagerState.history,
                turnState = screenState.turnState,
                canClaimDraw = screenState.canClaimDraw,
                onClaimFiftyMoveDraw = { events.claimFiftyMoveDraw(gameManagerState.gameState) },
                showLogoTransition = showLogoTransition,
                distributionState = screenState.distributionState,
                isTutorialActive = screenState.isTutorialActive,
                isAIThinking = screenState.turnState == TurnIndicatorState.AI_THINKING,
                pieceCounts = screenState.pieceCounts,
                boardEvents = object : BoardEvents {
                    override fun onMove(move: Move) = handleMove(move)
                    override fun onEditPiece(from: Vertex) = viewModel.editPiece(from)
                    override fun onResetCompleted() = Unit
                },
                events = events,
                playerManager = when {
                    onlinePlayerSide != null -> {
                        // Partida online activa: el humano ocupa un color, el oponente el otro.
                        val humanIsWhite = onlinePlayerSide == WHITE
                        object : IPlayerManager by viewModel {
                            override val whiteIsAI = MutableStateFlow(!humanIsWhite)
                            override val blackIsAI = MutableStateFlow(humanIsWhite)
                        }
                    }

                    spectatingState != null -> {
                        // Modo espectador: ningún lado es seleccionable.
                        // La supresión de IA se aplica en createAiThinkingDependencies.
                        object : IPlayerManager by viewModel {
                            override val whiteIsAI = MutableStateFlow(true)
                            override val blackIsAI = MutableStateFlow(true)
                        }
                    }

                    else -> viewModel
                },
                boardManager = viewModel,
                editBoard = viewModel,
                boardVisualState = settingsState.boardVisualState,
                preMovesEnabled = settingsState.preMovesEnabled,
                animationViewModel = animationViewModel,
                geometryViewModel = geometryViewModel,
                selectViewModel = selectViewModel,
                tutorialViewModel = tutorialViewModel,
                onTouchIndicator = {
                    when {
                        // Partida terminada: por status o porque el tablero es terminal por reglas
                        // (cubre el caso en que el diálogo de fin se descartó → status NO_PLAYING pero
                        // la posición sigue siendo final; sin este segundo check el tap caía en
                        // resumeGame() y "no hacía nada").
                        gameManagerState.gameStatus == GameStatus.GAME_OVER ||
                                gameManagerState.gameState.isGameOver(aiEngine.positionHistory) ->
                            events.showNewGameDialog(playerSide)

                        else -> events.resumeGame()
                    }
                },
                // ── BottomGameBar ─────────────────────────────────────────────
                onUndo = undoRedo.handleUndo,
                onRedo = undoRedo.handleRedo,
                onMoveToCurrent = undoRedo.handleMoveToCurrent,
                onMoveToIndex = undoRedo.handleMoveToIndex,
                // Undo/redo grisados mientras corre la online o se especta; activos offline o al
                // terminar la online (para navegar su desarrollo). Mismo criterio que el sidebar.
                navigationEnabled = !isOnlineGame && spectatingState == null,
                showEvaluationBar = settingsState.showEvaluationBar,
                onAnalysis = {
                    // Abrir el análisis cancela la secuencia de fin de partida en curso: game_over son
                    // ~53 grupos de highlights = decenas de segundos de render continuo. Si sigue viva
                    // mientras corre el análisis, satura el hilo principal y hambrea el pump del engine
                    // worker en Web (el watchdog lo daría por colgado y caería al fallback). El usuario ya
                    // pasó de la celebración a analizar, así que interrumpirla es lo correcto.
                    if (screenLayout == ScreenLayout.Expanded) {
                        // Toggle con back-stack: cerrar el análisis vuelve al panel de origen
                        // (Lobby, Perfil, Torneo…) o al tablero si se abrió desde él.
                        if (companion.destination == CompanionPanelDestination.Analysis) {
                            companion.back()
                        } else {
                            animationViewModel.clearQueue()
                            companion.navigate(CompanionPanelDestination.Analysis)
                        }
                    } else {
                        val opening = !showAnalysisPortrait
                        if (opening) animationViewModel.clearQueue()
                        showAnalysisPortrait = opening
                    }
                },
                showAnalysisPanel = showAnalysisPortrait && screenLayout != ScreenLayout.Expanded,
                onCloseAnalysisPanel = { showAnalysisPortrait = false },
                onOpenAnalysisDetails = {
                    onNavigateToAnalysisDetails(
                        viewModel.exportGameToMatchDto(whitePlayerLabel, blackPlayerLabel),
                        currentOnlineGame?.gameId,
                    )
                },
                // ── Online components ─────────────────────────────────────────
                onlineContent = if (isOnlineGame || spectatingState != null) ({
                    OnlineGameBar(
                        onlineGame = if (isOnlineGame) currentOnlineGame else null,
                        drawOfferFrom = if (isOnlineGame) drawOfferFrom else null,
                        pendingDrawSent = isOnlineGame && pendingDrawSent,
                        isPlayerTurn = !isWaitingForOpponent,
                        onResign = handleResignOnlineGame,
                        onOfferDraw = handleOfferDraw,
                        onAcceptDraw = {
                            scope.launch { onlineGameViewModel.respondToDraw(true) }
                        },
                        onDeclineDraw = {
                            scope.launch { onlineGameViewModel.respondToDraw(false) }
                        },
                        spectatingState = spectatingState,
                        compact = !screenState.isLandscape,
                    )
                }) else null,
                // Botón único: oculto durante partida propia, en modo lobby expandido,
                // y cuando el acceso online está desactivado por FeatureFlags.
                connectionState = when {
                    !FeatureFlags.ONLINE_ENABLED -> null
                    isOnlineGame -> null
                    screenLayout == ScreenLayout.Expanded &&
                            companionDestination == CompanionPanelDestination.Lobby -> null

                    else -> connectionState
                },
                matchmakingState = matchmakingState,
                onCreateSearch = handleCreateSearch,
                onCancelSearch = handleCancelMatchmaking,
                spectatingState = spectatingState,
                onStopSpectating = {
                    scope.launch { onlineGameViewModel.stopSpectating() }
                },
            )
        }

        // Contenedor adaptativo según el ancho de pantalla.
        if (screenLayout == ScreenLayout.Expanded) {
            // Pantalla ancha: sidebar permanente a la izquierda del tablero.
            Row(modifier = Modifier.fillMaxSize()) {
                sidebarSlot()
                Box(modifier = Modifier.weight(1f)) { mainSlot() }
            }
        } else {
            // Pantalla estrecha/media: sidebar como drawer modal (comportamiento existente).
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = sidebarSlot,
                content = mainSlot,
            )
        }
    }
}

@Composable
fun rememberMainScreenEvents(
    drawerState: DrawerState,
    gameService: IGameService,
    difficulty: () -> Difficulty,
    playerSide: () -> CobColor,
    whiteIsAI: () -> Boolean,
    blackIsAI: () -> Boolean,
    onGamesLibrary: () -> Unit,
    onSaveGame: () -> Unit,
    onCopyMoveHistoryToClipboard: (List<Move>) -> Unit,
    animationCoordinator: AnimationCoordinator,
    clockService: IClockService,
    timeControlProvider: () -> TimeControlMode,
    aiEngine: IAIEngine = koinInject(),
    tutorialService: ITutorialService,
    achievementsManager: IAchievementsManager = koinInject(),
    specialEventManager: ISpecialEventManager = koinInject(),
): GameEvents {
    val scope = rememberCoroutineScope()
    val soundService = LocalSoundService.current

    return remember {
        GameEvents(
            scope = scope,
            drawerState = drawerState,
            gameService = gameService,
            difficulty = difficulty,
            playerSide = playerSide,
            whiteIsAI = whiteIsAI,
            blackIsAI = blackIsAI,
            onGamesLibrary = onGamesLibrary,
            onSaveGame = onSaveGame,
            onCopyMovesToClipboard = onCopyMoveHistoryToClipboard,
            animationCoordinator = animationCoordinator,
            aiEngine = aiEngine,
            tutorialService = tutorialService,
            soundService = soundService,
            achievementsManager = achievementsManager,
            specialEventManager = specialEventManager,
            clockService = clockService,
            timeControlProvider = timeControlProvider,
        )
    }
}

// ── Move handling helpers ─────────────────────────────────────────────────────

internal fun handleGameMove(
    events: GameEvents,
    gameManagerState: GameManagerState,
    move: Move,
    viewModel: IGameModel,
) {
    val status = gameManagerState.gameStatus
    val state = gameManagerState.gameState

    if (status != GameStatus.PLAYING && state.isInitialState(viewModel.playerSide.value)) {
        viewModel.resumeGame()
    }

    events.applyMove(move, state)
}

private fun handleTutorialMove(
    events: GameEvents,
    gameManagerState: GameManagerState,
    move: Move,
    tutorialViewModel: TutorialService,
) {
    val gameState = gameManagerState.gameState

    tutorialViewModel.onMoveAttempted(
        move = move,
        onMoveAccepted = {
            events.applyTutorialMove(move, gameState)
            tutorialViewModel.nextStep()
        },
        onMoveRejected = {
            events.applyTutorialRejectedMove(move, gameState)
            tutorialViewModel.requestUserInteraction(it)
        },
    )
}

private fun createAiThinkingDependencies(
    gameStatus: GameStatus,
    currentTurn: CobColor,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    evalConfigWhite: EvaluationConfig,
    evalConfigBlack: EvaluationConfig,
    isEditing: Boolean,
    boardHash: String,
    orientationSide: CobColor?,
): AiThinkingDependencies = AiThinkingDependencies(
    gameStatus = gameStatus,
    currentTurn = currentTurn,
    whiteIsAI = whiteIsAI,
    blackIsAI = blackIsAI,
    currentTurnConfig = if (currentTurn == WHITE) evalConfigWhite else evalConfigBlack,
    isEditing = isEditing,
    boardHash = boardHash,
    orientationSide = orientationSide,
)

// ── Player label helper ───────────────────────────────────────────────────────

/**
 * Construye la etiqueta descriptiva de un jugador para guardar en el [MatchDto].
 *
 * Reglas:
 * - IA → `"IA (Fácil)"` / `"IA (Campeón)"` etc., usando el string localizado del nivel.
 * - Humano que es el usuario actual → nombre configurado, o `"Humano"` si está vacío.
 * - Humano oponente (partida local 2 jugadores) → `"Humano"`.
 *
 * @param isAI          True si esta banda es controlada por la IA.
 * @param isCurrentUser True si esta banda corresponde al jugador en el dispositivo.
 * @param userName      Nombre configurado en ajustes (puede estar vacío).
 */
fun buildPlayerLabel(
    aiLabel: String,
    humanLabel: String,
    difficultyLabel: String,
    isAI: Boolean,
    isCurrentUser: Boolean,
    userName: String,
): String = when {
    isAI -> "$aiLabel ($difficultyLabel)"
    isCurrentUser -> userName.ifBlank { humanLabel }
    else -> humanLabel
}
