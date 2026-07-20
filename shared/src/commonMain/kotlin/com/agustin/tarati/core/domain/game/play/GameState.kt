package com.agustin.tarati.core.domain.game.play


import androidx.compose.runtime.Stable
import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.database.dto.PGNHeader.Companion.createPGNHeader
import com.agustin.tarati.core.domain.game.board.BoardSymmetry
import com.agustin.tarati.core.domain.game.board.GameBoard
import com.agustin.tarati.core.domain.game.board.GameBoard.adjacencyMap
import com.agustin.tarati.core.domain.game.board.GameBoard.deadVertices
import com.agustin.tarati.core.domain.game.board.GameBoard.getHomeBaseNonForwardMoves
import com.agustin.tarati.core.domain.game.board.GameBoard.isForwardMove
import com.agustin.tarati.core.domain.game.board.GameBoard.isValidMove
import com.agustin.tarati.core.domain.game.board.GameBoard.vertexToRegions
import com.agustin.tarati.core.domain.game.board.GameBoard.vertices
import com.agustin.tarati.core.domain.game.board.Region
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.helpers.GameStateBuilder
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.PieceCounts
import com.agustin.tarati.core.domain.game.pieces.flipAdjacentCobs
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState.Companion.POS_DELIMITER_CHAR
import com.agustin.tarati.core.domain.game.play.GameState.Companion.TURN_DELIMITER_CHAR
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.MatchResult.UNDEFINED
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class GameState(
    val cobs: Map<Vertex, Cob>,
    val currentTurn: CobColor,
    /**
     * Half-move clock for the 50-move rule (§7.2). Counts consecutive half-moves
     * (single-player turns) without a cob being moved or promoted. Resets to 0
     * whenever a cob is moved (forward, home-base), any promotion occurs, or a
     * rok move captures pieces; rok moves without captures increment it.
     * At 100 (= 50 moves per player) the game may be drawn,
     * unless the player to move has an immediate winning move available.
     */
    val halfMoveClock: Int = 0,
    /**
     * Set to true when a player explicitly claims the 50-move draw (§7.2.2).
     * This is the only way FIFTY_MOVES enters isGameOver — the rule is never
     * auto-applied; the human claims it via the UI, the AI auto-claims it via
     * a LaunchedEffect in GameScreen.
     */
    val claimedFiftyMoveDraw: Boolean = false,
    /**
     * Color del bando que perdió por tiempo, o `null` si la partida no terminó
     * por timeout. Seteado por [GameEvents.onTimeout]
     * cuando [IClockService.timeoutEvents] emite.
     *
     * Cuando es no-null:
     *  - [isGameOver] retorna `true`.
     *  - [getMatchState] retorna [GameEndReason.TIMEOUT] con `winner = timedOutColor.opponent`.
     *
     * No participa en [hashBoard] (que solo incluye cobs + currentTurn), por lo
     * que no afecta al transposition table de la IA ni al historial de posiciones.
     */
    val timedOutColor: CobColor? = null,
    /**
     * Color del bando que resignó, o `null` si la partida no terminó por resignación.
     * Seteado desde [GameScreen] al recibir un [OnlineGameStatus.Finished] con
     * `reason = RESIGNATION`.
     *
     * Cuando es no-null:
     *  - [isGameOver] retorna `true`.
     *  - [getMatchState] retorna [GameEndReason.RESIGNATION] con `winner = resignedColor.opponent`.
     *
     * No participa en [hashBoard]: es un evento externo que no describe la posición
     * del tablero y no debe afectar la tabla de transposición ni el historial de posiciones.
     */
    val resignedColor: CobColor? = null,
    /**
     * True cuando la partida terminó por acuerdo de tablas entre los jugadores.
     * Seteado desde [GameScreen] al recibir un [OnlineGameStatus.Finished] con
     * `reason = DRAW_AGREEMENT`.
     *
     * Cuando es `true`:
     *  - [isGameOver] retorna `true`.
     *  - [getMatchState] retorna [GameEndReason.DRAW_AGREEMENT] con `winner = null`.
     *
     * No participa en [hashBoard]: evento externo sin reflejo en la posición del tablero.
     */
    val drawAgreed: Boolean = false,
) {
    // Función de extensión para modificar piezas
    fun modifyCob(
        position: Vertex,
        cob: Cob?,
    ): GameState = this.modifyCob(position, cob?.color, cob?.isUpgraded)

    fun modifyCob(
        position: Vertex,
        color: CobColor? = null,
        isUpgraded: Boolean? = null,
    ): GameState {
        val newCobs = cobs.toMutableMap()

        if (color == null && isUpgraded == null) {
            // Si ambos son null, eliminar la pieza
            newCobs.remove(position)
        } else {
            val currentCob = newCobs[position]
            val newColor = color ?: currentCob?.color ?: WHITE
            val newUpgraded = isUpgraded ?: currentCob?.isUpgraded ?: false

            newCobs[position] = Cob(newColor, newUpgraded)
        }

        return this.copy(cobs = newCobs)
    }

    // Función para mover piezas
    fun moveCob(move: Move): GameState {
        val newCobs = cobs.toMutableMap()
        val cob = newCobs[move.from] ?: return this

        newCobs.remove(move.from)
        newCobs[move.to] = cob

        return this.copy(cobs = newCobs)
    }

    // Función para cambiar el turno
    fun withTurn(newTurn: CobColor): GameState = this.copy(currentTurn = newTurn)

    /**
     * Zobrist hash of the current position.
     *
     * Computes a 64-bit XOR fingerprint from precomputed fixed-seed keys for
     * each (vertex, piece-type) pair on the board, XOR-ed with a side-to-move
     * key when it is BLACK's turn. Probability of collision between distinct
     * positions is 1/2^64 -- negligible in practice. A plain data-class
     * [hashCode] (32-bit Int) would collide too easily for the transposition
     * table and the triple-repetition history.
     *
     * Complexity: O(n) over pieces on the board, with no String allocation
     * or sorting.
     *
     * Returns a hex String for drop-in compatibility with positionHistory,
     * TranspositionTable, and HybridEvaluationCache (all use String keys).
     */
    fun hashBoard(): String {
        var hash =
            if (currentTurn == BLACK) ZOBRIST_SIDE_TO_MOVE else 0L
        for ((vertex, cob) in cobs) {
            val vi = VERTEX_INDEX[vertex] ?: continue
            hash = hash xor ZOBRIST_PIECES[vi][pieceIndex(cob)]
        }
        return hash.toString(16)
    }

    /**
     * Hash de la posición reflejada especularmente (ver [BoardSymmetry]), computado sin construir el
     * estado espejo: cada pieza se indexa por su vértice reflejado. Preserva el turno.
     */
    fun mirroredHashBoard(): String {
        var hash =
            if (currentTurn == BLACK) ZOBRIST_SIDE_TO_MOVE else 0L
        for ((vertex, cob) in cobs) {
            val vi = VERTEX_INDEX[BoardSymmetry.mirror(vertex)] ?: continue
            hash = hash xor ZOBRIST_PIECES[vi][pieceIndex(cob)]
        }
        return hash.toString(16)
    }

    /**
     * Clave estable bajo la simetría bilateral del tablero: el menor entre [hashBoard] y
     * [mirroredHashBoard]. Dos posiciones espejo comparten `canonicalHash`.
     */
    fun canonicalHash(): String = minOf(hashBoard(), mirroredHashBoard())

    /**
     * Representante canónico del par (posición, jugada) bajo la reflexión especular: pliega los pares
     * espejo en una sola clave. Devuelve `(hash canónico, jugada en el marco canónico)`.
     *
     * En posiciones auto-simétricas (posición == su espejo, p. ej. la inicial) desempata por nombre
     * de jugada, de modo que una jugada y su espejo colapsan al mismo representante.
     */
    fun canonicalMove(move: Move): Pair<String, Move> {
        val hash = hashBoard()
        val mirroredHash = mirroredHashBoard()
        return when {
            hash < mirroredHash -> hash to move
            mirroredHash < hash -> mirroredHash to move.mirrored()
            else -> {
                val mirrored = move.mirrored()
                if (move.name <= mirrored.name) hash to move else hash to mirrored
            }
        }
    }

    private fun pieceIndex(cob: Cob): Int = when {
        cob.color == WHITE && !cob.isUpgraded -> 0
        cob.color == WHITE && cob.isUpgraded -> 1
        !cob.isUpgraded -> 2
        else -> 3
    }

    // ==================== Estado del Juego ====================

    fun isGameOver(positionHistory: Map<String, Int> = emptyMap()): Boolean {
        if (timedOutColor != null) return true
        if (resignedColor != null) return true
        if (drawAgreed) return true
        if (claimedFiftyMoveDraw) return true

        val whiteCobs = this.cobs.values.count { it.color == WHITE }
        val blackCobs = this.cobs.values.count { it.color == BLACK }

        return whiteCobs == 0 || blackCobs == 0 ||
                this.allMovesForTurn().isEmpty() ||
                this.hasTripleRepetition(positionHistory)
    }

    fun getWinner(
        positionHistory: Map<String, Int> = emptyMap(),
    ): CobColor? = getMatchState(positionHistory).winner

    fun getMatchState(
        positionHistory: Map<String, Int> = emptyMap(),
    ): MatchState {
        fun matchState(reason: GameEndReason, winner: CobColor?) =
            MatchState(
                gameState = this,
                gameEndReason = reason,
                winner = winner,
                positionHistory = positionHistory,
            )

        if (!isGameOver(positionHistory)) return matchState(GameEndReason.PLAYING, winner = null)

        // TIMEOUT tiene precedencia sobre todas las demás condiciones: es un
        // evento externo (del reloj) que invalida la partida independientemente
        // de la posición del tablero.
        timedOutColor?.let { loser -> return matchState(GameEndReason.TIMEOUT, winner = loser.opponent) }

        // RESIGNATION: evento externo sin cambio en el tablero.
        // Tiene precedencia sobre cualquier condición derivada de la posición.
        resignedColor?.let { loser -> return matchState(GameEndReason.RESIGNATION, winner = loser.opponent) }

        // DRAW_AGREEMENT: tablas pactadas externamente, sin movimiento final.
        if (drawAgreed) return matchState(GameEndReason.DRAW_AGREEMENT, winner = null)

        // Draw — no winner. Must be checked before triple repetition since both
        // could technically be true; 50-move draw takes precedence as it was
        // established first in game time.
        if (claimedFiftyMoveDraw) return matchState(GameEndReason.FIFTY_MOVES, winner = null)

        if (hasTripleRepetition(positionHistory)) return matchState(GameEndReason.TRIPLE, winner = currentTurn)

        val (whiteCobs, blackCobs) = getPieceCounts()
        return when {
            whiteCobs == 0 -> matchState(GameEndReason.MIT, winner = BLACK)
            blackCobs == 0 -> matchState(GameEndReason.MIT, winner = WHITE)
            // isGameOver ya descartó el resto: sin movimientos → STALEMIT.
            else -> matchState(GameEndReason.STALEMIT, winner = currentTurn.opponent)
        }
    }

    private fun hasTripleRepetition(
        positionHistory: Map<String, Int> = emptyMap(),
    ): Boolean {
        val hash = this.hashBoard()
        return (positionHistory[hash] ?: 0) >= 3
    }

    fun checkIfWouldCauseRepetition(
        positionHistory: Map<String, Int> = emptyMap(),
    ): Boolean {
        val hash = this.hashBoard()
        val currentCount = positionHistory[hash] ?: 0
        return (currentCount + 1) >= 3
    }

    /**
     * Returns true if the 50-move draw rule can be claimed (§7.2.2).
     *
     * Conditions:
     * - At least 100 consecutive half-moves (50 per player) without a cob move or promotion.
     * - The player to move does NOT have an immediate winning move available.
     *   (A player who is about to win cannot be forced into a draw by this rule.)
     *
     * Used by the UI to show the claim badge and by the AI to auto-claim.
     */
    fun canClaimFiftyMoveDraw(): Boolean {
        if (halfMoveClock < 100) return false
        return !hasWinningMoveAvailable()
    }

    /**
     * Returns true if the current player has at least one move that immediately ends
     * the game in their favour. Used to suppress the 50-move draw when a win is available.
     *
     * Intentionally avoids calling [isGameOver] on the resulting state to prevent
     * mutual recursion through [canClaimFiftyMoveDraw].
     */
    private fun hasWinningMoveAvailable(): Boolean =
        allMovesForTurn().any { move ->
            isImmediateWinAfter(applyMove(move))
        }

    /**
     * Checks whether [next] (the state after our move) is an immediate win for
     * [currentTurn] without going through [isGameOver] / [canClaimFiftyMoveDraw].
     */
    private fun isImmediateWinAfter(next: GameState): Boolean {
        val opponentColor = currentTurn.opponent
        // MIT: all opponent pieces captured
        if (next.cobs.values.none { it.color == opponentColor }) return true
        // STALEMIT: opponent has no normal moves and no forced promotions
        if (next.normalMovesForTurn().isEmpty() && next.getForcedPromotions().isEmpty()) return true
        return false
    }

    fun allMovesForTurn(): List<Move> {
        val normal = normalMovesForTurn()
        if (normal.isNotEmpty()) return normal
        return getForcedPromotions()
    }

    /**
     * Memoized list of standard moves for the current turn player.
     *
     * [GameState] is immutable, so this result is computed at most once per instance.
     * [LazyThreadSafetyMode.PUBLICATION] is required — NOT [LazyThreadSafetyMode.NONE]:
     * on the server, the root `session.gameState` is read concurrently by multiple
     * threads (GameSessionManager.processMove under the session lock, and each
     * BotPlayer's poll loop without it). NONE publishes the computed list through a
     * plain field, so a racing reader can observe a partially-published ArrayList
     * (NPE / IndexOutOfBounds while iterating). PUBLICATION publishes via CAS: readers
     * either see nothing (and compute their own copy of this pure function) or a fully
     * initialized list. Post-initialization cost is a single volatile read.
     *
     * Not a constructor parameter: invisible to equals, hashCode, copy, and Parcelize.
     */
    private val normalMoves: List<Move> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            cobs.forEach { (from, cob) ->
                if (cob.color != currentTurn) return@forEach
                // Movimientos no-forward desde base propia (incluye capturas desde base)
                addAll(getHomeBaseMoves(from, cob))
                // Movimientos normales (forward para cobs, cualquier dirección para roks)
                adjacencyMap[from]
                    ?.filter { to -> isValidMove(this@GameState, Move(from to to)) }
                    ?.forEach { to -> add(Move(from to to)) }
            }
        }
    }

    /** Returns [normalMoves], computing it at most once per [GameState] instance. */
    fun normalMovesForTurn(): List<Move> = normalMoves

    /**
     * Returns forced promotion moves available when the player has no normal moves.
     *
     * Both triggers share the same prerequisite: [normalMovesForTurn] must be empty.
     * "Wherever it is positioned" in the sole-piece rule refers to the piece's location
     * on the board, not to an exemption from the no-normal-moves requirement.
     *
     * Two triggers (patent §6.3 / §6.4):
     *
     * - **Sole remaining cob**: if the current player has exactly one piece left and it
     *   is not yet a rok, it must be promoted regardless of where it sits on the board.
     *
     * - **Dead piece unlock**: if the player has one or more dead cobs, any dead cob that
     *   would gain at least one move after promotion is eligible.
     *
     * Uses [normalMovesForTurn] (not [allMovesForTurn]) on the post-promotion state to
     * avoid infinite recursion.
     */
    fun getForcedPromotions(): List<Move> {
        val myPieces = cobs.entries.filter { it.value.color == currentTurn }

        // Prerequisite for both triggers: no normal moves available (patent §6.3 / §6.4).
        if (normalMovesForTurn().isNotEmpty()) return emptyList()

        // Sole remaining cob: must promote regardless of position (patent §6.4).
        // "Wherever it is positioned" means the board location does not matter —
        // not that the piece can be promoted while it still has legal moves.
        if (myPieces.size == 1 && !myPieces.first().value.isUpgraded) {
            return listOf(Move(myPieces.first().key to myPieces.first().key))
        }

        // Dead piece unlock: promote a dead cob only if doing so gains at least one move
        // (patent §6.3: "if this permits it to be moved").
        val deadCobs = getDeadCobsForCurrentTurn()
        if (deadCobs.isEmpty()) return emptyList()

        return deadCobs
            .filter { (vertex, _) ->
                applyPromotion(Move(vertex to vertex)).normalMovesForTurn().isNotEmpty()
            }
            .map { (vertex, _) -> Move(vertex to vertex) }
    }

    /**
     * Applies a forced in-place promotion: upgrades the cob at [move.from] to a rok
     * without moving it. The turn remains with the same player so they can move the
     * newly promoted rok immediately (patent §6.3: promotion replaces a normal move,
     * it does not consume the player's turn).
     */
    fun applyPromotion(move: Move): GameState {
        val mutableCobs = cobs.toMutableMap()
        val cob = mutableCobs[move.from] ?: return this
        mutableCobs[move.from] = cob.copy(isUpgraded = true)
        // Promotion resets the 50-move clock (§7.2) but keeps the current turn.
        return GameState(mutableCobs, currentTurn)
    }

    fun getPieceCounts(): PieceCounts {
        val whiteCount = this.cobs.values.count { it.color == WHITE }
        val blackCount = this.cobs.values.count { it.color == BLACK }
        return PieceCounts(whiteCount, blackCount)
    }

    /**
     * Returns true if [cob] at [vertex] is dead according to the patent rules.
     *
     * A cob is dead if:
     * 1. **Primary dead**: it sits on one of the opponent's two outermost home-base vertices
     *    (D3/D4 for white, D1/D2 for black). These are only reachable via capture, never by
     *    forward movement, so no forward advance is possible from them.
     * 2. **Dead by proxy**: every forward-adjacent vertex is occupied by a dead cob of the
     *    same color. Blocked by an enemy piece, a rok of any color, or a live friendly cob
     *    does NOT make this cob dead — any of those blockers can move away.
     *
     * Roks are never dead.
     * Recursion is safe because forward moves are acyclic: proxy chains always terminate
     * at primary dead pieces.
     */
    fun isDeadCob(vertex: Vertex, cob: Cob): Boolean {
        if (cob.isUpgraded) return false
        if (vertex in deadVertices[cob.color].orEmpty()) return true

        val forwardNeighbors = adjacencyMap[vertex]?.filter { to ->
            isForwardMove(cob.color, Move(vertex to to))
        } ?: emptyList()

        // A cob with no forward neighbors is not dead by proxy — it just cannot move,
        // which the patent explicitly says does not make a piece dead by itself.
        if (forwardNeighbors.isEmpty()) return false

        return forwardNeighbors.all { to ->
            val blocker = cobs[to]
            blocker != null &&
                    !blocker.isUpgraded &&
                    blocker.color == cob.color &&
                    isDeadCob(to, blocker)
        }
    }

    /**
     * Returns all dead cobs belonging to the current turn player, paired with their vertex.
     * Used to determine eligibility for forced promotion (Fase 3).
     */
    fun getDeadCobsForCurrentTurn(): List<Pair<Vertex, Cob>> =
        cobs.entries
            .filter { (vertex, cob) -> cob.color == currentTurn && isDeadCob(vertex, cob) }
            .map { it.toPair() }

    fun detectCaptures(move: Move, newState: GameState): List<Pair<Vertex, Cob>> =
        (adjacencyMap[move.to] ?: emptyList()).mapNotNull { vertex ->
            val oldCob = cobs[vertex] ?: return@mapNotNull null
            val newCob = newState.cobs[vertex] ?: return@mapNotNull null
            (vertex to newCob).takeIf { oldCob.color != newCob.color }
        }

    fun detectUpgrades(newState: GameState): List<Pair<Vertex, Cob>> =
        newState.cobs.entries.mapNotNull { (vertex, newCob) ->
            val oldCob = cobs[vertex]
            val wasUpgraded = when {
                // In-place promotion
                oldCob != null && !oldCob.isUpgraded && newCob.isUpgraded -> true
                // Arrived via forward move onto upgrade vertex
                oldCob == null && newCob.isUpgraded ->
                    cobs.any { (fromVertex, fromCob) ->
                        fromCob.color == newCob.color &&
                                !fromCob.isUpgraded &&
                                newState.cobs[fromVertex] == null
                    }

                else -> false
            }
            (vertex to newCob).takeIf { wasUpgraded }
        }

    fun isEmptyBoard(): Boolean = this.cobs.isEmpty()

    fun isInitialState(playerSide: CobColor): Boolean = this == initialGameState(playerSide)

    fun findClosedRegions(
        to: Vertex,
        color: CobColor,
    ): List<Region> =
        vertexToRegions[to]
            ?.takeIf { it.isNotEmpty() }
            ?.filter { region ->
                region.vertices.all { vertex ->
                    cobs[vertex]?.color == color
                }
            } ?: emptyList()

    /**
     * Returns valid destination vertices for [cob] at [from] for UI highlighting.
     *
     * For normal/rok moves: forward (or any-direction for roks) empty adjacent vertices,
     * plus home-base capture targets.
     *
     * For forced promotion candidates: includes [from] itself, signalling to the UI that
     * the user should tap this vertex again to confirm the in-place promotion.
     */
    fun getValidVertex(
        from: Vertex,
        cob: Cob,
    ): List<Vertex> {
        val forwardMoves =
            adjacencyMap[from]?.filter { to ->
                !this.cobs.containsKey(to) &&
                        (cob.isUpgraded || isForwardMove(cob.color, Move(from to to)))
            } ?: emptyList()

        val homeBaseMoveTargets = getHomeBaseMoves(from, cob).map { it.to }
        val base = (forwardMoves + homeBaseMoveTargets).distinct()

        // If this piece is a forced promotion candidate, include its own vertex so the
        // highlight system shows "tap here to promote".
        val canPromote = getForcedPromotions().any { it.from == from }
        return if (canPromote) base + from else base
    }

    /**
     * Returns all legally playable non-forward moves from [from] for [cob] when it sits on
     * its own home base.
     *
     * A home-base move is valid only if it results in at least one capture. Two capture
     * mechanisms are evaluated:
     *
     * **Normal pre-adjacency captures**: an opponent's piece adjacent to the destination
     * that was NOT adjacent to the origin is captured.
     */
    fun getHomeBaseMoves(
        from: Vertex,
        cob: Cob,
    ): List<Move> {
        if (cob.isUpgraded) return emptyList()

        return getHomeBaseNonForwardMoves(cob.color, from).filter { move ->
            // Destination must be empty
            if (this.cobs[move.to] != null) return@filter false

            val originAdjacents = adjacencyMap[move.from]?.toSet() ?: emptySet()

            // Valid only if the move produces at least one capture (pre-adjacency rule)
            adjacencyMap[move.to]?.any { adj ->
                adj !in originAdjacents && this.cobs[adj]?.color == cob.color.opponent
            } ?: false
        }
    }

    fun getPossiblesVertexForCob(
        cob: Cob,
        vertex: Vertex,
    ): List<Vertex> =
        adjacencyMap[vertex]?.filter { to ->
            !cobs.containsKey(to) &&
                    (cob.isUpgraded || isForwardMove(cob.color, Move(vertex to to)))
        } ?: emptyList()

    /**
     * Applies [move] to produce the next game state.
     *
     * Capture logic by move type:
     * - **All other moves**: flips opponent pieces adjacent to the destination that were NOT
     *   adjacent to the origin (pre-adjacency rule). This covers both normal forward moves
     *   and home-base non-forward captures.
     */
    fun applyMove(move: Move): GameState {
        if (move.isPromotion()) return applyPromotion(move)

        val mutableCobs = this.cobs.toMutableMap()
        val movedCob = mutableCobs.remove(move.from) ?: return this

        val placedCob = movedCob.upgradeIfInEnemyBase(move.to)
        mutableCobs[move.to] = placedCob

        // Contar piezas adversarias antes de flipAdjacentCobs para detectar capturas.
        val opponentColor = placedCob.color.opponent
        val opponentsBefore = mutableCobs.values.count { it.color == opponentColor }
        placedCob.color.flipAdjacentCobs(mutableCobs, move.to, move.from)
        val hadCaptures = mutableCobs.values.count { it.color == opponentColor } < opponentsBefore

        // Resetear si: (a) pieza no promovida (Cob), o (b) Rok que capturó piezas.
        // Solo incrementar si el Rok se mueve sin realizar ninguna captura.
        val newClock = if (movedCob.isUpgraded && !hadCaptures) halfMoveClock + 1 else 0

        return GameState(mutableCobs, this.currentTurn.opponent, newClock)
    }

    /**
     * Función que, a partir de un GameState, genera una notación similar a FEN (Forsyth-Edwards Notation) en ajedrez.
     * Usar [POS_DELIMITER_CHAR] y [TURN_DELIMITER_CHAR] por futuros cambios.
     *
     * Cada posición ocupada en el tablero se escribe seguida por el color y el estado de la pieza:
     *    Cob: w/b - Rok: W/B,
     * y al final el indicador del turno actual (w/b) separado por un espacio.
     *
     * Ejemplo:
     *
     * ```B1B/B2B/B4B/B5w/C1B/C2B/C8B/C9b w```
     */
    fun toPositionNotation(): String =
        buildString {
            cobs.entries
                .sortedBy { it.key.name }
                .forEach { (position, piece) ->
                    val colorChar =
                        if (piece.isUpgraded) piece.color.letter.uppercaseChar() else piece.color.letter
                    append("${position.name}$colorChar$POS_DELIMITER_CHAR")
                }

            if (cobs.isNotEmpty()) {
                setLength(length - 1)
            }

            append("$TURN_DELIMITER_CHAR${currentTurn.letter}")
        }

    fun toMatchDto(
        moves: List<Move> = listOf(),
        result: MatchResult = UNDEFINED,
    ): MatchDto =
        MatchDto(
            header = createPGNHeader(this),
            game =
                GameDto(
                    boardPosition = this.toPositionNotation(),
                    matchResult = result,
                    moveHistory = moves,
                ),
        )

    fun getCobAtVertex(from: Vertex): Cob? = this.cobs[from]

    companion object {
        // Zobrist hashing
        // 23 vertices x 4 piece types + 1 side-to-move key = 93 Long values.
        // Piece type index: WHITE_COB=0, WHITE_ROK=1, BLACK_COB=2, BLACK_ROK=3
        // Vertex index: position in GameBoard.vertices (0..22)
        //
        // Las claves se derivan con SplitMix64 desde una semilla fija: la secuencia
        // es idéntica en todas las plataformas, procesos y versiones de Kotlin.
        // Los hashes viajan dentro de positionHistory persistido (recovery de
        // sesiones del servidor en Redis), por lo que deben ser estables entre
        // reinicios del proceso.
        private const val ZOBRIST_SEED = 0x544152415449L // "TARATI" en ASCII

        /** SplitMix64: flujo de claves pseudo-aleatorias determinista para las tablas Zobrist. */
        private fun zobristKey(index: Int): Long {
            var z = ZOBRIST_SEED + (index + 1L) * 0x9E3779B97F4A7C15uL.toLong()
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return z xor (z ushr 31)
        }

        private val ZOBRIST_PIECES: Array<LongArray> =
            Array(vertices.size) { vi -> LongArray(4) { pi -> zobristKey(vi * 4 + pi) } }

        private val ZOBRIST_SIDE_TO_MOVE: Long = zobristKey(vertices.size * 4)

        /** Stable vertex-to-index mapping for Zobrist key lookup (O(1)). */
        private val VERTEX_INDEX: Map<Vertex, Int> =
            vertices.mapIndexed { i, v -> v to i }.toMap()

        const val POS_DELIMITER_CHAR: Char = '/'
        const val TURN_DELIMITER_CHAR: Char = ' '

        fun initialGameState(currentTurn: CobColor = WHITE): GameState {
            val map =
                mapOf(
                    GameBoard.C1 to Cob(WHITE),
                    GameBoard.C2 to Cob(WHITE),
                    GameBoard.D1 to Cob(WHITE),
                    GameBoard.D2 to Cob(WHITE),
                    GameBoard.C7 to Cob(BLACK),
                    GameBoard.C8 to Cob(BLACK),
                    GameBoard.D3 to Cob(BLACK),
                    GameBoard.D4 to Cob(BLACK),
                )
            return GameState(cobs = map, currentTurn = currentTurn)
        }

        fun createGameState(block: GameStateBuilder.() -> Unit): GameState = GameStateBuilder().apply(block).build()

        fun cleanGameState(currentTurn: CobColor = WHITE): GameState =
            GameState(cobs = mapOf(), currentTurn = currentTurn)

        fun parseBoardNotation(notation: String): GameState {
            val cobs = mutableMapOf<Vertex, Cob>()

            val parts = notation.split(TURN_DELIMITER_CHAR)
            if (parts.size != 2) {
                throw IllegalArgumentException("Formato de notación inválido: debe contener piezas y turno separados por espacio")
            }

            val piecesPart = parts[0]
            val turnPart = parts[1]

            if (piecesPart.isNotEmpty()) {
                val pieceEntries = piecesPart.split(POS_DELIMITER_CHAR)
                for (entry in pieceEntries) {
                    if (entry.length < 3) continue

                    // Extraer la posición (todos los caracteres excepto el último)
                    val positionStr = entry.dropLast(1).uppercase()
                    val colorChar = entry.last()

                    val vertex =
                        vertices.find { it.name == positionStr }
                            ?: throw IllegalArgumentException("Posición inválida: $positionStr")

                    val color = CobColor.fromLetter(colorChar.lowercaseChar())
                        ?: throw IllegalArgumentException("Color inválido: $colorChar")

                    val isUpgraded = colorChar.isUpperCase()

                    cobs[vertex] = Cob(color, isUpgraded)
                }
            }

            val currentTurn =
                when (turnPart.lowercase()) {
                    "white", "w" -> WHITE
                    "black", "b" -> BLACK
                    else -> throw IllegalArgumentException("Turno inválido: $turnPart")
                }

            return GameState(cobs, currentTurn)
        }

        /**
         * Reconstruye el [GameState] después de aplicar los primeros
         * [moveIndex]+1 movimientos sobre [initialState].
         *
         * @param initialState Estado del tablero antes del primer movimiento.
         *   Por defecto [initialGameState] para compatibilidad con partidas
         *   que no persisten la posición inicial.
         */
        fun getBoardStateAtMove(
            moveHistory: List<Move>,
            moveIndex: Int? = null,
            initialState: GameState = initialGameState(),
        ): GameState {
            val moveIndex = moveIndex ?: moveHistory.lastIndex
            var currentState = initialState

            val movesToApply = moveHistory.take(moveIndex + 1)
            movesToApply.forEach { move ->
                currentState = currentState.applyMove(move)
            }

            return currentState
        }
    }
}