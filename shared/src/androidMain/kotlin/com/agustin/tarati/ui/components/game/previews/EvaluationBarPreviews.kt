package com.agustin.tarati.ui.components.game.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agustin.tarati.ui.components.game.EvaluationBar
import com.agustin.tarati.ui.theme.TaratiTheme

/**
 * Previews de [EvaluationBar] a lo largo del rango: neutro, ventaja de Blancas,
 * ventaja de Negras y posición decisiva ("Mit").
 */
@Preview(name = "Eval bar — rango", showBackground = true, widthDp = 180, heightDp = 260)
@Composable
private fun EvaluationBarPreview() {
    TaratiTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                listOf(
                    0.5f to "50%",
                    0.72f to "72%",
                    0.15f to "15%",
                    0.99f to "Mit",
                ).forEach { (prob, label) ->
                    EvaluationBar(
                        winProbWhite = prob,
                        label = label,
                        modifier = Modifier
                            .width(16.dp)
                            .height(220.dp),
                    )
                }
            }
        }
    }
}
