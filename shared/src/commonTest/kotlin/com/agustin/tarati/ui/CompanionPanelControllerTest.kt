package com.agustin.tarati.ui

import com.agustin.tarati.ui.layout.CompanionPanelController
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Lobby
import com.agustin.tarati.ui.layout.CompanionPanelDestination.MpGameDetail
import com.agustin.tarati.ui.layout.CompanionPanelDestination.MpLobby
import com.agustin.tarati.ui.layout.CompanionPanelDestination.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests de [CompanionPanelController], en particular [CompanionPanelController.syncLobbyMode]: al
 * cambiar de modo single↔multi con una sub-pantalla abierta, el `back` debe volver al lobby del modo
 * **actual** (no al del anterior), reescribiendo tanto el destino visible como el back-stack.
 */
class CompanionPanelControllerTest {

    @Test
    fun syncLobbyMode_rewritesVisibleLobby() {
        val c = CompanionPanelController()
        c.navigate(MpLobby)
        c.syncLobbyMode(multi = false) // pasa a single
        assertEquals(Lobby, c.destination)

        c.syncLobbyMode(multi = true) // vuelve a multi
        assertEquals(MpLobby, c.destination)
    }

    @Test
    fun syncLobbyMode_rewritesBackStackUnderSubScreen() {
        val c = CompanionPanelController()
        c.navigate(MpLobby)                // destino=MpLobby, stack=[None]
        c.navigate(MpGameDetail("g1"))     // destino=MpGameDetail, stack=[None, MpLobby]

        c.syncLobbyMode(multi = false)     // cambio a single: el MpLobby del stack → Lobby
        assertEquals(MpGameDetail("g1"), c.destination) // la sub-pantalla no se toca

        c.back()
        assertEquals(Lobby, c.destination) // back vuelve al lobby del modo actual (single)
    }

    @Test
    fun syncLobbyMode_leavesSharedDestinationsUntouched() {
        val c = CompanionPanelController()
        c.navigate(MpLobby)
        c.navigate(Profile("u1"))          // destino compartido (no es un lobby)

        c.syncLobbyMode(multi = false)
        assertEquals(Profile("u1"), c.destination) // el perfil no se reescribe

        c.back()
        assertEquals(Lobby, c.destination) // pero el lobby de abajo sí quedó alineado al modo actual
    }
}
