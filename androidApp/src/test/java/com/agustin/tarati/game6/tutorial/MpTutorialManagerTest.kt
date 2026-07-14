package com.agustin.tarati.game6.tutorial

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.tutorial.MpInteractiveTutorialStep
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialManager
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialState
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialStepId
import com.agustin.tarati.core.domain.game6.tutorial.mpTutorialSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del orquestador del tutorial multijugador (game6): orden y contenido de los pasos, avance /
 * retroceso, validación de los movimientos esperados de los pasos interactivos y cierre.
 */
class MpTutorialManagerTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    // ---- Contenido del recorrido ----

    @Test
    fun steps_areTheSevenDifferencesInOrder() {
        val ids = mpTutorialSteps().map { it.id }
        assertEquals(
            listOf(
                MpTutorialStepId.INTRO,
                MpTutorialStepId.BASE_EXIT,
                MpTutorialStepId.ANY_DIRECTION,
                MpTutorialStepId.SINGLE_PIECE,
                MpTutorialStepId.CONVERSION,
                MpTutorialStepId.WITHDRAWAL,
                MpTutorialStepId.END,
            ),
            ids,
        )
    }

    @Test
    fun interactiveSteps_areBaseExitAndConversion() {
        val interactive = mpTutorialSteps().filterIsInstance<MpInteractiveTutorialStep>().map { it.id }
        assertEquals(listOf(MpTutorialStepId.BASE_EXIT, MpTutorialStepId.CONVERSION), interactive)
    }

    // ---- Carga y navegación ----

    @Test
    fun load_showsFirstStep() {
        val manager = MpTutorialManager()
        manager.load()
        val state = manager.state.value
        assertTrue(state is MpTutorialState.ShowingStep)
        assertEquals(MpTutorialStepId.INTRO, (state as MpTutorialState.ShowingStep).step.id)
        assertEquals(1, manager.progress.currentStepIndex)
        assertEquals(7, manager.progress.totalSteps)
    }

    @Test
    fun next_reachesInteractiveBaseExitAsWaitingForMove() {
        val manager = MpTutorialManager()
        manager.load()
        manager.next() // BASE_EXIT
        val state = manager.state.value
        assertTrue("BASE_EXIT es interactivo", state is MpTutorialState.WaitingForMove)
        assertEquals(MpTutorialStepId.BASE_EXIT, (state as MpTutorialState.WaitingForMove).step.id)
        assertTrue(manager.isWaitingForMove())
    }

    @Test
    fun previous_isBoundedAtFirstStep() {
        val manager = MpTutorialManager()
        manager.load()
        manager.previous()
        assertEquals(1, manager.progress.currentStepIndex)
        assertEquals(MpTutorialStepId.INTRO, manager.currentStep()?.id)
    }

    @Test
    fun next_pastLastStep_completesTutorial() {
        val manager = MpTutorialManager()
        manager.load()
        repeat(6) { manager.next() } // hasta END (paso 7)
        assertEquals(MpTutorialStepId.END, manager.currentStep()?.id)
        manager.next() // más allá del último
        assertEquals(MpTutorialState.Completed, manager.state.value)
    }

    // ---- Validación de movimientos esperados ----

    @Test
    fun onUserMove_acceptsExpectedBaseExit_rejectsOthers() {
        val manager = MpTutorialManager()
        manager.load()
        manager.next() // BASE_EXIT
        assertTrue("D1→C1 es la salida esperada", manager.onUserMove(MpMove(v("D1"), v("C1"))))
        assertFalse("D2→C2 no es el movimiento guiado", manager.onUserMove(MpMove(v("D2"), v("C2"))))
        assertFalse("D1→D18 (anillo) rechazado", manager.onUserMove(MpMove(v("D1"), v("D18"))))
    }

    @Test
    fun onUserMove_acceptsBothExpectedConversions() {
        val manager = MpTutorialManager()
        manager.load()
        repeat(4) { manager.next() } // CONVERSION (paso 5)
        assertEquals(MpTutorialStepId.CONVERSION, manager.currentStep()?.id)
        // C2 puede capturar B2 desde B1 o desde C3: ambos son válidos.
        assertTrue("C2→B1 es una captura esperada", manager.onUserMove(MpMove(v("C2"), v("B1"))))
        assertTrue("C2→C3 es una captura esperada", manager.onUserMove(MpMove(v("C2"), v("C3"))))
        assertFalse("C2→D2 (no captura) no es el movimiento guiado", manager.onUserMove(MpMove(v("C2"), v("D2"))))
    }

    /**
     * Las capturas legales del paso de conversión deben ser **exactamente** las dos guiadas
     * (`C2→B1` y `C2→C3`, ambas voltean `B2`): así lo resaltado/aceptado coincide con lo jugable y no
     * queda una captura sin reconocer. `C2` es la única pieza `P1`, de modo que no hay capturas de
     * otras piezas.
     */
    @Test
    fun conversionStep_capturingMovesMatchTheExpectedOnes() {
        val step = mpTutorialSteps().first { it.id == MpTutorialStepId.CONVERSION } as MpInteractiveTutorialStep
        val capturing = MpRules.legalMoves(step.state)
            .filter { MpRules.captureTargets(step.state.pieces, it).isNotEmpty() }
            .toSet()
        assertEquals(setOf(MpMove(v("C2"), v("B1")), MpMove(v("C2"), v("C3"))), capturing)
        assertEquals(step.expectedMoves.toSet(), capturing)
    }

    @Test
    fun onUserMove_onGuidedStep_returnsFalse() {
        val manager = MpTutorialManager()
        manager.load() // INTRO (guiado)
        assertFalse(manager.onUserMove(MpMove(v("D1"), v("C1"))))
        assertFalse(manager.isWaitingForMove())
    }

    // ---- Cierre ----

    @Test
    fun close_returnsToIdle_reset_clearsSteps() {
        val manager = MpTutorialManager()
        manager.load()
        manager.close()
        assertEquals(MpTutorialState.Idle, manager.state.value)

        manager.reset()
        assertEquals(MpTutorialState.Idle, manager.state.value)
        assertNull(manager.currentStep())
        assertEquals(0, manager.progress.totalSteps)
    }
}
