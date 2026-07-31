package com.agustin.tarati.features.game6

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpNotation
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.features.online.auth.AuthState
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.features.online.lobby.ConnectedUsersTab
import com.agustin.tarati.features.online.lobby.IOnlineLobbyViewModel
import com.agustin.tarati.features.online.lobby.LobbyShell
import com.agustin.tarati.features.online.lobby.LobbyTabSpec
import com.agustin.tarati.features.online.lobby.OnlineLobbyViewModel
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpSeatDto
import com.agustin.tarati.network.models.MpStartPolicy
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.auth_logout
import com.agustin.tarati.shared.generated.resources.auth_logout_confirm
import com.agustin.tarati.shared.generated.resources.auth_sign_in
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.confirm
import com.agustin.tarati.shared.generated.resources.game6_lobby_action_error
import com.agustin.tarati.shared.generated.resources.game6_lobby_add_bot
import com.agustin.tarati.shared.generated.resources.game6_lobby_cancel_ready
import com.agustin.tarati.shared.generated.resources.game6_lobby_create
import com.agustin.tarati.shared.generated.resources.game6_lobby_empty_seat
import com.agustin.tarati.shared.generated.resources.game6_lobby_host_badge
import com.agustin.tarati.shared.generated.resources.game6_lobby_invite
import com.agustin.tarati.shared.generated.resources.game6_lobby_invite_empty
import com.agustin.tarati.shared.generated.resources.game6_lobby_invite_title
import com.agustin.tarati.shared.generated.resources.game6_lobby_join
import com.agustin.tarati.shared.generated.resources.game6_lobby_leave
import com.agustin.tarati.shared.generated.resources.game6_lobby_live_moves
import com.agustin.tarati.shared.generated.resources.game6_lobby_no_live
import com.agustin.tarati.shared.generated.resources.game6_lobby_no_tables
import com.agustin.tarati.shared.generated.resources.game6_lobby_policy_host
import com.agustin.tarati.shared.generated.resources.game6_lobby_policy_vote
import com.agustin.tarati.shared.generated.resources.game6_lobby_ready
import com.agustin.tarati.shared.generated.resources.game6_lobby_ready_badge
import com.agustin.tarati.shared.generated.resources.game6_lobby_remove_bot
import com.agustin.tarati.shared.generated.resources.game6_lobby_seats
import com.agustin.tarati.shared.generated.resources.game6_lobby_start
import com.agustin.tarati.shared.generated.resources.game6_lobby_start_policy
import com.agustin.tarati.shared.generated.resources.game6_lobby_tab_live
import com.agustin.tarati.shared.generated.resources.game6_lobby_tab_tables
import com.agustin.tarati.shared.generated.resources.game6_lobby_table_closed
import com.agustin.tarati.shared.generated.resources.game6_lobby_title
import com.agustin.tarati.shared.generated.resources.game6_lobby_waiting_host
import com.agustin.tarati.shared.generated.resources.game6_lobby_watch
import com.agustin.tarati.shared.generated.resources.lobby_connected_tab
import com.agustin.tarati.shared.generated.resources.lobby_in_live
import com.agustin.tarati.shared.generated.resources.lobby_my_games
import com.agustin.tarati.shared.generated.resources.profile_leaderboard
import com.agustin.tarati.shared.generated.resources.social_feed
import com.agustin.tarati.shared.generated.resources.supporter_title
import com.agustin.tarati.shared.generated.resources.tournaments
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.layout.DisplayMode
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla del **lobby online** del juego multijugador (Tarati Six, mesas 2–6, tablero `25`).
 *
 * Comparte el chrome con el lobby clásico vía [LobbyShell]: los mismos **5 tabs** (Conectados ·
 * En Vivo · Torneos · Mis Partidas · Seguidos) y las acciones de TopBar (Login/Logout + Supporter).
 * En esta fase de la convergencia solo **En Vivo** está implementado (mesas + partidas en vivo
 * fusionadas); el resto muestra el placeholder "próximamente" hasta sus fases respectivas.
 *
 * Observa el [MpLobbyViewModel]: lista de mesas públicas (con refresco), la mesa propia y la partida
 * arrancada. Igual que el lobby clásico: auto-loguea como invitado y autoconecta el WebSocket si no
 * hay sesión (se puede jugar online sin cuenta), arranca/detiene el polling con el ciclo de la
 * pantalla y muestra toasts de errores/cierre. El login queda disponible desde la TopBar.
 *
 * Es **solo lobby**: al arrancar la partida ([MpLobbyViewModel.currentGame] no-null) invoca
 * [onGameStarted] (cerrar panel / volver al tablero) — el tablero online se renderiza en el panel
 * **primario** (`MpGameScreen`), igual que el juego online de 2 jugadores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpLobbyScreen(
    onBack: () -> Unit,
    onGameStarted: () -> Unit,
    displayMode: DisplayMode = DisplayMode.FullScreen,
    /** Abre el modal de login/registro. */
    onShowLogin: () -> Unit = {},
    /** Abre la pantalla Supporter. Null = sin botón ♥ en la TopBar. */
    onNavigateToSupporter: (() -> Unit)? = null,
    /** Abre la clasificación multijugador. Null = sin botón 🏅 en la TopBar. */
    onNavigateToLeaderboard: (() -> Unit)? = null,
    /** Callback al tocar un perfil de usuario en línea (tab Conectados). Null = sin navegación. */
    onNavigateToProfile: ((userId: String) -> Unit)? = null,
    /** Abre el visor de replay de una partida MP terminada (tabs Mis Partidas / Seguidos). Null = sin navegación. */
    onOpenGame: ((gameId: String) -> Unit)? = null,
    viewModel: MpLobbyViewModel = koinInject(),
    lobbyViewModel: IOnlineLobbyViewModel = koinViewModel<OnlineLobbyViewModel>(),
    connectionViewModel: IConnectionViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
    settings: SettingsRepository = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val tables by viewModel.tables.collectAsState()
    val liveGames by viewModel.liveGames.collectAsState()
    val currentTable by viewModel.currentTable.collectAsState()
    val currentGame by viewModel.currentGame.collectAsState()
    // Usuarios conectados: fuente del picker de invitación a mesa (host).
    val onlineUsers by lobbyViewModel.onlineUsers.collectAsState()
    // Reactivo a la sesión: la pantalla recompone al iniciar/cerrar sesión.
    val authState by authViewModel.authState.collectAsState()
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val myUserId = (authState as? AuthState.Authenticated)?.userInfo?.userId
    val scope = rememberCoroutineScope()

    // Paridad con el lobby clásico: si no hay sesión, auto-login como invitado + auto-conexión del WS.
    // El nombre preferido sale de settings si es válido.
    var isAutoConnecting by remember {
        mutableStateOf(authState !is AuthState.Authenticated)
    }
    LaunchedEffect(Unit) {
        if (authViewModel.authState.value !is AuthState.Authenticated) {
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

    // Al volver a Offline habiendo estado Online (logout / expiración estando dentro) → salir.
    var hasBeenOnline by remember { mutableStateOf(connectionState is ConnectionState.Online) }
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Online) hasBeenOnline = true
        if (connectionState is ConnectionState.Offline && hasBeenOnline) onBack()
    }

    // Refresco periódico de la lista mientras la pantalla está activa.
    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    // Toast al cerrarse la mesa (host se fue). `rememberUpdatedState` → el colector usa siempre el
    // string ya cargado (no el "" del primer frame que devuelve `stringResource` mientras carga).
    val closedMsg by rememberUpdatedState(localizedString(Res.string.game6_lobby_table_closed))
    LaunchedEffect(Unit) {
        viewModel.tableClosed.collect { bus.toast(UIMessage.Toast(message = closedMsg)) }
    }

    // Toast de error, localizado con el código del servidor (p.ej. "table_not_found" al unirse a una
    // mesa que ya no existe). `stringResource` devuelve "" en el primer frame mientras carga el recurso;
    // por eso se **gatea en no-vacío** y `lastError` se limpia SOLO tras mostrar el toast real — si no,
    // el toast saldría vacío y el mensaje nunca se propagaría.
    var lastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.errors.collect { lastError = it } }
    val errorMsg = lastError?.let { localizedString(Res.string.game6_lobby_action_error, it) }
    LaunchedEffect(errorMsg) {
        val msg = errorMsg?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        bus.toast(UIMessage.Toast(message = msg))
        lastError = null
    }

    // Solo al **arrancar** la partida (transición sin-partida → partida) se notifica: en compacto vuelve
    // al tablero. Si el usuario reabre el lobby con una partida ya en curso (para crear/unirse a otra
    // mesa tras perder), NO se re-dispara → el panel no se cierra solo.
    var hadGame by remember { mutableStateOf(currentGame != null) }
    LaunchedEffect(currentGame) {
        val nowHasGame = currentGame != null
        if (nowHasGame && !hadGame) onGameStarted()
        hadGame = nowHasGame
    }

    val activeTable = currentTable
    val isAuthenticated = authState is AuthState.Authenticated
    val isGuest = authViewModel.currentUser?.isGuest == true
    var showLogoutConfirm by remember { mutableStateOf(false) }

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

    val topBarActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        // Botón Clasificación 🏅 — paridad con el lobby clásico.
        if (onNavigateToLeaderboard != null) {
            TooltipIconButton(
                tooltip = localizedString(Res.string.profile_leaderboard),
                onClick = onNavigateToLeaderboard,
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
    }

    // Default en "Conectados" (índice 0), igual que el lobby clásico.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        // 0 — Conectados: presencia en tiempo real (endpoint agnóstico del modo). Sin desafío directo
        // (2 jugadores no aplica a mesas MP) → las filas solo navegan al perfil.
        LobbyTabSpec(label = Res.string.lobby_connected_tab, icon = TaratiIcons.Group) {
            ConnectedUsersTab(
                viewModel = lobbyViewModel,
                currentUserId = myUserId,
                isCurrentUserGuest = isGuest,
                onNavigateToProfile = onNavigateToProfile,
            )
        },
        // 1 — En Vivo: mesas (crear/unirse/detalle) + partidas en curso (observar)
        LobbyTabSpec(label = Res.string.lobby_in_live, icon = TaratiIcons.Public) {
            MpLiveTab(
                tables = tables,
                liveGames = liveGames,
                activeTable = activeTable,
                myUserId = myUserId,
                onlineUsers = onlineUsers,
                viewModel = viewModel,
            )
        },
        // 2 — Torneos (Fase 4) — visible pero deshabilitado
        LobbyTabSpec(label = Res.string.tournaments, icon = TaratiIcons.EmojiEvents, enabled = false) {},
        // 3 — Mis Partidas: historial paginado de partidas MP propias (Fase 2)
        LobbyTabSpec(label = Res.string.lobby_my_games, icon = TaratiIcons.MenuBook) {
            MpHistoryTab(viewModel = viewModel, myUserId = myUserId, onOpenGame = onOpenGame)
        },
        // 4 — Seguidos: feed social de partidas de jugadores seguidos (Fase 3)
        LobbyTabSpec(label = Res.string.social_feed, icon = TaratiIcons.Group) {
            MpFeedTab(viewModel = viewModel, onOpenGame = onOpenGame)
        },
    )

    // Mismo chrome y ciclo de conexión que el lobby clásico (`OnlineLobbyScreen`), con el fondo del
    // juego MP: loader durante el auto-connect, banner de invitado y gating de `ConnectionState`.
    MultiplayerBackground(modifier = Modifier.fillMaxSize()) {
        LobbyShell(
            title = localizedString(Res.string.game6_lobby_title),
            displayMode = displayMode,
            onBack = onBack,
            connectionState = connectionState,
            isAutoConnecting = isAutoConnecting,
            showOfflineMessage = !hasBeenOnline,
            showGuestBanner = isGuest,
            onSignIn = onShowLogin,
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            topBarActions = topBarActions,
        )
    }
}

