package com.agustin.tarati.features.game6

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.network.models.CreateMpTournamentRequest
import com.agustin.tarati.network.models.MpTournamentDto
import com.agustin.tarati.network.models.MpTournamentStandingDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.back
import com.agustin.tarati.shared.generated.resources.game6_lobby_create
import com.agustin.tarati.shared.generated.resources.game6_lobby_seats
import com.agustin.tarati.shared.generated.resources.mp_tournament_action_error
import com.agustin.tarati.shared.generated.resources.mp_tournament_cancel
import com.agustin.tarati.shared.generated.resources.mp_tournament_duration
import com.agustin.tarati.shared.generated.resources.mp_tournament_empty
import com.agustin.tarati.shared.generated.resources.mp_tournament_minutes
import com.agustin.tarati.shared.generated.resources.mp_tournament_name
import com.agustin.tarati.shared.generated.resources.mp_tournament_register
import com.agustin.tarati.shared.generated.resources.mp_tournament_start
import com.agustin.tarati.shared.generated.resources.mp_tournament_status_active
import com.agustin.tarati.shared.generated.resources.mp_tournament_status_cancelled
import com.agustin.tarati.shared.generated.resources.mp_tournament_status_finished
import com.agustin.tarati.shared.generated.resources.mp_tournament_status_registering
import com.agustin.tarati.shared.generated.resources.mp_tournament_table_size
import com.agustin.tarati.shared.generated.resources.mp_tournament_unregister
import com.agustin.tarati.shared.generated.resources.tournament_arena_ended
import com.agustin.tarati.shared.generated.resources.tournament_arena_ends_in
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Contenido del tab **Torneos** del lobby MP (Arena, fase 4b). Si hay un torneo seleccionado muestra
 * su detalle (clasificación + acciones); si no, la lista de torneos abiertos + crear.
 *
 * Observa el [MpTournamentViewModel] (lista + detalle en vivo por WebSocket) y arranca/detiene el
 * refresco de la lista con el ciclo de la pantalla. Los errores de acción salen como toast.
 */
@Composable
fun MpTournamentTab(
    myUserId: String?,
    viewModel: MpTournamentViewModel = koinInject(),
    bus: UIMessageBus = koinInject(),
) {
    val tournaments by viewModel.list.collectAsState()
    val selected by viewModel.selected.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    // Toast de error de acción (código del servidor → mensaje genérico localizado).
    val errorMsg by rememberUpdatedState(localizedString(Res.string.mp_tournament_action_error))
    LaunchedEffect(Unit) {
        viewModel.errors.collect { bus.toast(UIMessage.Toast(message = errorMsg)) }
    }

    val current = selected
    if (current != null) {
        MpTournamentDetail(tournament = current, myUserId = myUserId, viewModel = viewModel)
    } else {
        MpTournamentList(tournaments = tournaments, viewModel = viewModel)
    }
}

// ── Lista + crear ────────────────────────────────────────────────────────────────

