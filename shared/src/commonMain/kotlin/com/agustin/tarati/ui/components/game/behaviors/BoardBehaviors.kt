package com.agustin.tarati.ui.components.game.behaviors

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerInputScope
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.GameBoard.isValidMove
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.findClosestVertex
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.PlatformLogger

/**
 * Activado durante el turno de la IA cuando pre-moves está habilitado.
 * Dirige los taps a [handlePreMoveTap] en lugar del flujo normal.
 *
 * @param preMoveFrom Origen ya pre-seleccionado, o `null` si el usuario aún no eligió pieza.
 * @param humanColor  Color del bando humano (el que puede pre-mover).
 */
data class PreMoveContext(
    val preMoveFrom: Vertex?,
    val humanColor: CobColor,
)

suspend fun PointerInputScope.tapGestures(
    visualWidth: Float,
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    from: Vertex?,
    orientation: BoardOrientation,
    editorMode: Boolean,
    tapEvents: TapEvents,
    logger: PlatformLogger,
    preMoveContext: PreMoveContext? = null,
) {
    detectTapGestures { offset ->
        val closestVertex =
            findClosestVertex(
                tapOffset = offset,
                size = Size(size.width.toFloat(), size.height.toFloat()),
                maxTapDistance = visualWidth,
                orientation = orientation,
            )

        closestVertex?.let { vertex ->
            when {
                editorMode -> tapEvents.onEditPieceRequested(vertex)

                preMoveContext != null -> handlePreMoveTap(
                    gameState = gameState,
                    context = preMoveContext,
                    to = vertex,
                    tapEvents = tapEvents,
                    logger = logger,
                )

                else -> handleTap(
                    gameState = gameState,
                    whiteIsAI = whiteIsAI,
                    blackIsAI = blackIsAI,
                    from = from,
                    to = vertex,
                    tapEvents = tapEvents,
                    logger = logger,
                )
            }
        }
    }
}

fun handleTap(
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    from: Vertex?,
    to: Vertex,
    tapEvents: TapEvents,
    logger: PlatformLogger,
) {
    logger.debug("TAP HANDLED: fromVertex=$from, toVertex=$to")

    // Seleccionar pieza si no hay origen
    if (from == null) {
        selectPiece(
            gameState = gameState,
            whiteIsAI = whiteIsAI,
            blackIsAI = blackIsAI,
            from = to,
            onSelected = tapEvents::onSelected,
            logger = logger,
        )
        return
    }

    // Toque sobre la pieza actualmente seleccionada
    if (to == from) {
        // Si es una promoción forzada válida, ejecutarla en lugar de deseleccionar
        val promotionMove = Move(from to from)
        if (gameState.allMovesForTurn().contains(promotionMove)) {
            logger.debug("Dispatching forced promotion at $from")
            tapEvents.onMove(promotionMove)
        } else {
            logger.debug("Deselecting piece")
            tapEvents.onCancel()
        }
        return
    }

    val fromColor =
        gameState.cobs[from]?.color ?: run {
            tapEvents.onCancel()
            return
        }

    logger.debug("Attempting move from $from to $to")

    val toColor = gameState.cobs[to]?.color

    when {
        // Seleccionar otra pieza del mismo color
        toColor == fromColor ->
            selectPiece(
                gameState = gameState,
                whiteIsAI = whiteIsAI,
                blackIsAI = blackIsAI,
                from = to,
                onSelected = tapEvents::onSelected,
                logger = logger,
            )

        // Deseleccionar si toca pieza adversaria
        toColor != null -> {
            logger.debug("Deselecting piece")
            tapEvents.onCancel()
        }

        // Intentar mover a casilla libre
        else ->
            movePiece(
                gameState = gameState,
                from = from,
                to = to,
                onMove = tapEvents::onMove,
                onInvalid = tapEvents::onInvalid,
                onCancel = tapEvents::onCancel,
                logger = logger,
            )
    }
}

/**
 * Flujo de tap durante el turno de la IA con pre-movimientos habilitados.
 * Los targets se validan contra el estado actual; si ya no son legales al
 * momento de ejecutar, el pre-move se descarta silenciosamente.
 */
