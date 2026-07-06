package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P3
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del motor de reglas multijugador (MpRules): salida de base, movimiento omnidireccional,
 * captura por conversión con pre-adyacencia, retiro y fin de partida (§2.2–2.4 del plan).
 */
class MpRulesTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    /** Estado base de 2 jugadores (P1@17, P2@20) con piezas [pieces] y turno de [P1]. */
    private fun twoPlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(2).copy(pieces = pieces, currentSeatIndex = 0)

    private fun threePlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(3).copy(pieces = pieces, currentSeatIndex = 0)

    // ---- Salida de base (§2.2) ----

    @Test
    fun initialMoves_onlyExitViaRadialDtoC() {
        val moves = MpRules.legalMoves(MpSetup.initialState(2))
        // P1 base 17: solo D1→C1 y D2→C2. Nada por el anillo D, ni las puntas E (bloqueadas).
        assertEquals(2, moves.size)
        assertTrue(moves.contains(MpMove(v("D1"), v("C1"))))
        assertTrue(moves.contains(MpMove(v("D2"), v("C2"))))
    }

    @Test
    fun pieceStillInBase_cannotUseDRing() {
        val moves = MpRules.legalMoves(MpSetup.initialState(2))
        assertFalse("D1→D18 (anillo) prohibido al inicio", moves.contains(MpMove(v("D1"), v("D18"))))
        assertFalse("D2→D3 (anillo) prohibido al inicio", moves.contains(MpMove(v("D2"), v("D3"))))
    }

    @Test
    fun exitingBase_setsHasLeftBase() {
        val after = MpRules.applyMove(MpSetup.initialState(2), MpMove(v("D1"), v("C1")))
        assertNull("La pieza salió de D1", after.pieces[v("D1")])
        assertTrue("La pieza en C1 marcó hasLeftBase", after.pieces.getValue(v("C1")).hasLeftBase)
        assertEquals("Turno pasa al siguiente jugador", 1, after.currentSeatIndex)
    }

    @Test
    fun afterLeavingBase_moveIsOmnidirectional() {
        // Pieza P1 ya fuera de base en C1: puede ir por el anillo D (C1→D18) y "hacia atrás" (C1→D1).
        val state = twoPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("D10") to Piece(P2, hasLeftBase = true),
            ),
        )
        val moves = MpRules.legalMoves(state)
        assertTrue("C1→D18 permitido (anillo)", moves.contains(MpMove(v("C1"), v("D18"))))
        assertTrue("C1→D1 permitido (retroceso)", moves.contains(MpMove(v("C1"), v("D1"))))
    }

    // ---- Captura por conversión + pre-adyacencia (§2.3) ----

    @Test
    fun capture_flipsNewNeighborButNotPreAdjacent() {
        // P1 en B3 mueve a A1. B1 (no adyacente a B3) se convierte; B2 (ya adyacente a B3) se protege.
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
                v("B2") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertEquals("Pieza movida", P1, after.pieces.getValue(v("A1")).owner)
        assertEquals("B1 capturado por conversión", P1, after.pieces.getValue(v("B1")).owner)
        assertEquals("B2 protegido por pre-adyacencia", P2, after.pieces.getValue(v("B2")).owner)
    }

    @Test
    fun capture_convertedPieceIsMarkedOutOfBase() {
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
                v("B2") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertTrue(after.pieces.getValue(v("B1")).hasLeftBase)
    }

    @Test
    fun preAdjacentEnemy_isNeverCaptured() {
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B2") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertEquals("Sin captura: enemigo pre-adyacente", P2, after.pieces.getValue(v("B2")).owner)
        assertEquals(1, after.pieceCount(P2))
    }

    // ---- Retiro y fin (§2.4) ----

    @Test
    fun capturingAllEnemies_endsTwoPlayerGame() {
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertTrue("Partida terminada", after.isGameOver)
        assertEquals(MpEndReason.LAST_STANDING, after.result?.reason)
        assertEquals(listOf(P1), after.result?.winners)
        assertEquals(SeatStatus.RETIRED, after.seats.first { it.color == P2 }.status)
    }

    @Test
    fun threePlayers_reduceToTwo_winnerByPieceMajority() {
        // P1 captura la última pieza de P3 → quedan P1 (3) y P2 (1) → gana P1 por mayoría.
        val state = threePlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("C10") to Piece(P1, hasLeftBase = true),
                v("E5") to Piece(P2, hasLeftBase = true),
                v("B1") to Piece(P3, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertTrue(after.isGameOver)
        assertEquals(MpEndReason.PIECE_MAJORITY, after.result?.reason)
        assertEquals(listOf(P1), after.result?.winners)
        assertEquals(3, after.pieceCount(P1))
        assertEquals(1, after.pieceCount(P2))
    }

    @Test
    fun threePlayers_reduceToTwo_tieIsSharedVictory() {
        val state = threePlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("E5") to Piece(P2, hasLeftBase = true),
                v("C10") to Piece(P2, hasLeftBase = true),
                v("B1") to Piece(P3, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertTrue(after.isGameOver)
        assertEquals(MpEndReason.SHARED, after.result?.reason)
        assertEquals(setOf(P1, P2), after.result?.winners?.toSet())
    }

    @Test
    fun immobilizedPlayer_retiresOnItsTurn() {
        // P2 encerrado en A1 (todos sus vecinos B1..B6 ocupados por P1). P1 mueve una pieza lejana
        // manteniendo la jaula → en el turno de P2, inmovilizado → se retira → gana P1.
        val cage = (1..6).associate { v("B$it") to Piece(P1, hasLeftBase = true) }
        val state = twoPlayerState(
            cage + mapOf(
                v("A1") to Piece(P2, hasLeftBase = true),
                v("C10") to Piece(P1, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("C10"), v("C11")))
        assertTrue(after.isGameOver)
        assertEquals(MpEndReason.LAST_STANDING, after.result?.reason)
        assertEquals(listOf(P1), after.result?.winners)
        assertFalse("Las piezas del retirado salen del tablero", after.pieces.containsKey(v("A1")))
    }

    // ---- Validación ----

    @Test(expected = IllegalArgumentException::class)
    fun applyMove_rejectsIllegalMove() {
        // D1→D18 es ilegal al inicio (anillo D con pieza en base).
        MpRules.applyMove(MpSetup.initialState(2), MpMove(v("D1"), v("D18")))
    }

    @Test
    fun cannotMoveOntoOccupiedVertex() {
        // Dos piezas P1 adyacentes: no puede moverse una sobre la otra.
        val state = twoPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C2") to Piece(P1, hasLeftBase = true),
            ),
        )
        val moves = MpRules.legalMoves(state)
        assertFalse(moves.contains(MpMove(v("C1"), v("C2"))))
        assertFalse(moves.contains(MpMove(v("C2"), v("C1"))))
    }
}
