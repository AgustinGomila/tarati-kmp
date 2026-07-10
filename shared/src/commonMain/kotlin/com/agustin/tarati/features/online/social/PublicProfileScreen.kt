package com.agustin.tarati.features.online.social


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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.features.achievements.ProfileAchievementsSection
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.lobby.GameHistoryUiState
import com.agustin.tarati.features.online.lobby.formatGameDate
import com.agustin.tarati.features.online.lobby.gameResultDisplay
import com.agustin.tarati.features.online.lobby.ratingChangeDisplay
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.ProfileRatingsDto
import com.agustin.tarati.network.models.ProfileStatsDto
import com.agustin.tarati.network.models.ProfileTimeControlStatsDto
import com.agustin.tarati.network.models.PublicProfileDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.casual_info_card
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.error
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.moves
import com.agustin.tarati.shared.generated.resources.no_games_found
import com.agustin.tarati.shared.generated.resources.profile_games_played
import com.agustin.tarati.shared.generated.resources.profile_history_section
import com.agustin.tarati.shared.generated.resources.profile_member_since
import com.agustin.tarati.shared.generated.resources.profile_peak_rating
import com.agustin.tarati.shared.generated.resources.profile_ratings_section
import com.agustin.tarati.shared.generated.resources.profile_stat_draw_short
import com.agustin.tarati.shared.generated.resources.profile_stat_loss_short
import com.agustin.tarati.shared.generated.resources.profile_stat_win_short
import com.agustin.tarati.shared.generated.resources.profile_title
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.rated_info_card
import com.agustin.tarati.shared.generated.resources.rating
import com.agustin.tarati.shared.generated.resources.result
import com.agustin.tarati.shared.generated.resources.social_challenge
import com.agustin.tarati.shared.generated.resources.social_challenge_dialog_title
import com.agustin.tarati.shared.generated.resources.social_follow
import com.agustin.tarati.shared.generated.resources.social_followers
import com.agustin.tarati.shared.generated.resources.social_following
import com.agustin.tarati.shared.generated.resources.social_unfollow
import com.agustin.tarati.shared.generated.resources.win
import com.agustin.tarati.ui.components.SupporterBadge
import com.agustin.tarati.ui.components.carditem.GameCardItem
import com.agustin.tarati.ui.components.game.CobColorIndicator
import com.agustin.tarati.ui.components.supporterNameColor
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.icon
import com.agustin.tarati.ui.theme.timeControlIcon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
    viewModel: IPublicProfileViewModel = koinViewModel<PublicProfileViewModel>(key = userId) {
        parametersOf(userId)
    },
    authViewModel: IAuthViewModel = koinInject(),
) {
    val isCurrentUserGuest = authViewModel.currentUser?.isGuest == true
    val profileState by viewModel.profileState.collectAsState()
    val historyState by viewModel.historyState.collectAsState()
    val followStatusState by viewModel.followStatusState.collectAsState()

    TaratiBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TaratiTopBar(
                    title = profileState.profile?.let {
                        it.displayName?.takeIf { d -> d.isNotBlank() } ?: it.username
                    } ?: localizedString(Res.string.profile_title),
                    navigationType = TopBarNavigationType.Back,
                    onNavigationClick = onBack,
                )
            },
        ) { padding ->
            when {
                profileState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                profileState.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = localizedString(Res.string.error, profileState.error.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                profileState.profile != null -> ProfileContent(
                    profile = profileState.profile ?: return@Scaffold,
                    historyState = historyState,
                    followStatusState = followStatusState,
                    isOwnProfile = viewModel.isOwnProfile,
                    onToggleFollow = viewModel::toggleFollow,
                    onSendChallenge = { tc, rated -> viewModel.sendChallenge(tc, rated) },
                    onNavigateToGameDetails = onNavigateToGameDetails,
                    forceNonRated = isCurrentUserGuest || (profileState.profile ?: return@Scaffold).isGuest,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    profile: PublicProfileDto,
    historyState: GameHistoryUiState,
    followStatusState: FollowStatusUiState,
    isOwnProfile: Boolean,
    onToggleFollow: () -> Unit,
    onSendChallenge: (timeControl: String, rated: Boolean) -> Unit,
    onNavigateToGameDetails: ((gameId: String) -> Unit)? = null,
    forceNonRated: Boolean = false,
    viewModel: IPublicProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showChallengeDialog by remember { mutableStateOf(false) }

    if (showChallengeDialog) {
        ChallengeDialog(
            targetName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
            forceNonRated = forceNonRated,
            onConfirm = { tc: String, rated: Boolean ->
                showChallengeDialog = false
                onSendChallenge(tc, rated)
            },
            onDismiss = { showChallengeDialog = false },
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreHistory()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Header
        item {
            ProfileHeader(
                profile = profile,
                followStatusState = followStatusState,
                isOwnProfile = isOwnProfile,
                onToggleFollow = onToggleFollow,
                onChallenge = if (profile.acceptsChallenges) {
                    { showChallengeDialog = true }
                } else null,
            )
        }

        // Ratings
        item {
            SectionHeader(text = localizedString(Res.string.profile_ratings_section))
            RatingsGrid(ratings = profile.ratings, stats = profile.stats)
            Spacer(Modifier.height(8.dp))
        }

        // Achievements
        item {
            val achievementsList by viewModel.achievements.collectAsState()
            ProfileAchievementsSection(achievements = achievementsList)
            Spacer(Modifier.height(8.dp))
        }

        // History header (con W/D/L del control de tiempo seleccionado) + filters
        item {
            ProfileHistoryHeader(
                stats = profile.stats,
                timeControlFilter = historyState.filters.timeControl,
            )
            ProfileHistoryFilters(state = historyState, viewModel = viewModel)
        }

        // History items
        when {
            historyState.isLoading && historyState.games.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            historyState.error != null -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = localizedString(Res.string.error, historyState.error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            historyState.games.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = localizedString(Res.string.no_games_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                itemsIndexed(historyState.games, key = { _, g -> g.gameId }) { _, game ->
                    ProfileHistoryCard(
                        game = game,
                        onClick = onNavigateToGameDetails?.let { cb -> { cb(game.gameId) } },
                    )
                }
                if (historyState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    profile: PublicProfileDto,
    followStatusState: FollowStatusUiState,
    isOwnProfile: Boolean,
    onToggleFollow: () -> Unit,
    onChallenge: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = TaratiIcons.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.isSupporter) supporterNameColor() else Color.Unspecified,
                    )
                    if (profile.isSupporter) SupporterBadge(size = 20.dp)
                }
                // Mostrar @handle solo para usuarios reales con displayName distinto al username.
                // Los bots tienen username interno (bot_lena) que nunca debe mostrarse.
                if (!profile.displayName.isNullOrBlank() && !profile.isBot) {
                    Text(
                        text = "@${profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!profile.country.isNullOrBlank()) {
                    Text(
                        text = profile.country,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val joinDate = remember(profile.createdAt) { formatJoinDate(profile.createdAt) }
        Text(
            text = localizedString(Res.string.profile_member_since, joinDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Contadores de seguidores / seguidos
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${followStatusState.followersCount} ${localizedString(Res.string.social_followers)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${followStatusState.followingCount} ${localizedString(Res.string.social_following)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Botones Follow + Desafiar (solo si no es el propio perfil)
        if (!isOwnProfile) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (followStatusState.isFollowing) {
                    OutlinedButton(onClick = onToggleFollow) {
                        Icon(TaratiIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(localizedString(Res.string.social_unfollow), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(onClick = onToggleFollow) {
                        Text(localizedString(Res.string.social_follow), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (onChallenge != null) {
                    OutlinedButton(onClick = onChallenge) {
                        Text(localizedString(Res.string.social_challenge), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

// ── Ratings grid ──────────────────────────────────────────────────────────────

@Composable
private fun RatingsGrid(ratings: ProfileRatingsDto, stats: ProfileStatsDto) {
    val entries = listOf(
        TimeControl.BULLET to Pair(ratings.bullet.rating, ratings.bullet.peak),
        TimeControl.BLITZ to Pair(ratings.blitz.rating, ratings.blitz.peak),
        TimeControl.RAPID to Pair(ratings.rapid.rating, ratings.rapid.peak),
        TimeControl.CLASSICAL to Pair(ratings.classical.rating, ratings.classical.peak),
    )
    val statEntries = listOf(
        TimeControl.BULLET to stats.bullet,
        TimeControl.BLITZ to stats.blitz,
        TimeControl.RAPID to stats.rapid,
        TimeControl.CLASSICAL to stats.classical,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEachIndexed { index, (tc, ratingPair) ->
            val (current, peak) = ratingPair
            val tcStats = statEntries[index].second
            RatingCard(
                tc = tc,
                rating = current,
                peak = peak,
                stats = tcStats,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RatingCard(
    tc: TimeControl,
    rating: Int,
    peak: Int,
    stats: ProfileTimeControlStatsDto,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = tc.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tc.key.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$rating",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = localizedString(Res.string.profile_peak_rating, peak),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = localizedString(Res.string.profile_games_played, stats.games),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── History filters ───────────────────────────────────────────────────────────

@Composable
private fun ProfileHistoryFilters(
    state: GameHistoryUiState,
    viewModel: IPublicProfileViewModel,
) {
    val filters = state.filters
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            TimeControl.list().forEach { tc ->
                FilterChip(
                    selected = filters.timeControl == tc,
                    onClick = {
                        viewModel.setTimeControlFilter(if (filters.timeControl == tc) null else tc)
                    },
                    label = { Text(tc.replaceFirstChar { it.titlecase() }) },
                    leadingIcon = {
                        Icon(timeControlIcon(tc), null, Modifier.size(16.dp))
                    },
                )
            }
        }
        item {
            listOf(
                "win" to localizedString(Res.string.win),
                "loss" to localizedString(Res.string.loss),
                "draw" to localizedString(Res.string.draw),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filters.result == key,
                    onClick = {
                        viewModel.setResultFilter(if (filters.result == key) null else key)
                    },
                    label = { Text(label) },
                )
            }
        }
        item {
            FilterChip(
                selected = filters.rated == true,
                onClick = { viewModel.setRatedFilter(if (filters.rated == true) null else true) },
                label = { Text(localizedString(Res.string.rated)) },
            )
        }
    }
}

// ── History card ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileHistoryCard(game: GameHistoryDto, onClick: (() -> Unit)? = null) {
    val (resultText, _) = gameResultDisplay(game.result)
    val (ratingChangeFmt, ratingChangeColor) = ratingChangeDisplay(game.ratingChange)
    val dateFmt = remember(game.endedAtMs) { formatGameDate(game.endedAtMs) }
    val myColor = cobColorByDescription(game.myColor) ?: CobColor.WHITE

    GameCardItem(
        title = "vs ${game.opponentUsername} (${game.opponentRating})",
        leadingContent = { CobColorIndicator(myColor, size = 28.dp) },
        subtitle = "${game.timeControl.toDisplayString()} · ${
            if (game.rated) localizedString(Res.string.rated_info_card)
            else localizedString(Res.string.casual_info_card)
        } · $dateFmt",
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

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Encabezado de la sección de historial: título "Partidas" + resumen W/D/L del
 * control de tiempo seleccionado en los filtros (o del total si no hay ninguno).
 * El resumen refleja siempre el control de tiempo, independiente del filtro de
 * resultado (Victorias/Derrotas/Tablas).
 */
@Composable
private fun ProfileHistoryHeader(stats: ProfileStatsDto, timeControlFilter: String?) {
    val tcStats = when (timeControlFilter) {
        TimeControl.BULLET.key -> stats.bullet
        TimeControl.BLITZ.key -> stats.blitz
        TimeControl.RAPID.key -> stats.rapid
        TimeControl.CLASSICAL.key -> stats.classical
        else -> stats.total
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = localizedString(Res.string.profile_history_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WdlPart(
                "${tcStats.wins}${localizedString(Res.string.profile_stat_win_short)}",
                Color(0xFF4CAF50),
            )
            WdlSeparator()
            WdlPart(
                "${tcStats.draws}${localizedString(Res.string.profile_stat_draw_short)}",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WdlSeparator()
            WdlPart(
                "${tcStats.losses}${localizedString(Res.string.profile_stat_loss_short)}",
                MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WdlPart(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun WdlSeparator() {
    Text(
        text = "/",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChallengeDialog(
    targetName: String,
    forceNonRated: Boolean = false,
    onConfirm: (timeControl: String, rated: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeControls = TimeControl.list()
    var selectedTc by remember { mutableStateOf(TimeControl.BLITZ.key) }
    var isRated by remember(forceNonRated) { mutableStateOf(!forceNonRated) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                localizedString(Res.string.social_challenge_dialog_title, targetName),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timeControls) { tc ->
                        FilterChip(
                            selected = selectedTc == tc,
                            onClick = { selectedTc = tc },
                            label = { Text(tc.replaceFirstChar { it.titlecase() }) },
                            leadingIcon = {
                                Icon(timeControlIcon(tc), null, Modifier.size(16.dp))
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(localizedString(Res.string.rated), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isRated,
                        onCheckedChange = { isRated = it },
                        enabled = !forceNonRated,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedTc, isRated) }) {
                Text(localizedString(Res.string.social_challenge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(Res.string.cancel))
            }
        },
    )
}

private fun formatJoinDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return date.format(LocalDate.Format {
        day(padding = Padding.ZERO); char('/'); monthNumber(); char('/'); year()
    })
}
