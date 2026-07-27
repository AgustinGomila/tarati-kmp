package com.agustin.tarati.core.domain.game6.rules

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpCutReason
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.play.SeatStatus
import com.agustin.tarati.core.domain.game6.rules.MpRules.resolveByCut

/**
 * Motor de reglas del juego multijugador (headless, sin estado propio).
 *
 * Reglas (patente, §2 del plan): movimiento omnidireccional a un punto adyacente vacío, con la
 * restricción de salida de base (una pieza que aún no salió solo puede moverse dentro de su base
 * o salir por la radial `D → C`); captura por **conversión** al color del que mueve, con la regla
 * de **pre-adyacencia** (no se captura a una pieza a la que ya se era adyacente desde el origen);
 * **retiro** de un jugador al quedar sin piezas (captura total) o sin movimientos (inmovilización);
 * la partida **termina al quedar 2 jugadores** (o 1 si empezaron 2), ganando el de más piezas
 * (empate → victoria compartida).
 *
 * La adyacencia se consulta contra un [BoardGraph] (por defecto [Board25]); la metadata de bases
 * proviene de [Board25].
 */
object MpRules {

    /** Movimientos legales del jugador al que le toca mover. Vacío si la partida terminó. */
    fun legalMoves(state: MpGameState, board: BoardGraph = Board25): List<MpMove> {
        if (state.isGameOver) return emptyList()
        return movesForSeat(state.pieces, state.currentSeat, board)
    }

    /** `true` si [move] es legal para el jugador actual en [state]. */
    fun isLegal(state: MpGameState, move: MpMove, board: BoardGraph = Board25): Boolean =
        move in legalMoves(state, board)

    /**
     * Aplica [move] (que debe ser legal) y devuelve el nuevo estado: mueve la pieza, resuelve las
     * capturas por conversión, procesa retiros y avanza el turno (o marca el resultado final).
     *
     * Si se pasa [cut], se aplica además el **corte por N jugadas sin conversión** (§2.6 del plan): si
     * tras la jugada la partida sigue en curso y `movesSinceCapture` alcanzó
     * [MpCutConfig.maxMovesWithoutCapture], se resuelve por mayoría de piezas
     * ([MpCutReason.NO_CAPTURE_LIMIT]). El corte por **triple repetición** requiere historial y lo
     * aplica [MpMatch], no esta función pura.
     */
    fun applyMove(
        state: MpGameState,
        move: MpMove,
        board: BoardGraph = Board25,
        cut: MpCutConfig? = null,
    ): MpGameState {
        require(!state.isGameOver) { "La partida ya terminó" }
        require(isLegal(state, move, board)) { "Movimiento ilegal: ${move.name}" }

        val seat = state.currentSeat
        val color = seat.color
        val movingPiece = state.pieces.getValue(move.from)
        val baseSquare = Board25.baseById(seat.baseId).startSquare.toSet()

        val newPieces = state.pieces.toMutableMap()
        newPieces.remove(move.from)
        newPieces[move.to] = movingPiece.copy(
            hasLeftBase = movingPiece.hasLeftBase || move.to !in baseSquare,
        )

        // Captura por conversión con pre-adyacencia: se voltean los enemigos vecinos del destino
        // que NO eran vecinos del origen (aproximación desde afuera).
        val originNeighbors = board.neighborsOf(move.from).toSet()
        var converted = false
        board.neighborsOf(move.to).forEach { neighbor ->
            val target = newPieces[neighbor] ?: return@forEach
            if (target.owner == color) return@forEach
            if (neighbor in originNeighbors) return@forEach
            newPieces[neighbor] = target.copy(owner = color, hasLeftBase = true)
            converted = true
        }

        // "Progreso" en MP = una conversión: resetea el contador de estancamiento, si no lo incrementa.
        val movesSinceCapture = if (converted) 0 else state.movesSinceCapture + 1

        val settled = settleAfterMove(
            pieces = newPieces.toMap(),
            seats = state.seats,
            justMovedIndex = state.currentSeatIndex,
            moveCount = state.moveCount + 1,
            movesSinceCapture = movesSinceCapture,
            board = board,
        )

        // Corte por N jugadas sin conversión (solo si sigue en curso).
        if (cut != null && !settled.isGameOver && settled.movesSinceCapture >= cut.maxMovesWithoutCapture) {
            return resolveByCut(settled, MpCutReason.NO_CAPTURE_LIMIT)
        }
        return settled
    }

    /**
     * Corta una partida **en curso** por estancamiento y la resuelve por **mayoría de piezas** entre
     * los asientos activos (§2.6 del plan): el/los de mayor conteo ganan; empate del máximo →
     * victoria compartida ([MpEndReason.SHARED]). No introduce "tablas": siempre hay ganador(es). El
     * resultado registra la causa del corte en [MpResult.cut] = [reason].
     */
    fun resolveByCut(state: MpGameState, reason: MpCutReason): MpGameState {
        require(!state.isGameOver) { "La partida ya terminó" }
        return state.copy(result = finalResult(state.pieces, state.seats, cut = reason))
    }

