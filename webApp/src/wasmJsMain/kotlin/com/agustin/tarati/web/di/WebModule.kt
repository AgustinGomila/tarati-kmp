package com.agustin.tarati.web.di

import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.InMemoryAnalysisCacheRepository
import com.agustin.tarati.core.domain.repository.GameRepository
import com.agustin.tarati.di.sharedModules
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.game.WasmGameViewModel
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.seasonal.ISpecialEventManager
import com.agustin.tarati.features.seasonal.NoOpSpecialEventManager
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.taratiClientJson
import com.agustin.tarati.services.achievements.AchievementSyncService
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.achievements.ServerAchievementsManager
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.clipboard.IClipboardService
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.url.IUrlLauncher
import com.agustin.tarati.web.NoOpGameRepository
import com.agustin.tarati.web.WasmAuthRepository
import com.agustin.tarati.web.WasmClipboardService
import com.agustin.tarati.web.WasmSettingsRepository
import com.agustin.tarati.web.WasmSettingsViewModel
import com.agustin.tarati.web.WasmUrlLauncher
import com.agustin.tarati.web.WebSoundService
import com.agustin.tarati.web.worker.WorkerAnalysisRunner
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
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

val webServiceModule: Module = module {
    single {
        HttpClient(Js) {
            install(WebSockets) {
                pingInterval = 30.toDuration(DurationUnit.SECONDS)
            }
            install(ContentNegotiation) {
                json(taratiClientJson)
            }
        }
    }

    single<IClipboardService> { WasmClipboardService() }
    single { GameClipboardHelper(get()) }
    single<ISoundService> { WebSoundService() }
    single<IUrlLauncher> { WasmUrlLauncher() }
    single<ISpecialEventManager> { NoOpSpecialEventManager() }

    // Achievements — syncs to server when authenticated
    single { AchievementSyncService(get()) }
    single<IAchievementsManager> { ServerAchievementsManager(get(), get(), get()) }
}

val webDataModule: Module = module {
    single<SettingsRepository> { WasmSettingsRepository() }
    single<AuthRepository> { WasmAuthRepository() }
    single<GameRepository> { NoOpGameRepository() }
    single<AnalysisCacheRepository> { InMemoryAnalysisCacheRepository() }
    // El análisis corre en un Web Worker (fuera del hilo principal del navegador).
    single<AnalysisRunner> { WorkerAnalysisRunner() }
}

val webViewModelModule: Module = module {
    viewModel { WasmSettingsViewModel(get(), get(), get()) }
    viewModel { WasmGameViewModel(get(), get()) } bind IGameModel::class
}

val webModules: List<Module> = listOf(webServiceModule) + sharedModules + listOf(webDataModule, webViewModelModule)
