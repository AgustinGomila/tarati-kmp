package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agustin.tarati.ui.theme.AppBackground

/**
 * Fondo decorativo del juego multijugador: mismo gradiente, grano y resplandor que
 * [com.agustin.tarati.ui.theme.TaratiBackground], pero con siluetas traslúcidas del **tablero 25**
 * en lugar del de Tarati.
 */
@Composable
fun MultiplayerBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AppBackground(
        drawSilhouette = { colors, sz -> drawBoard25Silhouette(colors, sz) },
        modifier = modifier,
        content = content,
    )
}
