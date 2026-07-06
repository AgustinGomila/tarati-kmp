package com.agustin.tarati.ui.components.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Chrome (layout) compartido del panel lateral: la superficie, el ancho fijo, el scroll y el
 * espaciado. Recibe por **slots** las secciones que varían según el modo de juego, de modo que
 * Tarati (single) y el multijugador construyen su sidebar con el mismo contenedor sin acoplarse
 * entre sí.
 *
 * Orden de las secciones (idéntico a la distribución histórica del sidebar de Tarati):
 * header → controls → playerConfig → moveHistory (ocupa el espacio disponible) → footer.
 */
@Composable
fun SidebarShell(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    playerConfig: @Composable () -> Unit,
    moveHistory: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight(),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()
            controls()
            playerConfig()
            moveHistory()
            footer()
        }
    }
}
