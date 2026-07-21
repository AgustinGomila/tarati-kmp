package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.network.models.ProfileStatsDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.no_games_found
import com.agustin.tarati.shared.generated.resources.profile_games_played
import com.agustin.tarati.shared.generated.resources.profile_stat_draw_short
import com.agustin.tarati.shared.generated.resources.profile_stat_loss_short
import com.agustin.tarati.shared.generated.resources.profile_stat_win_short
import com.agustin.tarati.ui.components.InfiniteScrollEffect
import com.agustin.tarati.ui.components.loadingMoreIndicator
import com.agustin.tarati.ui.theme.TaratiIcons

@Composable
internal fun GameHistoryTab(
    viewModel: IOnlineLobbyViewModel,
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
) {
    val state by viewModel.history.collectAsState()
    val myStats by viewModel.myStats.collectAsState()
    val listState = rememberLazyListState()

    // Cargar al entrar en el tab (solo si no hay datos ya).
    LaunchedEffect(Unit) {
        if (state.games.isEmpty() && !state.isLoading) {
            viewModel.loadHistory()
        }
    }

    // Paginación automática al acercarse al final de la lista.
    InfiniteScrollEffect(listState) { viewModel.loadMoreHistory() }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Estadísticas sumarizadas del control de tiempo seleccionado ─────────
        myStats?.let { stats ->
            HistoryStatsRow(stats = stats, timeControlFilter = state.filters.timeControl)
        }

        // ── Filtros ────────────────────────────────────────────────────────────
        GameHistoryFilterRow(
            filters = state.filters,
            onTimeControlFilter = viewModel::setTimeControlFilter,
            onResultFilter = viewModel::setResultFilter,
            onRatedFilter = viewModel::setRatedFilter,
        )

        // ── Contenido ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> CenteredLoader()

                state.error != null -> CenteredMessage(
                    text = localizedString(Res.string.error, state.error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                )

                state.games.isEmpty() -> CenteredMessage(
                    text = localizedString(Res.string.no_games_found),
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.games, key = { it.gameId }) { game ->
                        GameHistoryCard(
                            game = game,
                            onClick = onNavigateToGameDetails?.let { cb -> { cb(game.gameId) } },
                        )
                    }
                    loadingMoreIndicator(state.isLoadingMore)
                }
            }
        }
    }
}

/**
 * Fila de estadísticas sumarizadas del control de tiempo seleccionado en los filtros
 * (o del total si no hay ninguno), con el estilo [LobbyStatsRow] de las demás pestañas.
 * Refleja siempre el control de tiempo, independiente del filtro de resultado.
 */
@Composable
private fun HistoryStatsRow(stats: ProfileStatsDto, timeControlFilter: String?) {
    val s = stats.forTimeControl(timeControlFilter)
    LobbyStatsRow(
        stats = listOf(
            StatChip(
                icon = TaratiIcons.Leaderboard,
                text = localizedString(Res.string.profile_games_played, s.games),
            ),
            StatChip(
                icon = TaratiIcons.EmojiEvents,
                text = "${s.wins}${localizedString(Res.string.profile_stat_win_short)}" +
                        " / ${s.draws}${localizedString(Res.string.profile_stat_draw_short)}" +
                        " / ${s.losses}${localizedString(Res.string.profile_stat_loss_short)}",
            ),
        ),
    )
}
