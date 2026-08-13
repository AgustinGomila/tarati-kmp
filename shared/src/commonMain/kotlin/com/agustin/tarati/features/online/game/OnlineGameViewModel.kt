package com.agustin.tarati.features.online.game


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.client.OnlineGameClient
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel que orquesta el juego online.
 *
 * Puente entre [OnlineGameClient] (comunicación con servidor) y la UI ([GameScreen]).
 * Expone los flows del client sin transformación y delega todas las operaciones.
 *
 * La sincronización del tablero visual se maneja en [GameScreen] mediante
 * `LaunchedEffect(currentOnlineGame?.gameState)` → `events.applyMove(...)`.
 * Este ViewModel no necesita conocer [AndroidGameViewModel].
 *
 * ```
 * ┌──────────────────────────────────────────────────┐
 * │              GameScreen UI                       │
 * └────────────┬────────────────────┬────────────────┘
 *              │                    │
 *    ┌─────────▼──────┐   ┌────────▼────────────┐
 *    │  GameViewModel │   │ OnlineGameViewModel │
 *    │  (local state) │   │  (online state)     │
 *    └────────────────┘   └────────┬────────────┘
 *                                  │
 *                         ┌────────▼─────────┐
 *                         │ OnlineGameClient │
 *                         └────────┬─────────┘
 *                                  │
 *                         ┌────────▼────────┐
 *                         │  WebSocket      │
 *                         │  ↓↑ Server      │
 *                         └─────────────────┘
 * ```
 *
 * @param onlineClient Cliente de comunicación online.
 */
