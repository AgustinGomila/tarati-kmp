package com.agustin.tarati.game6.ai

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.ai.MpGreedyBot
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tests del bot greedy multijugador (MpGreedyBot) — §8 del plan. */
class MpGreedyBotTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    private fun twoPlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(2).copy(pieces = pieces, currentSeatIndex = 0)

    @Test
    fun choosesLegalMove_fromInitialState() {
        val state = MpSetup.initialState(2)
        val move = MpGreedyBot.chooseMove(state, random = Random(0))
        assertNotNull(move)
        assertTrue("El movimiento elegido es legal", MpRules.isLegal(state, move ?: return))
    }

    @Test
    fun returnsNull_whenNoMoves() {
        // Jugador actual sin piezas → sin movimientos legales → null (guarda defensiva).
        val noMoves = twoPlayerState(emptyMap())
        assertNull(MpGreedyBot.chooseMove(noMoves))
    }

    @Test
    fun takesWinningCapture() {
        // P1 puede capturar la última pieza de P2 (B1) → victoria. El bot debe cerrarla.
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
            ),
        )
        val move = MpGreedyBot.chooseMove(state, random = Random(0)) ?: return
        val after = MpRules.applyMove(state, move)
        assertTrue("La partida termina", after.isGameOver)
        assertEquals(listOf(P1), after.result?.winners)
    }

    @Test
    fun prefersCaptureOverQuietMove() {
        // Capturar B1 mejora el material sin exponerse (P2 solo tiene E7, lejano).
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
                v("E7") to Piece(P2, hasLeftBase = true),
            ),
        )
        val move = MpGreedyBot.chooseMove(state, random = Random(0)) ?: return
        val captured = MpRules.captureTargets(state.pieces, move)
        assertTrue("El bot elige un movimiento que captura B1", captured.contains(v("B1")))
    }

    @Test
    fun selfPlay_producesValidPlayableStates() {
        // Smoke de integración motor + bot. El motor multijugador no tiene regla de tablas (§2.4) y
        // dos greedy espejo pueden no terminar (por la pre-adyacencia, una pieza ya adyacente a un
        // enemigo no puede capturarlo), así que NO exigimos fin. Sí exigimos que el ciclo corra sin
        // excepciones, que todo estado en curso sea jugable (el motor nunca cede el turno a un
        // jugador sin movimientos) y que, si termina, haya un ganador válido.
        var state = MpSetup.initialState(3)
        val random = Random(7)
        var plies = 0
        val cap = 400
        while (!state.isGameOver && plies < cap) {
            assertTrue("Jugador activo siempre tiene movimientos", MpRules.legalMoves(state).isNotEmpty())
            assertEquals("El asiento en turno está activo", SeatStatus.ACTIVE, state.currentSeat.status)
            val move = MpGreedyBot.chooseMove(state, random = random) ?: return
            state = MpRules.applyMove(state, move)
            plies++
        }
        if (state.isGameOver) {
            assertTrue("Si terminó, hay ganador", (state.result ?: return).winners.isNotEmpty())
        }
    }
}
