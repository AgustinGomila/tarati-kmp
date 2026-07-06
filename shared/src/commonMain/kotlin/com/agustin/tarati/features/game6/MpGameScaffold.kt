package com.agustin.tarati.features.game6

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.game6_multiplayer
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.layout.LocalScreenLayout
import com.agustin.tarati.ui.layout.ScreenLayout

/**
 * Chrome adaptativo del juego multijugador, **idéntico al de Tarati single** (`GameScreen`):
 *
 * - **Expanded** (web/desktop/tablet ancha): el [sidebar] es permanente a la izquierda del tablero
 *   y **no hay barra superior** (el sidebar ya ofrece título y accesos → gana altura de tablero,
 *   paridad con D15 del single).
 * - **Compact/Medium** (Android/teléfono): el [sidebar] se colapsa en un `ModalNavigationDrawer`
 *   y aparece una [TaratiTopBar] con la **hamburguesa** que lo abre/cierra.
 *
 * El [board] recibe el `Modifier` del contenedor (con `weight`/`fillMaxHeight` en Expanded, o
 * `fillMaxSize` + `innerPadding` del Scaffold en compacto), de modo que ambos modos comparten el
 * mismo contenido de tablero. Lo usan `MpGameScreen` (local) y `MpOnlineGameScreen`.
 *
 * [topBarActions] se renderiza en las `actions` de la barra superior — por lo tanto **solo aparece en
 * compacto/medio** (en Expanded no hay barra), análogo a la búsqueda online del single (D15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpGameScaffold(
    modifier: Modifier = Modifier,
    title: String = localizedString(Res.string.game6_multiplayer),
    topBarActions: @Composable RowScope.() -> Unit = {},
    sidebar: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
) {
    val screenLayout = LocalScreenLayout.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    MultiplayerBackground(modifier = modifier.fillMaxSize()) {
        if (screenLayout == ScreenLayout.Expanded) {
            // Pantalla ancha: sidebar permanente a la izquierda del tablero, sin barra superior.
            Row(modifier = Modifier.fillMaxSize()) {
                sidebar()
                board(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            // Pantalla estrecha/media: sidebar como drawer modal + barra superior con hamburguesa
            // (mismo comportamiento que el juego single).
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { sidebar() },
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TaratiTopBar(
                            drawerState = drawerState,
                            title = title,
                            navigationType = TopBarNavigationType.Menu,
                            actions = topBarActions,
                        )
                    },
                ) { innerPadding ->
                    board(Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }
}
