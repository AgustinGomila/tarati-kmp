package com.agustin.tarati.features.online.social


import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.agustin.tarati.features.game6.MpHistoryUiState
import com.agustin.tarati.features.online.lobby.GameHistoryUiState
import com.agustin.tarati.network.models.PublicProfileDto
import com.agustin.tarati.network.models.ServerAchievementDto
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class PublicProfileUiState(
    val profile: PublicProfileDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Immutable
data class FollowStatusUiState(
    val isFollowing: Boolean = false,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val isLoading: Boolean = false,
)

@Stable
interface IPublicProfileViewModel {
    val profileState: StateFlow<PublicProfileUiState>
    val historyState: StateFlow<GameHistoryUiState>

    /** Historial paginado de partidas multijugador (Tarati Six) del perfil. */
    val mpHistoryState: StateFlow<MpHistoryUiState>
    val followStatusState: StateFlow<FollowStatusUiState>
    val achievements: StateFlow<List<ServerAchievementDto>>

    /** True si el perfil visualizado corresponde al usuario autenticado. */
    val isOwnProfile: Boolean

    /**
     * Cargas perezosas de los historiales paginados: la primera página se pide en el primer expand de
     * su sección (idempotentes; no recargan si ya se pidieron). Sus headers muestran W/D/L desde el
     * payload del perfil, así que se ven correctos colapsados. Perfil, ratings, follow y **logros** son
     * eager (el contador `X/N` del header de logros sale del propio listado, no del payload).
     */
    fun ensureHistoryLoaded()
    fun ensureMpHistoryLoaded()

    fun loadMoreHistory()

    /** Carga la siguiente página del historial multijugador (scroll infinito). */
    fun loadMoreMpHistory()
    fun setTimeControlFilter(tc: String?)
    fun setResultFilter(result: String?)
    fun setRatedFilter(rated: Boolean?)
    fun clearFilters()

    /** Alterna entre seguir y dejar de seguir al usuario del perfil. */
    fun toggleFollow()

    /** Envía un desafío directo al usuario del perfil. */
    fun sendChallenge(timeControl: String, rated: Boolean)
}
