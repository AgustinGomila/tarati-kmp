package com.agustin.tarati.features.online.social


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaderboardViewModel(
    private val repository: SocialRepository,
    private val authViewModel: IAuthViewModel,
) : ViewModel(), ILeaderboardViewModel {

    private val _leaderboardState = MutableStateFlow(LeaderboardUiState())
    override val leaderboardState: StateFlow<LeaderboardUiState> = _leaderboardState.asStateFlow()

    private val _selectedTc = MutableStateFlow(TimeControl.BLITZ.key)
    override val selectedTc: StateFlow<String> = _selectedTc.asStateFlow()

    init {
        loadLeaderboard(TimeControl.BLITZ.key)
    }

    override fun selectTimeControl(tc: String) {
        if (_selectedTc.value == tc) return
        _selectedTc.value = tc
        loadLeaderboard(tc)
    }

    override fun refresh() {
        loadLeaderboard(_selectedTc.value)
    }

    private fun loadLeaderboard(tc: String) {
        viewModelScope.launch {
            _leaderboardState.update { it.copy(isLoading = it.entries.isEmpty(), error = null) }
            val token = authViewModel.validToken() ?: run {
                _leaderboardState.update { it.copy(isLoading = false) }
                return@launch
            }
            repository.getLeaderboard(token = token, timeControl = tc)
                .onSuccess { entries ->
                    _leaderboardState.update { it.copy(entries = entries, isLoading = false) }
                }
                .onFailure { e ->
                    _leaderboardState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
