package com.agustin.tarati.ui.components.sidebar

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor

// positionHistory: Map<String, Int> puede ser backed por un mutable Map en runtime,
// por lo que no califica para @Immutable — @Stable es correcto aquí.
@Stable
data class SidebarGameState(
    val gameManagerState: GameManagerState,
    val playerSide: CobColor,
    val difficultyWhite: Difficulty,
    val difficultyBlack: Difficulty,
    val isAIEnabled: Boolean,
    val boardOrientation: BoardOrientation,
    val whiteIsAI: Boolean,
    val blackIsAI: Boolean,
    val isEditing: Boolean = false,
    /**
     * Historial de posiciones del motor de IA, necesario para detectar
     * triple repetición en [GameStatusRow]. Default emptyMap() para previews
     * y contextos donde el motor no está disponible.
     */
    val positionHistory: Map<String, Int> = emptyMap(),
    /**
     * Habilita la navegación por historial (undo/redo/saltar). `false` mientras corre una partida
     * online o una sesión de espectador → los controles se muestran **grisados** (paridad cosmética).
     * Se reactiva offline o cuando la partida online **termina**, para navegar su desarrollo.
     */
    val navigationEnabled: Boolean = true,
)