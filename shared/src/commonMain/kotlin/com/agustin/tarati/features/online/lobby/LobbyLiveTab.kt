package com.agustin.tarati.features.online.lobby


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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.UserInfo
import com.agustin.tarati.features.online.ui.LabeledSwitchRow
import com.agustin.tarati.features.online.ui.TimeControlChips
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.models.GameTimeControl
import com.agustin.tarati.network.models.LiveGameDto
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.MatchmakingTicket
import com.agustin.tarati.network.models.OpenSearchDto
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.allow_spectators
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.join
import com.agustin.tarati.shared.generated.resources.lobby_count_live_games
import com.agustin.tarati.shared.generated.resources.lobby_count_searching
import com.agustin.tarati.shared.generated.resources.lobby_filter_live_games
import com.agustin.tarati.shared.generated.resources.lobby_filter_open_searches
import com.agustin.tarati.shared.generated.resources.lobby_in_live
import com.agustin.tarati.shared.generated.resources.lobby_new_search
import com.agustin.tarati.shared.generated.resources.lobby_no_live_games
import com.agustin.tarati.shared.generated.resources.lobby_sort_oldest
import com.agustin.tarati.shared.generated.resources.lobby_sort_rating
import com.agustin.tarati.shared.generated.resources.lobby_waiting_time
import com.agustin.tarati.shared.generated.resources.move
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.sort
import com.agustin.tarati.shared.generated.resources.sort_newest
import com.agustin.tarati.shared.generated.resources.tournament
import com.agustin.tarati.shared.generated.resources.turn
import com.agustin.tarati.shared.generated.resources.watch_game
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.carditem.GameCardItem
import com.agustin.tarati.ui.components.game.CobColorIndicator
import com.agustin.tarati.ui.components.library.StaticBoardRenderer
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.icon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ── Tab: Lobby (En Vivo + Búsquedas) ──────────────────────────────────────────

private sealed class LobbyItem {
    data class Game(val dto: LiveGameDto) : LobbyItem()
    data class Search(val dto: OpenSearchDto) : LobbyItem()

    /** Búsqueda propia del usuario en curso — se muestra sin botón Unirse y con botón Cancelar. */
    data class OwnSearch(val ticket: MatchmakingTicket) : LobbyItem()
}

