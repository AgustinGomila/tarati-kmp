package com.agustin.tarati.features.game6

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.ui.components.game.draw.board.LightOfDay
import com.agustin.tarati.ui.components.game.draw.board.drawArrowHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawDynamicEdgeElectricHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawDynamicFireballEdgeHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawForceArcDynamicHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawForceArcImpactHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawPreMoveArrow
import com.agustin.tarati.ui.components.game.draw.board.drawPreMoveSelection
import com.agustin.tarati.ui.components.game.draw.board.drawVertexHighlightAt
import com.agustin.tarati.ui.components.game.draw.board.getHighlightsSegmentsRange
import com.agustin.tarati.ui.components.game.draw.board.getLightOfDay
import com.agustin.tarati.ui.components.game.draw.board.pulseFactor
import com.agustin.tarati.ui.components.game.draw.common.MorphShape
import com.agustin.tarati.ui.components.game.draw.pieces.CenterMotif
import com.agustin.tarati.ui.components.game.draw.pieces.CobColorScheme
import com.agustin.tarati.ui.components.game.draw.pieces.CobShape
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationType
import com.agustin.tarati.ui.components.game.draw.pieces.PieceColor
import com.agustin.tarati.ui.components.game.draw.pieces.PieceType
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypeManager
import com.agustin.tarati.ui.components.game.draw.pieces.createOrganicColor
import com.agustin.tarati.ui.components.game.draw.pieces.drawCoinFlip
import com.agustin.tarati.ui.components.game.draw.pieces.drawConversionFromBorder
import com.agustin.tarati.ui.components.game.draw.pieces.drawConversionFromCenter
import com.agustin.tarati.ui.components.game.draw.pieces.drawMorphCob
import com.agustin.tarati.ui.components.game.draw.pieces.drawMorphConversionFlip
import com.agustin.tarati.ui.components.game.draw.pieces.drawMorphConversionFromBorder
import com.agustin.tarati.ui.components.game.draw.pieces.drawMorphConversionFromCenter
import com.agustin.tarati.ui.components.game.draw.pieces.drawOrganicCob
import com.agustin.tarati.ui.components.game.draw.pieces.drawPolygonSelection
import com.agustin.tarati.ui.components.game.draw.pieces.drawSelection
import com.agustin.tarati.ui.components.game.draw.pieces.resolveType
import com.agustin.tarati.ui.components.game.draw.pieces.toShapeColors
import com.agustin.tarati.ui.components.game.highlights.HighlightAction
import com.agustin.tarati.ui.components.game.highlights.base.DynamicEdgeHighlight
import com.agustin.tarati.ui.theme.BoardColors
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

private const val ANIMATION_MS = 420

/** Fracción del progreso del movimiento dedicada al deslizamiento; el resto, a la conversión. */
private const val SLIDE_FRACTION = 0.55f

/**
 * Duración (ms) del resalte de los destinos alcanzables de la pieza recién movida, mostrado al
 * completarse el deslizamiento. Paridad con single, donde `createValidMovesHighlights` usa 400 ms.
 */
private const val POST_MOVE_MS = 400L


/**
 * [CobShape] de una pieza poligonal MP: la forma/guarda/patrón del [pieceType] con el color del
 * jugador ([pieceColor]) inyectado vía un scheme fijo. Sin motivo central (MP no tiene roks), como
 * `cobShapeFor` para un cob sin mejorar.
 */
private fun mpCobShape(pieceType: PieceType, pieceColor: PieceColor): CobShape =
    CobShape(
        shape = pieceType.shape,
        colorScheme = CobColorScheme.forShapeColors(pieceColor.toShapeColors()),
        borderPattern = pieceType.borderPattern,
        centerMotif = CenterMotif.None,
    )

/**
 * Reducción del `cornerRadius` de los polígonos en MP. `cornerRadius` es **absoluto** (px en el
 * espacio `radius*2`); como las piezas MP son ~1/3 del tamaño de las de single, sin escalarlo las
 * esquinas de los polígonos de pocos lados (Cuadrado/Triángulo/Diamante) se redondean hasta parecer
 * círculos. Se aplica solo a polígonos reales (`sides >= 3`); Círculo (1) y Cápsula (2) —cuya forma
 * depende de su `cornerRadius`— quedan intactos. Valor calibrado visualmente (ajustable).
 */
private const val MP_CORNER_RADIUS_SCALE = 0.5f

