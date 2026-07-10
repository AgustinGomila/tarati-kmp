package com.agustin.tarati.features.online.ui


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.MatchmakingTicket
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel_search
import com.agustin.tarati.shared.generated.resources.confirm_disconnect
import com.agustin.tarati.shared.generated.resources.connect
import com.agustin.tarati.shared.generated.resources.connecting
import com.agustin.tarati.shared.generated.resources.connection_error
import com.agustin.tarati.shared.generated.resources.disconnect
import com.agustin.tarati.shared.generated.resources.exit_spectator
import com.agustin.tarati.shared.generated.resources.find_match
import com.agustin.tarati.shared.generated.resources.just_for_fun
import com.agustin.tarati.shared.generated.resources.offline
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.reconnecting
import com.agustin.tarati.shared.generated.resources.retry
import com.agustin.tarati.ui.components.ConfirmDialog
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.theme.TaratiIcons

/**
 * Pill del modo espectador en el top bar.
 *
 * Muestra "👁 Blancas vs Negras [✕]" mientras se observa una partida.
 * Al tocar ✕ se sale del modo espectador.
 */
@Composable
fun SpectatingPill(
    state: SpectatingState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = TaratiIcons.Visibility,
                contentDescription = "${state.whitePlayer.username} vs ${state.blackPlayer.username}",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TooltipIconButton(
                tooltip = localizedString(Res.string.exit_spectator),
                onClick = onStop,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = TaratiIcons.Close,
                    contentDescription = localizedString(Res.string.exit_spectator),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Barra de búsqueda online para el top bar.
 *
 * - Sin búsqueda activa: solo muestra el ícono [🔍].
 * - Con búsqueda activa: pill `[[🔄] [TC] · [Rated] [✕]]` + ícono [🔍].
 * - MatchFound: pill con ✓ (sin botón cancelar) + ícono [🔍].
 */
@Composable
fun OnlineSearchBar(
    connectionState: ConnectionState,
    matchmakingState: MatchmakingState,
    onCreateSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        val searching = matchmakingState as? MatchmakingState.Searching
        val matchFound = matchmakingState is MatchmakingState.MatchFound

        AnimatedVisibility(
            visible = searching != null || matchFound,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            SearchingPill(
                ticket = searching?.ticket,
                matchFound = matchFound,
                onCancel = onCancelSearch,
            )
        }

        SearchButton(
            connectionState = connectionState,
            onClick = onCreateSearch,
        )
    }
}

@Composable
private fun SearchingPill(
    ticket: MatchmakingTicket?,
    matchFound: Boolean,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (matchFound) 10.dp else 2.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (matchFound) {
                Icon(
                    imageVector = TaratiIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            if (ticket != null) {
                Text(
                    text = ticket.timeControl.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                )
                Text(
                    text = localizedString(if (ticket.rated) Res.string.rated else Res.string.just_for_fun),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }

            if (!matchFound) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.cancel_search),
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = TaratiIcons.Close,
                        contentDescription = localizedString(Res.string.cancel_search),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Botón único para crear búsquedas online.
 *
 * Reemplaza al antiguo par Connect + Search. Muestra un solo ícono cuyo estado visual
 * refleja la conexión:
 *
 * | Estado         | Ícono               | Acción al tocar               |
 * |----------------|---------------------|-------------------------------|
 * | Offline        | 🔍 Search           | Conectar + abrir modal        |
 * | Connecting     | ⏳ spinner          | — (deshabilitado)             |
 * | Reconnecting   | ⏳ spinner          | — (deshabilitado)             |
 * | Online         | 🔍 Search           | Abrir modal directamente      |
 * | Error          | ⚠ Error             | Reintentar conexión + modal   |
 */
@Composable
fun SearchButton(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = connectionState,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "SearchButtonState",
    ) { state ->
        when (state) {
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting -> {
                IconButton(onClick = {}, enabled = false) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ConnectionState.Error -> {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.retry),
                    onClick = onClick,
                ) {
                    Icon(
                        imageVector = TaratiIcons.Error,
                        contentDescription = localizedString(Res.string.retry),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.find_match),
                    onClick = onClick,
                ) {
                    Icon(
                        imageVector = TaratiIcons.Search,
                        contentDescription = localizedString(Res.string.find_match),
                    )
                }
            }
        }
    }
}

/**
 * Botón que muestra y gestiona el estado de la conexión online.
 *
 * ## Estados
 *
 * | Estado          | Apariencia                        | Acción            |
 * |-----------------|-----------------------------------|-------------------|
 * | `Offline`       | ☁ Conectar                        | `onConnect()`     |
 * | `Connecting`    | ⏳ Conectando…                    | ninguna           |
 * | `Reconnecting`  | ⏳ username Reconectando…         | ninguna           |
 * | `Online`        | ● username  ✕                     | abre confirmación |
 * | `Error`         | ⚠ Reintentar                      | `onRetry()`       |
 *
 * El estado `Online` muestra el username del usuario conectado y un botón ✕
 * que abre un `AlertDialog` de confirmación antes de llamar `onDisconnect()`.
 * Esto evita desconexiones accidentales durante una partida en curso.
 *
 * @param connectionState Estado actual de la conexión.
 * @param onConnect       Callback para iniciar la conexión.
 * @param onDisconnect    Callback ejecutado tras confirmar la desconexión.
 * @param onRetry         Callback para reintentar tras un error.
 */
@Composable
fun ConnectionButton(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        ConfirmDialog(
            title = Res.string.disconnect,
            body = Res.string.confirm_disconnect,
            confirmLabel = Res.string.disconnect,
            destructive = true,
            onConfirm = onDisconnect,
            onDismiss = { showDisconnectDialog = false },
        )
    }

    AnimatedContent(
        targetState = connectionState,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "ConnectionButtonState",
    ) { state ->
        when (state) {

            // ── Offline ───────────────────────────────────────────────────────
            is ConnectionState.Offline -> {
                TextButton(onClick = onConnect) {
                    Icon(
                        imageVector = TaratiIcons.CloudOff,
                        contentDescription = localizedString(Res.string.offline),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    LocalizedText(
                        Res.string.connect,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Connecting ────────────────────────────────────────────────────
            is ConnectionState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    LocalizedText(
                        Res.string.connecting,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Reconnecting ──────────────────────────────────────────────────
            is ConnectionState.Reconnecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.userInfo.username,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    LocalizedText(
                        Res.string.reconnecting,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // ── Online ────────────────────────────────────────────────────────
            is ConnectionState.Online -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Indicador de conexión activa
                    Icon(
                        imageVector = TaratiIcons.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state.userInfo.username,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // Botón de desconexión — abre diálogo de confirmación
                    IconButton(
                        onClick = { showDisconnectDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = TaratiIcons.Close,
                            contentDescription = localizedString(Res.string.disconnect),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            is ConnectionState.Error -> {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = TaratiIcons.Error,
                        contentDescription = localizedString(Res.string.connection_error),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    LocalizedText(
                        Res.string.retry,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
