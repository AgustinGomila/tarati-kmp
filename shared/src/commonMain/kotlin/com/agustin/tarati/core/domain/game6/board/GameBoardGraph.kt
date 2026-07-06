package com.agustin.tarati.core.domain.game6.board

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.Vertex

/**
 * Adaptador que expone el tablero fijo de Tarati ([GameBoard]) a través de [BoardGraph],
 * **sin modificar** `GameBoard`. Permite tratar ambos tableros (Tarati `30` y multijugador `25`)
 * de forma uniforme detrás de la abstracción, y sirve de puente para reutilizar lógica
 * board-agnostic sobre el tablero publicado.
 *
 * Las propiedades delegan directamente en `GameBoard` (lectura), de modo que reflejan siempre
 * su estado sin duplicarlo.
 */
object GameBoardGraph : BoardGraph {
    override val vertices: List<Vertex> get() = GameBoard.vertices
    override val edges: List<Edge> get() = GameBoard.edges
    override val adjacencyMap: Map<Vertex, List<Vertex>> get() = GameBoard.adjacencyMap
}
