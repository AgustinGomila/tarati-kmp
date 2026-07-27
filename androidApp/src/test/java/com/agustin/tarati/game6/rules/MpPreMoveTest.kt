package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.rules.MpPreMove
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la FSM pura de pre-movimiento ([MpPreMove]), compartida por el juego multijugador local y
 * online. Fases idénticas a `handlePreMoveTap` de Tarati: pre-selección → confirmación/cancelación, y
 * revalidación ([MpPreMove.isReady]) al volver el turno del humano.
 */
class MpPreMoveTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    /**
     * Estado con P1 (humano) fuera de base en C1 y C2, P2 (rival) lejos en C7; el turno es de P2 salvo
     * que [seatIndex] lo cambie. Sirve para probar el pre-movimiento del humano durante el turno ajeno.
     */
    private fun state(seatIndex: Int = 1): MpGameState =
        MpSetup.initialState(2).copy(
            pieces = mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C2") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
            ),
            currentSeatIndex = seatIndex,
        )

    // ── Pre-selección ───────────────────────────────────────────────────────────

    @Test
    fun tapOwnPiece_withoutPreSelection_preSelectsWithTargets() {
        val result = MpPreMove.onTap(state(), humanColor = P1, preMoveFrom = null, to = v("C1"))
        assertTrue(result is MpPreMove.TapResult.PreSelect)
        result as MpPreMove.TapResult.PreSelect
        assertEquals(v("C1"), result.from)
        // Los destinos son los del pre-movimiento desde C1 (forma legal, admite casillas ocupadas por rivales).
        val expected = MpRules.preMoveTargetsFor(state(), state().seats.first { it.color == P1 }, v("C1"))
        assertEquals(expected, result.targets)
        assertTrue("C1 tiene destinos legales", result.targets.isNotEmpty())
    }

    @Test
    fun tapNonOwnPiece_withoutPreSelection_ignores() {
        // Sin pre-selección, tocar una casilla vacía o pieza ajena no altera nada (Ignore).
        assertEquals(MpPreMove.TapResult.Ignore, MpPreMove.onTap(state(), P1, null, v("E7")))
        assertEquals(MpPreMove.TapResult.Ignore, MpPreMove.onTap(state(), P1, null, v("C7")))
    }

    // ── Con pre-selección activa ─────────────────────────────────────────────────

    @Test
    fun tapSamePreSelectedPiece_clears() {
        assertEquals(MpPreMove.TapResult.Clear, MpPreMove.onTap(state(), P1, preMoveFrom = v("C1"), to = v("C1")))
    }

    @Test
    fun tapOtherOwnPiece_reSelects() {
        val result = MpPreMove.onTap(state(), P1, preMoveFrom = v("C1"), to = v("C2"))
        assertTrue(result is MpPreMove.TapResult.PreSelect)
        assertEquals(v("C2"), (result as MpPreMove.TapResult.PreSelect).from)
    }

    @Test
    fun tapNonAdjacentEnemyPiece_clears() {
        // C7 (rival) no es adyacente a C1 → no es un destino de forma legal → cancelar.
        assertEquals(MpPreMove.TapResult.Clear, MpPreMove.onTap(state(), P1, preMoveFrom = v("C1"), to = v("C7")))
    }

    @Test
    fun tapAdjacentEnemyPiece_setsPending() {
        // Un rival ocupa C12, que es un destino de forma legal desde C1. Se permite fijar el pre-move
        // a esa casilla ocupada, previendo que se desocupe cuando llegue nuestro turno; la legalidad
        // real se revalida al ejecutar.
        val occupied = state().copy(
            pieces = mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C12") to Piece(P2, hasLeftBase = true),
            ),
        )
        val result = MpPreMove.onTap(occupied, P1, preMoveFrom = v("C1"), to = v("C12"))
        assertTrue(result is MpPreMove.TapResult.SetPending)
        assertEquals(MpMove(v("C1"), v("C12")), (result as MpPreMove.TapResult.SetPending).move)
    }

    @Test
    fun tapLegalEmptyTarget_setsPending() {
        // C1–C12 es arista del dodecágono y C12 está vacío → destino legal.
        val result = MpPreMove.onTap(state(), P1, preMoveFrom = v("C1"), to = v("C12"))
        assertTrue(result is MpPreMove.TapResult.SetPending)
        assertEquals(MpMove(v("C1"), v("C12")), (result as MpPreMove.TapResult.SetPending).move)
    }

    @Test
    fun tapIllegalEmptyTarget_clears() {
        // E7 está vacío pero no es adyacente a C1 → no es un destino legal.
        assertEquals(MpPreMove.TapResult.Clear, MpPreMove.onTap(state(), P1, preMoveFrom = v("C1"), to = v("E7")))
    }

    @Test
    fun preSelectedPieceGone_clears() {
        // La pieza pre-seleccionada ya no es del humano (capturada / vacía) → cancelar.
        assertEquals(MpPreMove.TapResult.Clear, MpPreMove.onTap(state(), P1, preMoveFrom = v("C12"), to = v("D1")))
    }

    // ── Revalidación / ejecución ─────────────────────────────────────────────────

    @Test
    fun isReady_trueOnHumanTurnWhenLegal() {
        assertTrue(MpPreMove.isReady(state(seatIndex = 0), P1, MpMove(v("C1"), v("C12"))))
    }

    @Test
    fun isReady_falseWhenNotHumanTurn() {
        assertFalse(MpPreMove.isReady(state(), P1, MpMove(v("C1"), v("C12"))))
    }

    @Test
    fun isReady_falseWhenMoveIllegal() {
        assertFalse(MpPreMove.isReady(state(seatIndex = 0), P1, MpMove(v("C1"), v("E7"))))
    }
}
