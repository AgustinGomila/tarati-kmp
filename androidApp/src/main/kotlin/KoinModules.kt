package com.agustin.tarati


import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.agustin.tarati.core.data.database.MIGRATION_1_2
import com.agustin.tarati.core.data.database.MIGRATION_2_3
import com.agustin.tarati.core.data.database.MIGRATION_3_4
import com.agustin.tarati.core.data.database.TaratiDatabase
import com.agustin.tarati.core.data.database.dao.AnalysisDao
import com.agustin.tarati.core.data.database.dao.GameDao
import com.agustin.tarati.core.data.repositories.RoomAnalysisCacheRepository
import com.agustin.tarati.core.data.repositories.RoomGameRepository
import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.DefaultAnalysisRunner
import com.agustin.tarati.core.domain.repository.GameRepository
import com.agustin.tarati.di.sharedModules
import com.agustin.tarati.features.game.AndroidGameViewModel
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.online.auth.AndroidAuthRepository
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.seasonal.ISpecialEventManager
import com.agustin.tarati.features.seasonal.SpecialEventManager
import com.agustin.tarati.features.seasonal.SpecialEventRepository
import com.agustin.tarati.features.settings.AndroidSettingsRepository
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.network.taratiClientJson
import com.agustin.tarati.services.achievements.AchievementSyncService
import com.agustin.tarati.services.achievements.AchievementsManager
import com.agustin.tarati.services.achievements.AchievementsRepository
import com.agustin.tarati.services.achievements.AchievementsRepositoryImpl
import com.agustin.tarati.services.achievements.ActivityProvider
import com.agustin.tarati.services.achievements.GamesAuthManager
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.achievements.IAchievementsReporter
import com.agustin.tarati.services.achievements.IAchievementsRepository
import com.agustin.tarati.services.achievements.PlayGamesAchievementsReporter
import com.agustin.tarati.services.billing.BillingManager
import com.agustin.tarati.services.billing.IBillingManager
import com.agustin.tarati.services.clipboard.ClipboardServiceImpl
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.clipboard.IClipboardService
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.services.sound.SoundManager
import com.agustin.tarati.services.sound.SoundServiceImpl
import com.agustin.tarati.services.url.AndroidUrlLauncher
import com.agustin.tarati.services.url.IUrlLauncher
import com.google.android.gms.games.PlayGames
import features.settings.AndroidSettingsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// ── Database ──────────────────────────────────────────────────────────────────

val databaseModule: Module = module {
    single {
        Room.databaseBuilder(
            get(),
            TaratiDatabase::class.java,
            "tarati-db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
    single<GameDao> { get<TaratiDatabase>().gameDao() }
    single<GameRepository> { RoomGameRepository(get()) }
    single<AnalysisDao> { get<TaratiDatabase>().analysisDao() }
    single<AnalysisCacheRepository> { RoomAnalysisCacheRepository(get()) }
    single<AnalysisRunner> { DefaultAnalysisRunner() }
}

// ── DataStore ─────────────────────────────────────────────────────────────────

val dataStoreModule: Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = {
                File(get<Context>().filesDir, "datastore/user_preferences.preferences_pb")
            },
        )
    }
}

// ── Settings + Auth ───────────────────────────────────────────────────────────

val appStateModule: Module = module {
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    single<AuthRepository> { AndroidAuthRepository(get()) }
}

// ── Achievements ──────────────────────────────────────────────────────────────

val achievementsModule: Module = module {
    single { ActivityProvider() }
    single { AchievementsRepository(get()) }
    single<IAchievementsReporter> { PlayGamesAchievementsReporter(get(), get()) }
    single { AchievementSyncService(get()) }
    single { AchievementsManager(get(), get(), get(), get(), get(), get(), get()) } bind IAchievementsManager::class

    /**
     * Activity - Inyectada desde ActivityProvider.
     * IMPORTANTE: Se debe llamar a ActivityProvider.set(activity) desde MainActivity.onResume()
     * antes de resolver cualquier dependencia que necesite Activity.
     */
    factory<Activity> {
        get<ActivityProvider>().get()
            ?: error("Activity not available. Call ActivityProvider.set() in MainActivity.onResume() first.")
    }

    /**
     * GamesAuthManager - Gestiona autenticación con Google Play Games.
     * Single: Una instancia compartida en toda la app.
     */
    single {
        GamesAuthManager(
            activity = get()  // Activity inyectado por Koin
        )
    }

    /**
     * IAchievementsRepository - Implementación con Google Play Games.
     * Single: Una instancia compartida que mantiene el estado de achievements.
     */
    single<IAchievementsRepository> {
        val activity: Activity = get()

        AchievementsRepositoryImpl(
            gamesSignInClient = PlayGames.getGamesSignInClient(activity),
            achievementsClient = PlayGames.getAchievementsClient(activity)
        )
    }
}

// ── Special Events ────────────────────────────────────────────────────────────

val specialEventModule: Module = module {
    single { SpecialEventRepository(get()) }
    single<ISpecialEventManager> {
        SpecialEventManager(
            specialEventRepository = get(),
            achievementsRepository = get(),
            settingsRepository = get(),
            reporter = get(),
            syncService = get(),
            authRepository = get(),
        )
    }
}

// ── Billing ───────────────────────────────────────────────────────────────────

val billingModule: Module = module {
    single<IBillingManager> {
        BillingManager(
            context = get(),
            activityProvider = get(),
            entitlementsRepository = get()
        )
    }
}

// ── Services ──────────────────────────────────────────────────────────────────

val androidServiceModule: Module = module {
    // HttpClient con engine Android (OkHttp) para WebSockets y REST
    single {
        HttpClient(OkHttp) {
            install(WebSockets) {
                pingInterval = 30.toDuration(DurationUnit.SECONDS)
            }
            install(ContentNegotiation) {
                json(taratiClientJson)
            }
        }
    }

    single<IClipboardService> { ClipboardServiceImpl(get()) }
    single { GameClipboardHelper(get()) }
    single<IUrlLauncher> { AndroidUrlLauncher(get()) }
}

// ── Sound ─────────────────────────────────────────────────────────────────────

val soundModule: Module = module {
    single<SoundManager> { SoundManager(get()) }
    single<ISoundService> { SoundServiceImpl(get()) }
}

// ── ViewModels ────────────────────────────────────────────────────────────────

val androidViewModelModule: Module = module {
    // GameViewModel con SavedStateHandle (Android-specific)
    viewModel { params -> AndroidGameViewModel(params.get(), get(), get()) } bind IGameModel::class

    viewModel { AndroidSettingsViewModel(get(), get(), get(), get()) }
}

// ── Lista completa de módulos Android ────────────────────────────────────────

/**
 * Todos los módulos necesarios para Android.
 *
 * IMPORTANTE: androidServiceModule va PRIMERO porque crea HttpClient
 * que es necesario para onlineModule (que está en sharedModules).
 *
 * Uso en TaratiApplication:
 * ```kotlin
 * startKoin { modules(androidModules) }
 * ```
 */
val androidModules: List<Module> = listOf(
    androidServiceModule,  // PRIMERO: Crear HttpClient
) + sharedModules + listOf(
    databaseModule,
    dataStoreModule,
    appStateModule,
    achievementsModule,
    specialEventModule,
    billingModule,
    soundModule,
    androidViewModelModule,
)