package com.agustin.tarati.features.game6

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpSeatDto
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_lobby_action_error
import com.agustin.tarati.shared.generated.resources.game6_lobby_add_bot
import com.agustin.tarati.shared.generated.resources.game6_lobby_create
import com.agustin.tarati.shared.generated.resources.game6_lobby_empty_seat
import com.agustin.tarati.shared.generated.resources.game6_lobby_host_badge
import com.agustin.tarati.shared.generated.resources.game6_lobby_join
import com.agustin.tarati.shared.generated.resources.game6_lobby_leave
import com.agustin.tarati.shared.generated.resources.game6_lobby_live_moves
import com.agustin.tarati.shared.generated.resources.game6_lobby_no_live
import com.agustin.tarati.shared.generated.resources.game6_lobby_no_tables
import com.agustin.tarati.shared.generated.resources.game6_lobby_remove_bot
import com.agustin.tarati.shared.generated.resources.game6_lobby_seats
import com.agustin.tarati.shared.generated.resources.game6_lobby_start
import com.agustin.tarati.shared.generated.resources.game6_lobby_tab_live
import com.agustin.tarati.shared.generated.resources.game6_lobby_tab_tables
import com.agustin.tarati.shared.generated.resources.game6_lobby_table_closed
import com.agustin.tarati.shared.generated.resources.game6_lobby_title
import com.agustin.tarati.shared.generated.resources.game6_lobby_waiting_host
import com.agustin.tarati.shared.generated.resources.game6_lobby_watch
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.layout.CompanionPanelHeader
import com.agustin.tarati.ui.layout.DisplayMode
import com.agustin.tarati.ui.theme.TaratiIcons
import org.koin.compose.koinInject

/**
 * Pantalla del **lobby de mesas** del juego multijugador online.
 *
 * Observa el [MpLobbyViewModel]: lista de mesas públicas (con refresco), la mesa propia y la partida
 * arrancada. Autoconecta el WebSocket, arranca/detiene el polling con el ciclo de la pantalla, y
 * muestra toasts de errores/cierre. Sin sesión → abre el login sheet compartido de `AppContent`.
 *
 * Es **solo lobby**: al arrancar la partida ([MpLobbyViewModel.currentGame] no-null) invoca
 * [onGameStarted] (cerrar panel / volver al tablero) — el tablero online se renderiza en el panel
 * **primario** (`MpGameScreen`), igual que el juego online de 2 jugadores. En [DisplayMode.CompanionPanel]
 * (Expanded/web) se embebe en el panel lateral; en [DisplayMode.FullScreen] (compacto) ocupa la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpLobbyScreen(
    onBack: () -> Unit,
    onGameStarted: () -> Unit,
    displayMode: DisplayMode = DisplayMode.FullScreen,
    viewModel: MpLobbyViewModel = koinInject(),
    connectionViewModel: IConnectionViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val tables by viewModel.tables.collectAsState()
    val liveGames by viewModel.liveGames.collectAsState()
    val currentTable by viewModel.currentTable.collectAsState()
    val currentGame by viewModel.currentGame.collectAsState()
    // Reactivo a la sesión: la pantalla recompone al iniciar/cerrar sesión.
    val authState by authViewModel.authState.collectAsState()
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val myUserId = (authState as? AuthState.Authenticated)?.userInfo?.userId

    // El login se resuelve **antes** de entrar (el botón Online del tablero muestra el login sheet
    // sobre el tablero y solo navega aquí tras el acceso). Por lo tanto el lobby se monta siempre con
    // sesión; si se pierde (logout / expiración estando dentro), se **sale** — nunca se expone la UI de
    // mesas ni un prompt de login redundante.
    LaunchedEffect(myUserId) {
        if (myUserId == null) onBack()
    }

    // Autoconexión del WebSocket cuando hay sesión (se re-evalúa al loguearse).
    LaunchedEffect(myUserId) {
        val token = authViewModel.accessToken
        if (myUserId != null && token != null && !connectionViewModel.isConnected && !connectionViewModel.isConnecting) {
            connectionViewModel.connectToServer(devServerUrl, token)
        }
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
    val isOnline = connectionState is ConnectionState.Online

    // Mismo chrome que el lobby single (`OnlineLobbyScreen`): un `Scaffold` con `TaratiTopBar` (que
    // respeta los insets del sistema → la flecha de navegación queda **debajo** de la barra de estado y
    // es tocable) en FullScreen, y `CompanionPanelHeader` en el panel lateral. El fondo del juego MP.
    MultiplayerBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                when (displayMode) {
                    DisplayMode.FullScreen -> TaratiTopBar(
                        title = localizedString(Res.string.game6_lobby_title),
                        navigationType = TopBarNavigationType.Back,
                        onNavigationClick = onBack,
                    )

                    DisplayMode.CompanionPanel -> CompanionPanelHeader(
                        title = localizedString(Res.string.game6_lobby_title),
                        onClose = onBack,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    // Sin sesión (transitorio: se está saliendo) o autenticado pero aún sin conexión WS:
                    // loader. La creación/observación de mesas aparece **recién** al conectar (Online),
                    // nunca antes → no se expone UI del modo online a quien no accedió.
                    myUserId == null || !isOnline -> MpLobbyConnecting()

                    // Conectado: explorador (pestañas Mesas / En Vivo). La mesa propia (si la hay) se
                    // abre dentro del tab "Mesas", sin reemplazar el panel Online.
                    else -> MpLobbyBrowser(
                        tables = tables,
                        liveGames = liveGames,
                        activeTable = activeTable,
                        myUserId = myUserId,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

/** Loader centrado mientras se establece la conexión WS (antes de mostrar la creación de mesas). */
@Composable
private fun MpLobbyConnecting() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ── Explorador (pestañas Mesas / En Vivo) ──────────────────────────────────────

