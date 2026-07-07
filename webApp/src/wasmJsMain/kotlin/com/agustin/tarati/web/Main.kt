package com.agustin.tarati.web

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.agustin.tarati.features.online.auth.ResetPasswordSheet
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.sound.LocalSoundService
import com.agustin.tarati.ui.AppContent
import com.agustin.tarati.web.di.webModules
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Apply the saved language BEFORE the first composition to avoid a flash of wrong content.
    // WasmSettingsRepository.K_LANGUAGE = "w_language", values are AppLanguage enum names.
    val langCode = when (window.localStorage.getItem("w_language")) {
        "ENGLISH" -> "en"
        "SPANISH" -> "es"
        else -> null
    }
    if (langCode != null) {
        overrideNavigatorLanguages(langCode)
    }

    // Detectar token de reset de contraseña en la URL (/reset-password?token=...)
    val resetToken: String? = runCatching {
        val pathname = window.location.pathname
        val search = window.location.search
        if (pathname.contains("reset-password") && search.contains("token=")) {
            search.substringAfter("token=").substringBefore("&").takeIf { it.isNotBlank() }
        } else null
    }.getOrNull()

    ComposeViewport(document.body ?: return) {
        KoinApplication(
            configuration = koinConfiguration(declaration = { modules(webModules) }),
        ) {
            // Oculta el splash HTML apenas la primera composición commitea (ya hay
            // primer frame), empalmando con el splash propio de la app sin parpadeo.
            LaunchedEffect(Unit) { signalAppReady() }

            val settingsViewModel: ISettingsViewModel = koinViewModel<WasmSettingsViewModel>()
            val soundService: ISoundService = koinInject()

            // Aplica el estado de sonido (habilitado/volumen) de Settings al servicio, como en
            // Android — sin esto el toggle y el volumen de Settings se ignoraban en Web.
            val settings by settingsViewModel.settingsState.collectAsState()
            LaunchedEffect(settings.soundState.soundEnabled, settings.soundState.soundVolume) {
                soundService.setSoundEnabled(settings.soundState.soundEnabled)
                soundService.setVolume(settings.soundState.soundVolume)
            }

            WasmLanguageAwareApp(settingsViewModel) {
                CompositionLocalProvider(LocalSoundService provides soundService) {
                    AppContent(settingsViewModel)

                    // Mostrar sheet de reset si la URL contiene un token válido.
                    // Se renderiza sobre AppContent — el usuario completa el reset
                    // y luego inicia sesión normalmente.
                    var showResetSheet by remember { mutableStateOf(resetToken != null) }
                    if (showResetSheet && resetToken != null) {
                        ResetPasswordSheet(
                            token = resetToken,
                            onDone = { showResetSheet = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Oculta el splash HTML inicial (definido en `index.html`) llamando al hook global
 * `__taratiReady`. Se invoca desde la primera composición de Compose.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun signalAppReady() {
    js("if (globalThis.__taratiReady) globalThis.__taratiReady()")
}
