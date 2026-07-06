package com.agustin.tarati.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.agustin.tarati.desktop.di.desktopModules
import com.agustin.tarati.desktop.services.localization.DesktopLanguageAwareApp
import com.agustin.tarati.features.settings.DesktopSettingsViewModel
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.sound.LocalSoundService
import com.agustin.tarati.ui.AppContent
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration
import org.jetbrains.skia.Image as SkiaImage

fun main(): Unit = application {
    // Ícono de la ventana (barra de título / taskbar) en runtime. Sin esto, la
    // ventana muestra el ícono por defecto de Java. Se carga desde el classpath
    // (src/main/resources/icons/tarati.png) vía Skia para no depender de las APIs
    // de painterResource, que varían entre versiones de Compose.
    val appIcon = remember {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("icons/tarati.png")
            ?.use { stream ->
                BitmapPainter(SkiaImage.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
            }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tarati",
        icon = appIcon,
    ) {
        KoinApplication(
            configuration = koinConfiguration(declaration = { modules(desktopModules) }),
            content = {
                val settingsViewModel: ISettingsViewModel = koinViewModel<DesktopSettingsViewModel>()

                // Inject ISoundService from Koin and provide it to the Compose tree
                // via CompositionLocal so GameScreen can access it with LocalSoundService.current
                val soundService: ISoundService = koinInject()

                // Aplica el estado de sonido (habilitado/volumen) de Settings al servicio, como en
                // Android — sin esto el toggle y el volumen de Settings se ignoraban en Desktop.
                val settings by settingsViewModel.settingsState.collectAsState()
                LaunchedEffect(settings.soundState.soundEnabled, settings.soundState.soundVolume) {
                    soundService.setSoundEnabled(settings.soundState.soundEnabled)
                    soundService.setVolume(settings.soundState.soundVolume)
                }

                // Language-aware wrapper - updates JVM Locale when language changes
                DesktopLanguageAwareApp(viewModel = settingsViewModel) {
                    CompositionLocalProvider(LocalSoundService provides soundService) {
                        AppContent(settingsViewModel)
                    }
                }
            })
    }
}