@Composable
private fun MpLobbyBrowser(
    tables: List<MpTableDto>,
    liveGames: List<MpLiveGameDto>,
    activeTable: MpTableDto?,
    myUserId: String,
    viewModel: MpLobbyViewModel,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                Text(localizedString(Res.string.game6_lobby_tab_tables))
            })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                Text(localizedString(Res.string.game6_lobby_tab_live))
            })
        }
        when (tab) {
            // Tab "Mesas": si ya estoy sentado en una mesa, muestro su detalle aquí (sin ocultar el
            // header ni las pestañas); si no, la sección de crear + la lista de mesas abiertas.
            0 -> if (activeTable != null) {
                MpTableDetail(
                    table = activeTable,
                    isHost = activeTable.hostId == myUserId,
                    viewModel = viewModel,
                )
            } else {
                MpTablesList(tables = tables, viewModel = viewModel)
            }

            else -> MpLiveGamesList(liveGames = liveGames, onWatch = viewModel::spectate)
        }
    }
}

// ── Partidas en vivo (observar) ─────────────────────────────────────────────────

@Composable
private fun MpLiveGamesList(liveGames: List<MpLiveGameDto>, onWatch: (String) -> Unit) {
    if (liveGames.isEmpty()) {
        Text(
            text = localizedString(Res.string.game6_lobby_no_live),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(liveGames) { game -> MpLiveGameCard(game = game, onWatch = { onWatch(game.gameId) }) }
    }
}

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

// ── Lista de mesas + crear ──────────────────────────────────────────────────────

@Composable
private fun MpTablesList(tables: List<MpTableDto>, viewModel: MpLobbyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MpCreateTableSection(onCreate = viewModel::createTable)

        if (tables.isEmpty()) {
            Text(
                text = localizedString(Res.string.game6_lobby_no_tables),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tables) { table ->
                    MpTableCard(table = table, onJoin = { viewModel.joinTable(table.id) })
                }
            }
        }
    }
}

/**
 * Sección **colapsable** de creación de mesa: una cabecera "Crear mesa" que **se despliega hacia
 * abajo** revelando el selector de tamaño (2–6) + el botón de confirmar, sin dejar de mostrar el
 * resto del lobby (el panel Online nunca se reemplaza ni se cierra).
 */
@Composable
private fun MpCreateTableSection(onCreate: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var count by remember { mutableStateOf(MpSetup.MIN_PLAYERS + 2) } // 4 por defecto

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
                            if (n == count) {
                                Button(onClick = { count = n }) { Text("$n") }
                            } else {
                                OutlinedButton(onClick = { count = n }) { Text("$n") }
                            }
                        }
                    }
                    Button(onClick = { onCreate(count) }, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedString(Res.string.game6_lobby_create))
                    }
                }
            }
        }
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
private fun MpTableDetail(table: MpTableDto, isHost: Boolean, viewModel: MpLobbyViewModel) {
    val occupied = table.seats.count { it.occupantId != null || it.isBot }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        table.seats.forEach { seat ->
            MpSeatRow(seat = seat, isHost = isHost, hostId = table.hostId, viewModel = viewModel)
        }

        Spacer(Modifier.size(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isHost) {
                Button(
                    onClick = { viewModel.startTable() },
                    enabled = occupied >= MpSetup.MIN_PLAYERS,
                ) {
                    Icon(TaratiIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(localizedString(Res.string.game6_lobby_start))
                }
            } else {
                Text(
                    text = localizedString(Res.string.game6_lobby_waiting_host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = { viewModel.leaveTable() }) {
                Text(localizedString(Res.string.game6_lobby_leave))
            }
        }
    }
}

@Composable
private fun MpSeatRow(seat: MpSeatDto, isHost: Boolean, hostId: String, viewModel: MpLobbyViewModel) {
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
