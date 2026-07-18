package com.agustin.tarati.services.achievements

import com.agustin.tarati.R
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.B2
import com.agustin.tarati.core.domain.game.board.GameBoard.B4
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C3
import com.agustin.tarati.core.domain.game.board.GameBoard.C7
import com.agustin.tarati.core.domain.game.board.GameBoard.D3
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.network.models.ServerAchievementDto
import com.google.android.gms.games.achievement.Achievement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AchievementsManagerTest {

    /**
     * Fake reporter que registra en memoria qué logros fueron desbloqueados
     * y con qué pasos, sin tocar Play Games SDK ni Activity.
     */
    private class FakeAchievementsReporter : IAchievementsReporter {
        val unlockedAchievements = mutableSetOf<Int>()
        val stepUpdates = mutableMapOf<Int, Int>()

        /** Snapshots que [loadAchievements] devuelve — configurables por test de reconciliación. */
        var snapshots: List<AchievementSnapshot> = emptyList()

        override fun unlock(achievementResId: Int) {
            unlockedAchievements.add(achievementResId)
        }

        override fun setSteps(achievementResId: Int, steps: Int): Boolean {
            stepUpdates[achievementResId] = steps
            return true
        }

        override fun loadAchievements(
            onResult: (List<AchievementSnapshot>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onResult(snapshots)
        }

        fun wasUnlocked(achievementResId: Int) = achievementResId in unlockedAchievements
        fun reset() {
            unlockedAchievements.clear(); stepUpdates.clear()
        }
    }

    private lateinit var reporter: FakeAchievementsReporter
    private lateinit var repository: AchievementsRepository
    private lateinit var context: android.content.Context
    private lateinit var syncService: AchievementSyncService
    private lateinit var authRepository: AuthRepository
    private lateinit var manager: AchievementsManager

    @Before
    fun setUp() {
        reporter = FakeAchievementsReporter()
        repository = mockk(relaxed = true) {
            coEvery { incrementTotalCaptures(any()) } returns 1
            coEvery { incrementTotalPromotions() } returns 1
            coEvery { incrementTotalWins() } returns 1
            coEvery { incrementTotalGames() } returns 1
            coEvery { getCachedSteps(any()) } returns 0
            // Sin cuenta previa reconciliada (relaxed devolvería "", no null).
            coEvery { getLastReconciledUserId() } returns null
        }
        context = mockk(relaxed = true)
        syncService = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        manager = AchievementsManager(
            context = context,
            repository = repository,
            activityProvider = mockk(relaxed = true),
            reporter = reporter,
            aiEngine = mockk<IAIEngine>(relaxed = true),
            syncService = syncService,
            authRepository = authRepository,
        )
    }

    // ── onMoveApplied ─────────────────────────────────────────────────────────

    @Test
    fun onMoveApplied_withCapture_unlocksFirstCapture(): TestResult = runTest {
        val oldState = GameState.createGameState {
            setTurn(WHITE)
            setCob(C3, WHITE)
            setCob(B1, BLACK)
        }
        val move = Move(C3 to B2)
        val newState = oldState.applyMove(move)

        manager.onMoveApplied(move, oldState, newState)

        assertTrue(reporter.wasUnlocked(R.string.achievement_first_capture))
    }

    @Test
    fun onMoveApplied_withCapture_updatesFlipperSteps(): TestResult = runTest {
        val oldState = GameState.createGameState {
            setTurn(WHITE)
            setCob(C3, WHITE)
            setCob(B1, BLACK)
        }
        val move = Move(C3 to B2)
        val newState = oldState.applyMove(move)
        coEvery { repository.incrementTotalCaptures(any()) } returns 10

        manager.onMoveApplied(move, oldState, newState)

        assertEquals(10, reporter.stepUpdates[R.string.achievement_the_flipper])
    }

    @Test
    fun onMoveApplied_noCapture_doesNotUnlockFirstCapture(): TestResult = runTest {
        // WHITE en C3 mueve a B2, pero no hay pieza negra adyacente a B2
        val oldState = GameState.createGameState {
            setTurn(WHITE)
            setCob(C3, WHITE)
        }
        val move = Move(C3 to B2)
        val newState = oldState.applyMove(move)

        manager.onMoveApplied(move, oldState, newState)

        assertFalse(reporter.wasUnlocked(R.string.achievement_first_capture))
    }

    @Test
    fun onMoveApplied_withPromotion_unlocksFirstPromotion(): TestResult = runTest {
        val oldState = GameState.createGameState {
            setTurn(WHITE)
            setCob(C7, WHITE)
        }
        val move = Move(C7 to D3)
        val newState = oldState.applyMove(move)

        manager.onMoveApplied(move, oldState, newState)

        assertTrue(reporter.wasUnlocked(R.string.achievement_first_promotion))
    }

    // ── onGameOver ────────────────────────────────────────────────────────────

    @Test
    fun onGameOver_humanWins_unlocksFirstVictory(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_first_victory))
    }

    @Test
    fun onGameOver_humanLoses_doesNotUnlockFirstVictory(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = BLACK,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertFalse(reporter.wasUnlocked(R.string.achievement_first_victory))
    }

    @Test
    fun onGameOver_humanWinsByMit_unlocksMit(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_mit))
    }

    @Test
    fun onGameOver_humanWinsByStalemit_unlocksStalemit(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.STALEMIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_stalemit))
    }

    @Test
    fun onGameOver_humanWinsByTriple_unlocksEternalLoop(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.TRIPLE,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_eternal_loop))
    }

    @Test
    fun onGameOver_humanWinsOnChampion_unlocksChampion(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.CHAMPION)

        assertTrue(reporter.wasUnlocked(R.string.achievement_champion))
    }

    @Test
    fun onGameOver_humanWinsOnNonChampion_doesNotUnlockChampion(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.HARD)

        assertFalse(reporter.wasUnlocked(R.string.achievement_champion))
    }

    @Test
    fun onGameOver_draw_doesNotUnlockVictoryAchievements(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.FIFTY_MOVES,
            winner = null,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.CHAMPION)

        assertFalse(reporter.wasUnlocked(R.string.achievement_first_victory))
        assertFalse(reporter.wasUnlocked(R.string.achievement_champion))
    }

    @Test
    fun onGameOver_fiftyMoveDraw_unlocksFiftyMoveRule(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.FIFTY_MOVES,
            winner = null,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_fifty_move_rule))
    }

    @Test
    fun onGameOver_doesNotUnlockPlay10GamesBeforeReaching10(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = BLACK,
            positionHistory = emptyMap(),
        )
        coEvery { repository.incrementTotalGames() } returns 5

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertFalse(reporter.wasUnlocked(R.string.achievement_play_10_games))
    }

    // ── Difficulty achievements ───────────────────────────────────────────────

    @Test
    fun onGameOver_humanWinsOnEasy_unlocksApprentice(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.EASY)

        assertTrue(reporter.wasUnlocked(R.string.achievement_apprentice))
    }

    @Test
    fun onGameOver_humanWinsOnMedium_unlocksStrategist(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertTrue(reporter.wasUnlocked(R.string.achievement_strategist))
    }

    @Test
    fun onGameOver_humanWinsOnHard_unlocksTactician(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.HARD)

        assertTrue(reporter.wasUnlocked(R.string.achievement_tactician))
    }

    @Test
    fun onGameOver_humanWinsOnEasy_doesNotUnlockStrategistOrTactician(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.EASY)

        assertFalse(reporter.wasUnlocked(R.string.achievement_strategist))
        assertFalse(reporter.wasUnlocked(R.string.achievement_tactician))
    }

    @Test
    fun onGameOver_humanWinsOnMedium_doesNotUnlockApprenticeOrTactician(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = WHITE,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.DEFAULT)

        assertFalse(reporter.wasUnlocked(R.string.achievement_apprentice))
        assertFalse(reporter.wasUnlocked(R.string.achievement_tactician))
    }

    @Test
    fun onGameOver_humanLoses_doesNotUnlockDifficultyAchievements(): TestResult = runTest {
        val matchState = MatchState(
            gameState = mockk(relaxed = true),
            gameEndReason = GameEndReason.MIT,
            winner = BLACK,
            positionHistory = emptyMap(),
        )

        manager.onGameOver(matchState, playerSide = WHITE, difficulty = Difficulty.HARD)

        assertFalse(reporter.wasUnlocked(R.string.achievement_apprentice))
        assertFalse(reporter.wasUnlocked(R.string.achievement_strategist))
        assertFalse(reporter.wasUnlocked(R.string.achievement_tactician))
    }

    // ── onTutorialCompleted ───────────────────────────────────────────────────

    @Test
    fun onTutorialCompleted_unlocksWelcomeToTarati(): TestResult = runTest {
        manager.onTutorialCompleted()

        assertTrue(reporter.wasUnlocked(R.string.achievement_welcome_to_tarati))
    }

    // ── setSteps cache guard ──────────────────────────────────────────────────

    @Test
    fun setSteps_doesNotReportWhenNoCacheProgress(): TestResult = runTest {
        coEvery { repository.getCachedSteps(R.string.achievement_the_flipper) } returns 10
        coEvery { repository.incrementTotalCaptures(any()) } returns 10 // same as cached

        val oldState = GameState.createGameState {
            setTurn(WHITE)
            setCob(C1, WHITE)
            setCob(B4, BLACK)
        }
        val move = Move(C1 to B1)
        manager.onMoveApplied(move, oldState, oldState.applyMove(move))

        assertFalse(R.string.achievement_the_flipper in reporter.stepUpdates)
    }

    // ── reconcileAchievements (bidireccional) ─────────────────────────────────

    /** JWT falso cuyo payload decodifica a `{"sub": userId}` (firma irrelevante). */
    private fun jwtWithSub(userId: String): String {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"$userId"}""".toByteArray())
        return "header.$payload.sig"
    }

    private fun standardUnlocked(gplayId: String) = AchievementSnapshot(
        id = gplayId,
        type = Achievement.TYPE_STANDARD,
        state = Achievement.STATE_UNLOCKED,
        currentSteps = 0,
    )

    private fun incremental(gplayId: String, steps: Int) = AchievementSnapshot(
        id = gplayId,
        type = Achievement.TYPE_INCREMENTAL,
        state = Achievement.STATE_REVEALED,
        currentSteps = steps,
    )

    @Test
    fun reconcile_noToken_isNoOp(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns null

        manager.reconcileAchievements()

        assertFalse(reporter.wasUnlocked(R.string.achievement_mit))
        coVerify(exactly = 0) { syncService.getAll(any()) }
    }

    @Test
    fun reconcile_serverUnlocked_unlocksInGooglePlay(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        every { context.getString(R.string.achievement_mit) } returns "gplay_mit"
        reporter.snapshots = emptyList()
        coEvery { syncService.getAll(any()) } returns Result.success(
            listOf(ServerAchievementDto("mit", unlockedAt = "2026-01-01T00:00:00Z", currentSteps = 0)),
        )

        manager.reconcileAchievements()

        assertTrue(reporter.wasUnlocked(R.string.achievement_mit))
    }

    @Test
    fun reconcile_gplayUnlocked_pushesToServer(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        every { context.getString(R.string.achievement_mit) } returns "gplay_mit"
        reporter.snapshots = listOf(standardUnlocked("gplay_mit"))
        coEvery { syncService.getAll(any()) } returns Result.success(emptyList())

        manager.reconcileAchievements()

        coVerify { syncService.unlock(any(), AchievementId.MIT) }
    }

    @Test
    fun reconcile_incremental_serverHigher_pushesToGooglePlay(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        every { context.getString(R.string.achievement_the_flipper) } returns "gplay_flipper"
        reporter.snapshots = listOf(incremental("gplay_flipper", steps = 10))
        coEvery { syncService.getAll(any()) } returns Result.success(
            listOf(ServerAchievementDto("the_flipper", unlockedAt = null, currentSteps = 30)),
        )

        manager.reconcileAchievements()

        assertEquals(30, reporter.stepUpdates[R.string.achievement_the_flipper])
    }

    @Test
    fun reconcile_incremental_gplayHigher_pushesToServer(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        every { context.getString(R.string.achievement_the_flipper) } returns "gplay_flipper"
        reporter.snapshots = listOf(incremental("gplay_flipper", steps = 40))
        coEvery { syncService.getAll(any()) } returns Result.success(
            listOf(ServerAchievementDto("the_flipper", unlockedAt = null, currentSteps = 20)),
        )

        manager.reconcileAchievements()

        coVerify { syncService.progress(any(), AchievementId.THE_FLIPPER, 40) }
    }

    @Test
    fun reconcile_serverPalette_unlocksLocalPalette(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        every { context.getString(R.string.achievement_halloween_theme) } returns "gplay_halloween"
        reporter.snapshots = emptyList()
        coEvery { syncService.getAll(any()) } returns Result.success(
            listOf(ServerAchievementDto("halloween_theme", unlockedAt = "2026-01-01T00:00:00Z", currentSteps = 0)),
        )

        manager.reconcileAchievements()

        assertTrue(reporter.wasUnlocked(R.string.achievement_halloween_theme))
        coVerify { repository.unlockHalloween() }
    }

    @Test
    fun reconcile_accountMismatch_doesNotPushGooglePlayToServer(): TestResult = runTest {
        coEvery { authRepository.getToken() } returns jwtWithSub("u1")
        coEvery { repository.getLastReconciledUserId() } returns "someone_else"
        every { context.getString(R.string.achievement_mit) } returns "gplay_mit"
        reporter.snapshots = listOf(standardUnlocked("gplay_mit"))
        coEvery { syncService.getAll(any()) } returns Result.success(emptyList())

        manager.reconcileAchievements()

        coVerify(exactly = 0) { syncService.unlock(any(), AchievementId.MIT) }
    }
}