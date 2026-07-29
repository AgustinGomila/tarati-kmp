@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.game6.online

import com.agustin.tarati.features.game6.MpLeaderboardViewModel
import com.agustin.tarati.network.models.MpLeaderboardEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MpLeaderboardViewModel] (Fase 4a): carga de la clasificación MP (REST inyectado) con
 * [UnconfinedTestDispatcher] → el `load()` del init corre de inmediato.
 */
class MpLeaderboardViewModelTest {

    private fun entry(id: String, rating: Int) = MpLeaderboardEntryDto(
        rank = 0, id = id, username = id, displayName = null, country = null, avatarUrl = null,
        rating = rating, games = 5, wins = 3, shared = 1, losses = 1,
    )

    @Test
    fun init_loadsLeaderboard(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val list = listOf(entry("a", 1500), entry("b", 1400))

        val vm = MpLeaderboardViewModel(
            getToken = { "tok" },
            fetchLeaderboard = { Result.success(list) },
            scope = scope,
        )

        assertEquals(list, vm.state.value.entries)
        assertTrue(!vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        scope.cancel()
    }

    @Test
    fun noToken_keepsEmpty(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val vm = MpLeaderboardViewModel(
            getToken = { null },
            fetchLeaderboard = { Result.success(listOf(entry("a", 1500))) },
            scope = scope,
        )

        assertTrue(vm.state.value.entries.isEmpty())
        assertTrue(!vm.state.value.isLoading)
        scope.cancel()
    }

    @Test
    fun failure_setsError(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val vm = MpLeaderboardViewModel(
            getToken = { "tok" },
            fetchLeaderboard = { Result.failure(RuntimeException("boom")) },
            scope = scope,
        )

        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("boom", vm.state.value.error)
        scope.cancel()
    }
}
