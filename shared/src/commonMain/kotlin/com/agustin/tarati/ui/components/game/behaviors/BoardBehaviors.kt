package com.agustin.tarati.ui.components.game.behaviors

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Size
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

fun selectPiece(
    gameState: GameState,
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    from: Vertex,
    onSelected: (from: Vertex, valid: List<Vertex>) -> Unit,
    logger: PlatformLogger,
) {
    val cob = gameState.cobs[from] ?: return
    logger.debug("Checking piece: $cob at $from, currentTurn: ${gameState.currentTurn}")

    // Block selection of any piece whose band is controlled by the AI engine.
    val cobIsAIControlled = (cob.color == CobColor.WHITE && whiteIsAI) ||
            (cob.color == CobColor.BLACK && blackIsAI)
    if (cobIsAIControlled) {
        logger.debug("Cannot select: $cob is controlled by AI")
        return
    }

    logger.debug("Piece selected: $from")

    val validMoves = gameState.getValidVertex(from, cob)
    onSelected(from, validMoves)

    logger.debug("Highlighted moves: $validMoves")
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