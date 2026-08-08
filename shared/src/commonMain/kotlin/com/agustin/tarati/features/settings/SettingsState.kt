package com.agustin.tarati.features.settings

import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.availablePalettes
import kotlinx.serialization.Serializable

@Serializable
data class BoardVisualState(
    val labelsVisibles: Boolean = false,
    val verticesVisibles: Boolean = true,
    val edgesVisibles: Boolean = false,
    val regionsVisibles: Boolean = true,
    val perimeterVisible: Boolean = true,
    val animateEffects: Boolean = true,
    val conversionAnimationStyle: ConversionAnimationStyle = ConversionAnimationStyle.SURPRISE,
)

data class SoundState(
    val soundEnabled: Boolean = false,
    val soundVolume: Float = 0.8f,
)

data class SettingsState(
    val appTheme: AppTheme = AppTheme.MODE_AUTO,
    /** Difficulty for the White side (or the single AI when playing Human vs AI). */
    val difficulty: Difficulty = Difficulty.DEFAULT,
    /** Difficulty for the Black side. Defaults to the same as [difficulty]. */
    val difficultyBlack: Difficulty = Difficulty.DEFAULT,
    val userName: String = "",
    val language: AppLanguage = AppLanguage.SPANISH,
    val boardVisualState: BoardVisualState = BoardVisualState(),
    val palette: String = availablePalettes.first().name,
    val soundState: SoundState = SoundState(),
    val pieceTypeId: String = PieceTypes.default.id,
    /**
     * Modo de control de tiempo para partidas nuevas. Observado por
     * [GameScreen] para cablear
     * `timeControlProvider` en [GameEvents].
     */
    val timeControl: TimeControlMode = TimeControlMode.Unlimited,
    /** Whether pre-move selection is enabled during the AI's turn. */
    val preMovesEnabled: Boolean = true,
    /** Whether the live evaluation bar is shown alongside the board. */
    val showEvaluationBar: Boolean = true,
)