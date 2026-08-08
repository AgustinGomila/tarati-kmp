package com.agustin.tarati.features.analysis.previews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.analysis.EvalGraph
import com.agustin.tarati.ui.theme.TaratiTheme

@Preview(name = "EvalGraph", showBackground = true, widthDp = 340, heightDp = 180)
@Composable
private fun EvalGraphPreview() {
    TaratiTheme {
        Surface {
            EvalGraph(
                series = listOf(0.5f, 0.56f, 0.47f, 0.68f, 0.6f, 0.31f, 0.38f, 0.82f, 0.9f, 0.74f),
                currentIndex = 5,
                onSelectIndex = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(16.dp),
            )
        }
    }
}
