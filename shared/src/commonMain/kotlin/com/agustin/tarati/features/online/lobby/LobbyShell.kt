package com.agustin.tarati.features.online.lobby


import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.lobby_coming_soon
import com.agustin.tarati.shared.generated.resources.lobby_not_connected_to_server
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.layout.CompanionPanelHeader
import com.agustin.tarati.ui.layout.DisplayMode
import org.jetbrains.compose.resources.StringResource

/**
 * Especificación de una pestaña del [LobbyShell].
 *
 * @property label   Recurso de texto de la etiqueta (se muestra localizado).
 * @property icon    Ícono de la pestaña.
 * @property enabled Si es `false`, la pestaña sigue siendo visible y clickeable pero muestra el
 *                   placeholder "próximamente" en lugar de [content]. Útil para paridad estructural
 *                   con otro lobby mientras la funcionalidad del tab aún no existe.
 * @property content Contenido de la pestaña cuando está seleccionada y habilitada.
 */
data class LobbyTabSpec(
    val label: StringResource,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val content: @Composable () -> Unit,
)

/**
 * Chrome compartido de los lobbies online (clásico y multijugador).
 *
 * Absorbe todo lo común entre `OnlineLobbyScreen` y `MpLobbyScreen`: el [Scaffold] con
 * [TaratiTopBar] (FullScreen) o [CompanionPanelHeader] (CompanionPanel), el gating de
 * [ConnectionState] (loader / mensaje según el estado), el loader de auto-conexión, el banner de
 * sesión invitado, la `PrimaryScrollableTabRow` con arrastre horizontal y el dispatch del contenido
 * de la pestaña seleccionada.
 *
 * Cada pantalla mantiene su lógica de negocio propia (auto-connect, matchmaking vs. mesas, sheets y
 * diálogos) y solo delega aquí la presentación: construye la lista de [tabs] y las [topBarActions],
 * y observa la conexión. **No** incluye el fondo (`TaratiBackground` / `MultiplayerBackground`): la
 * pantalla envuelve al shell con el que corresponda.
 *
 * @param showOfflineMessage Cuando la conexión está [ConnectionState.Offline], indica si mostrar el
 *   mensaje "no conectado". El lobby clásico lo muestra solo si nunca llegó a estar online; el
 *   multijugador pasa `false` porque en ese caso sale de la pantalla vía `onBack`.
 * @param snackbarHost Slot de snackbar del [Scaffold]. El lobby clásico inyecta su `SnackbarHost`
 *   (errores de matchmaking); el multijugador usa toasts globales y pasa el default vacío.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyShell(
    title: String,
    displayMode: DisplayMode,
    onBack: () -> Unit,
    connectionState: ConnectionState,
    isAutoConnecting: Boolean,
    showOfflineMessage: Boolean,
    showGuestBanner: Boolean,
    onSignIn: () -> Unit,
    tabs: List<LobbyTabSpec>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    val tabScrollState = rememberScrollState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            when (displayMode) {
                DisplayMode.FullScreen -> TaratiTopBar(
                    title = title,
                    navigationType = TopBarNavigationType.Back,
                    onNavigationClick = onBack,
                    actions = topBarActions,
                )

                DisplayMode.CompanionPanel -> CompanionPanelHeader(
                    title = title,
                    onClose = onBack,
                    actions = topBarActions,
                )
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LobbyBody(
                connectionState = connectionState,
                isAutoConnecting = isAutoConnecting,
                showOfflineMessage = showOfflineMessage,
                showGuestBanner = showGuestBanner,
                onSignIn = onSignIn,
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                tabScrollState = tabScrollState,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyBody(
    connectionState: ConnectionState,
    isAutoConnecting: Boolean,
    showOfflineMessage: Boolean,
    showGuestBanner: Boolean,
    onSignIn: () -> Unit,
    tabs: List<LobbyTabSpec>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    tabScrollState: androidx.compose.foundation.ScrollState,
) {
    // Loader mientras el auto-connect inicial está en progreso (evita flashear "no conectado").
    if (isAutoConnecting) {
        CenteredLoader()
        return
    }

    when (connectionState) {
        is ConnectionState.Offline -> {
            // El caller decide si mostrar el mensaje: el lobby clásico lo omite cuando ya estuvo
            // online (en ese caso su LaunchedEffect llama onBack); el MP siempre pasa false.
            if (showOfflineMessage) {
                CenteredMessage(text = localizedString(Res.string.lobby_not_connected_to_server))
            }
            return
        }

        is ConnectionState.Connecting -> {
            CenteredLoader()
            return
        }

        is ConnectionState.Error -> {
            CenteredMessage(text = connectionState.message, color = MaterialTheme.colorScheme.error)
            return
        }

        is ConnectionState.Reconnecting -> {
            CenteredLoader()
            return
        }

        is ConnectionState.Online -> Unit
    }

    if (showGuestBanner) {
        GuestSessionBanner(onSignIn = onSignIn)
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTab,
        scrollState = tabScrollState,
        edgePadding = 0.dp,
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                tabScrollState.dispatchRawDelta(-dragAmount.x)
            }
        },
    ) {
        tabs.forEachIndexed { index, spec ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelectTab(index) },
                text = { Text(localizedString(spec.label)) },
                icon = {
                    Icon(
                        spec.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }

    val current = tabs.getOrNull(selectedTab)
    if (current != null) {
        if (current.enabled) {
            current.content()
        } else {
            CenteredMessage(text = localizedString(Res.string.lobby_coming_soon))
        }
    }
}
