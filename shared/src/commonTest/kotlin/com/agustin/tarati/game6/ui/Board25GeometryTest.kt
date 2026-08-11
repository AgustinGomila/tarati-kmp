package com.agustin.tarati.game6.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.features.game6.Board25Geometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests de la geometría visual del tablero 25 (Board25Geometry). */
class Board25GeometryTest {

    @Test
    fun hasPositionForEveryVertex() {
        assertEquals(49, Board25Geometry.positions.size)
        Board25.vertices.forEach { vertex ->
            assertTrue(Board25Geometry.positions.containsKey(vertex), "Falta posición para ${vertex.name}")
        }
    }

    @Test
    fun positionsAreDistinct() {
        val pts = Board25Geometry.positions.values.map { "${it.x},${it.y}" }
        assertEquals(pts.size, pts.toSet().size, "No debe haber posiciones superpuestas")
    }

    @Test
    fun centerIsAtOrigin() {
        assertEquals(0f, Board25Geometry.positions.getValue(Board25.A1).x, 1e-4f)
        assertEquals(0f, Board25Geometry.positions.getValue(Board25.A1).y, 1e-4f)
    }

    @Test
    fun rotation60_matchesGeometricRotation() {
        // Rotar el vértice (Board25.rotate60) equivale a rotar su posición 60° alrededor del centro.
        val theta = 60.0 / 180.0 * PI
        val cos = cos(theta)
        val sin = sin(theta)
        Board25.vertices.forEach { vertex ->
            val p = Board25Geometry.positions.getValue(vertex)
            val expected = Offset(
                (p.x * cos - p.y * sin).toFloat(),
                (p.x * sin + p.y * cos).toFloat(),
            )
            val actual = Board25Geometry.positions.getValue(Board25.rotate60(vertex))
            assertEquals(expected.x, actual.x, 1e-3f, "x de ${vertex.name}")
            assertEquals(expected.y, actual.y, 1e-3f, "y de ${vertex.name}")
        }
    }

    @Test
    fun fit_keepsAllVerticesWithinCanvas() {
        val size = Size(800f, 600f)
        val screen = Board25Geometry.fit(size)
        assertEquals(49, screen.size)
        screen.forEach { (vertex, p) ->
            assertTrue(p.x in 0f..size.width, "${vertex.name} fuera del ancho")
            assertTrue(p.y in 0f..size.height, "${vertex.name} fuera del alto")
        }
    }

    @Test
    fun fit_isCenteredOnCanvas() {
        val size = Size(800f, 600f)
        val screen = Board25Geometry.fit(size)
        val cx = (screen.values.minOf { it.x } + screen.values.maxOf { it.x }) / 2f
        val cy = (screen.values.minOf { it.y } + screen.values.maxOf { it.y }) / 2f
        assertEquals(size.width / 2f, cx, 1f, "Centrado en X")
        assertEquals(size.height / 2f, cy, 1f, "Centrado en Y")
    }

    @Test
    fun closestVertex_findsTappedVertexAndRejectsFarTaps() {
        val size = Size(800f, 600f)
        val screen = Board25Geometry.fit(size)
        val target = Board25.circumferenceVertices[0]
        val at = screen.getValue(target)

        assertEquals(target, Board25Geometry.closestVertex(at, screen, maxDistance = 20f))
        assertNull(
            Board25Geometry.closestVertex(Offset(-500f, -500f), screen, maxDistance = 20f),
            "Un tap fuera de rango no matchea",
        )
    }

    @Test
    fun baseRails_areCollinearWithUniformSpacing() {
        // Base 17 (inferior): los rieles C1–D1–E1 y C2–D2–E2 son verticales (misma x) y con
        // separación uniforme entre capas (|C→D| == |D→E|).
        val pos = Board25Geometry.positions
        fun rail(cIdx: Int, dIdx: Int, eIdx: Int) {
            val c = pos.getValue(Board25.circumferenceVertices[cIdx])
            val d = pos.getValue(Board25.domesticVertices[dIdx])
            val e = pos.getValue(Board25.edgeVertices[eIdx])
            assertEquals(c.x, d.x, 1e-3f, "Riel vertical: C.x == D.x")
            assertEquals(d.x, e.x, 1e-3f, "Riel vertical: D.x == E.x")
            assertEquals(d.y - c.y, e.y - d.y, 1e-3f, "Capas equiespaciadas en el riel")
        }
        rail(0, 0, 0) // C1, D1, E1
        rail(1, 1, 1) // C2, D2, E2
    }

    @Test
    fun domesticRing_isEquidistant() {
        // Las 18 aristas del anillo D tienen (casi) la misma longitud.
        val d = Board25.domesticVertices.map { Board25Geometry.positions.getValue(it) }
        val edgeLengths = (0 until 18).map { i ->
            val a = d[i]
            val b = d[(i + 1) % 18]
            sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
        }
        val mean = edgeLengths.average().toFloat()
        edgeLengths.forEach { len ->
            assertEquals(mean, len, mean * 0.03f, "Arista del anillo D ~ media")
        }
    }

    @Test
    fun freeSideConnectors_areVerticallyAligned() {
        // Lados libres: D5-D6-D7 (izquierda) y D14-D15-D16 (derecha) comparten x → verticales.
        val pos = Board25Geometry.positions
        fun x(i: Int) = pos.getValue(Board25.domesticVertices[i]).x
        assertEquals(x(4), x(5), 1e-3f, "D5.x == D6.x")
        assertEquals(x(5), x(6), 1e-3f, "D6.x == D7.x")
        assertEquals(x(13), x(14), 1e-3f, "D14.x == D15.x")
        assertEquals(x(14), x(15), 1e-3f, "D15.x == D16.x")
    }

    @Test
    fun connectors_lieOnNeighborRailSegment() {
        // Cada conector es el punto medio de sus dos rieles vecinos → colineal con ellos (todos
        // los lados del contorno D son rectos).
        val pos = Board25Geometry.positions
        for (k in 0 until 6) {
            val prev = pos.getValue(Board25.domesticVertices[3 * k + 1])       // riel 2 de la base k
            val conn = pos.getValue(Board25.domesticVertices[3 * k + 2])       // conector
            val next = pos.getValue(Board25.domesticVertices[(3 * k + 3) % 18]) // riel 1 de la base k+1
            assertEquals((prev.x + next.x) / 2f, conn.x, 1e-3f, "Conector k=$k en x del punto medio")
            assertEquals((prev.y + next.y) / 2f, conn.y, 1e-3f, "Conector k=$k en y del punto medio")
        }
    }
}
