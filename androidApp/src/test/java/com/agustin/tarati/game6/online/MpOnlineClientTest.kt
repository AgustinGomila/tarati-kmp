@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.game6.online

import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P1
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor.P2
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.network.client.MpOnlineClient
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MpOnlineClient] (M7.1): proyección de `MpServerMessage` a los flows de UI (mesa/partida/
 * errores) y envío de acciones como `ClientMessage.Mp`. Sin WS real: se inyecta el flujo de entrada
 * y una función de envío que captura. El scope del cliente usa un [UnconfinedTestDispatcher] para que
 * el colector procese cada emisión de inmediato (subscripción eager).
 */
class MpOnlineClientTest {

    private fun table(id: String, seats: Int) =
        MpTableDto(id, "host", seats, MpTableStatus.OPEN, (0 until seats).map { MpSeatDto(it) })

    private fun gameStarted(gameId: String) = MpServerMessage.GameStarted(
        gameId = gameId,
        state = MpSetup.initialState(2),
        players = listOf(MpPlayerDto(P1, "me", "Me"), MpPlayerDto(P2, name = "Bot", isBot = true)),
    )

    @Test
    fun tableUpdate_setsCurrentTable(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        incoming.emit(ServerMessage.Mp(MpServerMessage.TableUpdate(table("t1", 4))))

        assertEquals("t1", client.currentTable.value?.id)
        assertEquals(4, client.currentTable.value?.seats?.size)
        scope.cancel()
    }

    @Test
    fun gameStarted_setsGame_andClearsTable(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        incoming.emit(ServerMessage.Mp(MpServerMessage.TableUpdate(table("t1", 2))))
        incoming.emit(ServerMessage.Mp(gameStarted("g1")))

        assertNull(client.currentTable.value)
        assertEquals("g1", client.currentGame.value?.gameId)
        assertEquals(2, client.currentGame.value?.players?.size)
        scope.cancel()
    }

    @Test
    fun stateUpdate_updatesGame(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        incoming.emit(ServerMessage.Mp(gameStarted("g1")))
        incoming.emit(
            ServerMessage.Mp(MpServerMessage.StateUpdate("g1", MpSetup.initialState(2), "D1", "C1", mapOf("B1" to P2))),
        )

        assertEquals("D1", client.currentGame.value?.lastMoveFrom)
        assertEquals(mapOf("B1" to P2), client.currentGame.value?.converted)
        scope.cancel()
    }

    @Test
    fun gameEnded_setsRatingDeltas(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        incoming.emit(ServerMessage.Mp(gameStarted("g1")))
        val result = MpResult(listOf(P1), MpEndReason.LAST_STANDING, mapOf(P1 to 4, P2 to 0))
        incoming.emit(ServerMessage.Mp(MpServerMessage.GameEnded("g1", result, mapOf("me" to 12))))

        assertEquals(mapOf("me" to 12), client.currentGame.value?.ratingDeltas)
        scope.cancel()
    }

    @Test
    fun tableClosed_clearsAndEmits(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)
        val closed = mutableListOf<String>()
        scope.launch { client.tableClosed.collect { closed += it } }

        incoming.emit(ServerMessage.Mp(MpServerMessage.TableUpdate(table("t1", 3))))
        incoming.emit(ServerMessage.Mp(MpServerMessage.TableClosed("t1", "host_left")))

        assertNull(client.currentTable.value)
        assertEquals(listOf("host_left"), closed)
        scope.cancel()
    }

    @Test
    fun error_emitsCode(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)
        val errors = mutableListOf<String>()
        scope.launch { client.errors.collect { errors += it } }

        incoming.emit(ServerMessage.Mp(MpServerMessage.Error("table_full")))

        assertEquals(listOf("table_full"), errors)
        scope.cancel()
    }

    @Test
    fun stateUpdate_serializationRoundTrips() {
        // El servidor codifica con `allowStructuredMapKeys` (por MpGameState.pieces = Map<Vertex,Piece>);
        // el cliente (WebSocketClient) decodifica. Si esto falla, WebSocketClient DESCARTA el StateUpdate
        // en silencio → el tablero queda pegado en el estado inicial (bug reportado).
        val serverJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }
        val clientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

        val initial = MpSetup.initialState(2)
        val after = MpRules.applyMove(initial, MpRules.legalMoves(initial).first())
        val msg: ServerMessage = ServerMessage.Mp(MpServerMessage.StateUpdate("g1", after, "D1", "C1", emptyMap()))

        val encoded = serverJson.encodeToString(ServerMessage.serializer(), msg)
        val decoded = clientJson.decodeFromString(ServerMessage.serializer(), encoded)

        val payload = (decoded as ServerMessage.Mp).payload as MpServerMessage.StateUpdate
        assertEquals(after.moveCount, payload.state.moveCount)
        assertEquals(after.currentSeat.color, payload.state.currentSeat.color)
    }

    @Test
    fun freshMove_gatesReplayOnReentry(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        // Al entrar a la partida (GameStarted con moveCount=0), esa posición ya queda "presentada".
        incoming.emit(ServerMessage.Mp(gameStarted("g1")))
        assertEquals(false, client.isFreshMove(0)) // re-entrar con moveCount=0 → no re-anima/suena

        // Una jugada nueva (moveCount=1) es fresca hasta marcarla; luego re-entrar ya no la repite.
        assertTrue(client.isFreshMove(1))
        client.markMovePresented(1)
        assertEquals(false, client.isFreshMove(1))
        assertTrue(client.isFreshMove(2))
        scope.cancel()
    }

    @Test
    fun freshGameOver_gatesResultAlertOnReentry(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = MpOnlineClient(incoming, {}, scope)

        incoming.emit(ServerMessage.Mp(gameStarted("g1")))
        assertTrue(client.isFreshGameOver("g1"))  // fin aún no presentado
        client.markGameOverPresented("g1")
        assertEquals(false, client.isFreshGameOver("g1")) // re-entrar no repite la alerta
        assertTrue(client.isFreshGameOver("g2"))  // otra partida sí
        scope.cancel()
    }

    @Test
    fun sendActions_produceMpClientMessages(): TestResult = runTest {
        val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sent = mutableListOf<ClientMessage>()
        val client = MpOnlineClient(incoming, { sent += it }, scope)

        client.createTable(4)
        incoming.emit(ServerMessage.Mp(gameStarted("g1")))
        client.makeMove("D1", "C1")

        val payloads = sent.map { (it as ClientMessage.Mp).payload }
        assertTrue(payloads.contains(MpClientMessage.CreateTable(4)))
        assertTrue(payloads.contains(MpClientMessage.MakeMove("g1", "D1", "C1")))
        scope.cancel()
    }
}
