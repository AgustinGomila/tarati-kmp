package com.agustin.tarati.game6.board

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de topología del tablero `25` (Board25) — invariantes de §3 del plan multijugador.
 *
 * Cubre: conteos (49 vértices / 96 aristas), consistencia del grafo (bidireccional, sin
 * duplicados ni self-loops, referencias válidas), perfil de grados (§3.5), las 6 bases (§3.3)
 * y el automorfismo de rotación de 60° (§3.6) — el test más fuerte de correctitud.
 */
class Board25Test {

    // ---- Conteos ----

    @Test
    fun vertexCount_is49() {
        assertEquals("El tablero 25 tiene 49 vértices", 49, Board25.vertices.size)
        assertEquals(1, listOf(Board25.A1).size)
        assertEquals(6, Board25.bridgeVertices.size)
        assertEquals(12, Board25.circumferenceVertices.size)
        assertEquals(18, Board25.domesticVertices.size)
        assertEquals(12, Board25.edgeVertices.size)
    }

    @Test
    fun vertices_areUnique() {
        assertEquals(
            "No debe haber vértices duplicados",
            Board25.vertices.size,
            Board25.vertices.toSet().size,
        )
    }

    @Test
    fun edgeCount_is96() {
        assertEquals("El tablero 25 tiene 96 aristas", 96, Board25.edges.size)
    }

    @Test
    fun edges_areUniqueAndUndirected() {
        // Edge.equals es no dirigido: un duplicado (aunque esté invertido) colapsaría en el Set.
        assertEquals(
            "No debe haber aristas duplicadas (ni repetidas ni invertidas)",
            Board25.edges.size,
            Board25.edges.toSet().size,
        )
    }

    @Test
    fun edges_haveNoSelfLoops() {
        Board25.edges.forEach { edge ->
            assertFalse("Arista con self-loop: ${edge.name}", edge.from == edge.to)
        }
    }

    @Test
    fun edges_referenceKnownVertices() {
        val known = Board25.vertices.toSet()
        Board25.edges.forEach { edge ->
            assertTrue("Vértice desconocido: ${edge.from.name}", edge.from in known)
            assertTrue("Vértice desconocido: ${edge.to.name}", edge.to in known)
        }
    }

    // ---- Consistencia del grafo ----

    @Test
    fun adjacencyMap_containsAllVertices() {
        Board25.vertices.forEach { vertex ->
            assertTrue(
                "El mapa de adyacencia debe contener a ${vertex.name}",
                Board25.adjacencyMap.containsKey(vertex),
            )
        }
    }

    @Test
    fun adjacencyMap_isBidirectional() {
        Board25.edges.forEach { edge ->
            assertTrue(
                "${edge.from.name} debe conectar con ${edge.to.name}",
                Board25.isAdjacent(edge.from, edge.to),
            )
            assertTrue(
                "${edge.to.name} debe conectar con ${edge.from.name}",
                Board25.isAdjacent(edge.to, edge.from),
            )
        }
    }

    @Test
    fun adjacencyMap_doesNotTouchGlobalGameBoard() {
        // La adyacencia de Board25 es propia: E1 no existe en el grafo global de Tarati,
        // pero sí debe tener vecinos aquí (D1 + E2).
        val e1 = Vertex(Board25.EDGE, 1)
        assertEquals(setOf(Vertex(Board25.DOMESTIC, 1), Vertex(Board25.EDGE, 2)), Board25.neighborsOf(e1).toSet())
    }

    // ---- Perfil de grados (§3.5) ----

    @Test
    fun degreeProfile_matchesSpec() {
        fun degree(v: Vertex) = Board25.neighborsOf(v).size

        assertEquals("A1 tiene grado 6", 6, degree(Board25.A1))
        Board25.bridgeVertices.forEach { assertEquals("${it.name} grado 5", 5, degree(it)) }
        Board25.circumferenceVertices.forEach { assertEquals("${it.name} grado 5", 5, degree(it)) }
        Board25.domesticVertices.forEach { assertEquals("${it.name} grado 4", 4, degree(it)) }
        Board25.edgeVertices.forEach { assertEquals("${it.name} grado 2", 2, degree(it)) }
    }

