package com.agustin.tarati.ui.components.game.behaviors

import com.agustin.tarati.core.domain.game.board.GameBoard.A1
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C2
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.D1
import com.agustin.tarati.core.domain.game.board.GameBoard.D2
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.PlatformLogger
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests de la máquina de estados de [handlePreMoveTap].
 *
 * Estado inicial del board (standard opening): WHITE en C1, C2, D1, D2;
 * BLACK en C7, C8, D3, D4. `currentTurn` se setea a BLACK en todos los tests
 * (simula turno AI), humanColor = WHITE.
 *
 * Se valida la máquina de estados:
 *
 *   preMoveFrom == null
 *     → tap en pieza humana          → onPreMoveSelected
 *     → tap en pieza AI              → onPreMoveCancel
 *     → tap en casilla vacía         → onPreMoveCancel
 *
 *   preMoveFrom != null
 *     → tap en preMoveFrom (mismo)        → onPreMoveCancel
 *     → tap en otra pieza humana          → onPreMoveSelected (cambia selección)
 *     → tap en pieza AI no-adyacente      → onPreMoveCancel
 *     → tap en pieza AI en destino legal  → onPreMoveSet (casilla ocupada permitida)
 *     → tap en target válido              → onPreMoveSet
 *     → tap en target no-válido           → onPreMoveCancel
 *     → tap en pieza "fantasma"           → onPreMoveCancel (la pieza fue capturada)
 */
class HandlePreMoveTapTest {

    private lateinit var tapEvents: TapEvents
    private lateinit var logger: PlatformLogger

    /** Tablero en posición inicial, turno BLACK (IA). Humano = WHITE. */
    private val state: GameState = initialGameState(currentTurn = BLACK)

    private val context = PreMoveContext(preMoveFrom = null, humanColor = WHITE)

    @Before
    fun setup() {
        tapEvents = mockk(relaxed = true)
        logger = mockk(relaxed = true)
    }

    // ── Fase 1: sin pre-selección previa ─────────────────────────────────────

