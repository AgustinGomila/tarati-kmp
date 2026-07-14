package com.agustin.tarati.features.settings


import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.availablePalettes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidSettingsRepository(
    private var dataStore: DataStore<Preferences>,
) : SettingsRepository {
    // ── Flows ──────────────────────────────────────────────────────────────────

    override val difficulty: Flow<Difficulty> =
        dataStore.data.map { prefs ->
            prefs[DIFFICULTY_KEY]?.let { Difficulty.getByOrdinal(it) } ?: Difficulty.DEFAULT
        }

    override val difficultyBlack: Flow<Difficulty> =
        dataStore.data.map { prefs ->
            prefs[DIFFICULTY_BLACK_KEY]?.let { Difficulty.getByOrdinal(it) } ?: Difficulty.DEFAULT
        }

    override val difficultyWhite: Flow<Difficulty> =
        dataStore.data.map { prefs ->
            prefs[DIFFICULTY_WHITE_KEY]?.let { Difficulty.getByOrdinal(it) } ?: Difficulty.DEFAULT
        }

    override val userName: Flow<String> =
        dataStore.data.map { prefs -> prefs[USER_NAME_KEY] ?: "" }

    override val language: Flow<AppLanguage> =
        dataStore.data.map { prefs ->
            prefs[LANGUAGE_KEY]?.let { AppLanguage.valueOf(it) } ?: AppLanguage.SPANISH
        }

    override val palette: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[PALETTE_KEY] ?: availablePalettes.first().name
        }

    override val isDarkTheme: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[DARK_THEME_KEY] ?: DARK_THEME_DEFAULT }

    override val labelsVisibility: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[LABELS_VISIBILITY_KEY] ?: LABELS_VISIBILITY_DEFAULT }

    override val verticesVisibility: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[VERTICES_VISIBILITY_KEY] ?: VERTICES_VISIBILITY_DEFAULT }

    override val edgesVisibility: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[EDGES_VISIBILITY_KEY] ?: EDGES_VISIBILITY_DEFAULT }

    override val regionsVisibility: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[REGIONS_VISIBILITY_KEY] ?: REGIONS_VISIBILITY_DEFAULT }

    override val perimeterVisibility: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[PERIMETER_VISIBILITY_KEY] ?: PERIMETER_VISIBILITY_DEFAULT }

    override val animateEffects: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[ANIMATE_EFFECTS_KEY] ?: ANIMATE_EFFECTS_DEFAULT }

    override val conversionAnimationStyle: Flow<ConversionAnimationStyle> =
        dataStore.data.map { prefs ->
            prefs[CONVERSION_ANIMATION_STYLE_KEY]
                ?.let { runCatching { ConversionAnimationStyle.valueOf(it) }.getOrNull() }
                ?: ConversionAnimationStyle.SURPRISE
        }

    override val soundEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SOUND_ENABLED_KEY] ?: SOUND_ENABLED_DEFAULT }

    override val soundVolume: Flow<Float> =
        dataStore.data.map { prefs -> prefs[SOUND_VOLUME_KEY] ?: SOUND_VOLUME_DEFAULT }

    override val tutorialSeen: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[TUTORIAL_SEEN_KEY] ?: false }

    override val mpTutorialSeen: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[MP_TUTORIAL_SEEN_KEY] ?: false }

    override val seasonalAutoAppliedDate: Flow<String> =
        dataStore.data.map { prefs -> prefs[SEASONAL_AUTO_APPLIED_DATE_KEY] ?: "" }

    override val preSeasonalPalette: Flow<String> =
        dataStore.data.map { prefs -> prefs[PRE_SEASONAL_PALETTE_KEY] ?: "" }

    override val pieceTypeId: Flow<String> =
        dataStore.data.map { prefs -> prefs[PIECE_TYPE_ID_KEY] ?: PieceTypes.default.id }

    override val whiteIsAI: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[WHITE_IS_AI_KEY] ?: WHITE_IS_AI_DEFAULT }

    override val blackIsAI: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[BLACK_IS_AI_KEY] ?: BLACK_IS_AI_DEFAULT }

    override val boardOrientation: Flow<String> =
        dataStore.data.map { prefs -> prefs[BOARD_ORIENTATION_KEY] ?: BOARD_ORIENTATION_DEFAULT }

    override val isManuallyRotated: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[IS_MANUALLY_ROTATED_KEY] ?: false }

    override val timeControl: Flow<TimeControlMode> =
        dataStore.data.map { prefs ->
            prefs[TIME_CONTROL_KEY]
                ?.let { TimeControlMode.deserialize(it) }
                ?: TimeControlMode.Unlimited
        }

    override val preMovesEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[PRE_MOVES_ENABLED_KEY] ?: PRE_MOVES_ENABLED_DEFAULT }

    override val onlineTimeControl: Flow<String> =
        dataStore.data.map { prefs -> prefs[ONLINE_TIME_CONTROL_KEY] ?: ONLINE_TIME_CONTROL_DEFAULT }

    override val onlineRated: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[ONLINE_RATED_KEY] ?: ONLINE_RATED_DEFAULT }

    override val onlineSpectatingAllowed: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[ONLINE_SPECTATING_ALLOWED_KEY] ?: ONLINE_SPECTATING_ALLOWED_DEFAULT }

    override val companionPanelWidth: Flow<Float> =
        dataStore.data.map { prefs ->
            prefs[COMPANION_PANEL_WIDTH_KEY] ?: SettingsRepository.COMPANION_PANEL_DEFAULT_WIDTH
        }

    // ── Setters ────────────────────────────────────────────────────────────────

    override suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[DARK_THEME_KEY] = enabled }
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        dataStore.edit { it[DIFFICULTY_KEY] = difficulty.ordinal }
    }

    override suspend fun setDifficultyBlack(difficulty: Difficulty) {
        dataStore.edit { it[DIFFICULTY_BLACK_KEY] = difficulty.ordinal }
    }

    override suspend fun setDifficultyWhite(difficulty: Difficulty) {
        dataStore.edit { it[DIFFICULTY_WHITE_KEY] = difficulty.ordinal }
    }

    override suspend fun setUserName(userName: String) {
        dataStore.edit { it[USER_NAME_KEY] = userName }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[LANGUAGE_KEY] = language.name }
    }

    override suspend fun setPalette(paletteName: String) {
        dataStore.edit { it[PALETTE_KEY] = paletteName }
    }

    override suspend fun setLabelsVisibility(visibility: Boolean) {
        dataStore.edit { it[LABELS_VISIBILITY_KEY] = visibility }
    }

    override suspend fun setVerticesVisibility(visibility: Boolean) {
        dataStore.edit { it[VERTICES_VISIBILITY_KEY] = visibility }
    }

    override suspend fun setEdgesVisibility(visibility: Boolean) {
        dataStore.edit { it[EDGES_VISIBILITY_KEY] = visibility }
    }

    override suspend fun setRegionsVisibility(visibility: Boolean) {
        dataStore.edit { it[REGIONS_VISIBILITY_KEY] = visibility }
    }

    override suspend fun setPerimeterVisibility(visibility: Boolean) {
        dataStore.edit { it[PERIMETER_VISIBILITY_KEY] = visibility }
    }

    override suspend fun setAnimateEffects(animate: Boolean) {
        dataStore.edit { it[ANIMATE_EFFECTS_KEY] = animate }
    }

    override suspend fun setConversionAnimationStyle(style: ConversionAnimationStyle) {
        dataStore.edit { it[CONVERSION_ANIMATION_STYLE_KEY] = style.name }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    override suspend fun setSoundVolume(volume: Float) {
        dataStore.edit { it[SOUND_VOLUME_KEY] = volume }
    }

    override suspend fun setTutorialSeen(seen: Boolean) {
        dataStore.edit { it[TUTORIAL_SEEN_KEY] = seen }
    }

    override suspend fun setMpTutorialSeen(seen: Boolean) {
        dataStore.edit { it[MP_TUTORIAL_SEEN_KEY] = seen }
    }

    override suspend fun setSeasonalAutoAppliedDate(date: String) {
        dataStore.edit { it[SEASONAL_AUTO_APPLIED_DATE_KEY] = date }
    }

    override suspend fun setPreSeasonalPalette(paletteName: String) {
        dataStore.edit { it[PRE_SEASONAL_PALETTE_KEY] = paletteName }
    }

    override suspend fun clearPreSeasonalPalette() {
        dataStore.edit { it.remove(PRE_SEASONAL_PALETTE_KEY) }
    }

    override suspend fun setPieceTypeId(pieceTypeId: String) {
        dataStore.edit { it[PIECE_TYPE_ID_KEY] = pieceTypeId }
    }

    override suspend fun setWhiteIsAI(isAI: Boolean) {
        dataStore.edit { it[WHITE_IS_AI_KEY] = isAI }
    }

    override suspend fun setBlackIsAI(isAI: Boolean) {
        dataStore.edit { it[BLACK_IS_AI_KEY] = isAI }
    }

    override suspend fun setBoardOrientation(orientation: BoardOrientation) {
        dataStore.edit { it[BOARD_ORIENTATION_KEY] = orientation.name }
    }

    override suspend fun setManuallyRotated(value: Boolean) {
        dataStore.edit { it[IS_MANUALLY_ROTATED_KEY] = value }
    }

    override suspend fun setTimeControl(mode: TimeControlMode) {
        dataStore.edit { it[TIME_CONTROL_KEY] = TimeControlMode.serialize(mode) }
    }

    override suspend fun setPreMovesEnabled(enabled: Boolean) {
        dataStore.edit { it[PRE_MOVES_ENABLED_KEY] = enabled }
    }

    override suspend fun setOnlineTimeControl(timeControl: String) {
        dataStore.edit { it[ONLINE_TIME_CONTROL_KEY] = timeControl }
    }

    override suspend fun setOnlineRated(rated: Boolean) {
        dataStore.edit { it[ONLINE_RATED_KEY] = rated }
    }

    override suspend fun setOnlineSpectatingAllowed(allowed: Boolean) {
        dataStore.edit { it[ONLINE_SPECTATING_ALLOWED_KEY] = allowed }
    }

    override suspend fun setCompanionPanelWidth(width: Float) {
        dataStore.edit { it[COMPANION_PANEL_WIDTH_KEY] = width }
    }

    // ── Keys ───────────────────────────────────────────────────────────────────

    companion object {
        val DARK_THEME_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("dark_theme_enabled")
        val DIFFICULTY_KEY: Preferences.Key<Int> = intPreferencesKey("difficulty")
        val DIFFICULTY_BLACK_KEY: Preferences.Key<Int> = intPreferencesKey("difficulty_black")
        val DIFFICULTY_WHITE_KEY: Preferences.Key<Int> = intPreferencesKey("difficulty_white")
        val USER_NAME_KEY: Preferences.Key<String> = stringPreferencesKey("user_name")
        val LANGUAGE_KEY: Preferences.Key<String> = stringPreferencesKey("app_language")
        val PALETTE_KEY: Preferences.Key<String> = stringPreferencesKey("app_palette")
        val LABELS_VISIBILITY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("labels_visibles")
        val VERTICES_VISIBILITY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("vertices_visibles")
        val EDGES_VISIBILITY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("edges_visibles")
        val REGIONS_VISIBILITY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("regions_visibles")
        val PERIMETER_VISIBILITY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("perimeter_visible")
        val ANIMATE_EFFECTS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("animate_effects")
        val CONVERSION_ANIMATION_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("conversion_animation_style")
        val SOUND_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME_KEY: Preferences.Key<Float> = floatPreferencesKey("sound_volume")
        val TUTORIAL_SEEN_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("tutorial_seen")
        val MP_TUTORIAL_SEEN_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("mp_tutorial_seen")
        val SEASONAL_AUTO_APPLIED_DATE_KEY: Preferences.Key<String> = stringPreferencesKey("seasonal_auto_applied_date")
        val PRE_SEASONAL_PALETTE_KEY: Preferences.Key<String> = stringPreferencesKey("pre_seasonal_palette")
        val PIECE_TYPE_ID_KEY: Preferences.Key<String> = stringPreferencesKey("piece_type_id")

        // Game session
        val WHITE_IS_AI_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("white_is_ai")
        val BLACK_IS_AI_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("black_is_ai")
        val BOARD_ORIENTATION_KEY: Preferences.Key<String> = stringPreferencesKey("board_orientation_pref")
        val IS_MANUALLY_ROTATED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("is_manually_rotated")

        // Time control & pre-moves
        val TIME_CONTROL_KEY: Preferences.Key<String> = stringPreferencesKey("time_control")
        val PRE_MOVES_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("pre_moves_enabled")

        // Online matchmaking
        val ONLINE_TIME_CONTROL_KEY: Preferences.Key<String> = stringPreferencesKey("online_time_control")
        val ONLINE_RATED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("online_rated")
        val ONLINE_SPECTATING_ALLOWED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("online_spectating_allowed")

        // Companion panel layout
        val COMPANION_PANEL_WIDTH_KEY: Preferences.Key<Float> = floatPreferencesKey("companion_panel_width")

        private const val DARK_THEME_DEFAULT = true
        private const val LABELS_VISIBILITY_DEFAULT = false
        private const val VERTICES_VISIBILITY_DEFAULT = true
        private const val EDGES_VISIBILITY_DEFAULT = false
        private const val REGIONS_VISIBILITY_DEFAULT = true
        private const val PERIMETER_VISIBILITY_DEFAULT = true
        private const val ANIMATE_EFFECTS_DEFAULT = true
        private const val SOUND_ENABLED_DEFAULT = true
        private const val SOUND_VOLUME_DEFAULT = 0.8f

        // Game session defaults
        private const val WHITE_IS_AI_DEFAULT = false
        private const val BLACK_IS_AI_DEFAULT = true
        private val BOARD_ORIENTATION_DEFAULT = BoardOrientation.PORTRAIT_WHITE.name

        // Time control & pre-moves defaults
        const val PRE_MOVES_ENABLED_DEFAULT: Boolean = true

        // Online matchmaking defaults
        const val ONLINE_TIME_CONTROL_DEFAULT: String = "blitz"
        const val ONLINE_RATED_DEFAULT: Boolean = true
        const val ONLINE_SPECTATING_ALLOWED_DEFAULT: Boolean = true
    }
}