package com.agustin.tarati.game.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import com.agustin.tarati.core.domain.game.board.GameBoard.edges
import com.agustin.tarati.core.domain.game.board.GameBoard.homeBases
import com.agustin.tarati.core.domain.game.board.GameBoard.vertices
import com.agustin.tarati.core.domain.game.board.findClosestVertex
import com.agustin.tarati.core.domain.game.board.getVisualPosition
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoardTest {
    @Test
    fun adjacencyMap_containsAllVertices() {
        vertices.forEach { vertex ->
            assertTrue(
                "Adjacency map should contain all vertices",
                adjacencyMap.containsKey(vertex),
            )
        }
    }

    @Test
    fun adjacencyMap_hasBidirectionalConnections() {
        edges.forEach { edge ->
            assertTrue(
                "${edge.from} should connect to ${edge.to}",
                adjacencyMap[edge.from]?.contains(edge.to) == true,
            )
            assertTrue(
                "${edge.to} should connect to ${edge.from}",
                adjacencyMap[edge.to]?.contains(edge.from) == true,
            )
        }
    }

    @Test
    fun homeBases_containCorrectVertices() {
        val whiteHome = homeBases[WHITE] ?: return
        val blackHome = homeBases[BLACK] ?: return

        assertEquals("White home should have 4 vertices", 4, whiteHome.size)
        assertEquals("Black home should have 4 vertices", 4, blackHome.size)

        assertTrue("White home should contain C1", whiteHome.contains(C1))
        assertTrue("White home should contain C2", whiteHome.contains(C2))
        assertTrue("Black home should contain C7", blackHome.contains(C7))
        assertTrue("Black home should contain C8", blackHome.contains(C8))
    }

    @Test
    fun getVisualPosition_returnsCorrectPosition() {
        val position =
            getVisualPosition(
                vertex = A1,
                size = Size(500f, 500f),
                orientation = BoardOrientation.PORTRAIT_WHITE,
            )

        assertTrue(
            "Position should be within canvas bounds",
            position.x in 0f..500f,
        )
        assertTrue(
            "Position should be within canvas bounds",
            position.y in 0f..500f,
        )
    }

    @Test
    fun findClosestVertex_findsNearbyVertex() {
        // Test with coordinates close to a known name position
        val vertex =
            findClosestVertex(
                tapOffset =
                    Offset(250f, 250f),
                size = Size(500f, 500f),
                maxTapDistance = 50f,
                orientation = BoardOrientation.PORTRAIT_WHITE,
            )

        assertNotNull("Should find a name for nearby tap", vertex)
        assertTrue(
            "Found name should be in vertices list",
            vertices.contains(vertex),
        )
    }

    @Test
    fun findClosestVertex_tooFar_returnsNull() {
        val vertex =
            findClosestVertex(
                tapOffset =
                    Offset(10f, 10f),
                size = Size(500f, 500f),
                maxTapDistance = 5f, // Very small max distance
                orientation = BoardOrientation.PORTRAIT_WHITE,
            )

        assertNull("Should return null when no name is close enough", vertex)
    }
}
