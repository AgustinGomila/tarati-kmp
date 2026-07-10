package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.casual_info_card
import com.agustin.tarati.shared.generated.resources.clear_filters
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.lobby_filter_all
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.moves
import com.agustin.tarati.shared.generated.resources.no_tournaments_match_filters
import com.agustin.tarati.shared.generated.resources.rated_info_card
import com.agustin.tarati.shared.generated.resources.rating
import com.agustin.tarati.shared.generated.resources.result
import com.agustin.tarati.shared.generated.resources.social_feed_player_context
import com.agustin.tarati.shared.generated.resources.social_no_feed_games
import com.agustin.tarati.shared.generated.resources.time_control
import com.agustin.tarati.shared.generated.resources.win
import com.agustin.tarati.ui.components.carditem.GameCardItem
import com.agustin.tarati.ui.components.game.CobColorIndicator
import com.agustin.tarati.ui.theme.TaratiIcons

// ── Tab: Feed de seguidos ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedTab(
    viewModel: IOnlineLobbyViewModel,
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
) {
    val state by viewModel.feedState.collectAsState()
    val listState = rememberLazyListState()

    // ── Filtros locales ────────────────────────────────────────────────────────
    var resultFilter by remember { mutableStateOf<String?>(null) }
    var tcFilter by remember { mutableStateOf<TimeControl?>(null) }

    val displayGames = state.games
        .let { if (resultFilter != null) it.filter { g -> g.result == resultFilter } else it }
        .let { if (tcFilter != null) it.filter { g -> g.timeControl.type == tcFilter } else it }

    val filtersActive = resultFilter != null || tcFilter != null

    LaunchedEffect(Unit) {
        if (state.games.isEmpty() && !state.isLoading) viewModel.loadFeed()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreFeed()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedFilterBar(
            resultFilter = resultFilter,
            onResultFilter = { resultFilter = it },
            tcFilter = tcFilter,
            onTcFilter = { tcFilter = it },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CenteredLoader()

                state.error != null -> CenteredMessage(
                    text = localizedString(Res.string.error, state.error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                )

                state.games.isEmpty() -> CenteredMessage(
                    text = localizedString(Res.string.social_no_feed_games),
                )

                displayGames.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CenteredMessage(text = localizedString(Res.string.no_tournaments_match_filters))
                    if (filtersActive) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            resultFilter = null
                            tcFilter = null
                        }) {
                            Text(localizedString(Res.string.clear_filters))
                        }
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(displayGames, key = { _, g -> g.gameId }) { _, game ->
                        FeedGameCard(
                            game = game,
                            onClick = onNavigateToGameDetails?.let { cb -> { cb(game.gameId) } },
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedFilterBar(
    resultFilter: String?,
    onResultFilter: (String?) -> Unit,
    tcFilter: TimeControl?,
    onTcFilter: (TimeControl?) -> Unit,
) {
    var showTcMenu by remember { mutableStateOf(false) }
    val tcLabel = when (tcFilter) {
        null -> localizedString(Res.string.time_control)
        else -> tcFilter.description
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        // Fila 1: chips de resultado (ancho completo)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(null, "win", "loss", "draw").forEach { result ->
                FilterChip(
                    selected = resultFilter == result,
                    onClick = {
                        onResultFilter(if (resultFilter == result && result != null) null else result)
                    },
                    label = {
                        Text(
                            when (result) {
                                null -> localizedString(Res.string.lobby_filter_all)
                                "win" -> localizedString(Res.string.win)
                                "loss" -> localizedString(Res.string.loss)
                                else -> localizedString(Res.string.draw)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
        // Fila 2: control de tiempo alineado a la derecha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FilterChip(
                    selected = tcFilter != null,
                    onClick = { showTcMenu = true },
                    label = { Text(tcLabel, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(TaratiIcons.Timer, null, Modifier.size(14.dp)) },
                    trailingIcon = { Icon(TaratiIcons.ArrowDropDown, null, Modifier.size(14.dp)) },
                )
                DropdownMenu(expanded = showTcMenu, onDismissRequest = { showTcMenu = false }) {
                    listOf(
                        null,
                        TimeControl.BULLET,
                        TimeControl.BLITZ,
                        TimeControl.RAPID,
                        TimeControl.CLASSICAL
                    )
                        .forEach { tc ->
                            DropdownMenuItem(
                                text = { Text(tc?.description ?: localizedString(Res.string.lobby_filter_all)) },
                                onClick = { onTcFilter(tc); showTcMenu = false },
                                leadingIcon = if (tcFilter == tc) ({
                                    Icon(TaratiIcons.Check, null, Modifier.size(16.dp))
                                }) else null,
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun FeedGameCard(game: GameHistoryDto, onClick: (() -> Unit)? = null) {
    val (resultText, _) = gameResultDisplay(game.result)
    val (ratingChangeFmt, ratingChangeColor) = ratingChangeDisplay(game.ratingChange)
    val dateFmt = remember(game.endedAtMs) { formatGameDate(game.endedAtMs) }
    val feedColor = cobColorByDescription(game.myColor) ?: CobColor.WHITE
    val playerLabel = game.playerUsername ?: "?"

    GameCardItem(
        title = localizedString(Res.string.social_feed_player_context, playerLabel) +
                " vs ${game.opponentUsername} (${game.opponentRating})",
        subtitle = "${game.timeControl.toDisplayString()} · ${
            if (game.rated) localizedString(Res.string.rated_info_card)
            else localizedString(Res.string.casual_info_card)
        } · $dateFmt",
        leadingContent = { CobColorIndicator(feedColor, size = 28.dp) },
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
