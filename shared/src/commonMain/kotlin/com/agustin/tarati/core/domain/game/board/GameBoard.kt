package com.agustin.tarati.core.domain.game.board

import com.agustin.tarati.core.domain.game.board.GameBoard.REFERENCE_BOARD_SIZE
import com.agustin.tarati.core.domain.game.board.GameBoard.computeIsForwardMove
import com.agustin.tarati.core.domain.game.board.GameBoard.edges
import com.agustin.tarati.core.domain.game.board.GameBoard.forwardTargets
import com.agustin.tarati.core.domain.game.board.GameBoard.logicalPositions
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estructura lógica del tablero de Tarati.
 * Contiene la definición de vertices, edges, regiones y reglas del juego.
 *
 * NO contiene código de renderizado visual (sin dependencias de Compose).
 */
object GameBoard {
    // ========== Constantes de Juego ==========

    /** Umbral en unidades de referencia para determinar si un movimiento es "hacia adelante" */
    private const val FORWARD_MOVE_THRESHOLD = 10f

    /**
     * Tamaño de referencia sobre el que se calculan las [logicalPositions].
     * `BoardGeometry` lo usa para normalizarlas a 0..1.
     */
    const val REFERENCE_BOARD_SIZE: Float = 1100f

    /** Ancho de vértice en unidades de referencia */
    private const val VERTEX_WIDTH = 250f

    // ========== Zones ==========

    val ABSOLUTE: Zone = Zone('A')
    val BRIDGE: Zone = Zone('B')
    val CIRCUMFERENCE: Zone = Zone('C')
    val DOMESTIC: Zone = Zone('D')

    // ========== Vertex Collections ==========

    val A1: Vertex = Vertex(ABSOLUTE, 1)

    val bridgeVertices: List<Vertex> = (1..6).map { Vertex(BRIDGE, it) }
    val circumferenceVertices: List<Vertex> = (1..12).map { Vertex(CIRCUMFERENCE, it) }
    val domesticVertices: List<Vertex> = (1..4).map { Vertex(DOMESTIC, it) }

    // Named vertices for convenience
    val B1: Vertex get() = bridgeVertices[0]
    val B2: Vertex get() = bridgeVertices[1]
    val B3: Vertex get() = bridgeVertices[2]
    val B4: Vertex get() = bridgeVertices[3]
    val B5: Vertex get() = bridgeVertices[4]
    val B6: Vertex get() = bridgeVertices[5]

    val C1: Vertex get() = circumferenceVertices[0]
    val C2: Vertex get() = circumferenceVertices[1]
    val C3: Vertex get() = circumferenceVertices[2]
    val C4: Vertex get() = circumferenceVertices[3]
    val C5: Vertex get() = circumferenceVertices[4]
    val C6: Vertex get() = circumferenceVertices[5]
    val C7: Vertex get() = circumferenceVertices[6]
    val C8: Vertex get() = circumferenceVertices[7]
    val C9: Vertex get() = circumferenceVertices[8]
    val C10: Vertex get() = circumferenceVertices[9]
    val C11: Vertex get() = circumferenceVertices[10]
    val C12: Vertex get() = circumferenceVertices[11]

    val D1: Vertex get() = domesticVertices[0]
    val D2: Vertex get() = domesticVertices[1]
    val D3: Vertex get() = domesticVertices[2]
    val D4: Vertex get() = domesticVertices[3]

    // All vertices
    val centerVertices: List<Vertex> = listOf(A1) + bridgeVertices
    val vertices: List<Vertex> = centerVertices + circumferenceVertices + domesticVertices

    val externalBoundary: List<Vertex> =
        listOf(C1, D1, D2, C2, C3, C4, C5, C6, C7, D3, D4, C8, C9, C10, C11, C12)

    // ========== Edge Definitions ==========

    private val whiteDomesticEdges: List<Edge> =
        listOf(
            Edge(D1 to D2),
            Edge(D1 to C1),
            Edge(D2 to C2),
        )

    private val blackDomesticEdges: List<Edge> =
        listOf(
            Edge(D3 to D4),
            Edge(D3 to C7),
            Edge(D4 to C8),
        )

    val domesticEdges: List<Edge> = whiteDomesticEdges + blackDomesticEdges

    val bridgeEdges: List<Edge> =
        (0..5).map { index ->
            Edge(bridgeVertices[index] to bridgeVertices[(index + 1) % 6])
        }

    val circumferenceEdges: List<Edge> =
        (0..11).map { index ->
            Edge(circumferenceVertices[index] to circumferenceVertices[(index + 1) % 12])
        }

    val bridgeToCircumferenceEdges: List<Edge> =
        listOf(
            Edge(C1 to B1),
            Edge(C2 to B1),
            Edge(C3 to B2),
            Edge(C4 to B2),
            Edge(C5 to B3),
            Edge(C6 to B3),
            Edge(C7 to B4),
            Edge(C8 to B4),
            Edge(C9 to B5),
            Edge(C10 to B5),
            Edge(C11 to B6),
            Edge(C12 to B6),
        )