/**
 * Agrandado uniforme de las piezas poligonales MP para **parear su tamaño visual con el círculo
 * estándar**. En MP el círculo se dibuja a `pieceRadius` completo (a diferencia de single, que le
 * aplica su `sizeFrac` 0.82), así que los polígonos —que sí usan su `sizeFrac`— quedan relativamente
 * más chicos que en single. Multiplicar por `1/0.82` restaura la proporción polígono↔círculo de
 * single. Se aplica a **toda** la pieza: el radio del dibujo y el `cornerRadius` (para no deformar).
 */
private const val MP_POLYGON_SIZE_BOOST = 1f / 0.82f

/** Máximo tilt orgánico por pieza (±grados) — paridad con `TiltStateMap.MAX_TILT_DEG` de single. */
private const val MP_MAX_TILT_DEG = 10f

/**
 * Tilt orgánico **determinista** por vértice, en ±[MP_MAX_TILT_DEG]. Da el aspecto "no perfectamente
 * alineado" de single (que reasigna un tilt aleatorio al mover) sin estado: al desplazarse a otro
 * vértice la pieza cambia de tilt e interpola durante el movimiento. Solo se aplica a polígonos (el
 * círculo es invariante a la rotación).
 *
 * Se aplica una **mezcla de bits** (avalanche, estilo lowbias32) al `hashCode` antes de mapear a
 * `[-MAX, +MAX]`: `Vertex` (enum `Zone` + `Int`) produce hashCodes chicos —sobre todo en wasm, donde
 * el `hashCode` del enum es el ordinal— y tomar los 16 bits bajos directamente daba `u ≈ 0` para
 * todos → todas las piezas al mismo extremo (mismo lado) y delta origen→destino ≈ 0 (rotación
 * invisible al moverse). La mezcla reparte bien incluso con entradas pequeñas.
 */
private fun vertexTilt(vertex: Vertex): Float {
    var h = vertex.hashCode()
    h = h xor (h ushr 16)
    h *= 0x21f0aaad
    h = h xor (h ushr 15)
    h *= 0x735a2d97
    h = h xor (h ushr 15)
    val u = (h and 0xFFFF) / 0xFFFF.toFloat() // [0,1] bien distribuido
    return (u * 2f - 1f) * MP_MAX_TILT_DEG    // [-MAX, +MAX]
}

private fun mpScaledShape(shape: MorphShape): MorphShape =
    if (shape.sides >= 3 && shape.cornerRadius > 0f) {
        MorphShape(
            sides = shape.sides,
            cornerRadius = shape.cornerRadius * MP_CORNER_RADIUS_SCALE * MP_POLYGON_SIZE_BOOST,
            rotationDeg = shape.rotationDeg,
            edgeCurveStrength = shape.edgeCurveStrength,
            edgeCurves = shape.edgeCurves,
            sizeFrac = shape.sizeFrac,
        )
    } else {
        shape
    }

/**
 * Parámetros de dibujo de las piezas MP derivados del [PieceTypeManager] activo y del [pieceRadius]
 * del tablero. Lo comparten [Board25View] y [StaticBoard25Renderer] para que ambos renderers deriven
 * la escala/centroide de los polígonos de una única fuente (evita drift entre gameplay y miniaturas).
 *
 * Debe llamarse **dentro del `DrawScope`**: al leer `PieceTypeManager.currentPieceType` el Canvas se
 * redibuja si cambia el tipo de pieza.
 */
private class MpPieceLayout(
    /** Tipo de pieza con el `cornerRadius` ya escalado a MP (identidad si es círculo). */
    val pieceType: PieceType,
    val isPolygon: Boolean,
    /** Radio de dibujo de los polígonos (agrandado para parear el círculo estándar). */
    val polyRadius: Float,
    /** Corrección para que el centroide del polígono caiga sobre el vértice del tablero. */
    val polyCentroidOffset: Offset,
)

private fun mpPieceLayout(pieceRadius: Float): MpPieceLayout {
    val pieceType = PieceTypeManager.currentPieceType
    val isPolygon = pieceType.shape.sides > 1
    val mpPieceType = if (isPolygon) pieceType.copy(shape = mpScaledShape(pieceType.shape)) else pieceType
    val polyRadius = pieceRadius * MP_POLYGON_SIZE_BOOST
    // Los polígonos asimétricos (triángulo, pentágono) tienen el centroide desplazado del centro del
    // bounding box → se dibujan "bajos". El offset reubica el dibujo para que el centroide (centro
    // visual real) caiga sobre el vértice del tablero.
    val polyCentroidOffset = if (isPolygon) {
        val rx = polyRadius * mpPieceType.shape.sizeFrac
        val c = mpPieceType.shape.computeCentroid(polyRadius, polyRadius, rx, rx)
        Offset(c.x - polyRadius, c.y - polyRadius)
    } else {
        Offset.Zero
    }
    return MpPieceLayout(mpPieceType, isPolygon, polyRadius, polyCentroidOffset)
}