    /**
     * Movimientos legales de un asiento concreto, sin exigir que sea su turno (para IA/análisis:
     * p.ej. estimar las amenazas de los rivales).
     */
    fun legalMovesFor(state: MpGameState, seat: Seat, board: BoardGraph = Board25): List<MpMove> =
        movesForSeat(state.pieces, seat, board)

    /**
     * Destinos "de forma legal" para la pieza en [from] del asiento [seat], **ignorando la ocupación
     * del destino** salvo por piezas propias. Respeta la geometría y la restricción de salida de base.
     *
     * Lo usa el pre-movimiento: un jugador puede apuntar a una casilla ocupada por un rival previendo
     * que se desocupe cuando llegue su turno. La legalidad real se revalida al ejecutar
     * ([MpPreMove.isReady]); si el destino sigue ocupado o el movimiento dejó de ser legal, se descarta.
     */
    fun preMoveTargetsFor(
        state: MpGameState,
        seat: Seat,
        from: Vertex,
        board: BoardGraph = Board25,
    ): Set<Vertex> {
        val piece = state.pieces[from]?.takeIf { it.owner == seat.color } ?: return emptySet()
        val base = Board25.baseById(seat.baseId)
        val baseSquare = base.startSquare.toSet()
        val cExit = base.cExit.toSet()
        return board.neighborsOf(from).filter { to ->
            // Excluir destinos ocupados por piezas PROPIAS (esas son re-selección, no destino).
            state.pieces[to]?.owner != seat.color &&
                    // Salida de base: si aún no salió, solo dentro del cuadrado o por la radial D → C.
                    (piece.hasLeftBase || to in baseSquare || to in cExit)
        }.toSet()
    }

    /**
     * Vértices que [move] convertiría a su color aplicando la regla de captura con pre-adyacencia,
     * según el dueño de la pieza en `move.from`. Lista vacía si no hay pieza en el origen. No muta
     * el estado (es una predicción para heurísticas).
     */
    fun captureTargets(
        pieces: Map<Vertex, Piece>,
        move: MpMove,
        board: BoardGraph = Board25,
    ): List<Vertex> {
        val mover = pieces[move.from] ?: return emptyList()
        val originNeighbors = board.neighborsOf(move.from).toSet()
        return board.neighborsOf(move.to).filter { neighbor ->
            neighbor != move.from &&
                    pieces[neighbor]?.owner?.let { it != mover.owner } == true &&
                    neighbor !in originNeighbors
        }
    }

    /**
     * Retira **forzadamente** al jugador [color] de una partida en curso (p.ej. desconexión o timeout
     * por turno — disparador 3 del corte, §2.6 del plan). Marca su asiento RETIRED y saca sus piezas
     * del tablero (regla de retiro de la patente). Si con eso quedan ≤ umbral de jugadores, la partida
     * termina por mayoría de piezas; si el retirado tenía el turno, se avanza al próximo asiento activo
     * con movimientos (retirando inmovilizados por el camino); si no, el turno actual se preserva.
     * No-op si [color] no es un asiento activo.
     */
    fun retire(state: MpGameState, color: PlayerColor, board: BoardGraph = Board25): MpGameState {
        require(!state.isGameOver) { "La partida ya terminó" }
        val isActive = state.seats.any { it.color == color && it.status == SeatStatus.ACTIVE }
        if (!isActive) return state

        val pieces = state.pieces.filterValues { it.owner != color }
        val seats = markRetired(state.seats, color)

        if (isTerminal(seats)) {
            return terminalState(pieces, seats, state.currentSeatIndex, state.moveCount, state.movesSinceCapture)
        }
        return if (state.currentSeat.color == color) {
            // Se retiró quien tenía el turno → avanzar (settle) desde su índice.
            settleAfterMove(pieces, seats, state.currentSeatIndex, state.moveCount, state.movesSinceCapture, board)
        } else {
            // Se retiró otro asiento → el turno actual no cambia.
            state.copy(pieces = pieces, seats = seats)
        }
    }

    // ========== Internos ==========

    /** Movimientos legales de un asiento concreto sobre un mapa de piezas dado. */
    private fun movesForSeat(
        pieces: Map<Vertex, Piece>,
        seat: Seat,
        board: BoardGraph,
    ): List<MpMove> {
        val base = Board25.baseById(seat.baseId)
        val baseSquare = base.startSquare.toSet()
        val cExit = base.cExit.toSet()

        val moves = mutableListOf<MpMove>()
        pieces.forEach { (from, piece) ->
            if (piece.owner != seat.color) return@forEach
            board.neighborsOf(from).forEach { to ->
                if (pieces.containsKey(to)) return@forEach // punto ocupado
                if (!piece.hasLeftBase) {
                    // Salida de base: solo dentro del cuadrado o por la radial D → C.
                    if (to !in baseSquare && to !in cExit) return@forEach
                }
                moves.add(MpMove(from, to))
            }
        }
        return moves
    }

