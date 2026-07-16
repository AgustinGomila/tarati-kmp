package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialState
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialStep
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialStepId
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.perform_the_indicated_move
import com.agustin.tarati.ui.components.tutorial.BubbleConfig
import com.agustin.tarati.ui.components.tutorial.BubblePosition
import com.agustin.tarati.ui.components.tutorial.TutorialBubble
import com.agustin.tarati.ui.components.tutorial.TutorialBubbleContentState
import com.agustin.tarati.ui.components.tutorial.TutorialBubbleEvents
import com.agustin.tarati.ui.components.tutorial.TutorialBubbleState

/**
 * Overlay del tutorial multijugador: dibuja la burbuja de pasos (reusando el shell `TutorialBubble`
 * del tutorial single) sobre el `Board25Pane`. El tablero de fondo lo alimenta `MpGameScreen` desde
 * el [viewModel] mientras el recorrido está activo.
 *
 * @param onClose el usuario saltó/cerró el recorrido (X de la burbuja).
 * @param onFinish el usuario completó el recorrido (último paso).
 */
@Composable
fun MpTutorialOverlay(
    viewModel: IMpTutorialViewModel,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.tutorialState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is MpTutorialState.ShowingStep -> Bubble(viewModel, s.step, isWaitingForMove = false, onClose, onFinish)
            is MpTutorialState.WaitingForMove -> Bubble(viewModel, s.step, isWaitingForMove = true, onClose, onFinish)
            MpTutorialState.Idle -> Unit
            MpTutorialState.Completed -> onFinish()
        }
    }
}

@Composable
private fun Bubble(
    viewModel: IMpTutorialViewModel,
    step: MpTutorialStep,
    isWaitingForMove: Boolean,
    onClose: () -> Unit,
    onFinish: () -> Unit,
) {
    val progress = viewModel.progress
    val description =
        if (isWaitingForMove) {
            localizedString(Res.string.perform_the_indicated_move, localizedString(step.descriptionRes))
        } else {
            localizedString(step.descriptionRes)
        }

    TutorialBubble(
        title = localizedString(step.titleRes),
        bubbleState = TutorialBubbleState(
            contentState = TutorialBubbleContentState(
                description = description,
                canGoBack = progress.currentStepIndex > 1,
                canGoForward = true,
                currentStep = progress.currentStepIndex,
                totalSteps = progress.totalSteps,
            ),
            config = BubbleConfig(position = bubblePositionFor(step.id)),
        ),
        bubbleEvents = object : TutorialBubbleEvents {
            override fun onNext() {
                // Paso interactivo sin resolver: aplica el movimiento esperado y el auto-avance lo
                // muestra antes de pasar al siguiente paso (sin avanzar acá).
                if (isWaitingForMove && viewModel.skipInteractive()) return
                viewModel.next()
                if (viewModel.isCompleted()) onFinish()
            }

            override fun onPrevious() = viewModel.previous()

            override fun onSkip() {
                viewModel.close()
                onClose()
            }

            override fun onRepeat() = viewModel.repeatCurrent()
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Posición fija de la burbuja por paso, para no tapar la zona de acción del tablero. */
private fun bubblePositionFor(id: MpTutorialStepId): BubblePosition =
    when (id) {
        MpTutorialStepId.END -> BubblePosition.CENTER_CENTER
        else -> BubblePosition.TOP_CENTER
    }

/**
 * Área de tablero mientras el tutorial está activo: dibuja la posición scripteada del paso (con sus
 * cues) alimentada desde el [viewModel] y la burbuja de pasos encima. Reemplaza al tablero de la
 * partida local en `MpGameScreen`, que queda intacta detrás y reaparece al cerrar el recorrido.
 */
@Composable
internal fun MpTutorialBoard(
    viewModel: IMpTutorialViewModel,
    boardVisual: BoardVisualState,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display by viewModel.displayState.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val legalTargets by viewModel.legalTargets.collectAsState()
    val converted by viewModel.converted.collectAsState()
    val lastMove by viewModel.lastMove.collectAsState()
    val tutorialState by viewModel.tutorialState.collectAsState()

    // Vértices que el paso resalta (la pieza y sus destinos): se etiquetan aunque las etiquetas estén
    // apagadas en Settings, para que el usuario ubique el vértice del que habla el paso.
    val forcedLabels = remember(selection, legalTargets) { legalTargets + listOfNotNull(selection) }

    // Flechas guía del paso interactivo: los movimientos permitidos, con la misma flecha parpadeante
    // que el tutorial single. Solo mientras la pieza sugerida siga seleccionada — si el usuario
    // explora otra pieza propia (o ya resolvió el paso), las flechas se ocultan junto con los cues.
    val guideArrows = remember(tutorialState, selection) {
        val step = (tutorialState as? MpTutorialState.WaitingForMove)?.step
        if (step != null && selection == step.selection) step.expectedMoves else emptyList<MpMove>()
    }

    Box(modifier = modifier.fillMaxSize()) {
        display?.let { state ->
            Board25Pane(
                state = state,
                // En el tutorial todos los asientos se muestran como humanos (sin chips de IA).
                seatIsAI = List(state.seats.size) { false },
                selection = selection,
                legalTargets = legalTargets,
                threatened = emptySet(),
                lastMove = lastMove,
                converted = converted,
                boardVisual = boardVisual,
                onVertexTap = viewModel::onVertexTap,
                forcedLabelVertices = forcedLabels,
                guideArrows = guideArrows,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        MpTutorialOverlay(viewModel = viewModel, onClose = onClose, onFinish = onFinish)
    }
}
