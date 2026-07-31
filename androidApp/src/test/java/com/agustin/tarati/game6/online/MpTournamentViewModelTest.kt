@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.game6.online

import com.agustin.tarati.features.game6.MpTournamentViewModel
import com.agustin.tarati.network.models.CreateMpTournamentRequest
import com.agustin.tarati.network.models.MpTournamentDto
import com.agustin.tarati.network.models.MpTournamentStandingDto
import com.agustin.tarati.network.models.TournamentStatus
import com.agustin.tarati.network.protocol.MpServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MpTournamentViewModel] (torneos MP, fase 4b): auto-selección al crear, delegación de
 * acciones en el repo inyectado, y actualización en vivo del detalle por el flow de standings.
 */
class MpTournamentViewModelTest {

    private fun dto(
        id: String,
        status: TournamentStatus = TournamentStatus.REGISTERING,
        standings: List<MpTournamentStandingDto> = emptyList(),
    ) = MpTournamentDto(
        id = id, name = "T-$id", creatorId = "host", creatorName = "Host", status = status,
        tableSize = 4, turnTimeoutMs = 0L, minPlayers = 4, maxPlayers = 16, durationMinutes = 20,
        spectatingAllowed = true, participantCount = standings.size, standings = standings,
    )

    private fun standing(userId: String, score: Int) =
        MpTournamentStandingDto(1, userId, "n-$userId", score, 0, 0, 0, 0, 0)

    @Test
    fun create_selectsCreatedTournament(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val created = dto("t1")
        val vm = MpTournamentViewModel(
            getToken = { "tok" },
            fetchList = { Result.success(listOf(created)) },
            createReq = { _, _ -> Result.success(created) },
            scope = scope,
        )
        vm.create(CreateMpTournamentRequest(name = "T"))
        assertEquals("t1", vm.selected.value?.id)
        scope.cancel()
    }

    @Test
    fun register_delegatesToRepoAndUpdatesSelected(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var registeredId: String? = null
        val registered = dto("t1", standings = listOf(standing("me", 0)))
        val vm = MpTournamentViewModel(
            getToken = { "tok" },
            fetchList = { Result.success(listOf(dto("t1"))) },
            fetchDetail = { _, id -> Result.success(dto(id)) },
            registerReq = { _, id -> registeredId = id; Result.success(registered) },
            scope = scope,
        )
        vm.select("t1")
        vm.register()
        assertEquals("t1", registeredId)
        assertTrue(vm.selected.value?.standings?.any { it.userId == "me" } == true)
        scope.cancel()
    }

    @Test
    fun liveStandings_patchSelectedDetail(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val standings = MutableSharedFlow<MpServerMessage.TournamentStandingsUpdated>(extraBufferCapacity = 8)
        val vm = MpTournamentViewModel(
            getToken = { "tok" },
            fetchList = { Result.success(listOf(dto("t1"))) },
            fetchDetail = { _, id -> Result.success(dto(id)) },
            standings = standings,
            scope = scope,
        )
        vm.select("t1")
        standings.emit(MpServerMessage.TournamentStandingsUpdated("t1", listOf(standing("me", 5))))
        assertEquals(5, vm.selected.value?.standings?.firstOrNull { it.userId == "me" }?.score)
        scope.cancel()
    }

    @Test
    fun actionError_emitsErrorCode(): TestResult = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val errors = mutableListOf<String>()
        val vm = MpTournamentViewModel(
            getToken = { "tok" },
            fetchList = { Result.success(listOf(dto("t1"))) },
            fetchDetail = { _, id -> Result.success(dto(id)) },
            startReq = { _, _ -> Result.failure(IllegalStateException("not_enough_players")) },
            scope = scope,
        )
        val job = scope.launch { vm.errors.collect { errors += it } }
        vm.select("t1")
        vm.start()
        assertTrue("not_enough_players" in errors)
        assertNull(vm.selected.value?.standings?.firstOrNull()) // sin cambios en el detalle
        job.cancel()
        scope.cancel()
    }
}
