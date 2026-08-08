package com.agustin.tarati.features.analysis.previews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.features.analysis.AnalysisPanel
import com.agustin.tarati.ui.theme.TaratiTheme

/** Posición asimétrica de muestra (unas jugadas desde el inicio) para el preview. */
private val sampleState = run {
    var s = initialGameState()
    repeat(4) { s = s.applyMove(s.allMovesForTurn().first()) }
    s
}

@Preview(name = "AnalysisPanel", showBackground = true, widthDp = 320, heightDp = 520)
@Composable
private fun AnalysisPanelPreview() {
    TaratiTheme {
        Surface {
            AnalysisPanel(
                gameState = sampleState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