/**
 * Dibuja una pieza MP **en reposo** (sin conversión) en [center]: cob orgánico de Tarati si es
 * círculo, o `drawMorphCob` N-color con [tilt] si es polígono. Compartido por [Board25View] (con
 * `tilt` interpolado durante el deslizamiento) y [StaticBoard25Renderer] (tilt estático por vértice).
 */
private fun DrawScope.drawMpRestingPiece(
    center: Offset,
    layout: MpPieceLayout,
    pieceRadius: Float,
    tilt: Float,
    pieceColor: PieceColor,
    lightOfDay: LightOfDay,
    boardColors: BoardColors,
) {
    if (layout.isPolygon) {
        val drawC = center - layout.polyCentroidOffset
        rotate(degrees = tilt, pivot = drawC) {
            drawMorphCob(
                drawC,
                layout.polyRadius,
                mpCobShape(layout.pieceType, pieceColor),
                CobColor.WHITE,
                boardColors
            )
        }
    } else {
        val organicColor = createOrganicColor(pieceColor, hourOfDay = 12f, colors = boardColors)
        drawOrganicCob(
            position = center,
            radius = pieceRadius,
            hourOfDay = 12f,
            lightOfDay = lightOfDay,
            pieceColors = pieceColor,
            colors = boardColors,
            organicColor = organicColor,
        )
    }
}

/**
 * Render del tablero `25` con las piezas de una partida multijugador. Reutiliza el **cob de
 * Tarati** (`drawOrganicCob`) y su **animación de desplazamiento** (interpolando la posición de la
 * pieza del último movimiento), el **resalte de selección** (`drawSelection`) y muestra los
 * **vértices alcanzables** ([legalTargets]) y las **piezas amenazadas** ([threatened]) de la pieza
 * seleccionada. La visibilidad de aristas, vértices y etiquetas respeta la configuración de Settings.
 *
 * Junto a cada base dibuja un **indicador de jugador** (cob del color + ícono Humano/IA + contador de
 * piezas), con el turno actual resaltado y los retirados atenuados.
 *
 * @param seatIsAI tipo (IA = `true`) de cada asiento, alineado por índice con `state.seats`.
 * @param lastMove último movimiento aplicado, cuya pieza se anima de origen a destino.
 * @param moveCount contador de jugadas — dispara la animación al incrementarse.
 * @param animate "animar efectos" de Settings. Si `false`, se apagan sólo los **efectos decorativos**
 *   de conversión (arcos de fuerza, rayo eléctrico, estallido de impacto). La animación del
 *   movimiento, el morph de conversión, el resalte de amenazas, el anillo del selector y las guías
 *   del tutorial animan **siempre** (paridad con single, que no las liga a este flag).
 * @param suppressMoveAnimation si `true`, el último movimiento se dibuja ya en destino sin animarlo
 *   (p. ej. al **re-entrar** a una partida online tras cambiar de modo: la jugada ya fue presentada y
 *   no debe "rehacerse"). No afecta a jugadas nuevas.
 * @param showEdges / showVertices / showLabels visibilidad de aristas, puntos de parada y nombres.
 * @param preMoveFrom pieza pre-seleccionada durante el turno ajeno (halo pulsante + dots de destino).
 * @param preMoveTargets destinos legales de [preMoveFrom].
 * @param pendingPreMove pre-movimiento confirmado (flecha), pendiente de ejecución al volver el turno.
 * @param guideArrows movimientos permitidos de un paso interactivo del tutorial, marcados con la
 *   misma flecha parpadeante que el tutorial single ([drawArrowHighlight]).
 */
