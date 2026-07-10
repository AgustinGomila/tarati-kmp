package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.auth_guest_banner_title
import com.agustin.tarati.shared.generated.resources.auth_guest_description
import com.agustin.tarati.shared.generated.resources.auth_sign_in
import com.agustin.tarati.shared.generated.resources.draw
import com.agustin.tarati.shared.generated.resources.loss
import com.agustin.tarati.shared.generated.resources.win
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
internal fun CenteredLoader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun CenteredMessage(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

/** Tiempo de espera transcurrido desde [sinceMs] (epoch-ms) como "Xs" o "Xm Ys". */
internal fun formatWaiting(sinceMs: Long): String {
    val secs = (Clock.System.now().toEpochMilliseconds() - sinceMs) / 1000
    return if (secs < 60) "${secs}s" else "${secs / 60}m ${secs % 60}s"
}

private val gameDateFormat = LocalDate.Format {
    day(Padding.ZERO)
    char('/')
    monthNumber()
    char('/')
    year()
}

/** Fecha local "dd/MM/yyyy" a partir de un timestamp epoch-ms. */
internal fun formatGameDate(epochMs: Long): String =
    Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .format(gameDateFormat)

/** Verde de "victoria / cambio de rating positivo", consistente en todas las pestañas. */
internal val PositiveGreen = Color(0xFF4CAF50)

/** Texto localizado y color para un resultado de partida ("win" / "loss" / "draw"). */
@Composable
internal fun gameResultDisplay(result: String): Pair<String, Color> = when (result) {
    "win" -> localizedString(Res.string.win) to PositiveGreen
    "loss" -> localizedString(Res.string.loss) to MaterialTheme.colorScheme.error
    else -> localizedString(Res.string.draw) to MaterialTheme.colorScheme.onSurfaceVariant
}

/** Texto ("+N" / "N") y color para un cambio de rating. */
@Composable
internal fun ratingChangeDisplay(change: Int): Pair<String, Color> {
    val text = if (change > 0) "+$change" else "$change"
    val color = when {
        change > 0 -> PositiveGreen
        change < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return text to color
}

// ── Estadísticas de pestaña ─────────────────────────────────────────────────────

/** Una métrica con su ícono y texto ya formateado, para mostrar en [LobbyStatsRow]. */
internal data class StatChip(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String,
)

/**
 * Fila compacta de estadísticas en la cabecera de una pestaña del lobby.
 * Cada [StatChip] se renderiza como ícono + texto en estilo discreto.
 */
@Composable
internal fun LobbyStatsRow(stats: List<StatChip>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEach { stat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    stat.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stat.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Sesión invitado ───────────────────────────────────────────────────────────

@Composable
internal fun GuestSessionBanner(onSignIn: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TaratiIcons.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    localizedString(Res.string.auth_guest_banner_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    localizedString(Res.string.auth_guest_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
            TextButton(onClick = onSignIn) {
                Text(
                    localizedString(Res.string.auth_sign_in),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
