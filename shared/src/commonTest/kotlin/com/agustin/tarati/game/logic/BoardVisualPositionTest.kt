package com.agustin.tarati.game.logic

import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.board.GameBoard.vertices
import com.agustin.tarati.core.domain.game.board.getVisualPosition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardVisualPositionTest {
    @Test
    fun getVisualPosition_handlesExtendedCoordinates() {
        val size = Size(500f, 500f)
        val orientation = BoardOrientation.PORTRAIT_WHITE

        // Verificar que todas las posiciones visuales sean calculadas correctamente
        // incluso para bases con coordenadas extendidas
        vertices.forEach { vertex ->
            val visualPosition =
                getVisualPosition(
                    vertex,
                    size,
                    orientation,
                )

            // Las bases pueden estar fuera del área central, pero deberían ser posiciones válidas
            assertFalse(visualPosition.x.isNaN(), "Vertex $vertex X should not be NaN")
            assertFalse(visualPosition.y.isNaN(), "Vertex $vertex Y should not be NaN")
            assertFalse(visualPosition.x.isInfinite(), "Vertex $vertex X should not be infinite")
            assertFalse(visualPosition.y.isInfinite(), "Vertex $vertex Y should not be infinite")

            // Aunque algunas bases puedan estar fuera del canvas, deberían ser posiciones razonables
            val reasonableRange = -100f..(size.width + 100f)
            assertTrue(
                visualPosition.x in reasonableRange,
                "Vertex $vertex X should be in reasonable range, but was ${visualPosition.x}",
            )
            assertTrue(
                visualPosition.y in reasonableRange,
                "Vertex $vertex Y should be in reasonable range, but was ${visualPosition.y}",
            )
        }
    }

    @Test
    fun getVisualPositionPortraitWhite_basesPositionedCorrectly() {
        val size = Size(500f, 500f)
        val orientation = BoardOrientation.PORTRAIT_WHITE

        val d1 = getVisualPosition(D1, size, orientation)
        val d2 = getVisualPosition(D2, size, orientation)
        val d3 = getVisualPosition(D3, size, orientation)
        val d4 = getVisualPosition(D4, size, orientation)

        // En orientación PORTRAIT_WHITE, D1 y D2 deberían estar abajo, D3 y D4 arriba
        assertTrue(d1.y > size.height / 2, "D1 should be below center")
        assertTrue(d2.y > size.height / 2, "D2 should be below center")
        assertTrue(d3.y < size.height / 2, "D3 should be above center")
        assertTrue(d4.y < size.height / 2, "D4 should be above center")
    }

    @Test
    fun getVisualPositionPortraitBlack_basesPositionedCorrectly() {
        val size = Size(500f, 500f)
        val orientation = BoardOrientation.PORTRAIT_BLACK

        val d1 = getVisualPosition(D1, size, orientation)
        val d2 = getVisualPosition(D2, size, orientation)
        val d3 = getVisualPosition(D3, size, orientation)
        val d4 = getVisualPosition(D4, size, orientation)

        // En orientación PORTRAIT_WHITE, D3 y D4 deberían estar abajo, D1 y D2 arriba
        assertTrue(d1.y < size.height / 2, "D1 should be above center")
        assertTrue(d2.y < size.height / 2, "D2 should be above center")
        assertTrue(d3.y > size.height / 2, "D3 should be below center")
        assertTrue(d4.y > size.height / 2, "D4 should be below center")
    }
}
