package com.agustin.tarati.features.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.analysis_title
import com.agustin.tarati.ui.layout.CompanionPanelHeader

/**
 * Tarjeta flotante con el [AnalysisPanel], para el overlay colapsable de portrait
 * (equivalente al panel de historial). Cabecera con título + cierre reutilizando
 * [CompanionPanelHeader]; el contenido se acota con [maxHeight] y hace scroll.
 */
@Composable
fun AnalysisCard(
    gameState: GameState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 340.dp,
    onOpenFullAnalysis: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            CompanionPanelHeader(
                title = localizedString(Res.string.analysis_title),
                onClose = onClose,
            )
            AnalysisPanel(
                gameState = gameState,
                modifier = Modifier.heightIn(max = maxHeight),
                onOpenFullAnalysis = onOpenFullAnalysis,
            )
        }
    }
}
