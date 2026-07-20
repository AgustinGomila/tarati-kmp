package com.agustin.tarati.tools.openingminer

import com.agustin.tarati.core.domain.game.play.GameResult

/**
 * Una fila del corpus exportado desde Postgres (`games` + rating/flags de ambos jugadores).
 *
 * Es la unidad de entrada del minero: cada [GameRecord] es una partida terminada de la que se
 * reproducen las primeras jugadas para acumular estadística de aperturas.
 *
 * @property pgn PGN plano tal como lo persiste el servidor: tokens `from-to` separados por espacio
 *   (las promociones aparecen como `X-X`). Ver `PostGameProcessor.buildPgn`.
 * @property result Resultado de la partida (`white_wins` / `black_wins` / `draw`).
 * @property endMethod Método de fin (`mit` / `stalemit` / `triple` / `timeout` / `resignation`).
 * @property rated Si la partida fue rated.
 * @property timeControl Categoría de control de tiempo (`bullet` / `blitz` / `rapid` / `classical`).
 * @property whiteRating Rating de blancas al momento de la partida.
 * @property blackRating Rating de negras al momento de la partida.
 * @property whiteIsBot Si blancas era un bot.
 * @property blackIsBot Si negras era un bot.
 */
data class GameRecord(
    val pgn: String,
    val result: GameResult,
    val endMethod: String,
    val rated: Boolean,
    val timeControl: String,
    val whiteRating: Int,
    val blackRating: Int,
    val whiteIsBot: Boolean,
    val blackIsBot: Boolean,
)

/**
 * Resultado de una jugada visto **desde el bando que movía** en esa posición.
 * Es lo que se acumula por (posición, jugada): una jugada "buena" es la que gana más seguido
 * para quien la juega.
 */
enum class PlyOutcome { WIN, LOSS, DRAW }
