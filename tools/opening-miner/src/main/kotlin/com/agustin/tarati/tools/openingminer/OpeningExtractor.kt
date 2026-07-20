package com.agustin.tarati.tools.openingminer

import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameResult
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move

/**
 * Reproduce partidas a través del motor autoritativo de `shared` y emite una observación por cada
 * jugada de apertura: `(hash de la posición antes de mover, notación de la jugada, resultado para
 * el bando que movía)`.
 *
 * No agrega ni filtra: eso queda en [OpeningAggregator]. Es puro y determinista — la clave de
 * posición es [GameState.hashBoard] (Zobrist con semilla fija, estable entre plataformas y procesos).
 */
object OpeningExtractor {

    /** Cantidad de medias-jugadas de apertura que se registran por partida (horizonte del book). */
    const val DEFAULT_HORIZON_PLIES: Int = 10

    /** Una jugada observada durante el replay de una partida. */
    data class PlyObservation(
        val posHash: String,
        val moveName: String,
        val outcome: PlyOutcome,
    )

    /**
     * Parsea el PGN plano de la BD (tokens separados por espacio) a una lista de [Move].
     *
     * El PGN del servidor separa jugadas con espacio, mientras que [Move.parseMoveHistory] espera
     * comas; se normaliza a comas y se delega en el parser canónico (que además entiende el token
     * de promoción `=R` y el separador legacy `→`).
     *
     * @throws IllegalArgumentException si algún token no tiene forma de jugada válida.
     */
    fun parsePgn(pgn: String): List<Move> {
        val tokens = pgn.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return Move.parseMoveHistory(tokens.joinToString(","))
    }

    /**
     * Reproduce [record] desde la posición inicial y devuelve una [PlyObservation] por cada una de
     * las primeras [horizon] jugadas.
     *
     * Robustez: si el PGN es ilegible o una jugada resulta un no-op (token corrupto que [Move]
     * aplica sobre un origen vacío → estado sin cambios), se corta el replay de esa partida y se
     * devuelve lo acumulado hasta ahí, sin propagar la excepción.
     */
    fun extract(record: GameRecord, horizon: Int = DEFAULT_HORIZON_PLIES): List<PlyObservation> {
        val moves = try {
            parsePgn(record.pgn)
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }

        val observations = ArrayList<PlyObservation>(minOf(horizon, moves.size))
        var state = GameState.initialGameState()

        for ((i, move) in moves.withIndex()) {
            if (i >= horizon) break

            val sideToMove = state.currentTurn
            val rawHash = state.hashBoard()
            val next = state.applyMove(move)

            // Toda jugada legal invierte el turno → el bit de side-to-move del hash cambia siempre.
            // Un hash idéntico significa que applyMove tomó su rama no-op (origen sin pieza): el
            // token está corrupto y no se puede confiar en el resto del replay.
            if (next.hashBoard() == rawHash) break

            // Canonicalizar (posición, jugada) bajo la simetría bilateral del tablero: las aperturas
            // espejo colapsan en una sola clave (el tablero de Tarati es simétrico), duplicando la
            // muestra por posición y evitando que el sesgo izquierda/derecha de los bots las separe.
            val (canonHash, canonMove) = state.canonicalMove(move)
            observations += PlyObservation(canonHash, canonMove.name, outcomeFor(record.result, sideToMove))
            state = next
        }

        return observations
    }

    /** Traduce el resultado de la partida al punto de vista del bando que tenía el turno. */
    private fun outcomeFor(result: GameResult, sideToMove: CobColor): PlyOutcome = when (result) {
        GameResult.DRAW -> PlyOutcome.DRAW
        GameResult.WHITE_WIN -> if (sideToMove == CobColor.WHITE) PlyOutcome.WIN else PlyOutcome.LOSS
        GameResult.BLACK_WIN -> if (sideToMove == CobColor.BLACK) PlyOutcome.WIN else PlyOutcome.LOSS
    }

    private val WHITESPACE = Regex("\\s+")
}
