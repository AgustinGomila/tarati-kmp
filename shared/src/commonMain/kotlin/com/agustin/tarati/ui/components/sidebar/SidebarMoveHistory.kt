package com.agustin.tarati.ui.components.sidebar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.colorNameRes
import com.agustin.tarati.core.domain.game.play.GameEndReason.DRAW_AGREEMENT
import com.agustin.tarati.core.domain.game.play.GameEndReason.FIFTY_MOVES
import com.agustin.tarati.core.domain.game.play.GameEndReason.MIT
import com.agustin.tarati.core.domain.game.play.GameEndReason.RESIGNATION
import com.agustin.tarati.core.domain.game.play.GameEndReason.STALEMIT
import com.agustin.tarati.core.domain.game.play.GameEndReason.TIMEOUT
import com.agustin.tarati.core.domain.game.play.GameEndReason.TRIPLE
import com.agustin.tarati.core.domain.game.play.GameEndReason.UNDETERMINED
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.utils.FeatureFlags
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.copied
import com.agustin.tarati.shared.generated.resources.copy_move_history
import com.agustin.tarati.shared.generated.resources.jump_to_current_position
import com.agustin.tarati.shared.generated.resources.move_controls
import com.agustin.tarati.shared.generated.resources.online_lobby
import com.agustin.tarati.shared.generated.resources.player_wins
import com.agustin.tarati.shared.generated.resources.redo
import com.agustin.tarati.shared.generated.resources.save_game
import com.agustin.tarati.shared.generated.resources.saved_games
import com.agustin.tarati.shared.generated.resources.status_draw_agreement
import com.agustin.tarati.shared.generated.resources.status_draw_fifty
import com.agustin.tarati.shared.generated.resources.status_turn
import com.agustin.tarati.shared.generated.resources.status_undetermined
import com.agustin.tarati.shared.generated.resources.status_wins_mit
import com.agustin.tarati.shared.generated.resources.status_wins_resignation
import com.agustin.tarati.shared.generated.resources.status_wins_stalemit
import com.agustin.tarati.shared.generated.resources.status_wins_timeout
import com.agustin.tarati.shared.generated.resources.status_wins_triple
import com.agustin.tarati.shared.generated.resources.undo
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.movelist.MoveHistoryList
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// ── Move history + compact status row ────────────────────────────────────────

/**
 * Sección de historial de movimientos y controles de navegación.
 */
@Composable
internal fun MoveHistorySection(
    modifier: Modifier,
    isLandscape: Boolean,
    sidebarState: SidebarGameState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)? = null,
    onCopyMoveHistory: (moves: List<Move>) -> Unit,
    onGamesLibrary: () -> Unit,
    onOnlineLobby: () -> Unit,
    onSaveGame: () -> Unit,
) {
    var isCopying by remember { mutableStateOf(false) }
    val gameManagerState = sidebarState.gameManagerState
    val currentMoveIndex = gameManagerState.moveIndex
    val moves = gameManagerState.history.getMoves()

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val matchState = remember(gameManagerState.gameState, sidebarState.positionHistory) {
            gameManagerState.gameState.getMatchState(sidebarState.positionHistory)
        }
        GameStatusRow(matchState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedString(Res.string.move_controls).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            TooltipIconButton(
                tooltip = localizedString(Res.string.copy_move_history),
                onClick = {
                    isCopying = true; onCopyMoveHistory(moves)
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000.milliseconds)
                        isCopying = false
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isCopying) TaratiIcons.Done else TaratiIcons.ContentCopy,
                    if (isCopying) localizedString(Res.string.copied)
                    else localizedString(Res.string.copy_move_history),
                    tint = if (isCopying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            TooltipIconButton(
                tooltip = localizedString(Res.string.save_game),
                onClick = onSaveGame,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    TaratiIcons.Save, localizedString(Res.string.save_game),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            TooltipIconButton(
                tooltip = localizedString(Res.string.saved_games),
                onClick = onGamesLibrary,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    TaratiIcons.MenuBook,
                    localizedString(Res.string.saved_games),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (FeatureFlags.ONLINE_ENABLED) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.online_lobby),
                    onClick = onOnlineLobby,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        TaratiIcons.Public,
                        localizedString(Res.string.online_lobby),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        NavigableHistoryList(
            modifier, isLandscape, currentMoveIndex,
            gameManagerState.history, onUndo, onRedo, onMoveToCurrent,
            onMoveToIndex = onMoveToIndex,
            navigationEnabled = sidebarState.navigationEnabled,
        )
    }
}

// ── Compact game status chip ──────────────────────────────────────────────────

@Composable
private fun GameStatusRow(
    matchState: MatchState,
) {
    val winner = matchState.winner
    val result = matchState.gameEndReason
    val gameState = matchState.gameState
    val isOver = winner != null || result in listOf(FIFTY_MOVES, DRAW_AGREEMENT)
    val side = winner ?: gameState.currentTurn

    val bgColor = when (side) {
        WHITE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        BLACK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    }
    val fgColor = when (side) {
        WHITE -> MaterialTheme.colorScheme.onPrimaryContainer
        BLACK -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val text = when {
        result == FIFTY_MOVES -> localizedString(Res.string.status_draw_fifty)
        result == UNDETERMINED -> localizedString(Res.string.status_undetermined)
        result == DRAW_AGREEMENT -> localizedString(Res.string.status_draw_agreement)
        winner != null -> {
            val n = localizedString(winner.colorNameRes)
            when (result) {
                MIT -> localizedString(Res.string.status_wins_mit, n)
                STALEMIT -> localizedString(Res.string.status_wins_stalemit, n)
                TRIPLE -> localizedString(Res.string.status_wins_triple, n)
                TIMEOUT -> localizedString(Res.string.status_wins_timeout, n)
                RESIGNATION -> localizedString(Res.string.status_wins_resignation, n)
                else -> localizedString(Res.string.player_wins, n)
            }
        }

        else -> localizedString(
            Res.string.status_turn,
            localizedString(gameState.currentTurn.colorNameRes)
        )
    }

    val bc = getBoardColors()
    val dotColor = if (side == WHITE) bc.whiteCobColor else bc.blackCobColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(dotColor, size.minDimension / 2f)
        }
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isOver) FontWeight.SemiBold else FontWeight.Normal,
            color = fgColor
        )
    }
}

