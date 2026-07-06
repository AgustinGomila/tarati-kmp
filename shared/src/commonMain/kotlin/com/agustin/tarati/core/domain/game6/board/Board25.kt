package com.agustin.tarati.core.domain.game6.board

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.Zone
import com.agustin.tarati.core.domain.game6.board.Board25.adjacencyMap
import com.agustin.tarati.core.domain.game6.board.Board25.edges
import com.agustin.tarati.core.domain.game6.board.Board25.isAdjacent
import com.agustin.tarati.core.domain.game6.board.Board25.neighborsOf
import com.agustin.tarati.core.domain.game6.board.Board25.rotate60

/**
 * Estructura lógica del tablero ampliado `25` del juego multijugador (codename *Tarati Six*).
 *
 * Grafo inmutable de **49 vértices** y **96 aristas**. Embebe el centro A–B–C del tablero `30`
 * de Tarati intacto y le agrega el **anillo `D` de 18 vértices** y las **12 puntas `E`** que
 * forman las **6 bases** (Fig. 5 de la patente).
 *
 * Inventario: `1 (A1) + 6 (B) + 12 (C) + 18 (D) + 12 (E) = 49`.
 *
 * Adyacencia **por-tablero**: este grafo NO usa el singleton global `GameBoard.adjacencyMap` ni
 * `Vertex.isAdjacentTo` (que consulta ese singleton). Toda vecindad se consulta contra
 * [adjacencyMap] / [neighborsOf] / [isAdjacent] de este objeto.
 *
 * No contiene código de renderizado visual (sin dependencias de Compose).
 */
object Board25 : BoardGraph {
    // ========== Zones ==========

    val ABSOLUTE: Zone = Zone('A')
    val BRIDGE: Zone = Zone('B')
    val CIRCUMFERENCE: Zone = Zone('C')

    /** Anillo `D` de 18 vértices (pares de base + conectores entre bases). */
    val DOMESTIC: Zone = Zone('D')

    /** Puntas `E`: los 12 vértices externos que rematan las 6 bases. */
    val EDGE: Zone = Zone('E')

    // ========== Vertex Collections ==========

    val A1: Vertex = Vertex(ABSOLUTE, 1)

    val bridgeVertices: List<Vertex> = (1..6).map { Vertex(BRIDGE, it) }
    val circumferenceVertices: List<Vertex> = (1..12).map { Vertex(CIRCUMFERENCE, it) }
    val domesticVertices: List<Vertex> = (1..18).map { Vertex(DOMESTIC, it) }
    val edgeVertices: List<Vertex> = (1..12).map { Vertex(EDGE, it) }

    override val vertices: List<Vertex> =
        listOf(A1) + bridgeVertices + circumferenceVertices + domesticVertices + edgeVertices

    // Accesores 1-based por zona (uso interno para declarar aristas legibles)
    private fun b(i: Int): Vertex = bridgeVertices[i - 1]
    private fun c(i: Int): Vertex = circumferenceVertices[i - 1]
    private fun d(i: Int): Vertex = domesticVertices[i - 1]
    private fun e(i: Int): Vertex = edgeVertices[i - 1]

    // ========== Edge Definitions ==========

    // ---- Centro A–B–C (idéntico a GameBoard de Tarati) — 36 aristas ----

    /** A1 ↔ cada vértice del hexágono B (6). */
    val absoluteToBridgeEdges: List<Edge> = bridgeVertices.map { Edge(A1 to it) }

    /** Hexágono B (6). */
    val bridgeEdges: List<Edge> =
        (0..5).map { Edge(bridgeVertices[it] to bridgeVertices[(it + 1) % 6]) }

    /** Dodecágono C (12). */
    val circumferenceEdges: List<Edge> =
        (0..11).map { Edge(circumferenceVertices[it] to circumferenceVertices[(it + 1) % 12]) }

