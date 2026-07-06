package com.agustin.tarati.game6.play

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.Zone
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpNotation
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.play.toPositionNotation
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la notación de texto del juego multijugador (§10 del plan). */
class MpNotationTest {

    private fun v(zone: Char, position: Int) = Vertex(Zone(zone), position)

    // ── Letras de jugador ────────────────────────────────────────────────────────

    @Test
    fun playerLetters_mapP1ToAThroughP6ToF() {
        assertEquals('A', PlayerColor.P1.letter)
        assertEquals('B', PlayerColor.P2.letter)
        assertEquals('F', PlayerColor.P6.letter)
    }

    @Test
    fun fromLetter_isInverseAndCaseInsensitive() {
        assertEquals(PlayerColor.P1, PlayerColor.fromLetter('A'))
        assertEquals(PlayerColor.P1, PlayerColor.fromLetter('a'))
        assertEquals(PlayerColor.P6, PlayerColor.fromLetter('F'))
        PlayerColor.entries.forEach { assertEquals(it, PlayerColor.fromLetter(it.letter)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromLetter_rejectsOutOfRange() {
        PlayerColor.fromLetter('G')
    }

    // ── Posición (FEN) ───────────────────────────────────────────────────────────

    @Test
    fun sixPlayerInitialPosition_matchesExpectedFen() {
        val expected =
            "D1A/D2A/E1A/E2A/" +
                "D4B/D5B/E3B/E4B/" +
                "D7C/D8C/E5C/E6C/" +
                "D10D/D11D/E7D/E8D/" +
                "D13E/D14E/E9E/E10E/" +
                "D16F/D17F/E11F/E12F A"
        assertEquals(expected, MpSetup.initialState(6).toPositionNotation())
    }

    @Test
    fun twoPlayerInitialPosition_matchesExpectedFen() {
        val expected = "D1A/D2A/E1A/E2A/D10B/D11B/E7B/E8B A"
        assertEquals(expected, MpSetup.initialState(2).toPositionNotation())
    }

    @Test
    fun ordering_isByPlayerThenZoneThenIndex_andCasingEncodesHasLeftBase() {
        // Jugador A con piezas en zonas A/C/E (mezcladas), B con una en zona B.
        // El bloque de A va completo antes que el de B, aunque B1 (zona B) sea "menor"
        // que C1/E1 de A. Dentro de A: zona A < C < E.
        val state = MpGameState(
            pieces = mapOf(
                v('E', 1) to Piece(PlayerColor.P1), // en base → 'A'
                v('A', 1) to Piece(PlayerColor.P1, hasLeftBase = true),  // libre  → 'a'
                v('C', 1) to Piece(PlayerColor.P1, hasLeftBase = true),  // libre  → 'a'
                v('B', 1) to Piece(PlayerColor.P2, hasLeftBase = true),  // libre  → 'b'
            ),
            seats = listOf(Seat(PlayerColor.P1, baseId = 17), Seat(PlayerColor.P2, baseId = 20)),
            currentSeatIndex = 0,
        )
        assertEquals("A1a/C1a/E1A/B1b A", state.toPositionNotation())
    }

    @Test
    fun turnLetter_reflectsCurrentSeat() {
        val state = MpSetup.initialState(3).copy(currentSeatIndex = 2) // seat 2 = P3 = C
        assertTrue("Turno de C", state.toPositionNotation().endsWith(" C"))
    }

    @Test
    fun parsePosition_roundTripsPiecesAndTurn() {
        val state = MpSetup.initialState(6)
        val parsed = MpNotation.parsePosition(state.toPositionNotation())
        assertEquals(state.pieces, parsed.pieces)
        assertEquals(PlayerColor.P1, parsed.turn)
    }

    @Test
    fun parsePosition_readsCasingAsHasLeftBase() {
        val parsed = MpNotation.parsePosition("C1a/E1A B")
        assertEquals(true, parsed.pieces[v('C', 1)]?.hasLeftBase)
        assertEquals(false, parsed.pieces[v('E', 1)]?.hasLeftBase)
        assertEquals(PlayerColor.P1, parsed.pieces[v('C', 1)]?.owner)
        assertEquals(PlayerColor.P2, parsed.turn)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parsePosition_rejectsMalformed() {
        MpNotation.parsePosition("C1a-E1A") // sin separador de turno
    }

    // ── Movimientos ──────────────────────────────────────────────────────────────

    @Test
    fun serializeMove_prefixesPlayer() {
        assertEquals("A:D1-C1", MpNotation.serializeMove(PlayerColor.P1, MpMove(v('D', 1), v('C', 1))))
        assertEquals("D:C7-B4", MpNotation.serializeMove(PlayerColor.P4, MpMove(v('C', 7), v('B', 4))))
    }

    @Test
    fun parseMove_isInverseOfSerialize() {
        val pm = PlayerMove(PlayerColor.P4, MpMove(v('C', 7), v('B', 4)))
        assertEquals(pm, MpNotation.parseMove(MpNotation.serializeMove(pm.color, pm.move)))
    }

    @Test
    fun serializeAndParseHistory_roundTrip() {
        val history = listOf(
            PlayerMove(PlayerColor.P1, MpMove(v('D', 1), v('C', 1))),
            PlayerMove(PlayerColor.P4, MpMove(v('C', 7), v('B', 4))),
            PlayerMove(PlayerColor.P6, MpMove(v('D', 16), v('C', 11))),
        )
        val serialized = MpNotation.serializeHistory(history)
        assertEquals("A:D1-C1,D:C7-B4,F:D16-C11", serialized)
        assertEquals(history, MpNotation.parseHistory(serialized))
    }

    @Test
    fun emptyHistory_roundTrips() {
        assertEquals(emptyList<PlayerMove>(), MpNotation.parseHistory(""))
        assertEquals("", MpNotation.serializeHistory(emptyList()))
    }
}