    val absoluteCenterToBridgeEdges: List<Edge> = bridgeVertices.map { Edge(it to A1) }

    val edges: List<Edge> =
        domesticEdges + bridgeEdges + circumferenceEdges + bridgeToCircumferenceEdges + absoluteCenterToBridgeEdges

    // ========== Game Areas ==========

    val homeBases: Map<CobColor, List<Vertex>> =
        mapOf(
            CobColor.WHITE to listOf(C1, C2, D1, D2),
            CobColor.BLACK to listOf(C7, C8, D3, D4),
        )

    /**
     * Vertices where cobs are promoted to roks when advanced onto them.
     *
     * Per the patent: "A cob piece is promoted to a rok piece when it is advanced onto
     * an opponent's home-base stopping point." The opponent's home base has four stopping
     * points (C-ring + D-ring), so ALL four trigger promotion on arrival via forward move.
     *
     * The "dead cob" concept is orthogonal: a cob that ARRIVES at a D-ring vertex via
     * capture (flip) is never passed through upgradeIfInEnemyBase — it stays as a cob
     * and is immediately dead because there are no forward moves from D3/D4 (for white)
     * or D1/D2 (for black). A cob that MOVES forward onto a D-ring vertex is promoted
     * immediately and becomes a rok, which is never dead.
     */
    val upgradeVertices: Map<CobColor, List<Vertex>> =
        mapOf(
            CobColor.WHITE to listOf(C7, C8, D3, D4),
            CobColor.BLACK to listOf(C1, C2, D1, D2),
        )

    /**
     * Vertices where a cob of the given color is immediately dead and cannot advance.
     * These are the D-ring (outermost) vertices of the opponent's home base.
     * A cob can only reach these via capture (flip), never via forward movement.
     */
    val deadVertices: Map<CobColor, List<Vertex>> =
        mapOf(
            CobColor.WHITE to listOf(D3, D4),
            CobColor.BLACK to listOf(D1, D2),
        )

    // ========== Regions ==========

    val centralRegions: List<Region> =
        (0..5).map { index ->
            Region(listOf(A1, bridgeVertices[index], bridgeVertices[(index + 1) % 6]))
        }

    val circumferenceRegions: List<Region> =
        listOf(
            Region(listOf(B1, C1, C2)),
            Region(listOf(B1, C2, C3, B2)),
            Region(listOf(B2, C3, C4)),
            Region(listOf(B2, C4, C5, B3)),
            Region(listOf(B3, C5, C6)),
            Region(listOf(B3, C6, C7, B4)),
            Region(listOf(B4, C7, C8)),
            Region(listOf(B4, C8, C9, B5)),
            Region(listOf(B5, C9, C10)),
            Region(listOf(B5, C10, C11, B6)),
            Region(listOf(B6, C11, C12)),
            Region(listOf(B6, C12, C1, B1)),
        )

    val domesticRegions: List<Region> =
        listOf(
            Region(listOf(C1, C2, D2, D1)),
            Region(listOf(C7, C8, D4, D3)),
        )

    private val allRegions: List<Region> = domesticRegions + centralRegions + circumferenceRegions

    val vertexToRegions: Map<Vertex, List<Region>> by lazy {
        allRegions
            .flatMap { region ->
                region.vertices.map { vertex -> vertex to region }
            }.groupBy({ it.first }, { it.second })
    }

    /**
     * Mapa de adyacencias del tablero, derivado de [edges]. Inmutable hacia
     * afuera — la topología no cambia tras la construcción.
     * CRÍTICO: Usado por el motor de IA para generar movimientos válidos.
     */
    val adjacencyMap: Map<Vertex, List<Vertex>> by lazy {
        val map = mutableMapOf<Vertex, MutableList<Vertex>>()
        edges.forEach { edge ->
            map.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
            map.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
        }
        map
    }

    // ========== Movement Logic ==========

    /**
     * Posición lógica 2D de un vértice (sin depender de Compose).
     */
    data class Position2D(val x: Float, val y: Float)

    /**
     * Posición lógica de cada vértice sobre el tamaño de referencia
     * [REFERENCE_BOARD_SIZE], calculada una única vez. Única fuente de la
     * geometría del tablero: `BoardGeometry` deriva de aquí las posiciones
     * visuales normalizadas y [forwardTargets] la direccionalidad.
     */
    val logicalPositions: Map<Vertex, Position2D> by lazy {
        vertices.associateWith { computeLogicalPosition(it) }
    }

