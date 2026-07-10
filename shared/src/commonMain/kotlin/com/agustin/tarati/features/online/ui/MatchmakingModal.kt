package com.agustin.tarati.features.online.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.affects_your_rating
import com.agustin.tarati.shared.generated.resources.allow_spectators
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.find_an_opponent
import com.agustin.tarati.shared.generated.resources.just_for_fun
import com.agustin.tarati.shared.generated.resources.lobby_new_search
import com.agustin.tarati.shared.generated.resources.offer_draw
import com.agustin.tarati.shared.generated.resources.rated_game
import com.agustin.tarati.shared.generated.resources.resign
import com.agustin.tarati.shared.generated.resources.start_search
import com.agustin.tarati.shared.generated.resources.time_control
import com.agustin.tarati.shared.generated.resources.time_control_blitz
import com.agustin.tarati.shared.generated.resources.time_control_bullet
import com.agustin.tarati.shared.generated.resources.time_control_classical
import com.agustin.tarati.shared.generated.resources.time_control_rapid
import com.agustin.tarati.shared.generated.resources.your_color
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject

/**
 * Dialog para iniciar matchmaking
 *
 * Permite al usuario:
 * - Seleccionar time control (bullet/blitz/rapid/classical)
 * - Elegir si es rated o casual
 * - Iniciar búsqueda
 * - Cancelar búsqueda en progreso
 * - Ver tiempo estimado de espera
 *
 * ## Estados:
 *
 * ### Idle:
 * ```
 * ┌──────────────────────────┐
 * │  Find an Opponent        │
 * │                          │
 * │  Time Control:           │
 * │  ○ Bullet (1+0)          │
 * │  ● Blitz (3+2)           │
 * │  ○ Rapid (10+5)          │
 * │  ○ Classical (30+20)     │
 * │                          │
 * │  Rated: [ON/OFF]         │
 * │                          │
 * │  [Cancel] [Start Search] │
 * └──────────────────────────┘
 * ```
 *
 * ### Searching:
 * ```
 * ┌──────────────────────────┐
 * │  Finding Opponent...     │
 * │                          │
 * │  [spinner]               │
 * │                          │
 * │  Searching for blitz     │
 * │  Estimated: ~30 seconds  │
 * │                          │
 * │  [Cancel Search]         │
 * └──────────────────────────┘
 * ```
 *
 * ### Match Found:
 * ```
 * ┌──────────────────────────┐
 * │  Opponent Found!         │
 * │                          │
 * │  vs. player_123          │
 * │  Rating: 1543            │
 * │                          │
 * │  Starting game...        │
 * └──────────────────────────┘
 * ```
 *
 * @param matchmakingState Estado actual del matchmaking
 * @param onStartSearch Callback cuando el usuario inicia búsqueda (timeControl, rated)
 * @param onCancelSearch Callback cuando el usuario cancela búsqueda
 * @param onDismiss Callback cuando se cierra el dialog
 */
