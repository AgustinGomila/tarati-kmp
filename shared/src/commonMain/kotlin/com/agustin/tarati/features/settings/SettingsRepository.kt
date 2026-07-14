package com.agustin.tarati.features.settings

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.features.settings.SettingsRepository.Companion.COMPANION_PANEL_DEFAULT_WIDTH
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Interfaz multiplataforma para persistencia de settings.
 *
 * Implementaciones:
 * - [AndroidSettingsRepository] en androidApp (usa DataStore)
 * - [DesktopSettingsRepository] en desktopApp (con persistencia)
 */
interface SettingsRepository {
    val isDarkTheme: Flow<Boolean>

    /**
     * Tema de la aplicación como tri-estado (Auto / Light / Dark).
     *
     * Implementación por defecto: derivado de [isDarkTheme] para compatibilidad con
     * implementaciones existentes que solo almacenan un booleano.
     * Sobreescribir en implementaciones que soporten persistencia tri-estado real
     * (p. ej. [WasmSettingsRepository]).
     */
    val appTheme: Flow<AppTheme>
        get() = isDarkTheme.map { if (it) AppTheme.MODE_NIGHT else AppTheme.MODE_AUTO }
    val difficulty: Flow<Difficulty>

    /** Difficulty for the Black side. */
    val difficultyBlack: Flow<Difficulty>

    /** Difficulty for the White side (AI). */
    val difficultyWhite: Flow<Difficulty>

    val userName: Flow<String>
    val language: Flow<AppLanguage>
    val palette: Flow<String>
    val labelsVisibility: Flow<Boolean>
    val verticesVisibility: Flow<Boolean>
    val edgesVisibility: Flow<Boolean>
    val regionsVisibility: Flow<Boolean>
    val perimeterVisibility: Flow<Boolean>
    val animateEffects: Flow<Boolean>
    val conversionAnimationStyle: Flow<ConversionAnimationStyle>
    val soundEnabled: Flow<Boolean>
    val soundVolume: Flow<Float>
    val tutorialSeen: Flow<Boolean>

    /** `true` once the multiplayer (game6) tutorial has been auto-shown at least once. */
    val mpTutorialSeen: Flow<Boolean>

    /** "MM-dd" of the last day a seasonal theme was auto-applied. Empty string if never. */
    val seasonalAutoAppliedDate: Flow<String>

    /** Palette name to restore after a seasonal day ends. Empty string if none. */
    val preSeasonalPalette: Flow<String>

    /**
     * Id del [PieceType] seleccionado.
     * Se resuelve con [PieceTypes.findById] al leer. Default: [PieceTypes.default.id].
     */
    val pieceTypeId: Flow<String>

    // ── Game session preferences (survive app restarts) ────────────────────────

    /** Whether the White band is controlled by the AI engine. */
    val whiteIsAI: Flow<Boolean>

    /** Whether the Black band is controlled by the AI engine. */
    val blackIsAI: Flow<Boolean>

    /**
     * Last board orientation chosen by the user.
     * Stored as the [BoardOrientation] enum name string.
     */
    val boardOrientation: Flow<String>

    /**
     * Whether the user has manually rotated the board. When true,
     * [GameEffects] skips the automatic
     * orientation recalculation on screen rotation.
     * Persisted in DataStore so it survives app restarts.
     */
    val isManuallyRotated: Flow<Boolean>

    // ── Time control & pre-moves ───────────────────────────────────────────────

    /**
     * Modo de control de tiempo aplicado a las partidas nuevas.
     * Default: [TimeControlMode.Unlimited] — sin reloj, sin timeout.
     *
     * Se serializa con [TimeControlMode.serialize] y se deserializa de forma
     * tolerante: cualquier valor inválido o no reconocido (incluidos cambios de
     * schema en versiones futuras) cae al default Unlimited, preservando la
     * compatibilidad hacia atrás.
     */
    val timeControl: Flow<TimeControlMode>

    /**
     * Whether the pre-move feature is enabled. When `true`, the human player
     * can pre-select a move while the AI is thinking; the move auto-executes
     * on their turn if still legal. Default `true`.
     */
    val preMovesEnabled: Flow<Boolean>

    // ── Online matchmaking preferences ────────────────────────────────────────

    /**
     * Last time control selected in the matchmaking dialog.
     * One of: "bullet", "blitz", "rapid", "classical". Default: "blitz".
     * Persisted so the dialog reopens with the player's last choice.
     */
    val onlineTimeControl: Flow<String>

