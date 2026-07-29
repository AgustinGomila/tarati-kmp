package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpPreMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpTransforms
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.network.models.MpOnlineGame
import com.agustin.tarati.services.dialogs.AboutDialog
import com.agustin.tarati.services.dialogs.GameOverDialog
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.services.sound.LocalSoundService
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_lobby_leave
import com.agustin.tarati.shared.generated.resources.game6_lobby_spectating
import com.agustin.tarati.shared.generated.resources.game6_lobby_stop_watching
import com.agustin.tarati.ui.components.sidebar.SidebarShell
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tablero de una **partida multijugador online** (M7.3). Renderiza el estado de [MpOnlineGame] (que el
 * `MpOnlineClient` mantiene a partir de los `MpServerMessage`) con **el mismo chrome que el juego
 * local** ([SidebarShell] + [Board25View]) → la experiencia online es idéntica a la offline: el panel
 * lateral (Settings/Logros/Acerca/Online) queda visible en web. El tap está **gateado a mi turno**
 * (según [myColor]) y produce un `MakeMove` vía [onMove]; la selección/destinos se computan con
 * [MpRules]. El popup de fin reusa `GameOverDialog` vía [UIMessageBus].
 */
@Composable
fun MpOnlineGameScreen(
    game: MpOnlineGame,
    myColor: PlayerColor?,
    onMove: (from: String, to: String) -> Unit,
    onLeave: () -> Unit,
    /** `true` = **espectador**: tablero read-only, sin countdown; el botón salir "deja de observar". */
    spectating: Boolean = false,
    settingsViewModel: ISettingsViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToOnline: () -> Unit,
    // Gatean la presentación (sonido + animación) del último movimiento: `isFreshMove` distingue una
    // jugada nueva de una re-entrada (cambio de modo Single↔Multi) para no "rehacerla"; `onMovePresented`
    // la marca ya presentada. El estado vive en el `MpOnlineClient` (single) que sobrevive el cambio.
    isFreshMove: (moveCount: Int) -> Boolean,
    onMovePresented: (moveCount: Int) -> Unit,
    // Análogo para el **fin** de la partida: `isFreshGameOver` evita repetir el sonido + popup de
    // resultado al re-entrar a una partida ya terminada; `onGameOverPresented` lo marca.
    isFreshGameOver: (gameId: String) -> Boolean,
    onGameOverPresented: (gameId: String) -> Unit,
    bus: UIMessageBus = koinInject(),
) {
    // Perspectiva por-cliente: el jugador local ve **su** base al Sur (abajo). El estado del servidor
    // es canónico (mismo para todos); acá se rota solo para mostrar —tablero, último movimiento,
    // conversiones e historial, todo en el mismo marco— y se **des-rota** la jugada saliente antes de
    // enviarla (`sendMove`). Es estable durante la partida (la base del asiento no cambia). Espectador
    // (sin color) → sin rotar (marco canónico, host al Sur), igual que hoy.
    val viewRotation = remember(game.gameId, myColor) {
        if (spectating) 0 else MpTransforms.rotationToBottom(game.state, myColor)
    }
    val state = remember(game.state, viewRotation) { MpTransforms.rotate(game.state, viewRotation) }
    val settings by settingsViewModel.settingsState.collectAsState()
    val boardVisual = settings.boardVisualState
    val soundService = LocalSoundService.current

    // Nombres reales de los jugadores (color → nombre) para el sidebar y el popup de fin.
    val nameByColor = remember(game.players) { game.players.associate { it.color to it.name } }

    val myTurn = !state.isGameOver && myColor != null && state.currentSeat.color == myColor

    // Selección local; se limpia tras cada jugada (mía o ajena).
    var selection by remember(game.gameId) { mutableStateOf<Vertex?>(null) }

    // Pre-movimiento (durante el turno ajeno): estado local a la pantalla, análogo al `MpLocalGameViewModel`
    // pero para el juego online (el estado viene del servidor). Habilitado si no soy espectador. La FSM y
    // la revalidación se comparten con el modo local vía [MpPreMove].
    val preMoveEnabled = settings.preMovesEnabled && !spectating
    var preMoveFrom by remember(game.gameId) { mutableStateOf<Vertex?>(null) }
    var preMoveTargets by remember(game.gameId) { mutableStateOf<Set<Vertex>>(emptySet()) }
    var pendingPreMove by remember(game.gameId) { mutableStateOf<MpMove?>(null) }

    LaunchedEffect(state.moveCount) {
        selection = null
        // Paridad con local/single: una pre-selección sin confirmar se descarta al cambiar el estado;
        // un pre-movimiento confirmado sobrevive (se revalida al volver mi turno, más abajo).
        if (pendingPreMove == null) {
            preMoveFrom = null
            preMoveTargets = emptySet()
        }
    }

    val legalMoves = remember(selection, state) {
        val sel = selection
        if (sel != null && myTurn) MpRules.legalMoves(state).filter { it.from == sel } else emptyList()
    }
    val legalTargets = legalMoves.map { it.to }.toSet()
    val threatened = legalMoves.flatMap { MpRules.captureTargets(state.pieces, it) }.toSet()

    // Envía una jugada des-rotando del marco de display al canónico del servidor.
    val sendMove: (from: Vertex, to: Vertex) -> Unit = { from, to ->
        onMove(
            MpTransforms.rotate(from, -viewRotation).name,
            MpTransforms.rotate(to, -viewRotation).name,
        )
    }

    val onVertexTap: (Vertex) -> Unit = tap@{ vertex ->
        // Turno ajeno: ruta de pre-movimiento (si está habilitado).
        if (!myTurn) {
            if (!preMoveEnabled || myColor == null) return@tap
            when (val result = MpPreMove.onTap(state, myColor, preMoveFrom, vertex)) {
                is MpPreMove.TapResult.PreSelect -> {
                    preMoveFrom = result.from
                    preMoveTargets = result.targets
                    pendingPreMove = null
                }

                is MpPreMove.TapResult.SetPending -> {
                    pendingPreMove = result.move
                    preMoveFrom = null
                    preMoveTargets = emptySet()
                }

                MpPreMove.TapResult.Clear -> {
                    preMoveFrom = null
                    preMoveTargets = emptySet()
                    pendingPreMove = null
                }

                MpPreMove.TapResult.Ignore -> Unit
            }
            return@tap
        }
        val piece = state.pieces[vertex]
        if (piece != null && piece.owner == myColor) {
            selection = vertex
            return@tap
        }
        val from = selection
        if (from != null && MpRules.isLegal(state, MpMove(from, vertex))) {
            sendMove(from, vertex)
            selection = null
            return@tap
        }
        selection = null
    }

    // Ejecución del pre-movimiento al volver mi turno: revalida contra el estado actual y, si sigue
    // siendo legal, lo envía; si no, lo descarta. El delay deja que la animación del rival complete.
    LaunchedEffect(state.moveCount, myTurn) {
        val pending = pendingPreMove ?: return@LaunchedEffect
        val human = myColor ?: return@LaunchedEffect
        if (spectating || !myTurn) return@LaunchedEffect
        if (!MpPreMove.isReady(state, human, pending)) {
            pendingPreMove = null
            return@LaunchedEffect
        }
        delay(200.milliseconds)
        if (pendingPreMove == pending) {
            sendMove(pending.from, pending.to)
            preMoveFrom = null
            preMoveTargets = emptySet()
            pendingPreMove = null
        }
    }

    // ¿La jugada actual es nueva (no una re-entrada tras cambiar de modo)? Gatea sonido y animación
    // para no "rehacer" el último movimiento al volver a la partida. Se computa en cada composición
    // (lee el estado que sobrevive en el cliente), antes de que el efecto de sonido la marque.
    val freshMove = isFreshMove(state.moveCount)

    // Sonido por jugada (paridad con local): captura si la última jugada convirtió, si no movimiento.
    // Solo para jugadas nuevas; al re-entrar no re-suena (la marca vive en el cliente `single`).
    LaunchedEffect(state.moveCount) {
        if (state.moveCount > 0 && isFreshMove(state.moveCount)) {
            if (game.converted.isNotEmpty()) soundService.playCaptureSound() else soundService.playMoveSound()
            onMovePresented(state.moveCount)
        }
    }

    // Popup de fin: al pasar a terminado, sonido + diálogo (tras dejar completar la animación). Solo si
    // el fin es **nuevo** (no una re-entrada tras cambiar de modo) → no repite la alerta de resultado.
    val result = state.result
    // Los deltas de rating llegan en `GameEnded`, **después** del StateUpdate que trae el resultado; con
    // `rememberUpdatedState` el diálogo (mostrado tras el delay) lee el valor ya actualizado.
    val latestDeltas by rememberUpdatedState(game.ratingDeltas)
    LaunchedEffect(result, game.gameId) {
        if (result != null && isFreshGameOver(game.gameId)) {
            onGameOverPresented(game.gameId)
            delay(700.milliseconds)
            soundService.playGameOverSound()
            bus.alert { dismiss ->
                // Delta de rating MP del jugador local (vacío si casual, espectador o aún sin llegar).
                val myUserId = myColor?.let { c -> game.players.firstOrNull { it.color == c }?.userId }
                val deltaSuffix = myUserId?.let { latestDeltas[it] }
                    ?.let { if (it >= 0) " (+$it)" else " ($it)" } ?: ""
                GameOverDialog(
                    gameOverMessage = mpResultMessage(result, nameByColor) + deltaSuffix,
                    onConfirmed = {
                        dismiss()
                        onLeave()
                    },
                    onDismissed = dismiss,
                )
            }
        }
    }

    val seatIsAI = remember(state.seats, game.players) {
        state.seats.map { seat -> game.players.firstOrNull { it.color == seat.color }?.isBot ?: false }
    }

    // Rotados al marco de display (perspectiva del jugador), como el `state`.
    val lastMove = remember(game.lastMoveFrom, game.lastMoveTo, viewRotation) {
        val from = game.lastMoveFrom
        val to = game.lastMoveTo
        if (from != null && to != null) {
            runCatching {
                MpTransforms.rotate(MpMove(Vertex.parseVertex(from), Vertex.parseVertex(to)), viewRotation)
            }.getOrNull()
        } else {
            null
        }
    }

    // Vértice volteado → dueño previo (para animar el flip de la captura, paridad con local).
    val convertedMap = remember(game.converted, viewRotation) {
        game.converted.mapNotNull { (name, owner) ->
            runCatching { MpTransforms.rotate(Vertex.parseVertex(name), viewRotation) }.getOrNull()?.let { it to owner }
        }.toMap()
    }

    // Historial en el marco de display, para que la lista de movimientos coincida con el tablero rotado.
    val displayHistory = remember(game.history, viewRotation) {
        if (viewRotation == 0) game.history else game.history.map { MpTransforms.rotate(it, viewRotation) }
    }

    // Countdown del turno (solo si hay timer y el turno es de un humano; los bots no tienen timer).
    val currentIsBot = game.players.firstOrNull { it.color == state.currentSeat.color }?.isBot ?: false
    val showCountdown = game.turnTimeoutMs > 0 && !state.isGameOver && !currentIsBot && !spectating
    var remainingSec by remember(state.moveCount) { mutableStateOf((game.turnTimeoutMs / 1000).toInt()) }
    LaunchedEffect(state.moveCount, showCountdown) {
        if (showCountdown) {
            remainingSec = (game.turnTimeoutMs / 1000).toInt()
            while (remainingSec > 0) {
                delay(1000.milliseconds)
                remainingSec -= 1
            }
        }
    }

    MpGameScaffold(
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
                    MpOnlineControls(
                        countdownSec = if (showCountdown) remainingSec else null,
                        spectating = spectating,
                        onLeave = onLeave,
                    )
                },
                // Los jugadores se ven en los indicadores de base del tablero.
                playerConfig = {},
                moveHistory = {
                    MpMoveHistorySection(
                        modifier = Modifier.weight(1f),
                        state = state,
                        history = displayHistory,
                        onOnlineLobby = onNavigateToOnline,
                        nameByColor = nameByColor,
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
            Board25Pane(
                state = state,
                seatIsAI = seatIsAI,
                selection = selection,
                legalTargets = legalTargets,
                threatened = threatened,
                lastMove = lastMove,
                converted = convertedMap,
                boardVisual = boardVisual,
                onVertexTap = onVertexTap,
                // Al re-entrar (cambio de modo) el último movimiento ya fue presentado → sin re-animar.
                suppressMoveAnimation = !freshMove,
                preMoveFrom = preMoveFrom,
                preMoveTargets = preMoveTargets,
                pendingPreMove = pendingPreMove,
                modifier = boardModifier.padding(12.dp),
            )
        },
    )
}

/**
 * Controles del sidebar en la partida online: switch de modo, countdown del turno y salir. En modo
 * **espectador** ([spectating]) se muestra un aviso "Observando" y el botón pasa a "Dejar de observar".
 */
@Composable
private fun MpOnlineControls(countdownSec: Int?, spectating: Boolean, onLeave: () -> Unit) {
    GameModeSwitch(current = GameMode.MULTI)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (spectating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    TaratiIcons.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = localizedString(Res.string.game6_lobby_spectating),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (countdownSec != null) {
            val low = countdownSec <= 5
            Text(
                text = "${countdownSec}s",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(
                localizedString(
                    if (spectating) Res.string.game6_lobby_stop_watching else Res.string.game6_lobby_leave,
                ),
            )
        }
    }
}
