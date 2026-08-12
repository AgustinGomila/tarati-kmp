package com.agustin.tarati.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.util.prefs.Preferences

/**
 * Persiste el tamaño/posición de la ventana de Desktop entre sesiones.
 *
 * Compose Desktop no recuerda la geometría de la ventana: sin esto la app arranca
 * siempre con el [WindowState] por defecto. Se guarda en [Preferences] (misma raíz
 * que Settings, nodo `window`):
 * - Windows: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\agustin\tarati\window`
 * - macOS:   `~/Library/Preferences/com.agustin.tarati.window.plist`
 * - Linux:   `~/.java/.userPrefs/com/agustin/tarati/window/prefs.xml`
 *
 * Solo se persiste la geometría **flotante** (no maximizada): al maximizar, `size`
 * y `position` pasan a reflejar la pantalla completa y pisarían el tamaño al que
 * hay que volver. El estado maximizado se guarda en su propia bandera.
 */
object DesktopWindowStateStore {

    private val prefs: Preferences =
        Preferences.userNodeForPackage(DesktopWindowStateStore::class.java).node("window")

    // Defaults del primer arranque (sin estado guardado).
    private const val DEFAULT_WIDTH = 1000f
    private const val DEFAULT_HEIGHT = 720f

    /** Construye el [WindowState] inicial a partir de lo último persistido. */
    fun load(): WindowState {
        val width = prefs.getFloat(KEY_WIDTH, DEFAULT_WIDTH)
        val height = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT)
        val x = prefs.getFloat(KEY_X, Float.NaN)
        val y = prefs.getFloat(KEY_Y, Float.NaN)
        val maximized = prefs.getBoolean(KEY_MAXIMIZED, false)

        // Sin posición guardada (primer arranque) → que el SO la centre.
        val position =
            if (x.isNaN() || y.isNaN()) WindowPosition.PlatformDefault
            else WindowPosition(x.dp, y.dp)

        return WindowState(
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = position,
            size = DpSize(width.dp, height.dp),
        )
    }

    /** Guarda la geometría actual. No persiste estados transitorios (minimizada). */
    fun save(state: WindowState) {
        if (state.isMinimized) return

        prefs.putBoolean(KEY_MAXIMIZED, state.placement == WindowPlacement.Maximized)

        // Solo el tamaño/posición flotantes son restaurables; los maximizados/fullscreen
        // reflejan la pantalla y no deben pisar el valor previo.
        if (state.placement == WindowPlacement.Floating) {
            val size = state.size
            if (size.width.value > 0f && size.height.value > 0f) {
                prefs.putFloat(KEY_WIDTH, size.width.value)
                prefs.putFloat(KEY_HEIGHT, size.height.value)
            }
            val pos = state.position
            if (pos is WindowPosition.Absolute) {
                prefs.putFloat(KEY_X, pos.x.value)
                prefs.putFloat(KEY_Y, pos.y.value)
            }
        }

        runCatching { prefs.flush() }
    }

    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_MAXIMIZED = "maximized"
}

/**
 * Instantánea de las propiedades de geometría que disparan la persistencia en vivo.
 * `equals` estructural evita reguardar cuando nada relevante cambió.
 */
internal data class WindowGeometrySnapshot(
    val placement: WindowPlacement,
    val isMinimized: Boolean,
    val size: DpSize,
    val position: WindowPosition,
)
