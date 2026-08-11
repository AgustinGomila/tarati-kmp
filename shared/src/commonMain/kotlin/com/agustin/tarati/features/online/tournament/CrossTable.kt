package com.agustin.tarati.features.online.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.lobby.PositiveGreen
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentGameStatus
import com.agustin.tarati.network.models.TournamentRoundDto
import com.agustin.tarati.network.models.TournamentStandingDto

private sealed class CrossTableResult {
    data object Diagonal : CrossTableResult()
    data object Pending : CrossTableResult()
    data class Active(val gameId: String?) : CrossTableResult()
    data class Completed(val score: String, val gameId: String?) : CrossTableResult()
}

private fun buildCrossTable(
    players: List<TournamentStandingDto>,
    rounds: List<TournamentRoundDto>,
): List<List<CrossTableResult>> {
    // (whiteId, blackId) → pairing — cada par aparece una sola vez con los roles fijos
    val pairingMap = buildMap {
        for (round in rounds) {
            for (pairing in round.pairings) {
                put(pairing.whiteId to pairing.blackId, pairing)
            }
        }
    }

    return players.map { rowPlayer ->
        players.map { colPlayer ->
            if (rowPlayer.userId == colPlayer.userId) {
                CrossTableResult.Diagonal
            } else {
                // La clave puede estar en cualquiera de las dos orientaciones
                val pairing = pairingMap[rowPlayer.userId to colPlayer.userId]
                    ?: pairingMap[colPlayer.userId to rowPlayer.userId]

                when {
                    pairing == null -> CrossTableResult.Pending
                    pairing.status == TournamentGameStatus.PENDING -> CrossTableResult.Pending
                    pairing.status == TournamentGameStatus.ACTIVE -> CrossTableResult.Active(pairing.gameId)
                    else -> {
                        val rowIsWhite = pairing.whiteId == rowPlayer.userId
                        val score = when (pairing.result) {
                            "white_wins" -> if (rowIsWhite) "1" else "0"
                            "black_wins" -> if (rowIsWhite) "0" else "1"
                            "draw" -> "½"
                            else -> "?"
                        }
                        CrossTableResult.Completed(score, pairing.gameId)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrossTable(
    tournament: TournamentDetailDto,
    onNavigateToGameDetails: ((gameId: String) -> Unit)?,
) {
    val players = tournament.standings
    if (players.size < 2) return

    val matrix = remember(tournament) { buildCrossTable(players, tournament.rounds) }

    val cellDp = 30.dp
    val nameWidth = 108.dp
    val scoreWidth = 44.dp

    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            // ── Cabecera ─────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(nameWidth))
                players.forEachIndexed { index, _ ->
                    Box(Modifier.size(cellDp), contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.width(scoreWidth), contentAlignment = Alignment.Center) {
                    Text(
                        "Pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()

            // ── Filas ────────────────────────────────────────────────────────
            players.forEachIndexed { rowIndex, player ->
                Row(
                    modifier = Modifier.height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Columna de nombre: número + username con ellipsis
                    Row(
                        modifier = Modifier.width(nameWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${rowIndex + 1}",
                            modifier = Modifier.width(18.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                        Text(
                            player.username,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Celdas de resultados
                    matrix[rowIndex].forEach { cell ->
                        CrossTableCell(
                            cell = cell,
                            modifier = Modifier.size(cellDp),
                            onNavigateToGameDetails = onNavigateToGameDetails,
                        )
                    }

                    // Puntuación total
                    Box(Modifier.width(scoreWidth), contentAlignment = Alignment.Center) {
                        Text(
                            formatTournamentScore(player.score),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (rowIndex < players.size - 1) {
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun CrossTableCell(
    cell: CrossTableResult,
    modifier: Modifier = Modifier,
    onNavigateToGameDetails: ((gameId: String) -> Unit)?,
) {
    val cellModifier = when (cell) {
        is CrossTableResult.Diagonal -> modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        is CrossTableResult.Completed if cell.gameId != null && onNavigateToGameDetails != null ->
            modifier.clickable { onNavigateToGameDetails(cell.gameId) }

        else -> modifier
    }
    Box(cellModifier, contentAlignment = Alignment.Center) {
        when (cell) {
            is CrossTableResult.Diagonal -> Text(
                "×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is CrossTableResult.Pending -> Text(
                "·",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            )

            is CrossTableResult.Active -> Box(
                Modifier.size(7.dp).background(PositiveGreen, CircleShape)
            )

            is CrossTableResult.Completed -> {
                val (color, weight) = when (cell.score) {
                    "1" -> MaterialTheme.colorScheme.primary to FontWeight.Bold
                    "0" -> MaterialTheme.colorScheme.error to FontWeight.Normal
                    else -> MaterialTheme.colorScheme.onSurface to FontWeight.Normal
                }
                Text(
                    cell.score,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = weight,
                )
            }
        }
    }
}
