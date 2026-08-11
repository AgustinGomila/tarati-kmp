package com.agustin.tarati.features.online.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.network.models.TournamentGameStatus
import com.agustin.tarati.network.models.TournamentRoundDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.ui.theme.TaratiIcons

internal enum class SwissRoundState { Completed, Active, Pending }

internal fun buildSwissRoundStates(
    totalRounds: Int,
    currentRound: Int,
    tournamentStatus: TournamentStatus,
    rounds: List<TournamentRoundDto>,
): List<SwissRoundState> = (1..totalRounds).map { n ->
    val round = rounds.find { it.roundNumber == n }
    when {
        round != null && round.pairings.isNotEmpty() &&
                round.pairings.all { it.status == TournamentGameStatus.COMPLETED } ->
            SwissRoundState.Completed

        n == currentRound && tournamentStatus == TournamentStatus.ACTIVE ->
            SwissRoundState.Active

        else ->
            SwissRoundState.Pending
    }
}

@Composable
internal fun SwissRoundStepper(states: List<SwissRoundState>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        states.forEachIndexed { index, state ->
            RoundStep(roundNumber = index + 1, state = state)
            if (index < states.lastIndex) {
                // Conector: línea horizontal coloreada según si la ronda ya terminó
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(2.dp)
                        .width(20.dp)
                        .background(
                            color = if (state == SwissRoundState.Completed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                )
            }
        }
    }
}

@Composable
private fun RoundStep(roundNumber: Int, state: SwissRoundState) {
    val circleSize = 28.dp
    val (bgColor, contentColor) = when (state) {
        SwissRoundState.Completed -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        SwissRoundState.Active -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SwissRoundState.Pending -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.size(circleSize).background(bgColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (state == SwissRoundState.Completed) {
                Icon(
                    imageVector = TaratiIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor,
                )
            } else {
                Text(
                    text = "$roundNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (state == SwissRoundState.Active) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                )
            }
        }
        Text(
            text = "$roundNumber",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = if (state == SwissRoundState.Pending) 0.6f else 1f),
        )
    }
}
