@file:OptIn(ExperimentalMaterial3Api::class)

package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.game_details
import com.agustin.tarati.shared.generated.resources.mp_replay_first
import com.agustin.tarati.shared.generated.resources.mp_replay_last
import com.agustin.tarati.shared.generated.resources.mp_replay_next
import com.agustin.tarati.shared.generated.resources.mp_replay_previous
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiIcons
import org.koin.compose.koinInject

/**
 * Visor de **replay** de una partida multijugador terminada (Tarati Six). Reconstruye la partida
 * jugada a jugada desde el historial persistido ([MpGameDetailViewModel]) y la reproduce sobre el
 * mismo renderer del juego ([Board25Pane]), read-only. Navegación completa con [MpReplayControls]
 * (⏮ ◀ slider ▶ ⏭) y la grilla de jugadas ([MpMoveGrid], click-to-jump + resaltado).
 *
 * Layout adaptativo, paridad con `GameDetailsScreen` del single: en pantalla ancha tablero y panel
 * lado a lado; en compacto, apilados.
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
        Board25Pane(
            state = boardState,
            seatIsAI = seatIsAI,
            selection = null,
            legalTargets = emptySet(),
            threatened = emptySet(),
            lastMove = ui.lastMove,
            converted = ui.converted,
            boardVisual = boardVisual,
            onVertexTap = {},
            modifier = m.padding(12.dp),
        )
    }

    val panel: @Composable (Modifier) -> Unit = { m ->
        MpReplayPanel(
            modifier = m,
            ui = ui,
            seats = boardState.seats,
            nameByColor = nameByColor,
            onMoveToIndex = onMoveToIndex,
            onFirst = onFirst,
            onPrev = onPrev,
            onNext = onNext,
            onLast = onLast,
        )
    }

    // Adaptativo al contenedor real (no al layout global): apaisado → tablero y panel lado a lado;
    // vertical (p. ej. el companion panel angosto) → apilados. Paridad con `GameDetailsContent` (single).
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                board(Modifier.weight(1.3f).fillMaxHeight())
                panel(Modifier.weight(1f).fillMaxHeight().padding(12.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                board(Modifier.fillMaxWidth().weight(1.4f))
                panel(Modifier.fillMaxWidth().weight(1f).padding(12.dp))
            }
        }
    }
}

/** Panel del replay: resultado final + controles de navegación + grilla de jugadas navegable. */
@Composable
private fun MpReplayPanel(
    modifier: Modifier,
    ui: MpGameDetailUiState,
    seats: List<Seat>,
    nameByColor: Map<PlayerColor, String>,
    onMoveToIndex: (Int) -> Unit,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ui.result?.let { result ->
            Text(
                text = mpResultMessage(result, nameByColor),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        MpReplayControls(
            moveIndex = ui.moveIndex,
            plyCount = ui.history.size,
            onFirst = onFirst,
            onPrev = onPrev,
            onNext = onNext,
            onLast = onLast,
            onSeek = onMoveToIndex,
        )

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            elevation = CardDefaults.cardElevation(1.dp),
        ) {
            MpMoveGrid(
                modifier = Modifier.fillMaxSize(),
                seats = seats,
                history = ui.history,
                currentPly = ui.moveIndex,
                onCellClick = onMoveToIndex,
            )
        }
    }
}

/**
 * Barra de navegación del replay: primera (⏮), anterior (◀), un slider sobre las jugadas, siguiente
 * (▶) y última (⏭). [moveIndex] es el cursor (−1 = inicial; `plyCount − 1` = última jugada). El
 * slider mapea `0..plyCount` (0 = posición inicial).
 */
@Composable
private fun MpReplayControls(
    moveIndex: Int,
    plyCount: Int,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TooltipIconButton(
            tooltip = localizedString(Res.string.mp_replay_first),
            onClick = onFirst,
            enabled = moveIndex > -1,
        ) {
            Icon(TaratiIcons.SkipPrevious, contentDescription = localizedString(Res.string.mp_replay_first))
        }
        TooltipIconButton(
            tooltip = localizedString(Res.string.mp_replay_previous),
            onClick = onPrev,
            enabled = moveIndex > -1,
        ) {
            Icon(TaratiIcons.KeyboardArrowLeft, contentDescription = localizedString(Res.string.mp_replay_previous))
        }

        if (plyCount > 0) {
            Slider(
                value = (moveIndex + 1).toFloat(),
                onValueChange = { onSeek(it.toInt() - 1) },
                valueRange = 0f..plyCount.toFloat(),
                steps = (plyCount - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "${moveIndex + 1}/$plyCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )

        TooltipIconButton(
            tooltip = localizedString(Res.string.mp_replay_next),
            onClick = onNext,
            enabled = moveIndex < plyCount - 1,
        ) {
            Icon(TaratiIcons.KeyboardArrowRight, contentDescription = localizedString(Res.string.mp_replay_next))
        }
        TooltipIconButton(
            tooltip = localizedString(Res.string.mp_replay_last),
            onClick = onLast,
            enabled = moveIndex < plyCount - 1,
        ) {
            Icon(TaratiIcons.SkipNext, contentDescription = localizedString(Res.string.mp_replay_last))
        }
    }
}
