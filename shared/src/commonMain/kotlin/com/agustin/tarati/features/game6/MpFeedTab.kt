package com.agustin.tarati.features.game6

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.lobby.formatGameDate
import com.agustin.tarati.network.models.MpFeedGameDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_lobby_live_moves
import com.agustin.tarati.shared.generated.resources.game6_lobby_seats
import com.agustin.tarati.shared.generated.resources.mp_feed_empty

/**
 * Tab "Seguidos" del lobby MP: feed social con las partidas multijugador recientes de los jugadores
 * que el usuario sigue, más recientes primero. Cada card se muestra desde la perspectiva del jugador
 * seguido (nombre destacado + su resultado). Reusa [mpResultDisplay] con el sujeto del feed.
 */
@Composable
internal fun MpFeedTab(
    viewModel: MpLobbyViewModel,
    onOpenGame: ((gameId: String) -> Unit)? = null,
) {
    val state by viewModel.feed.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadFeed() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.items.size - 3) viewModel.loadMoreFeed() }
    }

    when {
        state.items.isEmpty() && state.isLoading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        state.items.isEmpty() && state.loaded ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = localizedString(Res.string.mp_feed_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.game.gameId }) { entry ->
                MpFeedCard(entry = entry, onOpenGame = onOpenGame)
            }
            if (state.isLoading && state.items.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MpFeedCard(entry: MpFeedGameDto, onOpenGame: ((gameId: String) -> Unit)? = null) {
    val game = entry.game
    val (resultLabel, resultColor) = mpResultDisplay(game.players, game.result, entry.subjectUserId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenGame != null) Modifier.clickable { onOpenGame(game.gameId) } else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.subjectName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = game.players.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resultLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = resultColor,
                )
                Text(
                    text = " · " +
                            localizedString(Res.string.game6_lobby_seats, game.playerCount) + " · " +
                            localizedString(Res.string.game6_lobby_live_moves, game.moveCount) + " · " +
                            formatGameDate(game.endedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
