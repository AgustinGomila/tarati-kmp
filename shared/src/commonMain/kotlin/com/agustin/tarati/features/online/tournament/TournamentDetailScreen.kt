package com.agustin.tarati.features.online.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.lobby.PositiveGreen
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentGameStatus
import com.agustin.tarati.network.models.TournamentPairingDto
import com.agustin.tarati.network.models.TournamentRoundDto
import com.agustin.tarati.network.models.TournamentStandingDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.network.models.TournamentType
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.back
import com.agustin.tarati.shared.generated.resources.cancel_tournament
import com.agustin.tarati.shared.generated.resources.casual
import com.agustin.tarati.shared.generated.resources.fixture
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.standings
import com.agustin.tarati.shared.generated.resources.tournament
import com.agustin.tarati.shared.generated.resources.tournament_active_status
import com.agustin.tarati.shared.generated.resources.tournament_arena_ended
import com.agustin.tarati.shared.generated.resources.tournament_arena_ends_in
import com.agustin.tarati.shared.generated.resources.tournament_cancel_confirm
import com.agustin.tarati.shared.generated.resources.tournament_cancelled_status
import com.agustin.tarati.shared.generated.resources.tournament_created_by
import com.agustin.tarati.shared.generated.resources.tournament_cross_table
import com.agustin.tarati.shared.generated.resources.tournament_finished_status
import com.agustin.tarati.shared.generated.resources.tournament_players_of
import com.agustin.tarati.shared.generated.resources.tournament_register
import com.agustin.tarati.shared.generated.resources.tournament_round_n
import com.agustin.tarati.shared.generated.resources.tournament_round_progress
import com.agustin.tarati.shared.generated.resources.tournament_start
import com.agustin.tarati.shared.generated.resources.tournament_status_active
import com.agustin.tarati.shared.generated.resources.tournament_status_cancelled
import com.agustin.tarati.shared.generated.resources.tournament_status_finished
import com.agustin.tarati.shared.generated.resources.tournament_status_registering
import com.agustin.tarati.shared.generated.resources.tournament_unregister
import com.agustin.tarati.shared.generated.resources.watch_game
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.layout.CompanionPanelHeader
import com.agustin.tarati.ui.layout.DisplayMode
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.icon
import com.agustin.tarati.ui.theme.tournamentTypeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pantalla de detalle de un torneo.
 *
 * Muestra la información completa del torneo: estado, standings en tiempo real,
 * rondas y emparejamientos con su estado (pendiente / en curso / completado),
 * y los controles de acción (inscribirse, iniciar, etc.).
 *
 * Las actualizaciones en tiempo real llegan vía [TournamentViewModel] que escucha
 * [IOnlineGameViewModel.tournamentEvents].
 *
 * @param tournamentId       ID del torneo a mostrar.
 * @param onBack             Callback de navegación hacia atrás.
 * @param displayMode        FullScreen o CompanionPanel — controla el tipo de top bar.
 * @param onSpectateGame     Callback al tocar una partida en curso del fixture. Null oculta el botón.
 * @param onNavigateToGameDetails Callback al tocar una partida terminada del fixture. Null oculta el tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailScreen(
    tournamentId: String,
    onBack: () -> Unit,
    displayMode: DisplayMode = DisplayMode.FullScreen,
    onSpectateGame: ((gameId: String) -> Unit)? = null,
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
    authViewModel: IAuthViewModel = koinInject(),
    onlineGameViewModel: IOnlineGameViewModel = koinInject(),
    viewModel: ITournamentViewModel = koinViewModel<TournamentViewModel>(),
) {
    val state by viewModel.detailState.collectAsState()
    val scope = rememberCoroutineScope()
    val currentUserId = authViewModel.currentUser?.userId

    LaunchedEffect(tournamentId) {
        viewModel.loadTournament(tournamentId)
    }

    TaratiBackground {
        Scaffold(
            topBar = {
                when (displayMode) {
                    DisplayMode.FullScreen -> TaratiTopBar(
                        title = state.tournament?.name ?: localizedString(Res.string.tournament),
                        navigationType = TopBarNavigationType.Back,
                        onNavigationClick = onBack,
                    )

                    DisplayMode.CompanionPanel -> CompanionPanelHeader(
                        title = state.tournament?.name ?: localizedString(Res.string.tournament),
                        onClose = onBack,
                    )
                }
            }
        ) { padding ->
            when {
                state.isLoading && state.tournament == null -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.tournament == null -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error ?: return@Box, color = MaterialTheme.colorScheme.error) }

                state.tournament != null -> TournamentDetailContent(
                    tournament = state.tournament ?: return@Scaffold,
                    currentUserId = currentUserId,
                    contentPadding = padding,
                    onRegister = {
                        scope.launch { viewModel.register(tournamentId) }
                    },
                    onUnregister = {
                        scope.launch { viewModel.unregister(tournamentId) }
                    },
                    onStart = {
                        scope.launch { viewModel.start(tournamentId) }
                    },
                    onCancel = {
                        scope.launch { viewModel.cancel(tournamentId) }
                    },
                    onRefresh = {
                        viewModel.loadTournament(tournamentId)
                    },
                    onSpectateGame = if (onSpectateGame != null) { gameId ->
                        scope.launch {
                            val ok = onlineGameViewModel.spectateGame(gameId)
                            if (ok) {
                                onSpectateGame(gameId)
                            } else {
                                // La partida terminó entre el fixture load y el tap.
                                // Recargar el fixture y abrir detalles si es posible.
                                viewModel.loadTournament(tournamentId)
                                onNavigateToGameDetails?.invoke(gameId)
                            }
                        }
                    } else null,
                    onNavigateToGameDetails = onNavigateToGameDetails,
                )
            }
        }
    }
}