@Composable
fun Board25View(
    state: MpGameState,
    seatIsAI: List<Boolean>,
    selection: Vertex?,
    legalTargets: Set<Vertex>,
    threatened: Set<Vertex>,
    onVertexTap: (Vertex) -> Unit,
    lastMove: MpMove?,
    converted: Map<Vertex, PlayerColor>,
    moveCount: Int,
    animate: Boolean,
    conversionStyle: ConversionAnimationStyle,
    showEdges: Boolean,
    showVertices: Boolean,
    showLabels: Boolean,
    showRegions: Boolean,
    showPerimeter: Boolean,
    modifier: Modifier = Modifier,
    suppressMoveAnimation: Boolean = false,
    preMoveFrom: Vertex? = null,
    preMoveTargets: Set<Vertex> = emptySet(),
    pendingPreMove: MpMove? = null,
    // Vértices cuya etiqueta se muestra **aunque** [showLabels] esté apagado (p. ej. el tutorial
    // resalta el nombre del vértice que explica). Ignorado si [showLabels] ya está activo.
    forcedLabelVertices: Set<Vertex> = emptySet(),
    guideArrows: List<MpMove> = emptyList(),
    // Indicadores de jugador junto a cada base (color + Humano/IA + Nº de piezas). El detalle en portrait
    // los apaga y los reubica como leyenda fuera del tablero.
    showBaseIndicators: Boolean = true,
) {
    val boardColors = getBoardColors()
    val edgeColor = boardColors.boardEdgeColor.copy(alpha = 0.8f)
    val labelColor = boardColors.textColor

    // Progreso del último movimiento (0→1): primero desliza la pieza, luego voltea las capturadas
    // (misma lógica que single). `remember(moveCount)` inicializa el valor de forma SÍNCRONA al
    // detectar la jugada, evitando el frame en que la pieza aparecía ya en destino antes de animar.
    // El deslizamiento y el morph de conversión NO dependen de [animate] (paridad con single, donde
    // "animar efectos" sólo apaga los efectos decorativos, no la animación del movimiento).
    // Guard de primera composición: al (re)montar el tablero (p. ej. al volver de single↔multi) la última
    // jugada ya está en su posición final y NO se re-anima; solo se anima una jugada que llega como cambio
    // de `moveCount` con el tablero ya montado.
    var mounted by remember { mutableStateOf(false) }
    val shouldAnimateMove = mounted && lastMove != null && moveCount > 0 && !suppressMoveAnimation
    val moveProgress = remember(moveCount) { Animatable(if (shouldAnimateMove) 0f else 1f) }

    // Destinos legales de la pieza recién movida — se resaltan brevemente al terminar el deslizamiento
    // (paridad con single: `createValidMovesHighlights` sobre `getValidVertex` del destino). El turno
    // ya avanzó, por eso se consulta contra el asiento del **dueño** de la pieza en el destino, no el
    // que está en turno. `legalMovesFor` no exige que sea su turno.
    val postMoveTargets = remember(moveCount) {
        when {
            lastMove == null || state.isGameOver -> emptySet()
            else -> {
                val owner = state.pieces[lastMove.to]?.owner
                val seat = owner?.let { o -> state.seats.firstOrNull { it.color == o } }
                if (seat == null) {
                    emptySet()
                } else {
                    MpRules.legalMovesFor(state, seat).asSequence()
                        .filter { it.from == lastMove.to }
                        .map { it.to }
                        .toSet()
                }
            }
        }
    }
    var showPostMove by remember { mutableStateOf(false) }

    LaunchedEffect(moveCount) {
        if (!mounted) {
            mounted = true
            return@LaunchedEffect
        }
        if (shouldAnimateMove) moveProgress.animateTo(1f, animationSpec = tween(ANIMATION_MS))
        // Tras el deslizamiento, resalta los destinos alcanzables de la pieza recién llegada.
        if (shouldAnimateMove && postMoveTargets.isNotEmpty()) {
            showPostMove = true
            delay(POST_MOVE_MS.milliseconds)
            showPostMove = false
        }
    }

    // Tipo de animación de conversión por pieza capturada, decidido **una vez por jugada** (estable
    // durante toda la animación) según la preferencia [conversionStyle] de Settings — como single.
    val conversionTypes = remember(moveCount) {
        converted.keys.associateWith { conversionStyle.resolveType() }
    }

    // Tick a ~60fps mientras haya una pieza seleccionada, un pre-movimiento activo o flechas guía
    // del tutorial, para animar el selector (anillo rotatorio), el pulso de los vértices
    // alcanzables/amenazados, el halo del pre-move y el parpadeo de las flechas. Independiente de
    // [animate]: el selector, las amenazas y las guías del tutorial animan siempre (aunque "animar
    // efectos" esté apagado — ese flag sólo controla los efectos decorativos de conversión).
    var animTick by remember { mutableLongStateOf(0L) }
    val hasSelection = selection != null
    val preMoveActive = preMoveFrom != null || pendingPreMove != null
    val tickActive = hasSelection || preMoveActive || guideArrows.isNotEmpty()
    LaunchedEffect(tickActive) {
        while (tickActive) {
            animTick = Clock.System.now().toEpochMilliseconds()
            delay(16L.milliseconds)
        }
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // El bloque de gestos se arma una sola vez (`pointerInput(Unit)`), así que capturaría el
    // `onVertexTap` de la primera composición. En online ese lambda captura el estado de la partida →
    // quedaría **congelado en el estado inicial** (una jugada legal ahí, ilegal luego, se rechazaría).
    // `rememberUpdatedState` hace que el gesto invoque siempre el callback más reciente.
    val currentOnVertexTap by rememberUpdatedState(onVertexTap)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it.toSize() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val screen = Board25Geometry.fit(size.toSize())
                        val tapRadius = minOf(size.width, size.height) * 0.06f
                        Board25Geometry.closestVertex(offset, screen, tapRadius)?.let(currentOnVertexTap)
                    }
                },
        ) {
            val screen = Board25Geometry.fit(Size(size.width, size.height))
            val unit = minOf(size.width, size.height)
            val pieceRadius = unit * 0.028f
            val edgeStroke = unit * 0.004f
            val lightOfDay = getLightOfDay(hourOfDay = 12f, baseRadius = pieceRadius)
            // Parámetros de dibujo de piezas (tipo activo + escala/centroide de polígonos), compartidos
            // con StaticBoard25Renderer. Al leer PieceTypeManager dentro del DrawScope el Canvas se
            // redibuja si cambia el tipo. Círculo → cob orgánico; polígono → drawMorphCob N-color.
            val layout = mpPieceLayout(pieceRadius)
            val isPolygon = layout.isPolygon
            val mpPieceType = layout.pieceType
            val polyRadius = layout.polyRadius
            val polyCentroidOffset = layout.polyCentroidOffset
            val moveP = moveProgress.value
            val slide = (moveP / SLIDE_FRACTION).coerceIn(0f, 1f)
            val conversion = ((moveP - SLIDE_FRACTION) / (1f - SLIDE_FRACTION)).coerceIn(0f, 1f)

            // Factor de pulso (0.7–1.0) compartido con los highlights de single. Anima siempre: el
            // resalte de amenazas es parte de la ayuda de juego, no un efecto decorativo.
            val tick = animTick
            val pulse = pulseFactor(tick)

            // Superficie del tablero (Settings): base + perímetro + áreas con paleta + grano,
            // con la misma estratificación que single (drawBoardBackground).
            drawBoard25Board(screen, boardColors, showRegions, showPerimeter, showEdges)

            // Aristas (Settings).
            if (showEdges) {
                Board25.edges.forEach { edge ->
                    drawLine(
                        color = edgeColor,
                        start = screen.getValue(edge.from),
                        end = screen.getValue(edge.to),
                        strokeWidth = edgeStroke,
                    )
                }
            }

            // Vértices — mismo dibujo que single: color por estado (seleccionado / alcanzable /
            // ocupado / normal) + borde tenue.
            if (showVertices) {
                val vertexRadius = unit * 0.015f
                screen.forEach { (vertex, center) ->
                    val vertexColor = when {
                        vertex == selection -> boardColors.vertexSelectedColor
                        vertex in legalTargets -> boardColors.vertexAdjacentColor
                        state.pieces.containsKey(vertex) -> boardColors.vertexOccupiedColor
                        else -> boardColors.boardVertexColor
                    }
                    drawCircle(color = vertexColor, radius = vertexRadius, center = center)
                    drawCircle(
                        color = boardColors.textColor.copy(alpha = 0.3f),
                        radius = vertexRadius,
                        center = center,
                        style = Stroke(width = 1f),
                    )
                }
            }

            // Piezas amenazadas — mismo resaltado de captura que single (recipe CAPTURE), pulsante.
            threatened.forEach { vertex ->
                screen[vertex]?.let { center ->
                    drawVertexHighlightAt(
                        action = HighlightAction.CAPTURE,
                        position = center,
                        pulseRadius = pieceRadius * 0.5f * pulse,
                        colors = boardColors,
                    )
                }
            }

            // Destinos alcanzables de la pieza recién movida (recipe MOVE) — resalte breve al terminar
            // el deslizamiento, paridad con el post-efecto de single. Ligado a "Animar efectos".
            if (animate && showPostMove) {
                postMoveTargets.forEach { vertex ->
                    screen[vertex]?.let { center ->
                        drawVertexHighlightAt(
                            action = HighlightAction.MOVE,
                            position = center,
                            pulseRadius = pieceRadius * 0.5f,
                            colors = boardColors,
                        )
                    }
                }
            }

            // Flechas guía del tutorial (movimientos permitidos del paso interactivo) — misma
            // flecha parpadeante que los pasos interactivos del tutorial single. Se dibujan **bajo
            // las piezas** (sobre los vértices/aristas) para no taparlas. El grosor conserva la
            // proporción flecha/pieza de single (0.03/0.04 = 0.75 del radio de pieza).
            guideArrows.forEach { move ->
                drawArrowHighlight(
                    from = screen.getValue(move.from),
                    to = screen.getValue(move.to),
                    pieceRadius = pieceRadius,
                    strokeWidth = pieceRadius * 0.75f,
                    colors = boardColors,
                    // La guía del tutorial parpadea siempre — "animar efectos" no aplica a tutoriales.
                    pulse = true,
                )
            }

            // Piezas — cob de Tarati. La del último movimiento se desliza; las capturadas voltean
            // (misma transformación que single, con los colores de cada jugador).
            val slidingMove = lastMove?.takeIf { slide < 1f }
            val movingTo = slidingMove?.to
            val movingFrom = slidingMove?.let { screen.getValue(it.from) }
            val convertingActive = moveP < 1f && converted.isNotEmpty()

            // Posición interpolada de la pieza que se desliza — origen de la estela y de los arcos en
            // vuelo (paridad con single, que los emite desde la posición actual de la pieza en movimiento).
            val movingPos = if (movingTo != null && movingFrom != null) {
                lerp(movingFrom, screen.getValue(movingTo), slide)
            } else {
                null
            }

            // Efectos "en vuelo" DURANTE el deslizamiento, saliendo de la pieza en movimiento (bajo las
            // piezas) y con "Animar efectos" activo — paridad exacta con single:
            //  · estela dinámica de la pieza hacia su destino + resalte del vértice de destino
            //    (createMoveDynamicHighlight);
            //  · onda de arcos (TRANSFORMATION) o rayo eléctrico (FLIP) hacia cada pieza a convertir.
            if (animate && movingPos != null && movingTo != null) {
                val destPos = screen.getValue(movingTo)
                drawDynamicFireballEdgeHighlight(
                    DynamicEdgeHighlight(from = movingPos, to = destPos, pulse = true),
                    size,
                    boardColors,
                )
                // Resalte pulsante del destino en el tramo final del deslizamiento (segundo componente
                // de createMoveDynamicHighlight, que en single aparece con startDelay).
                if (slide > SLIDE_FRACTION) {
                    drawVertexHighlightAt(
                        action = HighlightAction.MOVE,
                        position = destPos,
                        pulseRadius = pieceRadius * 0.5f * pulseFactor(Clock.System.now().toEpochMilliseconds()),
                        colors = boardColors,
                    )
                }
                converted.keys.forEach { captured ->
                    screen[captured]?.let { target ->
                        when (conversionTypes[captured] ?: ConversionAnimationType.FROM_CENTER) {
                            ConversionAnimationType.FROM_CENTER, ConversionAnimationType.FROM_BORDER ->
                                drawForceArcDynamicHighlight(movingPos, target, slide, boardColors)

                            ConversionAnimationType.FLIP -> {
                                val (minSeg, maxSeg) = getHighlightsSegmentsRange(movingPos, target)
                                drawDynamicEdgeElectricHighlight(
                                    from = movingPos,
                                    to = target,
                                    variationFactor = Random.nextFloat(),
                                    randomSegments = Random.nextInt(minSeg, maxSeg),
                                    colors = boardColors,
                                )
                            }
                        }
                    }
                }
            }

            // Estallido de anillos al "llegar" los arcos (TRANSFORMATION) — DURANTE la conversión, ya en
            // destino, como single (impacto a mitad de la animación de captura). El morph de cada pieza
            // capturada se dibuja en el bucle de piezas.
            if (animate && convertingActive && conversion > 0f) {
                converted.keys.forEach { captured ->
                    screen[captured]?.let { target ->
                        when (conversionTypes[captured] ?: ConversionAnimationType.FROM_CENTER) {
                            ConversionAnimationType.FROM_CENTER, ConversionAnimationType.FROM_BORDER -> {
                                val impact = ((conversion - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                if (impact > 0f) drawForceArcImpactHighlight(target, impact, boardColors)
                            }

                            ConversionAnimationType.FLIP -> Unit
                        }
                    }
                }
            }

            state.pieces.entries
                .sortedBy { if (it.key == movingTo) 1 else 0 }
                .forEach { (vertex, piece) ->
                    val center = if (vertex == movingTo && movingFrom != null) {
                        lerp(movingFrom, screen.getValue(vertex), slide)
                    } else {
                        screen.getValue(vertex)
                    }
                    // Centro de dibujo del polígono: corregido para que el centroide caiga sobre el vértice.
                    val drawC = center - polyCentroidOffset
                    // Tilt orgánico (solo polígonos): estable por vértice; si la pieza se desliza a otro
                    // vértice, interpola del tilt de origen al de destino (paridad con single).
                    val tilt = if (isPolygon) {
                        if (vertex == movingTo) {
                            val from = vertexTilt(slidingMove.from)
                            from + (vertexTilt(vertex) - from) * slide
                        } else {
                            vertexTilt(vertex)
                        }
                    } else {
                        0f
                    }
                    val oldOwner = converted[vertex]
                    if (convertingActive && oldOwner != null && vertex != movingTo) {
                        // Conversión del dueño anterior al nuevo (progreso `conversion`), con el tipo de
                        // animación elegido para esta jugada (FROM_CENTER / FROM_BORDER / FLIP).
                        val source = PlayerPalette.pieceColor(oldOwner)
                        val target = PlayerPalette.pieceColor(piece.owner)
                        val wave = PlayerPalette.fill(piece.owner)
                        val convType = conversionTypes[vertex] ?: ConversionAnimationType.FROM_CENTER
                        if (isPolygon) rotate(degrees = tilt, pivot = drawC) {
                            // Conversión poligonal N-color (núcleos compartidos con single).
                            when (convType) {
                                ConversionAnimationType.FROM_CENTER -> drawMorphConversionFromCenter(
                                    position = drawC,
                                    radius = polyRadius,
                                    conversionProgress = conversion,
                                    shape = mpPieceType.shape,
                                    sourceShape = mpCobShape(mpPieceType, source),
                                    targetShape = mpCobShape(mpPieceType, target),
                                    waveColor = wave,
                                    boardColors = boardColors,
                                    hourOfDay = 12f,
                                )

                                ConversionAnimationType.FROM_BORDER -> drawMorphConversionFromBorder(
                                    position = drawC,
                                    radius = polyRadius,
                                    conversionProgress = conversion,
                                    shape = mpPieceType.shape,
                                    sourceShape = mpCobShape(mpPieceType, source),
                                    targetShape = mpCobShape(mpPieceType, target),
                                    waveColor = wave,
                                    boardColors = boardColors,
                                    hourOfDay = 12f,
                                )

                                ConversionAnimationType.FLIP -> drawMorphConversionFlip(
                                    position = drawC,
                                    radius = polyRadius,
                                    conversionProgress = conversion,
                                    shape = mpPieceType.shape,
                                    borderPattern = mpPieceType.borderPattern,
                                    centerMotif = CenterMotif.None,
                                    sourceColors = source.toShapeColors(),
                                    targetColors = target.toShapeColors(),
                                    flipSeed = vertex.hashCode(),
                                    boardColors = boardColors,
                                    hourOfDay = 12f,
                                )
                            }
                        } else {
                            when (convType) {
                                ConversionAnimationType.FROM_CENTER -> drawConversionFromCenter(
                                    position = center,
                                    radius = pieceRadius,
                                    conversionProgress = conversion,
                                    hourOfDay = 12f,
                                    lightOfDay = lightOfDay,
                                    waveColor = wave,
                                    sourceColors = source,
                                    targetColors = target,
                                    colors = boardColors,
                                )

                                ConversionAnimationType.FROM_BORDER -> drawConversionFromBorder(
                                    position = center,
                                    radius = pieceRadius,
                                    conversionProgress = conversion,
                                    waveColor = wave,
                                    hourOfDay = 12f,
                                    sourceColors = source,
                                    targetColors = target,
                                    colors = boardColors,
                                )

                                ConversionAnimationType.FLIP -> drawCoinFlip(
                                    position = center,
                                    radius = pieceRadius,
                                    conversionProgress = conversion,
                                    hourOfDay = 12f,
                                    lightOfDay = lightOfDay,
                                    sourceColors = source,
                                    targetColors = target,
                                    flipSeed = vertex.hashCode(),
                                    colors = boardColors,
                                )
                            }
                        }
                    } else {
                        drawMpRestingPiece(
                            center = center,
                            layout = layout,
                            pieceRadius = pieceRadius,
                            tilt = tilt,
                            pieceColor = PlayerPalette.pieceColor(piece.owner),
                            lightOfDay = lightOfDay,
                            boardColors = boardColors,
                        )
                    }
                }

            // Resalte de selección — mismo anillo giratorio que Tarati (circular o poligonal según el tipo).
            selection?.let { vertex ->
                val piece = state.pieces[vertex] ?: return@let
                screen[vertex]?.let { center ->
                    // El anillo del selector gira siempre (ayuda de juego, no efecto decorativo).
                    val selTime = tick
                    if (isPolygon) {
                        drawPolygonSelection(
                            position = center - polyCentroidOffset,
                            radius = polyRadius,
                            pieceType = mpPieceType,
                            baseColor = PlayerPalette.fill(piece.owner),
                            colors = boardColors,
                            selectionTimeMs = selTime,
                        )
                    } else {
                        drawSelection(
                            position = center,
                            radius = pieceRadius,
                            baseColor = PlayerPalette.fill(piece.owner),
                            colors = boardColors,
                            timeMs = selTime,
                        )
                    }
                }
            }

            // Pre-movimiento (turno ajeno) — mismos gráficos que single (halo + dots + flecha),
            // vía el núcleo compartido de DrawPreMove con la geometría de Board25.
            drawPreMoveSelection(
                preMoveFromVertex = preMoveFrom,
                preMoveValidTargets = preMoveTargets,
                positionOf = screen::getValue,
                pieceRadius = pieceRadius,
                colors = boardColors,
                tickMs = tick,
            )
            drawPreMoveArrow(
                from = pendingPreMove?.from,
                to = pendingPreMove?.to,
                positionOf = screen::getValue,
                pieceRadius = pieceRadius,
                colors = boardColors,
            )
        }

        if (canvasSize != Size.Zero) {
            val screenPositions = Board25Geometry.fit(canvasSize)

            // Etiquetas de vértice (Settings). Overlay de Text — funciona en todas las plataformas,
            // incl. web, donde el texto en Canvas (Skiko WASM) no está disponible. Con las etiquetas
            // apagadas, se muestran igual las de [forcedLabelVertices] (las que resalta el tutorial).
            val labelSize = minOf(canvasSize.width, canvasSize.height) * 0.022f
            val labeled = when {
                showLabels -> screenPositions
                forcedLabelVertices.isNotEmpty() -> screenPositions.filterKeys { it in forcedLabelVertices }
                else -> emptyMap()
            }
            if (labeled.isNotEmpty()) {
                VertexLabels(
                    positions = labeled,
                    textSizePx = labelSize,
                    color = labelColor,
                )
            }

            // Indicadores de jugador junto a cada base (color + Humano/IA + contador de piezas).
            if (showBaseIndicators) {
                BaseIndicators(
                    state = state,
                    seatIsAI = seatIsAI,
                    positions = screenPositions,
                )
            }
        }
    }
}

