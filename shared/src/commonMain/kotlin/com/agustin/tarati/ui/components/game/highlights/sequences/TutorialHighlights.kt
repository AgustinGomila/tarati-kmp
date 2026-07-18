package com.agustin.tarati.ui.components.game.highlights.sequences

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.board.GameBoard.absoluteCenterToBridgeEdges
import com.agustin.tarati.core.domain.game.board.GameBoard.bridgeEdges
import com.agustin.tarati.core.domain.game.board.GameBoard.bridgeToCircumferenceEdges
import com.agustin.tarati.core.domain.game.board.GameBoard.bridgeVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.circumferenceEdges
import com.agustin.tarati.core.domain.game.board.GameBoard.circumferenceVertices
import com.agustin.tarati.ui.components.game.highlights.HighlightAction
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import com.agustin.tarati.ui.components.game.highlights.base.VertexHighlight
import com.agustin.tarati.ui.components.game.highlights.createArrowAnimation
import com.agustin.tarati.ui.components.game.highlights.createElectricEdgeAnimation
import com.agustin.tarati.ui.components.game.highlights.createVertexAnimation

fun createCenterAnimations(duration: Long = 3200L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 8

    // Secuencia 1: Centro Absoluto A
    sequences.add(
        listOf(
            createVertexAnimation(
                vertex = A1,
                duration = duration / 2,
                persistent = true,
                pulse = true,
            ),
        ),
    )

    // Secuencia 2: Pausa
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Secuencia 3: Aristas centrales en secuencia
    absoluteCenterToBridgeEdges.forEachIndexed { index, edge ->
        sequences.add(
            listOf(
                createElectricEdgeAnimation(
                    edge = edge,
                    duration = duration / 4,
                    startDelay = baseDelay * index,
                    pulse = true,
                ),
            ),
        )
    }

    // Secuencia 4: Pausa final antes de vértices del puente
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Secuencia 5: Vértices del puente con delays escalonados
    val bridgeVerticesAnimations =
        bridgeVertices.mapIndexed { index, vertex ->
            createVertexAnimation(
                vertex = vertex,
                duration = duration / 4,
                startDelay = baseDelay * index,
                pulse = true,
            )
        }
    sequences.add(bridgeVerticesAnimations)

    return sequences
}

fun createBridgeAnimations(duration: Long = 2200L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val vertexDelay = duration / bridgeVertices.size
    val edgeDelay = duration / bridgeEdges.size

    // Vértices del puente uno por uno
    bridgeVertices.forEachIndexed { index, vertex ->
        sequences.add(
            listOf(
                createVertexAnimation(
                    vertex = vertex,
                    duration = duration / 2,
                    startDelay = vertexDelay * index,
                    pulse = true,
                ),
            ),
        )
    }

    // Pausa entre vértices y aristas
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Aristas del puente una por una
    bridgeEdges.forEachIndexed { index, edge ->
        sequences.add(
            listOf(
                createElectricEdgeAnimation(
                    edge = edge,
                    duration = duration / 2,
                    startDelay = edgeDelay * index,
                    pulse = true,
                ),
            ),
        )
    }

    // Pausa antes de mostrar las conexiones radiales y con la circunferencia
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Todas las aristas A1↔Bx simultáneamente — rayos desde el centro absoluto
    sequences.add(
        absoluteCenterToBridgeEdges.map { edge ->
            createElectricEdgeAnimation(
                edge = edge,
                duration = (duration * 1.2).toLong(),
                pulse = true,
            )
        },
    )

    // Pausa antes de mostrar conexiones con la circunferencia
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Por cada vértice del puente, sus 2 conexiones con la circunferencia en secuencia.
    // bridgeToCircumferenceEdges está ordenado de a pares: (C1,B1), (C2,B1), (C3,B2)…
    // Se agrupa en chunks de 2 para mostrar ambas conexiones de cada Bx juntas.
    bridgeToCircumferenceEdges.chunked(2).forEach { pair ->
        sequences.add(listOf(HighlightAnimation.Pause()))
        sequences.add(
            pair.map { edge ->
                createElectricEdgeAnimation(
                    edge = edge,
                    duration = (duration * 0.8).toLong(),
                    pulse = true,
                )
            },
        )
    }

    return sequences
}

