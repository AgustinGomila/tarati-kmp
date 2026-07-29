package com.agustin.tarati.features.online.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.ui.components.SupporterBadge
import com.agustin.tarati.ui.components.supporterNameColor
import com.agustin.tarati.ui.theme.TaratiIcons

/** Colores oro/plata/bronce del podio, compartidos por las tablas de clasificación (single y MP). */
internal val leaderboardRankColors = mapOf(
    1 to Color(0xFFFFD700),
    2 to Color(0xFFC0C0C0),
    3 to Color(0xFFCD7F32),
)

/**
 * Fila común de las tablas de clasificación (single por time control y multijugador). Toma primitivos
 * para no acoplarse a un DTO concreto; cada pantalla arma su propia [statsLine] (p. ej. "3W 1D 2L" para
 * single, "3W 1S 2L" para MP). Muestra rank con color de podio, nombre (con flair de supporter), país,
 * rating y la línea de stats.
 */
@Composable
fun LeaderboardRow(
    rank: Int,
    name: String,
    country: String?,
    rating: Int,
    statsLine: String,
    isSupporter: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = leaderboardRankColors[rank] ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSupporter) supporterNameColor() else Color.Unspecified,
                    )
                    if (isSupporter) SupporterBadge(size = 14.dp)
                }
                if (!country.isNullOrBlank()) {
                    Text(
                        text = country,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$rating",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = TaratiIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
