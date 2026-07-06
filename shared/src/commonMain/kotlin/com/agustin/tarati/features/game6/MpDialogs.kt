package com.agustin.tarati.features.game6

import androidx.compose.runtime.Composable
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpCutReason
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_player_n
import com.agustin.tarati.shared.generated.resources.game6_result_cut_repetition
import com.agustin.tarati.shared.generated.resources.game6_result_cut_stall
import com.agustin.tarati.shared.generated.resources.game6_result_last_standing
import com.agustin.tarati.shared.generated.resources.game6_result_majority
import com.agustin.tarati.shared.generated.resources.game6_result_shared

/**
 * Textos del juego multijugador local. Reutilizan los diálogos genéricos de Tarati (`NewGameDialog`,
 * `GameOverDialog`) vía `UIMessageBus`/`AlertHost`; aquí vive solo el mensaje de resultado N-color.
 */

/** Nombres localizados de los 6 jugadores por letra (p. ej. "Jugador C"), resueltos fuera de lambdas. */
@Composable
fun mpPlayerNames(): Map<PlayerColor, String> =
    PlayerColor.entries.associateWith { localizedString(Res.string.game6_player_n, it.letter.toString()) }

/**
 * Mensaje localizado del desenlace de la partida (ganador/es + motivo + causa de corte si la hubo).
 * [names] permite usar los **nombres reales** de los jugadores (online) en vez del genérico
 * "Jugador A/B…"; por defecto usa los genéricos ([mpPlayerNames]).
 */
@Composable
fun mpResultMessage(result: MpResult, names: Map<PlayerColor, String> = mpPlayerNames()): String {
    val winners = result.winners.joinToString(", ") { names[it] ?: it.letter.toString() }
    val base = when (result.reason) {
        MpEndReason.LAST_STANDING -> localizedString(Res.string.game6_result_last_standing, winners)
        MpEndReason.PIECE_MAJORITY -> localizedString(Res.string.game6_result_majority, winners)
        MpEndReason.SHARED -> localizedString(Res.string.game6_result_shared, winners)
    }
    // Si la partida se cortó por estancamiento (§2.6), se anexa la causa.
    val cutSuffix = when (result.cut) {
        MpCutReason.REPETITION -> localizedString(Res.string.game6_result_cut_repetition)
        MpCutReason.NO_CAPTURE_LIMIT -> localizedString(Res.string.game6_result_cut_stall)
        null -> null
    }
    return if (cutSuffix != null) "$base — $cutSuffix" else base
}
