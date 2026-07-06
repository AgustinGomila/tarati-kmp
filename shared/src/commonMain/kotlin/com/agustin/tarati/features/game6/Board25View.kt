package com.agustin.tarati.features.game6

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.ui.components.game.draw.board.drawDynamicEdgeElectricHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawForceArcDynamicHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawForceArcImpactHighlight
import com.agustin.tarati.ui.components.game.draw.board.drawPreMoveArrow
import com.agustin.tarati.ui.components.game.draw.board.drawPreMoveSelection
import com.agustin.tarati.ui.components.game.draw.board.drawVertexHighlightAt
import com.agustin.tarati.ui.components.game.draw.board.getHighlightsSegmentsRange
import com.agustin.tarati.ui.components.game.draw.board.getLightOfDay
import com.agustin.tarati.ui.components.game.draw.board.pulseFactor
import com.agustin.tarati.ui.components.game.draw.common.NoiseTexture
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationType
import com.agustin.tarati.ui.components.game.draw.pieces.createOrganicColor
import com.agustin.tarati.ui.components.game.draw.pieces.drawCoinFlip
import com.agustin.tarati.ui.components.game.draw.pieces.drawConversionFromBorder
import com.agustin.tarati.ui.components.game.draw.pieces.drawConversionFromCenter
import com.agustin.tarati.ui.components.game.draw.pieces.drawOrganicCob
import com.agustin.tarati.ui.components.game.draw.pieces.drawSelection
import com.agustin.tarati.ui.components.game.highlights.HighlightAction
import com.agustin.tarati.ui.theme.BoardColors
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

private const val ANIMATION_MS = 420

/** Fracción del progreso del movimiento dedicada al deslizamiento; el resto, a la conversión. */
private const val SLIDE_FRACTION = 0.55f

/**
 * Resuelve la preferencia [ConversionAnimationStyle] a un [ConversionAnimationType] concreto por
 * captura (como single): TRANSFORMATION → centro o borde al azar; FLIP → volteo; SURPRISE → cualquiera
 * de los tres.
 */
