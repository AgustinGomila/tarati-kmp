package com.agustin.tarati.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.ai.services.displayNameRes
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.utils.FeatureFlags
import com.agustin.tarati.features.achievements.AchievementsScreen
import com.agustin.tarati.features.analysis.AnalysisPanel
import com.agustin.tarati.features.analysis.openGameAnalysis
import com.agustin.tarati.features.detail.GameDetailsScreen
import com.agustin.tarati.features.detail.GameDetailsViewModel
import com.agustin.tarati.features.detail.IGameDetailsViewModel
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.game.buildPlayerLabel
import com.agustin.tarati.features.game6.GameMode
import com.agustin.tarati.features.game6.GameModeController
import com.agustin.tarati.features.game6.LocalGameModeController
import com.agustin.tarati.features.game6.MpGameDetailScreen
import com.agustin.tarati.features.game6.MpLeaderboardScreen
import com.agustin.tarati.features.game6.MpLobbyScreen
import com.agustin.tarati.features.game6.MpLobbyViewModel
import com.agustin.tarati.features.library.GamesLibraryScreen
import com.agustin.tarati.features.library.GamesLibraryViewModel
import com.agustin.tarati.features.library.IGamesLibraryViewModel
import com.agustin.tarati.features.online.auth.AuthState
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.LoginSheet
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.features.online.game.ChallengeEvent
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.game.TournamentEvent
import com.agustin.tarati.features.online.lobby.IOnlineLobbyViewModel
import com.agustin.tarati.features.online.lobby.OnlineLobbyScreen
import com.agustin.tarati.features.online.lobby.OnlineLobbyViewModel
import com.agustin.tarati.features.online.social.LeaderboardScreen
import com.agustin.tarati.features.online.social.PublicProfileScreen
import com.agustin.tarati.features.online.supporter.SupporterScreen
import com.agustin.tarati.features.online.tournament.TournamentDetailScreen
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.features.settings.LanguageAwareSettingsScreen
import com.agustin.tarati.features.settings.OnlineSettingsScreen
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.features.store.StoreScreen
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.AlertHost
import com.agustin.tarati.services.notifications.ToastHost
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.accept
import com.agustin.tarati.shared.generated.resources.analysis_title
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.game6_invite_body
import com.agustin.tarati.shared.generated.resources.game6_invite_declined
import com.agustin.tarati.shared.generated.resources.game6_invite_expired
import com.agustin.tarati.shared.generated.resources.game6_invite_title
import com.agustin.tarati.shared.generated.resources.player_ai
import com.agustin.tarati.shared.generated.resources.player_human
import com.agustin.tarati.shared.generated.resources.social_challenge_declined
import com.agustin.tarati.shared.generated.resources.social_challenge_expired
import com.agustin.tarati.shared.generated.resources.social_challenge_from
import com.agustin.tarati.shared.generated.resources.social_challenge_invite
import com.agustin.tarati.shared.generated.resources.tournament_cancelled_notification
import com.agustin.tarati.shared.generated.resources.tournament_finished_notification
import com.agustin.tarati.shared.generated.resources.tournament_game_assigned
import com.agustin.tarati.ui.components.navigation.NavGraph
import com.agustin.tarati.ui.components.navigation.injectGameViewModel
import com.agustin.tarati.ui.layout.CompanionPanelController
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Achievements
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Analysis
import com.agustin.tarati.ui.layout.CompanionPanelDestination.GameDetails
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Leaderboard
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Library
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Lobby
import com.agustin.tarati.ui.layout.CompanionPanelDestination.MpGameDetail
import com.agustin.tarati.ui.layout.CompanionPanelDestination.MpLeaderboard
import com.agustin.tarati.ui.layout.CompanionPanelDestination.MpLobby
import com.agustin.tarati.ui.layout.CompanionPanelDestination.None
import com.agustin.tarati.ui.layout.CompanionPanelDestination.OnlineSettings
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Profile
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Settings
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Store
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Supporter
import com.agustin.tarati.ui.layout.CompanionPanelDestination.TournamentDetail
import com.agustin.tarati.ui.layout.CompanionPanelHeader
import com.agustin.tarati.ui.layout.DisplayMode
import com.agustin.tarati.ui.layout.LocalCompanionPanelController
import com.agustin.tarati.ui.layout.LocalScreenLayout
import com.agustin.tarati.ui.layout.ScreenLayout
import com.agustin.tarati.ui.layout.screenLayoutFor
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.PaletteManager
import com.agustin.tarati.ui.theme.TaratiTheme
import com.agustin.tarati.ui.theme.availablePalettes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Composable raíz compartido entre Android, Desktop y Web.
 *
 * ## Layouts adaptativos
 * - [ScreenLayout.Compact] / [ScreenLayout.Medium]: [NavGraph] ocupa toda la pantalla.
 * - [ScreenLayout.Expanded]: tablero siempre visible a la izquierda; panel lateral
 *   ([CompanionPane]) a la derecha cuando hay un destino activo. Las acciones de
 *   [GameScreen] (Lobby, Settings, Librería) populan el panel en lugar de navegar.
 *
 * ## KMP
 * Usa koinViewModel<ISettingsViewModel>() — Koin resuelve la implementación concreta
 * según el módulo registrado en cada plataforma.
 */
