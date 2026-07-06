@file:Suppress("SameParameterValue")

package com.agustin.tarati.ui.components.game.highlights.sequences

import com.agustin.tarati.core.domain.game.board.Edge
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B3
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.B5
import com.agustin.tarati.core.domain.game.board.GameBoard.B6
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C10
import com.agustin.tarati.core.domain.game.board.GameBoard.C11
import com.agustin.tarati.core.domain.game.board.GameBoard.C12
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C4
import com.agustin.tarati.core.domain.game.board.GameBoard.C5
import com.agustin.tarati.core.domain.game.board.GameBoard.C6
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.C9
import com.agustin.tarati.core.domain.game.board.GameBoard.bridgeVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.centralRegions
import com.agustin.tarati.core.domain.game.board.GameBoard.circumferenceRegions
import com.agustin.tarati.core.domain.game.board.GameBoard.circumferenceVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.domesticRegions
import com.agustin.tarati.core.domain.game.board.GameBoard.domesticVertices
import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import com.agustin.tarati.ui.components.game.highlights.createElectricEdgeAnimation
import com.agustin.tarati.ui.components.game.highlights.createRegionAnimation
import com.agustin.tarati.ui.components.game.highlights.createVertexAnimation
import com.agustin.tarati.ui.components.game.highlights.sequences.HighlightDurations.CENTRAL_RAY_SEQUENCE
import com.agustin.tarati.ui.components.game.highlights.sequences.HighlightDurations.CIRCUMFERENCE_PAIR_SEQUENCE
import com.agustin.tarati.ui.components.game.highlights.sequences.HighlightDurations.DOMESTIC_SEQUENCE
import com.agustin.tarati.ui.components.game.highlights.sequences.HighlightDurations.GAME_OVER_FLASH
import com.agustin.tarati.ui.components.game.highlights.sequences.HighlightDurations.WAVE_EXPANSION

fun createGameOverSequence(matchState: MatchState): List<List<HighlightAnimation>> {
    val winner = matchState.winner ?: return emptyList()
    val gameState = matchState.gameState
    val sequences = mutableListOf<List<HighlightAnimation>>()

    // Secuencia 1: Destello inicial desde el centro
    sequences.addAll(createConcentricFlashSequence(GAME_OVER_FLASH))

    // Secuencia 2: Iluminación total del tablero del ganador
    sequences.addAll(createBoardIlluminationSequence(winner, gameState, WAVE_EXPANSION))

    // Secuencia 3: Destello secuencial y concéntrico de regiones
    sequences.addAll(createRegionsSequence(domesticRegions, DOMESTIC_SEQUENCE))
    sequences.addAll(createRegionsSequence(circumferenceRegions, CIRCUMFERENCE_PAIR_SEQUENCE))
    sequences.addAll(createRegionsSequence(centralRegions))

    // Secuencia 4: Rayos simultáneos
    sequences.addAll(createSimultaneousRaysSequence())

    // Secuencia 5: Expansión en ondas desde el centro
    sequences.addAll(createRaysFinalSequence(A1, WAVE_EXPANSION))

    return sequences
}

fun createConcentricFlashSequence(duration: Long): List<List<HighlightAnimation>> {
    val createAnimations = { vertices: List<Vertex>, timeOffset: Int ->
        vertices.map {
            createVertexAnimation(
                vertex = it,
                duration = duration - (duration / 4) * timeOffset,
                pulse = true,
            )
        }
    }

    val center =
        listOf(
            createVertexAnimation(
                vertex = A1,
                duration = duration,
                pulse = true,
                persistent = true,
            ),
        )

    val bridge = createAnimations(bridgeVertices, 1)
    val circumference = createAnimations(circumferenceVertices, 2)
    val domestic = createAnimations(domesticVertices, 3)

    return listOf(center, bridge, circumference, domestic)
}

private fun createRegionsSequence(
    regions: List<Region>,
    duration: Long = 100L,
): List<List<HighlightAnimation>> {
    val regionHighlights: MutableList<List<HighlightAnimation>> = mutableListOf()

    regions.forEach { region ->
        regionHighlights.add(
            listOf(
                createRegionAnimation(
                    region = region,
                    duration = duration,
                ),
            ),
        )
    }

    return regionHighlights
}

/**
 * Secuencia de rayos simultáneos
 */
private fun createSimultaneousRaysSequence(): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()

    // Definimos los dos caminos de los rayos que ocurrirán simultáneamente
    val rayPaths = listOf(
        // Primer camino
        listOf(
            A1, B2, C3,
            B2, B3, C6,
            B3, A1, B6, C11,
            B6, B1, C2,
            B1, A1, B4, C7,
            B4, B5, C10,
            B5, A1
        ),
        // Segundo camino (simultáneo)
        listOf(
            A1, B5, C9,
            B5, B6, C12,
            B6, A1, B3, C5,
            B3, B4, C8,
            B4, A1, B1, C1,
            B1, B2, C4,
            B2, A1
        ),
    )

    // Creamos animaciones para ambos paths en paralelo
    sequences.addAll(createSimultaneousRaysSequenceInternal(rayPaths))

    return sequences
}

