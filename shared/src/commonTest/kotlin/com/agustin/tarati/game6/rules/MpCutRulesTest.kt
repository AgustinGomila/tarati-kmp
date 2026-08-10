package com.agustin.tarati.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.ai.MpGreedyBot
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P3
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P4
import com.agustin.tarati.core.domain.game6.play.MpCutReason
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpCutConfig
import com.agustin.tarati.core.domain.game6.rules.MpMatch
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del **corte de partida por estancamiento** del juego multijugador (§2.6 del plan): contador
 * `movesSinceCapture`, corte por N jugadas sin conversión, corte por triple repetición (vía [MpMatch])
 * y resolución por mayoría de piezas (nunca "tablas"). Ver [MpRules.resolveByCut] y [MpCutConfig].
 */
class MpCutRulesTest {

    private fun v(name: String): Vertex = Vertex.parseVertex(name)

    private fun twoPlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(2).copy(pieces = pieces, currentSeatIndex = 0)

    private fun threePlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(3).copy(pieces = pieces, currentSeatIndex = 0)

    private fun fourPlayerState(pieces: Map<Vertex, Piece>): MpGameState =
        MpSetup.initialState(4).copy(pieces = pieces, currentSeatIndex = 0)

    // ---- movesSinceCapture ----

    @Test
    fun movesSinceCapture_incrementsOnQuietMove() {
        // 4 jugadores lejanos: P1 mueve sin capturar → el contador sube en 1.
        val state = fourPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("E7") to Piece(P2, hasLeftBase = true),
                v("E5") to Piece(P3, hasLeftBase = true),
                v("E11") to Piece(P4, hasLeftBase = true),
            ),
        ).copy(movesSinceCapture = 5)

        val after = MpRules.applyMove(state, MpMove(v("C1"), v("D1")))
        assertFalse(after.isGameOver)
        assertEquals(6, after.movesSinceCapture)
    }

    @Test
    fun movesSinceCapture_resetsOnCapture() {
        // P1 en B3 mueve a A1 y convierte B1 (P2); P2 conserva E7 → 4 activos, no termina, contador a 0.
        val state = fourPlayerState(
            mapOf(
                v("B3") to Piece(P1, hasLeftBase = true),
                v("B1") to Piece(P2, hasLeftBase = true),
                v("E7") to Piece(P2, hasLeftBase = true),
                v("E5") to Piece(P3, hasLeftBase = true),
                v("E11") to Piece(P4, hasLeftBase = true),
            ),
        ).copy(movesSinceCapture = 5)

        val after = MpRules.applyMove(state, MpMove(v("B3"), v("A1")))
        assertFalse(after.isGameOver, "La partida sigue (4 activos)")
        assertEquals(P1, after.pieces.getValue(v("B1")).owner, "El B1 se convirtió")
        assertEquals(0, after.movesSinceCapture)
    }

    // ---- Corte por N jugadas sin conversión ----

    @Test
    fun noCaptureLimit_cutsWithMajorityWinner() {
        // Umbral 1 → la primera jugada tranquila corta. P1 tiene 2 piezas, P2 tiene 1 → gana P1.
        val cut = MpCutConfig(repetitionLimit = 99, maxMovesWithoutCapture = 1)
        val state = twoPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C2") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("C1"), v("D1")), cut = cut)
        assertTrue(after.isGameOver)
        assertEquals(MpCutReason.NO_CAPTURE_LIMIT, after.result?.cut)
        assertEquals(MpEndReason.PIECE_MAJORITY, after.result?.reason)
        assertEquals(listOf(P1), after.result?.winners)
    }

    @Test
    fun noCaptureLimit_tieIsSharedVictory() {
        // 1 pieza cada uno, sin captura posible → al cortar, empate → victoria compartida.
        val cut = MpCutConfig(repetitionLimit = 99, maxMovesWithoutCapture = 1)
        val state = twoPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
            ),
        )
        val after = MpRules.applyMove(state, MpMove(v("C1"), v("D1")), cut = cut)
        assertTrue(after.isGameOver)
        assertEquals(MpCutReason.NO_CAPTURE_LIMIT, after.result?.cut)
        assertEquals(MpEndReason.SHARED, after.result?.reason)
        assertEquals(setOf(P1, P2), after.result?.winners?.toSet())
    }

    @Test
    fun noCut_whenConfigNotProvided() {
        // Sin config de corte, applyMove nunca corta (compatibilidad con el motor puro / tests previos).
        val state = twoPlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
            ),
        ).copy(movesSinceCapture = 999)
        val after = MpRules.applyMove(state, MpMove(v("C1"), v("D1")))
        assertFalse(after.isGameOver)
        assertEquals(1000, after.movesSinceCapture)
    }

    // ---- resolveByCut (resolución directa por mayoría) ----

    @Test
    fun resolveByCut_ranksActiveSeatsByMajority() {
        // P1=2, P2=1, P3=1 → gana P1 por mayoría; se registra la causa del corte; no cambia el turno.
        val state = threePlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C2") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
                v("C9") to Piece(P3, hasLeftBase = true),
            ),
        )
        val cut = MpRules.resolveByCut(state, MpCutReason.REPETITION)
        assertTrue(cut.isGameOver)
        assertEquals(MpEndReason.PIECE_MAJORITY, cut.result?.reason)
        assertEquals(MpCutReason.REPETITION, cut.result?.cut)
        assertEquals(listOf(P1), cut.result?.winners)
        assertEquals(state.currentSeatIndex, cut.currentSeatIndex, "No altera el turno ni el contador")
    }

    @Test
    fun resolveByCut_rejectsFinishedGame() {
        val ongoing = threePlayerState(
            mapOf(
                v("C1") to Piece(P1, hasLeftBase = true),
                v("C2") to Piece(P1, hasLeftBase = true),
                v("C7") to Piece(P2, hasLeftBase = true),
                v("C9") to Piece(P3, hasLeftBase = true),
            ),
        )
        val finished = MpRules.resolveByCut(ongoing, MpCutReason.REPETITION)
        assertFailsWith<IllegalArgumentException> {
            MpRules.resolveByCut(finished, MpCutReason.REPETITION) // ya terminó → excepción
        }
    }

    // ---- Corte por triple repetición (MpMatch) ----

    @Test
    fun threefoldRepetition_cutsViaMatch() {
        // P1 oscila C1↔D1, P2 oscila C7↔D10 (jamás adyacentes → sin capturas). La posición inicial se
        // repite cada 4 plies; a la 3ª aparición (8º ply) el runner corta por repetición → empate.
        val cut = MpCutConfig(maxMovesWithoutCapture = 99)
        val match = MpMatch(
            twoPlayerState(
                mapOf(
                    v("C1") to Piece(P1, hasLeftBase = true),
                    v("C7") to Piece(P2, hasLeftBase = true),
                ),
            ),
            cut = cut,
        )

        val cycle = listOf(
            MpMove(v("C1"), v("D1")), MpMove(v("C7"), v("D10")),
            MpMove(v("D1"), v("C1")), MpMove(v("D10"), v("C7")),
        )
        // Dos vueltas completas (8 plies): la 2ª cierra la 3ª aparición de la posición inicial.
        repeat(2) { cycle.forEach { match.applyMove(it) } }

        assertTrue(match.state.isGameOver, "Cortada por repetición")
        assertEquals(MpCutReason.REPETITION, match.state.result?.cut)
        assertEquals(MpEndReason.SHARED, match.state.result?.reason)
        assertEquals(setOf(P1, P2), match.state.result?.winners?.toSet())
    }

    @Test
    fun match_beforeThreshold_doesNotCut() {
        // Una sola vuelta (2ª aparición): aún no se alcanza el umbral de 3 → sigue en curso.
        val cut = MpCutConfig(maxMovesWithoutCapture = 99)
        val match = MpMatch(
            twoPlayerState(
                mapOf(
                    v("C1") to Piece(P1, hasLeftBase = true),
                    v("C7") to Piece(P2, hasLeftBase = true),
                ),
            ),
            cut = cut,
        )
        listOf(
            MpMove(v("C1"), v("D1")), MpMove(v("C7"), v("D10")),
            MpMove(v("D1"), v("C1")), MpMove(v("D10"), v("C7")),
        ).forEach { match.applyMove(it) }
        assertFalse(match.state.isGameOver)
    }

    // ---- Retiro forzado (desconexión / timeout — disparador 3) ----

    @Test
    fun retire_currentPlayer_advancesTurn() {
        // 4 jugadores, turno de P1; P1 se retira → sus piezas salen, quedan 3 activos, turno pasa a P2.
        val after = MpRules.retire(MpSetup.initialState(4), P1)
        assertFalse(after.isGameOver)
        assertEquals(SeatStatus.RETIRED, after.seats.first { it.color == P1 }.status)
        assertEquals(0, after.pieceCount(P1))
        assertEquals(1, after.currentSeatIndex) // turno → P2
    }

    @Test
    fun retire_nonCurrentPlayer_preservesTurn() {
        // Turno de P1; se retira P3 (no es su turno) → el turno de P1 se conserva.
        val after = MpRules.retire(MpSetup.initialState(4), P3)
        assertFalse(after.isGameOver)
        assertEquals(SeatStatus.RETIRED, after.seats.first { it.color == P3 }.status)
        assertEquals(0, after.currentSeatIndex) // sigue P1
    }

    @Test
    fun retire_downToThreshold_endsByMajority() {
        // 3 jugadores; al retirarse P3 quedan 2 → termina; piezas iguales → victoria compartida, sin corte.
        val after = MpRules.retire(MpSetup.initialState(3), P3)
        assertTrue(after.isGameOver)
        assertEquals(MpEndReason.SHARED, after.result?.reason)
        assertNull(after.result?.cut, "Fin natural (no corte por estancamiento)")
    }

    @Test
    fun retire_inactiveColor_isNoOp() {
        // 4 jugadores: retirar P1 deja 3 activos (no termina); retirarlo de nuevo (ya retirado) es no-op.
        val once = MpRules.retire(MpSetup.initialState(4), P1)
        assertFalse(once.isGameOver)
        assertEquals(once, MpRules.retire(once, P1))
    }

    // ---- Garantía anti-infinito (motor + bot) ----

    @Test
    fun greedySelfPlay_alwaysTerminates() {
        // Con el corte activo, dos+ greedy espejo SIEMPRE terminan (por repetición o por no-captura),
        // a diferencia del smoke sin corte de MpGreedyBotTest. Valida que se cerró la deuda D8.
        val cut = MpCutConfig(maxMovesWithoutCapture = 30)
        val match = MpMatch(MpSetup.initialState(3), cut = cut)
        val random = Random(7)
        var plies = 0
        val safetyCap = 5000
        while (!match.state.isGameOver && plies < safetyCap) {
            val move = MpGreedyBot.chooseMove(match.state, random = random) ?: break
            match.applyMove(move)
            plies++
        }
        assertTrue(match.state.isGameOver, "La auto-partida termina antes del tope de seguridad")
        assertTrue((match.state.result ?: return).winners.isNotEmpty(), "Al terminar hay ganador(es)")
    }
}
