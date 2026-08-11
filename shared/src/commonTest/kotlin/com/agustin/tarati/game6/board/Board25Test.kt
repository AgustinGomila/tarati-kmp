package com.agustin.tarati.game6.board

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        assertEquals(49, Board25.vertices.size, "El tablero 25 tiene 49 vértices")
        assertEquals(1, listOf(Board25.A1).size)
        assertEquals(6, Board25.bridgeVertices.size)
        assertEquals(12, Board25.circumferenceVertices.size)
        assertEquals(18, Board25.domesticVertices.size)
        assertEquals(12, Board25.edgeVertices.size)
    }

    @Test
    fun vertices_areUnique() {
        assertEquals(
            Board25.vertices.size,
            Board25.vertices.toSet().size,
            "No debe haber vértices duplicados",
        )
    }

    @Test
    fun edgeCount_is96() {
        assertEquals(96, Board25.edges.size, "El tablero 25 tiene 96 aristas")
    }

    @Test
    fun edges_areUniqueAndUndirected() {
        // Edge.equals es no dirigido: un duplicado (aunque esté invertido) colapsaría en el Set.
        assertEquals(
            Board25.edges.size,
            Board25.edges.toSet().size,
            "No debe haber aristas duplicadas (ni repetidas ni invertidas)",
        )
    }

    @Test
    fun edges_haveNoSelfLoops() {
        Board25.edges.forEach { edge ->
            assertNotEquals(edge.from, edge.to, "Arista con self-loop: ${edge.name}")
        }
    }

    @Test
    fun edges_referenceKnownVertices() {
        val known = Board25.vertices.toSet()
        Board25.edges.forEach { edge ->
            assertTrue(edge.from in known, "Vértice desconocido: ${edge.from.name}")
            assertTrue(edge.to in known, "Vértice desconocido: ${edge.to.name}")
        }
    }

    // ---- Consistencia del grafo ----

    @Test
    fun adjacencyMap_containsAllVertices() {
        Board25.vertices.forEach { vertex ->
            assertTrue(
                Board25.adjacencyMap.containsKey(vertex),
                "El mapa de adyacencia debe contener a ${vertex.name}",
            )
        }
    }

    @Test
    fun adjacencyMap_isBidirectional() {
        Board25.edges.forEach { edge ->
            assertTrue(
                Board25.isAdjacent(edge.from, edge.to),
                "${edge.from.name} debe conectar con ${edge.to.name}",
            )
            assertTrue(
                Board25.isAdjacent(edge.to, edge.from),
                "${edge.to.name} debe conectar con ${edge.from.name}",
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

        assertEquals(6, degree(Board25.A1), "A1 tiene grado 6")
        Board25.bridgeVertices.forEach { assertEquals(5, degree(it), "${it.name} grado 5") }
        Board25.circumferenceVertices.forEach { assertEquals(5, degree(it), "${it.name} grado 5") }
        Board25.domesticVertices.forEach { assertEquals(4, degree(it), "${it.name} grado 4") }
        Board25.edgeVertices.forEach { assertEquals(2, degree(it), "${it.name} grado 2") }
    }

    @Test
    fun sumOfDegrees_isTwiceEdgeCount() {
        val sum = Board25.vertices.sumOf { Board25.neighborsOf(it).size }
        assertEquals(2 * Board25.edges.size, sum, "Handshake lemma: Σ grados = 2·|E|")
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
        assertEquals(6, Board25.bases.size, "Hay 6 bases")
        Board25.bases.forEach { base ->
            assertEquals(4, base.startSquare.size, "Base ${base.id}: cuadrado de 4 puntos")
            assertEquals(2, base.cExit.size, "Base ${base.id}: 2 salidas C")
            assertEquals(2, base.dInterior.size, "Base ${base.id}: 2 D interiores")
            assertEquals(2, base.eTips.size, "Base ${base.id}: 2 puntas E")
        }
    }

    @Test
    fun bases_coverAllEdgeVerticesDisjointly() {
        val allTips = Board25.bases.flatMap { it.eTips }
        assertEquals(12, allTips.size, "Las 12 puntas E pertenecen a exactamente una base")
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
                    Board25.isAdjacent(dVertex, expectedC),
                    "Base ${base.id}: ${dVertex.name} debe salir a ${expectedC.name}",
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
            assertTrue(Board25.isAdjacent(e1, e2), "Base ${base.id}: ${e1.name}-${e2.name}")
            assertTrue(Board25.isAdjacent(e1, d1), "Base ${base.id}: ${e1.name}-${d1.name}")
            assertTrue(Board25.isAdjacent(e2, d2), "Base ${base.id}: ${e2.name}-${d2.name}")
            assertTrue(Board25.isAdjacent(d1, d2), "Base ${base.id}: ${d1.name}-${d2.name}")
        }
    }

    // ---- Automorfismo de rotación 60° (§3.6) — test más fuerte ----

    @Test
    fun rotate60_isVertexPermutation() {
        val image = Board25.vertices.map { Board25.rotate60(it) }
        assertEquals(49, image.toSet().size, "La rotación mapea vértices en vértices, biyectivamente")
        assertEquals(Board25.vertices.toSet(), image.toSet())
    }

    @Test
    fun rotate60_appliedSixTimes_isIdentity() {
        Board25.vertices.forEach { vertex ->
            var image = vertex
            repeat(6) { image = Board25.rotate60(image) }
            assertEquals(vertex, image, "R^6 debe ser la identidad en ${vertex.name}")
        }
    }

    // ---- Regiones (áreas para paletas) ----

    @Test
    fun regions_haveExpectedCounts() {
        assertEquals(6, Board25.centralRegions.size, "6 triángulos centrales")
        assertEquals(12, Board25.circumferenceRegions.size, "12 regiones de circunferencia")
        assertEquals(6, Board25.bandRegions.size, "6 bandas C→D")
        assertEquals(6, Board25.baseSquareRegions.size, "6 cuadrados de base")
        assertEquals(6, Board25.connectorTipRegions.size, "6 triángulos puntiagudos de conector")
        assertEquals(12, Board25.connectorSideRegions.size, "12 strips laterales de conector")
    }

    @Test
    fun regions_referenceKnownVertices() {
        val known = Board25.vertices.toSet()
        val all = Board25.centralRegions + Board25.circumferenceRegions +
                Board25.bandRegions + Board25.baseSquareRegions +
                Board25.connectorTipRegions + Board25.connectorSideRegions
        all.forEach { region ->
            assertTrue(region.vertices.size >= 3, "Región con ≥3 vértices")
            region.vertices.forEach { vertex ->
                assertTrue(vertex in known, "Vértice de región desconocido: ${vertex.name}")
            }
        }
    }

    @Test
    fun externalBoundary_isAClosedRealPath() {
        val boundary = Board25.externalBoundary
        assertEquals(30, boundary.size, "El contorno tiene 30 vértices (12 E + 18 D)")
        assertEquals(boundary.size, boundary.toSet().size, "Sin vértices repetidos")
        // Solo puntas E y anillo D forman el contorno.
        assertTrue(
            boundary.all { it.zone == Board25.EDGE || it.zone == Board25.DOMESTIC },
            "El contorno son solo vértices E y D",
        )
        // Cada par consecutivo (cíclico) es una arista real del tablero.
        boundary.indices.forEach { i ->
            val a = boundary[i]
            val b = boundary[(i + 1) % boundary.size]
            assertTrue(Board25.isAdjacent(a, b), "Arista de contorno real: ${a.name}-${b.name}")
        }
    }

    @Test
    fun rotate60_isGraphAutomorphism() {
        val edgeSet = Board25.edges.toSet()
        Board25.edges.forEach { edge ->
            val rotated = Edge(Board25.rotate60(edge.from) to Board25.rotate60(edge.to))
            assertTrue(
                rotated in edgeSet,
                "La arista rotada ${rotated.name} debe existir en el grafo",
            )
        }
    }
}