    /** Calcula la posición lógica de un vértice en el tamaño de referencia. */
    private fun computeLogicalPosition(vertex: Vertex): Position2D {
        val center = REFERENCE_BOARD_SIZE / 2

        if (vertex == A1) return Position2D(center, center)

        return when (vertex.zone) {
            BRIDGE -> {
                val angle = (vertex.position - 1) * (PI / 3)
                Position2D(
                    x = center + VERTEX_WIDTH * cos(angle + PI / 2).toFloat(),
                    y = center + VERTEX_WIDTH * sin(angle + PI / 2).toFloat(),
                )
            }

            CIRCUMFERENCE -> {
                val angle = (vertex.position - 1) * (PI / 6) - PI / 12 + PI / 2
                val radius = VERTEX_WIDTH * (1 + sqrt(11.0 / 13)).toFloat()
                Position2D(
                    x = center + radius * cos(angle).toFloat(),
                    y = center + radius * sin(angle).toFloat(),
                )
            }

            DOMESTIC -> {
                val connectedC = getConnectedCircumferenceVertex(vertex)
                val baseRadius = VERTEX_WIDTH * (1 + sqrt(11.0 / 13)).toFloat()
                val baseAngle = (connectedC.position - 1) * (PI / 6) - PI / 12 + PI / 2

                val basePos =
                    Position2D(
                        x = center + baseRadius * cos(baseAngle).toFloat(),
                        y = center + baseRadius * sin(baseAngle).toFloat(),
                    )

                val displacement =
                    if (vertex in homeBases[CobColor.WHITE]!!) {
                        Position2D(0f, VERTEX_WIDTH)
                    } else {
                        Position2D(0f, -VERTEX_WIDTH)
                    }

                Position2D(
                    x = basePos.x + displacement.x,
                    y = basePos.y + displacement.y
                )
            }

            else -> Position2D(center, center)
        }
    }

    private fun getConnectedCircumferenceVertex(domesticVertex: Vertex): Vertex =
        domesticEdges
            .filter { it.from == domesticVertex || it.to == domesticVertex }
            .flatMap { listOf(it.from, it.to) }
            .first { it.zone == CIRCUMFERENCE }

    /**
     * Destinos "hacia adelante" por color para cada vértice, precomputados una
     * única vez sobre las aristas del tablero con el predicado geométrico
     * [computeIsForwardMove]. Evita recalcular trigonometría en cada consulta
     * del generador/validador de movimientos (hot path del minimax).
     */
    private val forwardTargets: Map<CobColor, Map<Vertex, Set<Vertex>>> by lazy {
        CobColor.entries.associateWith { color ->
            val targets = mutableMapOf<Vertex, MutableSet<Vertex>>()
            edges.forEach { edge ->
                if (computeIsForwardMove(color, edge.from, edge.to)) {
                    targets.getOrPut(edge.from) { mutableSetOf() }.add(edge.to)
                }
                if (computeIsForwardMove(color, edge.to, edge.from)) {
                    targets.getOrPut(edge.to) { mutableSetOf() }.add(edge.from)
                }
            }
            targets
        }
    }

    /**
     * Determina si un movimiento es "hacia adelante" para un color dado.
     * WHITE avanza hacia arriba (Y decrece), BLACK hacia abajo (Y crece).
     *
     * Consulta la tabla precomputada [forwardTargets], definida sobre las
     * aristas del tablero: un par de vértices no adyacentes nunca es forward.
     */
    fun isForwardMove(
        color: CobColor,
        move: Move,
    ): Boolean = forwardTargets.getValue(color)[move.from]?.contains(move.to) == true

    /** Predicado geométrico usado para construir [forwardTargets]. */
    private fun computeIsForwardMove(
        color: CobColor,
        from: Vertex,
        to: Vertex,
    ): Boolean {
        val fromPos = logicalPositions.getValue(from)
        val toPos = logicalPositions.getValue(to)

        return when (color) {
            CobColor.WHITE -> fromPos.y - toPos.y > FORWARD_MOVE_THRESHOLD
            else -> toPos.y - fromPos.y > FORWARD_MOVE_THRESHOLD
        }
    }

    /**
     * Returns all non-forward adjacent moves available from [from] if it belongs to [color]'s
     * own home base. These moves are only legally playable when they result in at least one
     * capture (see GameState.getHomeBaseMoves for the full validity check).
     */
    fun getHomeBaseNonForwardMoves(
        color: CobColor,
        from: Vertex,
    ): List<Move> {
        val ownBase = homeBases[color] ?: return emptyList()
        if (from !in ownBase) return emptyList()
        return adjacencyMap[from]
            ?.filter { to -> !isForwardMove(color, Move(from to to)) }
            ?.map { to -> Move(from to to) }
            ?: emptyList()
    }

    /**
     * Validates whether [move] is legal in [gameState].
     *
     * Valid move categories:
     * 1. Home-base non-forward moves that produce at least one capture (pre-adjacency rule).
     * 2. Normal forward moves for cobs to an empty adjacent vertex.
     * 3. Any-direction moves for roks to an empty adjacent vertex.
     */
    fun isValidMove(
        gameState: GameState,
        move: Move,
    ): Boolean {
        val cob = gameState.cobs[move.from] ?: return false
        if (cob.color != gameState.currentTurn) return false

        if (gameState.getHomeBaseMoves(move.from, cob).contains(move)) {
            return true
        }

        val isAdjacent = adjacencyMap[move.from]?.contains(move.to) ?: false
        if (!isAdjacent) return false

        return when {
            gameState.cobs.containsKey(move.to) -> false
            !cob.isUpgraded -> isForwardMove(cob.color, move)
            else -> true
        }
    }
}