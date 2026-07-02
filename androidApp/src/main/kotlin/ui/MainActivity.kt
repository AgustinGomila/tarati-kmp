package com.agustin.tarati.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.services.achievements.AchievementsManager
import com.agustin.tarati.services.achievements.ActivityProvider
import com.agustin.tarati.services.billing.IBillingManager
import com.agustin.tarati.services.localization.LanguageAwareApp
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.sound.LocalSoundService
import features.settings.AndroidSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.compose.viewmodel.koinViewModel
import java.util.*

class MainActivity : ComponentActivity() {

    // Registrado aquí porque registerForActivityResult requiere ComponentActivity.
    // El resultado es ignorado: Play Games gestiona su propio estado internamente.
    // Se usa en lugar de startActivity directo para evitar el bloqueo de
    // IntentRedirect Hardening en Android 14+ con intents de extras anidados.
    private val achievementsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* resultado ignorado: Play Games gestiona su propio estado internamente */ }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
        val langCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(langCode)

        super.attachBaseContext(
            newBase.wrapContext(locale),
        )
    }

    override fun onResume() {
        super.onResume()
        val provider = getKoin().get<ActivityProvider>()
        provider.set(this)
        provider.intentLauncher = { intent -> achievementsLauncher.launch(intent) }

        // Reconcile achievements bidirectionally (Play Games ↔ Tarati server) on
        // every foreground. Must run after ActivityProvider.set() so loadAchievements()
        // has an Activity available. Propagates unlocks earned on any platform to the
        // others and restores palette flags / incremental counters. No-op without a
        // server session; the AuthViewModel login/restore hook covers that case.
        lifecycleScope.launch(Dispatchers.IO) {
            getKoin().get<AchievementsManager>().reconcileAchievements()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            getKoin().get<IBillingManager>().queryOwnedPurchases()
        }
    }

    override fun onPause() {
        super.onPause()
        val provider = getKoin().get<ActivityProvider>()
        provider.clear()
        provider.intentLauncher = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This app draws behind the system bars, so we want to handle fitting system windows
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val settingsViewModel: ISettingsViewModel = koinViewModel<AndroidSettingsViewModel>()
            val settings = settingsViewModel.settingsState.collectAsState()

            val soundService: ISoundService = remember { getKoin().get() }
            val soundState = settings.value.soundState

            LaunchedEffect(soundState.soundEnabled, soundState.soundVolume) {
                soundService.setSoundEnabled(soundState.soundEnabled)
                soundService.setVolume(soundState.soundVolume)
            }

            LanguageAwareApp(viewModel = settingsViewModel) {
                CompositionLocalProvider(LocalSoundService provides soundService) {
                    AppContent(settingsViewModel)
                }
            }
        }
    }

    private fun Context.wrapContext(locale: Locale): Context {
        val config =
            Configuration(resources.configuration).apply {
                setLocale(locale)
            }
        return createConfigurationContext(config)
    }
}