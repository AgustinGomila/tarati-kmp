package com.agustin.tarati.core.domain.game.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Extensiones de geometría visual para GameBoard.
 * Contiene funciones de renderizado que dependen de Compose (Offset, Size) —
 * por eso viven separadas de [GameBoard], que se mantiene libre de
 * dependencias de UI.
 */

// ========== Constantes de Renderizado ==========

const val BOARD_MARGIN_PERCENT: Float = 0.8f

// ========== Data Classes de Renderizado ==========

/**
 * Rectángulo que contiene un conjunto de vértices en pantalla.
 */
data class BoardRect(
    val topLeft: Offset,
    val size: Size,
)

/**
 * Bounds normalizados del tablero para cada orientación.
 */
data class NormalizedBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

// ========== Posiciones Normalizadas ==========

/**
 * Posiciones normalizadas de todos los vértices (0.0 a 1.0).
 * Derivadas de [GameBoard.logicalPositions] (la única fuente de la geometría)
 * y calculadas una vez.
 */
val normalizedPositions: Map<Vertex, NormalizedBoard> by lazy {
    GameBoard.logicalPositions.mapValues { (_, position) ->
        NormalizedBoard(
            position.x / GameBoard.REFERENCE_BOARD_SIZE,
            position.y / GameBoard.REFERENCE_BOARD_SIZE,
        )
    }
}

/**
 * Bounds normalizados para cada orientación del tablero.
 */
val normalizedBoundsPerOrientation: Map<BoardOrientation, NormalizedBounds> by lazy {
    BoardOrientation.entries.associateWith { orientation ->
        val rotated = normalizedPositions.mapValues { (_, pos) -> pos.rotate(orientation) }
        NormalizedBounds(
            minX = rotated.values.minOf { it.x },
            maxX = rotated.values.maxOf { it.x },
            minY = rotated.values.minOf { it.y },
            maxY = rotated.values.maxOf { it.y },
        )
    }
}

// ========== Funciones de Geometría Visual ==========

/**
 * Devuelve el rectángulo que contiene todos los vértices según la visualización actual.
 */
fun getBoardRect(
    vertices: List<Vertex>,
    canvasSize: Size,
    orientation: BoardOrientation,
): BoardRect {
    val positions =
        vertices.distinct().map {
            getVisualPosition(it, canvasSize, orientation)
        }

    val minX = positions.minOf { it.x }
    val maxX = positions.maxOf { it.x }
    val minY = positions.minOf { it.y }
    val maxY = positions.maxOf { it.y }

    return BoardRect(
        topLeft = Offset(minX, minY),
        size = Size(width = maxX - minX, height = maxY - minY),
    )
}

/**
 * Calcula el factor de escala del tablero para que encaje en el contenedor
 * manteniendo el aspect ratio y respetando el margen de padding.
 */
fun getBoardScale(size: Size, orientation: BoardOrientation): Float {
    val bounds = normalizedBoundsPerOrientation[orientation]!!
    val scaleX = size.width / bounds.width
    val scaleY = size.height / bounds.height
    return minOf(scaleX, scaleY) * BOARD_MARGIN_PERCENT
}

/**
 * Convierte la posición normalizada de un vértice a píxeles en pantalla.
 *
 * @param vertex El vértice cuya posición se busca.
 * @param size El tamaño del contenedor donde se dibuja el tablero.
 * @param orientation La orientación actual del tablero.
 * @return La posición en píxeles dentro del contenedor.
 */
fun getVisualPosition(
    vertex: Vertex,
    size: Size,
    orientation: BoardOrientation,
): Offset {
    val normalized =
        normalizedPositions[vertex]
            ?: throw IllegalArgumentException("Unknown vertex: $vertex")

    val rotated = normalized.rotate(orientation)
    val bounds = normalizedBoundsPerOrientation[orientation]!!

    // Escala uniforme que maximiza el uso del espacio disponible
    // sin distorsionar el tablero ni exceder el margen de padding.
    val scale = getBoardScale(size, orientation)

    // Centrar el contenido real del tablero dentro del contenedor
    val drawWidth = bounds.width * scale
    val drawHeight = bounds.height * scale
    val offsetX = (size.width - drawWidth) / 2f - bounds.minX * scale
    val offsetY = (size.height - drawHeight) / 2f - bounds.minY * scale

    return Offset(
        x = offsetX + rotated.x * scale,
        y = offsetY + rotated.y * scale,
    )
}

/**
 * Encuentra el vértice más cercano a una posición de tap.
 * Retorna null si no hay vértices dentro de [maxTapDistance].
 */
fun findClosestVertex(
    tapOffset: Offset,
    size: Size,
    maxTapDistance: Float,
    orientation: BoardOrientation,
): Vertex? {
    // Una posición y una distancia (al cuadrado) por vértice; el umbral se
    // compara también al cuadrado para evitar la raíz.
    val closest = GameBoard.vertices
        .map { vertex ->
            val pos = getVisualPosition(vertex, size, orientation)
            vertex to (tapOffset - pos).getDistanceSquared()
        }
        .minByOrNull { (_, distanceSq) -> distanceSq }
        ?: return null

    val (vertex, distanceSq) = closest
    return vertex.takeIf { distanceSq < maxTapDistance * maxTapDistance }
}

// ========== Cache de Posiciones Visuales ==========

/**
 * Precomputed screen positions for all 23 board vertices at a specific
 * [size] / [orientation] combination.
 *
 * Positions change only when [size] or [orientation] change (device resize
 * or rotation). For all other invalidations (piece moves, highlights,
 * animation ticks) the positions are constant and can be reused without
 * recomputing [getVisualPosition] 100+ times per frame.
 *
 * Usage in Compose:
 * ```
 * val positionCache = remember(containerSize, boardOrientation) {
 *     buildPositionCache(containerSize, boardOrientation)
 * }
 * ```
 */
class VisualPositionCache(
    val size: Size,
    val orientation: BoardOrientation,
    private val positions: Map<Vertex, Offset>,
) {
    /** Returns the precomputed screen position for [vertex]. Falls back to
     *  [getVisualPosition] if the vertex is somehow not in the cache. */
    operator fun get(vertex: Vertex): Offset =
        positions[vertex] ?: getVisualPosition(vertex, size, orientation)
}

/**
 * Builds a [VisualPositionCache] for all 23 board vertices.
 * Cost: 23 x [getVisualPosition] calls, paid once per resize/rotation.
 */
fun buildPositionCache(
    size: Size,
    orientation: BoardOrientation,
): VisualPositionCache =
    VisualPositionCache(
        size = size,
        orientation = orientation,
        positions = GameBoard.vertices.associateWith { getVisualPosition(it, size, orientation) },
    )