    /** Puente C → B (12). */
    val bridgeToCircumferenceEdges: List<Edge> =
        listOf(
            Edge(c(1) to b(1)), Edge(c(2) to b(1)),
            Edge(c(3) to b(2)), Edge(c(4) to b(2)),
            Edge(c(5) to b(3)), Edge(c(6) to b(3)),
            Edge(c(7) to b(4)), Edge(c(8) to b(4)),
            Edge(c(9) to b(5)), Edge(c(10) to b(5)),
            Edge(c(11) to b(6)), Edge(c(12) to b(6)),
        )

    // ---- C ↔ D — 24 aristas ----

    /** Radial de base: conecta cada par C de un sextante con su par D interior (12). */
    val radialCToDEdges: List<Edge> =
        listOf(
            Edge(c(1) to d(1)), Edge(c(2) to d(2)),
            Edge(c(3) to d(4)), Edge(c(4) to d(5)),
            Edge(c(5) to d(7)), Edge(c(6) to d(8)),
            Edge(c(7) to d(10)), Edge(c(8) to d(11)),
            Edge(c(9) to d(13)), Edge(c(10) to d(14)),
            Edge(c(11) to d(16)), Edge(c(12) to d(17)),
        )

    /** Triangulación de conector: cada conector D toca los 2 vértices C que lo flanquean (12). */
    val connectorCToDEdges: List<Edge> =
        listOf(
            Edge(d(3) to c(2)), Edge(d(3) to c(3)),
            Edge(d(6) to c(4)), Edge(d(6) to c(5)),
            Edge(d(9) to c(6)), Edge(d(9) to c(7)),
            Edge(d(12) to c(8)), Edge(d(12) to c(9)),
            Edge(d(15) to c(10)), Edge(d(15) to c(11)),
            Edge(d(18) to c(12)), Edge(d(18) to c(1)),
        )

    // ---- Anillo D (ciclo completo) — 18 aristas ----

    val domesticRingEdges: List<Edge> =
        (0..17).map { Edge(domesticVertices[it] to domesticVertices[(it + 1) % 18]) }

    // ---- D ↔ E (lados de base) — 12 aristas ----

    val domesticToEdgeEdges: List<Edge> =
        listOf(
            Edge(e(1) to d(1)), Edge(e(2) to d(2)),
            Edge(e(3) to d(4)), Edge(e(4) to d(5)),
            Edge(e(5) to d(7)), Edge(e(6) to d(8)),
            Edge(e(7) to d(10)), Edge(e(8) to d(11)),
            Edge(e(9) to d(13)), Edge(e(10) to d(14)),
            Edge(e(11) to d(16)), Edge(e(12) to d(17)),
        )

    // ---- Punta E (arista externa de cada base) — 6 aristas ----

    val edgeTipEdges: List<Edge> =
        listOf(
            Edge(e(1) to e(2)), Edge(e(3) to e(4)), Edge(e(5) to e(6)),
            Edge(e(7) to e(8)), Edge(e(9) to e(10)), Edge(e(11) to e(12)),
        )

    /** Todas las aristas del tablero (96). */
    override val edges: List<Edge> =
        absoluteToBridgeEdges +
                bridgeEdges +
                circumferenceEdges +
                bridgeToCircumferenceEdges +
                radialCToDEdges +
                connectorCToDEdges +
                domesticRingEdges +
                domesticToEdgeEdges +
                edgeTipEdges

    // ========== Bases ==========

    /**
     * Una de las 6 bases (Fig. 5). El cuadrado de partida es `{E, E, D, D}` ([startSquare]);
     * la base conecta con el campo solo por sus 2 vértices D interiores, vía las aristas radiales
     * `D → C` ([cExit]).
     */
    data class Base(
        /** Número de base según Fig. 5 de la patente (17–22). */
        val id: Int,
        /** Dirección aproximada en el tablero: S, SW, NW, N, NE, SE. */
        val direction: String,
        /** Los 2 vértices C de salida al campo (destino de las radiales `D → C`). */
        val cExit: List<Vertex>,
        /** Los 2 vértices D interiores del cuadrado de la base. */
        val dInterior: List<Vertex>,
        /** Las 2 puntas E de la base. */
        val eTips: List<Vertex>,
    ) {
        /** Los 4 puntos de partida (cuadrado `{E, E, D, D}`) donde arrancan los cobs. */
        val startSquare: List<Vertex> get() = dInterior + eTips
    }

