package com.agustin.tarati.features.online.social


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.features.game6.MpHistoryUiState
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

    /** Historial multijugador del perfil ([GET /api/users/:id/mp-games]). MP no tiene filtros. */
    private val _mpHistoryState = MutableStateFlow(MpHistoryUiState())
    override val mpHistoryState: StateFlow<MpHistoryUiState> = _mpHistoryState.asStateFlow()

    private val _followStatusState = MutableStateFlow(FollowStatusUiState())
    override val followStatusState: StateFlow<FollowStatusUiState> = _followStatusState.asStateFlow()

    private val _achievements = MutableStateFlow<List<ServerAchievementDto>>(emptyList())
    override val achievements: StateFlow<List<ServerAchievementDto>> = _achievements.asStateFlow()

    override val isOwnProfile: Boolean
        get() = authViewModel.currentUser?.userId == userId

    // Guarda de carga perezosa del historial clásico: pide su primera página una sola vez, en su
    // primer expand ([historyLoader.load] recarga en cada llamada, así que necesita el flag).
    private var historyRequested = false

    init {
        loadProfile()
        // Logros son eager: el contador `X/N` del header debe ser correcto aun con la sección colapsada.
        loadAchievements()
        if (!isOwnProfile) loadFollowStatus()
    }

    // ── Cargas perezosas de los historiales paginados (primer expand de cada sección) ──

    override fun ensureHistoryLoaded() {
        if (historyRequested) return
        historyRequested = true
        historyLoader.load()
    }

    // [loadMpHistory] ya es idempotente por su guarda interna (isLoading/loaded).
    override fun ensureMpHistoryLoaded(): Unit = loadMpHistory()

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

    // ── Historial multijugador (paginado propio; MP no comparte el loader clásico) ──

    private fun loadMpHistory() {
        val st = _mpHistoryState.value
        if (st.isLoading || st.loaded) return
        viewModelScope.launch { fetchMpHistoryPage(0, replace = true) }
    }

    override fun loadMoreMpHistory() {
        val st = _mpHistoryState.value
        if (st.isLoading || st.endReached) return
        viewModelScope.launch { fetchMpHistoryPage(st.page + 1, replace = false) }
    }

    private suspend fun fetchMpHistoryPage(page: Int, replace: Boolean) {
        val token = authViewModel.validToken() ?: return
        _mpHistoryState.update { it.copy(isLoading = true, error = null) }
        repository.getUserMpGames(token, userId, page)
            .onSuccess { resp ->
                _mpHistoryState.update { cur ->
                    val merged = if (replace) resp.items else cur.items + resp.items
                    cur.copy(
                        items = merged,
                        isLoading = false,
                        page = resp.page,
                        endReached = merged.size.toLong() >= resp.total,
                        loaded = true,
                    )
                }
            }
            .onFailure { e ->
                _mpHistoryState.update { it.copy(isLoading = false, error = e.message, loaded = true) }
            }
    }

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
