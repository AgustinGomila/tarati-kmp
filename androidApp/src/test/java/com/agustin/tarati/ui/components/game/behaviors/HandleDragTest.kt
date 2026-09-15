package com.agustin.tarati.ui.components.game.behaviors

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.PlatformLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de los handlers de arrastrar y soltar ([handleDragStart] / [handleDragDrop]).
 *
 * Posición inicial estándar: WHITE en C1, C2, D1, D2; BLACK en C7, C8, D3, D4.
 * Se cubren los tres modos: normal (turno humano), pre-move (turno de la IA) y editor.
 * La lógica de validación/despacho se delega en `selectPiece`/`movePiece`/`getValidVertex`
 * (mismos que el tap); estos tests fijan el contrato de ruteo por modo del drag.
 */
class HandleDragTest {

    private lateinit var tapEvents: TapEvents
    private lateinit var logger: PlatformLogger

    /** Turno WHITE (humano local) — para el flujo normal. */
    private val normalState: GameState = initialGameState(currentTurn = WHITE)

    /** Turno BLACK (IA) — para el flujo de pre-move; humano = WHITE. */
    private val aiState: GameState = initialGameState(currentTurn = BLACK)
    private val preMoveCtx = PreMoveContext(preMoveFrom = null, humanColor = WHITE)

    @Before
    fun setup() {
        tapEvents = mockk(relaxed = true)
        logger = mockk(relaxed = true)
    }

    // ── handleDragStart ──────────────────────────────────────────────────────

    @Test
    fun `normal grab of own piece selects it and returns the vertex`() {
        val cob = normalState.cobs[C1] ?: return
        val grabbed = handleDragStart(
            gameState = normalState,
            whiteIsAI = false,
            blackIsAI = false,
            editorMode = false,
            preMoveContext = null,
            at = C1,
            tapEvents = tapEvents,
            logger = logger,
        )
        assertEquals(C1, grabbed)
        verify(exactly = 1) { tapEvents.onSelected(C1, normalState.getValidVertex(C1, cob)) }
    }

    @Test
    fun `normal grab of AI-controlled piece is rejected`() {
        val grabbed = handleDragStart(
            gameState = normalState,
            whiteIsAI = true, // WHITE controlado por IA → no agarrable
            blackIsAI = false,
            editorMode = false,
            preMoveContext = null,
            at = C1,
            tapEvents = tapEvents,
            logger = logger,
        )
        assertNull(grabbed)
        verify(exactly = 0) { tapEvents.onSelected(any(), any()) }
    }

    @Test
    fun `normal grab of empty vertex returns null`() {
        val grabbed = handleDragStart(
            gameState = normalState,
            whiteIsAI = false,
            blackIsAI = false,
            editorMode = false,
            preMoveContext = null,
            at = A1, // vacío en la apertura
            tapEvents = tapEvents,
            logger = logger,
        )
        assertNull(grabbed)
        verify(exactly = 0) { tapEvents.onSelected(any(), any()) }
    }

    @Test
    fun `premove grab of human piece pre-selects with projected targets`() {
        val cob = aiState.cobs[C1] ?: return
        val expected = aiState.getValidVertex(C1, cob, allowOccupiedTargets = true)
        val grabbed = handleDragStart(
            gameState = aiState,
            whiteIsAI = false,
            blackIsAI = true,
            editorMode = false,
            preMoveContext = preMoveCtx,
            at = C1,
            tapEvents = tapEvents,
            logger = logger,
        )
        assertEquals(C1, grabbed)
        verify(exactly = 1) { tapEvents.onPreMoveSelected(C1, expected) }
    }

    @Test
    fun `premove grab of AI piece is rejected`() {
        val grabbed = handleDragStart(
            gameState = aiState,
            whiteIsAI = false,
            blackIsAI = true,
            editorMode = false,
            preMoveContext = preMoveCtx,
            at = C7, // pieza BLACK (IA)
            tapEvents = tapEvents,
            logger = logger,
        )
        assertNull(grabbed)
        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
    }

    @Test
    fun `editor grab returns vertex with a piece and rejects empty`() {
        val onPiece = handleDragStart(
            gameState = normalState,
            whiteIsAI = false,
            blackIsAI = false,
            editorMode = true,
            preMoveContext = null,
            at = C1,
            tapEvents = tapEvents,
            logger = logger,
        )
        val onEmpty = handleDragStart(
            gameState = normalState,
            whiteIsAI = false,
            blackIsAI = false,
            editorMode = true,
            preMoveContext = null,
            at = A1,
            tapEvents = tapEvents,
            logger = logger,
        )
        assertEquals(C1, onPiece)
        assertNull(onEmpty)
        verify(exactly = 0) { tapEvents.onSelected(any(), any()) }
    }

    // ── handleDragDrop ───────────────────────────────────────────────────────

