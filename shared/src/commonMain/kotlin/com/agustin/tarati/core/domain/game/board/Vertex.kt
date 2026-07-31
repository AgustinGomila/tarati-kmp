package com.agustin.tarati.core.domain.game.board

import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import kotlinx.serialization.Serializable

@Serializable
data class Vertex(
    val zone: Zone,
    val position: Int,
) {
    val name: String get() = "${zone.name}$position"

    fun isAdjacentTo(other: Vertex): Boolean = adjacencyMap[this]?.contains(other) == true

    companion object {
        fun parseVertex(vertexName: String): Vertex {
            if (vertexName.length < 2) {
                throw IllegalArgumentException("Invalid vertex name: $vertexName")
            }

            val zoneChar = vertexName[0]
            val positionStr = vertexName.substring(1)

            val position =
                positionStr.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid vertex position: $positionStr")

            return Vertex(Zone(zoneChar), position)
        }
    }
}