// ── Navigable history list ────────────────────────────────────────────────────

/**
 * Altura mínima de pantalla (en dp) necesaria para mostrar el panel de historial
 * de movimientos cuando el dispositivo está en landscape.
 *
 * Los teléfonos en landscape tienen ~360–411 dp de alto; las tablets en landscape
 * tienen 600 dp o más. El umbral de 500 dp separa ambos casos de forma robusta.
 */
private const val HISTORY_MIN_HEIGHT_DP = 500

/**
 * Controles de navegación por historial.
 *
 * El panel expandible de historial se muestra cuando hay suficiente espacio
 * vertical disponible, independientemente de la orientación:
 * - Portrait: siempre visible.
 * - Landscape en teléfono (~360–411 dp): solo Undo/Redo. El historial completo
 *   está disponible en el BottomGameBar.
 * - Landscape en tablet (≥ 500 dp): también visible, ya que hay espacio suficiente.
 */
@Composable
private fun NavigableHistoryList(
    modifier: Modifier, isLandscape: Boolean, currentMoveIndex: Int,
    history: StableHistoryList, onUndo: () -> Unit, onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)? = null,
    /** `false` grisa undo/redo/saltar (online en curso / espectador); ver [SidebarGameState]. */
    navigationEnabled: Boolean = true,
) {
    val moves = history.getMoves()
    val density = LocalDensity.current
    val screenHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val showHistoryPanel = !isLandscape || screenHeightDp >= HISTORY_MIN_HEIGHT_DP.dp

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onUndo, enabled = navigationEnabled && currentMoveIndex >= 0,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
        ) {
            Icon(TaratiIcons.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp)); LocalizedText(Res.string.undo)
        }
        OutlinedButton(
            onRedo, enabled = navigationEnabled && currentMoveIndex < moves.size - 1,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
        ) {
            LocalizedText(Res.string.redo); Spacer(Modifier.width(4.dp))
            Icon(TaratiIcons.ArrowForward, null, Modifier.size(18.dp))
        }
    }

    if (showHistoryPanel) {
        Card(
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            MoveHistoryList(
                history = history,
                moveIndex = currentMoveIndex,
                // Sin navegación (online en curso) los clicks de la lista quedan inertes.
                onMoveClick = if (navigationEnabled) onMoveToIndex else null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }

    if (navigationEnabled && currentMoveIndex != moves.size - 1)
        Button(onMoveToCurrent, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            LocalizedText(Res.string.jump_to_current_position)
        }
}
