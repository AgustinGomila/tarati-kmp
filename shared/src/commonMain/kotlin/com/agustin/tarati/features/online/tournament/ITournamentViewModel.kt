package com.agustin.tarati.features.online.tournament

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.agustin.tarati.network.models.CreateTournamentRequest
import com.agustin.tarati.network.models.TournamentDetailDto
import com.agustin.tarati.network.models.TournamentSummaryDto
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato público del ViewModel de torneos.
 *
 * El token JWT es un detalle interno: el ViewModel lo obtiene (y renueva) con
 * `validToken()` en cada petición — la UI nunca maneja tokens.
 */
@Stable
interface ITournamentViewModel {
    val listState: StateFlow<TournamentListUiState>
    val detailState: StateFlow<TournamentDetailUiState>

    fun loadTournaments()
    fun loadTournament(id: String)
    fun startTournamentPolling()
    fun stopTournamentPolling()
    suspend fun createTournament(request: CreateTournamentRequest): Result<TournamentSummaryDto>
    suspend fun register(id: String): Result<Unit>
    suspend fun unregister(id: String): Result<Unit>
    suspend fun start(id: String): Result<Unit>
    suspend fun cancel(id: String): Result<Unit>
}

@Immutable
data class TournamentListUiState(
    val registering: List<TournamentSummaryDto> = emptyList(),
    val active: List<TournamentSummaryDto> = emptyList(),
    val finished: List<TournamentSummaryDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && registering.isEmpty() && active.isEmpty() && finished.isEmpty()
}

@Immutable
data class TournamentDetailUiState(
    val tournament: TournamentDetailDto? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)
