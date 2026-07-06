package com.agustin.tarati.core.domain.game6.board

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.Vertex

/**
 * Abstracción de un tablero como grafo no dirigido de [Vertex] conectados por [Edge].
 *
 * Desacopla la noción de **adyacencia** del singleton global `GameBoard.adjacencyMap` (y de
 * `Vertex.isAdjacentTo`, que lo consulta): cada tablero provee su propia adyacencia. Permite
 * escribir la lógica del juego multijugador (movimientos, capturas) contra esta interfaz en
 * lugar de contra un tablero concreto.
 *
 * Implementaciones: [Board25] (tablero multijugador de 6 bases) y [GameBoardGraph] (adaptador
 * del tablero fijo de Tarati, que no modifica `GameBoard`).
 */
interface BoardGraph {
    /** Todos los vértices del tablero. */
    val vertices: List<Vertex>

    /** Todas las aristas (no dirigidas) del tablero. */
    val edges: List<Edge>

    /** Adyacencia derivada de [edges]: para cada vértice, sus vecinos directos. */
    val adjacencyMap: Map<Vertex, List<Vertex>>

    /** Vecinos de [vertex] (lista vacía si no pertenece al tablero). */
    fun neighborsOf(vertex: Vertex): List<Vertex> = adjacencyMap[vertex] ?: emptyList()

    /** `true` si [a] y [b] están conectados por una arista. */
    fun isAdjacent(a: Vertex, b: Vertex): Boolean = adjacencyMap[a]?.contains(b) == true
}
