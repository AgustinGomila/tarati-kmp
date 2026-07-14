package com.agustin.tarati.features.online.connection

import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.UserInfo
import com.agustin.tarati.network.client.TaratiWebSocketClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests de reconexión automática para [ConnectionViewModel].
 *
 * Usa [runCurrent] (no advanceUntilIdle) tras simular la caída para que el
 * TestScheduler procese únicamente las tareas pendientes en el tick actual sin
 * avanzar el reloj virtual. Así el primer delay de backoff queda suspendido y
 * podemos verificar [ConnectionState.Reconnecting] antes del primer intento.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelReconnectTest {

    // Hardcodeados para coincidir con ConnectionViewModel.reconnectDelays
    private val delay1 = 2_000L
    private val delay2 = 4_000L
    private val delayAll = 2_000L + 4_000L + 8_000L + 16_000L + 30_000L

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var wsConnectionState: MutableStateFlow<TaratiWebSocketClient.ConnectionState>
    private lateinit var mockWsClient: TaratiWebSocketClient
    private lateinit var mockAuth: IAuthViewModel
    private lateinit var viewModel: ConnectionViewModel

    private val testUserInfo = UserInfo(
        userId = "user-1",
        username = "player1",
        displayName = "Player 1",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        wsConnectionState = MutableStateFlow(TaratiWebSocketClient.ConnectionState.Disconnected)
        mockWsClient = mockk(relaxed = true) {
            every { connectionState } returns wsConnectionState
        }
        mockAuth = mockk(relaxed = true) {
            every { currentUser } returns testUserInfo
        }
        viewModel = ConnectionViewModel(mockWsClient, mockAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Deja el viewModel en [ConnectionState.Online].
     * [TestScope] receptor permite llamar a [runCurrent] directamente.
     */
    private suspend fun TestScope.goOnline() {
        coEvery { mockWsClient.connect(any()) } answers {
            wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Connected
        }
        viewModel.connectToServer("localhost:8080", "token")
        runCurrent()
        assertTrue(viewModel.connectionState.value is ConnectionState.Online, "goOnline setup failed")
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `unexpected WS drop while Online triggers Reconnecting on first attempt`(): TestResult = runTest {
        goOnline()
        coEvery { mockWsClient.connect(any()) } throws Exception("Server unreachable")

        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        // runCurrent: procesa el collector y arranca autoReconnect hasta el primer delay
        runCurrent()

        val raw = viewModel.connectionState.value
        assertTrue(raw is ConnectionState.Reconnecting, "Expected Reconnecting but got $raw")
        assertEquals(1, raw.attempt)
        assertEquals("player1", raw.userInfo.username)
    }

    @Test
    fun `reconnect succeeds on second attempt`(): TestResult = runTest {
        goOnline()

        var attempts = 0
        coEvery { mockWsClient.connect(any()) } answers {
            attempts++
            if (attempts == 1) throw Exception("Timeout")
            wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Connected
        }

        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        runCurrent()                       // → Reconnecting(attempt=1)
        assertTrue(viewModel.connectionState.value is ConnectionState.Reconnecting)

        // delay1 vence → intento 1 falla → bucle avanza a intento 2
        advanceTimeBy((delay1 + 1).milliseconds)
        runCurrent()
        assertEquals(
            2,
            (viewModel.connectionState.value as? ConnectionState.Reconnecting)?.attempt,
            "Expected attempt 2 after first failure",
        )

        // delay2 vence → intento 2 tiene éxito → Online
        advanceTimeBy((delay2 + 1).milliseconds)
        runCurrent()

        val state = viewModel.connectionState.value
        assertTrue(state is ConnectionState.Online, "Expected Online but got $state")
    }

    @Test
    fun `intentional disconnect cancels auto-reconnect and goes Offline`(): TestResult = runTest {
        goOnline()
        coEvery { mockWsClient.connect(any()) } throws Exception("Still down")

        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        runCurrent()                       // → Reconnecting(attempt=1)
        assertTrue(viewModel.connectionState.value is ConnectionState.Reconnecting)

        viewModel.disconnect()
        runCurrent()

        val state = viewModel.connectionState.value
        assertTrue(state is ConnectionState.Offline, "Expected Offline after disconnect but got $state")
    }

    @Test
    fun `all reconnect attempts exhausted transitions to Offline`(): TestResult = runTest {
        goOnline()
        coEvery { mockWsClient.connect(any()) } throws Exception("Server down")

        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        runCurrent()                       // → Reconnecting(attempt=1)
        assertTrue(viewModel.connectionState.value is ConnectionState.Reconnecting)

        // Avanzar más allá de la suma de todos los delays
        advanceTimeBy((delayAll + 1_000).milliseconds)
        runCurrent()

        val state = viewModel.connectionState.value
        assertTrue(state is ConnectionState.Offline, "Expected Offline after all retries but got $state")
    }

    @Test
    fun `connect and reconnect use a freshly refreshed token, never the stale cached one`(): TestResult = runTest {
        // El access token está por expirar → validToken() lo renueva a "fresh-token".
        every { mockAuth.isTokenExpiringSoon(any()) } returns true
        coEvery { mockAuth.refreshToken() } returns Result.success("fresh-token")
        every { mockAuth.accessToken } returns "fresh-token"

        coEvery { mockWsClient.connect(any()) } answers {
            wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Connected
        }
        // El caller pasa un token expirado; el ViewModel debe ignorarlo y usar el renovado.
        viewModel.connectToServer("localhost:8080", "stale-token")
        runCurrent()
        assertTrue(viewModel.connectionState.value is ConnectionState.Online)

        // Caída inesperada → auto-reconnect.
        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        runCurrent()
        advanceTimeBy((delay1 + 1).milliseconds)
        runCurrent()

        coVerify(atLeast = 1) { mockWsClient.connect("fresh-token") }
        coVerify(exactly = 0) { mockWsClient.connect("stale-token") }
    }

    @Test
    fun `WS Disconnected event while already Offline does not trigger auto-reconnect`(): TestResult = runTest {
        // viewModel inicia Offline, nunca estuvo Online
        wsConnectionState.value = TaratiWebSocketClient.ConnectionState.Disconnected
        runCurrent()

        val state = viewModel.connectionState.value
        assertFalse(state is ConnectionState.Reconnecting, "Should not auto-reconnect from Offline")
        assertTrue(state is ConnectionState.Offline)
    }
}
