@file:OptIn(ExperimentalMaterial3Api::class)

package com.agustin.tarati.features.game6

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.features.online.lobby.formatGameDate
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.back
import com.agustin.tarati.shared.generated.resources.date
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.game6_lobby_live_moves
import com.agustin.tarati.shared.generated.resources.game6_players
import com.agustin.tarati.shared.generated.resources.game_details
import com.agustin.tarati.shared.generated.resources.game_information
import com.agustin.tarati.shared.generated.resources.go_to_begin
import com.agustin.tarati.shared.generated.resources.go_to_end
import com.agustin.tarati.shared.generated.resources.move_history
import com.agustin.tarati.shared.generated.resources.move_n_of_n
import com.agustin.tarati.shared.generated.resources.next
import com.agustin.tarati.shared.generated.resources.result
import com.agustin.tarati.shared.generated.resources.toggle_details
import com.agustin.tarati.shared.generated.resources.toggle_move_history
import com.agustin.tarati.shared.generated.resources.total_moves
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiIcons
import org.koin.compose.koinInject

private const val EXPAND_DURATION_MS = 300
private const val FADE_DURATION_MS = 250

/**
 * Visor de **replay** de una partida multijugador terminada (Tarati Six). Reconstruye la partida
 * jugada a jugada desde el historial persistido ([MpGameDetailViewModel]) y la reproduce sobre el
 * mismo renderer del juego ([Board25Pane]), read-only.
 *
 * Cosméticamente converge con el detalle de partida single ([com.agustin.tarati.features.detail.GameDetailsScreen]):
 * información superior colapsable (con el resultado), tablero con botones de avance/retroceso a los
 * lados + barra de progreso e inicio/fin debajo, y lista de movimientos colapsable (por defecto
 * colapsada en compacto) con resaltado + click-to-jump.
 */
@Composable
fun MpGameDetailScreen(
    gameId: String,
    onBack: () -> Unit,
    // Recibido desde el call site (NavGraph/CompanionPane): la instancia de settings se crea una sola vez
    // en el entrypoint de cada plataforma (Wasm/Desktop registran su propio VM como `viewModel`, no como
    // definición root) y se propaga. Resolverlo con `koinInject()` acá rompía en web (`_root_` sin `ISettingsViewModel`).
    settingsViewModel: ISettingsViewModel,
    viewModel: IMpGameDetailViewModel = koinInject(),
) {
    val ui by viewModel.state.collectAsState()
    val settings by settingsViewModel.settingsState.collectAsState()

    LaunchedEffect(gameId) { viewModel.loadGame(gameId) }

    MultiplayerBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TaratiTopBar(
                    title = localizedString(Res.string.game_details),
                    navigationType = TopBarNavigationType.Back,
                    onNavigationClick = onBack,
                )
            },
        ) { padding ->
            val boardState = ui.state
            when {
                ui.isLoading || (boardState == null && ui.error == null) ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                ui.error != null && boardState == null ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(
                            text = localizedString(Res.string.error, ui.error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                boardState != null -> MpGameDetailBody(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    ui = ui,
                    boardState = boardState,
                    boardVisual = settings.boardVisualState,
                    onMoveToIndex = viewModel::moveToIndex,
                    onFirst = viewModel::first,
                    onPrev = viewModel::prev,
                    onNext = viewModel::next,
                    onLast = viewModel::last,
                )
            }
        }
    }
}

@Composable
private fun MpGameDetailBody(
    modifier: Modifier,
    ui: MpGameDetailUiState,
    boardState: MpGameState,
    boardVisual: BoardVisualState,
    onMoveToIndex: (Int) -> Unit,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
) {
    val nameByColor = remember(ui.players) { ui.players.associate { it.color to it.name } }
    val seatIsAI = remember(boardState.seats, ui.players) {
        boardState.seats.map { seat -> ui.players.firstOrNull { it.color == seat.color }?.isBot ?: false }
    }

    val board: @Composable (Modifier) -> Unit = { m ->
        MpDetailBoard(
            modifier = m,
            state = boardState,
            seatIsAI = seatIsAI,
            lastMove = ui.lastMove,
            converted = ui.converted,
            boardVisual = boardVisual,
            moveIndex = ui.moveIndex,
            plyCount = ui.history.size,
            onFirst = onFirst,
            onPrev = onPrev,
            onNext = onNext,
            onLast = onLast,
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    MpGameInfoCard(ui = ui, nameByColor = nameByColor)
                }
                board(Modifier.weight(1.2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    MpCollapsibleMoveHistoryCard(
                        modifier = Modifier.fillMaxWidth(),
                        seats = boardState.seats,
                        ui = ui,
                        initialExpanded = true,
                        onMoveToIndex = onMoveToIndex,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MpGameInfoCard(ui = ui, nameByColor = nameByColor)
                board(Modifier.fillMaxWidth().weight(1f))
                MpCollapsibleMoveHistoryCard(
                    modifier = Modifier.fillMaxWidth(),
                    seats = boardState.seats,
                    ui = ui,
                    initialExpanded = false,
                    onMoveToIndex = onMoveToIndex,
                )
            }
        }
    }
}

// ── Tablero + navegación (paridad con CreateCardBoard de single) ─────────────────

/**
 * Tablero del replay con botones de avance/retroceso a los **lados** del tablero, más una barra de
 * progreso e inicio/fin debajo — mismo esquema de navegación que el detalle single ([CreateCardBoard]).
 * El tablero ([Board25Pane]) es read-only (sin taps).
 */
@Composable
private fun MpDetailBoard(
    modifier: Modifier,
    state: MpGameState,
    seatIsAI: List<Boolean>,
    lastMove: MpMove?,
    converted: Map<Vertex, PlayerColor>,
    boardVisual: BoardVisualState,
    moveIndex: Int,
    plyCount: Int,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
) {
    val canPrev = moveIndex > -1
    val canNext = moveIndex < plyCount - 1

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SideNavButton(TaratiIcons.KeyboardArrowLeft, localizedString(Res.string.back), canPrev, onPrev)
            Board25Pane(
                state = state,
                seatIsAI = seatIsAI,
                selection = null,
                legalTargets = emptySet(),
                threatened = emptySet(),
                lastMove = lastMove,
                converted = converted,
                boardVisual = boardVisual,
                onVertexTap = {},
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SideNavButton(TaratiIcons.KeyboardArrowRight, localizedString(Res.string.next), canNext, onNext)
        }

        // Barra de progreso + inicio/contador/fin (mismo bloque que single, debajo del tablero).
        if (plyCount > 0) {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (moveIndex + 1).toFloat() / plyCount.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EdgeNavButton(TaratiIcons.SkipPrevious, localizedString(Res.string.go_to_begin), canPrev, onFirst)
                    Text(
                        text = localizedString(Res.string.move_n_of_n, moveIndex + 1, plyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    EdgeNavButton(TaratiIcons.SkipNext, localizedString(Res.string.go_to_end), canNext, onLast)
                }
            }
        }
    }
}

