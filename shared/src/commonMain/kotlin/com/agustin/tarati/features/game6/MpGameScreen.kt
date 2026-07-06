package com.agustin.tarati.features.game6

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.auth.AuthState
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.dialogs.AboutDialog
import com.agustin.tarati.services.dialogs.GameOverDialog
import com.agustin.tarati.services.dialogs.NewGameDialog
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.services.sound.LocalSoundService
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.online_lobby
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.bottombar.rememberBoardTilt
import com.agustin.tarati.ui.components.sidebar.SidebarShell
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla del juego multijugador **local** (hot-seat o contra bots) sobre el tablero `25`.
 *
 * Comparte el chrome del panel lateral con Tarati vía [SidebarShell], pero con contenido propio
 * (config de 2–6 jugadores, estado/movimientos MP). El área de tablero usa [Board25View]. El
 * switch de modo (en la fila de controles) vuelve a Tarati vía `LocalGameModeController`.
 *
 * El [viewModel] lo provee el host (NavGraph) y **sobrevive** el cambio de modo Single↔Multi (igual
 * que el juego single vive en su propio ViewModel), así que ninguno de los dos sentidos descarta la
 * partida en curso → el cambio de modo no necesita confirmación.
 *
 * Alertas (vía [UIMessageBus]/`AlertHost`): confirmación de **nueva partida** (si hay partida en
 * curso) y **popup de fin de partida** con el resultado.
 */
