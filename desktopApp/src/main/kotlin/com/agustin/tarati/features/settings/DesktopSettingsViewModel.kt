package com.agustin.tarati.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.services.achievements.AchievementId
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.services.billing.effectiveOwnedProducts
import com.agustin.tarati.services.billing.lockedPaletteNames
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypeManager
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.PaletteList
import com.agustin.tarati.ui.theme.PaletteManager
import com.agustin.tarati.ui.theme.SeasonalThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.agustin.tarati.ui.theme.availablePalettes as allAvailablePalettes

/**
 * SettingsViewModel para Desktop.
 *
 * Versión simplificada de [SettingsViewModel] que no depende de:
 * - [AchievementsRepository] (Google Play Games — Android only)
 * - [IBillingManager] (Google Play Billing — Android only)
 *
 * En Desktop:
 * - Las paletas de logros (Halloween, Christmas, Aurora, Ember) se filtran
 *   según el estado del servidor de Tarati vía [IAchievementsManager.unlockedPaletteAchievements].
 * - No hay paletas bloqueadas por IAP.
 * - launchPurchaseFlow es no-op.
 * - purchasedProductIds es un StateFlow vacío.
 */
class DesktopSettingsViewModel(
    private val repository: SettingsRepository,
    private val achievementsManager: IAchievementsManager,
    entitlementsRepository: EntitlementsRepository,
) : ViewModel(), ISettingsViewModel {

    private val _hasTutorialBeenSeen = MutableStateFlow(false)
    override val hasTutorialBeenSeen: StateFlow<Boolean> = _hasTutorialBeenSeen

    // Ownership cross-platform leído del servidor (Desktop no tiene billing local), expandido con la
    // regla supporter (C4): el entitlement `supporter` desbloquea todo el contenido premium
    // (paletas + piezas). Sin la expansión, las piezas premium quedaban bloqueadas pese al supporter.
    override val purchasedProductIds: StateFlow<Set<String>> = entitlementsRepository.entitlements
        .map { effectiveOwnedProducts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── settingsState ─────────────────────────────────────────────────────────

    private val boardVisualStateFlow = combine(
        repository.labelsVisibility,
        repository.verticesVisibility,
        repository.edgesVisibility,
        repository.regionsVisibility,
        repository.perimeterVisibility,
    ) { labels, vertices, edges, regions, perimeter ->
        BoardVisualState(
            labelsVisibles = labels,
            verticesVisibles = vertices,
            edgesVisibles = edges,
            regionsVisibles = regions,
            perimeterVisible = perimeter,
        )
    }.combine(
        combine(repository.animateEffects, repository.conversionAnimationStyle) { animate, style ->
            animate to style
        },
    ) { boardVisual, (animate, style) ->
        boardVisual.copy(animateEffects = animate, conversionAnimationStyle = style)
    }

    private val soundStateFlow = combine(
        repository.soundEnabled,
        repository.soundVolume,
    ) { enabled, volume ->
        SoundState(soundEnabled = enabled, soundVolume = volume)
    }

    private val timeSettingsFlow = combine(
        repository.timeControl,
        repository.preMovesEnabled,
    ) { tc, preMoves -> tc to preMoves }

    override val settingsState: StateFlow<SettingsState> = combine(
        combine(repository.isDarkTheme, repository.difficulty, repository.difficultyBlack) { dark, diff, diffBlack ->
            Triple(dark, diff, diffBlack)
        },
        combine(repository.userName, repository.language, repository.palette) { user, lang, palette ->
            Triple(user, lang, palette)
        },
        combine(boardVisualStateFlow, soundStateFlow) { board, sound -> board to sound },
        combine(repository.pieceTypeId, timeSettingsFlow) { pieceTypeId, timeSettings ->
            pieceTypeId to timeSettings
        },
    ) { (dark, diff, diffBlack), (user, lang, palette), (board, sound), (pieceTypeId, timeSettings) ->
        val (timeControl, preMovesEnabled) = timeSettings
        SettingsState(
            appTheme = if (dark) AppTheme.MODE_NIGHT else AppTheme.MODE_AUTO,
            difficulty = diff,
            difficultyBlack = diffBlack,
            userName = user,
            language = lang,
            boardVisualState = board,
            palette = palette,
            soundState = sound,
            pieceTypeId = pieceTypeId,
            timeControl = timeControl,
            preMovesEnabled = preMovesEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(),
    )

    // ── Paletas — filtradas por logros desbloqueados ──────────────────────────

    override val availablePalettes: StateFlow<PaletteList> = MutableStateFlow(
        PaletteList(items = allAvailablePalettes)
    )

    override val allPalettesForSelector: StateFlow<PaletteList> =
        achievementsManager.unlockedPaletteAchievements
            .map { unlocked -> buildPaletteList(unlocked) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = buildPaletteList(emptySet()),
            )

    // Gate supporter (C4): Gilded queda bloqueada salvo que el usuario sea supporter o la posea.
    override val lockedPalettes: StateFlow<LockedPalettes> = entitlementsRepository.entitlements
        .map { LockedPalettes(lockedPaletteNames(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LockedPalettes.None)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch { achievementsManager.syncFromServer() }

        viewModelScope.launch {
            repository.palette.collect { paletteName ->
                val palette = allAvailablePalettes.find { it.name == paletteName }
                    ?: allAvailablePalettes.first()
                PaletteManager.setPalette(palette)
            }
        }

        viewModelScope.launch {
            repository.pieceTypeId.collect { id ->
                PieceTypeManager.setPieceType(PieceTypes.findById(id))
            }
        }

        viewModelScope.launch {
            repository.tutorialSeen.collect { seen ->
                _hasTutorialBeenSeen.value = seen
            }
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    override fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(enabled) }
    }

    override fun setUserName(name: String) {
        viewModelScope.launch { repository.setUserName(name) }
    }

    override fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    override fun setPalette(paletteName: String) {
        viewModelScope.launch { repository.setPalette(paletteName) }
    }

    override fun setDifficulty(newDifficulty: Difficulty) {
        viewModelScope.launch { repository.setDifficulty(newDifficulty) }
    }

    override fun setDifficultyBlack(newDifficulty: Difficulty) {
        viewModelScope.launch { repository.setDifficultyBlack(newDifficulty) }
    }

    override fun setLabelsVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setLabelsVisibility(visible) }
    }

    override fun setVerticesVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setVerticesVisibility(visible) }
    }

    override fun setEdgesVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setEdgesVisibility(visible) }
    }

    override fun setRegionsVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setRegionsVisibility(visible) }
    }

    override fun setPerimeterVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setPerimeterVisibility(visible) }
    }

    override fun setAnimateEffects(animate: Boolean) {
        viewModelScope.launch { repository.setAnimateEffects(animate) }
    }

    override fun setConversionAnimationStyle(style: ConversionAnimationStyle) {
        viewModelScope.launch { repository.setConversionAnimationStyle(style) }
    }

    override fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    override fun setSoundVolume(volume: Float) {
        viewModelScope.launch { repository.setSoundVolume(volume) }
    }

    override fun markTutorialSeen() {
        viewModelScope.launch { repository.setTutorialSeen(true) }
    }

    override fun setPieceType(pieceTypeId: String) {
        viewModelScope.launch { repository.setPieceTypeId(pieceTypeId) }
    }

    /** No-op en Desktop — no hay Google Play Billing. */
    override fun launchPurchaseFlow(productId: String): Unit = Unit

    override fun setTimeControl(mode: TimeControlMode) {
        viewModelScope.launch { repository.setTimeControl(mode) }
    }

    override fun setPreMovesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setPreMovesEnabled(enabled) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPaletteList(unlocked: Set<AchievementId>): PaletteList {
        val halloween = AchievementId.HALLOWEEN_THEME in unlocked
        val christmas = AchievementId.CHRISTMAS_THEME in unlocked
        val aurora = AchievementId.THE_FIRST_LIGHT in unlocked
        val ember = AchievementId.THE_DARK_SIDE in unlocked
        return PaletteList(items = allAvailablePalettes.filter { palette ->
            SeasonalThemeManager.isPaletteAvailable(palette.name, halloween, christmas, aurora, ember)
        })
    }
}