private fun resolveConversionType(style: ConversionAnimationStyle): ConversionAnimationType =
    when (style) {
        ConversionAnimationStyle.TRANSFORMATION ->
            listOf(ConversionAnimationType.FROM_CENTER, ConversionAnimationType.FROM_BORDER).random()

        ConversionAnimationStyle.FLIP -> ConversionAnimationType.FLIP
        ConversionAnimationStyle.SURPRISE -> ConversionAnimationType.entries.random()
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
 * @param animate si `false`, sin animación (respeta "animar efectos" de Settings).
 * @param suppressMoveAnimation si `true`, el último movimiento se dibuja ya en destino sin animarlo
 *   (p. ej. al **re-entrar** a una partida online tras cambiar de modo: la jugada ya fue presentada y
 *   no debe "rehacerse"). No afecta a jugadas nuevas.
 * @param showEdges / showVertices / showLabels visibilidad de aristas, puntos de parada y nombres.
 * @param preMoveFrom pieza pre-seleccionada durante el turno ajeno (halo pulsante + dots de destino).
 * @param preMoveTargets destinos legales de [preMoveFrom].
 * @param pendingPreMove pre-movimiento confirmado (flecha), pendiente de ejecución al volver el turno.
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
) {
    val boardColors = getBoardColors()
    val edgeColor = boardColors.boardEdgeColor.copy(alpha = 0.8f)
    val labelColor = boardColors.textColor

    // Progreso del último movimiento (0→1): primero desliza la pieza, luego voltea las capturadas
    // (misma lógica que single). `remember(moveCount)` inicializa el valor de forma SÍNCRONA al
    // detectar la jugada, evitando el frame en que la pieza aparecía ya en destino antes de animar.
    val shouldAnimateMove = animate && lastMove != null && moveCount > 0 && !suppressMoveAnimation
    val moveProgress = remember(moveCount) { Animatable(if (shouldAnimateMove) 0f else 1f) }
    LaunchedEffect(moveCount) {
        if (shouldAnimateMove) moveProgress.animateTo(1f, animationSpec = tween(ANIMATION_MS))
    }

    // Tipo de animación de conversión por pieza capturada, decidido **una vez por jugada** (estable
    // durante toda la animación) según la preferencia [conversionStyle] de Settings — como single.
    val conversionTypes = remember(moveCount) {
        converted.keys.associateWith { resolveConversionType(conversionStyle) }
    }

    // Tick a ~60fps mientras haya una pieza seleccionada o un pre-movimiento activo, para animar el
    // selector (anillo rotatorio), el pulso de los vértices alcanzables/amenazados y el halo del pre-move.
    var animTick by remember { mutableLongStateOf(0L) }
    val hasSelection = selection != null
    val preMoveActive = preMoveFrom != null || pendingPreMove != null
    val tickActive = hasSelection || preMoveActive
    LaunchedEffect(tickActive, animate) {
        while (tickActive && animate) {
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
            val moveP = moveProgress.value
            val slide = (moveP / SLIDE_FRACTION).coerceIn(0f, 1f)
            val conversion = ((moveP - SLIDE_FRACTION) / (1f - SLIDE_FRACTION)).coerceIn(0f, 1f)

            // Factor de pulso (0.7–1.0) compartido con los highlights de single. Estático si las
            // animaciones están desactivadas.
            val tick = animTick
            val pulse = if (animate) pulseFactor(tick) else 1f

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

            // Piezas — cob de Tarati. La del último movimiento se desliza; las capturadas voltean
            // (misma transformación que single, con los colores de cada jugador).
            val slidingMove = lastMove?.takeIf { slide < 1f }
            val movingTo = slidingMove?.to
            val movingFrom = slidingMove?.let { screen.getValue(it.from) }
            val convertingActive = moveP < 1f && converted.isNotEmpty()

            // Efecto de captura (dibujado **bajo** las piezas), como single: el efecto de borde depende
            // del tipo de conversión de cada pieza. TRANSFORMATION (centro/borde) → onda de **arcos** que
            // viaja desde la pieza capturadora + estallido de anillos al llegar. FLIP → **rayo eléctrico**
            // (parpadea). Solo durante la conversión y con "Animar efectos" activo.
            if (animate && convertingActive && conversion > 0f) {
                lastMove?.let { screen[it.to] }?.let { origin ->
                    converted.keys.forEach { captured ->
                        screen[captured]?.let { target ->
                            when (conversionTypes[captured] ?: ConversionAnimationType.FROM_CENTER) {
                                ConversionAnimationType.FROM_CENTER, ConversionAnimationType.FROM_BORDER -> {
                                    drawForceArcDynamicHighlight(origin, target, conversion, boardColors)
                                    // El estallido aparece cuando los arcos "llegan" (último tramo).
                                    val impact = ((conversion - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                    if (impact > 0f) drawForceArcImpactHighlight(target, impact, boardColors)
                                }

                                ConversionAnimationType.FLIP -> {
                                    val (minSeg, maxSeg) = getHighlightsSegmentsRange(origin, target)
                                    drawDynamicEdgeElectricHighlight(
                                        from = origin,
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
            }

            state.pieces.entries
                .sortedBy { if (it.key == movingTo) 1 else 0 }
                .forEach { (vertex, piece) ->
                    val center = if (vertex == movingTo && movingFrom != null) {
                        lerp(movingFrom, screen.getValue(vertex), slide)
                    } else {
                        screen.getValue(vertex)
                    }
                    val oldOwner = converted[vertex]
                    if (convertingActive && oldOwner != null && vertex != movingTo) {
                        // Conversión del dueño anterior al nuevo (progreso `conversion`), con el tipo de
                        // animación elegido para esta jugada (FROM_CENTER / FROM_BORDER / FLIP).
                        val source = PlayerPalette.pieceColor(oldOwner)
                        val target = PlayerPalette.pieceColor(piece.owner)
                        val wave = PlayerPalette.fill(piece.owner)
                        when (conversionTypes[vertex] ?: ConversionAnimationType.FROM_CENTER) {
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
                    } else {
                        val pieceColor = PlayerPalette.pieceColor(piece.owner)
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

            // Resalte de selección — mismo anillo giratorio que Tarati.
            selection?.let { vertex ->
                val piece = state.pieces[vertex] ?: return@let
                screen[vertex]?.let { center ->
                    drawSelection(
                        position = center,
                        radius = pieceRadius,
                        baseColor = PlayerPalette.fill(piece.owner),
                        colors = boardColors,
                        timeMs = if (animate) tick else 0L,
                    )
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
            // incl. web, donde el texto en Canvas (Skiko WASM) no está disponible.
            if (showLabels) {
                val labelSize = minOf(canvasSize.width, canvasSize.height) * 0.022f
                VertexLabels(
                    positions = screenPositions,
                    textSizePx = labelSize,
                    color = labelColor,
                )
            }

            // Indicadores de jugador junto a cada base (color + Humano/IA + contador de piezas).
            BaseIndicators(
                state = state,
                seatIsAI = seatIsAI,
                positions = screenPositions,
            )
        }
    }
}

/**
 * Overlay con un indicador por asiento, ubicado **afuera de su base** (empujado desde el punto
 * medio de las puntas E hacia el exterior). Muestra el color del jugador, si es Humano o IA, y su
 * cantidad de piezas en el tablero; resalta el turno actual y atenúa a los retirados.
 */
@Composable
private fun BaseIndicators(
    state: MpGameState,
    seatIsAI: List<Boolean>,
    positions: Map<Vertex, Offset>,
) {
    val center = positions[Board25.A1] ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        state.seats.forEachIndexed { index, seat ->
            val base = Board25.baseById(seat.baseId)
            val e0 = positions[base.eTips[0]] ?: return@forEachIndexed
            val e1 = positions[base.eTips[1]] ?: return@forEachIndexed
            val eMid = (e0 + e1) / 2f
            val dir = eMid - center
            val len = hypot(dir.x, dir.y)
            val anchor = if (len == 0f) eMid else {
                val r = dir / len          // radial unitario (centro → base)
                val layer = len / 4f        // la punta E está a 4 capas del centro
                // Colocación en el margen más holgado según la orientación de la base:
                //  N/S (verticales) → al costado; NW/NE (arriba) → arriba; SW/SE (abajo) → abajo.
                val (push, dist) = when {
                    abs(r.x) < 0.5f -> Offset(1f, 0f) to layer * 1.25f
                    r.y < 0f -> Offset(0f, -1f) to layer
                    else -> Offset(0f, 1f) to layer
                }
                eMid + push * dist
            }
            SeatIndicator(
                color = seat.color,
                isAI = seatIsAI.getOrElse(index) { false },
                count = state.pieceCount(seat.color),
                retired = seat.status == SeatStatus.RETIRED,
                isCurrent = index == state.currentSeatIndex && !state.isGameOver,
                anchor = anchor,
            )
        }
    }
}

@Composable
private fun SeatIndicator(
    color: PlayerColor,
    isAI: Boolean,
    count: Int,
    retired: Boolean,
    isCurrent: Boolean,
    anchor: Offset,
) {
    val fill = PlayerPalette.fill(color)
    val borderCol = PlayerPalette.border(color)
    val shape = RoundedCornerShape(50)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Row(
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    (anchor.x - boxSize.width / 2f).roundToInt(),
                    (anchor.y - boxSize.height / 2f).roundToInt(),
                )
            }
            .onSizeChanged { boxSize = it }
            .alpha(if (retired) 0.4f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) fill else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2f
            drawCircle(fill, radius = r, center = Offset(r, r))
            drawCircle(borderCol, radius = r, center = Offset(r, r), style = Stroke(width = r * 0.2f))
        }
        Icon(
            imageVector = if (isAI) TaratiIcons.SmartToy else TaratiIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VertexLabels(
    positions: Map<Vertex, Offset>,
    textSizePx: Float,
    color: Color,
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        positions.forEach { (vertex, pos) ->
            val x = with(density) { (pos.x - textSizePx * 1.2f).toDp() }
            val y = with(density) { (pos.y - textSizePx * 1.2f).toDp() }
            val fontSize = with(density) { textSizePx.toSp() }
            Text(
                text = vertex.name,
                color = color,
                fontSize = fontSize,
                lineHeight = fontSize,
                modifier = Modifier.absoluteOffset(x = x, y = y),
            )
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
            val center = screen.getValue(vertex)
            val pieceColor = PlayerPalette.pieceColor(piece.owner)
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
}

/**
 * Sombreado de las áreas del tablero `25` con la paleta activa (mismos tokens que single:
 * fondo de tablero + `boardPatternColor1/2/3`), sobre la geometría propia de Board25.
 */
private fun DrawScope.drawBoard25Board(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    showRegions: Boolean,
    showPerimeter: Boolean,
    showEdges: Boolean,
) {
    if (!showRegions && !showPerimeter) return

    // Superficie base del tablero.
    val minX = screen.values.minOf { it.x }
    val maxX = screen.values.maxOf { it.x }
    val minY = screen.values.minOf { it.y }
    val maxY = screen.values.maxOf { it.y }
    // Margen más ancho que el semigrosor del perímetro → el RoundRect de fondo lo contiene.
    val margin = minOf(size.width, size.height) * 0.065f
    drawRoundRect(
        color = colors.boardBackground.copy(alpha = 0.6f),
        topLeft = Offset(minX - margin, minY - margin),
        size = Size(maxX - minX + 2 * margin, maxY - minY + 2 * margin),
        cornerRadius = CornerRadius(16f),
    )

    // Perímetro (bajo las áreas, como single).
    if (showPerimeter) drawBoard25Perimeter(screen, colors, showEdges)

    if (showRegions) drawBoard25RegionFills(screen, colors, drawBorders = true)

    // Textura de grano sobre toda la superficie del tablero, igual que single.
    with(NoiseTexture) {
        applyNoise(
            topLeft = Offset(minX - margin, minY - margin),
            size = Size(maxX - minX + 2 * margin, maxY - minY + 2 * margin),
            cornerRadius = CornerRadius(16f),
            alpha = 0.07f,
        )
    }
}

/** Perímetro cosmético del tablero `25` (borde grueso + luz/sombra), como `drawPerimeter` de single. */
private fun DrawScope.drawBoard25Perimeter(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    edgesVisible: Boolean,
) {
    val path = Path().apply {
        Board25.externalBoundary.forEachIndexed { i, vertex ->
            val p = screen.getValue(vertex)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    val bounds = path.getBounds()
    val vertexDistance = minOf(bounds.width, bounds.height) * 0.10f

    if (edgesVisible) {
        drawPath(
            path = path,
            color = colors.boardEdgeColor.copy(alpha = 0.2f),
            style = Stroke(width = vertexDistance * 1.15f, join = StrokeJoin.Round),
        )
    }
    drawPath(
        path = path,
        color = colors.boardPerimeterColor,
        style = Stroke(width = vertexDistance, join = StrokeJoin.Round),
    )

    // Bordes de luz (lado del sol) y sombra (opuesto), como single.
    val lightOfDay = getLightOfDay(hourOfDay = 12f, baseRadius = 32f)
    val lightPath = Path().apply {
        addPath(path)
        translate(Offset(lightOfDay.sunPosition.x * 2f, lightOfDay.sunPosition.y * 2f))
    }
    drawPath(lightPath, color = colors.boardPatternColor3.copy(alpha = 0.4f), style = Stroke(width = 4f))
    val shadowPath = Path().apply {
        addPath(path)
        translate(Offset(-lightOfDay.sunPosition.x * 4f, -lightOfDay.sunPosition.y * 4f))
    }
    drawPath(shadowPath, color = colors.boardVertexColor.copy(alpha = 0.4f), style = Stroke(width = 4f))
}

/**
 * Rellena las regiones del tablero `25` con la paleta (sin fondo base ni grano). Reutilizado por el
 * sombreado del tablero real y por la silueta del fondo decorativo ([drawBoard25Silhouette]).
 */
private fun DrawScope.drawBoard25RegionFills(
    screen: Map<Vertex, Offset>,
    colors: BoardColors,
    drawBorders: Boolean,
) {
    val border = colors.boardPatternBorderColor
    Board25.centralRegions.forEachIndexed { i, region ->
        fillRegion(
            region,
            screen,
            if (i % 2 == 0) colors.boardPatternColor3 else colors.boardPatternColor2,
            border,
            drawBorders
        )
    }
    Board25.circumferenceRegions.forEachIndexed { i, region ->
        fillRegion(
            region,
            screen,
            if (i % 2 == 0) colors.boardPatternColor3 else colors.boardPatternColor1,
            border,
            drawBorders
        )
    }
    // Bandas C→D → color alternativo de las domésticas.
    Board25.bandRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor2, border, drawBorders)
    }
    // Cuadrados de base → color principal (alterna con la banda adyacente).
    Board25.baseSquareRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor1, border, drawBorders)
    }
    Board25.connectorSideRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor3, border, drawBorders)
    }
    // Triángulos puntiagudos del conector → color alternativo del centro.
    Board25.connectorTipRegions.forEach {
        fillRegion(it, screen, colors.boardPatternColor2, border, drawBorders)
    }
}

/** Silueta del tablero `25` para el fondo decorativo (solo rellenos de regiones, sin bordes). */
internal fun DrawScope.drawBoard25Silhouette(colors: BoardColors, canvasSize: Size) {
    val screen = Board25Geometry.fit(canvasSize)
    drawBoard25RegionFills(screen, colors, drawBorders = false)
}

private fun DrawScope.fillRegion(
    region: Region,
    screen: Map<Vertex, Offset>,
    fill: Color,
    border: Color,
    drawBorder: Boolean,
) {
    val path = Path().apply {
        region.vertices.forEachIndexed { i, vertex ->
            val p = screen.getValue(vertex)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(path = path, color = fill, style = Fill)
    if (drawBorder) {
        drawPath(path = path, color = border.copy(alpha = 0.2f), style = Stroke(width = 1f))
    }
}
