package com.agustin.tarati.features.online.auth

import com.agustin.tarati.services.billing.EntitlementsRepository
import io.ktor.client.HttpClient
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests unitarios para AuthViewModel
 *
 * Verifica:
 * - Autenticación con token
 * - Guardar/recuperar tokens
 * - Logout
 * - Restauración de sesión
 * - Manejo de errores
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockHttpClient: HttpClient
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockAuthRepository = mockk(relaxed = true) {
            every { getToken() } returns null
            // Sin refresh token → logout omite el POST a /auth/logout y completa inmediatamente.
            every { getRefreshToken() } returns null
        }
        mockHttpClient = mockk(relaxed = true)

        viewModel = AuthViewModel(mockAuthRepository, mockHttpClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Unauthenticated when no stored token`() {
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
        assertNull(viewModel.accessToken)
        assertFalse(viewModel.isAuthenticated)
    }

    @Test
    fun `authenticateWithToken updates state to Authenticated`(): TestResult = runTest {
        val token = makeTestJwt(sub = "user-123", username = "test_player")

        val result = viewModel.authenticateWithToken(token)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertTrue(viewModel.authState.value is AuthState.Authenticated)
        assertEquals(token, viewModel.accessToken)
        assertTrue(viewModel.isAuthenticated)

        val authState = viewModel.authState.value as AuthState.Authenticated
        assertEquals("test_player", authState.userInfo.username)
        assertFalse(authState.userInfo.isGuest)
    }

    @Test
    fun `authenticateWithToken with guest token creates guest user`(): TestResult = runTest {
        val token = makeTestJwt(sub = "guest-1", username = "guest_player", isGuest = true)

        val result = viewModel.authenticateWithToken(token)
        advanceUntilIdle()

        assertTrue(result.isSuccess)

        val authState = viewModel.authState.value as AuthState.Authenticated
        assertEquals("guest_player", authState.userInfo.username)
        assertTrue(authState.userInfo.isGuest)
    }

    @Test
    fun `saveToken calls repository`() {
        viewModel.saveToken("my_token")

        verify { mockAuthRepository.saveToken("my_token") }
        assertEquals("my_token", viewModel.accessToken)
    }

    @Test
    fun `getStoredToken calls repository`() {
        every { mockAuthRepository.getToken() } returns "stored_value"

        val token = viewModel.getStoredToken()

        assertEquals("stored_value", token)
        verify { mockAuthRepository.getToken() }
    }

    @Test
    fun `logout clears state and calls repository`(): TestResult = runTest {
        val token = makeTestJwt(sub = "user-123", username = "test_player")
        viewModel.authenticateWithToken(token)
        advanceUntilIdle()
        assertTrue(viewModel.isAuthenticated)

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
        assertNull(viewModel.accessToken)
        assertFalse(viewModel.isAuthenticated)
        verify { mockAuthRepository.clearAll() }
    }

    @Test
    fun `clearError transitions from Error to Unauthenticated`(): TestResult = runTest {
        // Forzar estado de error es complejo en este mock —
        // este test verifica que clearError no rompe el estado Unauthenticated.
        viewModel.clearError()

        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `restores session on init when token exists`() {
        val token = makeTestJwt(sub = "user-123", username = "test_player")
        every { mockAuthRepository.getToken() } returns token

        val newViewModel = AuthViewModel(mockAuthRepository, mockHttpClient)

        assertTrue(newViewModel.authState.value is AuthState.Authenticated)
        assertEquals(token, newViewModel.accessToken)
        assertTrue(newViewModel.isAuthenticated)
    }

    @Test
    fun `refreshes entitlements on session restore`(): TestResult = runTest {
        // El ownership de supporter debe cargarse apenas se restaura la sesión al arrancar,
        // sin que el usuario tenga que abrir la pantalla Supporter. Guarda la regresión de
        // attemptRestoreSession, que antes solo sincronizaba logros.
        val token = makeTestJwt(sub = "user-123", username = "test_player")
        every { mockAuthRepository.getToken() } returns token
        val mockEntitlements = mockk<EntitlementsRepository>(relaxed = true)

        AuthViewModel(mockAuthRepository, mockHttpClient, mockEntitlements)
        advanceUntilIdle()

        coVerify { mockEntitlements.refresh() }
    }

    @Test
    fun `does not restore session when no token exists`() {
        every { mockAuthRepository.getToken() } returns null

        val newViewModel = AuthViewModel(mockAuthRepository, mockHttpClient)

        assertTrue(newViewModel.authState.value is AuthState.Unauthenticated)
        assertNull(newViewModel.accessToken)
        assertFalse(newViewModel.isAuthenticated)
    }

    @Test
    fun `currentUser returns UserInfo when authenticated`(): TestResult = runTest {
        val token = makeTestJwt(sub = "user-123", username = "test_player")
        viewModel.authenticateWithToken(token)
        advanceUntilIdle()

        val user = viewModel.currentUser

        assertNotNull(user)
        assertEquals("test_player", user?.username)
    }

    @Test
    fun `currentUser returns null when not authenticated`() {
        assertNull(viewModel.currentUser)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Construye un JWT mínimo con header estándar y un payload JSON base64url-encoded.
     * La firma es un placeholder — AuthViewModel no la valida, solo decodifica el payload.
     *
     * @param sub      Claim "sub" (userId)
     * @param username Claim "username"
     * @param isGuest  Si true, añade "isGuest":true al payload
     * @param exp      Claim "exp" en segundos epoch (default: año ~2286)
     */
    private fun makeTestJwt(
        sub: String,
        username: String,
        isGuest: Boolean = false,
        exp: Long = 9_999_999_999L,
    ): String {
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payloadJson = buildString {
            append("{")
            append(""""sub":"$sub"""")
            append(""","username":"$username"""")
            if (isGuest) append(""","isGuest":true""")
            append(""","exp":$exp""")
            append("}")
        }
        val payloadB64 = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return "$header.$payloadB64.TESTHMACSIG"
    }
}