/**
 * Función interna para crear secuencias de rayos simultáneos
 */
private fun createSimultaneousRaysSequenceInternal(paths: List<List<Vertex>>): List<List<HighlightAnimation>> {
    val sequences = mutableListOf<List<HighlightAnimation>>()

    // Encontrar la longitud máxima entre todos los paths
    val maxLength = paths.maxOf { it.size }

    // Animamos todos los vértices iniciales simultáneamente
    val initialAnimations =
        paths.map {
            createVertexAnimation(
                vertex = it[0],
                duration = CENTRAL_RAY_SEQUENCE,
                pulse = true,
            )
        }
    sequences.add(initialAnimations)

    // Animamos pares de edges consecutivos para todos los paths simultáneamente
    for (i in 0 until maxLength - 2) {
        val currentStep = mutableListOf<HighlightAnimation>()

        paths.forEach { path ->
            if (i < path.size - 2) {
                // Primer edge del par consecutivo
                val firstEdge = Edge(path[i] to path[i + 1])
                currentStep.add(
                    createElectricEdgeAnimation(
                        edge = firstEdge,
                        duration = WAVE_EXPANSION,
                        pulse = true,
                    ),
                )

                // Segundo edge del par consecutivo
                val secondEdge = Edge(path[i + 1] to path[i + 2])
                currentStep.add(
                    createElectricEdgeAnimation(
                        edge = secondEdge,
                        duration = WAVE_EXPANSION,
                        pulse = true,
                    ),
                )

                // Vértice intermedio entre los dos edges
                currentStep.add(
                    createVertexAnimation(
                        vertex = path[i + 1],
                        duration = WAVE_EXPANSION,
                        pulse = true,
                    ),
                )
            }
        }

        sequences.add(currentStep)
    }

    // Animamos los últimos segmentos para todos los paths
    val finalStep = mutableListOf<HighlightAnimation>()
    paths.forEach { path ->
        if (path.size >= 2) {
            val lastEdge = Edge(path[path.size - 2] to path[path.size - 1])
            finalStep.add(
                createElectricEdgeAnimation(
                    edge = lastEdge,
                    duration = WAVE_EXPANSION,
                    pulse = true,
                ),
            )
            finalStep.add(
                createVertexAnimation(
                    vertex = path.last(),
                    duration = WAVE_EXPANSION,
                    pulse = true,
                ),
            )
        }
    }
    sequences.add(finalStep)

    return sequences
}

fun createRaysFinalSequence(
    startingVertex: Vertex,
    duration: Long = 100L,
): List<List<HighlightAnimation>> {
    val visited = mutableSetOf(startingVertex)
    val queue = ArrayDeque<Vertex>().apply { add(startingVertex) }
    val animations = mutableListOf<List<HighlightAnimation>>()
    val plainAnimation = mutableListOf<HighlightAnimation>()

    while (queue.isNotEmpty()) {
        val currentLevel = queue.toList().also { queue.clear() }
        val levelAnimations = mutableListOf<HighlightAnimation>()

        currentLevel.forEach { vertex ->
            levelAnimations.add(createVertexAnimation(vertex = vertex, duration = duration))

            GameBoard.adjacencyMap[vertex]?.forEach { neighbor ->
                if (neighbor !in visited) {
                    addEdgeAnimations(
                        edge = Edge(vertex to neighbor),
                        duration = duration,
                        levelAnimations = levelAnimations,
                        plainAnimation = plainAnimation,
                    )
                    queue.add(neighbor)
                    visited.add(neighbor)
                }
            }
        }

        animations.add(levelAnimations)
    }

    return animations + listOf(plainAnimation)
}

private fun addEdgeAnimations(
    edge: Edge,
    duration: Long,
    levelAnimations: MutableList<HighlightAnimation>,
    plainAnimation: MutableList<HighlightAnimation>,
) {
    levelAnimations.add(
        createElectricEdgeAnimation(
            edge = edge,
            persistent = true,
            duration = duration,
        ),
    )

    plainAnimation.add(
        createElectricEdgeAnimation(
            edge = edge,
            persistent = true,
            duration = duration * 10L,
        ),
    )
}

private fun createBoardIlluminationSequence(
    winner: CobColor,
    gameState: GameState,
    duration: Long = 100L,
): List<List<HighlightAnimation>> {
    val winnerVertices =
        gameState.cobs.entries
            .filter { it.value.color == winner }
            .map { it.key }

    // Iluminación simultánea de todas las piezas del ganador
    val vertices = mutableListOf<HighlightAnimation>()
    winnerVertices.forEach { vertex ->
        vertices.add(
            createVertexAnimation(
                vertex = vertex,
                duration = duration,
            ),
        )
    }

    // Efecto de pulso rítmico en todas las piezas
    val cobs = mutableListOf<HighlightAnimation>()
    repeat(4) { _ ->
        winnerVertices.forEach { vertex ->
            cobs.add(
                createVertexAnimation(
                    vertex = vertex,
                    pulse = true,
                    duration = duration,
                ),
            )
        }
    }

    return listOf(vertices, cobs)
}