    /** Las 6 bases, en orden geométrico alrededor del tablero (§3.3 del plan). */
    val bases: List<Base> =
        listOf(
            Base(17, "S", listOf(c(1), c(2)), listOf(d(1), d(2)), listOf(e(1), e(2))),
            Base(22, "SW", listOf(c(3), c(4)), listOf(d(4), d(5)), listOf(e(3), e(4))),
            Base(21, "NW", listOf(c(5), c(6)), listOf(d(7), d(8)), listOf(e(5), e(6))),
            Base(20, "N", listOf(c(7), c(8)), listOf(d(10), d(11)), listOf(e(7), e(8))),
            Base(19, "NE", listOf(c(9), c(10)), listOf(d(13), d(14)), listOf(e(9), e(10))),
            Base(18, "SE", listOf(c(11), c(12)), listOf(d(16), d(17)), listOf(e(11), e(12))),
        )

    private val baseByIdMap: Map<Int, Base> by lazy { bases.associateBy { it.id } }

    /** La base cuyo número Fig. 5 es [id] (17–22). */
    fun baseById(id: Int): Base =
        baseByIdMap[id] ?: throw IllegalArgumentException("No existe la base $id")

    // ========== Regions (áreas del tablero, para el sombreado con paletas) ==========

    private fun cc(index0: Int): Vertex = circumferenceVertices[index0 % 12]
    private fun dd(index0: Int): Vertex = domesticVertices[index0 % 18]

    /** Triángulos centrales `A1–Bk–Bk+1` (6) — idénticos a Tarati. */
    val centralRegions: List<Region> =
        (0 until 6).map { k -> Region(listOf(A1, bridgeVertices[k], bridgeVertices[(k + 1) % 6])) }

    /** Anillo B–C: triángulos y cuadros alternados (12) — idéntico a Tarati. */
    val circumferenceRegions: List<Region> =
        listOf(
            Region(listOf(b(1), c(1), c(2))),
            Region(listOf(b(1), c(2), c(3), b(2))),
            Region(listOf(b(2), c(3), c(4))),
            Region(listOf(b(2), c(4), c(5), b(3))),
            Region(listOf(b(3), c(5), c(6))),
            Region(listOf(b(3), c(6), c(7), b(4))),
            Region(listOf(b(4), c(7), c(8))),
            Region(listOf(b(4), c(8), c(9), b(5))),
            Region(listOf(b(5), c(9), c(10))),
            Region(listOf(b(5), c(10), c(11), b(6))),
            Region(listOf(b(6), c(11), c(12))),
            Region(listOf(b(6), c(12), c(1), b(1))),
        )

    /** Bandas radiales C→D de cada base (cuadrilátero, 6). */
    val bandRegions: List<Region> =
        (0 until 6).map { k -> Region(listOf(cc(2 * k), cc(2 * k + 1), dd(3 * k + 1), dd(3 * k))) }

    /** Cuadrados de base `{D, D, E, E}` (6). */
    val baseSquareRegions: List<Region> =
        (0 until 6).map { k ->
            Region(listOf(dd(3 * k), dd(3 * k + 1), edgeVertices[2 * k + 1], edgeVertices[2 * k]))
        }

    // Zona del conector entre bases: fan de 3 triángulos desde el propio conector (D). Así todas
    // las aristas de las regiones son aristas reales del tablero (radial `C→D`, anillo `D`,
    // triangulación `D→C`, cuerda `C→C`) y no aparecen diagonales espurias al pintar los bordes.

    /** Triángulo puntiagudo del conector `Ck–Dconn–Ck+1` (6) — cuerda C + dos aristas de conector. */
    val connectorTipRegions: List<Region> =
        (0 until 6).map { k ->
            Region(listOf(cc(2 * k + 1), dd(3 * k + 2), cc(2 * k + 2)))
        }

