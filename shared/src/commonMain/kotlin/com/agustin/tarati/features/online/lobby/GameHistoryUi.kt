package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.cobColorByDescription
import com.agustin.tarati.features.online.ui.TimeControlChips
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.moves
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.rating
import com.agustin.tarati.shared.generated.resources.result
import com.agustin.tarati.shared.generated.resources.win
import com.agustin.tarati.ui.components.carditem.GameCardItem
import com.agustin.tarati.ui.components.game.CobColorIndicator

/**
 * Componentes compartidos de los listados de partidas finalizadas
 * (tab "Mis Partidas", feed de seguidos y perfil público).
 */

/**
 * Card de una partida finalizada de [GameHistoryDto]: color jugado, rival, resultado,
 * cambio de rating y metadatos.
 *
 * @param titlePrefix Prefijo opcional del título (p. ej. "Fulano jugó " en el feed).
 */
@Composable
internal fun GameHistoryCard(
    game: GameHistoryDto,
    titlePrefix: String = "",
    onClick: (() -> Unit)? = null,
) {
    val (resultText, _) = gameResultDisplay(game.result)
    val (ratingChangeFmt, ratingChangeColor) = ratingChangeDisplay(game.ratingChange)
    val myColor = cobColorByDescription(game.myColor) ?: CobColor.WHITE

    GameCardItem(
        title = "${titlePrefix}vs ${game.opponentUsername} (${game.opponentRating})",
        subtitle = gameCardSubtitle(game.timeControl.toDisplayString(), game.rated, game.endedAtMs),
        leadingContent = { CobColorIndicator(myColor, size = 28.dp) },
        badge = "$resultText  $ratingChangeFmt",
        badgeColor = ratingChangeColor,
        rows = listOf(
            localizedString(Res.string.result) to resultText,
            localizedString(Res.string.moves) to "${game.moveCount}",
            localizedString(Res.string.rating) to "${game.ratingAfter} ($ratingChangeFmt)",
        ),
        onClick = onClick,
    )
}

/**
 * Fila de filtros de un listado de partidas: control de tiempo, resultado y puntuadas.
 *
 * Los callbacks reciben el nuevo valor ya con la lógica de toggle aplicada
 * (tocar el chip seleccionado lo deselecciona → null).
 */
@Composable
internal fun GameHistoryFilterRow(
    filters: HistoryFilters,
    onTimeControlFilter: (String?) -> Unit,
    onResultFilter: (String?) -> Unit,
    onRatedFilter: (Boolean?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Time control chips
        item {
            TimeControlChips(
                selected = filters.timeControl,
                onSelect = { tc ->
                    onTimeControlFilter(if (filters.timeControl == tc) null else tc)
                },
            )
        }

        // Result chips
        item {
            listOf(
                "win" to localizedString(Res.string.win),
                "loss" to localizedString(Res.string.loss),
                "draw" to localizedString(Res.string.draw),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filters.result == key,
                    onClick = { onResultFilter(if (filters.result == key) null else key) },
                    label = { Text(label) },
                )
            }
        }

        // Rated chip
        item {
            FilterChip(
                selected = filters.rated == true,
                onClick = { onRatedFilter(if (filters.rated == true) null else true) },
                label = { Text(localizedString(Res.string.rated)) },
            )
        }
    }
}
