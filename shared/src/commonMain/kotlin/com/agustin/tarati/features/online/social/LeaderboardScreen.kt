package com.agustin.tarati.features.online.social


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.network.models.LeaderboardEntryDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.profile_leaderboard
import com.agustin.tarati.shared.generated.resources.profile_no_leaderboard_data
import com.agustin.tarati.shared.generated.resources.refresh
import com.agustin.tarati.ui.components.SupporterBadge
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.supporterNameColor
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.timeControlIcon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (userId: String) -> Unit,
    viewModel: ILeaderboardViewModel = koinViewModel<LeaderboardViewModel>(),
) {
    val state by viewModel.leaderboardState.collectAsState()
    val selectedTc by viewModel.selectedTc.collectAsState()
    val timeControls = TimeControl.list()
    val selectedIndex = timeControls.indexOf(selectedTc).coerceAtLeast(0)

    TaratiBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TaratiTopBar(
                    title = localizedString(Res.string.profile_leaderboard),
                    navigationType = TopBarNavigationType.Back,
                    onNavigationClick = onBack,
                    actions = {
                        TooltipIconButton(
                            tooltip = localizedString(Res.string.refresh),
                            onClick = viewModel::refresh,
                        ) {
                            Icon(
                                imageVector = TaratiIcons.Replay,
                                contentDescription = localizedString(Res.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 0.dp,
                ) {
                    timeControls.forEach { tc ->
                        Tab(
                            selected = selectedTc == tc,
                            onClick = { viewModel.selectTimeControl(tc) },
                            text = { Text(tc.replaceFirstChar { it.titlecase() }) },
                            icon = {
                                Icon(
                                    timeControlIcon(tc),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    state.error != null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = localizedString(Res.string.error, state.error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    state.entries.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = localizedString(Res.string.profile_no_leaderboard_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(state.entries, key = { _, e -> e.id }) { _, entry ->
                            LeaderboardEntryRow(
                                entry = entry,
                                onClick = { onNavigateToProfile(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Entry row ─────────────────────────────────────────────────────────────────

private val rankColors = mapOf(
    1 to Color(0xFFFFD700),
    2 to Color(0xFFC0C0C0),
    3 to Color(0xFFCD7F32),
)

@Composable
private fun LeaderboardEntryRow(entry: LeaderboardEntryDto, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Rank
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = rankColors[entry.rank] ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )

            // Name + country
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.isSupporter) supporterNameColor() else Color.Unspecified,
                    )
                    if (entry.isSupporter) SupporterBadge(size = 14.dp)
                }
                if (!entry.country.isNullOrBlank()) {
                    Text(
                        text = entry.country,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Rating + W/D/L
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.rating}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${entry.wins}W ${entry.draws}D ${entry.losses}L",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = TaratiIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