fun handlePreMoveTap(
    gameState: GameState,
    context: PreMoveContext,
    to: Vertex,
    tapEvents: TapEvents,
    logger: PlatformLogger,
) {
    logger.debug("PRE-MOVE TAP: from=${context.preMoveFrom}, to=$to, humanColor=${context.humanColor}")

    val preMoveFrom = context.preMoveFrom

    // Fase 1: sin pre-selección previa
    if (preMoveFrom == null) {
        preSelectPiece(
            gameState = gameState,
            humanColor = context.humanColor,
            from = to,
            onPreSelected = tapEvents::onPreMoveSelected,
            onCancel = tapEvents::onPreMoveCancel,
            logger = logger,
        )
        return
    }

    // Tap sobre la pieza pre-seleccionada → cancelar
    if (to == preMoveFrom) {
        logger.debug("Pre-move cancelled (tap on pre-selected piece)")
        tapEvents.onPreMoveCancel()
        return
    }

    val fromColor = gameState.cobs[preMoveFrom]?.color
    if (fromColor != context.humanColor) {
        // La pieza pre-seleccionada ya no pertenece al humano (fue capturada).
        tapEvents.onPreMoveCancel()
        return
    }

    val toColor = gameState.cobs[to]?.color

    when {
        // Cambiar de pre-selección a otra pieza humana
        toColor == context.humanColor ->
            preSelectPiece(
                gameState = gameState,
                humanColor = context.humanColor,
                from = to,
                onPreSelected = tapEvents::onPreMoveSelected,
                onCancel = tapEvents::onPreMoveCancel,
                logger = logger,
            )

        // Cualquier otro destino (casilla libre o con pieza enemiga): candidato a pre-move.
        // Se permite apuntar a una casilla ocupada por el rival — podemos prever que se desocupe
        // cuando llegue nuestro turno. La forma del movimiento se valida ignorando la ocupación
        // (allowOccupiedTargets); la legalidad real se re-chequea al ejecutar y, si sigue ilegal,
        // el pre-move se descarta en silencio.
        else -> {
            val move = Move(preMoveFrom to to)
            val isValid = gameState.cobs[preMoveFrom]?.let { cob ->
                to in gameState.getValidVertex(preMoveFrom, cob, allowOccupiedTargets = true)
            } == true

            if (isValid) {
                logger.debug("Pre-move SET: $preMoveFrom → $to")
                tapEvents.onPreMoveSet(move)
            } else {
                logger.debug("Pre-move cancelled (target not valid)")
                tapEvents.onPreMoveCancel()
            }
        }
    }
}

fun movePiece(
    gameState: GameState,
    from: Vertex,
    to: Vertex,
    onMove: (move: Move) -> Unit,
    onInvalid: (from: Vertex, valid: List<Vertex>) -> Unit,
    onCancel: () -> Unit,
    logger: PlatformLogger,
) {
    // Deseleccionar si toca la misma pieza
    if (to == from) {
        logger.debug("Deselecting piece")
        onCancel()
        return
    }

    val isValid = isValidMove(gameState, Move(from to to))
    logger.debug("Move validation: $from -> $to = $isValid")

    if (isValid) {
        logger.debug("Calling onMove with: $from, $to")
        onMove(Move(from to to))
        return
    }

    logger.debug("Move is invalid")

    // Si el movimiento es inválido, seleccionar la nueva pieza si es del jugador actual
    gameState.cobs[to]?.let { cob ->
        if (cob.color == gameState.currentTurn) {
            onInvalid(to, gameState.getValidVertex(from, cob))
        } else {
            onCancel()
        }
    } ?: onCancel()
}

/**
 * Selecciona la pieza en [from] si su bando no está controlado por la IA.
 * @return `true` si la pieza quedó seleccionada (se invocó [onSelected]), `false` si no.
 */
fun selectPiece(
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    from: Vertex,
    onSelected: (from: Vertex, valid: List<Vertex>) -> Unit,
    logger: PlatformLogger,
): Boolean {
    val cob = gameState.cobs[from] ?: return false
    logger.debug("Checking piece: $cob at $from, currentTurn: ${gameState.currentTurn}")

    // Block selection of any piece whose band is controlled by the AI engine.
    val cobIsAIControlled = (cob.color == CobColor.WHITE && whiteIsAI) ||
            (cob.color == CobColor.BLACK && blackIsAI)
    if (cobIsAIControlled) {
        logger.debug("Cannot select: $cob is controlled by AI")
        return false
    }

    logger.debug("Piece selected: $from")

    val validMoves = gameState.getValidVertex(from, cob)
    onSelected(from, validMoves)

    logger.debug("Highlighted moves: $validMoves")
    return true
}

/**
 * Pre-selecciona una pieza humana durante el turno de la IA.
 * A diferencia de [selectPiece], no chequea `isAI` — se llama únicamente
 * cuando [PreMoveContext.humanColor] ya está determinado.
 */
