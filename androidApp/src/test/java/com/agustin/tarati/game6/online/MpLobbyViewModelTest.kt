@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.game6.online

import com.agustin.tarati.features.game6.MpLobbyViewModel
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.network.client.MpOnlineClient
import com.agustin.tarati.network.models.MpLiveGameDto
import com.agustin.tarati.network.models.MpPlayerDto
import com.agustin.tarati.network.models.MpSeatDto
import com.agustin.tarati.network.models.MpTableDto
import com.agustin.tarati.network.models.MpTableStatus
import com.agustin.tarati.network.protocol.ClientMessage
import com.agustin.tarati.network.protocol.MpClientMessage
import com.agustin.tarati.network.protocol.MpServerMessage
import com.agustin.tarati.network.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MpLobbyViewModel] (M7.2): refresco de la lista de mesas (REST inyectado), proyección del
 * estado del [MpOnlineClient] (mesa propia) y delegación de acciones. Scope de test con
 * [UnconfinedTestDispatcher] → los `launch` corren de inmediato.
 */
class MpLobbyViewModelTest {

    private fun table(id: String, seats: Int) =
        MpTableDto(id, "host", seats, MpTableStatus.OPEN, (0 until seats).map { MpSeatDto(it) })

    private fun setup(
        scope: CoroutineScope,
        incoming: MutableSharedFlow<ServerMessage>,
        sent: MutableList<ClientMessage> = mutableListOf(),
        token: String? = "tok",
        tables: List<MpTableDto> = emptyList(),
        liveGames: List<MpLiveGameDto> = emptyList(),
    ): MpLobbyViewModel {
        val client = MpOnlineClient(incoming, { sent += it }, scope)
        return MpLobbyViewModel(
            client = client,
            getToken = { token },
            fetchTables = { Result.success(tables) },
            fetchLiveGames = { Result.success(liveGames) },
            scope = scope,
        )
    }

    @Test
    fun refresh_populatesTables(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val list = listOf(table("t1", 4), table("t2", 2))
        val vm = setup(scope, MutableSharedFlow(extraBufferCapacity = 16), tables = list)

        vm.refresh()

        assertEquals(list, vm.tables.value)
        scope.cancel()
    }

    @Test
    fun refresh_noToken_keepsEmpty(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm =
            setup(scope, MutableSharedFlow(extraBufferCapacity = 16), token = null, tables = listOf(table("t1", 4)))

        vm.refresh()

        assertTrue(vm.tables.value.isEmpty())
        scope.cancel()
    }

    @Test
    fun currentTable_reflectsClient(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val vm = setup(scope, incoming)

        incoming.emit(ServerMessage.Mp(MpServerMessage.TableUpdate(table("t1", 3))))

        assertEquals("t1", vm.currentTable.value?.id)
        scope.cancel()
    }

    @Test
    fun refresh_populatesLiveGames(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val live = listOf(
            MpLiveGameDto(
                gameId = "g1",
                players = listOf(MpPlayerDto(PlayerColor.P1, "u", "Ana"), MpPlayerDto(PlayerColor.P2, name = "Bot", isBot = true)),
                playerCount = 2, moveCount = 3, currentTurn = PlayerColor.P1, positionNotation = "…",
            ),
        )
        val vm = setup(scope, MutableSharedFlow(extraBufferCapacity = 16), liveGames = live)

        vm.refresh()

        assertEquals(live, vm.liveGames.value)
        scope.cancel()
    }

    @Test
    fun actions_delegateToClient(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val sent = mutableListOf<ClientMessage>()
        val vm = setup(scope, incoming, sent)

        vm.createTable(4)
        vm.joinTable("t9")
        vm.spectate("g5")

        val payloads = sent.map { (it as ClientMessage.Mp).payload }
        assertTrue(payloads.contains(MpClientMessage.CreateTable(4)))
        assertTrue(payloads.contains(MpClientMessage.JoinTable("t9")))
        assertTrue(payloads.contains(MpClientMessage.SpectateGame("g5")))
        scope.cancel()
    }
}
