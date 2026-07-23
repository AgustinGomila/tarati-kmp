package com.agustin.tarati.features.game

import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de [resolveOpponentMoveSync] — la reconciliación del tablero del jugador ante un
 * `GameStateUpdate` del oponente en una partida online.
 *
 * Reproduce el race condition que causaba derrotas por tiempo: cuando un update intermedio se
 * conflaciona en el `StateFlow` (o un `applyMove` se descarta por lag de recomposición), el
 * tablero local queda un movimiento atrás del servidor. El movimiento del oponente entonces
 * **no es legal** desde el estado local, y un `applyMove` incremental se descartaría en silencio
 * dejando el tablero congelado. La reconciliación debe detectar ese caso y hacer **snap** al
 * estado completo del servidor.
 *
 * Los estados se derivan encadenando movimientos legales desde [initialGameState] para no depender
 * de vértices concretos del tablero.
 */
class OpponentMoveSyncTest {

    // Cadena de estados legales desde la posición inicial:
    //   base (WHITE) --m1--> s1 (BLACK) --m2--> s2 (WHITE) --m3--> s3 (BLACK)
    private val base = initialGameState()
    private val m1 = base.allMovesForTurn().first()
    private val s1 = base.applyMove(m1)
    private val m2 = s1.allMovesForTurn().first()
    private val s2 = s1.applyMove(m2)
    private val m3 = s2.allMovesForTurn().first()
    private val s3 = s2.applyMove(m3)

    @Test
    fun inSync_whenLocalBoardEqualsServer() {
        // El eco del propio movimiento (o un update ya aplicado): hashes de tablero coinciden.
        val result = resolveOpponentMoveSync(localState = s2, serverState = s2, lastMove = m2)
        assertEquals(OpponentMoveSync.InSync, result)
    }

    @Test
    fun animate_whenOpponentMoveIsLegalFromLocal() {
        // Caso normal: el local está exactamente un movimiento atrás y el movimiento del oponente
        // (m3, legal desde s2) puede animarse incrementalmente.
        assertTrue(s2.allMovesForTurn().contains(m3), "precondición: m3 legal desde s2")

        val result = resolveOpponentMoveSync(localState = s2, serverState = s3, lastMove = m3)
        assertEquals(OpponentMoveSync.Animate(m3), result)
    }

    @Test
    fun snap_whenLocalIsBehindAndOpponentMoveIsIllegal() {
        // El race: un update intermedio (m2) se perdió, el local quedó clavado en s1 (turno BLACK).
        // El movimiento del oponente m3 es un movimiento de WHITE — ilegal desde s1 — así que un
        // applyMove incremental lo descartaría y congelaría el tablero. Debe hacerse snap a s3.
        assertTrue(s1.hashBoard() != s3.hashBoard(), "precondición: local y servidor difieren")
        assertTrue(!s1.allMovesForTurn().contains(m3), "precondición: m3 ilegal desde s1")

        val result = resolveOpponentMoveSync(localState = s1, serverState = s3, lastMove = m3)
        assertEquals(OpponentMoveSync.Snap(s3), result)
    }

    @Test
    fun inSync_takesPrecedenceOverMoveLegality() {
        // Aunque se pase un lastMove, si los tableros ya coinciden no se toca nada (evita
        // re-aplicar el eco del propio movimiento sobre un tablero ya sincronizado).
        val result = resolveOpponentMoveSync(localState = s3, serverState = s3, lastMove = m3)
        assertEquals(OpponentMoveSync.InSync, result)
    }
}
