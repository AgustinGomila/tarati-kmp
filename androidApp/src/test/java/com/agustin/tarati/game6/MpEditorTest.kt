package com.agustin.tarati.game6

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.features.game6.MpLocalGameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests del **editor de posiciones** (D14) del [MpLocalGameViewModel]: colocar/quitar/reemplazar
 * piezas por color, derivación de `hasLeftBase`, cicladores de color/turno, limpiar/reiniciar,
 * validación libre (≥2 colores con piezas) e inicio desde la posición editada.
 */
class MpEditorTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    private lateinit var vm: MpLocalGameViewModel

    @Before
    fun setup() {
        vm = MpLocalGameViewModel()
    }

    /** Tap sobre [vertex] (en modo edición rutea a colocar/quitar). */
    private fun tap(vertex: String) = vm.onVertexTap(v(vertex))

    @Test
    fun toggleEditing_entersAndExits() {
        assertFalse(vm.isEditing.value)
        vm.toggleEditing()
        assertTrue(vm.isEditing.value)
        // Al entrar, el color de edición es el del primer asiento (P1).
        assertEquals(PlayerColor.P1, vm.editColor.value)
        vm.toggleEditing()
        assertFalse(vm.isEditing.value)
    }

    @Test
    fun editPiece_placeOnEmpty_thenRemoveOnOwn() {
        vm.toggleEditing()
        vm.clearEditBoard()
        // Vacío → coloca P1.
        tap("C1")
        assertEquals(PlayerColor.P1, vm.state.value.pieces[v("C1")]?.owner)
        // Propia → quita.
        tap("C1")
        assertNull(vm.state.value.pieces[v("C1")])
    }

    @Test
    fun editPiece_otherColor_isReplaced() {
        vm.toggleEditing()
        vm.clearEditBoard()
        tap("C1") // P1
        vm.cycleEditColor() // → P2
        tap("C1") // reemplaza por P2 (no lo quita)
        assertEquals(PlayerColor.P2, vm.state.value.pieces[v("C1")]?.owner)
    }

    @Test
    fun hasLeftBase_isDerivedFromOwnerBaseSquare() {
        vm.toggleEditing()
        vm.clearEditBoard()
        // P1 (base 17 = S) tiene su cuadrado en D1/D2/E1/E2. Colocar en D1 → aún en base.
        tap("D1")
        assertFalse(vm.state.value.pieces[v("D1")]?.hasLeftBase ?: true)
        // Colocar en C1 (fuera del cuadrado de la base) → ya salió de base.
        tap("C1")
        assertTrue(vm.state.value.pieces[v("C1")]?.hasLeftBase ?: false)
    }

    @Test
    fun cycleEditColor_walksSeatColors() {
        vm.toggleEditing()
        assertEquals(PlayerColor.P1, vm.editColor.value)
        vm.cycleEditColor()
        assertEquals(PlayerColor.P2, vm.editColor.value)
        vm.cycleEditColor() // vuelve al inicio (2 asientos)
        assertEquals(PlayerColor.P1, vm.editColor.value)
    }

    @Test
    fun cycleEditTurn_walksSeats() {
        vm.toggleEditing()
        assertEquals(0, vm.state.value.currentSeatIndex)
        vm.cycleEditTurn()
        assertEquals(1, vm.state.value.currentSeatIndex)
        vm.cycleEditTurn()
        assertEquals(0, vm.state.value.currentSeatIndex)
    }

    @Test
    fun clearEditBoard_emptiesPieces_resetKeepsStandardSetup() {
        vm.toggleEditing()
        vm.clearEditBoard()
        assertTrue(vm.state.value.pieces.isEmpty())

        vm.resetEditBoard()
        // 2 jugadores × 4 piezas.
        assertEquals(8, vm.state.value.pieces.size)
        assertEquals(PlayerColor.P1, vm.state.value.pieces[v("D1")]?.owner)
        assertEquals(0, vm.state.value.currentSeatIndex)
    }

    @Test
    fun editCanStart_requiresTwoColors() {
        vm.toggleEditing()
        vm.clearEditBoard()
        assertFalse(vm.editCanStart())
        tap("C1") // P1
        assertFalse(vm.editCanStart()) // un solo color
        vm.cycleEditColor() // → P2
        tap("C7")
        assertTrue(vm.editCanStart()) // dos colores
    }

    @Test
    fun startGameFromEdit_commitsPosition_andResetsHistory() {
        vm.toggleEditing()
        vm.clearEditBoard()
        tap("C1") // P1
        vm.cycleEditColor()
        tap("C7") // P2
        vm.startGameFromEdit()

        assertFalse(vm.isEditing.value)
        assertTrue(vm.history.value.isEmpty())
        assertEquals(-1, vm.moveIndex.value)
        assertTrue(vm.isAtTip())
        assertEquals(2, vm.state.value.pieces.size)
        assertEquals(PlayerColor.P1, vm.state.value.pieces[v("C1")]?.owner)
    }

    @Test
    fun startGameFromEdit_advancesTurnPastEmptySeat() {
        vm.setPlayerCount(3) // asientos P1, P2, P3
        vm.toggleEditing()
        vm.clearEditBoard()
        tap("C1") // P1
        vm.cycleEditColor() // → P2
        vm.cycleEditColor() // → P3
        tap("C7") // P3 (P2 queda sin piezas)
        vm.cycleEditTurn() // turno en el asiento 1 (P2, vacío)
        assertEquals(1, vm.state.value.currentSeatIndex)

        vm.startGameFromEdit()
        // El asiento 1 (P2) no tiene piezas → avanza al primero con piezas: el 2 (P3).
        assertEquals(2, vm.state.value.currentSeatIndex)
    }

    @Test
    fun cancelEditing_restoresPreEditPosition() {
        // Entra desde la posición inicial (8 piezas), edita y cancela → se restaura.
        val initialCount = vm.state.value.pieces.size
        vm.toggleEditing()
        vm.clearEditBoard()
        assertTrue(vm.state.value.pieces.isEmpty())
        vm.toggleEditing() // cancelar
        assertFalse(vm.isEditing.value)
        assertEquals(initialCount, vm.state.value.pieces.size)
        assertTrue(vm.state.value.pieces.containsKey(v("D1")))
    }
}
