package com.agustin.tarati.features.seasonal

import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.services.achievements.AchievementSyncService
import com.agustin.tarati.services.achievements.AchievementsRepository
import com.agustin.tarati.services.achievements.IAchievementsReporter
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.special_event_dark_side_desc
import com.agustin.tarati.shared.generated.resources.special_event_dark_side_title
import com.agustin.tarati.shared.generated.resources.special_event_first_light_desc
import com.agustin.tarati.shared.generated.resources.special_event_first_light_title
import com.agustin.tarati.shared.generated.resources.special_event_palette_aurora
import com.agustin.tarati.shared.generated.resources.special_event_palette_ember
import com.agustin.tarati.ui.theme.AuroraPalette
import com.agustin.tarati.ui.theme.BoardPalette
import com.agustin.tarati.ui.theme.EmberPalette
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Tests for [SpecialEventManager] — achievement unlock logic for
 * "First Light" (win as White → Aurora) and "Dark Side" (win as Black → Ember).
 *
 * ## Why stateful mocks for AchievementsRepository
 * [AchievementsRepository.unlockAurora] / [unlockEmber] write to DataStore.
 * [AchievementsRepository.auroraUnlocked] / [emberUnlocked] are Flows reflecting
 * that state. A relaxed mock returns the initial flow value forever — so after
 * calling unlockAurora(), isEventAlreadyUnlocked() still returns false and the
 * event never leaves activeEvents. The stateful mock updates [kotlinx.coroutines.flow.MutableStateFlow]
 * on each unlock call, replicating real persistence without Android DataStore.
 *
 * ## Date format
 * [SpecialEvent.activeDays] uses (month, day) pairs — April 5 = 4 to 5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecialEventManagerTest {

    // ── Test events ───────────────────────────────────────────────────────────

    private val firstLightEvent = SpecialEvent(
        id = "first_light",
        titleRes = Res.string.special_event_first_light_title,
        descRes = Res.string.special_event_first_light_desc,
        condition = SpecialEventCondition.WinAsWhite,
        reward = SpecialEventReward(
            paletteName = "Aurora",
            displayNameRes = Res.string.special_event_palette_aurora,
        ),
        activeDays = listOf(4 to 4, 4 to 6),
    )

    private val darkSideEvent = SpecialEvent(
        id = "dark_side",
        titleRes = Res.string.special_event_dark_side_title,
        descRes = Res.string.special_event_dark_side_desc,
        condition = SpecialEventCondition.WinAsBlack,
        reward = SpecialEventReward(
            paletteName = "Ember",
            displayNameRes = Res.string.special_event_palette_ember,
        ),
        activeDays = listOf(4 to 5, 4 to 6),
    )

    private val testEvents = listOf(firstLightEvent, darkSideEvent)

    // ── Stateful mock state ───────────────────────────────────────────────────

    private val auroraUnlockedFlow = MutableStateFlow(false)
    private val emberUnlockedFlow = MutableStateFlow(false)

    // ── Mocks / fakes ─────────────────────────────────────────────────────────

    private lateinit var achievementsRepository: AchievementsRepository
    private lateinit var specialEventRepository: SpecialEventRepository
    private lateinit var settingsRepository: SettingsRepository

    private val appliedPalettes = mutableListOf<BoardPalette>()
    private lateinit var reporter: IAchievementsReporter
    private var fakeDate: Pair<Int, Int> = 4 to 4

    @Before
    fun setUp() {
        auroraUnlockedFlow.value = false
        emberUnlockedFlow.value = false
        appliedPalettes.clear()
        fakeDate = 4 to 4

        reporter = mockk(relaxed = true)
        achievementsRepository = mockk(relaxed = true) {
            every { auroraUnlocked } returns auroraUnlockedFlow
            every { emberUnlocked } returns emberUnlockedFlow
            // answers must return Preferences (the type DataStore.edit returns).
            // The returned mock is unused by callers; we only need the side effect.
            coEvery { unlockAurora() } answers {
                auroraUnlockedFlow.value = true
                mockk(relaxed = true)
            }
            coEvery { unlockEmber() } answers {
                emberUnlockedFlow.value = true
                mockk(relaxed = true)
            }
        }
        specialEventRepository = mockk(relaxed = true) {
            coEvery { isGiftSeen(any()) } returns false
        }
        settingsRepository = mockk(relaxed = true)
    }

    private fun manager() = SpecialEventManager(
        specialEventRepository = specialEventRepository,
        achievementsRepository = achievementsRepository,
        settingsRepository = settingsRepository,
        reporter = reporter,
        syncService = mockk<AchievementSyncService>(relaxed = true),
        authRepository = mockk<AuthRepository>(relaxed = true),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        specialEvents = testEvents,
        dateProvider = { fakeDate },
        paletteApplier = { appliedPalettes.add(it) },
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun winBy(color: CobColor) = MatchState(
        gameState = mockk(relaxed = true),
        gameEndReason = GameEndReason.MIT,
        winner = color,
        positionHistory = emptyMap(),
    )

    private val draw = MatchState(
        gameState = mockk(relaxed = true),
        gameEndReason = GameEndReason.FIFTY_MOVES,
        winner = null,
        positionHistory = emptyMap(),
    )

    // ══════════════════════════════════════════════════════════════════════════
    // Active events per date
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `on April 4 only first light is active`(): TestResult = runTest {
        fakeDate = 4 to 4
        val active = manager().activeEvents.value
        Assert.assertEquals(1, active.size)
        Assert.assertEquals("first_light", active[0].id)
    }

    @Test
    fun `on April 5 only dark side is active`(): TestResult = runTest {
        fakeDate = 4 to 5
        val active = manager().activeEvents.value
        Assert.assertEquals(1, active.size)
        Assert.assertEquals("dark_side", active[0].id)
    }

    @Test
    fun `on April 6 both events are active`(): TestResult = runTest {
        fakeDate = 4 to 6
        Assert.assertEquals(2, manager().activeEvents.value.size)
    }

    @Test
    fun `on a non-event day no events are active`(): TestResult = runTest {
        fakeDate = 1 to 1
        Assert.assertTrue(manager().activeEvents.value.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // First Light — win as White → Aurora
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `first light unlocks on April 4 when human wins as White`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)

        coVerify { achievementsRepository.unlockAurora() }
        Assert.assertEquals(listOf(AuroraPalette), appliedPalettes)
        Assert.assertEquals(firstLightEvent, m.pendingCelebration.value)
    }

    @Test
    fun `first light unlocks on April 6 when human wins as White`(): TestResult = runTest {
        fakeDate = 4 to 6
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)

        coVerify { achievementsRepository.unlockAurora() }
        Assert.assertNotNull(m.pendingCelebration.value)
    }

    @Test
    fun `first light does not unlock on April 5 - not its active day`(): TestResult = runTest {
        fakeDate = 4 to 5
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)

        coVerify(exactly = 0) { achievementsRepository.unlockAurora() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `first light does not unlock when human plays as Black`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify(exactly = 0) { achievementsRepository.unlockAurora() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `first light does not unlock when AI wins`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.WHITE) // human is White but AI (Black) won

        coVerify(exactly = 0) { achievementsRepository.unlockAurora() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `first light does not unlock on a draw`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()

        m.onGameResult(draw, CobColor.WHITE)

        coVerify(exactly = 0) { achievementsRepository.unlockAurora() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `first light excluded from active events when already unlocked`(): TestResult = runTest {
        auroraUnlockedFlow.value = true
        fakeDate = 4 to 4
        Assert.assertTrue(manager().activeEvents.value.none { it.id == "first_light" })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dark Side — win as Black → Ember
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `dark side unlocks on April 5 when human wins as Black`(): TestResult = runTest {
        fakeDate = 4 to 5
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify { achievementsRepository.unlockEmber() }
        Assert.assertEquals(listOf(EmberPalette), appliedPalettes)
        Assert.assertEquals(darkSideEvent, m.pendingCelebration.value)
    }

    @Test
    fun `dark side unlocks on April 6 when human wins as Black`(): TestResult = runTest {
        fakeDate = 4 to 6
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify { achievementsRepository.unlockEmber() }
        Assert.assertNotNull(m.pendingCelebration.value)
    }

    @Test
    fun `dark side does not unlock on April 4 - not its active day`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify(exactly = 0) { achievementsRepository.unlockEmber() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `dark side does not unlock when human plays as White`(): TestResult = runTest {
        fakeDate = 4 to 5
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)

        coVerify(exactly = 0) { achievementsRepository.unlockEmber() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `dark side does not unlock when AI wins`(): TestResult = runTest {
        fakeDate = 4 to 5
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.BLACK) // human is Black but AI (White) won

        coVerify(exactly = 0) { achievementsRepository.unlockEmber() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `dark side does not unlock on a draw`(): TestResult = runTest {
        fakeDate = 4 to 5
        val m = manager()

        m.onGameResult(draw, CobColor.BLACK)

        coVerify(exactly = 0) { achievementsRepository.unlockEmber() }
        Assert.assertNull(m.pendingCelebration.value)
    }

    @Test
    fun `dark side excluded from active events when already unlocked`(): TestResult = runTest {
        emberUnlockedFlow.value = true
        fakeDate = 4 to 5
        Assert.assertTrue(manager().activeEvents.value.none { it.id == "dark_side" })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stale state — onGameResult refreshes before evaluating
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `dark side unlocks after first light was unlocked earlier in the same session`(): TestResult = runTest {
        // Day 4: first light active, dark side not active yet
        fakeDate = 4 to 4
        val m = manager()
        Assert.assertEquals("first_light", m.activeEvents.value.single().id)

        // Win as White → first light unlocked
        // Stateful mock sets auroraUnlockedFlow = true, so the refreshActiveEvents()
        // inside unlock() correctly removes first_light from activeEvents.
        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)
        coVerify { achievementsRepository.unlockAurora() }

        // activeEvents refreshed with day 4 → first_light removed (unlocked),
        // dark_side not in day 4 activeDays → list is empty
        Assert.assertTrue(
            "activeEvents should be empty after unlock on day 4",
            m.activeEvents.value.isEmpty()
        )

        // Date moves to April 5
        fakeDate = 4 to 5

        // onGameResult refreshes activeEvents first (now finds dark_side active)
        // then evaluates the condition
        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify { achievementsRepository.unlockEmber() }
        Assert.assertEquals(darkSideEvent, m.pendingCelebration.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Cross-unlock isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `winning as White on April 6 unlocks only Aurora`(): TestResult = runTest {
        fakeDate = 4 to 6
        val m = manager()

        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)

        coVerify { achievementsRepository.unlockAurora() }
        coVerify(exactly = 0) { achievementsRepository.unlockEmber() }
    }

    @Test
    fun `winning as Black on April 6 unlocks only Ember`(): TestResult = runTest {
        fakeDate = 4 to 6
        val m = manager()

        m.onGameResult(winBy(CobColor.BLACK), CobColor.BLACK)

        coVerify { achievementsRepository.unlockEmber() }
        coVerify(exactly = 0) { achievementsRepository.unlockAurora() }
    }

    @Test
    fun `dismissCelebration clears pending celebration`(): TestResult = runTest {
        fakeDate = 4 to 4
        val m = manager()
        m.onGameResult(winBy(CobColor.WHITE), CobColor.WHITE)
        Assert.assertNotNull(m.pendingCelebration.value)

        m.dismissCelebration()

        Assert.assertNull(m.pendingCelebration.value)
    }
}