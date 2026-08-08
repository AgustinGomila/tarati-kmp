package com.agustin.tarati.web

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación web de [SettingsRepository] usando [window.localStorage].
 *
 * Las preferencias sobreviven entre sesiones del navegador en el mismo origen.
 */
class WasmSettingsRepository : SettingsRepository {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun str(key: String, default: String? = null): String? =
        window.localStorage.getItem(key) ?: default

    private fun bool(key: String, default: Boolean): Boolean =
        window.localStorage.getItem(key)?.toBooleanStrictOrNull() ?: default

    private fun float(key: String, default: Float): Float =
        window.localStorage.getItem(key)?.toFloatOrNull() ?: default

    private fun set(key: String, value: String) = window.localStorage.setItem(key, value)
    private fun set(key: String, value: Boolean) = window.localStorage.setItem(key, value.toString())
    private fun set(key: String, value: Float) = window.localStorage.setItem(key, value.toString())

    // ── Theme & UI ─────────────────────────────────────────────────────────────

    private val _isDarkTheme = MutableStateFlow(bool(K_DARK_THEME, false))
    override val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _labelsVisibility = MutableStateFlow(bool(K_LABELS, false))
    override val labelsVisibility: StateFlow<Boolean> = _labelsVisibility.asStateFlow()

    private val _verticesVisibility = MutableStateFlow(bool(K_VERTICES, true))
    override val verticesVisibility: StateFlow<Boolean> = _verticesVisibility.asStateFlow()

    private val _edgesVisibility = MutableStateFlow(bool(K_EDGES, false))
    override val edgesVisibility: StateFlow<Boolean> = _edgesVisibility.asStateFlow()

    private val _regionsVisibility = MutableStateFlow(bool(K_REGIONS, true))
    override val regionsVisibility: StateFlow<Boolean> = _regionsVisibility.asStateFlow()

    private val _perimeterVisibility = MutableStateFlow(bool(K_PERIMETER, true))
    override val perimeterVisibility: StateFlow<Boolean> = _perimeterVisibility.asStateFlow()

    private val _animateEffects = MutableStateFlow(bool(K_ANIMATE, true))
    override val animateEffects: StateFlow<Boolean> = _animateEffects.asStateFlow()

    // ── Difficulty ─────────────────────────────────────────────────────────────

    private val _difficulty = MutableStateFlow(
        Difficulty.entries.find { it.name == str(K_DIFFICULTY) } ?: Difficulty.DEFAULT
    )
    override val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _difficultyBlack = MutableStateFlow(
        Difficulty.entries.find { it.name == str(K_DIFF_BLACK) } ?: Difficulty.DEFAULT
    )
    override val difficultyBlack: StateFlow<Difficulty> = _difficultyBlack.asStateFlow()

    private val _difficultyWhite = MutableStateFlow(
        Difficulty.entries.find { it.name == str(K_DIFF_WHITE) } ?: Difficulty.DEFAULT
    )
    override val difficultyWhite: StateFlow<Difficulty> = _difficultyWhite.asStateFlow()

    // ── Theme (tri-state) ──────────────────────────────────────────────────────

    private val _appTheme = MutableStateFlow(
        // Prefer the explicit tri-state key; fall back to the legacy boolean for
        // localStorage entries written before this field existed.
        AppTheme.entries.find { it.name == str(K_APP_THEME) }
            ?: if (bool(K_DARK_THEME, false)) AppTheme.MODE_NIGHT else AppTheme.MODE_AUTO
    )
    override val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    // ── User & Localization ────────────────────────────────────────────────────

    private val _userName = MutableStateFlow(str(K_USER_NAME, "") ?: "")
    override val userName: StateFlow<String> = _userName.asStateFlow()

    private val _language = MutableStateFlow(
        AppLanguage.entries.find { it.name == str(K_LANGUAGE) } ?: AppLanguage.SPANISH
    )
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // ── Visual Style ───────────────────────────────────────────────────────────