@Composable
private fun MpTournamentList(tournaments: List<MpTournamentDto>, viewModel: MpTournamentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MpCreateTournamentSection(onCreate = viewModel::create) }

        if (tournaments.isEmpty()) {
            item {
                Text(
                    text = localizedString(Res.string.mp_tournament_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(tournaments, key = { it.id }) { t ->
                MpTournamentCard(tournament = t, onClick = { viewModel.select(t.id) })
            }
        }
    }
}

@Composable
private fun MpCreateTournamentSection(onCreate: (CreateMpTournamentRequest) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var tableSize by remember { mutableStateOf(MpSetup.MIN_PLAYERS + 2) } // 4
    var duration by remember { mutableStateOf(20) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TaratiIcons.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = localizedString(Res.string.game6_lobby_create),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = if (expanded) TaratiIcons.ExpandLess else TaratiIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(100) },
                        label = { Text(localizedString(Res.string.mp_tournament_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = localizedString(Res.string.mp_tournament_table_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in MpSetup.MIN_PLAYERS..MpSetup.MAX_PLAYERS) {
                            MpToggleChip(label = "$n", selected = n == tableSize, onClick = { tableSize = n })
                        }
                    }
                    Text(
                        text = localizedString(Res.string.mp_tournament_duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in listOf(15, 30, 45, 60)) {
                            MpToggleChip(
                                label = localizedString(Res.string.mp_tournament_minutes, m),
                                selected = m == duration,
                                onClick = { duration = m },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onCreate(
                                CreateMpTournamentRequest(
                                    name = name.trim().ifBlank { "Arena" },
                                    tableSize = tableSize,
                                    minPlayers = tableSize,
                                    durationMinutes = duration,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(localizedString(Res.string.game6_lobby_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun MpTournamentCard(tournament: MpTournamentDto, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tournament.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = statusLabel(tournament.status) +
                            " · " + localizedString(Res.string.game6_lobby_seats, tournament.participantCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tournament.status == TournamentStatus.ACTIVE) MpArenaCountdown(tournament.endsAtMs)
        }
    }
}

// ── Detalle ──────────────────────────────────────────────────────────────────────

@Composable
private fun MpTournamentDetail(tournament: MpTournamentDto, myUserId: String?, viewModel: MpTournamentViewModel) {
    val isCreator = tournament.creatorId == myUserId
    val isRegistered = tournament.standings.any { it.userId == myUserId }
    val canStart = tournament.participantCount >= maxOf(tournament.minPlayers, tournament.tableSize)
    val registering = tournament.status == TournamentStatus.REGISTERING
    val active = tournament.status == TournamentStatus.ACTIVE

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tournament.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = statusLabel(tournament.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = localizedString(Res.string.game6_lobby_seats, tournament.tableSize) +
                    " · " + localizedString(Res.string.mp_tournament_minutes, tournament.durationMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active) MpArenaCountdown(tournament.endsAtMs)

        MpTournamentStandings(tournament.standings)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { viewModel.clearSelection() }) {
                Text(localizedString(Res.string.back))
            }
            Spacer(Modifier.weight(1f))
            if (registering) {
                if (isRegistered) {
                    OutlinedButton(onClick = { viewModel.unregister() }) {
                        Text(localizedString(Res.string.mp_tournament_unregister))
                    }
                } else {
                    Button(onClick = { viewModel.register() }) {
                        Text(localizedString(Res.string.mp_tournament_register))
                    }
                }
                if (isCreator) {
                    Button(onClick = { viewModel.start() }, enabled = canStart) {
                        Icon(TaratiIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(localizedString(Res.string.mp_tournament_start))
                    }
                }
            }
            if (isCreator && (registering || active)) {
                OutlinedButton(onClick = { viewModel.cancel() }) {
                    Text(localizedString(Res.string.mp_tournament_cancel))
                }
            }
        }
    }
}

@Composable
private fun MpTournamentStandings(standings: List<MpTournamentStandingDto>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            standings.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${s.rank}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(s.username, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (s.streak >= 2) {
                        Text(
                            "×${s.streak}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "${s.wins}/${s.shared}/${s.losses}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${s.score}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(32.dp),
                    )
                }
            }
        }
    }
}

/** Countdown de la ventana Arena (ticker 1 s; rojo <5 min). Réplica MP del de torneos 2-jugadores. */
@Composable
private fun MpArenaCountdown(endsAtMs: Long?) {
    if (endsAtMs == null) return
    val endsAt = remember(endsAtMs) { Instant.fromEpochMilliseconds(endsAtMs) }
    var remaining by remember(endsAtMs) { mutableStateOf(endsAt - Clock.System.now()) }

    LaunchedEffect(endsAtMs) {
        while (remaining.isPositive()) {
            delay(1000.milliseconds)
            remaining = endsAt - Clock.System.now()
        }
    }

    val ended = !remaining.isPositive()
    val text = if (ended) {
        localizedString(Res.string.tournament_arena_ended)
    } else {
        val formatted = if (remaining.inWholeHours > 0) {
            "${remaining.inWholeHours}h ${remaining.inWholeMinutes % 60}m"
        } else {
            "${remaining.inWholeMinutes}m ${remaining.inWholeSeconds % 60}s"
        }
        localizedString(Res.string.tournament_arena_ends_in, formatted)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = when {
            ended -> MaterialTheme.colorScheme.onSurfaceVariant
            remaining.inWholeMinutes < 5 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
    )
}

@Composable
private fun statusLabel(status: TournamentStatus): String = localizedString(
    when (status) {
        TournamentStatus.REGISTERING -> Res.string.mp_tournament_status_registering
        TournamentStatus.ACTIVE -> Res.string.mp_tournament_status_active
        TournamentStatus.FINISHED -> Res.string.mp_tournament_status_finished
        TournamentStatus.CANCELLED -> Res.string.mp_tournament_status_cancelled
    },
)
