package com.agustin.tarati.services.dialogs


import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.ITCH_IO_URL
import com.agustin.tarati.PLAY_STORE_URL
import com.agustin.tarati.appVersion
import com.agustin.tarati.core.domain.game.pieces.colorNameRes
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameResult.BLACK_WIN
import com.agustin.tarati.core.domain.game.play.GameResult.WHITE_WIN
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.services.localization.LocalAppLanguage
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.url.IUrlLauncher
import com.agustin.tarati.services.url.rateAppAvailable
import com.agustin.tarati.services.url.storeLinksAvailable
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.about_get_google_play
import com.agustin.tarati.shared.generated.resources.about_get_itch
import com.agustin.tarati.shared.generated.resources.about_rate_app
import com.agustin.tarati.shared.generated.resources.about_tarati
import com.agustin.tarati.shared.generated.resources.app_version
import com.agustin.tarati.shared.generated.resources.badge_google_play_en
import com.agustin.tarati.shared.generated.resources.badge_google_play_es
import com.agustin.tarati.shared.generated.resources.badge_itch
import com.agustin.tarati.shared.generated.resources.are_you_sure_you_want_to_start_a_new_game
import com.agustin.tarati.shared.generated.resources.black
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.close
import com.agustin.tarati.shared.generated.resources.continue_
import com.agustin.tarati.shared.generated.resources.credits
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.draw_by_agreement
import com.agustin.tarati.shared.generated.resources.draw_by_undetermined
import com.agustin.tarati.shared.generated.resources.game_over
import com.agustin.tarati.shared.generated.resources.game_over_fifty_moves
import com.agustin.tarati.shared.generated.resources.game_over_stalemit
import com.agustin.tarati.shared.generated.resources.game_over_timeout
import com.agustin.tarati.shared.generated.resources.game_over_triple_repetition
import com.agustin.tarati.shared.generated.resources.game_over_wins
import com.agustin.tarati.shared.generated.resources.game_rules
import com.agustin.tarati.shared.generated.resources.new_game
import com.agustin.tarati.shared.generated.resources.original_concept_george_spencer_brown
import com.agustin.tarati.shared.generated.resources.player_wins
import com.agustin.tarati.shared.generated.resources.players_2_white_vs_black_objective_control_the_board
import com.agustin.tarati.shared.generated.resources.show_tutorial
import com.agustin.tarati.shared.generated.resources.tarati_is_a_strategic_board_game_created_by_george_spencer_brown
import com.agustin.tarati.shared.generated.resources.white
import com.agustin.tarati.shared.generated.resources.wins_by_resignation
import com.agustin.tarati.shared.generated.resources.yes
import com.agustin.tarati.ui.components.DistinctLogo
import com.agustin.tarati.ui.theme.TaratiIcons
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit = {},
    // Nulo → no se muestra el botón "Ver tutorial" (p. ej. en la partida online, donde no aplica).
    onShowTutorial: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        dragHandle = {
            // Drag handle por defecto
            BottomSheetDefaults.DragHandle()
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Header
            AboutHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // Contenido principal con scroll
            AboutContent(onShowTutorial)

            // Actions - Siempre fijas en la parte inferior
            AboutActions(onDismiss)
        }
    }
}

@Composable
private fun AboutHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = TaratiIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = localizedString(Res.string.about_tarati),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = localizedString(Res.string.app_version).replace($$"%1$s", appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AboutContent(onShowTutorial: (() -> Unit)? = null) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = localizedString(Res.string.tarati_is_a_strategic_board_game_created_by_george_spencer_brown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
        )

        // Game Rules Section
        AboutGameRules()

        // Tutorial Button — solo si hay una acción de tutorial para el modo actual.
        if (onShowTutorial != null) {
            AboutTutorial(onShowTutorial)
        }

        // Marca de distinción (Laws of Form) — antesala de los créditos
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DistinctLogo(size = 56.dp, color = MaterialTheme.colorScheme.primary)
        }

        // Credits Section
        AboutCredits()

        // Tiendas de la app nativa (Web: badges Play + itch.io · Android: calificar)
        AboutStoreLinks()
    }
}

/**
 * Enlaces a las tiendas de la app nativa, al pie del modal "Acerca de".
 *
 * - **Web**: badges oficiales de Google Play (localizado EN/ES según el idioma
 *   de la app) e itch.io — puente browser → app nativa.
 * - **Android**: botón "Calificar en Google Play" (abre la ficha propia; sin
 *   enlace a itch.io por la política de *steering* de Play).
 * - **Desktop/iOS**: no emite nada.
 */
