package com.agustin.tarati.game6.board

import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.board.GameBoardGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato de [BoardGraph]: invariantes que toda implementación debe cumplir, verificados sobre
 * las dos que existen — [Board25] (tablero multijugador) y [GameBoardGraph] (adaptador del tablero
 * fijo de Tarati). Formaliza el desacople de M2: la adyacencia es por-tablero, no del singleton
 * global.
 */
class BoardGraphContractTest {

    private val boards: List<Pair<String, BoardGraph>> =
        listOf(
            "Board25" to Board25,
            "GameBoardGraph" to GameBoardGraph,
        )

    @Test
    fun adjacencyMap_containsEveryVertex() {
        boards.forEach { (name, board) ->
            board.vertices.forEach { vertex ->
                assertTrue(
                    board.adjacencyMap.containsKey(vertex),
                    "$name: la adyacencia debe contener a ${vertex.name}",
                )
            }
        }
    }

    @Test
    fun edges_areBidirectional() {
        boards.forEach { (name, board) ->
            board.edges.forEach { edge ->
                assertTrue(
                    board.isAdjacent(edge.from, edge.to),
                    "$name: ${edge.from.name} debe conectar con ${edge.to.name}",
                )
                assertTrue(
                    board.isAdjacent(edge.to, edge.from),
                    "$name: ${edge.to.name} debe conectar con ${edge.from.name}",
                )
            }
        }
    }

    @Test
    fun neighborsOf_matchesAdjacencyMap() {
        boards.forEach { (name, board) ->
            board.vertices.forEach { vertex ->
                assertEquals(
                    board.adjacencyMap[vertex] ?: emptyList<Any>(),
                    board.neighborsOf(vertex),
                    "$name: neighborsOf(${vertex.name}) debe coincidir con adjacencyMap",
                )
            }
        }
    }

    @Test
    fun isAdjacent_isSymmetric() {
        boards.forEach { (name, board) ->
            board.vertices.forEach { a ->
                board.neighborsOf(a).forEach { b ->
                    assertTrue(
                        board.isAdjacent(b, a),
                        "$name: isAdjacent(${a.name}, ${b.name}) debe ser simétrico",
                    )
                }
            }
        }
    }

    @Test
    fun noVertex_isAdjacentToItself() {
        boards.forEach { (name, board) ->
            board.vertices.forEach { vertex ->
                assertFalse(
                    board.isAdjacent(vertex, vertex),
                    "$name: ${vertex.name} no debe ser adyacente a sí mismo",
                )
            }
        }
    }

    @Test
    fun neighborsOf_unknownVertex_isEmpty() {
        boards.forEach { (name, board) ->
            // Zona inexistente en ambos tableros.
            val ghost = com.agustin.tarati.core.domain.game.board.Vertex(
                com.agustin.tarati.core.domain.game.board.Zone('Z'), 99,
            )
            assertTrue(board.neighborsOf(ghost).isEmpty(), "$name: vértice desconocido no tiene vecinos")
        }
    }

    @Test
    fun gameBoardGraph_reflectsGameBoardExactly() {
        assertEquals(GameBoard.vertices, GameBoardGraph.vertices)
        assertEquals(GameBoard.edges, GameBoardGraph.edges)
        assertEquals(
            GameBoard.adjacencyMap as Map<*, *>,
            GameBoardGraph.adjacencyMap as Map<*, *>,
        )
    }
}
