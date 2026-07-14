package com.agustin.tarati.features.game6

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.tutorial.MpInteractiveTutorialStep
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialManager
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialProgress
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialState
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialStep
import com.agustin.tarati.core.domain.game6.tutorial.isCompleted
import com.agustin.tarati.services.sound.ISoundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementación del tutorial multijugador. Envuelve el [MpTutorialManager] (navegación + validación
 * de movimientos) y le agrega el estado visual del tablero (posición + cues) y el sonido.
 *
 * No se anota `@Stable`: [progress] es un getter no observable (lee el manager) que cambia durante
 * la vida del objeto — anotarlo rompería la promesa de estabilidad (ver convención en `docs/CLAUDE.md`).
 */
class MpTutorialViewModel(
    private val soundService: ISoundService,
) : ViewModel(),
    IMpTutorialViewModel {

    private val manager = MpTutorialManager()

    override val tutorialState: StateFlow<MpTutorialState> = manager.state
    override val progress: MpTutorialProgress get() = manager.progress

    private val _displayState = MutableStateFlow<MpGameState?>(null)
    override val displayState: StateFlow<MpGameState?> = _displayState.asStateFlow()

    private val _selection = MutableStateFlow<Vertex?>(null)
    override val selection: StateFlow<Vertex?> = _selection.asStateFlow()

    private val _legalTargets = MutableStateFlow<Set<Vertex>>(emptySet())
    override val legalTargets: StateFlow<Set<Vertex>> = _legalTargets.asStateFlow()

    private val _converted = MutableStateFlow<Map<Vertex, PlayerColor>>(emptyMap())
    override val converted: StateFlow<Map<Vertex, PlayerColor>> = _converted.asStateFlow()

    private val _lastMove = MutableStateFlow<MpMove?>(null)
    override val lastMove: StateFlow<MpMove?> = _lastMove.asStateFlow()

    /** El paso interactivo actual ya fue resuelto (se ignoran taps posteriores hasta navegar). */
    private var solved = false

    /**
     * Auto-avance (paridad con single): temporiza el salto de los pasos guiados y el avance tras un
     * movimiento interactivo correcto. Se cancela en cada navegación manual o cambio de paso.
     */
    private var autoAdvanceJob: Job? = null

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    init {
        // El tablero sigue las transiciones de paso (avanzar/retroceder emiten un estado distinto).
        // El caso "repetir" emite un estado igual → no dispara el collector: se maneja aparte en
        // [repeatCurrent] llamando a [bindStep] directamente.
        viewModelScope.launch {
            manager.state.collect { state ->
                when (state) {
                    is MpTutorialState.ShowingStep -> bindStep(state.step)
                    is MpTutorialState.WaitingForMove -> bindStep(state.step)
                    MpTutorialState.Idle, MpTutorialState.Completed -> clearBoard()
                }
            }
        }
    }

    override fun start() {
        manager.load()
    }

    override fun onVertexTap(vertex: Vertex) {
        val step = (manager.state.value as? MpTutorialState.WaitingForMove)?.step ?: return
        if (solved) return

        val display = _displayState.value ?: step.state
        val currentColor = display.currentSeat.color
        val piece = display.pieces[vertex]

        // Seleccionar una pieza propia → resaltar sus destinos (los curados del paso si es la pieza
        // sugerida; los legales reales si el usuario elige otra pieza propia).
        if (piece != null && piece.owner == currentColor) {
            _selection.value = vertex
            _legalTargets.value =
                if (vertex == step.selection) {
                    step.legalTargets
                } else {
                    MpRules.legalMoves(display).filter { it.from == vertex }.map { it.to }.toSet()
                }
            return
        }

        val from = _selection.value ?: return
        val move = MpMove(from, vertex)
        if (manager.onUserMove(move)) {
            applyAccepted(step, move)
        } else {
            soundService.playIllegalMoveSound()
        }
    }

    override fun next() {
        cancelAutoAdvance()
        manager.next()
    }

    override fun previous() {
        cancelAutoAdvance()
        manager.previous()
    }

    override fun repeatCurrent() {
        manager.repeatCurrent()
        // "Repetir" emite un estado igual → el collector no reacciona; reponemos el paso a mano
        // ([bindStep] cancela y reprograma el auto-avance del paso).
        manager.currentStep()?.let { bindStep(it) }
    }

    override fun skipInteractive(): Boolean {
        val step = (manager.state.value as? MpTutorialState.WaitingForMove)?.step ?: return false
        if (solved) return false
        val move = step.expectedMoves.firstOrNull() ?: return false
        applyAccepted(step, move)
        return true
    }

    override fun close() {
        cancelAutoAdvance()
        manager.close()
    }

    override fun reset() {
        cancelAutoAdvance()
        manager.reset()
    }

    override fun isCompleted(): Boolean = manager.progress.isCompleted()

    // ── Internos ──────────────────────────────────────────────────────────────

    /**
     * Aplica un movimiento aceptado sobre la posición del paso, refleja el resultado + flip y
     * **auto-avanza** tras un breve retardo (paridad con single: mueve, deja ver el resultado y salta
     * al paso siguiente). El retardo se cancela si el usuario navega antes.
     */
    private fun applyAccepted(step: MpInteractiveTutorialStep, move: MpMove) {
        cancelAutoAdvance()
        val before = step.state
        val conversions =
            MpRules.captureTargets(before.pieces, move).associateWith { before.pieces.getValue(it).owner }
        val after = MpRules.applyMove(before, move)
        _converted.value = conversions
        _lastMove.value = move
        _displayState.value = after
        _selection.value = null
        _legalTargets.value = emptySet()
        solved = true
        if (conversions.isNotEmpty()) soundService.playCaptureSound() else soundService.playMoveSound()
        autoAdvanceJob = viewModelScope.launch {
            delay(INTERACTIVE_ADVANCE_DELAY_MS.milliseconds)
            manager.next()
        }
    }

    /**
     * Fija la posición y los cues del [step], reproduce el sonido de paso y, si es un paso **guiado**
     * con [MpTutorialStep.autoAdvanceDelay], programa su auto-avance (paridad con single).
     */
    private fun bindStep(step: MpTutorialStep) {
        cancelAutoAdvance()
        _displayState.value = step.state
        _selection.value = step.selection
        _legalTargets.value = step.legalTargets
        _converted.value = emptyMap()
        _lastMove.value = null
        solved = false
        soundService.playTutorialStepSound()

        val delayMs = step.autoAdvanceDelay
        if (step !is MpInteractiveTutorialStep && delayMs != null) {
            autoAdvanceJob = viewModelScope.launch {
                delay(delayMs.milliseconds)
                manager.next()
            }
        }
    }

    private fun clearBoard() {
        _displayState.value = null
        _selection.value = null
        _legalTargets.value = emptySet()
        _converted.value = emptyMap()
        _lastMove.value = null
        solved = false
    }

    private companion object {
        /** Retardo antes de auto-avanzar tras un movimiento interactivo correcto (deja ver el flip). */
        const val INTERACTIVE_ADVANCE_DELAY_MS = 1200L
    }
}
