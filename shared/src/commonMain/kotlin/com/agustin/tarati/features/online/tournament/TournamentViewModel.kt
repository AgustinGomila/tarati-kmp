package com.agustin.tarati.features.online.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.game.TournamentEvent
import com.agustin.tarati.network.models.CreateTournamentRequest
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.network.models.TournamentSummaryDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel de torneos.
 *
 * Gestiona la lista de torneos y el detalle de un torneo específico.
 * Se suscribe a [IOnlineGameViewModel.tournamentEvents] para recibir actualizaciones
 * en tiempo real (standings, nueva ronda, fin) mientras la pantalla de detalle está activa.
 *
 * El token JWT se obtiene con [validToken] en cada petición (renovación proactiva
 * incluida) — necesario porque el polling y la pantalla pueden quedar abiertos más
 * de los 15 minutos de vida del access token.
 *
 * Registrado como `viewModel` (no `single`) en Koin: cada pantalla de torneo
 * tiene su propia instancia con su propio estado de carga.
 */
class TournamentViewModel(
    private val repository: TournamentRepository,
    private val authViewModel: IAuthViewModel,
    onlineGameViewModel: IOnlineGameViewModel,
) : ViewModel(), ITournamentViewModel {

    private val _listState = MutableStateFlow(TournamentListUiState(isLoading = true))
    override val listState: StateFlow<TournamentListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(TournamentDetailUiState())
    override val detailState: StateFlow<TournamentDetailUiState> = _detailState.asStateFlow()

    // ID del torneo que se está viendo en detalle — para filtrar eventos WS
    private var currentDetailId: String? = null
    private var pollingJob: Job? = null

    init {
        onlineGameViewModel.tournamentEvents
            .onEach { event ->
                val targetId = currentDetailId

                when (event) {
                    is TournamentEvent.StandingsUpdated -> {
                        if (event.tournamentId != targetId) return@onEach
                        // Actualiza standings inline para feedback inmediato
                        _detailState.value = _detailState.value.let { s ->
                            s.copy(tournament = s.tournament?.copy(standings = event.standings))
                        }
                        // Recarga el detalle completo para capturar el fixture actualizado
                        // (estado ACTIVE → COMPLETED + resultado de la partida)
                        loadTournament(event.tournamentId)
                    }

                    is TournamentEvent.RoundStarted -> {
                        // Torneo pasó a ACTIVE: refrescar lista + detalle si está abierto.
                        loadTournaments()
                        if (event.tournamentId == targetId) loadTournament(event.tournamentId)
                    }

                    is TournamentEvent.Finished -> {
                        // Torneo terminó: refrescar lista + detalle si está abierto.
                        loadTournaments()
                        if (event.tournamentId == targetId) loadTournament(event.tournamentId)
                    }

                    is TournamentEvent.Cancelled -> {
                        // Torneo cancelado: refrescar lista + detalle si está abierto.
                        loadTournaments()
                        if (event.tournamentId == targetId) loadTournament(event.tournamentId)
                    }

                    is TournamentEvent.GameAssigned -> Unit // Manejado globalmente en AppContent
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Polling ────────────────────────────────────────────────────────────────

    override fun startTournamentPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchTournaments()
                delay(POLL_INTERVAL) // cancellation point — CancellationException detiene el loop
            }
        }
    }

    override fun stopTournamentPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ── Lista ──────────────────────────────────────────────────────────────────

    override fun loadTournaments() {
        viewModelScope.launch { fetchTournaments() }
    }

    private suspend fun fetchTournaments() {
        val token = authViewModel.validToken() ?: return
        _listState.value = _listState.value.copy(isLoading = true, error = null)
        repository.getTournaments(token)
            .onSuccess { tournaments ->
                _listState.value = TournamentListUiState(
                    registering = tournaments.filter { it.status == TournamentStatus.REGISTERING },
                    active = tournaments.filter { it.status == TournamentStatus.ACTIVE },
                    finished = tournaments.filter { it.status == TournamentStatus.FINISHED },
                )
            }
            .onFailure { e ->
                _listState.value = _listState.value.copy(isLoading = false, error = e.message)
            }
    }

    // ── Detalle ────────────────────────────────────────────────────────────────

    override fun loadTournament(id: String) {
        currentDetailId = id
        viewModelScope.launch {
            val token = authViewModel.validToken() ?: return@launch
            _detailState.value = _detailState.value.copy(isLoading = true, error = null)
            repository.getTournament(token, id)
                .onSuccess { t -> _detailState.value = TournamentDetailUiState(tournament = t) }
                .onFailure { e -> _detailState.value = TournamentDetailUiState(error = e.message) }
        }
    }

    // ── Acciones ───────────────────────────────────────────────────────────────

    override suspend fun createTournament(
        request: CreateTournamentRequest,
    ): Result<TournamentSummaryDto> =
        withToken { token -> repository.createTournament(token, request) }
            .also { if (it.isSuccess) loadTournaments() }

    override suspend fun register(id: String): Result<Unit> =
        withToken { token -> repository.register(token, id) }
            .also { if (it.isSuccess) loadTournament(id) }

    override suspend fun unregister(id: String): Result<Unit> =
        withToken { token -> repository.unregister(token, id) }
            .also { if (it.isSuccess) loadTournament(id) }

    override suspend fun start(id: String): Result<Unit> =
        withToken { token -> repository.start(token, id) }
            .also { if (it.isSuccess) loadTournament(id) }

    override suspend fun cancel(id: String): Result<Unit> =
        withToken { token -> repository.cancel(token, id) }
            .also { if (it.isSuccess) loadTournament(id) }

    /** Ejecuta [block] con un token válido, o falla si no hay sesión. */
    private suspend fun <T> withToken(block: suspend (String) -> Result<T>): Result<T> {
        val token = authViewModel.validToken()
            ?: return Result.failure(Exception("Not authenticated"))
        return block(token)
    }

    companion object {
        private val POLL_INTERVAL = 30.seconds
    }
}