    /**
     * Whether the last matchmaking search was rated. Default: `true`.
     * Persisted so the dialog reopens with the player's last choice.
     */
    val onlineRated: Flow<Boolean>

    /**
     * Whether spectators are allowed in the last matchmaking search. Default: `true`.
     * Persisted so the dialog reopens with the player's last choice.
     */
    val onlineSpectatingAllowed: Flow<Boolean>

    // ── Companion panel layout ─────────────────────────────────────────────────

    /**
     * Ancho en dp del panel lateral (companion) en layouts anchos (Expanded).
     * El usuario puede arrastrar el divisor para redimensionarlo; el valor se
     * persiste para restaurarlo entre sesiones. Default: [COMPANION_PANEL_DEFAULT_WIDTH].
     */
    val companionPanelWidth: Flow<Float>

    // ── Setters ────────────────────────────────────────────────────────────────

    suspend fun setTutorialSeen(seen: Boolean)
    suspend fun setMpTutorialSeen(seen: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)

    /**
     * Persiste el tema completo. Implementación por defecto: delega a [setDarkTheme]
     * y pierde la distinción [AppTheme.MODE_DAY]. Sobreescribir donde se necesite
     * almacenamiento tri-estado real.
     */
    suspend fun setAppTheme(theme: AppTheme) {
        setDarkTheme(theme == AppTheme.MODE_NIGHT)
    }

    suspend fun setDifficulty(difficulty: Difficulty)
    suspend fun setDifficultyBlack(difficulty: Difficulty)
    suspend fun setDifficultyWhite(difficulty: Difficulty)
    suspend fun setUserName(userName: String)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setPalette(paletteName: String)
    suspend fun setLabelsVisibility(visibility: Boolean)
    suspend fun setVerticesVisibility(visibility: Boolean)
    suspend fun setEdgesVisibility(visibility: Boolean)
    suspend fun setRegionsVisibility(visibility: Boolean)
    suspend fun setPerimeterVisibility(visibility: Boolean)
    suspend fun setAnimateEffects(animate: Boolean)
    suspend fun setConversionAnimationStyle(style: ConversionAnimationStyle)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setSoundVolume(volume: Float)
    suspend fun setSeasonalAutoAppliedDate(date: String)
    suspend fun setPreSeasonalPalette(paletteName: String)
    suspend fun clearPreSeasonalPalette()

    /** Persiste el id del tipo de pieza seleccionado. */
    suspend fun setPieceTypeId(pieceTypeId: String)

    // ── Game session setters ───────────────────────────────────────────────────

    suspend fun setWhiteIsAI(isAI: Boolean)
    suspend fun setBlackIsAI(isAI: Boolean)
    suspend fun setBoardOrientation(orientation: BoardOrientation)
    suspend fun setManuallyRotated(value: Boolean)

    // ── Time control & pre-moves setters ──────────────────────────────────────

    /** Persiste el modo de reloj. Se serializa con [TimeControlMode.serialize]. */
    suspend fun setTimeControl(mode: TimeControlMode)

    /** Persiste el flag de pre-movimientos. */
    suspend fun setPreMovesEnabled(enabled: Boolean)

    // ── Online matchmaking setters ─────────────────────────────────────────────

    /** Persiste el último time control elegido en el diálogo de matchmaking. */
    suspend fun setOnlineTimeControl(timeControl: String)

    /** Persiste si la última búsqueda online fue rated. */
    suspend fun setOnlineRated(rated: Boolean)

    /** Persiste si la última búsqueda online permitió espectadores. */
    suspend fun setOnlineSpectatingAllowed(allowed: Boolean)

    // ── Companion panel setters ────────────────────────────────────────────────

    /** Persiste el ancho (dp) del panel lateral elegido por el usuario. */
    suspend fun setCompanionPanelWidth(width: Float)

    companion object {
        /** Ancho por defecto del panel lateral, en dp. */
        const val COMPANION_PANEL_DEFAULT_WIDTH: Float = 380f

        /** Ancho mínimo permitido al redimensionar el panel, en dp. */
        const val COMPANION_PANEL_MIN_WIDTH: Float = 300f

        /** Ancho máximo permitido al redimensionar el panel, en dp. */
        const val COMPANION_PANEL_MAX_WIDTH: Float = 640f
    }
}