@Composable
private fun AboutStoreLinks() {
    if (!storeLinksAvailable() && !rateAppAvailable()) return

    // koinInject requiere un grafo Koin activo; en Compose Preview no lo hay, así que
    // se omite la inyección (los enlaces quedan inertes solo en previews).
    val inPreview = LocalInspectionMode.current
    val urlLauncher = if (inPreview) null else koinInject<IUrlLauncher>()

    if (storeLinksAvailable()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            // Badge oficial de Google Play — artwork localizado según el idioma de la app.
            val playBadge = if (LocalAppLanguage.current == "es") {
                Res.drawable.badge_google_play_es
            } else {
                Res.drawable.badge_google_play_en
            }
            Image(
                painter = painterResource(playBadge),
                contentDescription = localizedString(Res.string.about_get_google_play),
                modifier = Modifier
                    .height(52.dp)
                    .aspectRatio(646f / 250f, matchHeightConstraintsFirst = true)
                    .clickable { urlLauncher?.openUrl(PLAY_STORE_URL) },
            )
            // Badge oficial de itch.io (vector, del press kit).
            Image(
                painter = painterResource(Res.drawable.badge_itch),
                contentDescription = localizedString(Res.string.about_get_itch),
                modifier = Modifier
                    .height(40.dp)
                    .aspectRatio(740f / 228f, matchHeightConstraintsFirst = true)
                    .clickable { urlLauncher?.openUrl(ITCH_IO_URL) },
            )
        }
    }

    if (rateAppAvailable()) {
        FilledTonalButton(
            onClick = { urlLauncher?.openUrl(PLAY_STORE_URL) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = TaratiIcons.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = localizedString(Res.string.about_rate_app),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun AboutActions(onDismiss: () -> Unit = {}) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = localizedString(Res.string.close),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun AboutGameRules() {
    AboutCard(
        title = localizedString(Res.string.game_rules),
        body = localizedString(Res.string.players_2_white_vs_black_objective_control_the_board),
    )
}

@Composable
fun AboutTutorial(onShowTutorial: () -> Unit = {}) {
    FilledTonalButton(
        onClick = onShowTutorial,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = TaratiIcons.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = localizedString(Res.string.show_tutorial),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun AboutCredits() {
    AboutCard(
        title = localizedString(Res.string.credits),
        body = localizedString(Res.string.original_concept_george_spencer_brown),
    )
}

@Composable
fun AboutCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun GameOverDialog(
    gameOverMessage: String,
    onConfirmed: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        title = { LocalizedText(Res.string.game_over) },
        text = { Text(gameOverMessage) },
        confirmButton = {
            Button(
                onClick = onConfirmed,
            ) {
                LocalizedText(Res.string.new_game)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissed,
            ) {
                LocalizedText(Res.string.continue_)
            }
        },
    )
}

@Composable
fun NewGameDialog(
    onConfirmed: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        title = { LocalizedText(Res.string.new_game) },
        text = { LocalizedText(Res.string.are_you_sure_you_want_to_start_a_new_game) },
        confirmButton = {
            Button(
                onClick = onConfirmed,
            ) {
                LocalizedText(Res.string.yes)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissed,
            ) {
                LocalizedText(Res.string.cancel)
            }
        },
    )
}

@Composable
fun buildOnlineGameOverMessage(finished: OnlineGameStatus.Finished): String {
    val winnerName = when (finished.result) {
        WHITE_WIN.key -> localizedString(Res.string.white)
        BLACK_WIN.key -> localizedString(Res.string.black)
        else -> null
    }.orEmpty()
    return when (finished.reason) {
        GameEndReason.MIT.key -> localizedString(Res.string.game_over_wins).replace($$"%1$s", winnerName)
        GameEndReason.STALEMIT.key -> localizedString(Res.string.game_over_stalemit).replace($$"%1$s", winnerName)
        GameEndReason.TRIPLE.key -> localizedString(Res.string.game_over_triple_repetition).replace(
            $$"%1$s",
            winnerName
        )

        GameEndReason.FIFTY_MOVES.key -> localizedString(Res.string.game_over_fifty_moves)
        GameEndReason.TIMEOUT.key -> localizedString(Res.string.game_over_timeout).replace($$"%1$s", winnerName)
        GameEndReason.UNDETERMINED.key -> localizedString(Res.string.draw_by_undetermined)
        GameEndReason.DRAW_AGREEMENT.key -> localizedString(Res.string.draw_by_agreement)
        GameEndReason.RESIGNATION.key -> localizedString(Res.string.wins_by_resignation).replace($$"%1$s", winnerName)
        else -> if (winnerName.isNotEmpty())
            localizedString(Res.string.player_wins).replace($$"%1$s", winnerName)
        else localizedString(Res.string.draw)
    }
}

@Composable
fun buildGameOverMessage(matchState: MatchState): String {
    val winnerColor = matchState.winner
        ?: return localizedString(Res.string.game_over_fifty_moves)
    val winnerName = localizedString(winnerColor.colorNameRes)

    return when (matchState.gameEndReason) {
        GameEndReason.FIFTY_MOVES ->
            localizedString(Res.string.game_over_fifty_moves)

        GameEndReason.TRIPLE ->
            localizedString(Res.string.game_over_triple_repetition).replace($$"%1$s", winnerName)

        GameEndReason.MIT ->
            localizedString(Res.string.game_over_wins).replace($$"%1$s", winnerName)

        GameEndReason.TIMEOUT ->
            localizedString(Res.string.game_over_timeout).replace($$"%1$s", winnerName)

        else ->
            localizedString(Res.string.game_over_stalemit).replace($$"%1$s", winnerName)
    }
}