/** Botón lateral grande (48dp) de avance/retroceso; espacio reservado cuando no aplica (alineación). */
@Composable
private fun SideNavButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (enabled) {
        TooltipIconButton(tooltip = tooltip, onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                icon,
                contentDescription = tooltip,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        Spacer(Modifier.width(48.dp))
    }
}

/** Botón chico (36dp) de inicio/fin bajo la barra de progreso. */
@Composable
private fun EdgeNavButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TooltipIconButton(tooltip = tooltip, onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = tooltip,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── Información superior colapsable (paridad con GameInfoCard de single) ──────────

/**
 * Tarjeta de información superior, colapsable (por defecto colapsada): el resultado de la partida
 * como resumen; expandida agrega jugadores, fecha y jugadas. Mismo chrome que [GameInfoCard].
 */
@Composable
private fun MpGameInfoCard(
    ui: MpGameDetailUiState,
    nameByColor: Map<PlayerColor, String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val resultText = ui.result?.let { mpResultMessage(it, nameByColor) } ?: ""

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(EXPAND_DURATION_MS, easing = FastOutSlowInEasing),
        label = "mp_info_chevron",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedString(Res.string.game_information),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    // Resumen compacto (colapsado): el resultado en una línea.
                    if (!expanded && resultText.isNotEmpty()) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TooltipIconButton(
                    tooltip = localizedString(Res.string.toggle_details),
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        TaratiIcons.ExpandMore,
                        contentDescription = localizedString(Res.string.toggle_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    tween(EXPAND_DURATION_MS, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) +
                        fadeIn(tween(FADE_DURATION_MS)),
                exit = shrinkVertically(
                    tween(EXPAND_DURATION_MS - 50, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) +
                        fadeOut(tween(FADE_DURATION_MS - 50)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (resultText.isNotEmpty()) {
                        MpInfoRow(localizedString(Res.string.result), resultText)
                    }
                    MpInfoRow(
                        localizedString(Res.string.game6_players),
                        ui.players.joinToString(", ") { it.name },
                    )
                    if (ui.endedAtMs > 0) {
                        MpInfoRow(localizedString(Res.string.date), formatGameDate(ui.endedAtMs))
                    }
                    MpInfoRow(
                        localizedString(Res.string.move_history),
                        localizedString(Res.string.game6_lobby_live_moves, ui.history.size),
                    )
                }
            }
        }
    }
}

/** Fila etiqueta/valor del panel de información (paridad con la fila de vista de single). */
@Composable
private fun MpInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value.ifEmpty { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Lista de movimientos colapsable (paridad con CollapsibleMoveHistoryCard) ──────

/**
 * Tarjeta de movimientos colapsable ([initialExpanded] = false por defecto en compacto). Reusa
 * [MpMoveGrid] (columnas por jugador, resaltado del ply actual + click-to-jump). Mismo chrome que
 * [CollapsibleMoveHistoryCard] de single: cabecera con título, contador de jugadas y chevron animado.
 */
@Composable
private fun MpCollapsibleMoveHistoryCard(
    modifier: Modifier,
    seats: List<Seat>,
    ui: MpGameDetailUiState,
    initialExpanded: Boolean,
    onMoveToIndex: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(EXPAND_DURATION_MS, easing = FastOutSlowInEasing),
        label = "mp_moves_chevron",
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedString(Res.string.move_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = localizedString(Res.string.total_moves, ui.history.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    TooltipIconButton(
                        tooltip = localizedString(Res.string.toggle_move_history),
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            TaratiIcons.ExpandMore,
                            contentDescription = localizedString(Res.string.toggle_move_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    tween(EXPAND_DURATION_MS, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) +
                        fadeIn(tween(FADE_DURATION_MS)),
                exit = shrinkVertically(
                    tween(EXPAND_DURATION_MS - 50, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) +
                        fadeOut(tween(FADE_DURATION_MS - 50)),
            ) {
                MpMoveGrid(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(bottom = 8.dp),
                    seats = seats,
                    history = ui.history,
                    currentPly = ui.moveIndex,
                    onCellClick = onMoveToIndex,
                )
            }
        }
    }
}
