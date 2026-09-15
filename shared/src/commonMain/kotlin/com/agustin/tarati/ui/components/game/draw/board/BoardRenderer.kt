package com.agustin.tarati.ui.components.game.draw.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.VisualPositionCache
import com.agustin.tarati.core.domain.game.board.buildPositionCache
import com.agustin.tarati.core.domain.game.board.getBoardScale
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.utils.helpers.getCurrentHour
import com.agustin.tarati.core.utils.logging.LoggingFactory
import com.agustin.tarati.ui.components.game.BoardState
import com.agustin.tarati.ui.components.game.animation.AnimatedCob
import com.agustin.tarati.ui.components.game.behaviors.PreMoveContext
import com.agustin.tarati.ui.components.game.behaviors.TapEvents
import com.agustin.tarati.ui.components.game.behaviors.dragGestures
import com.agustin.tarati.ui.components.game.behaviors.tapGestures
import com.agustin.tarati.ui.components.game.draw.pieces.createOrganicColor
import com.agustin.tarati.ui.components.game.draw.pieces.drawAnimatedPiece
import com.agustin.tarati.ui.components.game.draw.pieces.drawPiece
import com.agustin.tarati.ui.components.game.draw.pieces.getPieceColors
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import com.agustin.tarati.ui.components.game.highlights.base.DynamicEdgeHighlight
import com.agustin.tarati.ui.components.game.highlights.base.EdgeHighlight
import com.agustin.tarati.ui.theme.BoardColors
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BoardRenderer(
    modifier: Modifier = Modifier,
    playerSide: CobColor,
    boardState: BoardState,
    tapEvents: TapEvents,
    boardData: BoardRenderData,
    boardEvents: BoardRenderEvents,
    /** True mientras la IA está calculando un movimiento. */
    isAIThinking: Boolean = false,
    /** True si el usuario habilitó pre-movimientos en settings. */
    preMovesEnabled: Boolean = false,
) {
    var prevGameState by remember { mutableStateOf<GameState?>(null) }
    val gameState = boardState.gameState

    val logger = remember { LoggingFactory.getLogger() }

    val selectedVertex = boardData.selectedVertex
    val validAdjacentVertexes = boardData.validAdjacentVertexes
    val visualState = boardData.visualState
    val animatedPieces = boardData.animatedPieces
    val currentHighlight = boardData.currentHighlights
    val preMoveFromVertex = boardData.preMoveFromVertex
    val preMoveValidTargets = boardData.preMoveValidTargets
    val pendingPreMove = boardData.pendingPreMove

    val hasPulsingHighlights = remember(currentHighlight) {
        currentHighlight.any { it.highlight?.pulse == true }
    }
    val hasSelection = selectedVertex != null
    val hasPreMoveActivity = preMoveFromVertex != null || pendingPreMove != null

    // Ticker unificado para todos los efectos de animación del Canvas dinámico.
    // Corre a 60fps mientras algún efecto lo necesite; un único State en lugar
    // de tres evita invalidaciones redundantes en el mismo frame.
    var animTick by remember { mutableLongStateOf(0L) }
    val needsTick = hasPulsingHighlights || hasSelection || hasPreMoveActivity
    LaunchedEffect(needsTick) {
        while (needsTick) {
            delay(16L.milliseconds) // ~60fps
            animTick = Clock.System.now().toEpochMilliseconds()
        }
    }
    val selectionTick = animTick
    val preMoveTick = animTick

    val random = remember { Random.Default }
    var randomSegments by remember { mutableIntStateOf(8) }
    var randomSeed by remember { mutableFloatStateOf(0.5f) }
    var hourOfDay by remember { mutableFloatStateOf(12f) }

    val boardColors = getBoardColors()

    // Effect to synchronize the initial state.
    //
    // LaunchedEffect(Unit) fires once per composition entry. On device rotation
    // Android recreates the Activity, tearing down and rebuilding the Compose tree.
    // Both ViewModels (GameViewModel, BoardAnimationViewModel) survive via the
    // ViewModel store, so visualState already contains the correct mid-game pieces.
    //
    // Guard: if visualState already has pieces, the ViewModel survived the config
    // change intact. Skip reset/sync to avoid overwriting the correct visual state
    // with a transient empty or initial-position state.
    LaunchedEffect(Unit) {
        val newGame = boardState.newGame
        val gameState = boardState.gameState
        when {
            newGame -> {
                boardEvents.onReset()
                prevGameState = null
            }

            visualState.cobs.isNotEmpty() -> {
                // ViewModel survived rotation with valid visual state.
                // Only update prevGameState so subsequent changes are detected
                // correctly; do NOT call onSyncState or onReset.
                prevGameState = gameState
            }

            else -> {
                boardEvents.onSyncState(gameState)
                prevGameState = gameState
            }
        }
    }

    // Efecto para actualizar el estado del tablero
    LaunchedEffect(gameState, visualState) {
        val gameState = boardState.gameState

        // Tablero sin piezas
        if (gameState.isEmptyBoard()) {
            boardEvents.onReset()
            prevGameState = null
            return@LaunchedEffect
        }

        // If visualState is empty but gameState has pieces, the visual layer was wiped
        // (by forceSync or cancelCurrentQueueAndWait) while the logical state is valid.
        // Sync unconditionally — waiting for animatedPieces to drain first would leave
        // the board blank for the entire duration of any running animation.
        val visualIsEmpty = visualState.cobs.isEmpty()
        if (visualIsEmpty) {
            boardEvents.onSyncState(gameState)
            prevGameState = gameState
            hourOfDay = getCurrentHour()
            return@LaunchedEffect
        }

        // Si NO estamos en medio de una animación, sincronizar el estado
        if (animatedPieces.isEmpty()) {
            if (gameState.isInitialState(playerSide)) {
                boardEvents.onSyncState(gameState)
            }

            // Solo sincronizar si el estado cambió
            if (gameState != prevGameState) {
                boardEvents.onSyncState(gameState)
                prevGameState = gameState
                hourOfDay = getCurrentHour()
            }
        } else {
            // Si hay animaciones en curso, solo actualizar prevGameState
            if (gameState != prevGameState) {
                prevGameState = gameState
                hourOfDay = getCurrentHour()
            }
        }
    }

    // Actualizar la orientación del tablero para referencia de las animaciones
    LaunchedEffect(boardState.boardOrientation) {
        val boardOrientation = boardState.boardOrientation
        boardEvents.onUpdateBoardOrientation(boardOrientation)
    }

    val currentBoardState =
        boardState.copy(
            gameState =
                GameState(
                    cobs = visualState.cobs,
                    currentTurn = visualState.currentTurn ?: boardState.gameState.currentTurn,
                ),
        )

    val density = LocalDensity.current
    val visualWidth by remember { mutableFloatStateOf(with(density) { 60.dp.toPx() }) }

    // SOURCE OF TRUTH para el tamaño del contenedor
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val inPreview = LocalInspectionMode.current

    // Precomputed screen positions for all vertices. Recomputed only when
    // containerSize or boardOrientation change -- not on every animation tick.
    //
    // En previews, onGloballyPositioned no dispara → containerSize = Size.Zero →
    // positionCache con escala cero. Se detecta aquí (contexto @Composable con acceso
    // a LocalInspectionMode) en lugar de en drawAllPieces (DrawScope sin CompositionLocals),
    // evitando el check Size.Zero en cada frame de producción.
    val positionCache = remember(containerSize, boardState.boardOrientation, inPreview) {
        if (inPreview && containerSize == Size.Zero) {
            // En preview no hay Canvas real todavía — se usa un tamaño fijo de referencia
            // que drawAllPieces sustituirá por DrawScope.size en el primer redibujado.
            // El remember(inPreview) garantiza que este path solo se toma en previews.
            buildPositionCache(Size.Zero, boardState.boardOrientation)
        } else {
            buildPositionCache(containerSize, boardState.boardOrientation)
        }
    }

    // Pre-move activo solo durante el turno de la IA, si el usuario lo habilitó.
    // El humano es el bando "no-AI"; su color se deriva de los flags whiteIsAI/blackIsAI
    // cruzados con el currentTurn. Si ambos bandos son AI (o ninguno es AI) el context
    // queda null: fallback al flujo normal.
    val preMoveContext: PreMoveContext? = remember(
        isAIThinking,
        preMovesEnabled,
        boardState.whiteIsAI,
        boardState.blackIsAI,
        boardState.gameState.currentTurn,
        preMoveFromVertex,
    ) {
        if (!isAIThinking || !preMovesEnabled) return@remember null

        val currentTurn = boardState.gameState.currentTurn
        val isAITurn = (currentTurn == CobColor.WHITE && boardState.whiteIsAI) ||
                (currentTurn == CobColor.BLACK && boardState.blackIsAI)
        if (!isAITurn) return@remember null

        // Humano = opuesto al turno actual (la IA juega ahora).
        val humanColor = currentTurn.opponent
        // Si el "humano" también está marcado como IA, ambos bandos son IA — no hay
        // humano al que habilitar pre-move.
        val humanIsAI = (humanColor == CobColor.WHITE && boardState.whiteIsAI) ||
                (humanColor == CobColor.BLACK && boardState.blackIsAI)
        if (humanIsAI) return@remember null

        PreMoveContext(
            preMoveFrom = preMoveFromVertex,
            humanColor = humanColor,
        )
    }

    // ── Arrastrar y soltar ─────────────────────────────────────────────────────
    // Estado visual transitorio del arrastre: qué pieza se agarró y dónde está el
    // dedo/puntero. Vive local al renderer (no en el ViewModel) para no generar
    // churn de StateFlow a 60fps; drawAllPieces lo lee para dibujar la pieza bajo
    // el puntero. La semántica (seleccionar/mover/pre-mover/reubicar) la resuelve
    // dragGestures reutilizando los mismos callbacks de TapEvents que el tap.
    var draggingFrom by remember { mutableStateOf<Vertex?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Unspecified) }

    // Limpieza reactiva del drag tras un drop que mueve la pieza (onRelease no limpió): en cuanto el
    // estado visual deja de tener la pieza en el origen (movida o pasada a animación), el render normal
    // toma el relevo desde el punto de soltado y ya no hace falta la pieza flotante → se limpia.
    LaunchedEffect(visualState.cobs, draggingFrom) {
        val df = draggingFrom
        if (df != null && dragPosition.isSpecified && df !in visualState.cobs) {
            draggingFrom = null
            dragPosition = Offset.Unspecified
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val newSize = coords.size.toSize()
                    containerSize = newSize
                    boardEvents.onBoardSizeChange(newSize)
                }
                .pointerInput(
                    visualWidth,
                    boardState.gameState,
                    playerSide,
                    selectedVertex,
                    boardState.boardOrientation,
                    boardState.isEditing,
                    boardState.whiteIsAI,
                    boardState.blackIsAI,
                    tapEvents,
                    // Cambios del preMoveContext deben reiniciar el detector para que el nuevo
                    // valor llegue a tapGestures. Se incluye el context entero (data class)
                    // porque su contenido (preMoveFrom, humanColor) es lo que cambia.
                    preMoveContext,
                ) {
                    tapGestures(
                        visualWidth = visualWidth,
                        gameState = boardState.gameState,
                        whiteIsAI = boardState.whiteIsAI,
                        blackIsAI = boardState.blackIsAI,
                        from = selectedVertex,
                        orientation = boardState.boardOrientation,
                        editorMode = boardState.isEditing,
                        tapEvents = tapEvents,
                        logger = logger,
                        preMoveContext = preMoveContext,
                    )
                }
                // Bloque de arrastre paralelo al de tap: Compose arbitra por touch-slop.
                // No depende de selectedVertex (el drag computa su propio origen al agarrar).
                .pointerInput(
                    visualWidth,
                    boardState.gameState,
                    boardState.boardOrientation,
                    boardState.isEditing,
                    boardState.whiteIsAI,
                    boardState.blackIsAI,
                    tapEvents,
                    preMoveContext,
                ) {
                    dragGestures(
                        visualWidth = visualWidth,
                        gameState = boardState.gameState,
                        whiteIsAI = boardState.whiteIsAI,
                        blackIsAI = boardState.blackIsAI,
                        orientation = boardState.boardOrientation,
                        editorMode = boardState.isEditing,
                        tapEvents = tapEvents,
                        logger = logger,
                        preMoveContext = preMoveContext,
                        onGrab = { from, pointer ->
                            draggingFrom = from
                            dragPosition = pointer
                        },
                        onDragTo = { pointer -> dragPosition = pointer },
                        onRelease = { moved ->
                            // Si el drop movió la pieza, se mantiene flotando en el punto de soltado
                            // hasta que el estado la reubique (limpieza reactiva más abajo), evitando el
                            // flash de 1 frame en el origen. En cancel/pre-move/ilegal se limpia ya.
                            if (!moved) {
                                draggingFrom = null
                                dragPosition = Offset.Unspecified
                            }
                        },
                        // Anima la pieza desde el punto de soltado hasta el destino
                        // (registra el offset en el pipeline de animación, keyed por el Move).
                        onDropMove = { move, pointer -> boardEvents.onMoveFromDrop(move, pointer) },
                    )
                },
    ) {
        // Canvas estático: aristas y vertices del tablero.
        // Se redibuja solo cuando cambia currentBoardState, selectedVertex,
        // validAdjacentVertexes o boardColors -- no en cada tick de animacion.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawEdges(
                canvasSize = size,
                orientation = boardState.boardOrientation,
                boardState = currentBoardState,
                colors = boardColors,
                positionCache = positionCache,
            )

            drawVertices(
                canvasSize = size,
                vWidth = visualWidth,
                selectedVertex = selectedVertex,
                adjacentVertexes = validAdjacentVertexes,
                boardState = currentBoardState,
                colors = boardColors,
            )
        }

        // Canvas dinámico: highlights y piezas.
        // Se redibuja a ~60fps durante animaciones o con seleccion activa.
        // Los ticks se leen aquí -- no en el Canvas estatico -- para que
        // Compose solo invalide este Canvas en cada tick.
        Canvas(modifier = Modifier.matchParentSize()) {
            currentHighlight.forEach { highlight ->
                when (highlight) {
                    is HighlightAnimation.Vertex -> {
                        drawVertexHighlight(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            orientation = boardState.boardOrientation,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.Arrow -> {
                        drawArrowEdgeHighlightFromVertex(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            colors = boardColors,
                            orientation = boardState.boardOrientation,
                        )
                    }

                    is HighlightAnimation.FireballEdge -> {
                        drawFireballEdgeHighlightFromVertex(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            colors = boardColors,
                            orientation = boardState.boardOrientation,
                        )
                    }

                    is HighlightAnimation.ElectricEdge -> {
                        val segmentsRange =
                            getHighlightsSegmentsRange(highlight.highlight, positionCache)
                        randomSegments = random.nextInt(segmentsRange.first, segmentsRange.second)
                        randomSeed = random.nextFloat()

                        drawElectricEdgeHighlightFromVertex(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            orientation = boardState.boardOrientation,
                            variationFactor = randomSeed,
                            randomSegments = randomSegments,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.Region -> {
                        drawRegionHighlight(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            orientation = boardState.boardOrientation,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.DynamicFireballEdge -> {
                        drawDynamicFireballEdgeHighlight(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.DynamicElectricEdge -> {
                        val segmentsRange = getHighlightsSegmentsRange(highlight.highlight)
                        randomSegments = random.nextInt(segmentsRange.first, segmentsRange.second)
                        randomSeed = random.nextFloat()

                        drawDynamicEdgeElectricHighlight(
                            highlight = highlight.highlight,
                            variationFactor = randomSeed,
                            randomSegments = randomSegments,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.DynamicForceArc -> {
                        drawForceArcDynamicHighlight(
                            highlight = highlight.highlight,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.ForceArcEdge -> {
                        drawForceArcEdgeHighlight(
                            highlight = highlight.highlight,
                            canvasSize = size,
                            orientation = boardState.boardOrientation,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.DynamicForceArcImpact -> {
                        drawForceArcImpactHighlight(
                            highlight = highlight.highlight,
                            colors = boardColors,
                        )
                    }

                    is HighlightAnimation.Pause -> {
                        // No dibujar nada durante pausas
                    }
                }
            }

            val animatedInvolved = HashSet<Vertex>(animatedPieces.size * 2)
            animatedPieces.values.forEach { animatedInvolved.add(it.currentPos); animatedInvolved.add(it.targetPos) }
            val staticCobs = visualState.cobs.filterKeys { vertex ->
                !animatedPieces.containsKey(vertex) && vertex !in animatedInvolved
            }

            drawAllPieces(
                staticCobs = staticCobs,
                animatedPieces = animatedPieces,
                tiltAngles = visualState.tiltAngles,
                positionCache = positionCache,
                orientation = boardState.boardOrientation,
                selectedPiece = selectedVertex,
                hourOfDay = hourOfDay,
                selectionTimeMs = selectionTick,
                colors = boardColors,
                draggingFrom = draggingFrom,
                dragPosition = dragPosition,
            )

            // ── Pre-movimiento ─────────────────────────────────────────────────
            // Derivar pieceRadius con la misma fórmula que drawAllPieces (sizeFactor 0.08).
            val preMovePieceRadius =
                getBoardScale(positionCache.size.takeIf { it != Size.Zero }
                    ?: size, boardState.boardOrientation) * 0.08f

            drawPreMoveSelection(
                preMoveFromVertex = preMoveFromVertex,
                preMoveValidTargets = preMoveValidTargets,
                positionCache = positionCache,
                pieceRadius = preMovePieceRadius,
                colors = boardColors,
                tickMs = preMoveTick,
            )

            drawPreMoveArrow(
                pendingPreMove = pendingPreMove,
                positionCache = positionCache,
                pieceRadius = preMovePieceRadius,
                colors = boardColors,
            )
        }

        VertexLabelsOverlay(
            occupiedVertex = VertexListWrapper(boardState.gameState.cobs.map { it.key }),
            labelsVisible = boardState.boardVisualState.labelsVisibles &&
                    boardState.boardVisualState.verticesVisibles,
            boardOrientation = boardState.boardOrientation,
            containerSize = containerSize,
            textSize = visualWidth / 4,
            textColor = boardColors.textColor,
        )
    }
}

fun DrawScope.drawAllPieces(
    staticCobs: Map<Vertex, Cob>,
    animatedPieces: Map<Vertex, AnimatedCob>,
    tiltAngles: Map<Vertex, Float> = emptyMap(),
    positionCache: VisualPositionCache,
    orientation: BoardOrientation,
    selectedPiece: Vertex?,
    sizeFactor: Float = 0.08f,
    hourOfDay: Float = 12f,
    selectionTimeMs: Long = 0L,
    colors: BoardColors,
    /** Vértice de la pieza que se está arrastrando, o null si no hay arrastre en curso. */
    draggingFrom: Vertex? = null,
    /** Posición actual del puntero mientras se arrastra (Unspecified si no aplica). */
    dragPosition: Offset = Offset.Unspecified,
) {
    // Fallback de seguridad: si positionCache llega con size cero (solo posible en
    // previews antes del primer layout), se construye desde DrawScope.size que
    // siempre tiene el tamaño real del Canvas. En producción este branch nunca
    // se toma — BoardRenderer detecta el caso en contexto @Composable antes de llegar aquí.
    val effectiveCache = if (positionCache.size == Size.Zero) {
        buildPositionCache(size, orientation)
    } else {
        positionCache
    }

    // Piece radius is derived from the same board scale used by getVisualPosition,
    // so it stays proportional to the rendered board size across all orientations.
    val pieceRadius = getBoardScale(effectiveCache.size, orientation) * sizeFactor

    // OPT: getLightOfDay y createOrganicColor son constantes por frame — misma hora,
    // mismo radio, misma paleta para todas las piezas. Calcularlos una vez aquí
    // elimina N llamadas a cos()/sin()/sqrt() donde N = nº de piezas en el tablero.
    val lightOfDay = getLightOfDay(hourOfDay, pieceRadius)
    val organicColors = mapOf(
        CobColor.WHITE to createOrganicColor(getPieceColors(Cob(CobColor.WHITE), colors), hourOfDay, colors),
        CobColor.BLACK to createOrganicColor(getPieceColors(Cob(CobColor.BLACK), colors), hourOfDay, colors),
    )

    // Dibujar piezas estáticas. La pieza que se está arrastrando se dibuja bajo el
    // puntero (dragPosition) y algo más grande, para que "flote" siguiendo el dedo;
    // su vértice de origen conserva el anillo de selección (Canvas estático).
    val isDragging = draggingFrom != null && dragPosition.isSpecified
    staticCobs.forEach { (vertex, cob) ->
        val dragged = isDragging && vertex == draggingFrom
        drawPiece(
            position = if (dragged) dragPosition else effectiveCache[vertex],
            radius = if (dragged) pieceRadius * 1.18f else pieceRadius,
            selectedVertex = selectedPiece,
            vertex = vertex,
            cob = cob,
            hourOfDay = hourOfDay,
            selectionTimeMs = selectionTimeMs,
            tiltDeg = tiltAngles[vertex] ?: 0f,
            colors = colors,
            precomputedLight = lightOfDay,
            precomputedOrganicColors = organicColors,
        )
    }

    // Dibujar piezas animadas
    animatedPieces.values.forEach { animatedCob ->
        // startOverride: arrastrar-y-soltar → arranca el tramo desde el punto de soltado.
        val currentPos = animatedCob.startOverride ?: effectiveCache[animatedCob.currentPos]
        val targetPos = effectiveCache[animatedCob.targetPos]

        // Interpolar posición para animación
        val animatedPos =
            Offset(
                currentPos.x + (targetPos.x - currentPos.x) * animatedCob.animationProgress,
                currentPos.y + (targetPos.y - currentPos.y) * animatedCob.animationProgress,
            )

        // Interpolar el tilt orgánico entre el ángulo de origen y el de destino.
        val tiltDeg = lerp(animatedCob.fromTiltDeg, animatedCob.toTiltDeg, animatedCob.animationProgress)

        drawAnimatedPiece(
            position = animatedPos,
            radius = pieceRadius,
            vertex = animatedCob.vertex,
            selectedVertex = selectedPiece,
            animatedCob = animatedCob,
            hourOfDay = hourOfDay,
            selectionTimeMs = selectionTimeMs,
            tiltDeg = tiltDeg,
            animationType = animatedCob.conversionType,
            colors = colors,
            precomputedLight = lightOfDay,
        )
    }
}

fun getHighlightsSegmentsRange(highlight: DynamicEdgeHighlight): Pair<Int, Int> =
    getHighlightsSegmentsRange(highlight.from, highlight.to)

fun getHighlightsSegmentsRange(
    highlight: EdgeHighlight,
    positionCache: VisualPositionCache,
): Pair<Int, Int> {
    val offset1 = positionCache[highlight.edge.from]
    val offset2 = positionCache[highlight.edge.to]
    return getHighlightsSegmentsRange(offset1, offset2)
}

fun getHighlightsSegmentsRange(
    offset1: Offset,
    offset2: Offset,
): Pair<Int, Int> {
    val distance =
        sqrt(
            (offset2.x - offset1.x).pow(2) + (offset2.y - offset1.y).pow(2),
        )
    val minSegmentLength = 10f
    val maxSegmentLength = 50f
    val absoluteMaxSegments = 20

    val minSegments = maxOf(1, (distance / maxSegmentLength).toInt()).coerceAtMost(absoluteMaxSegments)
    // maxSegments must always be strictly greater than minSegments so that
    // random.nextInt(min, max) never throws "bound must be greater than origin".
    // This can happen during rotation when containerSize is transiently Size.Zero,
    // collapsing both offsets to (0,0) and producing distance = 0.
    val maxSegments = maxOf(minSegments + 1, (distance / minSegmentLength).toInt())
        .coerceAtMost(absoluteMaxSegments + 1)

    return minSegments to maxSegments
}