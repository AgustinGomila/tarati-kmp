package com.agustin.tarati.services.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.runner.AiMoveRunner
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.LoggingFactory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class AIViewModel(
    private val aiEngine: IAIEngine,
    private val moveRunner: AiMoveRunner,
) : ViewModel(),
    IAIService {

    private val logger = LoggingFactory.getLogger("aiViewModel")

    private val _isAIThinking = MutableStateFlow(false)
    override val isAIThinking: StateFlow<Boolean> = _isAIThinking.asStateFlow()

    // extraBufferCapacity = 1: retains a move emitted during a configuration
    // change until the new composition starts collecting.
    private val _pendingAIMove = MutableSharedFlow<Move>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pendingAIMove: SharedFlow<Move> = _pendingAIMove.asSharedFlow()

    override val positionHistory: Map<String, Int>
        get() = aiEngine.positionHistory

    /**
     * Lanza el cómputo de la IA en [androidx.lifecycle.viewModelScope], que sobrevive rotaciones
     * de pantalla. El resultado se emite a [pendingAIMove] y es recogido por el Composable via
     * [androidx.compose.runtime.LaunchedEffect].
     *
     * El cómputo se delega a [AiMoveRunner]: en Desktop/Android corre en un hilo real; en Web, en un
     * Web Worker (fuera del hilo principal del navegador). Se le pasa un snapshot del
     * [positionHistory] del motor —autoritativo, construido en el flujo de juego— para que el runner
     * de worker, que trabaja sobre una instancia aislada, detecte la triple repetición igual que el
     * motor local.
     *
     * Ignorado si el motor ya está pensando, evitando cómputos duplicados cuando [GameEffects]
     * re-dispara tras la rotación con las mismas dependencias.
     */
    override fun requestAIMove(gameState: GameState, difficulty: Difficulty) {
        if (_isAIThinking.value) return

        // Snapshot en el hilo llamante (el mismo que muta el historial vía putState): seguro de leer.
        val historySnapshot = aiEngine.positionHistory.toMap()

        viewModelScope.launch {
            _isAIThinking.update { true }
            logger.debug("AI starting to think...")

            try {
                val result = moveRunner.bestMove(
                    gameState = gameState,
                    difficulty = difficulty,
                    positionHistory = historySnapshot,
                )

                logger.debug("AI calculated move: ${result.move}")

                // isActive verifica cancelación al retornar del runner antes de emitir.
                if (isActive) {
                    result.move?.let { _pendingAIMove.emit(it) }
                }
            } catch (e: CancellationException) {
                throw e // preservar structured concurrency
            } catch (t: Throwable) {
                logger.error(t.message.orEmpty(), t)
            } finally {
                _isAIThinking.update { false }
            }
        }
    }
}