    @Test
    fun `normal drop on legal target dispatches onMove`() {
        val cob = normalState.cobs[C1] ?: return
        val target = requireNotNull(
            normalState.getValidVertex(C1, cob).firstOrNull { it != C1 && normalState.cobs[it] == null },
        ) { "WHITE at C1 must have an empty legal target" }

        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = target,
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onMove(Move(C1 to target)) }
    }

    @Test
    fun `normal drop outside any vertex cancels`() {
        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = null,
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onCancel() }
        verify(exactly = 0) { tapEvents.onMove(any()) }
    }

    @Test
    fun `normal drop on the origin cancels`() {
        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = C1,
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onCancel() }
        verify(exactly = 0) { tapEvents.onMove(any()) }
    }

    @Test
    fun `normal drop on an illegal empty vertex cancels without moving`() {
        val cob = normalState.cobs[C1] ?: return
        val validTargets = normalState.getValidVertex(C1, cob)
        val illegal = normalState.cobs.keys.let { occupied ->
            // Un vértice vacío que NO es destino legal desde C1.
            com.agustin.tarati.core.domain.game.board.GameBoard.vertices
                .firstOrNull { it != C1 && it !in occupied && it !in validTargets }
        }
        assertNotNull_(illegal, "Must exist an empty non-target vertex")

        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = illegal,
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 0) { tapEvents.onMove(any()) }
        verify(exactly = 1) { tapEvents.onCancel() }
    }

    @Test
    fun `premove drop on a valid target sets the pre-move`() {
        val cob = aiState.cobs[C1] ?: return
        val target = requireNotNull(
            aiState.getValidVertex(C1, cob, allowOccupiedTargets = true)
                .firstOrNull { it != C1 && aiState.cobs[it] == null },
        ) { "WHITE at C1 must have a projected target" }

        handleDragDrop(
            gameState = aiState,
            from = C1,
            to = target,
            editorMode = false,
            preMoveContext = preMoveCtx,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onPreMoveSet(Move(C1 to target)) }
        verify(exactly = 0) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `premove drop on an invalid target cancels`() {
        handleDragDrop(
            gameState = aiState,
            from = C1,
            to = C7, // no es destino de forma legal desde C1
            editorMode = false,
            preMoveContext = preMoveCtx,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
    }

    @Test
    fun `normal legal drop signals onMoveDispatched with the move`() {
        val cob = normalState.cobs[C1] ?: return
        val target = requireNotNull(
            normalState.getValidVertex(C1, cob).firstOrNull { it != C1 && normalState.cobs[it] == null },
        ) { "WHITE at C1 must have an empty legal target" }
        var dispatched: Move? = null

        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = target,
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
            onMoveDispatched = { dispatched = it },
        )
        assertEquals(Move(C1 to target), dispatched)
    }

    @Test
    fun `invalid normal drop does not signal onMoveDispatched`() {
        var dispatched: Move? = null
        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = null, // fuera del tablero
            editorMode = false,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
            onMoveDispatched = { dispatched = it },
        )
        assertNull(dispatched)
    }

    @Test
    fun `premove drop does not signal onMoveDispatched`() {
        val cob = aiState.cobs[C1] ?: return
        val target = requireNotNull(
            aiState.getValidVertex(C1, cob, allowOccupiedTargets = true)
                .firstOrNull { it != C1 && aiState.cobs[it] == null },
        ) { "WHITE at C1 must have a projected target" }
        var dispatched: Move? = null

        handleDragDrop(
            gameState = aiState,
            from = C1,
            to = target,
            editorMode = false,
            preMoveContext = preMoveCtx,
            tapEvents = tapEvents,
            logger = logger,
            onMoveDispatched = { dispatched = it },
        )
        assertNull(dispatched)
    }

    @Test
    fun `handleDragDrop reports whether the piece left the origin`() {
        val cob = normalState.cobs[C1] ?: return
        val target = requireNotNull(
            normalState.getValidVertex(C1, cob).firstOrNull { it != C1 && normalState.cobs[it] == null },
        ) { "WHITE at C1 must have an empty legal target" }

        // Normal: legal → true; afuera/origen/ilegal → false.
        assertTrue(handleDragDrop(normalState, C1, target, false, null, tapEvents, logger))
        assertFalse(handleDragDrop(normalState, C1, null, false, null, tapEvents, logger))
        assertFalse(handleDragDrop(normalState, C1, C1, false, null, tapEvents, logger))
        assertFalse(handleDragDrop(normalState, C1, C7, false, null, tapEvents, logger))

        // Editor: reubicar a libre → true; a ocupado → false.
        assertTrue(handleDragDrop(normalState, C1, A1, true, null, tapEvents, logger))
        assertFalse(handleDragDrop(normalState, C1, C7, true, null, tapEvents, logger))

        // Pre-move: nunca "mueve" (queda como flecha pendiente) → false.
        val aiCob = aiState.cobs[C1] ?: return
        val pmTarget = requireNotNull(
            aiState.getValidVertex(C1, aiCob, allowOccupiedTargets = true)
                .firstOrNull { it != C1 && aiState.cobs[it] == null },
        ) { "WHITE at C1 must have a projected target" }
        assertFalse(handleDragDrop(aiState, C1, pmTarget, false, preMoveCtx, tapEvents, logger))
    }

    // ── Editor ─────────────────────────────────────────────────────────────

    @Test
    fun `editor drop on an empty vertex requests relocation`() {
        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = A1, // vacío
            editorMode = true,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 1) { tapEvents.onEditMovePieceRequested(C1, A1) }
    }

    @Test
    fun `editor drop on an occupied vertex is a no-op`() {
        handleDragDrop(
            gameState = normalState,
            from = C1,
            to = C7, // ocupado
            editorMode = true,
            preMoveContext = null,
            tapEvents = tapEvents,
            logger = logger,
        )
        verify(exactly = 0) { tapEvents.onEditMovePieceRequested(any(), any()) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun <T> assertNotNull_(value: T?, message: String) {
        if (value == null) throw AssertionError(message)
    }
}
