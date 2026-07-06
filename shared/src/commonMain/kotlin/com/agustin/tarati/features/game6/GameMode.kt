package com.agustin.tarati.features.game6

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Modo de juego activo en el host de la pantalla principal. */
enum class GameMode { SINGLE, MULTI }

/**
 * Controlador del modo de juego, provisto por el host (ver `NavGraph`) vía [LocalGameModeController].
 * El switch del sidebar ([GameModeSwitch]) lo lee para cambiar entre Tarati (single) y el juego
 * multijugador. Cuando el local es `null` (p. ej. previews, modo online), el switch no se muestra.
 *
 * Debe recordarse con `rememberSaveable(saver = GameModeController.Saver)` para conservar el modo
 * al navegar fuera de la pantalla (Settings/Logros) y volver, o ante recreación de proceso.
 */
@Stable
class GameModeController(initial: GameMode = GameMode.SINGLE) {
    var mode: GameMode by mutableStateOf(initial)
        private set

    fun set(mode: GameMode) {
        this.mode = mode
    }

    companion object {
        val Saver: Saver<GameModeController, String> = Saver(
            save = { it.mode.name },
            restore = { GameModeController(GameMode.valueOf(it)) },
        )
    }
}

val LocalGameModeController = staticCompositionLocalOf<GameModeController?> { null }
