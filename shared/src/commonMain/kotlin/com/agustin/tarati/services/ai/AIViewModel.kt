package com.agustin.tarati.services.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.runner.AiMoveRunner
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.LoggingFactory
import kotlinx.coroutines.Job
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

    /** Cómputo de IA en curso; se cancela al iniciar una partida nueva ([cancelThinking]). */
    private var currentJob: Job? = null

    /**
     * Generación del cómputo vigente. Cada [requestAIMove] y cada [cancelThinking] la incrementan;
     * el `finally` de un job solo baja [isAIThinking] si sigue siendo el vigente, así el job
     * cancelado (que termina tarde) no pisa el estado de un cómputo posterior ya arrancado.
     */
    private var thinkingGeneration = 0

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

        val myGeneration = ++thinkingGeneration
        currentJob = viewModelScope.launch {
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
                // Solo el cómputo vigente baja la bandera. Un job cancelado (p. ej. por
                // cancelThinking al iniciar partida nueva) termina tarde: si reseteara aquí,
                // pisaría el "pensando" de un cómputo posterior ya en curso.
                if (myGeneration == thinkingGeneration) {
                    _isAIThinking.update { false }
                }
            }
        }
    }

    /**
     * Cancela el cómputo de IA en vuelo (si lo hay) y baja [isAIThinking].
     *
     * El cómputo corre en [viewModelScope], que sobrevive al reset del tablero. Al iniciar una
     * partida nueva hay que cancelarlo: su jugada —calculada para la posición anterior— volvería
     * tarde y se aplicaría sobre el tablero nuevo (lo corrompe: sobrescribe una pieza y corre la
     * notación). El bump de generación invalida el `finally` del job cancelado.
     */
    override fun cancelThinking() {
        thinkingGeneration++
        currentJob?.cancel()
        currentJob = null
        _isAIThinking.update { false }
    }
}
