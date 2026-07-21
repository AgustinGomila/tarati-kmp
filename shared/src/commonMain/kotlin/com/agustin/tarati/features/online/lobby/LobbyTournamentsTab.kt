package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.tournament.ITournamentViewModel
import com.agustin.tarati.features.online.tournament.TournamentViewModel
import com.agustin.tarati.features.online.ui.LabeledSwitchRow
import com.agustin.tarati.features.online.ui.TimeControlChips
import com.agustin.tarati.network.models.CreateTournamentRequest
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.network.models.TournamentSummaryDto
import com.agustin.tarati.network.models.TournamentType
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.allow_spectators
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.clear_filters
import com.agustin.tarati.shared.generated.resources.create
import com.agustin.tarati.shared.generated.resources.create_tournament
import com.agustin.tarati.shared.generated.resources.lobby_count_tournaments_active
import com.agustin.tarati.shared.generated.resources.lobby_count_tournaments_registering
import com.agustin.tarati.shared.generated.resources.max_players
import com.agustin.tarati.shared.generated.resources.min_players
import com.agustin.tarati.shared.generated.resources.no_tournaments_available
import com.agustin.tarati.shared.generated.resources.no_tournaments_match_filters
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.retry
import com.agustin.tarati.shared.generated.resources.sort
import com.agustin.tarati.shared.generated.resources.sort_newest
import com.agustin.tarati.shared.generated.resources.time_control
import com.agustin.tarati.shared.generated.resources.tournament_duration_minutes
import com.agustin.tarati.shared.generated.resources.tournament_filter_all
import com.agustin.tarati.shared.generated.resources.tournament_format
import com.agustin.tarati.shared.generated.resources.tournament_players_of
import com.agustin.tarati.shared.generated.resources.tournament_recent_only
import com.agustin.tarati.shared.generated.resources.tournament_registering_section
import com.agustin.tarati.shared.generated.resources.tournament_sort_most_players
import com.agustin.tarati.shared.generated.resources.tournament_status_active
import com.agustin.tarati.shared.generated.resources.tournament_status_cancelled
import com.agustin.tarati.shared.generated.resources.tournament_status_finished
import com.agustin.tarati.shared.generated.resources.tournament_status_registering
import com.agustin.tarati.shared.generated.resources.tournaments_finished_section
import com.agustin.tarati.shared.generated.resources.user_name
import com.agustin.tarati.shared.generated.resources.validation_arena_duration
import com.agustin.tarati.shared.generated.resources.validation_max_gte_min
import com.agustin.tarati.shared.generated.resources.validation_max_players_count
import com.agustin.tarati.shared.generated.resources.validation_min_players_count
import com.agustin.tarati.shared.generated.resources.validation_players_number
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.icon
import com.agustin.tarati.ui.theme.timeControlIcon
import com.agustin.tarati.ui.theme.tournamentTypeLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

// ── Tab: Torneos ───────────────────────────────────────────────────────────────

private enum class TournamentSort { NEWEST, MOST_PLAYERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TournamentsTab(
    onNavigateToTournament: ((tournamentId: String) -> Unit)? = null,
    viewModel: ITournamentViewModel = koinViewModel<TournamentViewModel>(),
) {
    val state by viewModel.listState.collectAsState()
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }

    // ── Filtros locales ────────────────────────────────────────────────────────
    var statusFilter by remember { mutableStateOf<TournamentStatus?>(null) }
    var recentOnly by remember { mutableStateOf(true) }
    var sortBy by remember { mutableStateOf(TournamentSort.NEWEST) }

    DisposableEffect(Unit) {
        viewModel.startTournamentPolling()
        onDispose { viewModel.stopTournamentPolling() }
    }

    // ── Aplicar filtros ────────────────────────────────────────────────────────
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val cutoffMs = nowMs - 7L * 24 * 60 * 60 * 1000

    fun List<TournamentSummaryDto>.sorted() = when (sortBy) {
        TournamentSort.NEWEST -> sortedByDescending { it.createdAt.toEpochMilliseconds() }
        TournamentSort.MOST_PLAYERS -> sortedByDescending { it.participantCount }
    }

    val displayRegistering = state.registering
        .takeIf { statusFilter == null || statusFilter == TournamentStatus.REGISTERING }
        ?.sorted() ?: emptyList()

    val displayActive = state.active
        .takeIf { statusFilter == null || statusFilter == TournamentStatus.ACTIVE }
        ?.sorted() ?: emptyList()

    val displayFinished = state.finished
        .let { if (recentOnly) it.filter { t -> (t.finishedAt?.toEpochMilliseconds() ?: nowMs) > cutoffMs } else it }
        .takeIf { statusFilter == null || statusFilter == TournamentStatus.FINISHED }
        ?.sorted() ?: emptyList()