// ── Tab "En Vivo": mesas + partidas en curso ────────────────────────────────────

/**
 * Contenido del tab "En Vivo" del lobby MP. Si el usuario ya está sentado en una mesa muestra su
 * detalle (asientos, iniciar, salir); si no, un único scroll con: crear mesa, mesas abiertas
 * (unirse) y partidas en curso (observar).
 */
@Composable
private fun MpLiveTab(
    tables: List<MpTableDto>,
    liveGames: List<MpLiveGameDto>,
    activeTable: MpTableDto?,
    myUserId: String?,
    onlineUsers: List<OnlineUserDto>,
    viewModel: MpLobbyViewModel,
) {
    if (activeTable != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MpTableDetail(
                table = activeTable,
                isHost = activeTable.hostId == myUserId,
                myUserId = myUserId,
                onlineUsers = onlineUsers,
                viewModel = viewModel,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MpCreateTableSection(onCreate = viewModel::createTable) }

        // Mesas abiertas (unirse)
        item { MpSectionLabel(localizedString(Res.string.game6_lobby_tab_tables)) }
        if (tables.isEmpty()) {
            item {
                Text(
                    text = localizedString(Res.string.game6_lobby_no_tables),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(tables) { table ->
                MpTableCard(table = table, onJoin = { viewModel.joinTable(table.id) })
            }
        }

        // Partidas en curso (observar)
        item { MpSectionLabel(localizedString(Res.string.game6_lobby_tab_live)) }
        if (liveGames.isEmpty()) {
            item {
                Text(
                    text = localizedString(Res.string.game6_lobby_no_live),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(liveGames) { game ->
                MpLiveGameCard(game = game, onWatch = { viewModel.spectate(game.gameId) })
            }
        }
    }
}

/** Etiqueta discreta de sección dentro del tab "En Vivo" del lobby MP. */
@Composable
private fun MpSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ── Partidas en vivo (observar) ─────────────────────────────────────────────────

@Composable
private fun MpLiveGameCard(game: MpLiveGameDto, onWatch: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Miniatura del tablero reconstruida de la FEN.
            MpBoardThumbnail(
                notation = game.positionNotation,
                modifier = Modifier.size(56.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = game.players.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MpTurnDisc(game.currentTurn)
                    Text(
                        text = localizedString(Res.string.game6_lobby_seats, game.playerCount) +
                                " · " + localizedString(Res.string.game6_lobby_live_moves, game.moveCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onWatch) {
                Icon(TaratiIcons.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(localizedString(Res.string.game6_lobby_watch))
            }
        }
    }
}

/** Miniatura del tablero `25` desde una FEN de `game6`; tablero vacío si no parsea. */
@Composable
private fun MpBoardThumbnail(notation: String, modifier: Modifier) {
    val pieces = remember(notation) {
        runCatching { MpNotation.parsePosition(notation).pieces }.getOrElse { emptyMap() }
    }
    StaticBoard25Renderer(modifier = modifier, pieces = pieces)
}

/** Disco del color al que le toca mover (paridad visual con los discos del sidebar). */
@Composable
private fun MpTurnDisc(color: PlayerColor) {
    val fill = PlayerPalette.fill(color)
    val border = PlayerPalette.border(color)
    Canvas(Modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        drawCircle(fill, radius = r, center = Offset(r, r))
        drawCircle(border, radius = r, center = Offset(r, r), style = Stroke(width = r * 0.2f))
    }
}

// ── Crear mesa + tarjeta de mesa ─────────────────────────────────────────────────

/**
 * Sección **colapsable** de creación de mesa: una cabecera "Crear mesa" que **se despliega hacia
 * abajo** revelando el selector de tamaño (2–6), el modo de inicio (anfitrión / todos listos) y el
 * botón de confirmar.
 */
@Composable
private fun MpCreateTableSection(onCreate: (Int, MpStartPolicy) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var count by remember { mutableStateOf(MpSetup.MIN_PLAYERS + 2) } // 4 por defecto
    var policy by remember { mutableStateOf(MpStartPolicy.HOST_MANUAL) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TaratiIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = localizedString(Res.string.game6_lobby_create),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = if (expanded) TaratiIcons.ExpandLess else TaratiIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (n in MpSetup.MIN_PLAYERS..MpSetup.MAX_PLAYERS) {
                            MpToggleChip(label = "$n", selected = n == count, onClick = { count = n })
                        }
                    }
                    // Modo de inicio: anfitrión manual vs. votación de "listos".
                    Text(
                        text = localizedString(Res.string.game6_lobby_start_policy),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MpToggleChip(
                            label = localizedString(Res.string.game6_lobby_policy_host),
                            selected = policy == MpStartPolicy.HOST_MANUAL,
                            onClick = { policy = MpStartPolicy.HOST_MANUAL },
                        )
                        MpToggleChip(
                            label = localizedString(Res.string.game6_lobby_policy_vote),
                            selected = policy == MpStartPolicy.VOTE,
                            onClick = { policy = MpStartPolicy.VOTE },
                        )
                    }
                    Button(onClick = { onCreate(count, policy) }, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedString(Res.string.game6_lobby_create))
                    }
                }
            }
        }
    }
}

/** Chip binario (seleccionado = relleno / no = contorno) — tamaño de mesa y modo de inicio. */
@Composable
private fun MpToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun MpTableCard(table: MpTableDto, onJoin: () -> Unit) {
    val occupied = table.seats.count { it.occupantId != null || it.isBot }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = localizedString(Res.string.game6_lobby_seats, table.playerCount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$occupied / ${table.playerCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onJoin) { Text(localizedString(Res.string.game6_lobby_join)) }
        }
    }
}