// ── Contenido principal ────────────────────────────────────────────────────────

@Composable
private fun TournamentDetailContent(
    tournament: TournamentDetailDto,
    currentUserId: String?,
    contentPadding: PaddingValues,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onSpectateGame: ((gameId: String) -> Unit)?,
    onNavigateToGameDetails: ((gameId: String) -> Unit)?,
) {
    val isParticipant = tournament.standings.any { it.userId == currentUserId }
    val isCreator = tournament.creatorId == currentUserId

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            TournamentHeader(tournament)
        }

        // ── Acciones ──────────────────────────────────────────────────────────
        item {
            TournamentActions(
                tournament = tournament,
                isParticipant = isParticipant,
                isCreator = isCreator,
                onRegister = onRegister,
                onUnregister = onUnregister,
                onStart = onStart,
                onCancel = onCancel,
            )
        }

        // ── Standings ─────────────────────────────────────────────────────────
        if (tournament.standings.isNotEmpty()) {
            item {
                Text(
                    localizedString(Res.string.standings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            itemsIndexed(tournament.standings) { _, standing ->
                StandingRow(
                    standing,
                    isSwiss = tournament.type == TournamentType.SWISS,
                    isArena = tournament.type == TournamentType.ARENA,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        // ── Tabla cruzada (Round Robin) ───────────────────────────────────────
        if (tournament.type == TournamentType.ROUND_ROBIN && tournament.rounds.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    localizedString(Res.string.tournament_cross_table),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                CrossTable(
                    tournament = tournament,
                    onNavigateToGameDetails = onNavigateToGameDetails,
                )
            }
        }

        // ── Bracket (Eliminación) ─────────────────────────────────────────────
        if (tournament.type == TournamentType.ELIMINATION && tournament.rounds.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    localizedString(Res.string.fixture),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                BracketTree(
                    tournament = tournament,
                    onSpectateGame = onSpectateGame,
                    onNavigateToGameDetails = onNavigateToGameDetails,
                )
            }
        }

        // ── Fixture (rondas) ──────────────────────────────────────────────────
        if (tournament.type != TournamentType.ELIMINATION && tournament.rounds.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    localizedString(Res.string.fixture),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Swiss: stepper visual de progreso de rondas
            if (tournament.type == TournamentType.SWISS && tournament.totalRounds > 0) {
                // Computar los estados fuera del composable — List<enum> es estable en Compose.
                val swissStates = buildSwissRoundStates(
                    totalRounds = tournament.totalRounds,
                    currentRound = tournament.currentRound,
                    tournamentStatus = tournament.status,
                    rounds = tournament.rounds,
                )
                item {
                    SwissRoundStepper(states = swissStates)
                }
            }
            tournament.rounds.forEach { round ->
                item {
                    RoundSection(
                        round = round,
                        isCurrent = round.roundNumber == tournament.currentRound &&
                                tournament.status == TournamentStatus.ACTIVE,
                        onSpectateGame = onSpectateGame,
                        onNavigateToGameDetails = onNavigateToGameDetails,
                    )
                }
            }
        }
    }
}

// ── Composables auxiliares ─────────────────────────────────────────────────────

@Composable
private fun TournamentHeader(tournament: TournamentDetailDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(tournament.status)
            Text(
                tournament.timeControl.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "·",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (tournament.isRated) localizedString(Res.string.rated) else localizedString(Res.string.casual),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                tournament.type.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${tournamentTypeLabel(tournament.type)} · ${
                    localizedString(
                        Res.string.tournament_players_of,
                        tournament.standings.size,
                        tournament.maxPlayers,
                    )
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            tournament.status == TournamentStatus.ACTIVE && tournament.type == TournamentType.ARENA ->
                ArenaCountdown(tournament.endsAt?.toEpochMilliseconds())

            tournament.status == TournamentStatus.ACTIVE ->
                Text(
                    localizedString(
                        Res.string.tournament_round_progress,
                        tournament.currentRound,
                        tournament.totalRounds,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

            tournament.type == TournamentType.ARENA && tournament.durationMinutes != null ->
                Text(
                    "${tournament.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Text(
            localizedString(Res.string.tournament_created_by, tournament.creatorUsername),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TournamentActions(
    tournament: TournamentDetailDto,
    isParticipant: Boolean,
    isCreator: Boolean,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (tournament.status) {
            TournamentStatus.REGISTERING -> {
                if (isCreator && tournament.standings.size >= tournament.minPlayers) {
                    Button(onClick = onStart) { Text(localizedString(Res.string.tournament_start)) }
                }
                if (!isParticipant && tournament.standings.size < tournament.maxPlayers) {
                    Button(onClick = onRegister) { Text(localizedString(Res.string.tournament_register)) }
                } else if (isParticipant && !isCreator) {
                    // El creador queda inscrito de forma fija; para salir del torneo lo cancela.
                    OutlinedButton(onClick = onUnregister) { Text(localizedString(Res.string.tournament_unregister)) }
                }
                if (isCreator) {
                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(localizedString(Res.string.cancel_tournament)) }
                }
            }

            TournamentStatus.ACTIVE -> {
                Text(
                    localizedString(Res.string.tournament_active_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }

            TournamentStatus.FINISHED -> {
                Text(
                    localizedString(Res.string.tournament_finished_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TournamentStatus.CANCELLED -> {
                Text(
                    localizedString(Res.string.tournament_cancelled_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(localizedString(Res.string.cancel_tournament)) },
            text = { Text(localizedString(Res.string.tournament_cancel_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancel()
                    },
                ) {
                    Text(
                        localizedString(Res.string.cancel_tournament),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(localizedString(Res.string.back))
                }
            },
        )
    }
}

@Composable
private fun StatusBadge(status: TournamentStatus) {
    val (label, containerColor) = when (status) {
        TournamentStatus.REGISTERING -> localizedString(Res.string.tournament_status_registering) to MaterialTheme.colorScheme.secondaryContainer
        TournamentStatus.ACTIVE -> localizedString(Res.string.tournament_status_active) to MaterialTheme.colorScheme.primaryContainer
        TournamentStatus.FINISHED -> localizedString(Res.string.tournament_status_finished) to MaterialTheme.colorScheme.surfaceVariant
        TournamentStatus.CANCELLED -> localizedString(Res.string.tournament_status_cancelled) to MaterialTheme.colorScheme.errorContainer
    }
    Badge(containerColor = containerColor) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StandingRow(standing: TournamentStandingDto, isSwiss: Boolean, isArena: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank
        Text(
            "#${standing.rank}",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (standing.rank <= 3) FontWeight.Bold else FontWeight.Normal,
            color = when (standing.rank) {
                1 -> MaterialTheme.colorScheme.primary
                2 -> MaterialTheme.colorScheme.secondary
                3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        // Nombre + rating
        Column(modifier = Modifier.weight(1f)) {
            Text(standing.username, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${standing.rating}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // W/D/L
        Text(
            "${standing.wins}W ${standing.draws}D ${standing.losses}L",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // Puntos (+ buchholz para Swiss · + racha para Arena)
        Column(horizontalAlignment = Alignment.End) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatTournamentScore(standing.score)} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isArena && standing.currentStreak >= 2) {
                    Text(
                        "×${standing.currentStreak}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (isSwiss) {
                Text(
                    "BH ${formatTournamentScore(standing.buchholz)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArenaCountdown(endsAtMs: Long?) {
    if (endsAtMs == null) return

    // Long es un primitivo estable en Compose; se convierte a Instant internamente.
    val endsAt = remember(endsAtMs) { kotlin.time.Instant.fromEpochMilliseconds(endsAtMs) }
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
private fun RoundSection(
    round: TournamentRoundDto,
    isCurrent: Boolean,
    onSpectateGame: ((gameId: String) -> Unit)?,
    onNavigateToGameDetails: ((gameId: String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                localizedString(Res.string.tournament_round_n, round.roundNumber),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(PositiveGreen, CircleShape)
                )
            }
        }
        round.pairings.forEach { pairing ->
            PairingRow(
                pairing = pairing,
                onSpectate = if (pairing.status == TournamentGameStatus.ACTIVE && pairing.gameId != null && onSpectateGame != null) {
                    { onSpectateGame(pairing.gameId) }
                } else null,
                onViewCompleted = if (pairing.status == TournamentGameStatus.COMPLETED && pairing.gameId != null && onNavigateToGameDetails != null) {
                    { onNavigateToGameDetails(pairing.gameId) }
                } else null,
            )
        }
    }
}

@Composable
private fun PairingRow(
    pairing: TournamentPairingDto,
    onSpectate: (() -> Unit)?,
    onViewCompleted: (() -> Unit)?,
) {
    val isActive = pairing.status == TournamentGameStatus.ACTIVE
    val isPending = pairing.status == TournamentGameStatus.PENDING
    val isCompleted = pairing.result != null

    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val whiteWon = pairing.result == "white_wins"
    val blackWon = pairing.result == "black_wins"

    val rowModifier = when {
        isActive && onSpectate != null -> Modifier
            .fillMaxWidth()
            .clickable(onClick = onSpectate)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)

        isCompleted && onViewCompleted != null -> Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewCompleted)
            .padding(horizontal = 4.dp, vertical = 3.dp)

        else -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Jugador blancas ──────────────────────────────────────────────────
        Text(
            pairing.whiteUsername,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (whiteWon) FontWeight.Bold else FontWeight.Normal,
            color = if (isPending) dimColor else MaterialTheme.colorScheme.onSurface,
        )

        // ── Resultado / estado ───────────────────────────────────────────────
        when {
            isCompleted -> {
                val label = when (pairing.result) {
                    "white_wins" -> "1 - 0"
                    "black_wins" -> "0 - 1"
                    "draw" -> "½ - ½"
                    else -> "?"
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            isActive -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(PositiveGreen, CircleShape)
                    )
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

            else -> {
                Text(
                    "vs",
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                )
            }
        }

        // ── Jugador negras ───────────────────────────────────────────────────
        Text(
            pairing.blackUsername,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (blackWon) FontWeight.Bold else FontWeight.Normal,
            color = if (isPending) dimColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Convierte medios puntos (almacenamiento interno 2-1-0) a notación estándar 1-½-0.
 * 0→"0"  1→"½"  2→"1"  3→"1½"  4→"2"  ...
 */
internal fun formatTournamentScore(halfPoints: Int): String {
    val whole = halfPoints / 2
    val hasHalf = halfPoints % 2 != 0
    return when {
        !hasHalf -> whole.toString()
        whole == 0 -> "½"
        else -> "$whole½"
    }
}
