package com.agustin.tarati.ui.layout

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ── Display mode ──────────────────────────────────────────────────────────────

/** Indica si una pantalla se renderiza a pantalla completa o embebida en el panel lateral. */
enum class DisplayMode { FullScreen, CompanionPanel }

// ── Destinations ─────────────────────────────────────────────────────────────

/** Destinos posibles del panel lateral en layouts [ScreenLayout.Expanded]. */
sealed interface CompanionPanelDestination {
    data object None : CompanionPanelDestination
    data object Lobby : CompanionPanelDestination
    data object MpLobby : CompanionPanelDestination
    data object Settings : CompanionPanelDestination
    data object OnlineSettings : CompanionPanelDestination
    data object Supporter : CompanionPanelDestination
    data object Store : CompanionPanelDestination
    data object Library : CompanionPanelDestination
    data object Leaderboard : CompanionPanelDestination
    data class Profile(val userId: String) : CompanionPanelDestination
    data class GameDetails(val gameId: String) : CompanionPanelDestination
    data class TournamentDetail(val tournamentId: String) : CompanionPanelDestination
    data object Achievements : CompanionPanelDestination
}

// ── Controller ────────────────────────────────────────────────────────────────

/**
 * Controla la navegación dentro del panel lateral.
 *
 * Creado una sola vez en [AppContent] y propagado vía [LocalCompanionPanelController].
 * Implementa un back-stack completo: [navigate] apila el destino actual; [back] vuelve
 * al destino anterior; [close] vacía la pila.
 *
 * Ejemplo: Lobby → Leaderboard → Profile → back() → Leaderboard → back() → Lobby.
 *
 * @Stable: [destination] usa mutableStateOf — Compose trackea cada cambio,
 * por lo que se cumple el contrato de estabilidad.
 */
@Stable
class CompanionPanelController {
    var destination: CompanionPanelDestination by mutableStateOf(CompanionPanelDestination.None)
        private set

    private val backStack = ArrayDeque<CompanionPanelDestination>()

    val isOpen: Boolean get() = destination !is CompanionPanelDestination.None

    fun navigate(dest: CompanionPanelDestination) {
        backStack.addLast(destination)
        destination = dest
    }

    /**
     * Alterna el panel para [dest]: si ese mismo destino ya es el que está abierto, lo cierra; si no,
     * navega a él. Para los botones de entrada (Settings/Logros/Online/Biblioteca/…) que abren su
     * pestaña a la derecha y deben cerrarla al volver a tocarlos.
     */
    fun toggle(dest: CompanionPanelDestination) {
        if (destination == dest) close() else navigate(dest)
    }

    fun back() {
        destination = backStack.removeLastOrNull() ?: CompanionPanelDestination.None
    }

    fun close() {
        backStack.clear()
        destination = CompanionPanelDestination.None
    }
}

val LocalCompanionPanelController: ProvidableCompositionLocal<CompanionPanelController> =
    compositionLocalOf { CompanionPanelController() }
