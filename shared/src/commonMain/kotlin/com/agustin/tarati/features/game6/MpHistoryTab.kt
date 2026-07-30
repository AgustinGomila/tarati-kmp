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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.features.online.lobby.PositiveGreen
import com.agustin.tarati.features.online.lobby.formatGameDate
import com.agustin.tarati.network.models.MpGameHistoryDto
import com.agustin.tarati.network.models.MpPlayerDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_lobby_live_moves
import com.agustin.tarati.shared.generated.resources.game6_lobby_seats
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.mp_history_empty
import com.agustin.tarati.shared.generated.resources.mp_result_shared
import com.agustin.tarati.shared.generated.resources.win

/**
 * Tab "Mis Partidas" del lobby MP: historial paginado de partidas multijugador propias, más recientes
 * primero. Reusa el patrón del lobby clásico (scroll infinito + estado de carga), pero con cards MP:
 * jugadores + resultado propio (victoria / derrota / victoria compartida) + jugadas + fecha.
 *
 * El resultado propio se computa cruzando [myUserId] con los jugadores de la partida (para hallar el
 * color) y los ganadores del [MpGameHistoryDto.result].
 */
@Composable
internal fun MpHistoryTab(
    viewModel: MpLobbyViewModel,
    myUserId: String?,
    onOpenGame: ((gameId: String) -> Unit)? = null,
) {
    val state by viewModel.history.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    // Scroll infinito: al acercarse al final, pedir la siguiente página (guardas en el ViewModel).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.items.size - 3) viewModel.loadMoreHistory() }
    }

    when {
        state.items.isEmpty() && state.isLoading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        state.items.isEmpty() && state.loaded ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = localizedString(Res.string.mp_history_empty),
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
            items(state.items, key = { it.gameId }) { game ->
                MpHistoryCard(game = game, myUserId = myUserId, onOpenGame = onOpenGame)
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

/**
 * Etiqueta + color del resultado de una partida MP desde la perspectiva del jugador [subjectUserId]
 * (victoria / victoria compartida / derrota). Compartido por "Mis Partidas" (sujeto = yo) y "Seguidos"
 * (sujeto = jugador seguido).
 */
@Composable
internal fun mpResultDisplay(
    players: List<MpPlayerDto>,
    result: MpResult,
    subjectUserId: String?,
): Pair<String, Color> {
    val color = players.firstOrNull { it.userId == subjectUserId }?.color
    return when {
        color != null && color in result.winners && result.winners.size > 1 ->
            localizedString(Res.string.mp_result_shared) to PositiveGreen

        color != null && color in result.winners ->
            localizedString(Res.string.win) to PositiveGreen

        else ->
            localizedString(Res.string.loss) to MaterialTheme.colorScheme.error
    }
}

@Composable
internal fun MpHistoryCard(
    game: MpGameHistoryDto,
    myUserId: String?,
    onOpenGame: ((gameId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (resultLabel, resultColor) = mpResultDisplay(game.players, game.result, myUserId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onOpenGame != null) Modifier.clickable { onOpenGame(game.gameId) } else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = game.players.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
