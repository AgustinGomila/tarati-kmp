package com.agustin.tarati.features.game6

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.features.game6.Board25Geometry.LAYER
import com.agustin.tarati.features.game6.Board25Geometry.fit
import com.agustin.tarati.features.game6.Board25Geometry.positions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometría visual del tablero `25` (juego multijugador). Calcula la posición de los 49 vértices
 * en un espacio normalizado centrado en el origen (y hacia abajo, como en Compose) y las proyecta
 * al lienzo con [fit].
 *
 * Construcción por capas concéntricas equiespaciadas (paso [LAYER]): A (centro) → B (radio 1·L) →
 * C (2·L). Cada base es un **prisma de dos rieles paralelos** a su dirección radial `u`: los
 * vértices `D` y `E` se obtienen desplazando su vértice `C` a lo largo de `u`
 * (`D = C + L·u`, `E = C + 2·L·u`). Así `C–D–E` de una base quedan **colineales** (verticales en la
 * base inferior) y las capas quedan a distancia uniforme. Los conectores del anillo `D` van a radio
 * `3·L` en el ángulo medio entre bases, con lo que los 18 vértices `D` quedan **equidistantes** a lo
 * largo del anillo. Respeta la simetría de rotación de 60° (`Board25.rotate60`).
 */
object Board25Geometry {

    private const val LAYER = 1.0                  // paso radial entre capas
    private const val R_BRIDGE = LAYER             // B
    private const val R_CIRCUMFERENCE = 2 * LAYER  // C

    /** Ángulo (grados) del vértice C de índice [i1based]; válido también para índices > 12. */
    private fun cAngleDeg(i1based: Int): Double = 75.0 + (i1based - 1) * 30.0

    /** Ángulo (grados) de la dirección radial de la base [k] (0..5); base 0 = 90° (abajo, sur). */
    private fun baseAngleDeg(k: Int): Double = 90.0 + 60.0 * k

    private fun polar(radius: Double, angleDeg: Double): Offset {
        val rad = angleDeg / 180.0 * PI
        return Offset((radius * cos(rad)).toFloat(), (radius * sin(rad)).toFloat())
    }

    /** Vector unitario en la dirección [angleDeg]. */
    private fun unit(angleDeg: Double): Offset = polar(1.0, angleDeg)

    private fun circumference(i1based: Int): Offset = polar(R_CIRCUMFERENCE, cAngleDeg(i1based))

    /** Posiciones normalizadas centradas en el origen (mismas para toda sesión). */
    val positions: Map<Vertex, Offset> by lazy {
        val map = LinkedHashMap<Vertex, Offset>(49)

        map[Board25.A1] = Offset.Zero

        Board25.bridgeVertices.forEachIndexed { k, vertex ->
            map[vertex] = polar(R_BRIDGE, 90.0 + k * 60.0)
        }

        Board25.circumferenceVertices.forEachIndexed { idx, vertex ->
            map[vertex] = circumference(idx + 1)
        }

        // Anillo D: por sextante (k) → [par de base sobre los rieles] + [conector].
        // El conector va al punto medio de sus dos rieles vecinos (D de esta base y D de la
        // siguiente), quedando colineal con ellos → los lados libres del tablero son rectos
        // (p. ej. D5-D6-D7 y D14-D15-D16 verticales) y el tablero queda perfectamente simétrico.
        Board25.domesticVertices.forEachIndexed { idx, vertex ->
            val k = idx / 3
            val u = unit(baseAngleDeg(k))
            map[vertex] = when (idx % 3) {
                0 -> circumference(2 * k + 1) + u * LAYER.toFloat()
                1 -> circumference(2 * k + 2) + u * LAYER.toFloat()
                else -> {
                    val railThis = circumference(2 * k + 2) + u * LAYER.toFloat()
                    val railNext = circumference(2 * k + 3) + unit(baseAngleDeg(k + 1)) * LAYER.toFloat()
                    (railThis + railNext) / 2f
                }
            }
        }

        // Puntas E: extienden cada riel una capa más (E = C + 2·L·u).
        Board25.edgeVertices.forEachIndexed { idx, vertex ->
            val k = idx / 2
            val u = unit(baseAngleDeg(k))
            val cIndex = if (idx % 2 == 0) 2 * k + 1 else 2 * k + 2
            map[vertex] = circumference(cIndex) + u * (2 * LAYER).toFloat()
        }

        map
    }

    /**
     * Proyecta [positions] al lienzo [size], centrado y escalado uniformemente para caber con un
     * margen ([marginPercent] del espacio disponible).
     */
    fun fit(size: Size, marginPercent: Float = 0.86f): Map<Vertex, Offset> {
        val minX = positions.values.minOf { it.x }
        val maxX = positions.values.maxOf { it.x }
        val minY = positions.values.minOf { it.y }
        val maxY = positions.values.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY

        val scale = minOf(size.width / width, size.height / height) * marginPercent
        val offsetX = (size.width - width * scale) / 2f - minX * scale
        val offsetY = (size.height - height * scale) / 2f - minY * scale

        return positions.mapValues { (_, p) -> Offset(offsetX + p.x * scale, offsetY + p.y * scale) }
    }

    /** Vértice más cercano a [tap] entre [screenPositions], o `null` si ninguno está a < [maxDistance]. */
    fun closestVertex(
        tap: Offset,
        screenPositions: Map<Vertex, Offset>,
        maxDistance: Float,
    ): Vertex? =
        screenPositions.entries
            .minByOrNull { (_, pos) -> (tap.x - pos.x).pow(2) + (tap.y - pos.y).pow(2) }
            ?.takeIf { (_, pos) -> sqrt((tap.x - pos.x).pow(2) + (tap.y - pos.y).pow(2)) < maxDistance }
            ?.key
}
