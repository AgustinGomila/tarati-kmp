package com.agustin.tarati.features.online.social


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.lobby.GameHistoryUiState
import com.agustin.tarati.features.online.lobby.PagedGameHistoryLoader
import com.agustin.tarati.network.models.ServerAchievementDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PublicProfileViewModel(
    private val userId: String,
    private val repository: SocialRepository,
    private val authViewModel: IAuthViewModel,
    private val onlineGameViewModel: IOnlineGameViewModel,
) : ViewModel(), IPublicProfileViewModel {

    private val _profileState = MutableStateFlow(PublicProfileUiState())
    override val profileState: StateFlow<PublicProfileUiState> = _profileState.asStateFlow()

    /** Historial del usuario del perfil ([GET /api/users/:id/games]) con filtros del servidor. */
    private val historyLoader = PagedGameHistoryLoader(viewModelScope, authViewModel) { token, page, limit, filters ->
        repository.getUserGames(
            token = token,
            userId = userId,
            page = page,
            limit = limit,
            timeControl = filters.timeControl,
            result = filters.result,
            rated = filters.rated,
        )
    }
    override val historyState: StateFlow<GameHistoryUiState> = historyLoader.state

    private val _followStatusState = MutableStateFlow(FollowStatusUiState())
    override val followStatusState: StateFlow<FollowStatusUiState> = _followStatusState.asStateFlow()

    private val _achievements = MutableStateFlow<List<ServerAchievementDto>>(emptyList())
    override val achievements: StateFlow<List<ServerAchievementDto>> = _achievements.asStateFlow()

    override val isOwnProfile: Boolean
        get() = authViewModel.currentUser?.userId == userId

    init {
        loadProfile()
        historyLoader.load()
        loadAchievements()
        if (!isOwnProfile) loadFollowStatus()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = true, error = null) }
            val token = authViewModel.validToken() ?: run {
                _profileState.update { it.copy(isLoading = false) }
                return@launch
            }
            repository.getUserProfile(token = token, userId = userId)
                .onSuccess { profile ->
                    _profileState.update { it.copy(profile = profile, isLoading = false) }
                }
                .onFailure { e ->
                    _profileState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ── History (delegado en el loader compartido) ────────────────────────────

    override fun loadMoreHistory(): Unit = historyLoader.loadMore()

    override fun setTimeControlFilter(tc: String?): Unit = historyLoader.setTimeControlFilter(tc)

    override fun setResultFilter(result: String?): Unit = historyLoader.setResultFilter(result)

    override fun setRatedFilter(rated: Boolean?): Unit = historyLoader.setRatedFilter(rated)

    override fun clearFilters(): Unit = historyLoader.clearFilters()

    // ── Follow ────────────────────────────────────────────────────────────────

    private fun loadFollowStatus() {
        viewModelScope.launch {
            val token = authViewModel.validToken() ?: return@launch
            _followStatusState.update { it.copy(isLoading = true) }
            repository.getFollowStatus(token = token, userId = userId)
                .onSuccess { dto ->
                    _followStatusState.update {
                        it.copy(
                            isFollowing = dto.isFollowing,
                            followersCount = dto.followersCount,
                            followingCount = dto.followingCount,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { _followStatusState.update { it.copy(isLoading = false) } }
        }
    }

    override fun toggleFollow() {
        viewModelScope.launch {
            val token = authViewModel.validToken() ?: return@launch
            val wasFollowing = _followStatusState.value.isFollowing
            // Optimistic update
            _followStatusState.update {
                it.copy(
                    isFollowing = !wasFollowing,
                    followersCount = if (wasFollowing) it.followersCount - 1 else it.followersCount + 1,
                )
            }
            val result = if (wasFollowing) repository.unfollowUser(token, userId)
            else repository.followUser(token, userId)
            result.onFailure {
                // Revert on failure
                _followStatusState.update {
                    it.copy(
                        isFollowing = wasFollowing,
                        followersCount = if (wasFollowing) it.followersCount + 1 else it.followersCount - 1,
                    )
                }
            }
        }
    }

    // ── Challenge ─────────────────────────────────────────────────────────────

    private fun loadAchievements() {
        viewModelScope.launch {
            val token = authViewModel.validToken() ?: return@launch
            repository.getUserAchievements(token = token, userId = userId)
                .onSuccess { list -> _achievements.value = list }
        }
    }

    override fun sendChallenge(timeControl: String, rated: Boolean) {
        viewModelScope.launch {
            onlineGameViewModel.sendChallenge(userId, timeControl, rated)
        }
    }
}
