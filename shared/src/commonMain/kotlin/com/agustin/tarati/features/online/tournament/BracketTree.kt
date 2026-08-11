package com.agustin.tarati.features.online.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.lobby.PositiveGreen
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentGameStatus
import com.agustin.tarati.network.models.TournamentPairingDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.tournament_bye
import com.agustin.tarati.shared.generated.resources.tournament_champion
import com.agustin.tarati.shared.generated.resources.tournament_final
import com.agustin.tarati.shared.generated.resources.tournament_quarterfinals
import com.agustin.tarati.shared.generated.resources.tournament_round_of
import com.agustin.tarati.shared.generated.resources.tournament_semifinals
import com.agustin.tarati.shared.generated.resources.tournament_status_active
import com.agustin.tarati.shared.generated.resources.watch_game
import com.agustin.tarati.ui.theme.TaratiIcons

private data class BracketColumn(val roundNumber: Int, val slots: List<TournamentPairingDto>)

/**
 * Colapsa los emparejamientos de cada ronda a un slot por posición de bracket: si hubo revanchas,
 * muestra el juego decisivo; si no, el último jugado. Solo aparecen las rondas ya iniciadas.
 */
private fun buildBracketColumns(tournament: TournamentDetailDto): List<BracketColumn> =
    tournament.rounds
        .sortedBy { it.roundNumber }
        .map { round ->
            val slots = round.pairings
                .groupBy { it.bracketPosition ?: 0 }
                .entries
                .sortedBy { it.key }
                .map { (_, pairings) ->
                    pairings.firstOrNull { it.result == "white_wins" || it.result == "black_wins" }
                        ?: pairings.last()
                }
            BracketColumn(round.roundNumber, slots)
        }

@Composable
internal fun BracketTree(
    tournament: TournamentDetailDto,
    onSpectateGame: ((gameId: String) -> Unit)?,
    onNavigateToGameDetails: ((gameId: String) -> Unit)?,
) {
    val columns = remember(tournament) { buildBracketColumns(tournament) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        columns.forEach { column ->
            Column(
                modifier = Modifier.width(170.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    eliminationRoundTitle(column.roundNumber, tournament.totalRounds),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                column.slots.forEach { slot ->
                    BracketSlotCard(
                        pairing = slot,
                        onSpectate = if (slot.status == TournamentGameStatus.ACTIVE &&
                            slot.gameId != null && onSpectateGame != null
                        ) {
                            { onSpectateGame(slot.gameId) }
                        } else null,
                        onViewCompleted = if (slot.status == TournamentGameStatus.COMPLETED &&
                            slot.gameId != null && onNavigateToGameDetails != null
                        ) {
                            { onNavigateToGameDetails(slot.gameId) }
                        } else null,
                    )
                }
            }
        }
        // Columna del campeón cuando el torneo terminó
        if (tournament.status == TournamentStatus.FINISHED) {
            tournament.standings.firstOrNull()?.let { champ ->
                Column(
                    modifier = Modifier.width(150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        localizedString(Res.string.tournament_champion),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                MaterialTheme.shapes.small,
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            TaratiIcons.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            champ.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BracketSlotCard(
    pairing: TournamentPairingDto,
    onSpectate: (() -> Unit)?,
    onViewCompleted: (() -> Unit)?,
) {
    val whiteWon = pairing.result == "white_wins"
    val blackWon = pairing.result == "black_wins"
    val isActive = pairing.status == TournamentGameStatus.ACTIVE
    val isPending = pairing.status == TournamentGameStatus.PENDING

    val clickMod = when {
        isActive && onSpectate != null -> Modifier.clickable(onClick = onSpectate)
        pairing.status == TournamentGameStatus.COMPLETED && onViewCompleted != null ->
            Modifier.clickable(onClick = onViewCompleted)

        else -> Modifier
    }
    val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.small)
            .then(clickMod)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BracketPlayerLine(
            name = pairing.whiteUsername,
            won = whiteWon,
            dim = isPending,
            scoreMark = if (whiteWon) "1" else if (blackWon) "0" else null,
        )
        if (pairing.isBye) {
            Text(
                localizedString(Res.string.tournament_bye),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BracketPlayerLine(
                name = pairing.blackUsername,
                won = blackWon,
                dim = isPending,
                scoreMark = if (blackWon) "1" else if (whiteWon) "0" else null,
            )
        }
        if (isActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(6.dp).background(PositiveGreen, CircleShape))
                Text(
                    localizedString(Res.string.tournament_status_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = PositiveGreen,
                )
                if (onSpectate != null) {
                    Icon(
                        TaratiIcons.Visibility,
                        contentDescription = localizedString(Res.string.watch_game),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BracketPlayerLine(name: String, won: Boolean, dim: Boolean, scoreMark: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            color = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (scoreMark != null) {
            Text(
                scoreMark,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Nombre de la ronda de Eliminación: Final / Semifinales / Cuartos / "Ronda de N". */
@Composable
private fun eliminationRoundTitle(roundNumber: Int, totalRounds: Int): String {
    return when (val roundsFromEnd = totalRounds - roundNumber) {  // 0 = final
        0 -> localizedString(Res.string.tournament_final)
        1 -> localizedString(Res.string.tournament_semifinals)
        2 -> localizedString(Res.string.tournament_quarterfinals)
        else -> {
            // Jugadores que entran a la ronda = 2^(roundsFromEnd + 1)
            val size = 1 shl (roundsFromEnd + 1)
            localizedString(Res.string.tournament_round_of, size)
        }
    }
}
