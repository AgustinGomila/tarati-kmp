package com.agustin.tarati.services.sound

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interfaz de servicio de sonido multiplataforma.
 *
 * Implementaciones:
 * - [SoundServiceImpl] en androidApp (usa MediaPlayer)
 * - [NoOpSoundService] en desktopApp (sin audio por ahora)
 */
interface ISoundService {
    fun playMoveSound()
    fun playCaptureSound()
    fun playUpgradeSound()
    fun playIllegalMoveSound()
    fun playNewGameSound()
    fun playGameOverSound()
    fun playTutorialStepSound()
    fun setSoundEnabled(enabled: Boolean)
    fun setVolume(volume: Float)
}

val LocalSoundService: ProvidableCompositionLocal<ISoundService> = staticCompositionLocalOf {
    error("No ISoundService provided")
}