@file:OptIn(ExperimentalCoroutinesApi::class)

package com.agustin.tarati.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.services.achievements.IAchievementsRepository
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.IBillingManager
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.services.billing.PaletteProducts
import com.agustin.tarati.services.billing.PurchaseResult
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypeManager
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.ClassicPalette
import com.agustin.tarati.ui.theme.GildedPalette
import com.agustin.tarati.ui.theme.SeasonalThemeManager
import features.settings.AndroidSettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

class SettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsViewModel
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockAchievementsRepository: IAchievementsRepository
    private lateinit var mockBillingManager: IBillingManager
    private lateinit var mockEntitlementsRepository: EntitlementsRepository

    /**
     * Configura respuestas por defecto para TODOS los flows del SettingsRepository.
     * Cualquier flow no mockeado causará MockKException en tiempo de ejecución.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Limpia cualquier instancia Koin dejada por una clase de test anterior en el mismo JVM.
        // Sin esto, si MainViewModelTest (u otra clase) deja Koin levantado, startKoin falla
        // en cascade para todos los tests de esta clase.
        if (GlobalContext.getOrNull() != null) stopKoin()

        mockSettingsRepository = mockk()
        mockAchievementsRepository = mockk()
        mockBillingManager = mockk(relaxed = true)
        mockEntitlementsRepository = mockk(relaxed = true)
        every { mockEntitlementsRepository.entitlements } returns MutableStateFlow(emptySet())

        // ── SettingsRepository flows ───────────────────────────────────────────
        coEvery { mockSettingsRepository.isDarkTheme } returns MutableStateFlow(false)
        every { mockSettingsRepository.appTheme } returns MutableStateFlow(AppTheme.MODE_AUTO)
        coEvery { mockSettingsRepository.difficulty } returns MutableStateFlow(Difficulty.DEFAULT)
        coEvery { mockSettingsRepository.difficultyBlack } returns MutableStateFlow(Difficulty.DEFAULT)
        coEvery { mockSettingsRepository.difficultyWhite } returns MutableStateFlow(Difficulty.DEFAULT)
        coEvery { mockSettingsRepository.userName } returns MutableStateFlow("")
        coEvery { mockSettingsRepository.language } returns MutableStateFlow(AppLanguage.SPANISH)
        coEvery { mockSettingsRepository.labelsVisibility } returns MutableStateFlow(false)
        coEvery { mockSettingsRepository.verticesVisibility } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.edgesVisibility } returns MutableStateFlow(false)
        coEvery { mockSettingsRepository.regionsVisibility } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.perimeterVisibility } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.animateEffects } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.conversionAnimationStyle } returns
                MutableStateFlow(ConversionAnimationStyle.SURPRISE)
        coEvery { mockSettingsRepository.palette } returns MutableStateFlow(ClassicPalette.name)
        coEvery { mockSettingsRepository.soundEnabled } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.soundVolume } returns MutableStateFlow(0.8f)
        coEvery { mockSettingsRepository.tutorialSeen } returns MutableStateFlow(false)
        coEvery { mockSettingsRepository.mpTutorialSeen } returns MutableStateFlow(false)
        coEvery { mockSettingsRepository.seasonalAutoAppliedDate } returns MutableStateFlow("")
        coEvery { mockSettingsRepository.preSeasonalPalette } returns MutableStateFlow("")
        coEvery { mockSettingsRepository.pieceTypeId } returns MutableStateFlow(PieceTypes.default.id)

        // Game session flows
        coEvery { mockSettingsRepository.whiteIsAI } returns MutableStateFlow(false)
        coEvery { mockSettingsRepository.blackIsAI } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.boardOrientation } returns
                MutableStateFlow(BoardOrientation.PORTRAIT_WHITE.name)
        coEvery { mockSettingsRepository.isManuallyRotated } returns MutableStateFlow(false)

        // ── AchievementsRepository flows ───────────────────────────────────────
        coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
        coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
        coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
        coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

        // ── IBillingManager stubs ──────────────────────────────────────────────
        // purchasedProductIds: StateFlow vacío por defecto — sin compras previas.
        // purchaseResult: SharedFlow vacío — sin compras en curso.
        // Ambos se pueden sobreescribir en tests específicos.
        every { mockBillingManager.purchasedProductIds } returns MutableStateFlow(emptySet())
        every { mockBillingManager.purchaseResult } returns
                kotlinx.coroutines.flow.MutableSharedFlow()

        // ── Time control ───────────────────────────────────────────────────────
        coEvery { mockSettingsRepository.timeControl } returns MutableStateFlow(TimeControlMode.Unlimited)
        coEvery { mockSettingsRepository.preMovesEnabled } returns MutableStateFlow(true)
        coEvery { mockSettingsRepository.showEvaluationBar } returns MutableStateFlow(true)

        startKoin {
            modules(
                module {
                    single { mockSettingsRepository }
                    single { mockAchievementsRepository }
                    single<IBillingManager> { mockBillingManager }
                },
            )
        }
        viewModel = AndroidSettingsViewModel(
            mockSettingsRepository,
            mockAchievementsRepository,
            mockBillingManager,
            mockEntitlementsRepository
        )
    }

    @After
    fun tearDown() {
        // Guard with isInitialized: if setUp() failed, viewModel was never assigned and
        // calling .viewModelScope would throw UninitializedPropertyAccessException,
        // preventing stopKoin() from running and poisoning all subsequent tests.
        if (::viewModel.isInitialized) {
            (viewModel as ViewModel).viewModelScope.cancel()
        }
        stopKoin()
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initialState_hasDefaultValues(): TestResult =
        runTest {
            advanceTimeBy(100.milliseconds)

            val state = viewModel.settingsState.value

            assertEquals("Initial theme should be AUTO", AppTheme.MODE_AUTO, state.appTheme)
            assertEquals("Initial difficulty should be DEFAULT", Difficulty.DEFAULT, state.difficulty)
            assertEquals("Initial difficultyBlack should be DEFAULT", Difficulty.DEFAULT, state.difficultyBlack)
            assertEquals("Initial username should be empty", "", state.userName)
            assertEquals("Initial language should be SPANISH", AppLanguage.SPANISH, state.language)
            assertFalse("Initial labels should be hidden", state.boardVisualState.labelsVisibles)
            assertTrue("Initial vertices should be visible", state.boardVisualState.verticesVisibles)
            assertFalse("Initial edges should be hidden", state.boardVisualState.edgesVisibles)
            assertTrue("Initial regions should be visible", state.boardVisualState.regionsVisibles)
            assertTrue("Initial perimeter should be visible", state.boardVisualState.perimeterVisible)
            assertTrue("Initial animate effects should be enabled", state.boardVisualState.animateEffects)
            assertEquals(
                "Initial conversion animation style should be SURPRISE",
                ConversionAnimationStyle.SURPRISE,
                state.boardVisualState.conversionAnimationStyle,
            )
            assertEquals(
                "Initial pieceTypeId should be circle (default)",
                PieceTypes.default.id,
                state.pieceTypeId,
            )
            assertEquals(TimeControlMode.Unlimited, state.timeControl)
            assertTrue(state.preMovesEnabled)
        }

    @Test
    fun initialState_includesAllBoardStateProperties(): TestResult =
        runTest {
            val state = viewModel.settingsState.value

            assertFalse("Initial labels should be hidden", state.boardVisualState.labelsVisibles)
            assertTrue("Initial vertices should be visible", state.boardVisualState.verticesVisibles)
            assertFalse("Initial edges should be hidden", state.boardVisualState.edgesVisibles)
            assertTrue("Initial regions should be visible", state.boardVisualState.regionsVisibles)
            assertTrue("Initial perimeter should be visible", state.boardVisualState.perimeterVisible)
            assertTrue("Initial animate effects should be enabled", state.boardVisualState.animateEffects)
            assertEquals(
                "Initial conversion animation style should be SURPRISE",
                ConversionAnimationStyle.SURPRISE,
                state.boardVisualState.conversionAnimationStyle,
            )
        }

    // ── Repository save calls ─────────────────────────────────────────────────

    @Test
    fun toggleDarkTheme_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setDarkTheme(any()) } returns Unit

            viewModel.toggleDarkTheme(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setDarkTheme(true) }
        }

    @Test
    fun setUserName_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setUserName(any()) } returns Unit

            viewModel.setUserName("Player A")

            coVerify { mockSettingsRepository.setUserName("Player A") }
        }

    @Test
    fun setLanguage_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setLanguage(any()) } returns Unit

            viewModel.setLanguage(AppLanguage.ENGLISH)

            coVerify { mockSettingsRepository.setLanguage(AppLanguage.ENGLISH) }
        }

    @Test
    fun setLabelsVisibility_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setLabelsVisibility(any()) } returns Unit

            viewModel.setLabelsVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setLabelsVisibility(true) }
        }

    @Test
    fun setVerticesVisibility_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setVerticesVisibility(any()) } returns Unit

            viewModel.setVerticesVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setVerticesVisibility(true) }
        }

    @Test
    fun setEdgesVisibility_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setEdgesVisibility(any()) } returns Unit

            viewModel.setEdgesVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setEdgesVisibility(true) }
        }

    @Test
    fun setRegionsVisibility_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setRegionsVisibility(any()) } returns Unit

            viewModel.setRegionsVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setRegionsVisibility(true) }
        }

    @Test
    fun setPerimeterVisibility_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPerimeterVisibility(any()) } returns Unit

            viewModel.setPerimeterVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPerimeterVisibility(true) }
        }

    @Test
    fun setAnimateEffects_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setAnimateEffects(any()) } returns Unit

            viewModel.setAnimateEffects(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setAnimateEffects(true) }
        }

    @Test
    fun setDifficultyBlack_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setDifficultyBlack(any()) } returns Unit

            viewModel.setDifficultyBlack(Difficulty.HARD)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setDifficultyBlack(Difficulty.HARD) }
        }

    // ── conversionAnimationStyle ──────────────────────────────────────────────

    @Test
    fun setConversionAnimationStyle_flip_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setConversionAnimationStyle(any()) } returns Unit

            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.FLIP)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.FLIP) }
        }

    @Test
    fun setConversionAnimationStyle_transformation_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setConversionAnimationStyle(any()) } returns Unit

            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.TRANSFORMATION)
            advanceUntilIdle()

            coVerify {
                mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.TRANSFORMATION)
            }
        }

    @Test
    fun setConversionAnimationStyle_surprise_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setConversionAnimationStyle(any()) } returns Unit

            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.SURPRISE)
            advanceUntilIdle()

            coVerify {
                mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.SURPRISE)
            }
        }

    @Test
    fun conversionAnimationStyle_reflectsRepositoryValue(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.conversionAnimationStyle } returns
                    MutableStateFlow(ConversionAnimationStyle.FLIP)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            assertEquals(
                "ViewModel should reflect FLIP from repository",
                ConversionAnimationStyle.FLIP,
                testViewModel.settingsState.value.boardVisualState.conversionAnimationStyle,
            )
        }

    @Test
    fun conversionAnimationStyle_multipleChanges_triggerRepositoryCalls(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setConversionAnimationStyle(any()) } returns Unit

            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.FLIP)
            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.TRANSFORMATION)
            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.SURPRISE)
            advanceUntilIdle()

            coVerify(exactly = 3) { mockSettingsRepository.setConversionAnimationStyle(any()) }
            coVerify { mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.FLIP) }
            coVerify {
                mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.TRANSFORMATION)
            }
            coVerify {
                mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.SURPRISE)
            }
        }

    // ── pieceType ─────────────────────────────────────────────────────────────

    @Test
    fun setPieceType_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            viewModel.setPieceType(PieceTypes.Hexagon.id)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPieceTypeId(PieceTypes.Hexagon.id) }
        }

    @Test
    fun setPieceType_triangle_savesSetting(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            viewModel.setPieceType(PieceTypes.Triangle.id)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPieceTypeId(PieceTypes.Triangle.id) }
        }

    @Test
    fun pieceTypeId_reflectsRepositoryValue(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.pieceTypeId } returns
                    MutableStateFlow(PieceTypes.Hexagon.id)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            assertEquals(
                "ViewModel should reflect hexagon id from repository",
                PieceTypes.Hexagon.id,
                testViewModel.settingsState.value.pieceTypeId,
            )
        }

    @Test
    fun pieceTypeId_unknownId_defaultsToCircleInManager(): TestResult =
        runTest {
            // Un id desconocido en DataStore nunca rompe el estado;
            // PieceTypes.findById devuelve el círculo por defecto.
            coEvery { mockSettingsRepository.pieceTypeId } returns
                    MutableStateFlow("unknown_shape_id")

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            assertEquals(
                "Unknown id should fall back to circle in PieceTypeManager",
                PieceTypes.default,
                PieceTypeManager.currentPieceType,
            )
            // settingsState guarda el id crudo del repositorio (no lo normaliza).
            assertEquals(
                "SettingsState stores the raw id from DataStore",
                "unknown_shape_id",
                testViewModel.settingsState.value.pieceTypeId,
            )
        }

    // ── Multiple changes ──────────────────────────────────────────────────────

    @Test
    fun settingsChanges_triggerRepositoryCalls(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setDarkTheme(any()) } returns Unit
            coEvery { mockSettingsRepository.setUserName(any()) } returns Unit
            coEvery { mockSettingsRepository.setLanguage(any()) } returns Unit
            coEvery { mockSettingsRepository.setLabelsVisibility(any()) } returns Unit

            viewModel.toggleDarkTheme(true)
            viewModel.setUserName("Player A")
            viewModel.setLanguage(AppLanguage.ENGLISH)
            viewModel.setLabelsVisibility(true)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setDarkTheme(true) }
            coVerify { mockSettingsRepository.setUserName("Player A") }
            coVerify { mockSettingsRepository.setLanguage(AppLanguage.ENGLISH) }
            coVerify { mockSettingsRepository.setLabelsVisibility(true) }
        }

    @Test
    fun multipleSettingsChanges_triggerMultipleRepositoryCalls(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setDarkTheme(any()) } returns Unit
            coEvery { mockSettingsRepository.setUserName(any()) } returns Unit
            coEvery { mockSettingsRepository.setLanguage(any()) } returns Unit

            viewModel.toggleDarkTheme(true)
            viewModel.toggleDarkTheme(false)
            viewModel.setUserName("Player A")
            viewModel.setUserName("")
            viewModel.setLanguage(AppLanguage.ENGLISH)
            viewModel.setLanguage(AppLanguage.SPANISH)

            coVerify(exactly = 2) { mockSettingsRepository.setDarkTheme(any()) }
            coVerify(exactly = 2) { mockSettingsRepository.setUserName(any()) }
            coVerify(exactly = 2) { mockSettingsRepository.setLanguage(any()) }
            coVerify { mockSettingsRepository.setDarkTheme(true) }
            coVerify { mockSettingsRepository.setDarkTheme(false) }
            coVerify { mockSettingsRepository.setUserName("Player A") }
            coVerify { mockSettingsRepository.setUserName("") }
            coVerify { mockSettingsRepository.setLanguage(AppLanguage.ENGLISH) }
            coVerify { mockSettingsRepository.setLanguage(AppLanguage.SPANISH) }
        }

    @Test
    fun multipleVisibilityChanges_triggerRepositoryCalls(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setVerticesVisibility(any()) } returns Unit
            coEvery { mockSettingsRepository.setEdgesVisibility(any()) } returns Unit
            coEvery { mockSettingsRepository.setRegionsVisibility(any()) } returns Unit
            coEvery { mockSettingsRepository.setPerimeterVisibility(any()) } returns Unit
            coEvery { mockSettingsRepository.setAnimateEffects(any()) } returns Unit
            coEvery { mockSettingsRepository.setConversionAnimationStyle(any()) } returns Unit

            viewModel.setVerticesVisibility(false)
            viewModel.setEdgesVisibility(true)
            viewModel.setRegionsVisibility(true)
            viewModel.setPerimeterVisibility(false)
            viewModel.setAnimateEffects(false)
            viewModel.setConversionAnimationStyle(ConversionAnimationStyle.FLIP)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setVerticesVisibility(false) }
            coVerify { mockSettingsRepository.setEdgesVisibility(true) }
            coVerify { mockSettingsRepository.setRegionsVisibility(true) }
            coVerify { mockSettingsRepository.setPerimeterVisibility(false) }
            coVerify { mockSettingsRepository.setAnimateEffects(false) }
            coVerify { mockSettingsRepository.setConversionAnimationStyle(ConversionAnimationStyle.FLIP) }
        }

    // ── Theme logic ───────────────────────────────────────────────────────────

    @Test
    fun themeLogic_correctlyConvertsBooleanToTheme() {
        assertEquals(
            "false should convert to AUTO",
            AppTheme.MODE_AUTO,
            convertDarkThemeToAppTheme(false),
        )
        assertEquals(
            "true should convert to NIGHT",
            AppTheme.MODE_NIGHT,
            convertDarkThemeToAppTheme(true),
        )
    }

    // ── setAppTheme ───────────────────────────────────────────────────────────

    @Test
    fun setAppTheme_night_callsRepositorySetAppTheme(): TestResult = runTest {
        coEvery { mockSettingsRepository.setAppTheme(any()) } returns Unit

        viewModel.setAppTheme(AppTheme.MODE_NIGHT)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setAppTheme(AppTheme.MODE_NIGHT) }
    }

    @Test
    fun setAppTheme_auto_callsRepositorySetAppTheme(): TestResult = runTest {
        coEvery { mockSettingsRepository.setAppTheme(any()) } returns Unit

        viewModel.setAppTheme(AppTheme.MODE_AUTO)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setAppTheme(AppTheme.MODE_AUTO) }
    }

    @Test
    fun settingsState_appTheme_reflectsRepositoryAppThemeFlow(): TestResult = runTest {
        every { mockSettingsRepository.appTheme } returns MutableStateFlow(AppTheme.MODE_NIGHT)

        val testViewModel =
            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
        advanceUntilIdle()

        assertEquals(
            "settingsState.appTheme should reflect MODE_NIGHT from repository",
            AppTheme.MODE_NIGHT,
            testViewModel.settingsState.value.appTheme,
        )
    }

    // ── Repository state reflection ───────────────────────────────────────────

    @Test
    fun viewModelReflectsRepositoryState(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.isDarkTheme } returns MutableStateFlow(true)
            coEvery { mockSettingsRepository.difficulty } returns MutableStateFlow(Difficulty.HARD)
            coEvery { mockSettingsRepository.difficultyBlack } returns MutableStateFlow(Difficulty.HARD)
            coEvery { mockSettingsRepository.difficultyWhite } returns MutableStateFlow(Difficulty.HARD)
            coEvery { mockSettingsRepository.userName } returns MutableStateFlow("Player A")
            coEvery { mockSettingsRepository.language } returns MutableStateFlow(AppLanguage.ENGLISH)
            coEvery { mockSettingsRepository.labelsVisibility } returns MutableStateFlow(true)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            assertNotNull("ViewModel should be created", testViewModel)
            assertNotNull("Settings state should be available", testViewModel.settingsState)
        }

    // ── Tutorial ──────────────────────────────────────────────────────────────

    @Test
    fun markTutorialSeen_callsSetTutorialSeenTrue(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setTutorialSeen(any()) } returns Unit

            viewModel.markTutorialSeen()
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setTutorialSeen(true) }
        }

    @Test
    fun hasTutorialBeenSeen_reflectsRepositoryOnInit() {
        assertFalse(
            "hasTutorialBeenSeen should reflect repository value (false) after eager collection on init",
            viewModel.hasTutorialBeenSeen.value,
        )
    }

    @Test
    fun hasTutorialBeenSeen_reflectsRepositoryTutorialSeenFlow(): TestResult =
        runTest {
            val tutorialSeenFlow = MutableStateFlow(false)
            coEvery { mockSettingsRepository.tutorialSeen } returns tutorialSeenFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            assertFalse(
                "hasTutorialBeenSeen should be false when repository emits false",
                testViewModel.hasTutorialBeenSeen.value,
            )

            tutorialSeenFlow.value = true

            assertTrue(
                "hasTutorialBeenSeen should be true after repository emits true",
                testViewModel.hasTutorialBeenSeen.value,
            )
        }

    // ── Seasonal themes ───────────────────────────────────────────────────────

    @Test
    fun setPalette_nonSeasonal_onNonSeasonalDay_onlySavesPalette(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            viewModel.setPalette("Nature")
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPalette("Nature") }
            coVerify(exactly = 0) { mockSettingsRepository.setPreSeasonalPalette(any()) }
        }

    @Test
    fun setPalette_seasonal_doesNotUpdatePreSeasonal(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            viewModel.setPalette(SeasonalThemeManager.HALLOWEEN_PALETTE)
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPalette(SeasonalThemeManager.HALLOWEEN_PALETTE) }
            coVerify(exactly = 0) { mockSettingsRepository.setPreSeasonalPalette(any()) }
        }

    @Test
    fun availablePalettes_initiallyContainsNonSeasonalPalettes(): TestResult =
        runTest {
            advanceUntilIdle()

            val names = viewModel.availablePalettes.value.items.map { it.name }

            assertFalse(
                "Halloween palette should not be available by default",
                names.contains(SeasonalThemeManager.HALLOWEEN_PALETTE),
            )
            assertFalse(
                "Christmas palette should not be available by default",
                names.contains(SeasonalThemeManager.CHRISTMAS_PALETTE),
            )
        }

    @Test
    fun availablePalettes_includesHalloweenWhenUnlocked(): TestResult =
        runTest {
            val halloweenFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns halloweenFlow
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            halloweenFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.availablePalettes.value.items.map { it.name }
            assertTrue(
                "Halloween palette should be available after unlock",
                names.contains(SeasonalThemeManager.HALLOWEEN_PALETTE),
            )
        }

    @Test
    fun availablePalettes_includesChristmasWhenUnlocked(): TestResult =
        runTest {
            val christmasFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.christmasUnlocked } returns christmasFlow
            coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            christmasFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.availablePalettes.value.items.map { it.name }
            assertTrue(
                "Christmas palette should be available after unlock",
                names.contains(SeasonalThemeManager.CHRISTMAS_PALETTE),
            )
        }

    @Test
    fun availablePalettes_doesNotIncludeAuroraByDefault(): TestResult =
        runTest {
            advanceUntilIdle()

            val names = viewModel.availablePalettes.value.items.map { it.name }

            assertFalse(
                "Aurora palette should not be available by default",
                names.contains(SeasonalThemeManager.AURORA_PALETTE),
            )
        }

    @Test
    fun availablePalettes_doesNotIncludeEmberByDefault(): TestResult =
        runTest {
            advanceUntilIdle()

            val names = viewModel.availablePalettes.value.items.map { it.name }

            assertFalse(
                "Ember palette should not be available by default",
                names.contains(SeasonalThemeManager.EMBER_PALETTE),
            )
        }

    @Test
    fun availablePalettes_includesAuroraWhenUnlocked(): TestResult =
        runTest {
            val auroraFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns auroraFlow
            coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            auroraFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.availablePalettes.value.items.map { it.name }
            assertTrue(
                "Aurora palette should be available after unlock",
                names.contains(SeasonalThemeManager.AURORA_PALETTE),
            )
        }

    @Test
    fun availablePalettes_includesEmberWhenUnlocked(): TestResult =
        runTest {
            val emberFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.emberUnlocked } returns emberFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            emberFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.availablePalettes.value.items.map { it.name }
            assertTrue(
                "Ember palette should be available after unlock",
                names.contains(SeasonalThemeManager.EMBER_PALETTE),
            )
        }

    // ── Billing — purchasedProductIds ─────────────────────────────────────────

    @Test
    fun purchasedProductIds_initiallyEmpty(): TestResult =
        runTest {
            advanceUntilIdle()

            assertTrue(
                "purchasedProductIds should be empty initially",
                viewModel.purchasedProductIds.value.isEmpty(),
            )
        }

    @Test
    fun purchasedProductIds_reflectsBillingManagerState(): TestResult =
        runTest {
            val idsFlow = MutableStateFlow<Set<String>>(emptySet())
            every { mockBillingManager.purchasedProductIds } returns idsFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            assertTrue(
                "Should be empty before any purchase",
                testViewModel.purchasedProductIds.value.isEmpty(),
            )

            idsFlow.value = setOf("piece_hexagon")
            advanceUntilIdle()

            assertTrue(
                "Should contain piece_hexagon after purchase",
                testViewModel.purchasedProductIds.value.contains("piece_hexagon"),
            )
        }

    // ── Billing — launchPurchaseFlow ──────────────────────────────────────────

    @Test
    fun launchPurchaseFlow_delegatesToBillingManager() {
        viewModel.launchPurchaseFlow("piece_hexagon")

        verify { mockBillingManager.launchPurchaseFlow("piece_hexagon") }
    }

    // ── Billing — auto-activate on purchase success ───────────────────────────

    @Test
    fun purchaseResult_success_activatesPieceType(): TestResult =
        runTest {
            val purchaseResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<PurchaseResult>()
            every { mockBillingManager.purchaseResult } returns purchaseResultFlow
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
            advanceUntilIdle()

            purchaseResultFlow.emit(PurchaseResult.Success("piece_hexagon"))
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPieceTypeId(PieceTypes.Hexagon.id) }
        }

    @Test
    fun purchaseResult_successUnknownProduct_doesNotActivateAnyPieceType(): TestResult =
        runTest {
            val purchaseResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<PurchaseResult>()
            every { mockBillingManager.purchaseResult } returns purchaseResultFlow
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
            advanceUntilIdle()

            purchaseResultFlow.emit(PurchaseResult.Success("unknown_product_id"))
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPieceTypeId(any()) }
        }

    @Test
    fun purchaseResult_cancelled_doesNotActivateAnyPieceType(): TestResult =
        runTest {
            val purchaseResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<PurchaseResult>()
            every { mockBillingManager.purchaseResult } returns purchaseResultFlow
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
            advanceUntilIdle()

            purchaseResultFlow.emit(PurchaseResult.Cancelled)
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPieceTypeId(any()) }
        }

    @Test
    fun purchaseResult_error_doesNotActivateAnyPieceType(): TestResult =
        runTest {
            val purchaseResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<PurchaseResult>()
            every { mockBillingManager.purchaseResult } returns purchaseResultFlow
            coEvery { mockSettingsRepository.setPieceTypeId(any()) } returns Unit

            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
            advanceUntilIdle()

            purchaseResultFlow.emit(PurchaseResult.Error(responseCode = 3, debugMessage = "Billing unavailable"))
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPieceTypeId(any()) }
        }

    // ── Billing — Gilded palette ──────────────────────────────────────────────

    @Test
    fun lockedPalettes_initiallyContainsGilded(): TestResult =
        runTest {
            advanceUntilIdle()

            assertTrue(
                "Gilded should be locked initially",
                GildedPalette.name in viewModel.lockedPalettes.value,
            )
        }

    @Test
    fun lockedPalettes_emptyWhenGildedPurchased(): TestResult =
        runTest {
            val idsFlow = MutableStateFlow<Set<String>>(emptySet())
            every { mockBillingManager.purchasedProductIds } returns idsFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            assertTrue("Gilded locked before purchase", GildedPalette.name in testViewModel.lockedPalettes.value)

            idsFlow.value = setOf(PaletteProducts.GILDED)
            advanceUntilIdle()

            assertFalse("Gilded unlocked after purchase", GildedPalette.name in testViewModel.lockedPalettes.value)
            assertEquals("lockedPalettes should be empty", LockedPalettes.None, testViewModel.lockedPalettes.value)
        }

    @Test
    fun allPalettesForSelector_alwaysIncludesGilded(): TestResult =
        runTest {
            advanceUntilIdle()

            val names = viewModel.allPalettesForSelector.value.items.map { it.name }

            assertTrue(
                "Gilded should always appear in the selector",
                names.contains(GildedPalette.name),
            )
        }

    @Test
    fun availablePalettes_doesNotIncludeGildedBeforePurchase(): TestResult =
        runTest {
            advanceUntilIdle()

            val names = viewModel.availablePalettes.value.items.map { it.name }

            assertFalse(
                "Gilded should not be in availablePalettes before purchase",
                names.contains(GildedPalette.name),
            )
        }

    @Test
    fun availablePalettes_includesGildedAfterPurchase(): TestResult =
        runTest {
            val idsFlow = MutableStateFlow<Set<String>>(emptySet())
            every { mockBillingManager.purchasedProductIds } returns idsFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )
            advanceUntilIdle()

            idsFlow.value = setOf(PaletteProducts.GILDED)
            advanceUntilIdle()

            val names = testViewModel.availablePalettes.value.items.map { it.name }
            assertTrue(
                "Gilded should appear in availablePalettes after purchase",
                names.contains(GildedPalette.name),
            )
        }

    @Test
    fun purchaseResult_gildedSuccess_activatesGildedPalette(): TestResult =
        runTest {
            val purchaseResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<PurchaseResult>()
            every { mockBillingManager.purchaseResult } returns purchaseResultFlow
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            AndroidSettingsViewModel(
                mockSettingsRepository,
                mockAchievementsRepository,
                mockBillingManager,
                mockEntitlementsRepository
            )
            advanceUntilIdle()

            purchaseResultFlow.emit(PurchaseResult.Success(PaletteProducts.GILDED))
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPalette(GildedPalette.name) }
        }

    @Test
    fun setTimeControl_savesSetting(): TestResult = runTest {
        coEvery { mockSettingsRepository.setTimeControl(any()) } returns Unit
        viewModel.setTimeControl(TimeControlMode.Fischer(180_000L, 2_000L))
        advanceUntilIdle()
        coVerify { mockSettingsRepository.setTimeControl(TimeControlMode.Fischer(180_000L, 2_000L)) }
    }

    @Test
    fun setPreMovesEnabled_savesSetting(): TestResult = runTest {
        coEvery { mockSettingsRepository.setPreMovesEnabled(any()) } returns Unit
        viewModel.setPreMovesEnabled(false)
        advanceUntilIdle()
        coVerify { mockSettingsRepository.setPreMovesEnabled(false) }
    }

    // ── allPalettesForSelector — achievement unlocks ───────────────────────────

    @Test
    fun allPalettesForSelector_includesHalloweenWhenUnlocked(): TestResult =
        runTest {
            val halloweenFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns halloweenFlow
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            halloweenFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.allPalettesForSelector.value.items.map { it.name }
            assertTrue(
                "allPalettesForSelector should include Halloween when unlocked",
                names.contains(SeasonalThemeManager.HALLOWEEN_PALETTE),
            )
        }

    @Test
    fun allPalettesForSelector_includesAuroraWhenUnlocked(): TestResult =
        runTest {
            val auroraFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns auroraFlow
            coEvery { mockAchievementsRepository.emberUnlocked } returns MutableStateFlow(false)

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            auroraFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.allPalettesForSelector.value.items.map { it.name }
            assertTrue(
                "allPalettesForSelector should include Aurora when unlocked",
                names.contains(SeasonalThemeManager.AURORA_PALETTE),
            )
        }

    @Test
    fun allPalettesForSelector_includesEmberWhenUnlocked(): TestResult =
        runTest {
            val emberFlow = MutableStateFlow(false)
            coEvery { mockAchievementsRepository.halloweenUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.christmasUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.auroraUnlocked } returns MutableStateFlow(false)
            coEvery { mockAchievementsRepository.emberUnlocked } returns emberFlow

            val testViewModel =
                AndroidSettingsViewModel(
                    mockSettingsRepository,
                    mockAchievementsRepository,
                    mockBillingManager,
                    mockEntitlementsRepository
                )

            emberFlow.value = true
            advanceUntilIdle()

            val names = testViewModel.allPalettesForSelector.value.items.map { it.name }
            assertTrue(
                "allPalettesForSelector should include Ember when unlocked",
                names.contains(SeasonalThemeManager.EMBER_PALETTE),
            )
        }

    // ── applySeasonalThemeIfNeeded ────────────────────────────────────────────

    @Test
    fun applySeasonalThemeIfNeeded_appliesThemeWhenSeasonalAndNeverApplied(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit
            coEvery { mockSettingsRepository.setSeasonalAutoAppliedDate(any()) } returns Unit
            coEvery { mockSettingsRepository.setPreSeasonalPalette(any()) } returns Unit

            val seasonalPalette = SeasonalThemeManager.getSeasonalPaletteForToday() ?: return@runTest
            // no es día estacional, skip

            (viewModel as AndroidSettingsViewModel).applySeasonalThemeIfNeeded(
                repository = mockSettingsRepository,
                currentPaletteName = "Classic",
                lastAppliedDate = "",            // nunca aplicado
                preSeasonalPalette = "",
            )
            advanceUntilIdle()

            coVerify { mockSettingsRepository.setPalette(seasonalPalette) }
        }

    @Test
    fun applySeasonalThemeIfNeeded_skipsIfAlreadyAppliedToday(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            val todayKey = SeasonalThemeManager.todayKey()

            (viewModel as AndroidSettingsViewModel).applySeasonalThemeIfNeeded(
                repository = mockSettingsRepository,
                currentPaletteName = "Classic",
                lastAppliedDate = todayKey,      // ya aplicado hoy
                preSeasonalPalette = "",
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPalette(any()) }
        }

    @Test
    fun restorePreSeasonalPalette_doesNothingWhenNoPreSeasonal(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            (viewModel as AndroidSettingsViewModel).restorePreSeasonalPaletteIfNeeded(
                repository = mockSettingsRepository,
                currentPaletteName = SeasonalThemeManager.HALLOWEEN_PALETTE,
                preSeasonalPalette = "",         // sin paleta guardada
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPalette(any()) }
        }

    @Test
    fun restorePreSeasonalPalette_doesNothingWhenCurrentIsNotSeasonal(): TestResult =
        runTest {
            coEvery { mockSettingsRepository.setPalette(any()) } returns Unit

            (viewModel as AndroidSettingsViewModel).restorePreSeasonalPaletteIfNeeded(
                repository = mockSettingsRepository,
                currentPaletteName = "Classic",  // no es estacional
                preSeasonalPalette = "Nature",
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mockSettingsRepository.setPalette(any()) }
        }
}

// Helper para testear la lógica de conversión directamente
private fun convertDarkThemeToAppTheme(isDark: Boolean): AppTheme =
    if (isDark) AppTheme.MODE_NIGHT else AppTheme.MODE_AUTO