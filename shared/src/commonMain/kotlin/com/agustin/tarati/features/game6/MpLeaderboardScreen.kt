package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.social.LeaderboardRow
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.mp_leaderboard_title
import com.agustin.tarati.shared.generated.resources.profile_no_leaderboard_data
import com.agustin.tarati.shared.generated.resources.refresh
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import org.koin.compose.koinInject

/**
 * Clasificación multijugador (Tarati Six) — pantalla separada, accesible desde la TopBar del lobby MP.
 *
 * Cosméticamente consistente con [com.agustin.tarati.features.online.social.LeaderboardScreen] (mismo
 * fondo, TopBar y estilo de fila), pero **sin tabs de time control**: MP tiene un único bucket de
 * rating. Cada fila muestra rating + W/S/L (victorias / compartidas / derrotas), sin empates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpLeaderboardScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (userId: String) -> Unit,
    viewModel: IMpLeaderboardViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsState()

    TaratiBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TaratiTopBar(
                    title = localizedString(Res.string.mp_leaderboard_title),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()

                    state.error != null -> Text(
                        text = localizedString(Res.string.error, state.error.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    state.entries.isEmpty() -> Text(
                        text = localizedString(Res.string.profile_no_leaderboard_data),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(state.entries, key = { _, e -> e.id }) { _, entry ->
                            LeaderboardRow(
                                rank = entry.rank,
                                name = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.username,
                                country = entry.country,
                                rating = entry.rating,
                                statsLine = "${entry.wins}W ${entry.shared}S ${entry.losses}L",
                                isSupporter = entry.isSupporter,
                                onClick = { onNavigateToProfile(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

