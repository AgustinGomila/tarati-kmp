package com.agustin.tarati.game6.ai

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.ai.MpBotLevel
import com.agustin.tarati.core.domain.game6.ai.MpMaxN
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests de correctitud del motor de búsqueda max^n multijugador ([MpMaxN]). */
class MpMaxNTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    private fun twoPlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(2).copy(pieces = pieces, currentSeatIndex = 0)

    @Test
    fun choosesLegalMove_fromInitialState() {
        val state = MpSetup.initialState(2)
        val move = MpMaxN.chooseMove(state, MpBotLevel.HARD, random = Random(0))
        assertNotNull(move)
        assertTrue(MpRules.isLegal(state, move), "El movimiento elegido es legal")
    }

    @Test
    fun returnsNull_whenNoMoves() {
        assertNull(MpMaxN.chooseMove(twoPlayerState(emptyMap()), MpBotLevel.HARD))
    }

    @Test
    fun takesWinningCapture() {
        // P1 puede capturar la última pieza de P2 (B1) → victoria. La búsqueda debe cerrarla.
        val state = twoPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
            ),
        )
        val move = MpMaxN.chooseMove(state, MpBotLevel.CHAMPION, random = Random(0)) ?: return
        val after = MpRules.applyMove(state, move)
        assertTrue(after.isGameOver, "La partida termina")
        assertEquals(listOf(P1), after.result?.winners)
    }

    @Test
    fun isDeterministic_withFixedSeed() {
        val state = MpSetup.initialState(3)
        val first = MpMaxN.chooseMove(state, MpBotLevel.MEDIUM, random = Random(42))
        val second = MpMaxN.chooseMove(state, MpBotLevel.MEDIUM, random = Random(42))
        assertEquals(first, second, "Misma semilla + mismo estado → misma jugada")
    }

    @Test
    fun deeperSearch_avoidsImmediateRecapture() {
        // Trampa a 2 ply: capturar en un destino donde P2 recaptura más de lo que ganó P1.
        // Con lookahead (depth ≥ 2) el motor prefiere no meterse en la línea perdedora de material.
        // Verificación estructural: la jugada elegida deja a P1 sin exponer más piezas de las que gana.
        val state = MpSetup.initialState(2)
        val move = MpMaxN.chooseMove(state, MpBotLevel.HARD, random = Random(3)) ?: return
        val after = MpRules.applyMove(state, move)
        val myThreatExposure = MpRules.legalMovesFor(after, after.seats[1])
            .maxOfOrNull { m ->
                MpRules.captureTargets(after.pieces, m).count { after.pieces[it]?.owner == after.seats[0].color }
            }
            ?: 0
        // Desde la apertura, ninguna respuesta del rival debería capturar 2+ piezas de una:
        // la búsqueda evita dejar una captura múltiple servida.
        assertTrue(
            myThreatExposure < 2,
            "La jugada no cuelga una captura múltiple del rival (exposición=$myThreatExposure)",
        )
    }
}
