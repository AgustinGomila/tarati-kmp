package com.agustin.tarati.features.game6

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.models.MpLeaderboardEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la clasificación multijugador (Tarati Six). Bucket único (sin tabs de time control).
 */
@Immutable
data class MpLeaderboardUiState(
    val entries: List<MpLeaderboardEntryDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Stable
interface IMpLeaderboardViewModel {
    val state: StateFlow<MpLeaderboardUiState>
    fun refresh()
}

/**
 * ViewModel de la clasificación multijugador online (Fase 4a). Clase plana con scope inyectable
 * (igual que [MpLobbyViewModel]/[MpLocalGameViewModel], no un androidx `ViewModel`) → testeable sin
 * `Dispatchers.setMain`. `fetchLeaderboard` inyectado (default no-op) para poder mockearlo en tests.
 */
@Stable
class MpLeaderboardViewModel(
    private val getToken: suspend () -> String?,
    private val fetchLeaderboard: suspend (token: String) -> Result<List<MpLeaderboardEntryDto>> =
        { Result.success(emptyList()) },
    scope: CoroutineScope? = null,
) : IMpLeaderboardViewModel {
    private val logger = getLogger("MpLeaderboardViewModel")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(MpLeaderboardUiState())
    override val state: StateFlow<MpLeaderboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    override fun refresh(): Unit = load()

    private fun load() {
        _scope.launch {
            _state.update { it.copy(isLoading = it.entries.isEmpty(), error = null) }
            val token = getToken() ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            fetchLeaderboard(token)
                .onSuccess { entries -> _state.update { it.copy(entries = entries, isLoading = false) } }
                .onFailure { e ->
                    logger.debug("getMpLeaderboard failed: ${e.message}")
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
