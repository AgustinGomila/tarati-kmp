package com.agustin.tarati.features.settings

import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.PaletteList
import kotlinx.coroutines.flow.StateFlow

@Stable
interface ISettingsViewModel {
    fun toggleDarkTheme(enabled: Boolean)

    /** Persiste el tema completo (Auto / Light / Dark). */
    fun setAppTheme(theme: AppTheme) {
        toggleDarkTheme(theme == AppTheme.MODE_NIGHT)
    }

    fun setUserName(name: String)
    fun setLanguage(language: AppLanguage)
    fun setPalette(paletteName: String)
    fun setDifficulty(newDifficulty: Difficulty)

    /** Sets the difficulty for the Black side independently. */
    fun setDifficultyBlack(newDifficulty: Difficulty)
    fun setLabelsVisibility(visible: Boolean)
    fun setVerticesVisibility(visible: Boolean)
    fun setEdgesVisibility(visible: Boolean)
    fun setRegionsVisibility(visible: Boolean)
    fun setPerimeterVisibility(visible: Boolean)
    fun setAnimateEffects(animate: Boolean)
    fun setConversionAnimationStyle(style: ConversionAnimationStyle)
    fun setSoundEnabled(enabled: Boolean)
    fun setSoundVolume(volume: Float)
    fun markTutorialSeen()
    fun setPieceType(pieceTypeId: String)

    /**
     * Cambia el modo de control de tiempo. El cambio se aplica a las partidas
     * iniciadas después de esta llamada — la partida en curso conserva su reloj.
     */
    fun setTimeControl(mode: TimeControlMode)

    /** Habilita o deshabilita la selección de pre-movimientos. */
    fun setPreMovesEnabled(enabled: Boolean)

    /**
     * Inicia el flujo de compra de Google Play para un tipo de pieza premium.
     * Requiere una [android.app.Activity] en primer plano para mostrar el diálogo de pago.
     */
    fun launchPurchaseFlow(productId: String)

    /**
     * IDs de productos comprados y verificados. Un tipo de pieza está desbloqueado
     * cuando su [PieceType.productId]
     * es `null` o está contenido en este conjunto.
     */
    val purchasedProductIds: StateFlow<Set<String>>

    /**
     * Todas las paletas que deben mostrarse en el selector, incluyendo las
     * premium bloqueadas. Difiere de [availablePalettes] en que siempre incluye
     * Gilded (u otras futuras paletas premium), aunque aún no estén compradas.
     */
    val allPalettesForSelector: StateFlow<PaletteList>

    /**
     * Paletas premium visibles en el selector pero aún no compradas.
     * El selector las muestra con candado; al pulsarlas se invoca [launchPurchaseFlow].
     */
    val lockedPalettes: StateFlow<LockedPalettes>

    val hasTutorialBeenSeen: StateFlow<Boolean>
    val settingsState: StateFlow<SettingsState>

    /**
     * Paletas disponibles para el selector. Filtra temas estacionales:
     * solo visibles el día correspondiente o si fueron desbloqueados permanentemente.
     */
    val availablePalettes: StateFlow<PaletteList>
}