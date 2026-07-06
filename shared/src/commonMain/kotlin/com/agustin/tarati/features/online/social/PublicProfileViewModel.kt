package com.agustin.tarati.features.online.social


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.lobby.GameHistoryUiState
import com.agustin.tarati.features.online.lobby.HistoryFilters
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

    private val _historyState = MutableStateFlow(GameHistoryUiState())
    override val historyState: StateFlow<GameHistoryUiState> = _historyState.asStateFlow()

    private val _followStatusState = MutableStateFlow(FollowStatusUiState())
    override val followStatusState: StateFlow<FollowStatusUiState> = _followStatusState.asStateFlow()

    private val _achievements = MutableStateFlow<List<ServerAchievementDto>>(emptyList())
    override val achievements: StateFlow<List<ServerAchievementDto>> = _achievements.asStateFlow()

    override val isOwnProfile: Boolean
        get() = authViewModel.currentUser?.userId == userId

    init {
        loadProfile()
        loadHistory()
        loadAchievements()
        if (!isOwnProfile) loadFollowStatus()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = true, error = null) }
            val token = getValidToken() ?: run {
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

    private fun loadHistory() {
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            _historyState.update {
                it.copy(isLoading = true, error = null, currentPage = 0, games = emptyList())
            }
            repository.getUserGames(
                token = token,
                userId = userId,
                timeControl = _historyState.value.filters.timeControl,
                result = _historyState.value.filters.result,
                rated = _historyState.value.filters.rated,
            ).onSuccess { paged ->
                _historyState.update {
                    it.copy(
                        games = paged.items,
                        isLoading = false,
                        currentPage = 0,
                        total = paged.total,
                        hasMore = paged.items.size < paged.total,
                    )
                }
            }.onFailure { e ->
                _historyState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    override fun loadMoreHistory() {
        val state = _historyState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            val nextPage = state.currentPage + 1
            _historyState.update { it.copy(isLoadingMore = true) }
            repository.getUserGames(
                token = token,
                userId = userId,
                page = nextPage,
                timeControl = state.filters.timeControl,
                result = state.filters.result,
                rated = state.filters.rated,
            ).onSuccess { paged ->
                _historyState.update {
                    it.copy(
                        games = it.games + paged.items,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        total = paged.total,
                        hasMore = (it.games.size + paged.items.size) < paged.total,
                    )
                }
            }.onFailure { e ->
                _historyState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    override fun setTimeControlFilter(tc: String?) {
        _historyState.update { it.copy(filters = it.filters.copy(timeControl = tc)) }
        loadHistory()
    }

    override fun setResultFilter(result: String?) {
        _historyState.update { it.copy(filters = it.filters.copy(result = result)) }
        loadHistory()
    }

    override fun setRatedFilter(rated: Boolean?) {
        _historyState.update { it.copy(filters = it.filters.copy(rated = rated)) }
        loadHistory()
    }

    override fun clearFilters() {
        _historyState.update { it.copy(filters = HistoryFilters()) }
        loadHistory()
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    private fun loadFollowStatus() {
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
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
            val token = getValidToken() ?: return@launch
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
            val token = getValidToken() ?: return@launch
            repository.getUserAchievements(token = token, userId = userId)
                .onSuccess { list -> _achievements.value = list }
        }
    }

    override fun sendChallenge(timeControl: String, rated: Boolean) {
        viewModelScope.launch {
            onlineGameViewModel.sendChallenge(userId, timeControl, rated)
        }
    }

    private suspend fun getValidToken(): String? {
        if (authViewModel.isTokenExpiringSoon()) authViewModel.refreshToken()
        return authViewModel.accessToken
    }
}
