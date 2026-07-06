package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.auth_logout
import com.agustin.tarati.shared.generated.resources.auth_logout_confirm
import com.agustin.tarati.shared.generated.resources.auth_sign_in
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.confirm
import com.agustin.tarati.shared.generated.resources.connect_to_server_first
import com.agustin.tarati.shared.generated.resources.could_not_connect
import com.agustin.tarati.shared.generated.resources.lobby_connected_tab
import com.agustin.tarati.shared.generated.resources.lobby_in_live
import com.agustin.tarati.shared.generated.resources.lobby_my_games
import com.agustin.tarati.shared.generated.resources.lobby_new_search
import com.agustin.tarati.shared.generated.resources.lobby_not_connected_to_server
import com.agustin.tarati.shared.generated.resources.online_lobby
import com.agustin.tarati.shared.generated.resources.profile_leaderboard
import com.agustin.tarati.shared.generated.resources.search_no_longer_available
import com.agustin.tarati.shared.generated.resources.social_feed
import com.agustin.tarati.shared.generated.resources.spectator_unavailable
import com.agustin.tarati.shared.generated.resources.supporter_title
import com.agustin.tarati.shared.generated.resources.tournaments
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.layout.CompanionPanelHeader
import com.agustin.tarati.ui.layout.DisplayMode
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla de lobby online.
 *
 * ## Tabs
 *
 * ### En Vivo
 * Lista intercalada de partidas en curso y búsquedas abiertas, refrescada cada 5 s.
 * Filtros (chips): "En Vivo" / "Buscando". Ordenamiento: Más recientes / Más antiguos / Rating.
 * Botón 🔍 en la TopBar abre el [NewSearchSheet] para crear una búsqueda propia.
 *
 * ### Mis Partidas
 * Historial paginado con filtros por time control, resultado y tipo.
 *
 * @param onBack          Navega hacia atrás. También usado para ir al GameScreen cuando la partida se forma.
 * @param viewModel       ViewModel inyectado via Koin.
 * @param connectionViewModel    Singleton de conexión inyectado via Koin.
 * @param onlineGameViewModel    Singleton de juego online inyectado via Koin.
 * @param authViewModel          ViewModel de autenticación inyectado via Koin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLobbyScreen(
    onBack: () -> Unit,
    displayMode: DisplayMode = DisplayMode.FullScreen,
    /** Abre el modal de login/registro. */
    onShowLogin: () -> Unit = {},
    /**
     * Llamado cuando se forma una partida propia. Por defecto usa [onBack].
     * En [DisplayMode.CompanionPanel] se puede pasar un no-op para que el panel
     * permanezca abierto — el tablero ya muestra la partida en el panel primario.
     */
    onMatchFound: (() -> Unit)? = null,
    /** Callback al tocar "Ver" en una partida en vivo. Null = feature no disponible en este contexto. */
    onSpectateGame: ((gameId: String) -> Unit)? = null,
    onLeaderboard: (() -> Unit)? = null,
    /** Callback al tocar un perfil de usuario en línea. Null = sin navegación al perfil. */
    onNavigateToProfile: ((userId: String) -> Unit)? = null,
    /** Callback al tocar una partida en el historial. Null = sin navegación al detalle. */
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
    /** Callback al tocar un torneo en el tab Torneos. Null = sin navegación al detalle. */
    onNavigateToTournament: ((tournamentId: String) -> Unit)? = null,
    /** Abre la pantalla Supporter (pago C3). Null = sin botón ♥ en la TopBar. */
    onNavigateToSupporter: (() -> Unit)? = null,
    /** Tab inicial a mostrar al entrar. Útil en CompanionPanel para restaurar el tab activo. */
    initialTab: Int = 0,
    viewModel: IOnlineLobbyViewModel = koinViewModel<OnlineLobbyViewModel>(),
    connectionViewModel: IConnectionViewModel = koinInject(),
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
    settings: SettingsRepository = koinInject(),
) {
    val authState by authViewModel.authState.collectAsState()
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val matchmakingState by onlineGameViewModel.matchmakingState.collectAsState()
    val currentGame by onlineGameViewModel.currentGame.collectAsState()
    val hasActiveGame = currentGame?.status == OnlineGameStatus.InProgress
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showMatchmakingSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val spectatorUnavailableMsg = localizedString(Res.string.spectator_unavailable)
    // True mientras el usuario inició una búsqueda desde este lobby (nueva o uniéndose a otra).
    // Evita que el LaunchedEffect de MatchFound dispare si el estado viene de una partida anterior.
    var searchStartedInLobby by remember { mutableStateOf(false) }

    // Rastrea si en esta sesión del lobby llegamos a estar Online alguna vez.
    // Cuando volvemos a Offline habiendo estado Online → el usuario hizo logout o la sesión expiró:
    // cerrar el panel en lugar de mostrar el mensaje "no conectado".
    var hasBeenOnline by remember { mutableStateOf(connectionState is ConnectionState.Online) }
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Online) hasBeenOnline = true
        if (connectionState is ConnectionState.Offline && hasBeenOnline) onBack()
    }

    val tabScrollState = rememberScrollState()

    // Inicializa en true si no hay sesión activa — evita flashear el mensaje "no conectado"
    // mientras el auto-connect está en progreso en el primer frame.
    var isAutoConnecting by remember {
        mutableStateOf(authState !is com.agustin.tarati.features.online.auth.AuthState.Authenticated)
    }

    // Auto-conexión como invitado al primer acceso al Lobby.
    LaunchedEffect(Unit) {
        if (authViewModel.authState.value !is com.agustin.tarati.features.online.auth.AuthState.Authenticated) {
            val settingsName = settings.userName.first().trim()
                .takeIf { n -> n.length in 3..20 && n.matches(Regex("[A-Za-z0-9_]+")) }
            val result = authViewModel.loginAsGuest(settingsName)
            if (result.isFailure && settingsName != null) {
                // Nombre tomado o inválido — reintentar con nombre aleatorio
                authViewModel.loginAsGuest()
            }
        }
        val token = authViewModel.accessToken
        if (token != null && !connectionViewModel.isConnected && !connectionViewModel.isConnecting) {
            connectionViewModel.connectToServer(devServerUrl, token)
        }
        isAutoConnecting = false
    }

    val connectToServerFirstMsg = localizedString(Res.string.connect_to_server_first)
    val couldNotConnectMsg = localizedString(Res.string.could_not_connect)
    val searchNoLongerAvailableMsg = localizedString(Res.string.search_no_longer_available)

    /**
     * Garantiza conexión WS activa antes de intentar matchmaking.
     * Espera hasta 5 s si hay una conexión en progreso.
     */
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
                } catch (e: Exception) {
                    Result.failure(Exception(couldNotConnectMsg))
                }
            }

            else -> {
                if (authViewModel.isTokenExpiringSoon()) authViewModel.refreshToken()
                val token = authViewModel.accessToken ?: authViewModel.getStoredToken()
                if (token == null) Result.failure(Exception(connectToServerFirstMsg))
                else connectionViewModel.connectToServer(devServerUrl, token).map { }
            }
        }
    }

    /**
     * Navegar al GameScreen cuando la partida está formada.
     * Unifica los dos caminos: nueva búsqueda propia y unirse a búsqueda ajena.
     */
    LaunchedEffect(matchmakingState) {
        if (matchmakingState is MatchmakingState.MatchFound && searchStartedInLobby) {
            searchStartedInLobby = false
            (onMatchFound ?: onBack).invoke()
        }
    }

    // Volver atrás si el lobby falla al cargar por primera vez (sin datos previos).
    // Un error con lista vacía indica un problema de conexión o configuración que
    // impide usar el lobby — no tiene sentido mostrar una pantalla vacía con error.
    val liveGamesError by viewModel.liveGames.collectAsState()
    LaunchedEffect(liveGamesError.error) {
        if (liveGamesError.error != null && liveGamesError.games.isEmpty()) {
            onBack()
        }
    }

    /**
     * Acepta directamente la búsqueda abierta de [targetUserId] (lógica estilo Lichess seek board).
     *
     * En lugar de entrar al queue general con el mismo TC y esperar al matchmaking worker,
     * envía [ClientMessage.JoinOpenSearch] para que el servidor cree la partida de inmediato
     * con ese jugador específico. Ambos reciben [ServerMessage.MatchFound] directamente.
     *
     * Si la búsqueda ya no existe (el jugador se fue o fue emparejado por el worker),
     * el servidor devuelve Error("search_not_found") y se resetea la búsqueda.
     */
    val handleJoinExistingSearch: (String, String, Boolean) -> Unit = { targetUserId, tc, rated ->
        scope.launch {
            val connResult = ensureConnected()
            if (connResult.isFailure) {
                snackbarHostState.showSnackbar(
                    message = connResult.exceptionOrNull()?.message ?: couldNotConnectMsg,
                    duration = SnackbarDuration.Long,
                )
                return@launch
            }
            // Marcar ANTES del suspend: MatchFound puede llegar durante el RTT del servidor.
            searchStartedInLobby = true
            val joinResult = onlineGameViewModel.joinOpenSearch(targetUserId, tc, rated)
            if (joinResult.isFailure) {
                searchStartedInLobby = false
                snackbarHostState.showSnackbar(
                    message = searchNoLongerAvailableMsg,
                    duration = SnackbarDuration.Short,
                )
                viewModel.refreshOpenSearches()
            }
        }
    }

    val isGuest = authViewModel.currentUser?.isGuest == true
    val isAuthenticated = authState is com.agustin.tarati.features.online.auth.AuthState.Authenticated

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(localizedString(Res.string.auth_logout)) },
            text = { Text(localizedString(Res.string.auth_logout_confirm)) },
            confirmButton = {
                Button(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        authViewModel.logout()
                        connectionViewModel.disconnect()
                    }
                }) { Text(localizedString(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(localizedString(Res.string.cancel))
                }
            },
        )
    }

    TaratiBackground {
        // Acciones compartidas entre TopBar (FullScreen) y CompanionPanelHeader (CompanionPanel).
        val topBarActions: @Composable RowScope.() -> Unit = {
            if (onLeaderboard != null) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.profile_leaderboard),
                    onClick = onLeaderboard,
                ) {
                    Icon(
                        imageVector = TaratiIcons.Leaderboard,
                        contentDescription = localizedString(Res.string.profile_leaderboard),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // Botón Supporter ♥ — solo para usuarios registrados (el checkout requiere sesión).
            if (onNavigateToSupporter != null && isAuthenticated && !isGuest) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.supporter_title),
                    onClick = onNavigateToSupporter,
                ) {
                    Icon(
                        imageVector = TaratiIcons.Supporter,
                        contentDescription = localizedString(Res.string.supporter_title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Botón Login / Logout
            val loginLogoutLabel = localizedString(
                if (isAuthenticated && !isGuest) Res.string.auth_logout else Res.string.auth_sign_in
            )
            TooltipIconButton(
                tooltip = loginLogoutLabel,
                onClick = {
                    when {
                        !isAuthenticated || isGuest -> onShowLogin()
                        else -> showLogoutConfirm = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (isAuthenticated && !isGuest) TaratiIcons.Logout else TaratiIcons.AccountCircle,
                    contentDescription = loginLogoutLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Botón 🔍 — visible en el tab "En Vivo" salvo partida online en curso.
            if (selectedTab == 1 && !hasActiveGame) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.lobby_new_search),
                    onClick = { showMatchmakingSheet = true },
                ) {
                    Icon(
                        imageVector = TaratiIcons.Search,
                        contentDescription = localizedString(Res.string.lobby_new_search),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                when (displayMode) {
                    DisplayMode.FullScreen -> TaratiTopBar(
                        title = localizedString(Res.string.online_lobby),
                        navigationType = TopBarNavigationType.Back,
                        onNavigationClick = onBack,
                        actions = topBarActions,
                    )

                    DisplayMode.CompanionPanel -> CompanionPanelHeader(
                        title = localizedString(Res.string.online_lobby),
                        onClose = onBack,
                        actions = topBarActions,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Mostrar loader durante el auto-connect inicial
                if (isAutoConnecting) {
                    CenteredLoader()
                    return@Scaffold
                }

                when (val state = connectionState) {
                    is ConnectionState.Offline -> {
                        // Si ya estuvimos Online, el LaunchedEffect llama onBack() — no mostrar nada.
                        if (!hasBeenOnline) {
                            CenteredMessage(text = localizedString(Res.string.lobby_not_connected_to_server))
                        }
                        return@Scaffold
                    }

                    is ConnectionState.Connecting -> {
                        CenteredLoader()
                        return@Scaffold
                    }

                    is ConnectionState.Error -> {
                        CenteredMessage(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        return@Scaffold
                    }

                    is ConnectionState.Reconnecting -> {
                        CenteredLoader()
                        return@Scaffold
                    }

                    is ConnectionState.Online -> Unit
                }

                // Banner de sesión invitado
                if (isGuest) {
                    GuestSessionBanner(onSignIn = onShowLogin)
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    scrollState = tabScrollState,
                    edgePadding = 0.dp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            tabScrollState.dispatchRawDelta(-dragAmount.x)
                        }
                    },
                ) {
                    // 0 — Conectados
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(localizedString(Res.string.lobby_connected_tab)) },
                        icon = {
                            Icon(
                                TaratiIcons.Group,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    // 1 — En Vivo
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { LocalizedText(Res.string.lobby_in_live) },
                        icon = {
                            Icon(
                                TaratiIcons.Public,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    // 2 — Torneos
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(localizedString(Res.string.tournaments)) },
                        icon = {
                            Icon(
                                TaratiIcons.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    // 3 — Mis Partidas
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { LocalizedText(Res.string.lobby_my_games) },
                        icon = {
                            Icon(
                                TaratiIcons.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    // 4 — Seguidos
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { LocalizedText(Res.string.social_feed) },
                        icon = {
                            Icon(
                                TaratiIcons.Group,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }

                when (selectedTab) {
                    0 -> ConnectedUsersTab(
                        viewModel = viewModel,
                        currentUserId = authViewModel.currentUser?.userId,
                        isCurrentUserGuest = isGuest,
                        onlineGameViewModel = onlineGameViewModel,
                        onNavigateToProfile = onNavigateToProfile,
                    )

                    1 -> LobbyTab(
                        viewModel = viewModel,
                        onJoinSearch = { userId, tc, rated -> handleJoinExistingSearch(userId, tc, rated) },
                        matchmakingState = matchmakingState,
                        currentUser = authViewModel.currentUser,
                        onCancelMatchmaking = {
                            scope.launch { onlineGameViewModel.cancelMatchmaking() }
                            searchStartedInLobby = false
                        },
                        onSpectateGame = if (onSpectateGame != null) { gameId ->
                            scope.launch {
                                val success = onlineGameViewModel.spectateGame(gameId)
                                if (success) {
                                    onSpectateGame(gameId)
                                } else {
                                    snackbarHostState.showSnackbar(spectatorUnavailableMsg)
                                }
                            }
                        } else null,
                    )

                    2 -> TournamentsTab(onNavigateToTournament = onNavigateToTournament)
                    3 -> GameHistoryTab(viewModel = viewModel, onNavigateToGameDetails = onNavigateToGameDetails)
                    4 -> FeedTab(viewModel = viewModel, onNavigateToGameDetails = onNavigateToGameDetails)
                }
            }
        }
    }

    // Modal de creación de búsqueda — inicia matchmaking en el lobby y muestra OwnSearchCard
    if (showMatchmakingSheet) {
        NewSearchSheet(
            onStartSearch = { tc, rated, spectatingAllowed ->
                showMatchmakingSheet = false
                scope.launch {
                    // Cancelar búsqueda previa si la hay, antes de iniciar la nueva.
                    if (matchmakingState is MatchmakingState.Searching) {
                        onlineGameViewModel.cancelMatchmaking()
                        searchStartedInLobby = false
                    }
                    val connResult = ensureConnected()
                    if (connResult.isFailure) {
                        snackbarHostState.showSnackbar(
                            message = connResult.exceptionOrNull()?.message ?: couldNotConnectMsg,
                            duration = SnackbarDuration.Long,
                        )
                        return@launch
                    }
                    searchStartedInLobby = true
                    val result = onlineGameViewModel.startMatchmaking(tc, rated, spectatingAllowed)
                    if (result.isFailure) {
                        searchStartedInLobby = false
                        snackbarHostState.showSnackbar(
                            message = couldNotConnectMsg,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onDismiss = { showMatchmakingSheet = false },
        )
    }
}
