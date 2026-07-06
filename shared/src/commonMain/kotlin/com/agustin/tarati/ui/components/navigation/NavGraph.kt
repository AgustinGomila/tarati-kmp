package com.agustin.tarati.ui.components.navigation


import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.features.achievements.AchievementsScreen
import com.agustin.tarati.features.detail.GameDetailsScreen
import com.agustin.tarati.features.detail.GameDetailsViewModel
import com.agustin.tarati.features.detail.IGameDetailsViewModel
import com.agustin.tarati.features.game.GameScreen
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.game6.GameMode
import com.agustin.tarati.features.game6.GameModeController
import com.agustin.tarati.features.game6.LocalGameModeController
import com.agustin.tarati.features.game6.MpGameScreen
import com.agustin.tarati.features.game6.MpLobbyScreen
import com.agustin.tarati.features.game6.MpLocalGameViewModel
import com.agustin.tarati.features.library.GamesLibraryScreen
import com.agustin.tarati.features.library.GamesLibraryViewModel
import com.agustin.tarati.features.library.IGamesLibraryViewModel
import com.agustin.tarati.features.online.auth.AuthState
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
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
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.features.store.StoreScreen
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.ui.components.game.animation.BoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.BoardGeometryViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardGeometryViewModel
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.AchievementsDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.GameDetailsDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.GameScreenDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.GamesLibraryDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.LeaderboardDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.OnlineSettingsDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.PublicProfileDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.SettingsScreenDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.SplashScreenDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.StoreDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.SupporterDest
import com.agustin.tarati.ui.components.navigation.ScreenDestinations.TournamentDetailDest
import com.agustin.tarati.ui.layout.CompanionPanelDestination
import com.agustin.tarati.ui.layout.LocalCompanionPanelController
import com.agustin.tarati.ui.layout.LocalScreenLayout
import com.agustin.tarati.ui.layout.ScreenLayout
import com.agustin.tarati.ui.settingsEvents
import com.agustin.tarati.ui.splash.SplashScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NavGraph(
    onShowLogin: ((suspend () -> Unit)?) -> Unit = {},
    gameViewModel: IGameModel = injectGameViewModel(),
    animationViewModel: IBoardAnimationViewModel = koinViewModel<BoardAnimationViewModel>(),
    geometryViewModel: IBoardGeometryViewModel = koinViewModel<BoardGeometryViewModel>(),
    gamesLibraryViewModel: IGamesLibraryViewModel = koinViewModel<GamesLibraryViewModel>(),
    gameDetailsViewModel: IGameDetailsViewModel = koinViewModel<GameDetailsViewModel>(),
    settingsViewModel: ISettingsViewModel = koinViewModel<SettingsViewModel>(),
    onlineLobbyViewModel: IOnlineLobbyViewModel = koinViewModel<OnlineLobbyViewModel>(),
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
    clipboardHelper: GameClipboardHelper = koinInject(),
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Estado de transferencia entre el lobby y GameScreen.
    val pendingMatchmaking = remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // VM del multijugador local: vive a nivel del **body del NavGraph** (no dentro del composable de
    // GameScreenDest) para que la partida en curso y la config del Sidebar (nº jugadores, humano/IA)
    // sobrevivan tanto el cambio de modo Single↔Multi como la navegación a otras pantallas
    // (Settings/Logros) y vuelta — igual que el juego single vive en `gameViewModel` (scope Activity).
    val mpViewModel = remember { MpLocalGameViewModel() }

    NavHost(
        navController = navController,
        startDestination = SplashScreenDest.route,
    ) {
        composable(route = SplashScreenDest.route) {
            // El juego es totalmente offline — siempre arranca en GameScreen.
            SplashScreen {
                navController.navigate(GameScreenDest.route) {
                    popUpTo(SplashScreenDest.route) { inclusive = true }
                }
            }
        }

        composable(route = GameScreenDest.route) {
            // El modo de juego vive a nivel de app (AppContent, `LocalGameModeController`); acá se lee.
            // El fallback es defensivo — AppContent siempre lo provee.
            val gameModeController = LocalGameModeController.current
                ?: rememberSaveable(saver = GameModeController.Saver) { GameModeController() }
            CompositionLocalProvider(LocalGameModeController provides gameModeController) {
                val layout = LocalScreenLayout.current
                val companion = LocalCompanionPanelController.current

                when (gameModeController.mode) {
                    GameMode.MULTI -> MpGameScreen(
                        viewModel = mpViewModel,
                        // Mismo comportamiento que Single: en Expanded (web/tablet) despliega en el
                        // panel lateral derecho; en compacto navega a pantalla completa.
                        onNavigateToSettings = {
                            if (layout == ScreenLayout.Expanded)
                                companion.toggle(CompanionPanelDestination.Settings)
                            else
                                navController.navigate(SettingsScreenDest.route)
                        },
                        onNavigateToAchievements = {
                            if (layout == ScreenLayout.Expanded)
                                companion.toggle(CompanionPanelDestination.Achievements)
                            else
                                navController.navigate(AchievementsDest.route)
                        },
                        // En modo Multi, "Online" abre el lobby de **mesas** MP (no el matchmaking 2p):
                        // en Expanded (web/tablet) en el panel lateral; en compacto a pantalla completa.
                        onNavigateToOnline = {
                            val openMpLobby: () -> Unit = {
                                if (layout == ScreenLayout.Expanded)
                                    companion.toggle(CompanionPanelDestination.MpLobby)
                                else
                                    navController.navigate(ScreenDestinations.MpLobbyDest.route)
                            }
                            // El modo online multijugador exige sesión: sin ella, se muestra el login
                            // sheet **sobre el tablero** (sin pre-acceder a la UI de mesas) y solo tras
                            // el acceso se abre el lobby. Si se cierra el modal, se queda en el tablero.
                            if (authViewModel.isAuthenticated) openMpLobby()
                            else onShowLogin { openMpLobby() }
                        },
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )

                    GameMode.SINGLE -> {
                        // En Expanded, las acciones de navegación populan el panel lateral en lugar
                        // de navegar en el NavGraph — el tablero permanece visible en todo momento.
                        GameScreen(
                            viewModel = gameViewModel,
                            animationViewModel = animationViewModel,
                            geometryViewModel = geometryViewModel,
                            settingsViewModel = settingsViewModel,
                            onNavigateToSettings = {
                                if (layout == ScreenLayout.Expanded)
                                    companion.toggle(CompanionPanelDestination.Settings)
                                else
                                    navController.navigate(SettingsScreenDest.route)
                            },
                            onNavigateToAchievements = {
                                if (layout == ScreenLayout.Expanded)
                                    companion.toggle(CompanionPanelDestination.Achievements)
                                else
                                    navController.navigate(AchievementsDest.route)
                            },
                            onGamesLibrary = {
                                if (layout == ScreenLayout.Expanded)
                                    companion.toggle(CompanionPanelDestination.Library)
                                else
                                    navController.navigate(GamesLibraryDest.route)
                            },
                            onOnlineLobby = {
                                if (layout == ScreenLayout.Expanded)
                                    companion.toggle(CompanionPanelDestination.Lobby)
                                else
                                    navController.navigate(ScreenDestinations.OnlineLobbyDest.route)
                            },
                            onSaveGame = { match -> gamesLibraryViewModel.saveCurrentGame(match) },
                            onNavigateToLogin = { suspendAction -> onShowLogin(suspendAction) },
                            initialMatchmaking = pendingMatchmaking.value.also { pendingMatchmaking.value = null },
                        )
                    }
                }
            }
        }

        composable(route = SettingsScreenDest.route) {
            val gameStatus by gameViewModel.gameStatus.collectAsState()
            val currentUser by authViewModel.authState.collectAsState()
            LanguageAwareSettingsScreen(
                viewModel = settingsViewModel,
                events = remember(settingsViewModel) { settingsEvents(settingsViewModel) },
                isGameActive = gameStatus == GameStatus.PLAYING,
                onNavigateBack = { navController.popBackStack() },
                loggedInUsername = (currentUser as? AuthState.Authenticated)
                    ?.userInfo?.username,
                onLogout = if (authViewModel.isAuthenticated) {
                    {
                        scope.launch {
                            authViewModel.logout()
                            navController.popBackStack()
                        }
                    }
                } else null,
                onNavigateToOnlineSettings = {
                    navController.navigate(OnlineSettingsDest.route)
                },
                onNavigateToAchievements = {
                    navController.navigate(AchievementsDest.route)
                },
                onNavigateToSupporter = {
                    navController.navigate(SupporterDest.route)
                },
                onNavigateToStore = {
                    navController.navigate(StoreDest.route)
                },
            )
        }

        composable(route = OnlineSettingsDest.route) {
            OnlineSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = StoreDest.route) {
            StoreScreen(
                settingsViewModel = settingsViewModel,
                onNavigateToSupporter = { navController.navigate(SupporterDest.route) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = SupporterDest.route) {
            SupporterScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(GamesLibraryDest.route) {
            GamesLibraryScreen(
                onGameSelected = { gameId ->
                    navController.navigate("game_details/$gameId")
                },
                onBack = { navController.popBackStack() },
                viewModel = gamesLibraryViewModel,
            )
        }

        composable(GameDetailsDest.route) { backStackEntry ->
            val gameId = backStackEntry.arguments?.read {
                getString("gameId")
            } ?: ""
            GameDetailsScreen(
                gameId = gameId,
                onImport = { matchDto ->
                    gameViewModel.importGameFromMatchDto(matchDto)
                    navController.navigate(GameScreenDest.route) {
                        popUpTo(GameScreenDest.route) { inclusive = true }
                    }
                },
                onCopyMoveHistory = { matchDto ->
                    scope.launch {
                        clipboardHelper.copyMoveHistory(
                            moves = matchDto.game.moveHistory,
                            gameState = gameViewModel.gameState.value,
                            playerSide = gameViewModel.playerSide.value,
                            aiEnabled = gameViewModel.aIEnabled.value,
                        )
                    }
                },
                onBack = { navController.popBackStack() },
                viewModel = gameDetailsViewModel,
            )
        }

        composable(ScreenDestinations.OnlineLobbyDest.route) {
            OnlineLobbyScreen(
                onBack = { navController.popBackStack() },
                onShowLogin = { onShowLogin(null) },
                onSpectateGame = { _ ->
                    navController.popBackStack()
                },
                onLeaderboard = { navController.navigate(LeaderboardDest.route) },
                onNavigateToProfile = { userId ->
                    navController.navigate(PublicProfileDest.createRoute(userId))
                },
                onNavigateToGameDetails = { gameId ->
                    // Pre-cargar el detalle desde el servidor antes de navegar.
                    // loadAndPreviewGame obtiene el Game y lo convierte a MatchDto;
                    // updateCurrentMatchDto lo deja disponible para GameDetailsScreen
                    // sin necesidad de una segunda llamada HTTP.
                    scope.launch {
                        val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                        if (matchDto != null) {
                            gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                            navController.navigate("game_details/$gameId")
                        }
                    }
                },
                onNavigateToTournament = { tournamentId ->
                    navController.navigate(TournamentDetailDest.createRoute(tournamentId))
                },
                onNavigateToSupporter = { navController.navigate(SupporterDest.route) },
                viewModel = onlineLobbyViewModel,
            )
        }

        composable(ScreenDestinations.MpLobbyDest.route) {
            MpLobbyScreen(
                onBack = { navController.popBackStack() },
                // Al arrancar la partida, volver al tablero (GameScreenDest → MpGameScreen la muestra).
                onGameStarted = { navController.popBackStack() },
            )
        }

        composable(LeaderboardDest.route) {
            LeaderboardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProfile = { userId ->
                    navController.navigate(PublicProfileDest.createRoute(userId))
                },
            )
        }

        composable(PublicProfileDest.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.read { getString("userId") } ?: return@composable
            PublicProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onNavigateToGameDetails = { gameId ->
                    scope.launch {
                        val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                        if (matchDto != null) {
                            gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                            navController.navigate("game_details/$gameId")
                        }
                    }
                },
            )
        }

        composable(AchievementsDest.route) {
            AchievementsScreen(onBack = { navController.popBackStack() })
        }

        composable(TournamentDetailDest.route) { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.read {
                getString("tournamentId")
            } ?: return@composable
            TournamentDetailScreen(
                tournamentId = tournamentId,
                onBack = { navController.popBackStack() },
                onSpectateGame = { _ -> },
                onNavigateToGameDetails = { gameId ->
                    scope.launch {
                        val matchDto = onlineLobbyViewModel.loadAndPreviewGame(gameId)
                        if (matchDto != null) {
                            gameDetailsViewModel.updateCurrentMatchDto(matchDto)
                            navController.navigate("game_details/$gameId")
                        }
                    }
                },
            )
        }
    }
}
