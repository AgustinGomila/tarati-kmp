package com.agustin.tarati.features.online.social


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.features.achievements.ProfileAchievementsSection
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.lobby.GameHistoryCard
import com.agustin.tarati.features.online.lobby.GameHistoryFilterRow
import com.agustin.tarati.features.online.lobby.GameHistoryUiState
import com.agustin.tarati.features.online.lobby.PositiveGreen
import com.agustin.tarati.features.online.lobby.forTimeControl
import com.agustin.tarati.features.online.lobby.formatDate
import com.agustin.tarati.features.online.ui.ChallengeDialog
import com.agustin.tarati.network.models.ProfileRatingsDto
import com.agustin.tarati.network.models.ProfileStatsDto
import com.agustin.tarati.network.models.ProfileTimeControlStatsDto
import com.agustin.tarati.network.models.PublicProfileDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.error
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
import com.agustin.tarati.shared.generated.resources.social_challenge
import com.agustin.tarati.shared.generated.resources.social_follow
import com.agustin.tarati.shared.generated.resources.social_followers
import com.agustin.tarati.shared.generated.resources.social_following
import com.agustin.tarati.shared.generated.resources.social_unfollow
import com.agustin.tarati.ui.components.InfiniteScrollEffect
import com.agustin.tarati.ui.components.SupporterBadge
import com.agustin.tarati.ui.components.loadingMoreIndicator
import com.agustin.tarati.ui.components.supporterNameColor
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.icon
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    // Secciones colapsables de logros e historial (por defecto expandidas).
    var achievementsExpanded by remember { mutableStateOf(true) }
    var historyExpanded by remember { mutableStateOf(true) }

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

    InfiniteScrollEffect(listState) { viewModel.loadMoreHistory() }

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

        // Achievements (sección colapsable)
        item {
            val achievementsList by viewModel.achievements.collectAsState()
            ProfileAchievementsSection(
                achievements = achievementsList,
                expanded = achievementsExpanded,
                onToggleExpanded = { achievementsExpanded = !achievementsExpanded },
            )
            Spacer(Modifier.height(8.dp))
        }

        // History header (con W/D/L del control de tiempo seleccionado) + filters (colapsable)
        item {
            ProfileHistoryHeader(
                stats = profile.stats,
                timeControlFilter = historyState.filters.timeControl,
                expanded = historyExpanded,
                onToggle = { historyExpanded = !historyExpanded },
            )
            AnimatedVisibility(visible = historyExpanded) {
                GameHistoryFilterRow(
                    filters = historyState.filters,
                    onTimeControlFilter = viewModel::setTimeControlFilter,
                    onResultFilter = viewModel::setResultFilter,
                    onRatedFilter = viewModel::setRatedFilter,
                )
            }
        }

        // History items (ocultos cuando la sección está colapsada)
        if (historyExpanded) when {
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
                items(historyState.games, key = { it.gameId }) { game ->
                    GameHistoryCard(
                        game = game,
                        onClick = onNavigateToGameDetails?.let { cb -> { cb(game.gameId) } },
                    )
                }
                loadingMoreIndicator(historyState.isLoadingMore)
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
        val joinDate = remember(profile.createdAt) { formatDate(profile.createdAt) }
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
private fun ProfileHistoryHeader(
    stats: ProfileStatsDto,
    timeControlFilter: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tcStats = stats.forTimeControl(timeControlFilter)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "history_chevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
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
                PositiveGreen,
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
            Icon(
                imageVector = TaratiIcons.ExpandMore,
                contentDescription = localizedString(Res.string.profile_history_section),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
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