private fun preSelectPiece(
    gameState: GameState,
    humanColor: CobColor,
    from: Vertex,
    onPreSelected: (from: Vertex, valid: List<Vertex>) -> Unit,
    onCancel: () -> Unit,
    logger: PlatformLogger,
) {
    val cob = gameState.cobs[from]
    if (cob == null || cob.color != humanColor) {
        logger.debug("Pre-select ignored: no human piece at $from")
        onCancel()
        return
    }

    // allowOccupiedTargets: los hints del pre-move incluyen casillas ocupadas por el rival
    // (destinos que podrían desocuparse en nuestro turno), no solo las vacías.
    val validTargets = gameState.getValidVertex(from, cob, allowOccupiedTargets = true)
    logger.debug("Pre-selected $from — targets: $validTargets")
    onPreSelected(from, validTargets)
}

// ── Arrastrar y soltar ───────────────────────────────────────────────────────
//
// Gesto paralelo a [tapGestures] (ambos conviven en el mismo pointerInput/Box):
// Compose arbitra por touch-slop → un toque corto dispara el tap, un arrastre
// dispara el drag. Reutiliza la misma lógica de selección/validación del tap.
// Los tres callbacks visuales ([onGrab]/[onDragTo]/[onRelease]) le permiten al
// renderer dibujar la pieza siguiendo el dedo sin rutear el offset por ViewModel.

/**
 * Detecta el gesto de arrastre y lo enruta a [handleDragStart] (agarre) y
 * [handleDragDrop] (soltar), computando el vértice más cercano igual que el tap.
 *
 * @param onGrab    Se invoca al agarrar una pieza válida, con su vértice y el offset inicial.
 * @param onDragTo  Se invoca en cada frame del arrastre con el offset actual del puntero.
 * @param onRelease Se invoca al soltar/cancelar. El parámetro `moved` indica si el drop **movió** la
 *   pieza (movimiento normal o reubicación de editor): `true` → el renderer debe **mantener** la pieza
 *   flotando hasta que el estado la reubique (evita el flash de 1 frame en el origen); `false` (cancel,
 *   pre-move, ilegal) → limpiar de inmediato.
 */
suspend fun PointerInputScope.dragGestures(
    visualWidth: Float,
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    orientation: BoardOrientation,
    editorMode: Boolean,
    tapEvents: TapEvents,
    logger: PlatformLogger,
    preMoveContext: PreMoveContext? = null,
    onGrab: (from: Vertex, pointer: Offset) -> Unit,
    onDragTo: (pointer: Offset) -> Unit,
    onRelease: (moved: Boolean) -> Unit,
    /** Se invoca al despachar un movimiento normal por soltado, con el offset de soltado. */
    onDropMove: (move: Move, pointer: Offset) -> Unit = { _, _ -> },
) {
    // Estado del gesto en curso (vive entre onDragStart/onDrag/onDragEnd).
    var from: Vertex? = null
    var pointer = Offset.Unspecified

    fun canvasSize() = Size(size.width.toFloat(), size.height.toFloat())

    detectDragGestures(
        onDragStart = { offset ->
            val at = findClosestVertex(
                tapOffset = offset,
                size = canvasSize(),
                maxTapDistance = visualWidth,
                orientation = orientation,
            )
            val grabbed = at?.let {
                handleDragStart(
                    gameState = gameState,
                    whiteIsAI = whiteIsAI,
                    blackIsAI = blackIsAI,
                    editorMode = editorMode,
                    preMoveContext = preMoveContext,
                    at = it,
                    tapEvents = tapEvents,
                    logger = logger,
                )
            }
            from = grabbed
            if (grabbed != null) {
                pointer = offset
                onGrab(grabbed, offset)
            }
        },
        onDrag = { change, _ ->
            if (from != null) {
                change.consume()
                pointer = change.position
                onDragTo(change.position)
            }
        },
        onDragEnd = {
            val origin = from
            var moved = false
            if (origin != null) {
                val dropOffset = pointer
                val target = if (dropOffset.isSpecified) {
                    findClosestVertex(
                        tapOffset = dropOffset,
                        size = canvasSize(),
                        maxTapDistance = visualWidth,
                        orientation = orientation,
                    )
                } else {
                    null
                }
                moved = handleDragDrop(
                    gameState = gameState,
                    from = origin,
                    to = target,
                    editorMode = editorMode,
                    preMoveContext = preMoveContext,
                    tapEvents = tapEvents,
                    logger = logger,
                    onMoveDispatched = { move -> onDropMove(move, dropOffset) },
                )
            }
            from = null
            pointer = Offset.Unspecified
            onRelease(moved)
        },
        onDragCancel = {
            if (from != null) {
                when {
                    editorMode -> Unit
                    preMoveContext != null -> tapEvents.onPreMoveCancel()
                    else -> tapEvents.onCancel()
                }
            }
            from = null
            pointer = Offset.Unspecified
            onRelease(false)
        },
    )
}