@Composable
fun AppContent(
    settingsViewModel: ISettingsViewModel = koinViewModel<SettingsViewModel>(),
) {
    val settings by settingsViewModel.settingsState.collectAsState()

    setCurrentPalette(settings.palette)

    val useDarkTheme = when (settings.appTheme) {
        AppTheme.MODE_AUTO -> isSystemInDarkTheme()
        AppTheme.MODE_DAY -> false
        AppTheme.MODE_NIGHT -> true
    }

    val companion = remember { CompanionPanelController() }

    // Modo de juego (single/multi) a nivel de app: disponible tanto en el NavGraph como en el panel
    // lateral (Settings/Tienda), para que sus previews de tablero se adapten al modo activo.
    val gameModeController = rememberSaveable(saver = GameModeController.Saver) { GameModeController() }

    // Ancho redimensionable del panel lateral (Expanded). El valor persistido se
    // adopta mientras el usuario no esté arrastrando; al soltar, se guarda.
    val settingsRepo: SettingsRepository = koinInject()
    val persistedPanelWidth by settingsRepo.companionPanelWidth
        .collectAsState(SettingsRepository.COMPANION_PANEL_DEFAULT_WIDTH)
    var draggedPanelWidth by remember { mutableStateOf<Float?>(null) }
    val panelWidth = draggedPanelWidth ?: persistedPanelWidth
    val density = LocalDensity.current
    val panelScope = rememberCoroutineScope()

    val authViewModel: IAuthViewModel = koinInject()
    val connectionViewModel: IConnectionViewModel = koinInject()
    val authState by authViewModel.authState.collectAsState()

    var showLoginModal by remember { mutableStateOf(false) }
    var pendingLoginAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val loginScope = rememberCoroutineScope()

    val onShowLogin: ((suspend () -> Unit)?) -> Unit = if (FeatureFlags.ONLINE_ENABLED) {
        { action ->
            pendingLoginAction = action
            showLoginModal = true
        }
    } else { _ -> }

    // Al autenticarse, precarga el nombre local con el displayName de la cuenta.
    // El usuario puede cambiarlo después sin afectar su cuenta online.
    if (FeatureFlags.ONLINE_ENABLED) {
        LaunchedEffect(authState) {
            val state = authState
            if (state is AuthState.Authenticated && !state.userInfo.isGuest) {
                val name = state.userInfo.displayName.takeIf { it.isNotBlank() }
                    ?: state.userInfo.username
                settingsViewModel.setUserName(name)
            }
        }
    }

    TaratiTheme(darkTheme = useDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val layout = screenLayoutFor(maxWidth)
                CompositionLocalProvider(
                    LocalScreenLayout provides layout,
                    LocalCompanionPanelController provides companion,
                    LocalGameModeController provides gameModeController,
                ) {
                    // Al cambiar de modo (single↔multi), alinear los lobbies del panel lateral al modo
                    // nuevo — el board primario ya cambia solo. `syncLobbyMode` reescribe el lobby tanto
                    // en el destino visible como en el back-stack, de modo que con una sub-pantalla abierta
                    // (p. ej. el detalle de una partida) el `back` vuelve al lobby del modo actual y no al
                    // del anterior. Solo aplica en Expanded (el panel solo existe ahí); el resto de
                    // destinos (Settings, Tienda, Leaderboard…) son compartidos y no se mueven.
                    if (layout == ScreenLayout.Expanded) {
                        LaunchedEffect(gameModeController.mode) {
                            companion.syncLobbyMode(multi = gameModeController.mode == GameMode.MULTI)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (layout == ScreenLayout.Expanded) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    NavGraph(
                                        settingsViewModel = settingsViewModel,
                                        onShowLogin = onShowLogin,
                                    )
                                }
                                if (companion.isOpen) {
                                    ResizablePanelDivider(
                                        onDrag = { deltaPx ->
                                            val deltaDp = with(density) { deltaPx.toDp().value }
                                            // El panel está a la derecha: arrastrar hacia la
                                            // izquierda (delta negativo) lo ensancha.
                                            draggedPanelWidth = (panelWidth - deltaDp).coerceIn(
                                                SettingsRepository.COMPANION_PANEL_MIN_WIDTH,
                                                SettingsRepository.COMPANION_PANEL_MAX_WIDTH,
                                            )
                                        },
                                        onDragStopped = {
                                            draggedPanelWidth?.let { w ->
                                                panelScope.launch { settingsRepo.setCompanionPanelWidth(w) }
                                            }
                                        },
                                    )
                                    Box(modifier = Modifier.width(panelWidth.dp).fillMaxHeight()) {
                                        CompanionPane(
                                            controller = companion,
                                            settingsViewModel = settingsViewModel,
                                            onShowLogin = onShowLogin,
                                        )
                                    }
                                }
                            }
                        } else {
                            NavGraph(
                                settingsViewModel = settingsViewModel,
                                onShowLogin = onShowLogin,
                            )
                        }
                        ToastHost(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        AlertHost()
                        if (FeatureFlags.ONLINE_ENABLED) ChallengeNotificationEffect()
                        if (FeatureFlags.ONLINE_ENABLED) TournamentNotificationEffect()
                        if (FeatureFlags.ONLINE_ENABLED) MpTableInviteNotificationEffect()
                        if (showLoginModal) {
                            LoginSheet(
                                onLoginSuccess = {
                                    showLoginModal = false
                                    // Re-conectar con las nuevas credenciales (upgrade desde invitado)
                                    val newToken = authViewModel.accessToken
                                    if (newToken != null) {
                                        loginScope.launch {
                                            if (connectionViewModel.isConnected) connectionViewModel.disconnect()
                                            connectionViewModel.connectToServer(devServerUrl, newToken)
                                        }
                                    }
                                    val action = pendingLoginAction
                                    pendingLoginAction = null
                                    if (action != null) loginScope.launch { action() }
                                },
                                onDismiss = {
                                    showLoginModal = false
                                    pendingLoginAction = null
                                },
                                authViewModel = authViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Companion panel ───────────────────────────────────────────────────────────

/**
 * Divisor vertical arrastrable entre el área principal y el panel lateral.
 *
 * Expone una zona de agarre más ancha que la línea visible para facilitar el
 * arrastre. [onDrag] recibe el delta horizontal en píxeles; [onDragStopped] se
 * invoca al soltar para persistir el ancho resultante.
 */
@Composable
private fun ResizablePanelDivider(
    onDrag: (deltaPx: Float) -> Unit,
    onDragStopped: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDrag(delta) },
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider()
        // Grip central: pastilla con tres puntos que indica que el panel es redimensionable.
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(3.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(2.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * Renderiza el destino activo del panel lateral.
 *
 * Cada pantalla recibe callbacks que navegan dentro del panel ([controller.navigate])
 * o lo cierran ([controller.close]), en lugar de delegar en el [NavGraph] global.
 *
 * Las pantallas aún sin [DisplayMode.CompanionPanel] optimizado renderizan su propio
 * Scaffold dentro del panel — funcionalmente correcto, visualmente mejorable en fases
 * siguientes agregando el parámetro `displayMode` a cada una.
 */
@Composable
private fun CompanionPane(
    controller: CompanionPanelController,
    settingsViewModel: ISettingsViewModel,
    onShowLogin: ((suspend () -> Unit)?) -> Unit = {},
    gameViewModel: IGameModel = injectGameViewModel(),
    authViewModel: IAuthViewModel = koinInject(),
    gamesLibraryViewModel: IGamesLibraryViewModel = koinViewModel<GamesLibraryViewModel>(),
    onlineLobbyViewModel: IOnlineLobbyViewModel = koinViewModel<OnlineLobbyViewModel>(),
    gameDetailsViewModel: IGameDetailsViewModel = koinViewModel<GameDetailsViewModel>(),
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    clipboardHelper: GameClipboardHelper = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val gameStatus by gameViewModel.gameStatus.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val loggedInUsername = (authState as? AuthState.Authenticated)?.userInfo?.username

    // Tab del Lobby activo al momento de navegar a una subpantalla (TournamentDetail, etc.)
    // Permite restaurar el tab correcto al volver con controller.back().
    var lastLobbyTab by remember { mutableIntStateOf(0) }

    when (val dest = controller.destination) {
        None -> Unit

        MpLobby -> MpLobbyScreen(
            displayMode = DisplayMode.CompanionPanel,
            onBack = controller::close,
            // Consistencia con el lobby single online (`onMatchFound` no-op): al arrancar la partida el
            // panel NO se cierra — el tablero se muestra en el primario y el lobby queda abierto para
            // crear/unirse a otra mesa. Se cierra solo con la X o re-tocando el botón Online (toggle).
            onGameStarted = { },
            onShowLogin = { onShowLogin(null) },
            onNavigateToSupporter = { controller.navigate(Supporter) },
            onNavigateToLeaderboard = { controller.navigate(MpLeaderboard) },
            onNavigateToProfile = { userId -> controller.navigate(Profile(userId)) },
            onOpenGame = { gameId -> controller.navigate(MpGameDetail(gameId)) },
        )

        Lobby -> OnlineLobbyScreen(
            displayMode = DisplayMode.CompanionPanel,
            onBack = controller::close,
            onShowLogin = { onShowLogin(null) },
            onMatchFound = { /* no-op: el tablero en el panel primario ya muestra la partida */ },
            onSpectateGame = { /* no-op: el tablero en el panel primario ya muestra el espectado */ },
            onLeaderboard = { controller.navigate(Leaderboard) },
            onNavigateToProfile = { userId ->
                controller.navigate(Profile(userId))
            },
            onNavigateToGameDetails = { gameId ->
                scope.launch {
                    val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                    if (matchDto != null) {
                        gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                        controller.navigate(GameDetails(gameId))
                    }
                }
            },
            onNavigateToTournament = { tournamentId ->
                lastLobbyTab = 2 // Tab "Torneos"
                controller.navigate(TournamentDetail(tournamentId))
            },
            onNavigateToSupporter = {
                controller.navigate(Supporter)
            },
            initialTab = lastLobbyTab,
        )

        Leaderboard -> LeaderboardScreen(
            onBack = controller::back,
            onNavigateToProfile = { userId ->
                controller.navigate(Profile(userId))
            },
        )

        MpLeaderboard -> MpLeaderboardScreen(
            onBack = controller::back,
            onNavigateToProfile = { userId ->
                controller.navigate(Profile(userId))
            },
        )

        is MpGameDetail -> key(dest.gameId) {
            MpGameDetailScreen(
                gameId = dest.gameId,
                onBack = controller::back,
                settingsViewModel = settingsViewModel,
            )
        }

        is Profile -> key(dest.userId) {
            PublicProfileScreen(
                userId = dest.userId,
                onBack = controller::back,
                // El desafío 2-jugadores solo aplica en single; en MULTI se oculta (consistencia de modo).
                allowChallenge = LocalGameModeController.current?.mode != GameMode.MULTI,
                onNavigateToGameDetails = { gameId ->
                    scope.launch {
                        val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                        if (matchDto != null) {
                            gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                            controller.navigate(GameDetails(gameId))
                        }
                    }
                },
                onNavigateToMpGameDetails = { gameId ->
                    controller.navigate(MpGameDetail(gameId))
                },
            )
        }

        Settings -> LanguageAwareSettingsScreen(
            viewModel = settingsViewModel,
            events = remember(settingsViewModel) { settingsEvents(settingsViewModel) },
            isGameActive = gameStatus == GameStatus.PLAYING,
            onNavigateBack = controller::close,
            loggedInUsername = loggedInUsername,
            onLogout = if (authViewModel.isAuthenticated) {
                {
                    scope.launch {
                        authViewModel.logout()
                        controller.close()
                    }
                }
            } else null,
            onNavigateToOnlineSettings = {
                controller.navigate(OnlineSettings)
            },
            onNavigateToAchievements = {
                controller.navigate(Achievements)
            },
            onNavigateToSupporter = {
                controller.navigate(Supporter)
            },
            onNavigateToStore = {
                controller.navigate(Store)
            },
        )

        Achievements -> AchievementsScreen(onBack = controller::back)

        Analysis -> {
            val analysisGameState by gameViewModel.gameState.collectAsState()
            val onlineGame by onlineGameViewModel.currentGame.collectAsState()
            // Retenido tras el fin de la online/espectada: el análisis resuelve por el detalle remoto
            // (jugadores reales) aunque onlineGame/spectatingState ya se hayan limpiado.
            val lastFinishedOnlineGameId by onlineGameViewModel.lastFinishedGameId.collectAsState()
            // Partida espectada en curso: su gameId también resuelve el detalle remoto.
            val spectatingState by onlineGameViewModel.spectatingState.collectAsState()
            // Labels reales por-banda para el detalle temporal offline (IA (Dificultad) / nombre
            // de usuario / Humano), en paridad con GameScreen — así el PGN copiado refleja el tipo
            // de jugador correcto, incluidas partidas IA-vs-IA.
            val whiteIsAI by gameViewModel.whiteIsAI.collectAsState()
            val blackIsAI by gameViewModel.blackIsAI.collectAsState()
            val playerSide by gameViewModel.playerSide.collectAsState()
            val userName by gameViewModel.userName.collectAsState()
            val difficultyWhite by gameViewModel.difficultyWhite.collectAsState()
            val difficultyBlack by gameViewModel.difficultyBlack.collectAsState()
            val aiLabel = stringResource(Res.string.player_ai)
            val humanLabel = stringResource(Res.string.player_human)
            val whiteLabel = buildPlayerLabel(
                aiLabel = aiLabel,
                humanLabel = humanLabel,
                difficultyLabel = stringResource(difficultyWhite.displayNameRes),
                isAI = whiteIsAI,
                isCurrentUser = playerSide == CobColor.WHITE,
                userName = userName,
            )
            val blackLabel = buildPlayerLabel(
                aiLabel = aiLabel,
                humanLabel = humanLabel,
                difficultyLabel = stringResource(difficultyBlack.displayNameRes),
                isAI = blackIsAI,
                isCurrentUser = playerSide == CobColor.BLACK,
                userName = userName,
            )
            Column(modifier = Modifier.fillMaxSize()) {
                CompanionPanelHeader(
                    title = localizedString(Res.string.analysis_title),
                    // back (no close): cerrar el análisis vuelve al panel de origen (Lobby, Perfil…).
                    onClose = controller::back,
                )
                AnalysisPanel(
                    gameState = analysisGameState,
                    onOpenFullAnalysis = {
                        scope.launch {
                            openGameAnalysis(
                                onlineGameId = onlineGame?.gameId
                                    ?: spectatingState?.gameId
                                    ?: lastFinishedOnlineGameId,
                                buildOfflineMatch = {
                                    gameViewModel.exportGameToMatchDto(whiteLabel, blackLabel)
                                },
                                loadOnlineMatch = onlineLobbyViewModel::loadAndPreviewGame,
                                updateCurrentMatch = gameDetailsViewModel::updateCurrentMatchDto,
                                navigateToDetails = { id -> controller.navigate(GameDetails(id)) },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        OnlineSettings -> OnlineSettingsScreen(
            onNavigateBack = controller::back,
        )

        Supporter -> SupporterScreen(
            onNavigateBack = controller::back,
        )

        Store -> StoreScreen(
            settingsViewModel = settingsViewModel,
            onNavigateToSupporter = { controller.navigate(Supporter) },
            onNavigateBack = controller::back,
        )

        Library -> GamesLibraryScreen(
            onGameSelected = { gameId ->
                controller.navigate(GameDetails(gameId))
            },
            onBack = controller::close,
            viewModel = gamesLibraryViewModel,
        )

        is TournamentDetail -> key(dest.tournamentId) {
            TournamentDetailScreen(
                tournamentId = dest.tournamentId,
                onBack = controller::back,
                displayMode = DisplayMode.CompanionPanel,
                onSpectateGame = { _ -> },
                onNavigateToGameDetails = { gameId ->
                    scope.launch {
                        val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                        if (matchDto != null) {
                            gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                            controller.navigate(GameDetails(gameId))
                        }
                    }
                },
            )
        }

        is GameDetails -> GameDetailsScreen(
            gameId = dest.gameId,
            onImport = { matchDto ->
                gameViewModel.importGameFromMatchDto(matchDto)
                controller.close()
            },
            onCopyMoveHistory = { matchDto ->
                scope.launch { clipboardHelper.copyMatch(matchDto) }
            },
            onBack = controller::back,
            viewModel = gameDetailsViewModel,
        )
    }
}

// ── Challenge notifications ───────────────────────────────────────────────────

/**
 * Collector global de eventos de desafío directo.
 *
 * Corre en la raíz de la composición (AppContent) para que las notificaciones
 * de challenge sean visibles desde cualquier pantalla, no solo desde GameScreen.
 */
@Composable
private fun ChallengeNotificationEffect(
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val challengeDeclinedMsg = localizedString(Res.string.social_challenge_declined)
    val challengeExpiredMsg = localizedString(Res.string.social_challenge_expired)

    LaunchedEffect(Unit) {
        onlineGameViewModel.challengeEvents.collect { event ->
            when (event) {
                is ChallengeEvent.Received -> {
                    val tcDisplay = event.timeControl.replaceFirstChar { it.titlecase() }
                    val ratedDisplay = if (event.rated) "·" else ""
                    bus.alert { dismiss ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = localizedString(Res.string.social_challenge_from)
                                    .replace($$"%1$s", event.challengerInfo.username),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = localizedString(Res.string.social_challenge_invite)
                                    .replace($$"%1$s", event.challengerInfo.username)
                                    .replace($$"%2$s", "$tcDisplay $ratedDisplay"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(onClick = {
                                    scope.launch {
                                        onlineGameViewModel.respondToChallenge(event.challengeId, true)
                                    }
                                    dismiss()
                                }) {
                                    Text(localizedString(Res.string.accept))
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        onlineGameViewModel.respondToChallenge(event.challengeId, false)
                                    }
                                    dismiss()
                                }) {
                                    Text(localizedString(Res.string.cancel))
                                }
                            }
                        }
                    }
                }

                is ChallengeEvent.Declined -> bus.toast(
                    UIMessage.Toast(challengeDeclinedMsg)
                )

                is ChallengeEvent.Expired -> bus.toast(
                    UIMessage.Toast(challengeExpiredMsg)
                )
            }
        }
    }
}

/**
 * Collector global de eventos de torneo.
 *
 * Corre en la raíz de la composición para que las notificaciones de torneo
 * sean visibles desde cualquier pantalla.
 */
@Composable
private fun TournamentNotificationEffect(
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val gameAssignedTemplate = localizedString(Res.string.tournament_game_assigned)
    val tournamentFinishedMsg = localizedString(Res.string.tournament_finished_notification)
    val tournamentCancelledMsg = localizedString(Res.string.tournament_cancelled_notification)

    LaunchedEffect(Unit) {
        onlineGameViewModel.tournamentEvents.collect { event ->
            when (event) {
                is TournamentEvent.GameAssigned -> bus.toast(
                    UIMessage.Toast(
                        gameAssignedTemplate
                            .replace($$"%1$s", event.tournamentName)
                            .replace($$"%2$d", "${event.round}")
                            .replace($$"%3$d", "${event.totalRounds}")
                    )
                )

                is TournamentEvent.Finished -> bus.toast(
                    UIMessage.Toast(tournamentFinishedMsg)
                )

                is TournamentEvent.RoundStarted -> Unit
                is TournamentEvent.StandingsUpdated -> Unit
                is TournamentEvent.Cancelled -> bus.toast(UIMessage.Toast(tournamentCancelledMsg))
            }
        }
    }
}

/**
 * Collector global de invitaciones a mesas multijugador (dirigidas).
 *
 * Corre en la raíz de la composición para que la invitación sea visible desde cualquier pantalla
 * (espejo de [ChallengeNotificationEffect] pero para el lobby de mesas MP). Inyecta el
 * [MpLobbyViewModel] singleton, así comparte el flujo con la pantalla del lobby.
 */
@Composable
private fun MpTableInviteNotificationEffect(
    mpLobbyViewModel: MpLobbyViewModel = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val bodyTemplate = localizedString(Res.string.game6_invite_body)
    val declinedMsg = localizedString(Res.string.game6_invite_declined)
    val expiredMsg = localizedString(Res.string.game6_invite_expired)

    LaunchedEffect(Unit) {
        mpLobbyViewModel.tableInvites.collect { invite ->
            bus.alert { dismiss ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = localizedString(Res.string.game6_invite_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = bodyTemplate
                            .replace($$"%1$s", invite.inviterName)
                            .replace($$"%2$d", "${invite.playerCount}"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch { mpLobbyViewModel.respondToInvite(invite.inviteId, true) }
                            dismiss()
                        }) {
                            Text(localizedString(Res.string.accept))
                        }
                        TextButton(onClick = {
                            scope.launch { mpLobbyViewModel.respondToInvite(invite.inviteId, false) }
                            dismiss()
                        }) {
                            Text(localizedString(Res.string.cancel))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        mpLobbyViewModel.inviteResolved.collect { outcome ->
            val msg = if (outcome == "declined") declinedMsg else expiredMsg
            bus.toast(UIMessage.Toast(msg))
        }
    }
}

fun setCurrentPalette(paletteName: String) {
    val currentPalette =
        availablePalettes.find { it.name == paletteName }
            ?: availablePalettes.first()
    PaletteManager.setPalette(currentPalette)
}
