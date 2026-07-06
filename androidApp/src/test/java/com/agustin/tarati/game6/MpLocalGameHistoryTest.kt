package com.agustin.tarati.game6

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.features.game6.MpLocalGameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests del **historial navegable** (undo/redo, D11) del [MpLocalGameViewModel]. Se juega con los dos
 * asientos como humanos para conducir jugadas deterministas (sin la aleatoriedad del bot).
 */
class MpLocalGameHistoryTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    private lateinit var vm: MpLocalGameViewModel

    @Before
    fun setup() {
        vm = MpLocalGameViewModel()
        // Ambos asientos humanos → onVertexTap conduce cualquier turno sin que jueguen bots.
        vm.setSeatIsAI(1, false)
    }

    /** Aplica la jugada [from]→[to] vía dos taps (seleccionar + destino). */
    private fun play(from: String, to: String) {
        vm.onVertexTap(v(from))
        vm.onVertexTap(v(to))
    }

    @Test
    fun initialState_isAtInitialPosition() {
        assertEquals(-1, vm.moveIndex.value)
        assertTrue(vm.history.value.isEmpty())
        assertTrue(vm.isAtTip())
    }

    @Test
    fun applyingMoves_advancesMoveIndexAndHistory() {
        play("D1", "C1") // P1
        play("D10", "C7") // P2
        assertEquals(2, vm.history.value.size)
        assertEquals(1, vm.moveIndex.value)
        assertTrue(vm.isAtTip())
    }

    @Test
    fun undoRedo_navigatesSnapshotsWithoutLosingHistory() {
        play("D1", "C1") // P1 → estado tras jugada 1 (turno P2)
        play("D10", "C7") // P2 → estado tras jugada 2 (turno P1)

        vm.undo()
        assertEquals(0, vm.moveIndex.value)
        assertEquals(1, vm.state.value.currentSeatIndex) // turno de P2

        vm.undo()
        assertEquals(-1, vm.moveIndex.value)
        assertEquals(0, vm.state.value.currentSeatIndex) // turno de P1
        assertTrue("La pieza vuelve a D1 en la posición inicial", vm.state.value.pieces.containsKey(v("D1")))

        // El historial NO se pierde al navegar hacia atrás.
        assertEquals(2, vm.history.value.size)

        vm.redo()
        assertEquals(0, vm.moveIndex.value)

        vm.moveToCurrent()
        assertEquals(1, vm.moveIndex.value)
        assertTrue(vm.isAtTip())
    }

    @Test
    fun applyingMoveAfterUndo_truncatesForwardLine() {
        play("D1", "C1") // P1
        play("D10", "C7") // P2 (jugada 2 original)

        vm.undo() // volver a la posición tras la jugada 1 (turno de P2)
        assertEquals(0, vm.moveIndex.value)

        play("D11", "C8") // P2 juega una jugada distinta → descarta la línea futura

        assertEquals(2, vm.history.value.size)
        assertEquals(1, vm.moveIndex.value)
        assertEquals(v("D11"), vm.history.value.last().move.from)
        assertEquals(v("C8"), vm.history.value.last().move.to)
    }

    @Test
    fun rotate_rotatesTheWholeLine_keepingIndexAndSize() {
        play("D1", "C1") // P1
        play("D10", "C7") // P2

        val sizeBefore = vm.history.value.size
        val indexBefore = vm.moveIndex.value
        vm.rotate()

        assertEquals(sizeBefore, vm.history.value.size)
        assertEquals(indexBefore, vm.moveIndex.value)
        // La primera jugada quedó rotada 60° (from/to mapeados por Board25.rotate60).
        assertEquals(Board25.rotate60(v("D1")), vm.history.value.first().move.from)
        assertEquals(Board25.rotate60(v("C1")), vm.history.value.first().move.to)
    }
}
