package com.agustin.tarati.core.data.database.dto

import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.core.domain.game.play.HistoryEntry

/**
 * Reproduce el historial de movimientos de la partida desde [GameDto.initialBoardPosition]
 * y devuelve un [HistoryEntry] por movimiento (la jugada + el estado resultante).
 *
 * Reusado por el detalle de partida (lista de movimientos) y por el análisis
 * post-partida (gráfico de evaluación). Si la posición inicial no parsea, cae al
 * tablero inicial estándar.
 */
fun GameDto.toHistoryEntries(): List<HistoryEntry> {
    var state = runCatching { parseBoardNotation(initialBoardPosition) }.getOrElse { initialGameState() }
    return moveHistory.map { move ->
        state = state.applyMove(move)
        HistoryEntry(move, state)
    }
}