/**
 * Render **estático** del tablero `25` (sin interacción ni animación) con una configuración inicial
 * de 6 jugadores, para previews (selector de paletas, vitrina de la Tienda). Reusa la superficie/áreas
 * de [Board25View] y el cob de Tarati; el color del tablero sale de la paleta activa
 * (`getBoardColors()` → `LocalBoardPalette`), igual que `StaticBoardRenderer` en single.
 */
@Composable
fun StaticBoard25Renderer(modifier: Modifier) {
    val state = remember { MpSetup.initialState(MpSetup.MAX_PLAYERS) }
    StaticBoard25Renderer(modifier, state.pieces)
}

/**
 * Variante estática con [pieces] arbitrarias (color de cada dueño), para **miniaturas** de una
 * partida en curso (p. ej. reconstruidas de una FEN vía `MpNotation.parsePosition`).
 */
@Composable
fun StaticBoard25Renderer(modifier: Modifier, pieces: Map<Vertex, Piece>) {
    val boardColors = getBoardColors()
    val edgeColor = boardColors.boardEdgeColor.copy(alpha = 0.8f)
    Canvas(modifier = modifier) {
        val screen = Board25Geometry.fit(Size(size.width, size.height))
        val unit = minOf(size.width, size.height)
        val pieceRadius = unit * 0.03f
        val edgeStroke = unit * 0.004f
        val lightOfDay = getLightOfDay(hourOfDay = 12f, baseRadius = pieceRadius)
        // Parámetros de dibujo de piezas (tipo activo + escala/centroide), mismos que Board25View para
        // paridad de aspecto. Leído en el DrawScope → redibuja si cambia el tipo de pieza.
        val layout = mpPieceLayout(pieceRadius)

        drawBoard25Board(screen, boardColors, showRegions = true, showPerimeter = false, showEdges = true)

        Board25.edges.forEach { edge ->
            drawLine(
                color = edgeColor,
                start = screen.getValue(edge.from),
                end = screen.getValue(edge.to),
                strokeWidth = edgeStroke,
            )
        }

        pieces.forEach { (vertex, piece) ->
            drawMpRestingPiece(
                center = screen.getValue(vertex),
                layout = layout,
                pieceRadius = pieceRadius,
                tilt = if (layout.isPolygon) vertexTilt(vertex) else 0f,
                pieceColor = PlayerPalette.pieceColor(piece.owner),
                lightOfDay = lightOfDay,
                boardColors = boardColors,
            )
        }
    }
}

// El sombreado del tablero (drawBoard25Board & co.) vive en Board25Drawing.kt; los indicadores de
// asiento y las etiquetas de vértice, en Board25Indicators.kt (mismo paquete).
