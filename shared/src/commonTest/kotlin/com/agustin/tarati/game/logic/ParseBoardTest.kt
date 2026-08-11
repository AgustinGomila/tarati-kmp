package com.agustin.tarati.game.logic

import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseBoardTest {
    @Test
    fun `parseBoardNotation should handle positions with two-digit numbers`() {
        val notation = "C10w/C11b/C12w/B1b w"

        val gameState = parseBoardNotation(notation)
        val vertices = GameBoard.vertices.associateBy { it.name }

        // Verificar que se parsearon correctamente las posiciones de 2 dígitos
        assertEquals(WHITE, gameState.cobs[vertices["C10"]]?.color)
        assertEquals(BLACK, gameState.cobs[vertices["C11"]]?.color)
        assertEquals(WHITE, gameState.cobs[vertices["C12"]]?.color)
        assertEquals(BLACK, gameState.cobs[vertices["B1"]]?.color)

        assertEquals(4, gameState.cobs.size)
        assertEquals(WHITE, gameState.currentTurn)
    }

    @Test
    fun `parseBoardNotation should handle mixed case colors correctly`() {
        // Test para verificar que el problema original de comparación de colores está solucionado
        val notation = "B1w/C2W/D3b/D4B w"

        val gameState = parseBoardNotation(notation)
        val vertices = GameBoard.vertices.associateBy { it.name }

        // Todos los colores deben parsearse correctamente independientemente de mayúsculas/minúsculas
        assertEquals(WHITE, gameState.cobs[vertices["B1"]]?.color)
        assertEquals(WHITE, gameState.cobs[vertices["C2"]]?.color) // W mayúscula
        assertEquals(BLACK, gameState.cobs[vertices["D3"]]?.color)
        assertEquals(BLACK, gameState.cobs[vertices["D4"]]?.color) // B mayúscula
    }

    @Test
    fun `parseBoardNotation should handle the original complex notation`() {
        val notation = "B1w/C2w/C6b/C8b/D1w/D2w/D3b/D4b w"

        val gameState = parseBoardNotation(notation)

        assertEquals(WHITE, gameState.currentTurn)
        assertEquals(8, gameState.cobs.size)

        // Verificar que todas las piezas son no mejoradas (letras minúsculas en posición)
        gameState.cobs.values.forEach { cob ->
            assertEquals(false, cob.isUpgraded)
        }
    }
}
