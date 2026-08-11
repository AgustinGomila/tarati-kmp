package com.agustin.tarati.ui.components.board

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.board.GameBoard.DOMESTIC
import com.agustin.tarati.core.domain.game.board.normalizedPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardNormalizationTest {
    @Test
    fun normalizedPositions_mostCoordinatesInValidRange() {
        normalizedPositions.forEach { (vertex, normalizedBoard) ->
            // Las bases D pueden tener coordenadas fuera de [0,1], ya que están fuera del tablero circular
            if (vertex.zone != DOMESTIC) {
                assertTrue(
                    normalizedBoard.x in 0f..1f,
                    "Vertex $vertex X coordinate should be between 0 and 1, but was ${normalizedBoard.x}",
                )
                assertTrue(
                    normalizedBoard.y in 0f..1f,
                    "Vertex $vertex Y coordinate should be between 0 and 1, but was ${normalizedBoard.y}",
                )
            }
        }
    }

    @Test
    fun normalizedPositions_basesHaveValidExtendedCoordinates() {
        // Verificar que las bases tengan coordenadas consistentes aunque estén fuera de [0,1]
        val d1 = normalizedPositions[D1]
        val d2 = normalizedPositions[D2]
        val d3 = normalizedPositions[D3]
        val d4 = normalizedPositions[D4]

        assertNotNull(d1, "D1 should exist")
        assertNotNull(d2, "D2 should exist")
        assertNotNull(d3, "D3 should exist")
        assertNotNull(d4, "D4 should exist")

        // D1 y D2 deberían estar en la parte superior (Y > 1)
        assertTrue(d1.y > 1f, "D1 should be above main board")
        assertTrue(d2.y > 1f, "D2 should be above main board")

        // D3 y D4 deberían estar en la parte inferior (Y < 0)
        assertTrue(d3.y < 0f, "D3 should be below main board")
        assertTrue(d4.y < 0f, "D4 should be below main board")

        // Todas las bases deberían tener X entre 0 y 1
        assertTrue(d1.x in 0f..1f, "D1 X should be reasonable")
        assertTrue(d2.x in 0f..1f, "D2 X should be reasonable")
        assertTrue(d3.x in 0f..1f, "D3 X should be reasonable")
        assertTrue(d4.x in 0f..1f, "D4 X should be reasonable")
    }

    @Test
    fun normalizedPositions_noNaNOrInfiniteValues() {
        normalizedPositions.forEach { (vertex, normalizedBoard) ->
            assertFalse(normalizedBoard.x.isNaN(), "Vertex $vertex X should not be NaN")
            assertFalse(normalizedBoard.y.isNaN(), "Vertex $vertex Y should not be NaN")
            assertFalse(normalizedBoard.x.isInfinite(), "Vertex $vertex X should not be infinite")
            assertFalse(normalizedBoard.y.isInfinite(), "Vertex $vertex Y should not be infinite")
        }
    }

    @Test
    fun normalizedPositions_centerVertexAtCenter() {
        val a1 = normalizedPositions[A1]
        assertNotNull(a1, "A1 should exist")
        // A1 debería estar cerca del centro del tablero principal
        assertEquals(0.5f, a1.x, 0.01f, "A1 X should be approximately 0.5")
        assertEquals(0.5f, a1.y, 0.01f, "A1 Y should be approximately 0.5")
    }

    @Test
    fun normalizedPositions_consistentWithBoardGeometry() {
        // Verificar que las posiciones normalizadas mantengan la geometría del tablero
        val a1 = normalizedPositions[A1] ?: return
        val b1 = normalizedPositions[B1] ?: return
        val c1 = normalizedPositions[C1] ?: return
        val d1 = normalizedPositions[D1] ?: return
        val d4 = normalizedPositions[D4] ?: return
        val d3 = normalizedPositions[D3] ?: return

        // A1 debería estar en el centro del tablero principal
        assertEquals(0.5f, a1.x, 0.1f)
        assertEquals(0.5f, a1.y, 0.1f)

        // Las bases D1 debería estar arriba del centro
        assertTrue(d1.y > 0.5f, "D1 should be above center")

        // Las bases D3 debería estar abajo del centro
        assertTrue(d3.y < 0.5f, "D3 should be below center")

        // Las bases B1 debería estar arriba del centro
        assertTrue(b1.y > 0.5f, "B1 should be above center")

        // Las bases C1 debería estar arriba del centro
        assertTrue(c1.y > 0.5f, "C1 should be above center")

        // Las bases D4 debería estar abajo del centro
        assertTrue(d4.y < 0.5f, "D4 should be below center")
    }
}
