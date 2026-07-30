package com.agustin.tarati.ui.components.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.buildPositionCache
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.ui.components.game.BoardState
import com.agustin.tarati.ui.components.game.animation.AnimatedCob
import com.agustin.tarati.ui.components.game.draw.board.drawAllPieces
import com.agustin.tarati.ui.components.game.draw.board.drawBoardBackground
import com.agustin.tarati.ui.components.game.draw.board.drawEdges
import com.agustin.tarati.ui.components.game.draw.board.drawVertices
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationType
import com.agustin.tarati.ui.components.game.draw.pieces.resolveType
import com.agustin.tarati.ui.theme.getBoardColors


/**
 * Renderiza un tablero estático (sin animaciones) en un único [Canvas].
 *
 * A diferencia del tablero de juego en vivo, que usa dos Canvas independientes
 * (uno estático para aristas/vértices y otro dinámico para piezas/highlights),
 * aquí toda la escena se dibuja en un solo pase. Esto elimina el desfase
 * de 1-2 frames entre el tablero y las piezas que ocurría al animar el
 * contenedor padre (expansión/colapso de paneles en Detalle de Partida).
 */
@Composable
fun StaticBoardRenderer(
    modifier: Modifier,
    gameState: GameState,
) {
    val boardState =
        BoardState(
            gameState = gameState,
            boardVisualState = BoardVisualState().copy(animateEffects = false, perimeterVisible = false),
            aiEnabled = false,
        )

    val boardColors = getBoardColors()
    val density = LocalDensity.current
    val vWidth = with(density) { 60.dp.toPx() }
    val orientation = boardState.boardOrientation

    Canvas(modifier = modifier) {
        val positionCache = buildPositionCache(size, orientation)

        drawBoardBackground(
            canvasSize = size,
            orientation = orientation,
            edgesVisible = boardState.boardVisualState.edgesVisibles,
            regionsVisible = boardState.boardVisualState.regionsVisibles,
            perimeterVisible = boardState.boardVisualState.perimeterVisible,
            colors = boardColors,
        )

        drawEdges(
            canvasSize = size,
            orientation = orientation,
            boardState = boardState,
            colors = boardColors,
        )

        drawVertices(
            canvasSize = size,
            vWidth = vWidth,
            selectedVertex = null,
            adjacentVertexes = emptyList(),
            boardState = boardState,
            colors = boardColors,
        )

        drawAllPieces(
            staticCobs = gameState.cobs,
            animatedPieces = emptyMap(),
            positionCache = positionCache,
            orientation = orientation,
            selectedPiece = null,
            colors = boardColors,
        )
    }
}

/** Duración del deslizamiento de la pieza al avanzar una jugada en el replay (ms) — igual que MP. */
private const val REPLAY_SLIDE_MS = 420

/** Duración del volteo de las piezas capturadas, encadenado tras el deslizamiento. */
private const val REPLAY_CONVERSION_MS = 300

/**
 * Variante de [StaticBoardRenderer] para el **detalle/replay** que anima el **avance** de una jugada: la
 * pieza que se mueve se desliza de origen a destino y, al llegar, las piezas capturadas **voltean** a su
 * nuevo color (paridad con el detalle MP, que ya anima ambas cosas). En retrocesos, saltos o sin jugada
 * hace snap idéntico a [StaticBoardRenderer].
 *
 * @param gameState estado ya en la posición de destino (lo que se muestra al terminar).
 * @param previousState estado antes de la jugada avanzada; `null` = snap (sin animación).
 * @param lastMove jugada aplicada al avanzar; `null` = snap. Una promoción in-place (`from == to`) no desliza.
 * @param animationKey se incrementa en cada avance para (re)disparar la animación.
 *
 * La conversión de las capturas usa **siempre** el estilo Transformación (centro/borde, nunca flip) en la
 * pantalla de Detalle de Partidas, igual que el detalle MP.
 */