/**
 * Decide si el vértice [at] tiene una pieza agarrable según el modo y, si aplica,
 * dispara la selección/pre-selección (los mismos efectos que produce el tap).
 * @return el vértice agarrado, o `null` si nada es agarrable ahí.
 */
fun handleDragStart(
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    editorMode: Boolean,
    preMoveContext: PreMoveContext?,
    at: Vertex,
    tapEvents: TapEvents,
    logger: PlatformLogger,
): Vertex? {
    // Editor: cualquier vértice con pieza puede agarrarse para reubicarla.
    if (editorMode) {
        return at.takeIf { gameState.cobs[it] != null }
    }

    // Pre-move: solo piezas del humano; muestra los targets proyectados.
    if (preMoveContext != null) {
        val cob = gameState.cobs[at] ?: return null
        if (cob.color != preMoveContext.humanColor) return null
        val targets = gameState.getValidVertex(at, cob, allowOccupiedTargets = true)
        logger.debug("DRAG pre-grab $at — targets: $targets")
        tapEvents.onPreMoveSelected(at, targets)
        return at
    }

    // Normal: reutiliza selectPiece (respeta el bloqueo por IA).
    val selected = selectPiece(
        gameState = gameState,
        whiteIsAI = whiteIsAI,
        blackIsAI = blackIsAI,
        from = at,
        onSelected = tapEvents::onSelected,
        logger = logger,
    )
    return at.takeIf { selected }
}

/**
 * Enruta el soltar del arrastre. [to] es `null` si se soltó fuera de todo vértice.
 * Reutiliza [movePiece] en el flujo normal y la validación de pre-move; en el editor
 * pide reubicar la pieza a un vértice libre.
 *
 * @return `true` si el drop **movió** la pieza fuera del origen (movimiento normal despachado o
 *   reubicación de editor). `false` en cancel, pre-move o ilegal (la pieza no deja el origen). El
 *   renderer lo usa para decidir si mantener la pieza flotando hasta que el estado la reubique.
 */
fun handleDragDrop(
    gameState: GameState,
    from: Vertex,
    to: Vertex?,
    editorMode: Boolean,
    preMoveContext: PreMoveContext?,
    tapEvents: TapEvents,
    logger: PlatformLogger,
    /**
     * Se invoca con el movimiento **justo antes** de despacharlo, solo en el flujo normal
     * y solo si el movimiento resultó válido. Lo usa el drag para registrar el offset de
     * soltado y animar desde ahí. No se dispara en pre-move ni editor.
     */
    onMoveDispatched: (Move) -> Unit = {},
): Boolean {
    // Soltar fuera del tablero o sobre el propio origen → cancelar.
    if (to == null || to == from) {
        when {
            editorMode -> Unit // la pieza vuelve a su lugar; nada que hacer
            preMoveContext != null -> tapEvents.onPreMoveCancel()
            else -> tapEvents.onCancel()
        }
        return false
    }

    // Editor: reubicar solo a un vértice libre.
    if (editorMode) {
        if (gameState.cobs[to] == null) {
            logger.debug("DRAG editor relocate: $from → $to")
            tapEvents.onEditMovePieceRequested(from, to)
            return true
        }
        return false
    }

    // Pre-move: valida la forma del movimiento ignorando la ocupación (se re-chequea al ejecutar).
    // No mueve la pieza (queda como flecha pendiente) → retorna false.
    if (preMoveContext != null) {
        val cob = gameState.cobs[from]
        val isValid = cob != null &&
                to in gameState.getValidVertex(from, cob, allowOccupiedTargets = true)
        if (isValid) {
            logger.debug("DRAG pre-move SET: $from → $to")
            tapEvents.onPreMoveSet(Move(from to to))
        } else {
            logger.debug("DRAG pre-move cancelled (target not valid)")
            tapEvents.onPreMoveCancel()
        }
        return false
    }

    // Normal: mismo camino de validación/despacho que el tap. El wrapper de onMove
    // señala el offset de soltado (onMoveDispatched) solo cuando movePiece confirmó
    // que el movimiento es válido y lo despacha.
    var moved = false
    movePiece(
        gameState = gameState,
        from = from,
        to = to,
        onMove = { move ->
            moved = true
            onMoveDispatched(move)
            tapEvents.onMove(move)
        },
        onInvalid = tapEvents::onInvalid,
        onCancel = tapEvents::onCancel,
        logger = logger,
    )
    return moved
}