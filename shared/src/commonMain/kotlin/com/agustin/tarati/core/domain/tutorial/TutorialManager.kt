package com.agustin.tarati.core.domain.tutorial

import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.ui.components.game.animation.AnimationCoordinator
import com.agustin.tarati.ui.components.game.animation.AnimationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class TutorialManager(
    private val animationCoordinator: AnimationCoordinator,
) {
    // SupervisorJob: el fallo de un paso no cancela el scope completo; los
    // pasos diferidos en vuelo se cancelan explícitamente en [closeTutorial].
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoAdvanceJob: Job? = null

    private val _tutorialState = MutableStateFlow<TutorialState>(TutorialState.Idle)
    val tutorialState: StateFlow<TutorialState> = _tutorialState.asStateFlow()

    private fun updateTutorialState(state: TutorialState) {
        _tutorialState.update { state }
    }

    // Navegación por la lista de pasos (índice + topes + progreso 1-based) delegada en el
    // StepCursor compartido con el tutorial multijugador.
    private val cursor = StepCursor<TutorialStep>()

    val progress: TutorialProgress get() = cursor.progress

    fun loadRulesTutorial(onStep: () -> Unit) {
        cursor.load(
            listOf(
                IntroductionStep(),
                CenterStep(),
                BridgeStep(),
                CircumferenceStep(),
                DomesticBasesStep(),
                CobsStep(),
                BasicMovesStep(),
                CapturesStep(),
                PreAdjacencyStep(),
                UpgradeStep(),
                DeadPieceStep(),
                DomesticCaptureStep(),
                EndConditionsStep(),
                CompletedStep(),
            ),
        )
        startTutorial(onStep)
    }

    private fun startTutorial(onStep: () -> Unit) {
        val step = cursor.current ?: return
        showStep(step, onStep)
    }

    fun nextStep(onStep: () -> Unit) {
        coroutineScope.launch {
            // Pequeña pausa para asegurar que se limpie el tablero
            delay(300L.milliseconds)

            stopCurrentAnimations()
            autoAdvanceJob?.cancel()

            if (cursor.advance()) {
                cursor.current?.let { showStep(it, onStep) }
            } else {
                endTutorial()
            }
        }
    }

    fun previousStep(onStep: () -> Unit) {
        coroutineScope.launch {
            // Pequeña pausa para asegurar que se limpie el tablero
            delay(300L.milliseconds)

            stopCurrentAnimations()
            autoAdvanceJob?.cancel()

            if (cursor.back()) {
                cursor.current?.let { showStep(it, onStep) }
            }
        }
    }

    fun repeatCurrentStep(onStep: () -> Unit) {
        stopCurrentAnimations()
        autoAdvanceJob?.cancel()

        // Limpiar la cola completamente antes de repetir
        animationCoordinator.handleEvent(AnimationEvent.ClearQueue)

        // Pequeño delay para asegurar que la cola se limpió
        coroutineScope.launch {
            delay(50.milliseconds)
            cursor.current?.let { showStep(it, onStep) }
        }
    }

    fun onUserMove(move: Move): Boolean =
        when (val currentState = _tutorialState.value) {
            is TutorialState.WaitingForMove -> {
                val step = currentState.step as? InteractiveTutorialStep
                step != null && step.isExpectedMove(move)
            }

            else -> false
        }

    fun getExpectedMoves(): List<Move> {
        val currentState = _tutorialState.value as? TutorialState.WaitingForMove ?: return listOf()
        val step = currentState.step as? InteractiveTutorialStep ?: return listOf()
        return step.expectedMoves
    }

    fun requestUserInteraction(expectedMove: List<Move> = listOf()) {
        val currentStep = getCurrentStep()
        if (currentStep != null) {
            updateTutorialState(TutorialState.WaitingForMove(currentStep, expectedMove))
        }
    }

    private fun getCurrentStep(): TutorialStep? = cursor.current

    fun getCurrentGameState(): GameState? = getCurrentStep()?.gameState

    private fun shouldAutoAdvance(): Boolean {
        val currentStep = getCurrentStep()
        return currentStep?.autoAdvanceDelay != null &&
                currentStep !is InteractiveTutorialStep
    }

    private fun getCurrentStepDuration(): Long = getCurrentStep()?.autoAdvanceDelay ?: 0L

    fun isWaitingForUserInteraction(): Boolean = _tutorialState.value is TutorialState.WaitingForMove

    private fun showStep(
        step: TutorialStep,
        onStep: () -> Unit,
    ) {
        // Actualizar estado del juego primero
        step.onStepStart?.invoke()

        // Determinar el estado basado en el tipo de paso
        updateTutorialState(
            when (step) {
                is InteractiveTutorialStep -> TutorialState.WaitingForMove(step, step.expectedMoves)
                else -> TutorialState.ShowingStep(step)
            },
        )

        // Iniciar animaciones del paso con un delay que permite a Compose propagar el
        // cambio de tutorialState → LaunchedEffect → updateGameState → syncState antes
        // de que loadTutorialStep (CANCEL_CURRENT) limpie el visualState. Sin este delay,
        // el tablero queda con las piezas del paso anterior porque syncState llega tarde.
        coroutineScope.launch {
            delay(100L.milliseconds)
            startStepAnimations(step, onStep)

            // Configurar auto-avance si es necesario
            if (shouldAutoAdvance()) {
                startAutoAdvance(onStep)
            }
        }
    }

    private fun startStepAnimations(
        step: TutorialStep,
        onStep: () -> Unit,
    ) {
        if (step.animations.isEmpty()) return

        onStep()

        animationCoordinator.handleEvent(
            AnimationEvent.TutorialHighlightEvent(
                highlights = step.animations,
                source = step::class.simpleName.orEmpty(),
            ),
        )
    }

    private fun stopCurrentAnimations() {
        animationCoordinator.handleEvent(AnimationEvent.StopHighlights)
    }

    private fun startAutoAdvance(onStep: () -> Unit) {
        val delayTime = getCurrentStepDuration()
        autoAdvanceJob =
            coroutineScope.launch {
                delay(delayTime.milliseconds)
                nextStep(onStep)
            }
    }

    fun endTutorial() {
        stopCurrentAnimations()
        autoAdvanceJob?.cancel()
        updateTutorialState(TutorialState.Completed)
    }

    fun closeTutorial() {
        stopCurrentAnimations()
        autoAdvanceJob?.cancel()
        // Cancela los pasos diferidos en vuelo (delays de nextStep/showStep)
        // para que no re-aparezca un paso después de cerrar.
        coroutineScope.coroutineContext.cancelChildren()
        updateTutorialState(TutorialState.Idle)
    }

    fun reset() {
        closeTutorial()
        cursor.reset()
    }
}
