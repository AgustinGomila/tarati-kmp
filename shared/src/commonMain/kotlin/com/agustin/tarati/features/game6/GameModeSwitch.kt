package com.agustin.tarati.features.game6

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_mode_multi
import com.agustin.tarati.shared.generated.resources.game6_mode_single

/**
 * Interruptor segmentado Single / Multi que vive en la fila de controles del sidebar. Lee el
 * [GameModeController] del host vía [LocalGameModeController]; si no hay host (local `null`), no
 * se dibuja (previews, modo online). El [current] indica qué segmento resaltar en cada pantalla.
 *
 * Cada segmento muestra el **ícono del modo** (hexágono con peones: 2 en single, 6 de colores en
 * multi) con su **título** debajo. Los íconos son multicolor, por eso se dibujan con [Image] (sin
 * tinte), no con `Icon`.
 *
 * Cambiar de modo no descarta nada: ambas partidas (single y multi) viven en ViewModels del host y
 * sobreviven el cambio → tocar un segmento cambia el modo directamente, sin confirmación.
 */
@Composable
fun GameModeSwitch(
    current: GameMode,
    modifier: Modifier = Modifier,
) {
    val controller = LocalGameModeController.current ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        ModeSegment(
            mode = GameMode.SINGLE,
            label = localizedString(Res.string.game6_mode_single),
            selected = current == GameMode.SINGLE,
            onClick = { controller.set(GameMode.SINGLE) },
            modifier = Modifier.weight(1f),
        )
        ModeSegment(
            mode = GameMode.MULTI,
            label = localizedString(Res.string.game6_mode_multi),
            selected = current == GameMode.MULTI,
            onClick = { controller.set(GameMode.MULTI) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSegment(
    mode: GameMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Ícono en Canvas: las líneas/contornos usan [fg] → se adaptan al tema y a la selección.
        GameModeIcon(
            mode = mode,
            lineColor = fg,
            modifier = Modifier.height(26.dp).fillMaxWidth(),
        )
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
        )
    }
}
