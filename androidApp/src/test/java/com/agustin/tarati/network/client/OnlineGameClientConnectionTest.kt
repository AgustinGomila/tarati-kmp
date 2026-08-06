package com.agustin.tarati.network.client

import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.description
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.features.online.game.ServerErrorEvent
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.network.protocol.PlayerInfo
import com.agustin.tarati.network.protocol.ServerMessage
import com.agustin.tarati.network.protocol.TimeControlInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests unitarios de [OnlineGameClient] para desconexión del oponente (10.1)
 * y errores del servidor (10.3).
 *
 * ## Scheduler compartido
 * Se usa un único [UnconfinedTestDispatcher] tanto para el scope del cliente como
 * para [runTest], de modo que los emisores y colectores comparten el mismo scheduler.
 * Esto garantiza que `handleServerMessage` corre de forma eager en el mismo hilo
 * cuando se emite a [fakeMessages], evitando deadlocks entre schedulers distintos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineGameClientConnectionTest {

    // Dispatcher compartido: OnlineGameClient y runTest usan el mismo scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    private val clientScope = CoroutineScope(dispatcher)

    private val fakeMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private lateinit var mockWsClient: TaratiWebSocketClient
    private lateinit var client: OnlineGameClient

    private val testTC = TimeControlInfo(initial = 180, increment = 2, label = "3+2 Blitz")
    private val opponent = PlayerInfo(userId = "opp-1", username = "opponent", rating = 1500)
    private val gameId = "game-test-001"

    @Before
    fun setup() {
        mockWsClient = mockk(relaxed = true) {
            every { messages } returns fakeMessages
            every { connectionState } returns MutableStateFlow(
                TaratiWebSocketClient.ConnectionState.Disconnected
            )
        }
        client = OnlineGameClient(wsClient = mockWsClient, scope = clientScope)
    }

    @After
    fun tearDown() {
        clientScope.cancel()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Emite MatchFound + GameStarted para dejar _currentGame en InProgress. */
    private suspend fun startGame() {
        fakeMessages.emit(
            ServerMessage.MatchFound(
                gameId = gameId,
                opponentInfo = opponent,
                yourColor = CobColor.WHITE.description,
                timeControl = testTC,
            )
        )
        fakeMessages.emit(
            ServerMessage.GameStarted(
                gameId = gameId,
                initialState = GameState.initialGameState(),
            )
        )
    }

    // ── Tests 10.1 — OpponentDisconnected / OpponentReconnected ──────────────

    @Test
    fun `game status is InProgress after GameStarted`(): TestResult = runTest(dispatcher) {
        startGame()
        assertEquals(OnlineGameStatus.InProgress, client.currentGame.value?.status)
    }

    @Test
    fun `GameStarted seeds clock with initial time control`(): TestResult = runTest(dispatcher) {
        // GameStarted no trae tiempos: el cliente los siembra con el inicial del control de tiempo
        // (180s → 180000ms) para que el visor del reloj aparezca sin esperar el primer GameStateUpdate.
        startGame()
        assertEquals(180_000L, client.currentGame.value?.whiteTimeMs)
        assertEquals(180_000L, client.currentGame.value?.blackTimeMs)
    }

    @Test
    fun `GameStarted leaves clock null for untimed game`(): TestResult = runTest(dispatcher) {
        fakeMessages.emit(
            ServerMessage.MatchFound(
                gameId = gameId,
                opponentInfo = opponent,
                yourColor = CobColor.WHITE.description,
                timeControl = TimeControlInfo(initial = 0, increment = 0, label = "Unlimited"),
            )
        )
        fakeMessages.emit(
            ServerMessage.GameStarted(gameId = gameId, initialState = GameState.initialGameState()),
        )
        assertNull(client.currentGame.value?.whiteTimeMs)
        assertNull(client.currentGame.value?.blackTimeMs)
    }

    @Test
    fun `OpponentDisconnected sets opponentConnected to false`(): TestResult = runTest(dispatcher) {
        startGame()
        assertEquals(client.currentGame.value?.opponentConnected, true)

        fakeMessages.emit(ServerMessage.OpponentDisconnected(gameId, gracePeriod = 60))

        assertFalse(client.currentGame.value?.opponentConnected ?: true)
    }

    @Test
    fun `OpponentDisconnected stores gracePeriodSec from server message`(): TestResult = runTest(dispatcher) {
        startGame()

        fakeMessages.emit(ServerMessage.OpponentDisconnected(gameId, gracePeriod = 45))

        assertEquals(45, client.currentGame.value?.gracePeriodSec)
    }

    @Test
    fun `OpponentDisconnected stores client-side timestamp`(): TestResult = runTest(dispatcher) {
        startGame()
        val before = System.currentTimeMillis()

        fakeMessages.emit(ServerMessage.OpponentDisconnected(gameId, gracePeriod = 60))

        val after = System.currentTimeMillis()
        val ts = client.currentGame.value?.opponentDisconnectedAtMs
        assertNotNull(ts)
        assertTrue(ts in before..after)
    }

    @Test
    fun `OpponentReconnected restores opponentConnected and clears timestamp`(): TestResult = runTest(dispatcher) {
        startGame()
        fakeMessages.emit(ServerMessage.OpponentDisconnected(gameId, gracePeriod = 60))
        assertFalse(client.currentGame.value?.opponentConnected ?: true)
        assertNotNull(client.currentGame.value?.opponentDisconnectedAtMs)

        fakeMessages.emit(ServerMessage.OpponentReconnected(gameId))

        assertTrue(client.currentGame.value?.opponentConnected ?: false)
        assertNull(client.currentGame.value?.opponentDisconnectedAtMs)
    }

    // ── Tests 10.3 — serverErrors ─────────────────────────────────────────────

    @Test
    fun `ServerMessage Error emits GenericError to serverErrors`(): TestResult = runTest(dispatcher) {
        // La colección empieza ANTES de la emisión para no perder el evento.
        // Con el dispatcher compartido, async arranca en el mismo scheduler y
        // first() suspende hasta que serverErrors emite.
        val deferred = async { client.serverErrors.first() }

        fakeMessages.emit(ServerMessage.Error(code = "game_not_found", message = "Game not found"))

        val event = deferred.await() as? ServerErrorEvent.GenericError
        assertNotNull(event)
        assertEquals("game_not_found", event.code)
        assertEquals("Game not found", event.message)
    }

    @Test
    fun `ServerMessage InvalidMove emits InvalidMove to serverErrors`(): TestResult = runTest(dispatcher) {
        val deferred = async { client.serverErrors.first() }

        fakeMessages.emit(ServerMessage.InvalidMove(gameId = gameId, reason = "not_your_turn"))

        val event = deferred.await() as? ServerErrorEvent.InvalidMove
        assertNotNull(event)
        assertEquals("not_your_turn", event.reason)
    }

    @Test
    fun `HeartbeatAck does not emit to serverErrors`(): TestResult = runTest(dispatcher) {
        var received = false
        val collector = async {
            client.serverErrors.first()
            received = true
        }

        fakeMessages.emit(ServerMessage.HeartbeatAck)

        // HeartbeatAck no dispara serverErrors → collector sigue suspendido
        assertFalse(received, "serverErrors should not emit for HeartbeatAck")
        collector.cancel()
    }
}
