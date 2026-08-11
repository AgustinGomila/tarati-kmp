package com.agustin.tarati.tutorial

import com.agustin.tarati.core.domain.tutorial.StepCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del cursor de pasos compartido por los tutoriales single y multijugador: navegación acotada
 * (topes en el primer/último paso), progreso 1-based y reinicio.
 */
class StepCursorTest {

    @Test
    fun empty_hasNoCurrentAndZeroTotal() {
        val cursor = StepCursor<String>()
        assertNull(cursor.current)
        assertEquals(0, cursor.progress.totalSteps)
        assertTrue(cursor.atFirst)
        assertTrue(cursor.atLast)
        // Sin pasos, no hay a dónde moverse.
        assertFalse(cursor.advance())
        assertFalse(cursor.back())
    }

    @Test
    fun load_positionsAtFirstStep() {
        val cursor = StepCursor<String>()
        cursor.load(listOf("a", "b", "c"))
        assertEquals("a", cursor.current)
        assertTrue(cursor.atFirst)
        assertFalse(cursor.atLast)
        assertEquals(1, cursor.progress.currentStepIndex)
        assertEquals(3, cursor.progress.totalSteps)
    }

    @Test
    fun advance_movesForwardAndStopsAtLast() {
        val cursor = StepCursor<String>()
        cursor.load(listOf("a", "b"))

        assertTrue(cursor.advance())
        assertEquals("b", cursor.current)
        assertTrue(cursor.atLast)
        assertEquals(2, cursor.progress.currentStepIndex)

        // En el último: no avanza y no se mueve.
        assertFalse(cursor.advance())
        assertEquals("b", cursor.current)
        assertEquals(2, cursor.progress.currentStepIndex)
    }

    @Test
    fun back_movesBackwardAndStopsAtFirst() {
        val cursor = StepCursor<String>()
        cursor.load(listOf("a", "b"))
        cursor.advance()

        assertTrue(cursor.back())
        assertEquals("a", cursor.current)
        assertTrue(cursor.atFirst)

        // En el primero: no retrocede y no se mueve.
        assertFalse(cursor.back())
        assertEquals("a", cursor.current)
        assertEquals(1, cursor.progress.currentStepIndex)
    }

    @Test
    fun reset_clearsStepsAndIndex() {
        val cursor = StepCursor<String>()
        cursor.load(listOf("a", "b", "c"))
        cursor.advance()

        cursor.reset()

        assertNull(cursor.current)
        assertEquals(0, cursor.progress.totalSteps)
        assertEquals(1, cursor.progress.currentStepIndex)
    }
}