// ── Detalle de la mesa (sentado) ────────────────────────────────────────────────

@Composable
private fun MpTableDetail(
    table: MpTableDto,
    isHost: Boolean,
    myUserId: String?,
    onlineUsers: List<OnlineUserDto>,
    viewModel: MpLobbyViewModel,
) {
    val occupied = table.seats.count { it.occupantId != null || it.isBot }
    val hasFreeSeat = table.seats.any { it.occupantId == null && !it.isBot }
    val mySeat = table.seats.firstOrNull { it.occupantId == myUserId }
    val isVote = table.startPolicy == MpStartPolicy.VOTE
    var showInvite by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        table.seats.forEach { seat ->
            MpSeatRow(
                seat = seat,
                isHost = isHost,
                hostId = table.hostId,
                isVote = isVote,
                viewModel = viewModel,
            )
        }

        Spacer(Modifier.size(4.dp))

        // Mesas VOTE: toggle "listo" del jugador local (al estar todos listos, arranca sola).
        if (isVote && mySeat != null) {
            if (mySeat.ready) {
                Button(onClick = { viewModel.setReady(false) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(TaratiIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(localizedString(Res.string.game6_lobby_cancel_ready))
                }
            } else {
                OutlinedButton(onClick = { viewModel.setReady(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedString(Res.string.game6_lobby_ready))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isHost) {
                // El inicio manual del host vale también en VOTE (escape ante un ausente).
                Button(
                    onClick = { viewModel.startTable() },
                    enabled = occupied >= MpSetup.MIN_PLAYERS,
                ) {
                    Icon(TaratiIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(localizedString(Res.string.game6_lobby_start))
                }
                if (hasFreeSeat) {
                    OutlinedButton(onClick = { showInvite = true }) {
                        Icon(TaratiIcons.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(localizedString(Res.string.game6_lobby_invite))
                    }
                }
            } else if (!isVote) {
                Text(
                    text = localizedString(Res.string.game6_lobby_waiting_host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(onClick = { viewModel.leaveTable() }) {
                Text(localizedString(Res.string.game6_lobby_leave))
            }
        }
    }

    if (showInvite) {
        MpInviteDialog(
            onlineUsers = onlineUsers,
            excludedIds = table.seats.mapNotNull { it.occupantId }.toSet() + setOfNotNull(myUserId),
            onInvite = { viewModel.inviteToTable(it); showInvite = false },
            onDismiss = { showInvite = false },
        )
    }
}

@Composable
private fun MpSeatRow(seat: MpSeatDto, isHost: Boolean, hostId: String, isVote: Boolean, viewModel: MpLobbyViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    seat.isBot -> Icon(TaratiIcons.SmartToy, contentDescription = null, modifier = Modifier.size(20.dp))
                    seat.occupantId != null -> Icon(
                        TaratiIcons.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    else -> Spacer(Modifier.size(20.dp))
                }
                Text(
                    text = when {
                        seat.occupantId != null -> seat.occupantName ?: seat.occupantId
                        seat.isBot -> seat.occupantName ?: "Bot"
                        else -> localizedString(Res.string.game6_lobby_empty_seat)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (seat.occupantId != null && seat.occupantId == hostId) {
                    Text(
                        text = localizedString(Res.string.game6_lobby_host_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Badge "Listo" en mesas VOTE para ocupantes humanos que ya confirmaron.
                if (isVote && seat.ready && seat.occupantId != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            TaratiIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = localizedString(Res.string.game6_lobby_ready_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isHost) {
                when {
                    seat.isBot -> TextButton(onClick = { viewModel.removeBot(seat.index) }) {
                        Text(localizedString(Res.string.game6_lobby_remove_bot))
                    }

                    seat.occupantId == null -> TextButton(onClick = { viewModel.addBot(seat.index) }) {
                        Text(localizedString(Res.string.game6_lobby_add_bot))
                    }

                    else -> Unit
                }
            }
        }
    }
}

/**
 * Diálogo de **invitación dirigida**: lista los usuarios conectados invitables (excluye bots, al
 * emisor y a quienes ya están sentados en la mesa). Tocar uno envía la invitación y cierra.
 */
@Composable
private fun MpInviteDialog(
    onlineUsers: List<OnlineUserDto>,
    excludedIds: Set<String>,
    onInvite: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val invitable = remember(onlineUsers, excludedIds) {
        onlineUsers.filter { !it.isBot && it.userId !in excludedIds }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(Res.string.game6_lobby_invite_title)) },
        text = {
            if (invitable.isEmpty()) {
                Text(
                    text = localizedString(Res.string.game6_lobby_invite_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(invitable) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onInvite(user.userId) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(TaratiIcons.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(text = user.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(localizedString(Res.string.cancel)) }
        },
    )
}
