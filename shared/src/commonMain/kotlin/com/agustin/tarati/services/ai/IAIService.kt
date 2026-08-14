package com.agustin.tarati.services.ai

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IAIService {
    /**
     * Emits one [Move] each time the AI engine finishes computing a move.
     * Observed by [GameScreen] via
     * [androidx.compose.runtime.LaunchedEffect] so the result survives
     * configuration changes — the computation runs in [AIViewModel.viewModelScope]
     * and the new composition picks up the emission after rotation.
     *
     * replay = 0: a move emitted during a configuration change is not replayed.
     * The [androidx.compose.runtime.LaunchedEffect] for
     * [AiThinkingDependencies] re-triggers [requestAIMove]
     * after rotation only if [isAIThinking] is false — preventing duplicate calls.
     */
    val pendingAIMove: SharedFlow<Move>

    val isAIThinking: StateFlow<Boolean>

    /**
     * Launches the AI computation in [AIViewModel.viewModelScope].
     * Safe to call from a Composable context — does not require a coroutine scope
     * from the composition. Ignored if the engine is already thinking.
     *
     * [difficulty] is the difficulty of the side about to move; the runner rebuilds the
     * canonical config from it (and, on Web, ships it to the engine worker).
     */
    fun requestAIMove(gameState: GameState, difficulty: Difficulty)

    /**
     * Cancels the in-flight AI computation, if any, and lowers [isAIThinking].
     *
     * Must be called when a new game starts: the computation runs in
     * [AIViewModel.viewModelScope], which survives the board reset. Without cancelling,
     * its move — computed for the previous position — would return late and be applied to
     * the fresh board, corrupting it (it overwrites a piece and shifts the move list).
     */
    fun cancelThinking()

    /**
     * Current position history from the AI engine.
     * Used by callers that need to evaluate game-over conditions
     * (triple repetition) without accessing the engine singleton directly.
     */
    val positionHistory: Map<String, Int>
}