    private val _palette = MutableStateFlow(str(K_PALETTE, "Classic") ?: "Classic")
    override val palette: StateFlow<String> = _palette.asStateFlow()

    private val _conversionStyle = MutableStateFlow(
        ConversionAnimationStyle.entries.find { it.name == str(K_CONV_STYLE) } ?: ConversionAnimationStyle.SURPRISE
    )
    override val conversionAnimationStyle: StateFlow<ConversionAnimationStyle> = _conversionStyle.asStateFlow()

    private val _pieceTypeId = MutableStateFlow(str(K_PIECE_TYPE, PieceTypes.default.id) ?: PieceTypes.default.id)
    override val pieceTypeId: StateFlow<String> = _pieceTypeId.asStateFlow()

    // ── Sound ──────────────────────────────────────────────────────────────────

    private val _soundEnabled = MutableStateFlow(bool(K_SOUND_ENABLED, false))
    override val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _soundVolume = MutableStateFlow(float(K_SOUND_VOLUME, 0.8f))
    override val soundVolume: StateFlow<Float> = _soundVolume.asStateFlow()

    // ── Tutorial & Seasonal ────────────────────────────────────────────────────

    private val _tutorialSeen = MutableStateFlow(bool(K_TUTORIAL, false))
    override val tutorialSeen: StateFlow<Boolean> = _tutorialSeen.asStateFlow()

    private val _mpTutorialSeen = MutableStateFlow(bool(K_MP_TUTORIAL, false))
    override val mpTutorialSeen: StateFlow<Boolean> = _mpTutorialSeen.asStateFlow()

    private val _seasonalDate = MutableStateFlow(str(K_SEASONAL_DATE, "") ?: "")
    override val seasonalAutoAppliedDate: StateFlow<String> = _seasonalDate.asStateFlow()

    private val _preSeasonalPalette = MutableStateFlow(str(K_PRE_SEASONAL, "") ?: "")
    override val preSeasonalPalette: StateFlow<String> = _preSeasonalPalette.asStateFlow()

    // ── Game Session ───────────────────────────────────────────────────────────

    private val _whiteIsAI = MutableStateFlow(bool(K_WHITE_AI, false))
    override val whiteIsAI: StateFlow<Boolean> = _whiteIsAI.asStateFlow()

    private val _blackIsAI = MutableStateFlow(bool(K_BLACK_AI, true))
    override val blackIsAI: StateFlow<Boolean> = _blackIsAI.asStateFlow()

    private val _boardOrientation = MutableStateFlow(
        str(K_ORIENTATION, BoardOrientation.PORTRAIT_WHITE.name) ?: BoardOrientation.PORTRAIT_WHITE.name
    )
    override val boardOrientation: StateFlow<String> = _boardOrientation.asStateFlow()

    private val _manuallyRotated = MutableStateFlow(bool(K_MANUALLY_ROTATED, false))
    override val isManuallyRotated: StateFlow<Boolean> = _manuallyRotated.asStateFlow()

    // ── Time Control & Pre-moves ───────────────────────────────────────────────

    private val _timeControl = MutableStateFlow(
        str(K_TIME_CONTROL)?.let { TimeControlMode.deserialize(it) } ?: TimeControlMode.Unlimited
    )
    override val timeControl: StateFlow<TimeControlMode> = _timeControl.asStateFlow()

    private val _preMoves = MutableStateFlow(bool(K_PRE_MOVES, true))
    override val preMovesEnabled: StateFlow<Boolean> = _preMoves.asStateFlow()

    private val _showEvalBar = MutableStateFlow(bool(K_SHOW_EVAL_BAR, true))
    override val showEvaluationBar: StateFlow<Boolean> = _showEvalBar.asStateFlow()

    private val _onlineTc = MutableStateFlow(str(K_ONLINE_TC, TimeControl.BLITZ.key) ?: TimeControl.BLITZ.key)
    override val onlineTimeControl: StateFlow<String> = _onlineTc.asStateFlow()

