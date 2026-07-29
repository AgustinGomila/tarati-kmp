package com.agustin.tarati.core.domain.game.board

import com.agustin.tarati.core.domain.game.board.BoardSymmetry.mirror
import kotlin.math.abs

/**
 * Simetría bilateral del tablero de Tarati.
 *
 * A diferencia del ajedrez —donde rey y reina rompen la simetría izquierda/derecha—, el tablero de
 * Tarati es simétrico respecto del eje vertical que une ambas bases: una apertura hacia un lado es
 * idéntica a su especular hacia el otro, siempre que la respuesta también sea especular.
 *
 * [mirror] es la permutación de vértices inducida por esa reflexión. Se deriva de la geometría
 * ([GameBoard.logicalPositions]): reflejar `x → REFERENCE_BOARD_SIZE − x` dejando `y` intacto. Como
 * el avance de las piezas es sobre el eje Y, la reflexión **preserva color, turno y direccionalidad**
 * (una jugada "hacia adelante" de blancas sigue siéndolo tras reflejar).
 *
 * Uso: canonicalizar posiciones y jugadas al minar aperturas (plegar los pares espejo en una única
 * clave duplica la muestra por posición) y —de forma idéntica— al consultar el opening book en el
 * motor. Ver [com.agustin.tarati.core.domain.game.play.GameState.canonicalMove].
 */
object BoardSymmetry {

    /** Permutación de vértices bajo la reflexión especular. Involución: `mirror(mirror(v)) == v`. */
    private val mirrorMap: Map<Vertex, Vertex> = buildMirror()

    /** Vértice reflejado especularmente de [vertex]. */
    fun mirror(vertex: Vertex): Vertex = mirrorMap.getValue(vertex)

    private fun buildMirror(): Map<Vertex, Vertex> {
        val positions = GameBoard.logicalPositions
        val size = GameBoard.REFERENCE_BOARD_SIZE
        // Los vértices están separados por mucho más que esta tolerancia; el layout es simétrico por
        // construcción (ángulos alrededor de π/2), así que el espejo de cada vértice es otro vértice.
        val tolerance = 1f
        return positions.keys.associateWith { vertex ->
            val position = positions.getValue(vertex)
            val mirroredX = size - position.x
            positions.entries
                .first { (_, other) -> abs(other.x - mirroredX) < tolerance && abs(other.y - position.y) < tolerance }
                .key
        }
    }
}