    /** Strips laterales del conector hacia cada base adyacente (12). */
    val connectorSideRegions: List<Region> =
        (0 until 6).flatMap { k ->
            val cInner = cc(2 * k + 1)   // C interior del sextante
            val cNext = cc(2 * k + 2)    // primer C de la base siguiente
            val dBase2 = dd(3 * k + 1)   // D2 del sextante
            val dConn = dd(3 * k + 2)    // conector
            val dNext = dd(3 * k + 3)    // D1 de la base siguiente
            listOf(
                Region(listOf(cInner, dBase2, dConn)),
                Region(listOf(cNext, dConn, dNext)),
            )
        }

    /**
     * Contorno externo del tablero (30 vértices), en orden para trazar el perímetro: por cada base,
     * sus 2 puntas `E` (arista exterior), luego baja por el riel a `D`, recorre el anillo D pasando
     * por el conector y sube al riel de la base siguiente. Todas las aristas consecutivas son reales
     * (punta E, lado `E→D`, anillo `D`).
     */
    val externalBoundary: List<Vertex> =
        (0 until 6).flatMap { k ->
            listOf(
                edgeVertices[2 * k],          // E punta 1
                edgeVertices[2 * k + 1],      // E punta 2
                domesticVertices[3 * k + 1],  // D2 de la base
                domesticVertices[3 * k + 2],  // conector
                domesticVertices[(3 * k + 3) % 18], // D1 de la base siguiente
            )
        }

    // ========== Adjacency (por-tablero) ==========

    /**
     * Mapa de adyacencias propio de este tablero, derivado de [edges].
     * NO comparte estado con `GameBoard.adjacencyMap`.
     */
    override val adjacencyMap: Map<Vertex, List<Vertex>> by lazy {
        val map = mutableMapOf<Vertex, MutableList<Vertex>>()
        edges.forEach { edge ->
            map.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
            map.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
        }
        map
    }

    /** Vecinos de [vertex] en este tablero (lista vacía si no existe). */
    override fun neighborsOf(vertex: Vertex): List<Vertex> = adjacencyMap[vertex] ?: emptyList()

    /** `true` si [a] y [b] están conectados por una arista de este tablero. */
    override fun isAdjacent(a: Vertex, b: Vertex): Boolean = adjacencyMap[a]?.contains(b) == true

    // ========== Symmetry ==========

    /** Rota un índice 1-based [i] en [step] posiciones dentro de un ciclo de tamaño [n]. */
    private fun rot(i: Int, step: Int, n: Int): Int = ((i - 1 + step) % n) + 1

    /**
     * Rotación de 60° `R` del tablero (un sextante): `A1→A1`, `Bi→Bi+1`, `Ci→Ci+2`, `Di→Di+3`,
     * `Ei→Ei+2`. Es un automorfismo del grafo (mapea el conjunto de aristas en sí mismo) y el
     * generador del grupo de simetría rotacional de orden 6.
     */
    fun rotate60(vertex: Vertex): Vertex =
        when (vertex.zone) {
            ABSOLUTE -> A1
            BRIDGE -> b(rot(vertex.position, 1, 6))
            CIRCUMFERENCE -> c(rot(vertex.position, 2, 12))
            DOMESTIC -> d(rot(vertex.position, 3, 18))
            EDGE -> e(rot(vertex.position, 2, 12))
            else -> vertex
        }

    /**
     * La base (17–22) a la que se mapea la base [id] bajo la rotación de 60° [rotate60]. Se deriva
     * rotando un vértice interior de cada base y localizando la base que lo contiene, de modo que la
     * rotación de asientos quede consistente con la de las piezas.
     */
    fun rotatedBaseId(id: Int): Int = rotatedBaseIdMap.getValue(id)

    private val rotatedBaseIdMap: Map<Int, Int> by lazy {
        bases.associate { base ->
            val rotated = rotate60(base.dInterior.first())
            base.id to bases.first { rotated in it.dInterior }.id
        }
    }
}