    private val _onlineRated = MutableStateFlow(bool(K_ONLINE_RATED, true))
    override val onlineRated: StateFlow<Boolean> = _onlineRated.asStateFlow()

    private val _onlineSpectating = MutableStateFlow(bool(K_ONLINE_SPECTATING, true))
    override val onlineSpectatingAllowed: StateFlow<Boolean> = _onlineSpectating.asStateFlow()

    private val _companionPanelWidth =
        MutableStateFlow(float(K_COMPANION_PANEL_WIDTH, SettingsRepository.COMPANION_PANEL_DEFAULT_WIDTH))
    override val companionPanelWidth: StateFlow<Float> = _companionPanelWidth.asStateFlow()

    // ── Setters ────────────────────────────────────────────────────────────────

    override suspend fun setTutorialSeen(seen: Boolean) {
        _tutorialSeen.value = seen; set(K_TUTORIAL, seen)
    }

    override suspend fun setMpTutorialSeen(seen: Boolean) {
        _mpTutorialSeen.value = seen; set(K_MP_TUTORIAL, seen)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled; set(K_DARK_THEME, enabled)
    }

    override suspend fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        set(K_APP_THEME, theme.name)
        val isDark = theme == AppTheme.MODE_NIGHT
        _isDarkTheme.value = isDark
        set(K_DARK_THEME, isDark)
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        _difficulty.value = difficulty; set(K_DIFFICULTY, difficulty.name)
    }

    override suspend fun setDifficultyBlack(difficulty: Difficulty) {
        _difficultyBlack.value = difficulty; set(K_DIFF_BLACK, difficulty.name)
    }

    override suspend fun setDifficultyWhite(difficulty: Difficulty) {
        _difficultyWhite.value = difficulty; set(K_DIFF_WHITE, difficulty.name)
    }

    override suspend fun setUserName(userName: String) {
        _userName.value = userName; set(K_USER_NAME, userName)
    }

    override suspend fun setLanguage(language: AppLanguage) {
        _language.value = language; set(K_LANGUAGE, language.name)
    }

    override suspend fun setPalette(paletteName: String) {
        _palette.value = paletteName; set(K_PALETTE, paletteName)
    }

    override suspend fun setLabelsVisibility(visibility: Boolean) {
        _labelsVisibility.value = visibility; set(K_LABELS, visibility)
    }

    override suspend fun setVerticesVisibility(visibility: Boolean) {
        _verticesVisibility.value = visibility; set(K_VERTICES, visibility)
    }

    override suspend fun setEdgesVisibility(visibility: Boolean) {
        _edgesVisibility.value = visibility; set(K_EDGES, visibility)
    }

    override suspend fun setRegionsVisibility(visibility: Boolean) {
        _regionsVisibility.value = visibility; set(K_REGIONS, visibility)
    }

    override suspend fun setPerimeterVisibility(visibility: Boolean) {
        _perimeterVisibility.value = visibility; set(K_PERIMETER, visibility)
    }

    override suspend fun setAnimateEffects(animate: Boolean) {
        _animateEffects.value = animate; set(K_ANIMATE, animate)
    }

    override suspend fun setConversionAnimationStyle(style: ConversionAnimationStyle) {
        _conversionStyle.value = style; set(K_CONV_STYLE, style.name)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled; set(K_SOUND_ENABLED, enabled)
    }

    override suspend fun setSoundVolume(volume: Float) {
        _soundVolume.value = volume; set(K_SOUND_VOLUME, volume)
    }

    override suspend fun setSeasonalAutoAppliedDate(date: String) {
        _seasonalDate.value = date; set(K_SEASONAL_DATE, date)
    }

    override suspend fun setPreSeasonalPalette(paletteName: String) {
        _preSeasonalPalette.value = paletteName; set(K_PRE_SEASONAL, paletteName)
    }

    override suspend fun clearPreSeasonalPalette() {
        _preSeasonalPalette.value = ""; window.localStorage.removeItem(K_PRE_SEASONAL)
    }

    override suspend fun setPieceTypeId(pieceTypeId: String) {
        _pieceTypeId.value = pieceTypeId; set(K_PIECE_TYPE, pieceTypeId)
    }

    override suspend fun setWhiteIsAI(isAI: Boolean) {
        _whiteIsAI.value = isAI; set(K_WHITE_AI, isAI)
    }

    override suspend fun setBlackIsAI(isAI: Boolean) {
        _blackIsAI.value = isAI; set(K_BLACK_AI, isAI)
    }

    override suspend fun setBoardOrientation(orientation: BoardOrientation) {
        _boardOrientation.value = orientation.name; set(K_ORIENTATION, orientation.name)
    }

    override suspend fun setManuallyRotated(value: Boolean) {
        _manuallyRotated.value = value; set(K_MANUALLY_ROTATED, value)
    }

    override suspend fun setTimeControl(mode: TimeControlMode) {
        _timeControl.value = mode; set(K_TIME_CONTROL, TimeControlMode.serialize(mode))
    }

    override suspend fun setPreMovesEnabled(enabled: Boolean) {
        _preMoves.value = enabled; set(K_PRE_MOVES, enabled)
    }

    override suspend fun setShowEvaluationBar(enabled: Boolean) {
        _showEvalBar.value = enabled; set(K_SHOW_EVAL_BAR, enabled)
    }

    override suspend fun setOnlineTimeControl(timeControl: String) {
        _onlineTc.value = timeControl; set(K_ONLINE_TC, timeControl)
    }

    override suspend fun setOnlineRated(rated: Boolean) {
        _onlineRated.value = rated; set(K_ONLINE_RATED, rated)
    }

    override suspend fun setOnlineSpectatingAllowed(allowed: Boolean) {
        _onlineSpectating.value = allowed; set(K_ONLINE_SPECTATING, allowed)
    }

    override suspend fun setCompanionPanelWidth(width: Float) {
        _companionPanelWidth.value = width; set(K_COMPANION_PANEL_WIDTH, width)
    }

    companion object {
        private const val K_DARK_THEME = "w_dark_theme"
        private const val K_DIFFICULTY = "w_difficulty"
        private const val K_DIFF_BLACK = "w_diff_black"
        private const val K_DIFF_WHITE = "w_diff_white"
        private const val K_USER_NAME = "w_user_name"
        private const val K_LANGUAGE = "w_language"
        private const val K_PALETTE = "w_palette"
        private const val K_LABELS = "w_labels"
        private const val K_VERTICES = "w_vertices"
        private const val K_EDGES = "w_edges"
        private const val K_REGIONS = "w_regions"
        private const val K_PERIMETER = "w_perimeter"
        private const val K_ANIMATE = "w_animate"
        private const val K_CONV_STYLE = "w_conv_style"
        private const val K_SOUND_ENABLED = "w_sound_enabled"
        private const val K_SOUND_VOLUME = "w_sound_volume"
        private const val K_TUTORIAL = "w_tutorial"
        private const val K_MP_TUTORIAL = "w_mp_tutorial"
        private const val K_SEASONAL_DATE = "w_seasonal_date"
        private const val K_PRE_SEASONAL = "w_pre_seasonal"
        private const val K_PIECE_TYPE = "w_piece_type"
        private const val K_WHITE_AI = "w_white_ai"
        private const val K_BLACK_AI = "w_black_ai"
        private const val K_ORIENTATION = "w_orientation"
        private const val K_MANUALLY_ROTATED = "w_manually_rotated"
        private const val K_TIME_CONTROL = "w_time_control"
        private const val K_PRE_MOVES = "w_pre_moves"
        private const val K_SHOW_EVAL_BAR = "w_show_eval_bar"
        private const val K_ONLINE_TC = "w_online_tc"
        private const val K_ONLINE_RATED = "w_online_rated"
        private const val K_ONLINE_SPECTATING = "w_online_spectating"
        private const val K_COMPANION_PANEL_WIDTH = "w_companion_panel_width"
        private const val K_APP_THEME = "w_app_theme"
    }
}