@Composable
fun MpGameScreen(
    viewModel: MpLocalGameViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToOnline: () -> Unit,
    // Se recibe del host (NavGraph): en web el tipo concreto SettingsViewModel no está registrado
    // en Koin (existe WasmSettingsViewModel ligado a ISettingsViewModel), así que no debe resolverse
    // aquí — se propaga la misma instancia que usa el resto de la app.
    settingsViewModel: ISettingsViewModel,
    modifier: Modifier = Modifier,
    bus: UIMessageBus = koinInject(),
    mpLobbyViewModel: MpLobbyViewModel = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
) {
    // Partida MP **online** en curso → el tablero online ocupa el panel primario (igual que el juego
    // online de 2 jugadores muestra su tablero acá, con el lobby en el panel lateral). El juego local
    // (VM propio) queda intacto detrás y reaparece al terminar/abandonar la online (`clearGame`).
    val onlineGame by mpLobbyViewModel.currentGame.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    onlineGame?.let { og ->
        val myUserId = (authState as? AuthState.Authenticated)?.userInfo?.userId
        MpOnlineGameScreen(
            game = og,
            // Espectador: no soy jugador → sin color (el tap queda gateado, tablero read-only).
            myColor = if (og.spectating) null else og.players.firstOrNull { it.userId == myUserId }?.color,
            spectating = og.spectating,
            onMove = mpLobbyViewModel::makeMove,
            onLeave = { if (og.spectating) mpLobbyViewModel.leaveSpectating() else mpLobbyViewModel.clearGame() },
            settingsViewModel = settingsViewModel,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAchievements = onNavigateToAchievements,
            onNavigateToOnline = onNavigateToOnline,
            isFreshMove = mpLobbyViewModel::isFreshMove,
            onMovePresented = mpLobbyViewModel::markMovePresented,
            isFreshGameOver = mpLobbyViewModel::isFreshGameOver,
            onGameOverPresented = mpLobbyViewModel::markGameOverPresented,
            bus = bus,
        )
        return
    }

    val state by viewModel.state.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val legalTargets by viewModel.legalTargets.collectAsState()
    val threatened by viewModel.threatened.collectAsState()
    val lastMove by viewModel.lastMove.collectAsState()
    val converted by viewModel.converted.collectAsState()
    val config by viewModel.config.collectAsState()
    val history by viewModel.history.collectAsState()
    val settings by settingsViewModel.settingsState.collectAsState()
    val boardVisual = settings.boardVisualState
    val soundService = LocalSoundService.current
    val botKick by viewModel.botKick.collectAsState()
    val preMoveFrom by viewModel.preMoveFrom.collectAsState()
    val preMoveTargets by viewModel.preMoveTargets.collectAsState()
    val pendingPreMove by viewModel.pendingPreMove.collectAsState()
    val moveIndex by viewModel.moveIndex.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editColor by viewModel.editColor.collectAsState()

    // Cabeceo inercial del tablero al expandir el FAB / abrir el panel de historial (paridad con single).
    var isFabExpanded by remember { mutableStateOf(false) }
    var isHistoryPanelOpen by remember { mutableStateOf(false) }
    val boardTilt = rememberBoardTilt(isFabExpanded, isHistoryPanelOpen)
    val density = LocalDensity.current

    // Hay una partida "en curso" (con jugadas y sin terminar) → "Nueva partida" pide confirmación.
    val hasGameInProgress = history.isNotEmpty() && !state.isGameOver

    // Preferencia de pre-movimientos (Settings) → el VM decide si el tap va a la ruta de pre-move.
    LaunchedEffect(settings.preMovesEnabled) {
        viewModel.setPreMovesEnabled(settings.preMovesEnabled)
    }

    // Turnos de bot: cuando el asiento en turno lo controla un bot, juega tras un retardo. Se
    // re-evalúa también con [botKick] (nueva partida sobre el mismo asiento inicial, o convertir un
    // asiento en IA), no solo al cambiar currentSeatIndex → los bots arrancan solos. Al volver el
    // turno humano, ejecuta el pre-movimiento pendiente (si hay), revalidado en el VM.
    LaunchedEffect(state.currentSeatIndex, state.isGameOver, botKick) {
        if (viewModel.isBotTurn()) {
            delay(550.milliseconds)
            viewModel.playBotMove()
        } else if (!state.isGameOver) {
            delay(200.milliseconds)
            viewModel.tryExecutePreMove()
        }
    }

    // Eventos one-shot del VM (sonido de jugada/nueva-partida + fin de partida). Desacoplados de la
    // recomposición vía SharedFlow: no se re-disparan al volver a entrar en Multi con una partida
    // avanzada/terminada (el VM persiste en el host). Igual que single: captura vs. movimiento; al
    // fin, sonido + popup tras dejar completar la animación del último movimiento.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MpUiEvent.Moved ->
                    if (event.capture) soundService.playCaptureSound() else soundService.playMoveSound()

                MpUiEvent.NewGame -> soundService.playNewGameSound()

                is MpUiEvent.Finished -> {
                    delay(700.milliseconds)
                    soundService.playGameOverSound()
                    bus.alert { dismiss ->
                        GameOverDialog(
                            gameOverMessage = mpResultMessage(event.result),
                            onConfirmed = {
                                dismiss()
                                viewModel.newGame()
                            },
                            onDismissed = dismiss,
                        )
                    }
                }
            }
        }
    }

    val onNewGame: () -> Unit = {
        if (hasGameInProgress) {
            bus.alert { dismiss ->
                NewGameDialog(
                    onConfirmed = {
                        dismiss()
                        viewModel.newGame()
                    },
                    onDismissed = dismiss,
                )
            }
        } else {
            viewModel.newGame()
        }
    }

    // Cambiar el nº de jugadores reinicia la partida (recoloca bases y orden de turno). En tablero
    // fresco/terminado se aplica al instante (setup en vivo, silencioso); con partida en curso pide la
    // misma confirmación que "Nueva partida" y, al confirmar, suena como un reinicio deliberado.
    val onSetPlayerCount: (Int) -> Unit = { count ->
        if (hasGameInProgress) {
            bus.alert { dismiss ->
                NewGameDialog(
                    onConfirmed = {
                        dismiss()
                        viewModel.setPlayerCount(count)
                        soundService.playNewGameSound()
                    },
                    onDismissed = dismiss,
                )
            }
        } else {
            viewModel.setPlayerCount(count)
        }
    }

    MpGameScaffold(
        modifier = modifier,
        // Búsqueda de partidas online en la barra superior (solo compacto, donde el sidebar está en el
        // drawer) — análogo a la búsqueda del single. Lleva al lobby de mesas MP (creación/observación);
        // si no hay sesión, el MpLobbyScreen abre el login sheet compartido.
        topBarActions = {
            TooltipIconButton(
                tooltip = localizedString(Res.string.online_lobby),
                onClick = onNavigateToOnline,
            ) {
                Icon(TaratiIcons.Search, contentDescription = localizedString(Res.string.online_lobby))
            }
        },
        sidebar = {
            SidebarShell(
                // Insets del sistema (igual que el SidebarContent de single): en el drawer compacto el
                // header (Settings/Logros) no queda tapado por la barra de estado.
                modifier = Modifier.systemBarsPadding(),
                header = {
                    MpSidebarHeader(
                        onSettings = onNavigateToSettings,
                        onAchievements = onNavigateToAchievements,
                    )
                },
                controls = {
                    MpControls(
                        onNewGame = onNewGame,
                        onRotate = viewModel::rotate,
                        isEditing = isEditing,
                        onToggleEdit = viewModel::toggleEditing,
                    )
                },
                playerConfig = {
                    MpPlayerConfig(
                        config = config,
                        onSetPlayerCount = onSetPlayerCount,
                        onSetSeatIsAI = viewModel::setSeatIsAI,
                    )
                },
                moveHistory = {
                    MpMoveHistorySection(
                        modifier = Modifier.weight(1f),
                        state = state,
                        history = history,
                        onOnlineLobby = onNavigateToOnline,
                        moveIndex = moveIndex,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onMoveToIndex = viewModel::moveToIndex,
                        onMoveToCurrent = viewModel::moveToCurrent,
                    )
                },
                footer = {
                    MpAboutFooter(
                        onAbout = { bus.alert { dismiss -> AboutDialog(onDismiss = dismiss) } },
                    )
                },
            )
        },
        board = { boardModifier ->
            BoxWithConstraints(modifier = boardModifier) {
                // En área ancha, al abrir el panel de historial el tablero se corre a la izquierda para
                // dejarle lugar a la lista (paridad con single: boardEndPadding). El mismo criterio de
                // "ancho" (maxWidth > maxHeight) que usa MpBottomBar para acotar el panel a 320dp.
                val boardEndPadding by animateDpAsState(
                    targetValue = if (maxWidth > maxHeight && isHistoryPanelOpen) 320.dp else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "mp_board_shift",
                )
                Board25Pane(
                    state = state,
                    seatIsAI = config.seatIsAI,
                    selection = selection,
                    legalTargets = legalTargets,
                    threatened = threatened,
                    lastMove = lastMove,
                    converted = converted,
                    boardVisual = boardVisual,
                    onVertexTap = viewModel::onVertexTap,
                    preMoveFrom = preMoveFrom,
                    preMoveTargets = preMoveTargets,
                    pendingPreMove = pendingPreMove,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = boardEndPadding)
                        .graphicsLayer {
                            rotationX = boardTilt.rotationX
                            rotationY = boardTilt.rotationY
                            // cameraDistance estándar de Material: perspectiva sutil sin distorsión.
                            cameraDistance = 12f * density.density
                        }
                        .padding(12.dp),
                )

                // Editor de posiciones (D14): overlay de controles sobre el tablero mientras se edita
                // (los taps colocan/quitan piezas vía el ViewModel). Reemplaza el FAB de historial.
                if (isEditing) {
                    MpEditControls(
                        state = state,
                        editColor = editColor,
                        canStart = state.pieces.values.mapTo(mutableSetOf()) { it.owner }.size >= 2,
                        // Ancho (landscape/Expanded) → controles a los lados; portrait → arriba/abajo.
                        isLandscape = maxWidth > maxHeight,
                        onCycleColor = viewModel::cycleEditColor,
                        onCycleTurn = viewModel::cycleEditTurn,
                        onClear = viewModel::clearEditBoard,
                        onReset = viewModel::resetEditBoard,
                        onStart = viewModel::startGameFromEdit,
                        onCancel = viewModel::toggleEditing,
                    )
                } else {
                    // FAB de undo/redo + lista de movimientos (D12), superpuesto al tablero en cualquier
                    // layout — igual que el BottomGameBar de single, que también convive con el sidebar.
                    // Sus cambios de estado alimentan el cabeceo del tablero (boardTilt).
                    MpBottomBar(
                        moves = history,
                        seats = state.seats,
                        moveIndex = moveIndex,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onMoveToCurrent = viewModel::moveToCurrent,
                        onMoveToIndex = viewModel::moveToIndex,
                        onFabExpandedChange = { isFabExpanded = it },
                        onHistoryOpenChange = { isHistoryPanelOpen = it },
                    )
                }
            }
        },
    )
}
