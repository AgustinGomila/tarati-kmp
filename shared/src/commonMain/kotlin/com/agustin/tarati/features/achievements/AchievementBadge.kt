package com.agustin.tarati.features.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.network.models.ServerAchievementDto
import com.agustin.tarati.services.achievements.AchievementsMetadata
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.achievements
import com.agustin.tarati.shared.generated.resources.achievements_count
import com.agustin.tarati.shared.generated.resources.achievements_none_yet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sección de logros para [PublicProfileScreen].
 * Muestra una cuadrícula compacta de insignias de los logros desbloqueados.
 *
 * El encabezado incluye el contador `desbloqueados / total`, y cada insignia
 * revela su nombre y descripción (hint) al tocarla o al pasar el cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAchievementsSection(
    achievements: List<ServerAchievementDto>,
    modifier: Modifier = Modifier,
) {
    val unlocked = achievements.filter { it.unlockedAt != null }
    val total = AchievementsMetadata.all.size

    Text(
        text = "${stringResource(Res.string.achievements)} " +
                stringResource(Res.string.achievements_count, unlocked.size, total),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )

    if (unlocked.isEmpty()) {
        Text(
            text = stringResource(Res.string.achievements_none_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        return
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        unlocked.forEach { dto ->
            val meta = AchievementsMetadata.byId.entries
                .firstOrNull { it.key.id == dto.achievementId }?.value
            if (meta != null) {
                AchievementBadge(meta = meta)
            }
        }
    }
}

/**
 * Insignia de un logro. Muestra el ícono y, al tocarlo (o al pasar el cursor en
 * desktop/web), revela un [RichTooltip] con el nombre del logro y su descripción (hint).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementBadge(
    meta: AchievementsMetadata.Meta,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val title = stringResource(meta.titleRes)
    val hint = stringResource(meta.descRes)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            spacingBetweenTooltipAndAnchor = 4.dp,
        ),
        tooltip = {
            RichTooltip(title = { Text(title) }) { Text(hint) }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(meta.iconRes),
            contentDescription = title,
            modifier = Modifier
                .size(40.dp)
                .clickable { scope.launch { tooltipState.show() } },
        )
    }
}