@Composable
internal fun LobbyTab(
    viewModel: IOnlineLobbyViewModel,
    onJoinSearch: (targetUserId: String, timeControl: String, rated: Boolean) -> Unit,
    matchmakingState: MatchmakingState,
    currentUser: UserInfo?,
    onCancelMatchmaking: () -> Unit,
    onSpectateGame: ((String) -> Unit)? = null,
) {
    val gamesState by viewModel.liveGames.collectAsState()
    val searchesState by viewModel.openSearches.collectAsState()
    val filters by viewModel.lobbyFilters.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startLivePolling()
        onDispose { viewModel.stopLivePolling() }
    }

    val ownTicket = (matchmakingState as? MatchmakingState.Searching)?.ticket

    // Lista intercalada ordenada según filtros
    val combinedItems = remember(gamesState.games, searchesState.searches, filters, ownTicket) {
        buildList {
            if (filters.showLiveGames) gamesState.games.forEach { add(LobbyItem.Game(it)) }
            if (filters.showOpenSearches) {
                searchesState.searches.forEach { add(LobbyItem.Search(it)) }
                if (ownTicket != null) add(LobbyItem.OwnSearch(ownTicket))
            }
        }.sortedWith(
            when (filters.sort) {
                LobbySort.NEWEST -> compareByDescending {
                    when (it) {
                        is LobbyItem.Game -> it.dto.startedAtMs
                        is LobbyItem.Search -> it.dto.waitingSinceMs
                        is LobbyItem.OwnSearch -> it.ticket.joinedAt
                    }
                }

                LobbySort.OLDEST -> compareBy {
                    when (it) {
                        is LobbyItem.Game -> it.dto.startedAtMs
                        is LobbyItem.Search -> it.dto.waitingSinceMs
                        is LobbyItem.OwnSearch -> it.ticket.joinedAt
                    }
                }

                LobbySort.RATING_DESC -> compareByDescending {
                    when (it) {
                        is LobbyItem.Game -> maxOf(it.dto.whiteRating, it.dto.blackRating)
                        is LobbyItem.Search -> it.dto.playerRating
                        is LobbyItem.OwnSearch -> currentUser?.rating ?: 0
                    }
                }
            }
        )
    }

    val searchingCount = searchesState.searches.size + (if (ownTicket != null) 1 else 0)

    Column(modifier = Modifier.fillMaxSize()) {
        LobbyStatsRow(
            stats = listOf(
                StatChip(
                    icon = TaratiIcons.Public,
                    text = localizedString(Res.string.lobby_count_live_games, gamesState.games.size),
                ),
                StatChip(
                    icon = TaratiIcons.Search,
                    text = localizedString(Res.string.lobby_count_searching, searchingCount),
                ),
            ),
        )
        LobbyFilterBar(filters = filters, viewModel = viewModel)
        Box(modifier = Modifier.weight(1f)) {
            when {
                gamesState.isLoading && combinedItems.isEmpty() -> CenteredLoader()
                combinedItems.isEmpty() -> CenteredMessage(
                    text = localizedString(Res.string.lobby_no_live_games),
                )

                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(combinedItems, key = { item ->
                        when (item) {
                            is LobbyItem.Game -> "game_${item.dto.gameId}"
                            // searchId ya es compuesto (userId:tc:rated), pero agregamos
                            // el prefijo para evitar colisión si gameId y searchId coincidieran.
                            is LobbyItem.Search -> "search_${item.dto.searchId}"
                            is LobbyItem.OwnSearch -> "own_search"
                        }
                    }) { item ->
                        when (item) {
                            is LobbyItem.Game -> LiveGameCard(
                                game = item.dto,
                                onSpectate = if (item.dto.spectatingAllowed && onSpectateGame != null) {
                                    { onSpectateGame(item.dto.gameId) }
                                } else null,
                            )

                            is LobbyItem.Search -> OpenSearchCard(
                                search = item.dto,
                                onJoin = {
                                    onJoinSearch(
                                        item.dto.userId,
                                        item.dto.timeControl.type.key,
                                        item.dto.rated,
                                    )
                                },
                            )

                            is LobbyItem.OwnSearch -> OwnSearchCard(
                                ticket = item.ticket,
                                currentUser = currentUser,
                                onCancel = onCancelMatchmaking,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Filter / sort bar ──────────────────────────────────────────────────────────

@Composable
private fun LobbyFilterBar(filters: LobbyFilters, viewModel: IOnlineLobbyViewModel) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filters.showLiveGames,
            onClick = { viewModel.setShowLiveGames(!filters.showLiveGames) },
            label = { LocalizedText(Res.string.lobby_filter_live_games) },
            leadingIcon = { Icon(TaratiIcons.Timer, null, Modifier.size(14.dp)) },
        )
        FilterChip(
            selected = filters.showOpenSearches,
            onClick = { viewModel.setShowOpenSearches(!filters.showOpenSearches) },
            label = { LocalizedText(Res.string.lobby_filter_open_searches) },
            leadingIcon = { Icon(TaratiIcons.Search, null, Modifier.size(14.dp)) },
        )
        Spacer(Modifier.weight(1f))
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
                    tint = if (filters.sort != LobbySort.NEWEST)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                listOf(
                    LobbySort.NEWEST to Res.string.sort_newest,
                    LobbySort.OLDEST to Res.string.lobby_sort_oldest,
                    LobbySort.RATING_DESC to Res.string.lobby_sort_rating,
                ).forEach { (sort, stringRes) ->
                    DropdownMenuItem(
                        text = { LocalizedText(stringRes) },
                        onClick = { viewModel.setLobbySort(sort); showSortMenu = false },
                        leadingIcon = if (filters.sort == sort) ({
                            Icon(TaratiIcons.Check, null, Modifier.size(16.dp))
                        }) else null,
                    )
                }
            }
        }
    }
}

// ── Cards ──────────────────────────────────────────────────────────────────────

@Composable
private fun LiveGameCard(game: LiveGameDto, onSpectate: (() -> Unit)? = null) {
    val whiteTimeFmt = formatMs(game.whiteTimeMs)
    val blackTimeFmt = formatMs(game.blackTimeMs)
    val activeSide = cobColorByDescription(game.currentTurn)

    // Parsear la notación de posición para renderizar una miniatura del tablero.
    // Fallback al ícono Timer si la notación está vacía o es inválida.
    val boardState = remember(game.positionNotation) {
        if (game.positionNotation.isNotEmpty())
            runCatching { parseBoardNotation(game.positionNotation) }.getOrNull()
        else null
    }

    GameCardItem(
        title = "${game.whiteUsername} (${game.whiteRating}) vs ${game.blackUsername} (${game.blackRating})",
        subtitle = gameCardSubtitle(game.timeControl.toDisplayString(), game.rated),
        leadingContent = boardState?.let { state ->
            { StaticBoardRenderer(modifier = Modifier.fillMaxSize(), gameState = state) }
        },
        leadingIcon = if (boardState == null) game.timeControl.type.icon else null,
        badge = localizedString(Res.string.lobby_in_live),
        badgeColor = MaterialTheme.colorScheme.error,
        badgeTrailingContent = if (onSpectate != null) {
            {
                TextButton(
                    onClick = onSpectate,
                    contentPadding = PaddingValues(
                        horizontal = 6.dp, vertical = 0.dp,
                    ),
                ) {
                    Icon(
                        TaratiIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    LocalizedText(
                        Res.string.watch_game,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else null,
        rows = listOf(
            localizedString(Res.string.move) to "${game.moveCount}",
        ),
        customRows = {
            LiveGameTurnRow(
                activeSide = activeSide,
                whiteTimeFmt = whiteTimeFmt,
                blackTimeFmt = blackTimeFmt,
            )
            if (game.tournamentId != null) {
                TournamentContextRow(
                    name = game.tournamentName ?: localizedString(Res.string.tournament),
                    round = game.tournamentRound,
                    totalRounds = game.tournamentTotalRounds,
                )
            }
        },
    )
}

@Composable
private fun TournamentContextRow(name: String, round: Int?, totalRounds: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            TaratiIcons.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = buildString {
                append(name)
                if (round != null && totalRounds != null && round > 0 && totalRounds > 0) {
                    append(" · R$round/$totalRounds")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveGameTurnRow(
    activeSide: CobColor?,
    whiteTimeFmt: String,
    blackTimeFmt: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${localizedString(Res.string.turn)}:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Blancas
            CobColorIndicator(
                color = CobColor.WHITE,
                size = if (activeSide == CobColor.WHITE) 14.dp else 10.dp,
            )
            Text(
                text = whiteTimeFmt,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (activeSide == CobColor.WHITE) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            // Negras
            CobColorIndicator(
                color = CobColor.BLACK,
                size = if (activeSide == CobColor.BLACK) 14.dp else 10.dp,
            )
            Text(
                text = blackTimeFmt,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (activeSide == CobColor.BLACK) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Card de búsqueda abierta.
 *
 * Visualmente distinta de [LiveGameCard]: usa [MaterialTheme.colorScheme.tertiaryContainer]
 * como fondo (vs. [surfaceVariant] de las partidas en vivo) y tiene un botón [Unirse].
 *
 * @param search   DTO de la búsqueda.
 * @param onJoin   Callback al tocar [Unirse]. Null = botón deshabilitado (propia búsqueda).
 */
@Composable
private fun OpenSearchCard(search: OpenSearchDto, onJoin: (() -> Unit)?) {
    val waitingFmt = formatWaiting(search.waitingSinceMs)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = search.timeControl.type.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${search.playerUsername} (${search.playerRating})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = gameCardSubtitle(search.timeControl.toDisplayString(), search.rated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = localizedString(Res.string.lobby_waiting_time, waitingFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                )
            }
            if (onJoin != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onJoin,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    LocalizedText(
                        Res.string.join,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * Card de la búsqueda propia del usuario.
 *
 * Visualmente distinta de [OpenSearchCard]: usa [MaterialTheme.colorScheme.primaryContainer]
 * como fondo y tiene un botón [Cancelar] en lugar de [Unirse].
 *
 * @param ticket     Ticket de matchmaking activo.
 * @param currentUser Información del usuario autenticado (nombre y rating).
 * @param onCancel   Callback al tocar [Cancelar].
 */
@Composable
private fun OwnSearchCard(
    ticket: MatchmakingTicket,
    currentUser: UserInfo?,
    onCancel: () -> Unit,
) {
    val waitingFmt = formatWaiting(ticket.joinedAt)

    val tcDisplay = remember(ticket.timeControl) {
        runCatching {
            val tc = TimeControl.fromKey(ticket.timeControl)
            val (initial, increment) = tc.timeControl
            GameTimeControl(type = tc, initialTime = initial, increment = increment).toDisplayString()
        }.getOrElse { ticket.timeControl }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${currentUser?.displayName ?: "–"} (${currentUser?.rating ?: "–"})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = gameCardSubtitle(tcDisplay, ticket.rated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = localizedString(Res.string.lobby_waiting_time, waitingFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                LocalizedText(
                    Res.string.cancel,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Modal de nueva búsqueda ────────────────────────────────────────────────────

/**
 * Dialog simplificado para crear una nueva búsqueda desde el lobby.
 * Persiste las preferencias de time control y rated/casual en [SettingsRepository],
 * compartiendo el mismo almacenamiento que el modal de GameScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewSearchSheet(
    onStartSearch: (timeControl: String, rated: Boolean, spectatingAllowed: Boolean) -> Unit,
    onDismiss: () -> Unit,
    settings: SettingsRepository = koinInject(),
    authViewModel: IAuthViewModel = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val isGuest = authViewModel.currentUser?.isGuest == true

    val savedTc by settings.onlineTimeControl.collectAsState(TimeControl.BLITZ.key)
    val savedRated by settings.onlineRated.collectAsState(true)
    val savedSpectatingAllowed by settings.onlineSpectatingAllowed.collectAsState(true)

    var selectedTc by remember(savedTc) { mutableStateOf(savedTc) }
    // Invitados solo pueden jugar partidas no-puntuadas
    var isRated by remember(savedRated, isGuest) { mutableStateOf(if (isGuest) false else savedRated) }
    // Los invitados siempre permiten espectadores — el toggle queda desactivado.
    var spectatingAllowed by remember(savedSpectatingAllowed, isGuest) {
        mutableStateOf(if (isGuest) true else savedSpectatingAllowed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText(Res.string.lobby_new_search, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Time control chips — FlowRow para que todas las opciones queden
                // visibles en portrait (sin scroll horizontal que las oculte).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimeControlChips(
                        selected = selectedTc,
                        onSelect = { tc ->
                            selectedTc = tc
                            scope.launch { settings.setOnlineTimeControl(tc) }
                        },
                    )
                }
                // Rated / casual row
                LabeledSwitchRow(
                    label = localizedString(Res.string.rated),
                    checked = isRated,
                    onCheckedChange = {
                        isRated = it
                        scope.launch { settings.setOnlineRated(it) }
                    },
                    enabled = !isGuest,
                )
                // Allow spectators row
                LabeledSwitchRow(
                    label = localizedString(Res.string.allow_spectators),
                    checked = spectatingAllowed,
                    onCheckedChange = {
                        spectatingAllowed = it
                        scope.launch { settings.setOnlineSpectatingAllowed(it) }
                    },
                    enabled = !isGuest,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onStartSearch(selectedTc, isRated, spectatingAllowed) }) {
                LocalizedText(Res.string.lobby_new_search)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                LocalizedText(Res.string.cancel)
            }
        },
    )
}