fun createCircumferenceAnimations(duration: Long = 2800L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val vertexDelay = duration / circumferenceVertices.size
    val edgeDelay = duration / circumferenceEdges.size

    // Vértices de la circunferencia, uno por uno en secuencia
    circumferenceVertices.forEachIndexed { index, vertex ->
        sequences.add(
            listOf(
                createVertexAnimation(
                    vertex = vertex,
                    duration = duration / 3,
                    startDelay = vertexDelay * index,
                    pulse = true,
                ),
            ),
        )
    }

    // Pausa entre vértices y aristas
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Aristas de la circunferencia una por una
    circumferenceEdges.forEachIndexed { index, edge ->
        sequences.add(
            listOf(
                createElectricEdgeAnimation(
                    edge = edge,
                    duration = duration / 2,
                    startDelay = edgeDelay * index,
                    pulse = true,
                ),
            ),
        )
    }

    // Todas las aristas C→B simultáneamente como rayos
    sequences.add(
        bridgeToCircumferenceEdges.map { edge ->
            createElectricEdgeAnimation(
                edge = edge,
                duration = (duration * 1.5).toLong(),
                pulse = true,
            )
        },
    )

    return sequences
}

fun createDomesticAnimations(duration: Long = 4000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 16

    // Base blanca - vértices simultáneos con delays escalonados
    sequences.add(
        listOf(
            createVertexAnimation(D1, duration / 2, baseDelay * 1, true),
            createVertexAnimation(D2, duration / 2, baseDelay * 2, true),
            createVertexAnimation(C1, duration / 2, baseDelay * 3, true),
            createVertexAnimation(C2, duration / 2, baseDelay * 4, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    // Conexiones base blanca - simultáneas con delays
    sequences.add(
        listOf(
            createElectricEdgeAnimation(Edge(D1 to D2), duration / 2, baseDelay * 5, true),
            createElectricEdgeAnimation(Edge(D2 to C2), duration / 2, baseDelay * 6, true),
            createElectricEdgeAnimation(Edge(C2 to C1), duration / 2, baseDelay * 7, true),
            createElectricEdgeAnimation(Edge(C1 to D1), duration / 2, baseDelay * 8, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    // Base negra - vértices simultáneos con delays escalonados
    sequences.add(
        listOf(
            createVertexAnimation(D3, duration / 2, baseDelay * 9, true),
            createVertexAnimation(D4, duration / 2, baseDelay * 10, true),
            createVertexAnimation(C7, duration / 2, baseDelay * 11, true),
            createVertexAnimation(C8, duration / 2, baseDelay * 12, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    // Conexiones base negra - simultáneas con delays
    sequences.add(
        listOf(
            createElectricEdgeAnimation(Edge(D3 to D4), duration / 2, baseDelay * 13, true),
            createElectricEdgeAnimation(Edge(D4 to C8), duration / 2, baseDelay * 14, true),
            createElectricEdgeAnimation(Edge(C8 to C7), duration / 2, baseDelay * 15, true),
            createElectricEdgeAnimation(Edge(C7 to D3), duration / 2, baseDelay * 16, true),
        ),
    )

    return sequences
}

/**
 * Step 6 — Las Piezas.
 *
 * Highlights each of the 4 starting white cobs (C1, C2, D1, D2) sequentially
 * using the CAPTURE action, which produces the "threatened piece" pulsing effect.
 *
 * Each cob is its own sequence in the queue. The system waits `duration` ms per
 * group before moving to the next, so `startDelay` must not be used here — it
 * would make the highlight start after the system has already moved on.
 *
 * An initial Pause gives the board time to render before the first cob lights up,
 * preventing the first highlight from being missed on slower devices.
 */
fun createCobsAnimations(duration: Long = 2000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 4

    // Initial pause: ensures the board is fully rendered before the first highlight fires.
    sequences.add(listOf(HighlightAnimation.Pause(200L)))

    // Each cob in its own sequence — no startDelay, the queue handles ordering.
    listOf(C1, C2, D1, D2).forEachIndexed { index, vertex ->
        sequences.add(
            listOf(
                HighlightAnimation.Vertex(
                    VertexHighlight(
                        vertex = vertex,
                        pulse = true,
                        startDelay = baseDelay * index,
                        duration = duration,
                        action = HighlightAction.CAPTURE,
                    ),
                ),
            ),
        )
        sequences.add(listOf(HighlightAnimation.Pause(200L)))
    }

    return sequences
}

fun createMoveAnimations(duration: Long = 3000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 12

    sequences.add(listOf(createVertexAnimation(C1, duration / 3, baseDelay * 1, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createArrowAnimation(Edge(C1 to B1), startDelay = baseDelay * 2, pulse = true),
            createArrowAnimation(Edge(C1 to C12), startDelay = baseDelay * 2, pulse = true),
        ),
    )

    sequences.add(
        listOf(
            createVertexAnimation(B1, duration / 3, baseDelay * 3, true),
            createVertexAnimation(C12, duration / 3, baseDelay * 3, true),
        ),
    )

    sequences.add(listOf(createVertexAnimation(C2, duration / 3, baseDelay * 4, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createArrowAnimation(Edge(C2 to B1), startDelay = baseDelay * 5, pulse = true),
            createArrowAnimation(Edge(C2 to C3), startDelay = baseDelay * 5, pulse = true),
        ),
    )

    sequences.add(
        listOf(
            createVertexAnimation(B1, duration / 3, baseDelay * 6, true),
            createVertexAnimation(C3, duration / 3, baseDelay * 6, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createVertexAnimation(B1, duration / 3, baseDelay * 7, true),
            createVertexAnimation(C12, duration / 3, baseDelay * 7, true),
            createVertexAnimation(C3, duration / 3, baseDelay * 7, true),
        ),
    )

    return sequences
}

fun createCaptureAnimations(duration: Long = 2000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 4

    sequences.add(listOf(createVertexAnimation(C1, duration / 2, baseDelay * 1, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createArrowAnimation(Edge(C1 to B1), startDelay = baseDelay * 2, pulse = true),
            createVertexAnimation(B1, duration / 2, baseDelay * 2, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))
    sequences.add(listOf(createVertexAnimation(A1, duration / 2, baseDelay * 3, true)))

    return sequences
}

fun createUpgradeAnimations(duration: Long = 2500L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 6

    sequences.add(listOf(createVertexAnimation(B4, duration / 3, baseDelay * 1, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createArrowAnimation(Edge(B4 to C7), startDelay = baseDelay * 2, pulse = true),
            createVertexAnimation(C7, duration / 3, baseDelay * 2, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createArrowAnimation(Edge(B4 to C8), startDelay = baseDelay * 3, pulse = true),
            createVertexAnimation(C8, duration / 3, baseDelay * 3, true),
        ),
    )

    sequences.add(listOf(HighlightAnimation.Pause()))

    sequences.add(
        listOf(
            createVertexAnimation(C7, duration / 3, baseDelay * 4, true),
            createVertexAnimation(C8, duration / 3, baseDelay * 4, true),
        ),
    )

    return sequences
}

/**
 * Animation for the pre-adjacency rule tutorial step.
 *
 * Board: White at C3, Black at C4 (pre-adjacent — will NOT be captured),
 *        Black at B3 (not pre-adjacent — WILL be captured when white moves C3→B2).
 *
 * Sequence:
 *  1. Highlight C3 — the moving white piece.
 *  2. Show edge C3→C4 + C4 — "this enemy is already adjacent to you before the move → blocked".
 *  3. Pause.
 *  4. Show the intended path C3→B2.
 *  5. Show B3 + edge B2→B3 — "this enemy is only adjacent to the destination → captured".
 */
fun createPreAdjacencyAnimations(duration: Long = 3000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 2

    sequences.add(
        listOf(
            createArrowAnimation(Edge(C3 to B2), startDelay = baseDelay * 1, pulse = true),
            createVertexAnimation(B2, duration / 2, baseDelay * 2, true),
        ),
    )

    return sequences
}

/**
 * Animation for the end conditions tutorial step.
 *
 * Uses the initial board state. Highlights white and black home bases alternately
 * to suggest "control of enemy territory = victory", then pulses all bridge vertices
 * as contested central space.
 */
fun createEndConditionsAnimations(duration: Long = 3000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 10

    // White home base — where white starts
    sequences.add(
        listOf(
            createVertexAnimation(D1, duration / 3, baseDelay * 1, true),
            createVertexAnimation(D2, duration / 3, baseDelay * 2, true),
            createVertexAnimation(C1, duration / 3, baseDelay * 3, true),
            createVertexAnimation(C2, duration / 3, baseDelay * 4, true),
        ),
    )
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Black home base — where black starts
    sequences.add(
        listOf(
            createVertexAnimation(D3, duration / 3, baseDelay * 5, true),
            createVertexAnimation(D4, duration / 3, baseDelay * 6, true),
            createVertexAnimation(C7, duration / 3, baseDelay * 7, true),
            createVertexAnimation(C8, duration / 3, baseDelay * 8, true),
        ),
    )
    sequences.add(listOf(HighlightAnimation.Pause()))

    // All 8 starting vertices pulse together — "one side converts all → game over"
    sequences.add(
        listOf(
            createVertexAnimation(D1, duration / 3, pulse = true),
            createVertexAnimation(D2, duration / 3, pulse = true),
            createVertexAnimation(C1, duration / 3, pulse = true),
            createVertexAnimation(C2, duration / 3, pulse = true),
            createVertexAnimation(D3, duration / 3, pulse = true),
            createVertexAnimation(D4, duration / 3, pulse = true),
            createVertexAnimation(C7, duration / 3, pulse = true),
            createVertexAnimation(C8, duration / 3, pulse = true),
        ),
    )

    return sequences
}

/**
 *
 * Shows a white cob at D3 (a primary dead vertex for white — deepest inside the
 * black home base), then highlights its two neighbors D4 and C7 as blockers.
 * Finally pulses D3 again to signal the in-place promotion to rok.
 *
 * Board positions used:
 *   D3 — white cob, primary dead vertex → will promote to rok
 *   D4, C7 — black cobs blocking all exits
 */
fun createDeadPieceAnimations(duration: Long = 2500L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 4

    // Pulse D3 — the trapped dead piece that will promote in-place
    sequences.add(listOf(createVertexAnimation(D3, duration / 3, baseDelay * 1, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Pulse D3 with CAPTURE color — same effect as a threatened piece,
    // signalling the user to tap it to trigger the in-place promotion.
    sequences.add(
        listOf(
            HighlightAnimation.Vertex(
                VertexHighlight(
                    vertex = D3,
                    pulse = true,
                    duration = duration / 3,
                    startDelay = baseDelay * 2,
                    action = HighlightAction.CAPTURE,
                ),
            ),
        ),
    )

    return sequences
}

/**
 * Animation for the domestic capture tutorial step.
 *
 * Board positions: white cob at D2, black cob at C1 (inside white's domestic zone).
 * Both non-forward moves from D2 produce a capture of C1:
 *   D2→C2: C2 adj C1, D2 not adj C1 ✓
 *   D2→D1: D1 adj C1, D2 not adj C1 ✓
 */
fun createDomesticCaptureAnimations(duration: Long = 2000L): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()
    val baseDelay = duration / 5

    // Highlight white piece at D2 (origin)
    sequences.add(listOf(createVertexAnimation(D2, duration / 3, baseDelay, true)))
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Highlight the enemy at C1 (target inside our domestic) with CAPTURE color
    sequences.add(
        listOf(
            HighlightAnimation.Vertex(
                VertexHighlight(
                    vertex = C1,
                    pulse = true,
                    duration = duration / 3,
                    startDelay = baseDelay,
                    action = HighlightAction.CAPTURE,
                ),
            ),
        ),
    )
    sequences.add(listOf(HighlightAnimation.Pause()))

    // Show both valid non-forward move edges simultaneously
    sequences.add(
        listOf(
            createArrowAnimation(Edge(D2 to C2), startDelay = baseDelay * 2, pulse = true),
            createArrowAnimation(Edge(D2 to D1), startDelay = baseDelay * 2, pulse = true),
        ),
    )

    return sequences
}