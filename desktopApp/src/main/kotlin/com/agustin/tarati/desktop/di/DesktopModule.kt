package com.agustin.tarati.desktop.di

import com.agustin.tarati.core.data.database.TaratiDatabase
import com.agustin.tarati.core.data.database.dao.AnalysisDao
import com.agustin.tarati.core.data.database.dao.GameDao
import com.agustin.tarati.core.data.repositories.RoomAnalysisCacheRepository
import com.agustin.tarati.core.data.repositories.RoomGameRepository
import com.agustin.tarati.core.domain.ai.runner.AiMoveRunner
import com.agustin.tarati.core.domain.ai.runner.DefaultAiMoveRunner
import com.agustin.tarati.core.domain.game6.ai.DefaultMpBotRunner
import com.agustin.tarati.core.domain.game6.ai.MpBotRunner
import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.repository.GameRepository
import com.agustin.tarati.desktop.data.createDesktopDatabase
import com.agustin.tarati.di.sharedModules
import com.agustin.tarati.features.game.DesktopGameViewModel
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.online.auth.DesktopAuthRepository
import com.agustin.tarati.features.seasonal.ISpecialEventManager
import com.agustin.tarati.features.seasonal.NoOpSpecialEventManager
import com.agustin.tarati.features.settings.DesktopSettingsRepository
import com.agustin.tarati.features.settings.DesktopSettingsViewModel
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.taratiClientJson
import com.agustin.tarati.services.achievements.AchievementSyncService
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.achievements.ServerAchievementsManager
import com.agustin.tarati.services.clipboard.DesktopClipboardService
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.clipboard.IClipboardService
import com.agustin.tarati.services.sound.DesktopSoundService
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.url.DesktopUrlLauncher
import com.agustin.tarati.services.url.IUrlLauncher
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Koin module for Desktop (JVM).
 *
 * Provides Desktop-specific implementations:
 * - [DesktopClipboardService] → uses java.awt.Toolkit
 * - [DesktopSoundService] → javax.sound.sampled + mp3spi (decodes composeResources MP3)
 * - [ServerAchievementsManager] → syncs achievements to Tarati server when logged in
 * - [DesktopSettingsRepository] → **java.util.prefs.Preferences persistence** ✅
 * - [RoomGameRepository] → **Room with SQLite persistence** ✅
 *
 * ## Persistence Locations
 *
 * **Game Database** (Room/SQLite):
 * - Windows: `C:\Users\{username}\.tarati\tarati.db`
 * - macOS: `/Users/{username}/.tarati/tarati.db`
 * - Linux: `/home/{username}/.tarati/tarati.db`
 *
 * **Settings** (Preferences):
 * - Windows: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\agustin\tarati\settings`
 * - macOS: `~/Library/Preferences/com.agustin.tarati.settings.plist`
 * - Linux: `~/.java/.userPrefs/com/agustin/tarati/settings/prefs.xml`
 */
val desktopServiceModule: Module = module {
    // HttpClient con engine CIO (JVM/Desktop) para WebSockets y REST
    single {
        HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 30.toDuration(DurationUnit.SECONDS)
            }
            install(ContentNegotiation) {
                json(taratiClientJson)
            }
            // Acota las llamadas REST para que no queden colgadas ante un servidor inalcanzable
            // (black-hole). El WebSocket (misma instancia de client) se exime por-request con
            // requestTimeoutMillis = INFINITE (ver WebSocketClient).
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
            }
        }
    }

    // Clipboard — java.awt.Toolkit
    single<IClipboardService> { DesktopClipboardService() }
    single { GameClipboardHelper(get()) }
    single<IUrlLauncher> { DesktopUrlLauncher() }

    // Sound — javax.sound.sampled + mp3spi (decodifica los MP3 de composeResources)
    single<ISoundService> { DesktopSoundService() }

    // Special Events — no-op in Desktop
    single<ISpecialEventManager> { NoOpSpecialEventManager() }

    // Achievements — syncs to server when authenticated
    single { AchievementSyncService(get()) }
    single<IAchievementsManager> { ServerAchievementsManager(get(), get(), get()) }
}

/**
 * Database and Repository module for Desktop.
 *
 * **Room Database with SQLite persistence**:
 * - Uses Room 2.8+ multiplatform support
 * - SQLite via JDBC (BundledSQLiteDriver)
 * - Database persists between app launches
 * - Supports migrations (MIGRATION_1_2, MIGRATION_2_3)
 * - Same schema as Android (shared DAO and Entity definitions)
 *
 * **Settings Repository with Preferences**:
 * - Uses java.util.prefs.Preferences for persistence
 * - Settings survive app restarts
 * - Type-safe with enums
 * - Reactive with StateFlows
 */
val desktopDataModule: Module = module {
    // Settings — Preferences persistence
    single<SettingsRepository> { DesktopSettingsRepository() }
    single<AuthRepository> { DesktopAuthRepository() }

    // Room Database — creates instance with SQLite persistence
    single<TaratiDatabase> { createDesktopDatabase() }

    // GameDao — extracted from database
    single<GameDao> { get<TaratiDatabase>().gameDao() }

    // AnalysisDao + caché de análisis persistente (Room)
    single<AnalysisDao> { get<TaratiDatabase>().analysisDao() }
    single<AnalysisCacheRepository> { RoomAnalysisCacheRepository(get()) }
    single<AnalysisRunner> { DefaultAnalysisRunner() }
    // La IA en vivo corre en un hilo real (Dispatchers.Default) sobre el motor singleton.
    single<AiMoveRunner> { DefaultAiMoveRunner(get()) }
    // Bots del multijugador (game6) también en hilo real.
    single<MpBotRunner> { DefaultMpBotRunner() }

    // Repository — Room implementation with disk persistence
    single<GameRepository> { RoomGameRepository(get()) }
}

val desktopViewModelModule: Module = module {
    // Settings ViewModel
    viewModel { DesktopSettingsViewModel(get(), get(), get()) }

    // GameViewModel — CRITICAL: register with IGameModel interface
    // so OnlineGameViewModel can resolve it correctly
    viewModel { DesktopGameViewModel(get(), get()) } bind IGameModel::class
}

val desktopModules: List<Module> = listOf(
    desktopServiceModule
) + sharedModules + listOf(
    desktopDataModule,
    desktopViewModelModule
)
