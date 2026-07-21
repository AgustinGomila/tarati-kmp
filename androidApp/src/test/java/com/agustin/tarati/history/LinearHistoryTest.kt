package com.agustin.tarati.history

import com.agustin.tarati.core.domain.history.LinearHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del historial lineal navegable compartido por single (`GameManager`) y multi
 * (`MpLocalGameViewModel`): navegación acotada, truncado de rama, replay/restore y transform.
 *
 * Modelo de prueba: el estado [S] es un `Int` = cantidad de jugadas aplicadas; cada jugada [M] es un
 * `String`, y el "apply" incrementa el contador. Así los snapshots son verificables sin dominio real.
 */
class LinearHistoryTest {

    private fun newHistory() = LinearHistory<Int, String>(initial = 0)

    @Test
    fun empty_isAtInitialPosition() {
        val h = newHistory()
        assertEquals(-1, h.cursor)
        assertEquals(0, h.size)
        assertEquals(-1, h.tip)
        assertTrue(h.isAtTip)
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals(0, h.currentState())
    }

    @Test
    fun append_advancesCursorAndStoresSnapshot() {
        val h = newHistory()
        h.append("a", 1)
        h.append("b", 2)

        assertEquals(2, h.size)
        assertEquals(1, h.cursor)
        assertTrue(h.isAtTip)
        assertEquals(2, h.currentState())
        assertEquals(listOf("a", "b"), h.movesList)
        assertEquals(0, h.stateAt(-1))
        assertEquals(1, h.stateAt(0))
        assertEquals(2, h.stateAt(1))
    }

    @Test
    fun undoRedo_moveCursorWithoutLosingLine() {
        val h = newHistory()
        h.append("a", 1)
        h.append("b", 2)

        assertTrue(h.undo())
        assertEquals(0, h.cursor)
        assertEquals(1, h.currentState())
        assertEquals(2, h.size) // la línea NO se pierde al navegar

        assertTrue(h.undo())
        assertEquals(-1, h.cursor)
        assertEquals(0, h.currentState())
        assertFalse(h.undo()) // tope inicial

        assertTrue(h.redo())
        assertEquals(0, h.cursor)
        h.moveToTip()
        assertEquals(1, h.cursor)
        assertFalse(h.redo()) // tope en el tip
    }

    @Test
    fun append_afterUndo_truncatesForwardLine() {
        val h = newHistory()
        h.append("a", 1)
        h.append("b", 2)
        h.append("c", 3)

        h.undo() // cursor en la jugada 1 (tras "b")
        assertEquals(1, h.cursor)

        h.append("x", 99) // descarta "c" y continúa desde aquí

        assertEquals(3, h.size)
        assertEquals(listOf("a", "b", "x"), h.movesList)
        assertEquals(2, h.cursor)
        assertEquals(99, h.currentState())
    }

    @Test
    fun moveTo_respectsBounds() {
        val h = newHistory()
        h.append("a", 1)
        h.append("b", 2)

        assertTrue(h.moveTo(-1))
        assertEquals(-1, h.cursor)
        assertTrue(h.moveTo(1))
        assertEquals(1, h.cursor)

        assertFalse(h.moveTo(2)) // fuera de rango, no se mueve
        assertEquals(1, h.cursor)
        assertFalse(h.moveTo(-2))
        assertEquals(1, h.cursor)
    }

    @Test
    fun replay_rebuildsSnapshotsFromBaseAndLeavesCursorAtTip() {
        val h = newHistory()
        // apply: el estado es "cantidad de jugadas" partiendo de base 10.
        h.replay(base = 10, newMoves = listOf("a", "b", "c")) { prev, _ -> prev + 1 }

        assertEquals(3, h.size)
        assertEquals(2, h.cursor)
        assertEquals(10, h.initialState)
        assertEquals(11, h.stateAt(0))
        assertEquals(13, h.stateAt(2))
        assertEquals(13, h.currentState())
    }

    @Test
    fun restore_rehydratesEntriesAndCursorWithoutTouchingInitial() {
        val h = LinearHistory<Int, String>(initial = 5)
        h.restore(entries = listOf("a" to 6, "b" to 7), at = 0)

        assertEquals(5, h.initialState) // restore no toca la base
        assertEquals(2, h.size)
        assertEquals(0, h.cursor)
        assertEquals(6, h.currentState())
        // cursor fuera de rango se acota.
        h.restore(entries = listOf("a" to 6), at = 99)
        assertEquals(0, h.cursor)
    }

    @Test
    fun reset_clearsLineAndSetsNewBase() {
        val h = newHistory()
        h.append("a", 1)
        h.reset(base = 42)

        assertEquals(42, h.initialState)
        assertEquals(0, h.size)
        assertEquals(-1, h.cursor)
        assertEquals(42, h.currentState())
    }

    @Test
    fun rebase_setsOnlyInitialState() {
        val h = newHistory()
        h.rebase(base = 7)
        assertEquals(7, h.initialState)
        assertEquals(0, h.size)
        assertEquals(-1, h.cursor)
    }

    @Test
    fun transform_mapsInitialSnapshotsAndMovesPreservingCursor() {
        val h = newHistory()
        h.append("a", 1)
        h.append("b", 2)
        h.undo() // cursor en 0

        h.transform(mapState = { it * 100 }, mapMove = { it.uppercase() })

        assertEquals(0, h.cursor) // cursor preservado
        assertEquals(0, h.initialState) // 0 * 100
        assertEquals(100, h.stateAt(0))
        assertEquals(200, h.stateAt(1))
        assertEquals(listOf("A", "B"), h.movesList)
    }

    @Test
    fun moveAt_returnsMoveOrNull() {
        val h = newHistory()
        h.append("a", 1)
        assertEquals("a", h.moveAt(0))
        assertNull(h.moveAt(1))
        assertNull(h.moveAt(-1))
    }
}
