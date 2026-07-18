package com.agustin.tarati.core.domain.game.board

import kotlinx.serialization.Serializable

/**
 * Arista no dirigida entre dos vértices del tablero.
 *
 * La igualdad es **simétrica**: `Edge(a to b) == Edge(b to a)` (con hashCode
 * conmutativo acorde). Clase normal, no data class: los `copy`/`componentN`
 * generados serían inconsistentes con esa igualdad no posicional.
 */
@Serializable
class Edge(
    val pair: Pair<Vertex, Vertex>,
) {
    val from: Vertex get() = this.pair.first
    val to: Vertex get() = this.pair.second
    val name: String get() = "${from.name}-${to.name}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Edge) return false

        return (this.from == other.from && this.to == other.to) ||
                (this.from == other.to && this.to == other.from)
    }

    override fun hashCode(): Int = from.hashCode() + to.hashCode()

    override fun toString(): String = name
}
