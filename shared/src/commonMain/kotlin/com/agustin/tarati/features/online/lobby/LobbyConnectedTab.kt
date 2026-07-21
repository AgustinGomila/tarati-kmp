package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.ui.ChallengeDialog
import com.agustin.tarati.network.models.OnlineUserDto
import com.agustin.tarati.network.models.OnlineUserStatus
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.lobby_count_online
import com.agustin.tarati.shared.generated.resources.lobby_count_playing
import com.agustin.tarati.shared.generated.resources.lobby_filter_all
import com.agustin.tarati.shared.generated.resources.lobby_filter_registered_only
import com.agustin.tarati.shared.generated.resources.lobby_no_players_match_filters
import com.agustin.tarati.shared.generated.resources.lobby_online_users_section
import com.agustin.tarati.shared.generated.resources.lobby_status_in_lobby
import com.agustin.tarati.shared.generated.resources.lobby_status_playing
import com.agustin.tarati.shared.generated.resources.social_challenge
import com.agustin.tarati.shared.generated.resources.you
import com.agustin.tarati.ui.components.SupporterBadge
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.supporterNameColor
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.launch

// ── Tab: Conectados ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedUsersTab(
    viewModel: IOnlineLobbyViewModel,
    currentUserId: String?,
    isCurrentUserGuest: Boolean,
    onlineGameViewModel: IOnlineGameViewModel,
    onNavigateToProfile: ((String) -> Unit)?,
) {
    val users by viewModel.onlineUsers.collectAsState()
    var challengeTarget by remember { mutableStateOf<OnlineUserDto?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        viewModel.startConnectedPolling()
        onDispose { viewModel.stopConnectedPolling() }
    }

    // ── Filtros locales ────────────────────────────────────────────────────────
    var statusFilter by remember { mutableStateOf<OnlineUserStatus?>(null) }
    var registeredOnly by remember { mutableStateOf(false) }

    val displayUsers = users
        .let { if (statusFilter != null) it.filter { u -> u.status == statusFilter } else it }
        .let { if (registeredOnly) it.filter { u -> !u.isGuest } else it }

    challengeTarget?.let { target ->
        ChallengeDialog(
            targetName = target.displayName,
            forceNonRated = isCurrentUserGuest || target.isGuest,
            onConfirm = { tc, rated ->
                challengeTarget = null
                scope.launch { onlineGameViewModel.sendChallenge(target.userId, tc, rated) }
            },
            onDismiss = { challengeTarget = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LobbyStatsRow(
            stats = listOf(
                StatChip(
                    icon = TaratiIcons.Group,
                    text = localizedString(Res.string.lobby_count_online, users.size),
                ),
                StatChip(
                    icon = TaratiIcons.PlayArrow,
                    text = localizedString(
                        Res.string.lobby_count_playing,
                        users.count { it.status == OnlineUserStatus.PLAYING },
                    ),
                ),
            ),
        )
        ConnectedUsersFilterBar(
            statusFilter = statusFilter,
            onStatusFilter = { statusFilter = it },
            registeredOnly = registeredOnly,
            onRegisteredOnlyToggle = { registeredOnly = !registeredOnly },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                users.isEmpty() ->
                    CenteredMessage(text = localizedString(Res.string.lobby_online_users_section))

                displayUsers.isEmpty() -> CenteredMessage(
                    text = localizedString(Res.string.lobby_no_players_match_filters),
                )

                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(displayUsers, key = { it.userId }) { user ->
                        ConnectedUserRow(
                            user = user,
                            isSelf = user.userId == currentUserId,
                            onClick = if (!user.isGuest && onNavigateToProfile != null) {
                                { onNavigateToProfile(user.userId) }
                            } else null,
                            onChallenge = if (!user.isGuest && user.userId != currentUserId &&
                                user.status == OnlineUserStatus.IN_LOBBY && user.acceptsChallenges
                            ) {
                                { challengeTarget = user }
                            } else null,
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedUsersFilterBar(
    statusFilter: OnlineUserStatus?,
    onStatusFilter: (OnlineUserStatus?) -> Unit,
    registeredOnly: Boolean,
    onRegisteredOnlyToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        // Fila 1: chips de estado (ancho completo)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(null, OnlineUserStatus.PLAYING, OnlineUserStatus.IN_LOBBY)
                .forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            onStatusFilter(if (statusFilter == status && status != null) null else status)
                        },
                        label = {
                            Text(
                                when (status) {
                                    null -> localizedString(Res.string.lobby_filter_all)
                                    OnlineUserStatus.PLAYING -> localizedString(Res.string.lobby_status_playing)
                                    OnlineUserStatus.IN_LOBBY -> localizedString(Res.string.lobby_status_in_lobby)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
        }
        // Fila 2: filtro secundario alineado a la derecha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = registeredOnly,
                onClick = onRegisteredOnlyToggle,
                label = { LocalizedText(Res.string.lobby_filter_registered_only) },
                leadingIcon = { Icon(TaratiIcons.Person, null, Modifier.size(14.dp)) },
            )
        }
    }
}

@Composable
private fun ConnectedUserRow(
    user: OnlineUserDto,
    isSelf: Boolean,
    onClick: (() -> Unit)?,
    onChallenge: (() -> Unit)?,
) {
    val statusColor = if (user.status == OnlineUserStatus.PLAYING) PositiveGreen
    else MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(statusColor, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (isSelf) "${user.displayName} (${localizedString(Res.string.you)})"
                    else user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelf) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (user.isSupporter) supporterNameColor() else Color.Unspecified,
                )
                if (user.isSupporter) SupporterBadge(size = 14.dp)
            }
            Text(
                localizedString(
                    if (user.status == OnlineUserStatus.PLAYING) Res.string.lobby_status_playing
                    else Res.string.lobby_status_in_lobby
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (user.status == OnlineUserStatus.PLAYING) PositiveGreen
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!user.isGuest && user.ratingBlitz != null) {
            Text(
                "${user.ratingBlitz}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        if (onChallenge != null) {
            TooltipIconButton(
                tooltip = localizedString(Res.string.social_challenge),
                onClick = onChallenge,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    TaratiIcons.PlayArrow,
                    contentDescription = localizedString(Res.string.social_challenge),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