    /**
     * Tras un movimiento: retira jugadores sin piezas, avanza al siguiente asiento activo
     * retirando por el camino a los inmovilizados, y determina el resultado si corresponde.
     */
    private fun settleAfterMove(
        pieces: Map<Vertex, Piece>,
        seats: List<Seat>,
        justMovedIndex: Int,
        moveCount: Int,
        movesSinceCapture: Int,
        board: BoardGraph,
    ): MpGameState {
        // 1. Retiro inmediato por captura total (0 piezas). No hay piezas que quitar del tablero.
        var currentPieces = pieces
        var currentSeats = retireZeroPieceSeats(currentPieces, seats)

        if (isTerminal(currentSeats)) {
            return terminalState(currentPieces, currentSeats, justMovedIndex, moveCount, movesSinceCapture)
        }

        // 2. Buscar el próximo asiento activo que pueda mover; retirar inmovilizados en el camino.
        var index = justMovedIndex
        repeat(seats.size) {
            index = (index + 1) % seats.size
            val seat = currentSeats[index]
            if (seat.status != SeatStatus.ACTIVE) return@repeat

            if (movesForSeat(currentPieces, seat, board).isNotEmpty()) {
                return MpGameState(
                    pieces = currentPieces,
                    seats = currentSeats,
                    currentSeatIndex = index,
                    moveCount = moveCount,
                    movesSinceCapture = movesSinceCapture,
                )
            }

            // Inmovilizado: se retira y sus piezas salen del tablero.
            currentPieces = currentPieces.filterValues { it.owner != seat.color }
            currentSeats = markRetired(currentSeats, seat.color)
            if (isTerminal(currentSeats)) {
                return terminalState(currentPieces, currentSeats, index, moveCount, movesSinceCapture)
            }
        }

        // Sin asientos activos con movimientos (todos inmovilizados): cierre por resultado.
        return terminalState(currentPieces, currentSeats, justMovedIndex, moveCount, movesSinceCapture)
    }

    private fun retireZeroPieceSeats(pieces: Map<Vertex, Piece>, seats: List<Seat>): List<Seat> =
        seats.map { seat ->
            if (seat.status == SeatStatus.ACTIVE && pieces.values.none { it.owner == seat.color }) {
                seat.copy(status = SeatStatus.RETIRED)
            } else {
                seat
            }
        }

    private fun markRetired(seats: List<Seat>, color: PlayerColor): List<Seat> =
        seats.map { if (it.color == color) it.copy(status = SeatStatus.RETIRED) else it }

    /** Umbral de jugadores activos en el que la partida termina (1 si empezaron 2, si no 2). */
    private fun endThreshold(totalSeats: Int): Int = if (totalSeats <= 2) 1 else 2

    private fun isTerminal(seats: List<Seat>): Boolean =
        seats.count { it.status == SeatStatus.ACTIVE } <= endThreshold(seats.size)

    private fun terminalState(
        pieces: Map<Vertex, Piece>,
        seats: List<Seat>,
        currentSeatIndex: Int,
        moveCount: Int,
        movesSinceCapture: Int,
    ): MpGameState = MpGameState(
        pieces = pieces,
        seats = seats,
        currentSeatIndex = currentSeatIndex,
        moveCount = moveCount,
        movesSinceCapture = movesSinceCapture,
        result = finalResult(pieces, seats, cut = null),
    )

    /**
     * Ranking final por mayoría de piezas entre los asientos **activos**: el/los de mayor conteo
     * ganan (empate del máximo → [MpEndReason.SHARED]; un único activo → [MpEndReason.LAST_STANDING];
     * si no, [MpEndReason.PIECE_MAJORITY]). [cut] registra la causa del corte por estancamiento o
     * `null` si fue terminación natural. Compartido por la terminación natural y por [resolveByCut].
     */
    private fun finalResult(
        pieces: Map<Vertex, Piece>,
        seats: List<Seat>,
        cut: MpCutReason?,
    ): MpResult {
        // Un solo conteo de piezas por color (los retirados no tienen piezas → cuentan 0).
        val countByColor = pieces.values.groupingBy { it.owner }.eachCount()
        fun countOf(seat: Seat) = countByColor[seat.color] ?: 0

        val active = seats.filter { it.status == SeatStatus.ACTIVE }
        val maxCount = active.maxOfOrNull { countOf(it) } ?: 0
        val winners = active.filter { countOf(it) == maxCount }.map { it.color }

        val reason = when {
            winners.size != 1 -> MpEndReason.SHARED
            active.size == 1 -> MpEndReason.LAST_STANDING
            else -> MpEndReason.PIECE_MAJORITY
        }

        val finalCounts = seats.associate { it.color to countOf(it) }

        return MpResult(winners = winners, reason = reason, finalPieceCounts = finalCounts, cut = cut)
    }
}
