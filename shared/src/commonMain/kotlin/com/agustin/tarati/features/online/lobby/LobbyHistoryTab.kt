package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.ProfileStatsDto
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.casual_info_card
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.moves
import com.agustin.tarati.shared.generated.resources.no_games_found
import com.agustin.tarati.shared.generated.resources.profile_games_played
import com.agustin.tarati.shared.generated.resources.profile_stat_draw_short
import com.agustin.tarati.shared.generated.resources.profile_stat_loss_short
import com.agustin.tarati.shared.generated.resources.profile_stat_win_short
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.rated_info_card
import com.agustin.tarati.shared.generated.resources.rating
import com.agustin.tarati.shared.generated.resources.result
import com.agustin.tarati.shared.generated.resources.win
import com.agustin.tarati.ui.components.carditem.GameCardItem
import com.agustin.tarati.ui.components.game.CobColorIndicator
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

    // Paginación automática: cuando faltan 3 ítems para el final, cargar más.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreHistory()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Estadísticas sumarizadas del control de tiempo seleccionado ─────────
        myStats?.let { stats ->
            HistoryStatsRow(stats = stats, timeControlFilter = state.filters.timeControl)
        }

        // ── Filtros ────────────────────────────────────────────────────────────
        HistoryFilterRow(state = state, viewModel = viewModel)

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
                    itemsIndexed(state.games, key = { _, g -> g.gameId }) { _, game ->
                        HistoryGameCard(
                            game = game,
                            onClick = onNavigateToGameDetails?.let { cb -> { cb(game.gameId) } },
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
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
    val s = when (timeControlFilter) {
        TimeControl.BULLET.key -> stats.bullet
        TimeControl.BLITZ.key -> stats.blitz
        TimeControl.RAPID.key -> stats.rapid
        TimeControl.CLASSICAL.key -> stats.classical
        else -> stats.total
    }
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

@Composable
private fun HistoryFilterRow(
    state: GameHistoryUiState,
    viewModel: IOnlineLobbyViewModel,
) {
    val filters = state.filters

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Time control chips
        item {
            TimeControl.list().forEach { tc ->
                FilterChip(
                    selected = filters.timeControl == tc,
                    onClick = { viewModel.setTimeControlFilter(if (filters.timeControl == tc) null else tc) },
                    label = { Text(tc.replaceFirstChar { it.titlecase() }) },
                )
            }
        }

        // Result chips
        item {
            listOf(
                "win" to localizedString(Res.string.win),
                "loss" to localizedString(Res.string.loss),
                "draw" to localizedString(Res.string.draw)
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filters.result == key,
                    onClick = { viewModel.setResultFilter(if (filters.result == key) null else key) },
                    label = { Text(label) },
                )
            }
        }

        // Rated chip
        item {
            FilterChip(
                selected = filters.rated == true,
                onClick = { viewModel.setRatedFilter(if (filters.rated == true) null else true) },
                label = { LocalizedText(Res.string.rated) },
            )
        }
    }
}

@Composable
private fun HistoryGameCard(game: GameHistoryDto, onClick: (() -> Unit)? = null) {
    val myColor = cobColorByDescription(game.myColor) ?: CobColor.WHITE
    val (resultText, _) = gameResultDisplay(game.result)
    val (ratingChangeFmt, ratingChangeColor) = ratingChangeDisplay(game.ratingChange)
    val dateFmt = remember(game.endedAtMs) { formatGameDate(game.endedAtMs) }

    GameCardItem(
        title = "vs ${game.opponentUsername} (${game.opponentRating})",
        subtitle = "${game.timeControl.toDisplayString()} · ${
            if (game.rated) localizedString(Res.string.rated_info_card)
            else localizedString(Res.string.casual_info_card)
        } · $dateFmt",
        leadingContent = { CobColorIndicator(myColor, size = 28.dp) },
        badge = "$resultText  $ratingChangeFmt",
        badgeColor = ratingChangeColor,
        rows = listOf(
            localizedString(Res.string.result) to resultText,
            localizedString(Res.string.moves) to "${game.moveCount}",
            localizedString(Res.string.rating) to "${game.ratingAfter} ($ratingChangeFmt)",
        ),
        onClick = onClick,
    )
}