    @Test
    fun `no preselection - tap on human piece triggers onPreMoveSelected with valid targets`() {
        val humanPiece = C1  // WHITE cob en posición inicial estándar
        assertEquals(
            "Precondition: C1 must hold a WHITE cob in the initial position",
            WHITE,
            state.cobs[humanPiece]?.color,
        )

        handlePreMoveTap(
            gameState = state,
            context = context,
            to = humanPiece,
            tapEvents = tapEvents,
            logger = logger,
        )

        val expectedTargets =
            state.getValidVertex(humanPiece, state.cobs[humanPiece] ?: return, allowOccupiedTargets = true)
        verify(exactly = 1) { tapEvents.onPreMoveSelected(humanPiece, expectedTargets) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 0) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `no preselection - tap on AI piece triggers onPreMoveCancel`() {
        val aiPiece = C7  // BLACK cob
        assertEquals(BLACK, state.cobs[aiPiece]?.color)

        handlePreMoveTap(
            gameState = state,
            context = context,
            to = aiPiece,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `no preselection - tap on empty vertex triggers onPreMoveCancel`() {
        val emptyVertex = A1  // Central, vacío en opening
        assertEquals(null, state.cobs[emptyVertex])

        handlePreMoveTap(
            gameState = state,
            context = context,
            to = emptyVertex,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    // ── Fase 2: pre-selección activa ─────────────────────────────────────────

    @Test
    fun `preselected - tap on same piece triggers onPreMoveCancel`() {
        val preSelected = C1
        val ctx = context.copy(preMoveFrom = preSelected)

        handlePreMoveTap(
            gameState = state,
            context = ctx,
            to = preSelected,  // mismo vertex
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected - tap on another human piece switches pre-selection`() {
        val firstSelected = C1
        val secondSelected = C2  // otra pieza WHITE adyacente
        val ctx = context.copy(preMoveFrom = firstSelected)
        assertEquals(WHITE, state.cobs[secondSelected]?.color)

        handlePreMoveTap(
            gameState = state,
            context = ctx,
            to = secondSelected,
            tapEvents = tapEvents,
            logger = logger,
        )

        val expectedTargets =
            state.getValidVertex(secondSelected, state.cobs[secondSelected] ?: return, allowOccupiedTargets = true)
        verify(exactly = 1) { tapEvents.onPreMoveSelected(secondSelected, expectedTargets) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 0) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected - tap on non-adjacent AI piece triggers onPreMoveCancel`() {
        val preSelected = C1
        val aiPiece = C7  // BLACK, no adyacente a C1 → no es destino de forma legal
        val ctx = context.copy(preMoveFrom = preSelected)

        handlePreMoveTap(
            gameState = state,
            context = ctx,
            to = aiPiece,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected - tap on enemy piece on a legal target sets the pre-move`() {
        // Se permite fijar un pre-move a una casilla OCUPADA por el rival, previendo que se desocupe
        // en nuestro turno. Se coloca una pieza enemiga en un destino de forma legal (vacío) de C1.
        val preSelected = C1
        val cob = state.cobs[preSelected] ?: return
        val target = state.getValidVertex(preSelected, cob)
            .firstOrNull { it != preSelected && state.cobs[it] == null }
        assertNotNull_(target, "WHITE at C1 must have at least one empty valid target")
        val occupied = state.copy(cobs = state.cobs + ((target ?: return) to Cob(BLACK)))
        val ctx = context.copy(preMoveFrom = preSelected)

        handlePreMoveTap(
            gameState = occupied,
            context = ctx,
            to = target,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 1) { tapEvents.onPreMoveSet(Move(preSelected to target)) }
        verify(exactly = 0) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected - tap on valid target triggers onPreMoveSet with correct move`() {
        val preSelected = C1
        val cob = state.cobs[preSelected] ?: return
        val ctx = context.copy(preMoveFrom = preSelected)

        // Elegir dinámicamente un target válido vacío para que el test no
        // dependa del detalle de la geometría - solo se requiere que exista
        // al menos un destino legal desde C1 en la posición inicial.
        val validTargets = state.getValidVertex(preSelected, cob)
        val emptyValidTarget = validTargets.firstOrNull { it != preSelected && state.cobs[it] == null }
        assertNotNull_(emptyValidTarget, "WHITE at C1 must have at least one empty valid target")

        handlePreMoveTap(
            gameState = state,
            context = ctx,
            to = emptyValidTarget ?: return,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 1) { tapEvents.onPreMoveSet(Move(preSelected to emptyValidTarget)) }
        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected - tap on non-adjacent empty vertex triggers onPreMoveCancel`() {
        val preSelected = C1
        val farEmpty = B1  // adyacente pero no-destino válido para WHITE en C1
        val cob = state.cobs[preSelected] ?: return
        val ctx = context.copy(preMoveFrom = preSelected)

        // Precondición: B1 está vacío y NO es target válido desde C1 para
        // una pieza WHITE non-upgraded (B1 queda "atrás" respecto al sentido
        // forward de WHITE, y C1 no está en home-base capture context).
        // Si el detalle de la geometría hiciera B1 válido, este test sería
        // falso-positivo. El skip explicado aquí lo documenta.
        val validTargets = state.getValidVertex(preSelected, cob)
        if (B1 in validTargets) {
            // B1 resultó válido: probar con otra casilla que garantizadamente no lo es.
            // A1 a menudo es inalcanzable en un solo move desde C1 cuando la pieza
            // no está en home-base con capture.
            val nonValid = (setOf(A1, B1) - validTargets.toSet()).firstOrNull()
            assertNotNull_(nonValid, "At least one adjacent empty vertex must be non-valid")
            handlePreMoveTap(
                gameState = state,
                context = ctx,
                to = nonValid ?: return,
                tapEvents = tapEvents,
                logger = logger,
            )
        } else {
            handlePreMoveTap(
                gameState = state,
                context = ctx,
                to = farEmpty,
                tapEvents = tapEvents,
                logger = logger,
            )
        }

        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected piece no longer exists - cancels before considering target`() {
        // Simula un estado donde el gameState real ya no tiene la pieza
        // pre-seleccionada (p. ej. la IA la capturó mid-flight y el ViewModel
        // aún no reaccionó). handlePreMoveTap debe cancelar sin intentar move.
        val stateMissingPiece = GameState(
            cobs = mapOf(
                C2 to Cob(WHITE),
                C3 to Cob(WHITE),
                D1 to Cob(WHITE),
                D2 to Cob(WHITE),
                C7 to Cob(BLACK),
                C8 to Cob(BLACK),
                D3 to Cob(BLACK),
                D4 to Cob(BLACK),
            ),
            currentTurn = BLACK,
        )
        val ghostPreSelected = C1  // vacío en este state
        assertEquals(null, stateMissingPiece.cobs[ghostPreSelected])
        val ctx = context.copy(preMoveFrom = ghostPreSelected)

        handlePreMoveTap(
            gameState = stateMissingPiece,
            context = ctx,
            to = B1,  // cualquier destino
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 0) { tapEvents.onPreMoveSet(any()) }
        verify(exactly = 0) { tapEvents.onPreMoveSelected(any(), any()) }
        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    @Test
    fun `preselected with stale data - piece now belongs to opponent triggers cancel`() {
        // Edge case: preMoveFrom apunta a un vertex donde ahora está una pieza
        // de la IA (no está previsto que ocurra, pero el guard lo cubre).
        val stateCaptured = GameState(
            cobs = mapOf(
                C1 to Cob(BLACK),  // antes WHITE, ahora BLACK (hipotético)
                C2 to Cob(WHITE),
                C3 to Cob(WHITE),
                D1 to Cob(WHITE),
                D2 to Cob(WHITE),
                C7 to Cob(BLACK),
                C8 to Cob(BLACK),
                D3 to Cob(BLACK),
            ),
            currentTurn = BLACK,
        )
        val ctx = context.copy(preMoveFrom = C1)

        handlePreMoveTap(
            gameState = stateCaptured,
            context = ctx,
            to = B1,
            tapEvents = tapEvents,
            logger = logger,
        )

        verify(exactly = 1) { tapEvents.onPreMoveCancel() }
    }

    // ── Secuencias ───────────────────────────────────────────────────────────

    @Test
    fun `sequence - select then confirm emits onPreMoveSelected then onPreMoveSet`() {
        val preSelected = C1
        val cob = state.cobs[preSelected] ?: return
        val validTargets = state.getValidVertex(preSelected, cob, allowOccupiedTargets = true)
        val target = validTargets.firstOrNull { it != preSelected && state.cobs[it] == null }
        assertNotNull_(target, "Need a valid empty target from C1")

        // Tap 1: sin pre-selección, toca C1 → onPreMoveSelected
        handlePreMoveTap(
            gameState = state,
            context = context,  // preMoveFrom = null
            to = preSelected,
            tapEvents = tapEvents,
            logger = logger,
        )

        // Tap 2: ya hay pre-selección, toca target → onPreMoveSet
        handlePreMoveTap(
            gameState = state,
            context = context.copy(preMoveFrom = preSelected),
            to = target ?: return,
            tapEvents = tapEvents,
            logger = logger,
        )

        verifyOrder {
            tapEvents.onPreMoveSelected(preSelected, validTargets)
            tapEvents.onPreMoveSet(Move(preSelected to target))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Helper local para hacer assertNotNull con mensaje custom en JUnit 4.
     * (JUnit 4 tiene `assertNotNull(message, actual)` pero el orden invierte y
     * aquí preferimos explicitar el mensaje para legibilidad.)
     */
    private fun <T> assertNotNull_(value: T?, message: String) {
        if (value == null) throw AssertionError(message)
    }
}