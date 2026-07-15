package com.agustin.tarati.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TaratiLogoPreview() {
    TaratiTheme {
        TaratiLogo(size = 160.dp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun TaratiLogoDarkPreview() {
    TaratiTheme(darkTheme = true) {
        TaratiLogo(size = 160.dp)
    }
}