    @Test
    fun sumOfDegrees_isTwiceEdgeCount() {
        val sum = Board25.vertices.sumOf { Board25.neighborsOf(it).size }
        assertEquals("Handshake lemma: Σ grados = 2·|E|", 2 * Board25.edges.size, sum)
    }

    @Test
    fun neighborComposition_baseVsConnectorD() {
        // D1 (base): 1·C + 1·E + 2·D (anillo)
        val d1 = Vertex(Board25.DOMESTIC, 1)
        assertEquals(
            setOf(
                Vertex(Board25.CIRCUMFERENCE, 1), // radial
                Vertex(Board25.EDGE, 1),          // lado de base
                Vertex(Board25.DOMESTIC, 18),     // anillo
                Vertex(Board25.DOMESTIC, 2),      // anillo
            ),
            Board25.neighborsOf(d1).toSet(),
        )

        // D3 (conector): 2·C + 2·D (anillo)
        val d3 = Vertex(Board25.DOMESTIC, 3)
        assertEquals(
            setOf(
                Vertex(Board25.CIRCUMFERENCE, 2), // triangulación
                Vertex(Board25.CIRCUMFERENCE, 3), // triangulación
                Vertex(Board25.DOMESTIC, 2),      // anillo
                Vertex(Board25.DOMESTIC, 4),      // anillo
            ),
            Board25.neighborsOf(d3).toSet(),
        )
    }

    // ---- Bases (§3.3) ----

    @Test
    fun bases_areSixWithFourStartPoints() {
        assertEquals("Hay 6 bases", 6, Board25.bases.size)
        Board25.bases.forEach { base ->
            assertEquals("Base ${base.id}: cuadrado de 4 puntos", 4, base.startSquare.size)
            assertEquals("Base ${base.id}: 2 salidas C", 2, base.cExit.size)
            assertEquals("Base ${base.id}: 2 D interiores", 2, base.dInterior.size)
            assertEquals("Base ${base.id}: 2 puntas E", 2, base.eTips.size)
        }
    }

    @Test
    fun bases_coverAllEdgeVerticesDisjointly() {
        val allTips = Board25.bases.flatMap { it.eTips }
        assertEquals("Las 12 puntas E pertenecen a exactamente una base", 12, allTips.size)
        assertEquals(Board25.edgeVertices.toSet(), allTips.toSet())
    }

    @Test
    fun bases_matchSpecTable() {
        fun c(i: Int) = Vertex(Board25.CIRCUMFERENCE, i)
        fun d(i: Int) = Vertex(Board25.DOMESTIC, i)
        fun e(i: Int) = Vertex(Board25.EDGE, i)

        val base17 = Board25.bases.first { it.id == 17 }
        assertEquals(listOf(c(1), c(2)), base17.cExit)
        assertEquals(listOf(d(1), d(2)), base17.dInterior)
        assertEquals(listOf(e(1), e(2)), base17.eTips)

        val base20 = Board25.bases.first { it.id == 20 }
        assertEquals(listOf(c(7), c(8)), base20.cExit)
        assertEquals(listOf(d(10), d(11)), base20.dInterior)
        assertEquals(listOf(e(7), e(8)), base20.eTips)
    }

    @Test
    fun bases_exitOnlyViaRadialDToC() {
        // Cada D interior de una base conecta con su C de salida correspondiente (misma posición
        // en el par). Es la única ruta base → campo.
        Board25.bases.forEach { base ->
            base.dInterior.forEachIndexed { idx, dVertex ->
                val expectedC = base.cExit[idx]
                assertTrue(
                    "Base ${base.id}: ${dVertex.name} debe salir a ${expectedC.name}",
                    Board25.isAdjacent(dVertex, expectedC),
                )
            }
        }
    }

