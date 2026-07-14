package com.agustin.tarati.features.settings


import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Implementación de [SettingsRepository] para Desktop usando [Preferences].
 *
 * **Ubicación de persistencia**:
 * - Windows: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\agustin\tarati\settings`
 * - macOS: `~/Library/Preferences/com.agustin.tarati.settings.plist`
 * - Linux: `~/.java/.userPrefs/com/agustin/tarati/settings/prefs.xml`
 *
 * **Características**:
 * - Persistencia automática en disco
 * - Reactivo (usa StateFlows)
 * - Type-safe con enums
 * - Compatible con futuras migraciones (valores inválidos → defaults)
 *
 * **Diferencias con Android**:
 * - Android usa DataStore (protobuf)
 * - Desktop usa Preferences (XML/Registry)
 * - Mismo comportamiento, diferente backend
 */
class DesktopSettingsRepository : SettingsRepository {

    private val prefs: Preferences = Preferences.userNodeForPackage(javaClass)
        .node("settings")

    private val logger = getLogger("DesktopSettingsRepository")

    // ── Theme & UI ─────────────────────────────────────────────────────────────

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, false))
    override val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _labelsVisibility = MutableStateFlow(prefs.getBoolean(KEY_LABELS_VISIBLE, false))
    override val labelsVisibility: StateFlow<Boolean> = _labelsVisibility.asStateFlow()

    private val _verticesVisibility = MutableStateFlow(prefs.getBoolean(KEY_VERTICES_VISIBLE, true))
    override val verticesVisibility: StateFlow<Boolean> = _verticesVisibility.asStateFlow()

    private val _edgesVisibility = MutableStateFlow(prefs.getBoolean(KEY_EDGES_VISIBLE, false))
    override val edgesVisibility: StateFlow<Boolean> = _edgesVisibility.asStateFlow()

    private val _regionsVisibility = MutableStateFlow(prefs.getBoolean(KEY_REGIONS_VISIBLE, true))
    override val regionsVisibility: StateFlow<Boolean> = _regionsVisibility.asStateFlow()

    private val _perimeterVisibility = MutableStateFlow(prefs.getBoolean(KEY_PERIMETER_VISIBLE, true))
    override val perimeterVisibility: StateFlow<Boolean> = _perimeterVisibility.asStateFlow()

    private val _animateEffects = MutableStateFlow(prefs.getBoolean(KEY_ANIMATE_EFFECTS, true))
    override val animateEffects: StateFlow<Boolean> = _animateEffects.asStateFlow()

    // ── Difficulty ─────────────────────────────────────────────────────────────

    private val _difficulty = MutableStateFlow(
        Difficulty.entries.find { it.name == prefs.get(KEY_DIFFICULTY, null) } ?: Difficulty.DEFAULT
    )
    override val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _difficultyBlack = MutableStateFlow(
        Difficulty.entries.find { it.name == prefs.get(KEY_DIFFICULTY_BLACK, null) } ?: Difficulty.DEFAULT
    )
    override val difficultyBlack: StateFlow<Difficulty> = _difficultyBlack.asStateFlow()

    private val _difficultyWhite = MutableStateFlow(
        Difficulty.entries.find { it.name == prefs.get(KEY_DIFFICULTY_WHITE, null) } ?: Difficulty.DEFAULT
    )
    override val difficultyWhite: StateFlow<Difficulty> = _difficultyWhite.asStateFlow()

    // ── User & Localization ────────────────────────────────────────────────────

    private val _userName = MutableStateFlow(prefs.get(KEY_USER_NAME, ""))
    override val userName: StateFlow<String> = _userName.asStateFlow()

    private val _language = MutableStateFlow(
        AppLanguage.entries.find { it.name == prefs.get(KEY_LANGUAGE, null) } ?: AppLanguage.SPANISH
    )
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // ── Visual Style ───────────────────────────────────────────────────────────

    private val _palette = MutableStateFlow(prefs.get(KEY_PALETTE, "Classic"))
    override val palette: StateFlow<String> = _palette.asStateFlow()

    private val _conversionAnimationStyle = MutableStateFlow(
        ConversionAnimationStyle.entries.find { it.name == prefs.get(KEY_CONVERSION_STYLE, null) }
            ?: ConversionAnimationStyle.SURPRISE
    )
    override val conversionAnimationStyle: StateFlow<ConversionAnimationStyle> = _conversionAnimationStyle.asStateFlow()

    private val _pieceTypeId = MutableStateFlow(prefs.get(KEY_PIECE_TYPE_ID, PieceTypes.default.id))
    override val pieceTypeId: StateFlow<String> = _pieceTypeId.asStateFlow()

    // ── Sound ──────────────────────────────────────────────────────────────────

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ENABLED, false))
    override val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _soundVolume = MutableStateFlow(prefs.getFloat(KEY_SOUND_VOLUME, 0.8f))
    override val soundVolume: StateFlow<Float> = _soundVolume.asStateFlow()

    // ── Tutorial & Seasonal ────────────────────────────────────────────────────

    private val _tutorialSeen = MutableStateFlow(prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
    override val tutorialSeen: StateFlow<Boolean> = _tutorialSeen.asStateFlow()

    private val _mpTutorialSeen = MutableStateFlow(prefs.getBoolean(KEY_MP_TUTORIAL_SEEN, false))
    override val mpTutorialSeen: StateFlow<Boolean> = _mpTutorialSeen.asStateFlow()

    private val _seasonalAutoAppliedDate = MutableStateFlow(prefs.get(KEY_SEASONAL_DATE, ""))
    override val seasonalAutoAppliedDate: StateFlow<String> = _seasonalAutoAppliedDate.asStateFlow()

    private val _preSeasonalPalette = MutableStateFlow(prefs.get(KEY_PRE_SEASONAL_PALETTE, ""))
    override val preSeasonalPalette: StateFlow<String> = _preSeasonalPalette.asStateFlow()

    // ── Game Session ───────────────────────────────────────────────────────────

    private val _whiteIsAI = MutableStateFlow(prefs.getBoolean(KEY_WHITE_IS_AI, false))
    override val whiteIsAI: StateFlow<Boolean> = _whiteIsAI.asStateFlow()

    private val _blackIsAI = MutableStateFlow(prefs.getBoolean(KEY_BLACK_IS_AI, true))
    override val blackIsAI: StateFlow<Boolean> = _blackIsAI.asStateFlow()

    private val _boardOrientation = MutableStateFlow(
        prefs.get(KEY_BOARD_ORIENTATION, BoardOrientation.PORTRAIT_WHITE.name)
    )
    override val boardOrientation: StateFlow<String> = _boardOrientation.asStateFlow()

    private val _isManuallyRotated = MutableStateFlow(prefs.getBoolean(KEY_MANUALLY_ROTATED, false))
    override val isManuallyRotated: StateFlow<Boolean> = _isManuallyRotated.asStateFlow()

    // ── Time Control & Pre-moves ───────────────────────────────────────────────

    private val _timeControl = MutableStateFlow(
        prefs.get(KEY_TIME_CONTROL, null)?.let { TimeControlMode.deserialize(it) }
            ?: TimeControlMode.Unlimited
    )
    override val timeControl: StateFlow<TimeControlMode> = _timeControl.asStateFlow()

    private val _preMovesEnabled = MutableStateFlow(prefs.getBoolean(KEY_PRE_MOVES_ENABLED, true))
    override val preMovesEnabled: StateFlow<Boolean> = _preMovesEnabled.asStateFlow()

    private val _onlineTimeControl = MutableStateFlow(prefs.get(KEY_ONLINE_TIME_CONTROL, TimeControl.BLITZ.key))
    override val onlineTimeControl: StateFlow<String> = _onlineTimeControl.asStateFlow()

    private val _onlineRated = MutableStateFlow(prefs.getBoolean(KEY_ONLINE_RATED, true))
    override val onlineRated: StateFlow<Boolean> = _onlineRated.asStateFlow()

    private val _onlineSpectatingAllowed = MutableStateFlow(prefs.getBoolean(KEY_ONLINE_SPECTATING_ALLOWED, true))
    override val onlineSpectatingAllowed: StateFlow<Boolean> = _onlineSpectatingAllowed.asStateFlow()

    private val _companionPanelWidth = MutableStateFlow(
        prefs.getFloat(KEY_COMPANION_PANEL_WIDTH, SettingsRepository.COMPANION_PANEL_DEFAULT_WIDTH)
    )
    override val companionPanelWidth: StateFlow<Float> = _companionPanelWidth.asStateFlow()

    // ── Setters ────────────────────────────────────────────────────────────────

    override suspend fun setTutorialSeen(seen: Boolean) {
        _tutorialSeen.value = seen
        prefs.putBoolean(KEY_TUTORIAL_SEEN, seen)
        flush()
    }

    override suspend fun setMpTutorialSeen(seen: Boolean) {
        _mpTutorialSeen.value = seen
        prefs.putBoolean(KEY_MP_TUTORIAL_SEEN, seen)
        flush()
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.putBoolean(KEY_DARK_THEME, enabled)
        flush()
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        _difficulty.value = difficulty
        prefs.put(KEY_DIFFICULTY, difficulty.name)
        flush()
    }

    override suspend fun setDifficultyBlack(difficulty: Difficulty) {
        _difficultyBlack.value = difficulty
        prefs.put(KEY_DIFFICULTY_BLACK, difficulty.name)
        flush()
    }

    override suspend fun setDifficultyWhite(difficulty: Difficulty) {
        _difficultyWhite.value = difficulty
        prefs.put(KEY_DIFFICULTY_WHITE, difficulty.name)
        flush()
    }

    override suspend fun setUserName(userName: String) {
        _userName.value = userName
        prefs.put(KEY_USER_NAME, userName)
        flush()
    }

    override suspend fun setLanguage(language: AppLanguage) {
        _language.value = language
        prefs.put(KEY_LANGUAGE, language.name)
        flush()
    }

    override suspend fun setPalette(paletteName: String) {
        _palette.value = paletteName
        prefs.put(KEY_PALETTE, paletteName)
        flush()
    }

    override suspend fun setLabelsVisibility(visibility: Boolean) {
        _labelsVisibility.value = visibility
        prefs.putBoolean(KEY_LABELS_VISIBLE, visibility)
        flush()
    }

    override suspend fun setVerticesVisibility(visibility: Boolean) {
        _verticesVisibility.value = visibility
        prefs.putBoolean(KEY_VERTICES_VISIBLE, visibility)
        flush()
    }

    override suspend fun setEdgesVisibility(visibility: Boolean) {
        _edgesVisibility.value = visibility
        prefs.putBoolean(KEY_EDGES_VISIBLE, visibility)
        flush()
    }

    override suspend fun setRegionsVisibility(visibility: Boolean) {
        _regionsVisibility.value = visibility
        prefs.putBoolean(KEY_REGIONS_VISIBLE, visibility)
        flush()
    }

    override suspend fun setPerimeterVisibility(visibility: Boolean) {
        _perimeterVisibility.value = visibility
        prefs.putBoolean(KEY_PERIMETER_VISIBLE, visibility)
        flush()
    }

    override suspend fun setAnimateEffects(animate: Boolean) {
        _animateEffects.value = animate
        prefs.putBoolean(KEY_ANIMATE_EFFECTS, animate)
        flush()
    }

    override suspend fun setConversionAnimationStyle(style: ConversionAnimationStyle) {
        _conversionAnimationStyle.value = style
        prefs.put(KEY_CONVERSION_STYLE, style.name)
        flush()
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.putBoolean(KEY_SOUND_ENABLED, enabled)
        flush()
    }

    override suspend fun setSoundVolume(volume: Float) {
        _soundVolume.value = volume
        prefs.putFloat(KEY_SOUND_VOLUME, volume)
        flush()
    }

    override suspend fun setSeasonalAutoAppliedDate(date: String) {
        _seasonalAutoAppliedDate.value = date
        prefs.put(KEY_SEASONAL_DATE, date)
        flush()
    }

    override suspend fun setPreSeasonalPalette(paletteName: String) {
        _preSeasonalPalette.value = paletteName
        prefs.put(KEY_PRE_SEASONAL_PALETTE, paletteName)
        flush()
    }

    override suspend fun clearPreSeasonalPalette() {
        _preSeasonalPalette.value = ""
        prefs.remove(KEY_PRE_SEASONAL_PALETTE)
        flush()
    }

    override suspend fun setPieceTypeId(pieceTypeId: String) {
        _pieceTypeId.value = pieceTypeId
        prefs.put(KEY_PIECE_TYPE_ID, pieceTypeId)
        flush()
    }

    override suspend fun setWhiteIsAI(isAI: Boolean) {
        _whiteIsAI.value = isAI
        prefs.putBoolean(KEY_WHITE_IS_AI, isAI)
        flush()
    }

    override suspend fun setBlackIsAI(isAI: Boolean) {
        _blackIsAI.value = isAI
        prefs.putBoolean(KEY_BLACK_IS_AI, isAI)
        flush()
    }

    override suspend fun setBoardOrientation(orientation: BoardOrientation) {
        _boardOrientation.value = orientation.name
        prefs.put(KEY_BOARD_ORIENTATION, orientation.name)
        flush()
    }

    override suspend fun setManuallyRotated(value: Boolean) {
        _isManuallyRotated.value = value
        prefs.putBoolean(KEY_MANUALLY_ROTATED, value)
        flush()
    }

    override suspend fun setTimeControl(mode: TimeControlMode) {
        _timeControl.value = mode
        prefs.put(KEY_TIME_CONTROL, TimeControlMode.serialize(mode))
        flush()
    }

    override suspend fun setPreMovesEnabled(enabled: Boolean) {
        _preMovesEnabled.value = enabled
        prefs.putBoolean(KEY_PRE_MOVES_ENABLED, enabled)
        flush()
    }

    override suspend fun setOnlineTimeControl(timeControl: String) {
        _onlineTimeControl.value = timeControl
        prefs.put(KEY_ONLINE_TIME_CONTROL, timeControl)
        flush()
    }

    override suspend fun setOnlineRated(rated: Boolean) {
        _onlineRated.value = rated
        prefs.putBoolean(KEY_ONLINE_RATED, rated)
        flush()
    }

    override suspend fun setOnlineSpectatingAllowed(allowed: Boolean) {
        _onlineSpectatingAllowed.value = allowed
        prefs.putBoolean(KEY_ONLINE_SPECTATING_ALLOWED, allowed)
        flush()
    }

    override suspend fun setCompanionPanelWidth(width: Float) {
        _companionPanelWidth.value = width
        prefs.putFloat(KEY_COMPANION_PANEL_WIDTH, width)
        flush()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Forces a flush to disk.
     *
     * Preferences API commits changes asynchronously, but we call flush()
     * after each write to ensure immediate persistence. This is important
     * for Desktop apps that may be closed abruptly.
     */
    private fun flush() {
        try {
            prefs.flush()
        } catch (e: Exception) {
            // Log but don't crash - preferences may be read-only in some environments
            logger.warn("Could not flush preferences: ${e.message}")
        }
    }

    // ── Keys ───────────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_DIFFICULTY_BLACK = "difficulty_black"
        private const val KEY_DIFFICULTY_WHITE = "difficulty_white"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_PALETTE = "palette"
        private const val KEY_LABELS_VISIBLE = "labels_visible"
        private const val KEY_VERTICES_VISIBLE = "vertices_visible"
        private const val KEY_EDGES_VISIBLE = "edges_visible"
        private const val KEY_REGIONS_VISIBLE = "regions_visible"
        private const val KEY_PERIMETER_VISIBLE = "perimeter_visible"
        private const val KEY_ANIMATE_EFFECTS = "animate_effects"
        private const val KEY_CONVERSION_STYLE = "conversion_style"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_MP_TUTORIAL_SEEN = "mp_tutorial_seen"
        private const val KEY_SEASONAL_DATE = "seasonal_date"
        private const val KEY_PRE_SEASONAL_PALETTE = "pre_seasonal_palette"
        private const val KEY_PIECE_TYPE_ID = "piece_type_id"
        private const val KEY_WHITE_IS_AI = "white_is_ai"
        private const val KEY_BLACK_IS_AI = "black_is_ai"
        private const val KEY_BOARD_ORIENTATION = "board_orientation"
        private const val KEY_MANUALLY_ROTATED = "manually_rotated"
        private const val KEY_TIME_CONTROL = "time_control"
        private const val KEY_PRE_MOVES_ENABLED = "pre_moves_enabled"
        private const val KEY_ONLINE_TIME_CONTROL = "online_time_control"
        private const val KEY_ONLINE_RATED = "online_rated"
        private const val KEY_ONLINE_SPECTATING_ALLOWED = "online_spectating_allowed"
        private const val KEY_COMPANION_PANEL_WIDTH = "companion_panel_width"
    }
}