class OnlineGameViewModel(
    private val onlineClient: OnlineGameClient,
) : ViewModel(), IOnlineGameViewModel {

    private val logger = getLogger("OnlineGameViewModel")

    // ============ State (delegated to OnlineGameClient) ============

    override val currentGame: StateFlow<OnlineGame?> = onlineClient.currentGame

    override val lastFinishedGameId: StateFlow<String?> = onlineClient.lastFinishedGameId
    override val matchmakingState: StateFlow<MatchmakingState> = onlineClient.matchmakingState
    override val drawOffer: StateFlow<String?> = onlineClient.drawOffer
    override val pendingDrawSent: StateFlow<Boolean> = onlineClient.pendingDrawSent
    override val drawOfferEvents: SharedFlow<DrawOfferEvent> = onlineClient.drawOfferEvents

    // ── Spectating ────────────────────────────────────────────────────────────

    override val spectatingState: StateFlow<SpectatingState?> = onlineClient.spectatingState
    override val spectatingGameEnded: SharedFlow<SpectatingGameEndedEvent> = onlineClient.spectatingGameEnded

    // ── Rematch ───────────────────────────────────────────────────────────────

    override val rematchOffer: StateFlow<String?> = onlineClient.rematchOffer
    override val rematchEvents: SharedFlow<RematchEvent> = onlineClient.rematchEvents
    override val serverErrors: SharedFlow<ServerErrorEvent> = onlineClient.serverErrors

    // ── Challenge ─────────────────────────────────────────────────────────────

    override val challengeEvents: SharedFlow<ChallengeEvent> = onlineClient.challengeEvents

    // ── Torneos ───────────────────────────────────────────────────────────────

    override val tournamentEvents: SharedFlow<TournamentEvent> = onlineClient.tournamentEvents

    init {
        observeGameStatusTransitions()
    }

    // ============ Matchmaking API ============

    override suspend fun startMatchmaking(
        timeControl: String,
        rated: Boolean,
        spectatingAllowed: Boolean,
    ): Result<String> {
        logger.debug("startMatchmaking: timeControl=$timeControl, rated=$rated spectating=$spectatingAllowed")

        return try {
            val previousState = matchmakingState.value
            val previousTicketId = (previousState as? MatchmakingState.Searching)?.ticket?.ticketId

            onlineClient.joinMatchmaking(timeControl, rated, spectatingAllowed)

            val state = matchmakingState.first { s ->
                when (s) {
                    is MatchmakingState.Searching if s.ticket.ticketId != previousTicketId -> true
                    MatchmakingState.Idle if s != previousState -> true
                    else -> false
                }
            }

            if (state is MatchmakingState.Searching) {
                Result.success(state.ticket.ticketId)
            } else {
                Result.failure(Exception("Failed to enter matchmaking queue"))
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("startMatchmaking failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun joinOpenSearch(
        targetUserId: String,
        timeControl: String,
        rated: Boolean,
    ): Result<Unit> {
        logger.debug("joinOpenSearch: target=$targetUserId tc=$timeControl rated=$rated")
        return try {
            onlineClient.joinOpenSearch(targetUserId, timeControl, rated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("joinOpenSearch failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun cancelMatchmaking() {
        logger.debug("cancelMatchmaking")
        if (!isSearchingMatch) {
            logger.debug("Not searching, nothing to cancel")
            return
        }
        safely("cancelMatchmaking") { onlineClient.cancelMatchmaking() }
    }

    // ============ Gameplay API ============

    override suspend fun makeOnlineMove(move: Move) {
        if (currentGame.value == null) {
            logger.warn("makeOnlineMove: no active game, ignoring")
            return
        }
        logger.debug("makeOnlineMove: ${move.from} → ${move.to}")
        safely("makeOnlineMove") { onlineClient.makeMove(move) }
    }

    override suspend fun resign() {
        val game = currentGame.value
        if (game == null) {
            logger.warn("resign: no active game, ignoring")
            return
        }
        logger.debug("resign: gameId=${game.gameId}")
        safely("resign") { onlineClient.resign() }
    }

    override suspend fun offerDraw() {
        val game = currentGame.value
        if (game == null) {
            logger.warn("offerDraw: no active game, ignoring")
            return
        }
        logger.debug("offerDraw: gameId=${game.gameId}")
        safely("offerDraw") { onlineClient.offerDraw() }
    }

    override suspend fun respondToDraw(accept: Boolean) {
        val game = currentGame.value
        if (game == null) {
            logger.warn("respondToDraw: no active game, ignoring")
            return
        }
        logger.debug("respondToDraw: accept=$accept, gameId=${game.gameId}")
        safely("respondToDraw") { onlineClient.respondToDraw(accept) }
    }

    // ============ Rematch API ============

    override suspend fun offerRematch(gameId: String) {
        logger.debug("offerRematch game=$gameId")
        safely("offerRematch") { onlineClient.offerRematch(gameId) }
    }

    override suspend fun acceptRematch() {
        logger.debug("acceptRematch")
        safely("acceptRematch") { onlineClient.acceptRematch() }
    }

    override suspend fun declineRematch() {
        logger.debug("declineRematch")
        safely("declineRematch") { onlineClient.declineRematch() }
    }

    // ============ State Synchronization ============

    /**
     * Implementación intencionalmente vacía.
     *
     * La sincronización del tablero visual con el estado del servidor se maneja
     * en [GameScreen] mediante `LaunchedEffect(currentOnlineGame?.gameState)` que
     * llama a `events.applyMove(...)`. Este diseño mantiene la lógica de UI en la
     * capa de UI y evita el acoplamiento entre OnlineGameViewModel y GameViewModel.
     */
    override fun syncOnlineStateToLocal(onlineState: GameState): Unit = Unit

    override fun clearOnlineGame(gameId: String) {
        logger.debug("clearOnlineGame: $gameId")
        onlineClient.clearCurrentGame(gameId)
    }

    override fun clearLastFinishedGameId() {
        onlineClient.clearLastFinishedGameId()
    }

    override suspend fun spectateGame(gameId: String): Boolean {
        logger.debug("spectateGame: $gameId")
        return try {
            onlineClient.spectateGame(gameId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("spectateGame failed: ${e.message}")
            false
        }
    }

    override suspend fun stopSpectating() {
        val gameId = spectatingState.value?.gameId ?: return
        logger.debug("stopSpectating: $gameId")
        safely("stopSpectating") { onlineClient.stopSpectating(gameId) }
    }

    override fun clearSpectatingAfterGameEnded() {
        onlineClient.clearSpectatingState()
    }

    // ============ Private Helpers ============

    private fun observeGameStatusTransitions() {
        currentGame
            .distinctUntilChangedBy { it?.status }
            .onEach { game ->
                when (val status = game?.status) {
                    null -> Unit
                    OnlineGameStatus.Starting ->
                        logger.debug("Game starting: ${game.gameId}")

                    OnlineGameStatus.InProgress ->
                        logger.debug("Game in progress: ${game.gameId}")

                    is OnlineGameStatus.Finished ->
                        logger.debug("Game ended: ${status.result} (${status.reason})")
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Ejecuta [block] propagando la cancelación estructurada y logueando cualquier otra excepción
     * como "$op failed". Patrón fire-and-forget de las acciones que delegan en [onlineClient].
     */
    private suspend fun safely(op: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("$op failed: ${e.message}")
        }
    }

    // ============ Challenge API ============

    override suspend fun sendChallenge(targetUserId: String, timeControl: String, rated: Boolean) {
        logger.debug("sendChallenge: target=$targetUserId tc=$timeControl rated=$rated")
        safely("sendChallenge") { onlineClient.sendChallenge(targetUserId, timeControl, rated) }
    }

    override suspend fun respondToChallenge(challengeId: String, accept: Boolean) {
        logger.debug("respondToChallenge: id=$challengeId accept=$accept")
        safely("respondToChallenge") { onlineClient.respondToChallenge(challengeId, accept) }
    }

    override suspend fun cancelChallenge(challengeId: String) {
        logger.debug("cancelChallenge: id=$challengeId")
        safely("cancelChallenge") { onlineClient.cancelChallenge(challengeId) }
    }

    // ============ Cleanup ============

    override fun onCleared() {
        super.onCleared()
        logger.debug("onCleared")
        onlineClient.clearCurrentGameUnconditionally()
    }
}