@Composable
fun MatchmakingModal(
    matchmakingState: MatchmakingState,
    onStartSearch: (timeControl: String, rated: Boolean, spectatingAllowed: Boolean) -> Unit,
    onCancelSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayerTurn: Boolean = false,
    /** When non-null, the modal shows in-game status and resign/draw actions instead of matchmaking. */
    currentOnlineGame: OnlineGame? = null,
    onResign: () -> Unit = {},
    onOfferDraw: () -> Unit = {},
    settings: SettingsRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()

    // Persist last-used preferences so the dialog reopens with the player's choices.
    val savedTimeControl by settings.onlineTimeControl.collectAsState(TimeControl.BLITZ.key)
    val savedRated by settings.onlineRated.collectAsState(true)
    val savedSpectatingAllowed by settings.onlineSpectatingAllowed.collectAsState(true)

    var selectedTimeControl by remember(savedTimeControl) { mutableStateOf(savedTimeControl) }
    var isRated by remember(savedRated) { mutableStateOf(savedRated) }
    var spectatingAllowed by remember(savedSpectatingAllowed) { mutableStateOf(savedSpectatingAllowed) }
    var confirmResign by remember { mutableStateOf(false) }

    // In-game mode: show game status and actions when there's an active online game.
    if (currentOnlineGame != null && currentOnlineGame.status == OnlineGameStatus.InProgress) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "${currentOnlineGame.opponentInfo.username} · ${currentOnlineGame.timeControl.initial / 60}+${currentOnlineGame.timeControl.increment}",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (confirmResign) {
                        LocalizedText(Res.string.resign, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            localizedString(Res.string.your_color, currentOnlineGame.yourColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (confirmResign) {
                    Button(onClick = { onResign(); onDismiss() }) { LocalizedText(Res.string.resign) }
                } else {
                    Button(
                        onClick = { onOfferDraw(); onDismiss() },
                        enabled = isPlayerTurn
                    )
                    {
                        LocalizedText(Res.string.offer_draw)
                    }
                }
            },
            dismissButton = {
                if (confirmResign) {
                    TextButton(onClick = { confirmResign = false }) { LocalizedText(Res.string.cancel) }
                } else {
                    TextButton(onClick = { confirmResign = true }) {
                        LocalizedText(Res.string.resign, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            modifier = modifier
        )
        return
    }

    // El modal siempre muestra el selector de configuración.
    // El estado "buscando" se muestra en el pill del top bar, no en el modal.
    val isSearching = matchmakingState is MatchmakingState.Searching

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            LocalizedText(
                Res.string.find_an_opponent,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LocalizedText(
                    Res.string.time_control,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeControlOption(
                        labelRes = Res.string.time_control_bullet,
                        value = TimeControl.BULLET.key,
                        selected = selectedTimeControl == TimeControl.BULLET.key,
                        onSelect = {
                            selectedTimeControl = TimeControl.BULLET.key
                            scope.launch { settings.setOnlineTimeControl(TimeControl.BULLET.key) }
                        }
                    )
                    TimeControlOption(
                        labelRes = Res.string.time_control_blitz,
                        value = TimeControl.BLITZ.key,
                        selected = selectedTimeControl == TimeControl.BLITZ.key,
                        onSelect = {
                            selectedTimeControl = TimeControl.BLITZ.key
                            scope.launch { settings.setOnlineTimeControl(TimeControl.BLITZ.key) }
                        }
                    )
                    TimeControlOption(
                        labelRes = Res.string.time_control_rapid,
                        value = TimeControl.RAPID.key,
                        selected = selectedTimeControl == TimeControl.RAPID.key,
                        onSelect = {
                            selectedTimeControl = TimeControl.RAPID.key
                            scope.launch { settings.setOnlineTimeControl(TimeControl.RAPID.key) }
                        }
                    )
                    TimeControlOption(
                        labelRes = Res.string.time_control_classical,
                        value = TimeControl.CLASSICAL.key,
                        selected = selectedTimeControl == TimeControl.CLASSICAL.key,
                        onSelect = {
                            selectedTimeControl = TimeControl.CLASSICAL.key
                            scope.launch { settings.setOnlineTimeControl(TimeControl.CLASSICAL.key) }
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        LocalizedText(Res.string.rated_game, style = MaterialTheme.typography.bodyMedium)
                        LocalizedText(
                            if (isRated) Res.string.affects_your_rating else Res.string.just_for_fun,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isRated,
                        onCheckedChange = {
                            isRated = it
                            scope.launch { settings.setOnlineRated(it) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocalizedText(Res.string.allow_spectators, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = spectatingAllowed,
                        onCheckedChange = {
                            spectatingAllowed = it
                            scope.launch { settings.setOnlineSpectatingAllowed(it) }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartSearch(selectedTimeControl, isRated, spectatingAllowed)
                    onDismiss()
                }
            ) {
                LocalizedText(if (isSearching) Res.string.lobby_new_search else Res.string.start_search)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                LocalizedText(Res.string.cancel)
            }
        },
        modifier = modifier,
    )
}

/**
 * Radio button option para time control
 */
@Composable
private fun TimeControlOption(
    labelRes: StringResource,
    value: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // handled by Row
        )
        Spacer(Modifier.width(8.dp))
        LocalizedText(
            labelRes,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}