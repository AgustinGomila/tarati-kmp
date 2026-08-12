package com.agustin.tarati.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration
import kotlin.time.Duration.Companion.milliseconds
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

    // Geometría de la ventana restaurada de la sesión anterior (tamaño, posición,
    // maximizada). Compose Desktop no la persiste por sí solo.
    val windowState = remember { DesktopWindowStateStore.load() }

    Window(
        onCloseRequest = {
            // Guardado sincrónico al cerrar: garantiza el último estado aunque el
            // debounce en vivo no haya llegado a disparar.
            DesktopWindowStateStore.save(windowState)
            exitApplication()
        },
        state = windowState,
        title = "Tarati",
        icon = appIcon,
    ) {
        // Persistencia en vivo: guarda cuando cambia el tamaño/posición/maximizada.
        // `collectLatest` + `delay` implementa un debounce (cada cambio cancela el
        // guardado pendiente) sin depender de la API `debounce` en preview, para no
        // escribir a disco en cada píxel de arrastre.
        LaunchedEffect(windowState) {
            snapshotFlow {
                WindowGeometrySnapshot(
                    placement = windowState.placement,
                    isMinimized = windowState.isMinimized,
                    size = windowState.size,
                    position = windowState.position,
                )
            }.collectLatest {
                delay(400L.milliseconds)
                DesktopWindowStateStore.save(windowState)
            }
        }

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