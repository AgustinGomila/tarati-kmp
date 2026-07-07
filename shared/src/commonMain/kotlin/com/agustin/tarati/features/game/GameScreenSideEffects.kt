package com.agustin.tarati.features.game


import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.ai.AIViewModel
import com.agustin.tarati.services.ai.IAIService
import com.agustin.tarati.services.clock.ClockViewModel
import com.agustin.tarati.services.clock.IClockService
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.board_position_copied_to_clipboard
import com.agustin.tarati.ui.components.game.animation.IBoardAnimationViewModel
import com.agustin.tarati.ui.components.game.behaviors.BoardSelectionViewModel
import com.agustin.tarati.ui.components.game.behaviors.IBoardSelectionViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/** All [LaunchedEffect]s of [GameScreen], extracted for readability. */
@Composable
fun GameScreenSideEffects(
    aiEngine: IAIEngine = koinInject(),
    aiViewModel: IAIService = koinViewModel<AIViewModel>(),
    clockService: IClockService = koinViewModel<ClockViewModel>(),
    selectViewModel: IBoardSelectionViewModel = koinViewModel<BoardSelectionViewModel>(),
    gameManagerState: GameManagerState,
    screenState: GameScreenState,
    services: GameScreenServices,
    events: GameEvents,
    viewModel: IGameModel,
    settingsViewModel: ISettingsViewModel,
    animationViewModel: IBoardAnimationViewModel,
    /** Must be the same [handleMove] from [GameScreen] so forced promotions are also sent online. */
    onMoveHandled: (Move) -> Unit = {},
    /** When true, server is the game-over authority — local timeout only stops the clock visually. */
    isOnlineGame: Boolean = false,
    /** When true, clock is managed by spectating sync — skip local pause/reset logic. */
    isSpectating: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val bus: UIMessageBus = koinInject()

    val isEditing by viewModel.isEditing.collectAsState()
    val aiEnabled by viewModel.aIEnabled.collectAsState(true)
    val playerSide by viewModel.playerSide.collectAsState()
    val boardOrientation by viewModel.boardOrientation.collectAsState()
    val pasteRequested by viewModel.pasteRequested.collectAsState()
    val boardPosition by viewModel.boardPosition.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()
    val hasTutorialBeenSeen by settingsViewModel.hasTutorialBeenSeen.collectAsState()
    val pendingPreMove by selectViewModel.pendingPreMove.collectAsState()

    LaunchedEffect(gameManagerState) {
        viewModel.saveGameState()
    }

    LaunchedEffect(isEditing, playerSide, aiEnabled, boardOrientation) {
        viewModel.saveGameState()
    }

    LaunchedEffect(hasTutorialBeenSeen) {
        if (!hasTutorialBeenSeen) {
            settingsViewModel.markTutorialSeen()
            events.startTutorial(scope)
        }
    }

    // whiteIsAI/blackIsAI rather than playerSide == currentTurn so AI vs AI doesn't
    // double-apply promotions already handled by the engine.
    LaunchedEffect(gameManagerState.gameState) {
        val state = gameManagerState.gameState
        val isCurrentTurnHuman =
            (state.currentTurn == CobColor.WHITE && !screenState.whiteIsAI) ||
                    (state.currentTurn == CobColor.BLACK && !screenState.blackIsAI)
        if (!isCurrentTurnHuman || isEditing || screenState.isTutorialActive) return@LaunchedEffect
        if (gameManagerState.gameStatus != GameStatus.PLAYING) return@LaunchedEffect

        val forcedPromotions = state.getForcedPromotions()
        if (forcedPromotions.size == 1) {
            onMoveHandled(forcedPromotions.first())
        }
    }

    LaunchedEffect(gameManagerState.gameState) {
        val state = gameManagerState.gameState
        val isCurrentTurnAI = (state.currentTurn == CobColor.WHITE && screenState.whiteIsAI) ||
                (state.currentTurn == CobColor.BLACK && screenState.blackIsAI)
        if (!isCurrentTurnAI || isEditing || screenState.isTutorialActive) return@LaunchedEffect
        if (gameManagerState.gameStatus != GameStatus.PLAYING) return@LaunchedEffect
        if (state.canClaimFiftyMoveDraw()) {
            events.claimFiftyMoveDraw(state)
        }
    }

    // ── AI move result ────────────────────────────────────────────────────────

    // rememberUpdatedState garantiza que los collectors de larga vida vean siempre el
    // valor más reciente, aunque LaunchedEffect(Unit) no se relance.
    val latestGameManagerState by rememberUpdatedState(gameManagerState)
    val latestIsOnlineGame by rememberUpdatedState(isOnlineGame)
    val latestIsSpectating by rememberUpdatedState(isSpectating)

    LaunchedEffect(Unit) {
        aiViewModel.pendingAIMove.collect { move ->
            // En partidas online / espectado la IA local nunca debe aplicar movimientos: el
            // estado autoritativo es el del servidor. Sin este guard, una IA local con la
            // configuración heredada de la última partida offline desincroniza el tablero y
            // el servidor rechaza toda jugada posterior con InvalidMove.
            if (latestIsOnlineGame || latestIsSpectating) return@collect
            if (latestGameManagerState.gameStatus == GameStatus.PLAYING) {
                handleGameMove(events, latestGameManagerState, move, viewModel)
            }
        }
    }

    // ── Pre-move execution ────────────────────────────────────────────────────

    LaunchedEffect(gameManagerState.gameState, gameManagerState.gameStatus) {
        val move = pendingPreMove
        if (move == null) {
            // State changed while the piece was selected but before the destination was tapped —
            // the pre-selection highlight is now stale and must be cleared.
            selectViewModel.resetPreMove()
            return@LaunchedEffect
        }
        val state = gameManagerState.gameState

        if (gameManagerState.gameStatus != GameStatus.PLAYING) {
            selectViewModel.resetPreMove()
            return@LaunchedEffect
        }

        if (isEditing || screenState.isTutorialActive) {
            selectViewModel.resetPreMove()
            return@LaunchedEffect
        }

        // En partidas online no hay IA local: el oponente es remoto y whiteIsAI/blackIsAI
        // reflejan la configuración de la partida local anterior (no reseteada al entrar online).
        // Ignorar el check de IA en ese contexto para que el pre-move pueda disparar.
        val isAITurn = !isOnlineGame && (
                (state.currentTurn == CobColor.WHITE && screenState.whiteIsAI) ||
                        (state.currentTurn == CobColor.BLACK && screenState.blackIsAI))
        if (isAITurn) return@LaunchedEffect

        if (move !in state.allMovesForTurn()) {
            selectViewModel.resetPreMove()
            return@LaunchedEffect
        }

        delay(200L.milliseconds)

        // pendingPreMove is not a key and may have changed during the delay.
        val liveMove = selectViewModel.pendingPreMove.value
        if (liveMove != move) return@LaunchedEffect

        // onMoveHandled en lugar de handleGameMove: en partidas online envía el movimiento
        // al servidor vía makeOnlineMove (igual que las promociones forzadas en línea 97).
        onMoveHandled(move)
        selectViewModel.resetPreMove()
    }

    LaunchedEffect(settingsState.preMovesEnabled) {
        if (!settingsState.preMovesEnabled) {
            selectViewModel.resetPreMove()
        }
    }

    // ── Fin de partida ────────────────────────────────────────────────────────

    val latestScreenState by rememberUpdatedState(screenState)
    val latestIsEditing by rememberUpdatedState(isEditing)

    // gameStatus is read from the StateFlow (not Compose snapshot) to avoid a premature
    // return when NotifyGameOver fires synchronously before recomposition.
    LaunchedEffect(Unit) {
        animationViewModel.gameOverReady.collect {
            val status = viewModel.gameStatus.value
            if (status != GameStatus.GAME_OVER) return@collect
            if (latestScreenState.isTutorialActive || latestIsEditing) return@collect
            // En partidas online y espectado el servidor es la autoridad del fin de partida.
            // Ambos usan rememberUpdatedState para leer el valor actual dentro del coroutine
            // de larga vida — sin esto, los valores se congelan en la primera composición.
            if (latestIsOnlineGame || latestIsSpectating) return@collect
            val gameState = viewModel.gameState.value
            animationViewModel.animateGameOver(gameState.getMatchState(aiEngine.positionHistory))
            events.gameOver(scope)
        }
    }

    LaunchedEffect(gameManagerState.gameStatus) {
        val status = gameManagerState.gameStatus
        val clockState = clockService.clockState.value
        when (status) {
            GameStatus.PLAYING -> {
                if (clockState.activeColor == null) {
                    clockService.startClockFor(gameManagerState.gameState.currentTurn)
                } else if (!clockState.running) {
                    clockService.resumeClock()
                }
            }

            GameStatus.NO_PLAYING -> {
                // En espectado el reloj lo gestiona syncFromServer — no pausar localmente.
                if (!isSpectating && clockState.running) clockService.pauseClock()
            }

            GameStatus.GAME_OVER -> clockService.stopClock()
        }
    }

    LaunchedEffect(Unit) {
        clockService.timeoutEvents.collect { loser ->
            if (latestIsEditing || latestScreenState.isTutorialActive) return@collect
            if (isOnlineGame) return@collect
            events.onTimeout(loser)
        }
    }

    // firstClockSync resets the clock unconditionally on startup; subsequent changes
    // only reset when not actively playing, preserving accumulated time.
    var firstClockSync by remember { mutableStateOf(true) }
    LaunchedEffect(settingsState.timeControl) {
        // En espectado el modo lo establece syncFromServer — no resetear con el modo local.
        if (!isSpectating && (firstClockSync || gameManagerState.gameStatus != GameStatus.PLAYING)) {
            clockService.resetClock(settingsState.timeControl)
        }
        firstClockSync = false
    }

    // Aplica una posición pegada desde el portapapeles.
    LaunchedEffect(pasteRequested) {
        if (pasteRequested) {
            viewModel.pasteBoardFromClipboard(false)
            val newGameState = services.clipboardHelper.pasteBoardPosition()
            if (newGameState != null) {
                viewModel.setGame(newGameState)
            }
        }
    }

    // Copia la posición del tablero al portapapeles cuando el ViewModel la emite.
    val boardPositionCopiedMessage = localizedString(Res.string.board_position_copied_to_clipboard)
    LaunchedEffect(boardPosition) {
        if (boardPosition == "") return@LaunchedEffect
        viewModel.boardPositionCopied()
        services.clipboardHelper.copyBoardPosition(boardPosition)
        bus.toast(UIMessage.Toast(message = boardPositionCopiedMessage))
    }

    // Sincroniza el flag de efectos animados con el ViewModel de animación.
    LaunchedEffect(settingsState.boardVisualState.animateEffects) {
        animationViewModel.updateAnimateEffects(settingsState.boardVisualState.animateEffects)
    }

    // Sincroniza el estilo de animación de conversión con el ViewModel de animación.
    LaunchedEffect(settingsState.boardVisualState.conversionAnimationStyle) {
        animationViewModel.updateConversionAnimationStyle(
            settingsState.boardVisualState.conversionAnimationStyle
        )
    }

    // Propaga el nombre de usuario al ViewModel de juego.
    LaunchedEffect(settingsState.userName) {
        viewModel.updateUserName(settingsState.userName)
    }
}