@Composable
fun ReplayBoardRenderer(
    modifier: Modifier,
    gameState: GameState,
    previousState: GameState?,
    lastMove: Move?,
    animationKey: Int,
) {
    val boardState =
        BoardState(
            gameState = gameState,
            boardVisualState = BoardVisualState().copy(animateEffects = false, perimeterVisible = false),
            aiEnabled = false,
        )

    val boardColors = getBoardColors()
    val density = LocalDensity.current
    val vWidth = with(density) { 60.dp.toPx() }
    val orientation = boardState.boardOrientation

    // Solo anima en avances reales: hay estado previo, hay jugada y ésta desplaza la pieza (no promoción).
    val sliding = previousState != null && lastMove != null && lastMove.from != lastMove.to

    // Piezas capturadas (cambian de dueño entre el estado previo y el nuevo) + su tipo de conversión,
    // elegido una vez por jugada (estable durante toda la animación).
    val conversions: Map<Vertex, ConversionAnimationType> = remember(animationKey) {
        if (previousState == null || lastMove == null || !sliding) {
            emptyMap()
        } else {
            gameState.cobs.keys
                .filter { v ->
                    v != lastMove.to && v != lastMove.from &&
                            previousState.cobs[v]?.color?.let { it != gameState.cobs[v]?.color } == true
                }
                .associateWith { ConversionAnimationStyle.TRANSFORMATION.resolveType() }
        }
    }

    val hasConversions = conversions.isNotEmpty()
    val totalMs = REPLAY_SLIDE_MS + if (hasConversions) REPLAY_CONVERSION_MS else 0
    // Fracción del progreso dedicada al deslizamiento; el resto, al volteo de las capturadas.
    val slideFraction = REPLAY_SLIDE_MS.toFloat() / totalMs

    val progress = remember(animationKey) { Animatable(if (sliding) 0f else 1f) }
    LaunchedEffect(animationKey) {
        if (sliding) progress.animateTo(1f, tween(totalMs, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val positionCache = buildPositionCache(size, orientation)

        drawBoardBackground(
            canvasSize = size,
            orientation = orientation,
            edgesVisible = boardState.boardVisualState.edgesVisibles,
            regionsVisible = boardState.boardVisualState.regionsVisibles,
            perimeterVisible = boardState.boardVisualState.perimeterVisible,
            colors = boardColors,
        )

        drawEdges(
            canvasSize = size,
            orientation = orientation,
            boardState = boardState,
            colors = boardColors,
        )

        drawVertices(
            canvasSize = size,
            vWidth = vWidth,
            selectedVertex = null,
            adjacentVertexes = emptyList(),
            boardState = boardState,
            colors = boardColors,
        )

        val p = progress.value
        if (sliding && p < 1f) {
            // Fase 1 (slide): la pieza viaja; el resto conserva posición/colores previos. Fase 2 (conversion):
            // al llegar el atacante, las capturadas voltean a su nuevo color. Fuera de la animación (p ≥ 1)
            // se hace snap a `gameState`.
            val slide = (p / slideFraction).coerceIn(0f, 1f)
            val conversion = if (hasConversions) {
                ((p - slideFraction) / (1f - slideFraction)).coerceIn(0f, 1f)
            } else {
                0f
            }

            val animated = HashMap<Vertex, AnimatedCob>(conversions.size + 1)
            // Pieza que se mueve (se desliza origen → destino) — mismo `AnimatedCob` que el juego en vivo,
            // que usa la forma **final** (ya promovida a Rok si la jugada promocionó).
            val movingCob = gameState.cobs[lastMove.to] ?: previousState.cobs[lastMove.from]
            if (movingCob != null) {
                animated[lastMove.to] = AnimatedCob(
                    vertex = lastMove.to,
                    cob = movingCob,
                    currentPos = lastMove.from,
                    targetPos = lastMove.to,
                    animationProgress = slide,
                )
            }
            // Piezas capturadas: voltean a su nuevo color una vez llegado el atacante (`conversion > 0`).
            // Construcción idéntica a `BoardAnimationViewModel.animateDetectedCaptures` (cob = pieza nueva).
            if (conversion > 0f) {
                conversions.forEach { (v, type) ->
                    val newCob = gameState.cobs[v] ?: return@forEach
                    animated[v] = AnimatedCob(
                        vertex = v,
                        cob = newCob,
                        currentPos = v,
                        targetPos = v,
                        targetColor = newCob.color,
                        conversionProgress = conversion,
                        isConverting = true,
                        conversionType = type,
                    )
                }
            }
            // El resto (incluidas las capturadas mientras `conversion == 0`) se dibuja con el estado previo.
            val excluded = animated.keys + lastMove.from
            val staticCobs = previousState.cobs.filterKeys { it !in excluded }
            drawAllPieces(
                staticCobs = staticCobs,
                animatedPieces = animated,
                positionCache = positionCache,
                orientation = orientation,
                selectedPiece = null,
                colors = boardColors,
            )
        } else {
            drawAllPieces(
                staticCobs = gameState.cobs,
                animatedPieces = emptyMap(),
                positionCache = positionCache,
                orientation = orientation,
                selectedPiece = null,
                colors = boardColors,
            )
        }
    }
}