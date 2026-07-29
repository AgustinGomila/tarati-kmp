@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.game6.online

import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpEndReason
import com.agustin.tarati.core.domain.game6.play.MpNotation
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.rules.MpMatch
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.features.game6.MpGameDetailViewModel
import com.agustin.tarati.network.models.MpGameDetailDto
import com.agustin.tarati.network.models.MpPlayerDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MpGameDetailViewModel] (visor de replay MP): reconstrucción determinista del historial +
 * navegación (first/prev/next/last/moveToIndex). REST inyectado; [UnconfinedTestDispatcher] hace correr
 * el `loadGame` de inmediato.
 */
class MpGameDetailViewModelTest {

    // Apertura legal de 2 jugadores (misma que MpLocalGameHistoryTest): P1 D1-C1, P2 D10-C7.
    private val history = "a:D1-C1,b:D10-C7"

    private fun detail(history: String) = MpGameDetailDto(
        gameId = "g1",
        playerCount = 2,
        players = listOf(
            MpPlayerDto(PlayerColor.P1, "u1", "Alice"),
            MpPlayerDto(PlayerColor.P2, "u2", "Bob"),
        ),
        result = MpResult(
            winners = listOf(PlayerColor.P1),
            reason = MpEndReason.LAST_STANDING,
            finalPieceCounts = mapOf(PlayerColor.P1 to 4, PlayerColor.P2 to 3),
        ),
        history = history,
        moveCount = MpNotation.parseHistory(history).size,
        startedAtMs = 1000,
        endedAtMs = 2000,
    )

    private fun vmWith(detail: MpGameDetailDto, token: String? = "tok", scope: CoroutineScope) =
        MpGameDetailViewModel(
            getToken = { token },
            fetchDetail = { _, _ -> Result.success(detail) },
            scope = scope,
        ).also { it.loadGame("g1") }

    @Test
    fun load_reconstructsAndShowsFinalPosition(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = vmWith(detail(history), scope = scope)

        val expectedFinal = MpMatch(MpSetup.initialState(2)).apply {
            MpNotation.parseHistory(history).forEach { applyMove(it.move) }
        }.state

        val s = vm.state.value
        assertTrue(!s.isLoading)
        assertNull(s.error)
        assertEquals(2, s.history.size)
        assertEquals(1, s.moveIndex)           // última jugada
        assertEquals(expectedFinal, s.state)   // posición final reconstruida
        scope.cancel()
    }

    @Test
    fun navigation_firstPrevNextLast(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = vmWith(detail(history), scope = scope)

        val initial = MpSetup.initialState(2)

        vm.first()
        assertEquals(-1, vm.state.value.moveIndex)
        assertEquals(initial, vm.state.value.state)
        assertNull(vm.state.value.lastMove)

        vm.next()
        assertEquals(0, vm.state.value.moveIndex)
        assertNotNull(vm.state.value.lastMove) // avanzar puebla el último movimiento (animación)

        vm.last()
        assertEquals(1, vm.state.value.moveIndex)

        vm.prev()
        assertEquals(0, vm.state.value.moveIndex)
        assertNull(vm.state.value.lastMove)    // retroceder es snap (sin animación)

        vm.moveToIndex(-1)
        assertEquals(-1, vm.state.value.moveIndex)
        scope.cancel()
    }

    @Test
    fun emptyHistory_hasOnlyInitialSnapshot(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = vmWith(detail(""), scope = scope)

        assertTrue(vm.state.value.history.isEmpty())
        assertEquals(-1, vm.state.value.moveIndex)
        assertEquals(MpSetup.initialState(2), vm.state.value.state)

        vm.next() // sin jugadas → no-op
        assertEquals(-1, vm.state.value.moveIndex)
        scope.cancel()
    }

    @Test
    fun noToken_setsError(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = vmWith(detail(history), token = null, scope = scope)

        assertNull(vm.state.value.state)
        assertEquals("no_session", vm.state.value.error)
        scope.cancel()
    }

    @Test
    fun failure_setsError(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = MpGameDetailViewModel(
            getToken = { "tok" },
            fetchDetail = { _, _ -> Result.failure(RuntimeException("boom")) },
            scope = scope,
        ).also { it.loadGame("g1") }

        assertNull(vm.state.value.state)
        assertEquals("boom", vm.state.value.error)
        scope.cancel()
    }
}
