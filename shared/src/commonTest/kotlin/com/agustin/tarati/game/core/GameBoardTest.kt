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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameBoardTest {
    @Test
    fun adjacencyMap_containsAllVertices() {
        vertices.forEach { vertex ->
            assertTrue(
                adjacencyMap.containsKey(vertex),
                "Adjacency map should contain all vertices",
            )
        }
    }

    @Test
    fun adjacencyMap_hasBidirectionalConnections() {
        edges.forEach { edge ->
            assertEquals(adjacencyMap[edge.from]?.contains(edge.to), true, "${edge.from} should connect to ${edge.to}")
            assertEquals(adjacencyMap[edge.to]?.contains(edge.from), true, "${edge.to} should connect to ${edge.from}")
        }
    }

    @Test
    fun homeBases_containCorrectVertices() {
        val whiteHome = homeBases[WHITE] ?: return
        val blackHome = homeBases[BLACK] ?: return

        assertEquals(4, whiteHome.size, "White home should have 4 vertices")
        assertEquals(4, blackHome.size, "Black home should have 4 vertices")

        assertTrue(whiteHome.contains(C1), "White home should contain C1")
        assertTrue(whiteHome.contains(C2), "White home should contain C2")
        assertTrue(blackHome.contains(C7), "Black home should contain C7")
        assertTrue(blackHome.contains(C8), "Black home should contain C8")
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
            position.x in 0f..500f,
            "Position should be within canvas bounds",
        )
        assertTrue(
            position.y in 0f..500f,
            "Position should be within canvas bounds",
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

        assertNotNull(vertex, "Should find a name for nearby tap")
        assertTrue(
            vertices.contains(vertex),
            "Found name should be in vertices list",
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

        assertNull(vertex, "Should return null when no name is close enough")
    }
}
