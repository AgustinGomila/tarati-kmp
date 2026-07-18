package com.agustin.tarati.features.online.lobby


import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.database.dto.PGNHeader
import com.agustin.tarati.core.domain.game.play.GameResult
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.MatchResult
import com.agustin.tarati.core.domain.game.play.MatchResult.BLACK_WON
import com.agustin.tarati.core.domain.game.play.MatchResult.UNDEFINED
import com.agustin.tarati.core.domain.game.play.MatchResult.WHITE_WON
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.getValue
import com.agustin.tarati.network.models.Game
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Convierte un [Game] del servidor en un [MatchDto] para su visualización
 * en [GameDetailsScreen].
 *
 * Los movimientos se extraen del campo PGN almacenado en el servidor.
 * Si el PGN no puede parsearse, [GameDto.moveHistory] queda vacío y la
 * pantalla de detalle muestra la posición inicial en lugar de la final.
 */
fun Game.toMatchDto(): MatchDto {
    val matchResult: MatchResult = when (result) {
        GameResult.WHITE_WIN -> WHITE_WON
        GameResult.BLACK_WIN -> BLACK_WON
        GameResult.DRAW -> UNDEFINED
    }

    val initialPosition = initialGameState().toPositionNotation()

    return MatchDto(
        id = id,
        header = PGNHeader(
            event = "Tarati Online",
            site = "Tarati Online",
            date = endedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
            white = whitePlayer.username,
            black = blackPlayer.username,
            result = matchResult.getValue(),
            timeControl = timeControl.toDisplayString(),
            termination = endMethod.key,
        ),
        game = GameDto(
            initialBoardPosition = initialPosition,
            boardPosition = initialPosition,
            matchResult = matchResult,
            moveHistory = extractMovesFromPgn(pgn),
        ),
        createdAt = endedAt.toEpochMilliseconds(),
    )
}

/**
 * Extrae la lista de movimientos desde un string PGN en formato Tarati.
 *
 * El PGN generado por el servidor tiene la forma:
 * ```
 * [Event "..."]
 * [White "..."]
 * ...
 *
 * 1. B6→B1 C3→D4 2. A1→B2 ...  1-0
 * ```
 *
 * Esta función extrae los tokens de movimiento (`B6→B1`, `C12=R`, etc.)
 * y los convierte al formato comma-separated que [Move.parseMoveHistory] espera.
 *
 * Retorna lista vacía si el PGN está vacío o no puede parsearse.
 */
private fun extractMovesFromPgn(pgn: String): List<Move> {
    if (pgn.isBlank()) return emptyList()
    return try {
        // Eliminar líneas de cabecera (empiezan con "[") y líneas vacías
        val moveSection = pgn.lines()
            .dropWhile { it.startsWith("[") || it.isBlank() }
            .joinToString(" ")
            .trim()

        // Extraer tokens que son movimientos: comienzan con una letra y contienen "-" o "→" (legacy)
        // o terminan en el sufijo de promoción ("=R")
        val moveTokens = moveSection.split(Regex("\\s+"))
            .filter { token ->
                token.first().isLetter() &&
                        (token.contains(Move.MOVE_SEPARATOR) ||
                                token.contains(Move.LEGACY_SEPARATOR) ||
                                token.endsWith(Move.PROMOTION_SUFFIX))
            }

        if (moveTokens.isEmpty()) return emptyList()

        // Normalizar legacy "→" → "-"; las promociones "C3=R" las entiende parseMoveHistory.
        val normalized = moveTokens.joinToString(",") { token ->
            token.replace(Move.LEGACY_SEPARATOR, Move.MOVE_SEPARATOR)
        }

        Move.parseMoveHistory(normalized)
    } catch (_: Exception) {
        emptyList()
    }
}