    @Test
    fun bases_squareIsClosedLoop() {
        // El cuadrado {E,E,D,D} de cada base es un ciclo: E1-E2, E2-D2, D2-D1, D1-E1.
        Board25.bases.forEach { base ->
            val (d1, d2) = base.dInterior
            val (e1, e2) = base.eTips
            assertTrue("Base ${base.id}: ${e1.name}-${e2.name}", Board25.isAdjacent(e1, e2))
            assertTrue("Base ${base.id}: ${e1.name}-${d1.name}", Board25.isAdjacent(e1, d1))
            assertTrue("Base ${base.id}: ${e2.name}-${d2.name}", Board25.isAdjacent(e2, d2))
            assertTrue("Base ${base.id}: ${d1.name}-${d2.name}", Board25.isAdjacent(d1, d2))
        }
    }

    // ---- Automorfismo de rotación 60° (§3.6) — test más fuerte ----

    @Test
    fun rotate60_isVertexPermutation() {
        val image = Board25.vertices.map { Board25.rotate60(it) }
        assertEquals("La rotación mapea vértices en vértices, biyectivamente", 49, image.toSet().size)
        assertEquals(Board25.vertices.toSet(), image.toSet())
    }

    @Test
    fun rotate60_appliedSixTimes_isIdentity() {
        Board25.vertices.forEach { vertex ->
            var image = vertex
            repeat(6) { image = Board25.rotate60(image) }
            assertEquals("R^6 debe ser la identidad en ${vertex.name}", vertex, image)
        }
    }

    // ---- Regiones (áreas para paletas) ----

    @Test
    fun regions_haveExpectedCounts() {
        assertEquals("6 triángulos centrales", 6, Board25.centralRegions.size)
        assertEquals("12 regiones de circunferencia", 12, Board25.circumferenceRegions.size)
        assertEquals("6 bandas C→D", 6, Board25.bandRegions.size)
        assertEquals("6 cuadrados de base", 6, Board25.baseSquareRegions.size)
        assertEquals("6 triángulos puntiagudos de conector", 6, Board25.connectorTipRegions.size)
        assertEquals("12 strips laterales de conector", 12, Board25.connectorSideRegions.size)
    }

    @Test
    fun regions_referenceKnownVertices() {
        val known = Board25.vertices.toSet()
        val all = Board25.centralRegions + Board25.circumferenceRegions +
            Board25.bandRegions + Board25.baseSquareRegions +
            Board25.connectorTipRegions + Board25.connectorSideRegions
        all.forEach { region ->
            assertTrue("Región con ≥3 vértices", region.vertices.size >= 3)
            region.vertices.forEach { vertex ->
                assertTrue("Vértice de región desconocido: ${vertex.name}", vertex in known)
            }
        }
    }

    @Test
    fun externalBoundary_isAClosedRealPath() {
        val boundary = Board25.externalBoundary
        assertEquals("El contorno tiene 30 vértices (12 E + 18 D)", 30, boundary.size)
        assertEquals("Sin vértices repetidos", boundary.size, boundary.toSet().size)
        // Solo puntas E y anillo D forman el contorno.
        assertTrue(
            "El contorno son solo vértices E y D",
            boundary.all { it.zone == Board25.EDGE || it.zone == Board25.DOMESTIC },
        )
        // Cada par consecutivo (cíclico) es una arista real del tablero.
        boundary.indices.forEach { i ->
            val a = boundary[i]
            val b = boundary[(i + 1) % boundary.size]
            assertTrue("Arista de contorno real: ${a.name}-${b.name}", Board25.isAdjacent(a, b))
        }
    }

    @Test
    fun rotate60_isGraphAutomorphism() {
        val edgeSet = Board25.edges.toSet()
        Board25.edges.forEach { edge ->
            val rotated = Edge(Board25.rotate60(edge.from) to Board25.rotate60(edge.to))
            assertTrue(
                "La arista rotada ${rotated.name} debe existir en el grafo",
                rotated in edgeSet,
            )
        }
    }
}