    val filtersActive = statusFilter != null || !recentOnly || sortBy != TournamentSort.NEWEST
    val isEmptyAfterFilter = displayRegistering.isEmpty() && displayActive.isEmpty() && displayFinished.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CenteredLoader()

            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadTournaments() }) {
                        Text(localizedString(Res.string.retry))
                    }
                }
            }

            state.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        localizedString(Res.string.no_tournaments_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Text(localizedString(Res.string.create_tournament))
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    LobbyStatsRow(
                        stats = listOf(
                            StatChip(
                                icon = TaratiIcons.EmojiEvents,
                                text = localizedString(Res.string.lobby_count_tournaments_active, state.active.size),
                            ),
                            StatChip(
                                icon = TaratiIcons.Group,
                                text = localizedString(
                                    Res.string.lobby_count_tournaments_registering,
                                    state.registering.size,
                                ),
                            ),
                        ),
                    )
                    TournamentFilterBar(
                        statusFilter = statusFilter,
                        onStatusFilter = { statusFilter = it },
                        recentOnly = recentOnly,
                        onRecentOnlyToggle = { recentOnly = !recentOnly },
                        sortBy = sortBy,
                        onSortChange = { sortBy = it },
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isEmptyAfterFilter) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    localizedString(Res.string.no_tournaments_match_filters),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (filtersActive) {
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(onClick = {
                                        statusFilter = null
                                        recentOnly = true
                                        sortBy = TournamentSort.NEWEST
                                    }) {
                                        Text(localizedString(Res.string.clear_filters))
                                    }
                                }
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)) {
                                if (displayRegistering.isNotEmpty()) {
                                    item {
                                        Text(
                                            localizedString(Res.string.tournament_registering_section),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }
                                    items(displayRegistering, key = { it.id }) { t ->
                                        TournamentCard(t, onClick = { onNavigateToTournament?.invoke(t.id) })
                                    }
                                }
                                if (displayActive.isNotEmpty()) {
                                    item {
                                        Text(
                                            localizedString(Res.string.tournament_status_active),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                        )
                                    }
                                    items(displayActive, key = { it.id }) { t ->
                                        TournamentCard(t, onClick = { onNavigateToTournament?.invoke(t.id) })
                                    }
                                }
                                if (displayFinished.isNotEmpty()) {
                                    item {
                                        Text(
                                            localizedString(Res.string.tournaments_finished_section),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                        )
                                    }
                                    items(displayFinished, key = { it.id }) { t ->
                                        TournamentCard(t, onClick = { onNavigateToTournament?.invoke(t.id) })
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                        // FAB siempre visible cuando hay datos
                        androidx.compose.material3.FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        ) {
                            Icon(
                                TaratiIcons.EmojiEvents,
                                contentDescription = localizedString(Res.string.create_tournament)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTournamentDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { request ->
                scope.launch {
                    viewModel.createTournament(request)
                    showCreateDialog = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TournamentFilterBar(
    statusFilter: TournamentStatus?,
    onStatusFilter: (TournamentStatus?) -> Unit,
    recentOnly: Boolean,
    onRecentOnlyToggle: () -> Unit,
    sortBy: TournamentSort,
    onSortChange: (TournamentSort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        // Fila 1: chips de estado (ancho completo)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                null,
                TournamentStatus.REGISTERING,
                TournamentStatus.ACTIVE,
                TournamentStatus.FINISHED
            )
                .forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            onStatusFilter(if (statusFilter == status && status != null) null else status)
                        },
                        label = {
                            Text(
                                when (status) {
                                    null -> localizedString(Res.string.tournament_filter_all)
                                    TournamentStatus.REGISTERING -> localizedString(Res.string.tournament_status_registering)
                                    TournamentStatus.ACTIVE -> localizedString(Res.string.tournament_status_active)
                                    TournamentStatus.FINISHED -> localizedString(Res.string.tournament_status_finished)
                                    TournamentStatus.CANCELLED -> localizedString(Res.string.tournament_status_cancelled)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
        }
        // Fila 2: Recientes + ordenamiento alineados a la derecha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = recentOnly,
                onClick = onRecentOnlyToggle,
                label = { LocalizedText(Res.string.tournament_recent_only) },
                leadingIcon = { Icon(TaratiIcons.Timer, null, Modifier.size(14.dp)) },
            )
            Box {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.sort),
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        TaratiIcons.Sort,
                        contentDescription = localizedString(Res.string.sort),
                        modifier = Modifier.size(18.dp),
                        tint = if (sortBy != TournamentSort.NEWEST)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf(
                        TournamentSort.NEWEST to Res.string.sort_newest,
                        TournamentSort.MOST_PLAYERS to Res.string.tournament_sort_most_players,
                    ).forEach { (sort, stringRes) ->
                        DropdownMenuItem(
                            text = { LocalizedText(stringRes) },
                            onClick = { onSortChange(sort); showSortMenu = false },
                            leadingIcon = if (sortBy == sort) ({
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
private fun TournamentCard(
    tournament: TournamentSummaryDto,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    tournament.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val statusColor = when (tournament.status) {
                    TournamentStatus.REGISTERING -> Color(0xFFFFC107)
                    TournamentStatus.ACTIVE -> PositiveGreen
                    TournamentStatus.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
                    TournamentStatus.CANCELLED -> MaterialTheme.colorScheme.error
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        tournament.type.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        tournamentTypeLabel(tournament.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        timeControlIcon(tournament.timeControl),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        tournament.timeControl.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    localizedString(
                        Res.string.tournament_players_of,
                        tournament.participantCount,
                        tournament.maxPlayers,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateTournamentRequest) -> Unit,
    authViewModel: IAuthViewModel = koinInject(),
) {
    val isGuest = authViewModel.currentUser?.isGuest == true
    var name by remember { mutableStateOf("") }
    var selectedType by remember {
        mutableStateOf(TournamentType.ROUND_ROBIN)
    }
    var selectedTc by remember { mutableStateOf("blitz") }
    var isRated by remember { mutableStateOf(true) }
    var spectatingAllowed by remember { mutableStateOf(true) }
    var minPlayers by remember { mutableStateOf("4") }
    var maxPlayers by remember { mutableStateOf("16") }
    var durationMinutes by remember { mutableStateOf("60") }

    val isArena = selectedType == TournamentType.ARENA

    val minInt = minPlayers.toIntOrNull()
    val maxInt = maxPlayers.toIntOrNull()
    val playerError: String? = when {
        minInt == null || maxInt == null -> localizedString(Res.string.validation_players_number)
        minInt < 2 -> localizedString(Res.string.validation_min_players_count)
        maxInt > 128 -> localizedString(Res.string.validation_max_players_count)
        maxInt < minInt -> localizedString(Res.string.validation_max_gte_min)
        else -> null
    }

    // Arena requiere una ventana de duración (1–360 min); el resto de formatos no la usan.
    val durationInt = durationMinutes.toIntOrNull()
    val durationError: String? = if (isArena && (durationInt == null || durationInt < 1 || durationInt > 360)) {
        localizedString(Res.string.validation_arena_duration)
    } else null

    // Presets de tiempo control
    val tcPresets = mapOf(
        "bullet" to (60 to 0),
        "blitz" to (180 to 2),
        "rapid" to (600 to 5),
        "classical" to (1800 to 20),
    )
    val (initialTime, increment) = tcPresets[selectedTc] ?: (180 to 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString(Res.string.create_tournament)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(localizedString(Res.string.user_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Tipo
                Text(localizedString(Res.string.tournament_format), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TournamentType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(tournamentTypeLabel(type)) },
                            leadingIcon = {
                                Icon(type.icon, null, Modifier.size(16.dp))
                            },
                        )
                    }
                }
                // Time control
                Text(localizedString(Res.string.time_control), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimeControlChips(selected = selectedTc, onSelect = { selectedTc = it })
                }
                // Duración — solo Arena (ventana de tiempo fija)
                if (isArena) {
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        label = { Text(localizedString(Res.string.tournament_duration_minutes)) },
                        singleLine = true,
                        isError = durationError != null,
                        supportingText = if (durationError != null) {
                            { Text(durationError, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Rated toggle — invitados solo juegan no puntuado
                LabeledSwitchRow(
                    label = localizedString(Res.string.rated),
                    checked = if (isGuest) false else isRated,
                    onCheckedChange = { isRated = it },
                    enabled = !isGuest,
                )
                // Spectating toggle — invitados siempre permiten espectadores
                LabeledSwitchRow(
                    label = localizedString(Res.string.allow_spectators),
                    checked = if (isGuest) true else spectatingAllowed,
                    onCheckedChange = { spectatingAllowed = it },
                    enabled = !isGuest,
                )
                // Jugadores
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minPlayers,
                        onValueChange = { minPlayers = it },
                        label = { Text(localizedString(Res.string.min_players)) },
                        singleLine = true,
                        isError = playerError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxPlayers,
                        onValueChange = { maxPlayers = it },
                        label = { Text(localizedString(Res.string.max_players)) },
                        singleLine = true,
                        isError = playerError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (playerError != null) {
                    Text(
                        text = playerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = CreateTournamentRequest(
                        name = name.trim(),
                        type = selectedType,
                        timeControl = selectedTc,
                        initialTime = initialTime,
                        increment = increment,
                        isRated = isRated,
                        minPlayers = minInt ?: 4,
                        maxPlayers = maxInt ?: 16,
                        spectatingAllowed = if (isGuest) true else spectatingAllowed,
                        durationMinutes = if (isArena) durationInt else null,
                    )
                    onCreate(request)
                },
                enabled = name.isNotBlank() && playerError == null && durationError == null,
            ) { Text(localizedString(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedString(Res.string.cancel)) }
        },
    )
}
