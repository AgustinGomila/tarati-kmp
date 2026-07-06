package com.agustin.tarati.features.online


import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.achievements.AchievementsViewModel
import com.agustin.tarati.features.achievements.IAchievementsViewModel
import com.agustin.tarati.features.game6.MpLobbyViewModel
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.features.online.auth.AuthViewModel
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.connection.ConnectionViewModel
import com.agustin.tarati.features.online.connection.IConnectionViewModel
import com.agustin.tarati.features.online.game.IOnlineGameViewModel
import com.agustin.tarati.features.online.game.OnlineGameViewModel
import com.agustin.tarati.features.online.lobby.IOnlineLobbyViewModel
import com.agustin.tarati.features.online.lobby.OnlineLobbyRepository
import com.agustin.tarati.features.online.lobby.OnlineLobbyViewModel
import com.agustin.tarati.features.online.social.ILeaderboardViewModel
import com.agustin.tarati.features.online.social.IPublicProfileViewModel
import com.agustin.tarati.features.online.social.LeaderboardViewModel
import com.agustin.tarati.features.online.social.PublicProfileViewModel
import com.agustin.tarati.features.online.social.SocialRepository
import com.agustin.tarati.features.online.supporter.ISupporterViewModel
import com.agustin.tarati.features.online.supporter.SupporterViewModel
import com.agustin.tarati.features.online.tournament.ITournamentViewModel
import com.agustin.tarati.features.online.tournament.TournamentRepository
import com.agustin.tarati.features.online.tournament.TournamentViewModel
import com.agustin.tarati.network.client.MpOnlineClient
import com.agustin.tarati.network.client.OnlineGameClient
import com.agustin.tarati.network.client.TaratiWebSocketClient
import com.agustin.tarati.services.billing.EntitlementSyncService
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.EntitlementsRepositoryImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Módulo de Koin para componentes online
 *
 * Configura:
 * - Cliente HTTP con WebSocket support
 * - WebSocketClient para comunicación con servidor
 * - OnlineGameClient (API de alto nivel)
 * - ViewModels de online (Connection, OnlineGame)
 *
 * ## Configuración:
 *
 * Este módulo debe ser agregado a la lista de módulos de Koin
 * en cada plataforma:
 *
 * ```kotlin
 * // Android (KoinModules.kt)
 * val androidModules = listOf(
 *     sharedModules,
 *     onlineModule,  // ← Agregar aquí
 *     androidDataModule,
 *     // ...
 * )
 *
 * // Desktop (DesktopModule.kt)
 * val desktopModules = listOf(
 *     sharedModules,
 *     onlineModule,  // ← Agregar aquí
 *     desktopServiceModule,
 *     // ...
 * )
 * ```
 *
 * ## Dependencias:
 *
 * Los ViewModels dependen de:
 * - `OnlineGameClient` (inyectado automáticamente)
 * - `TaratiWebSocketClient` (inyectado automáticamente)
 * - `IGameModel` (debe estar ya registrado en sharedModules)
 *
 * ## Configuración del servidor:
 *
 * Por defecto, el servidor apunta a localhost:8080 para desarrollo.
 * Para producción, actualizar SERVER_URL en la configuración:
 *
 * ```kotlin
 * // En SettingsRepository o archivo de config
 * const val SERVER_URL = "wss://tarati.com"
 * const val AUTH_TOKEN = "..." // Token JWT del usuario
 * ```
 */
// Logger del proyecto (PlatformLogger) — el `logger` del Scope de Koin no acepta Throwable,
// así que se usa este para que los errores lleguen con stack trace al canal estándar.
private val moduleLogger = getLogger("OnlineModule")

val onlineModule: Module = module {

    // ============ HTTP Client with WebSocket support ============

    /**
     * NOTA: HttpClient NO se crea aquí porque necesita un engine específico
     * de cada plataforma (Android, Desktop).
     *
     * Cada plataforma debe registrar HttpClient en su módulo:
     * - Android: HttpClient(Android) o HttpClient(OkHttp)
     * - Desktop: HttpClient(CIO) o HttpClient(Java)
     *
     * Ver: androidModule, desktopModule
     */

    // HttpClient ya debe estar registrado por la plataforma ✅

    // ============ WebSocket Client ============

    // TaratiWebSocketClient: serverUrl = devServerUrl (platform-specific para dev/staging).
    // El token JWT se obtiene dinámicamente de authRepository en cada conexión.
    single {
        moduleLogger.info("🔧 [Koin] Creating TaratiWebSocketClient...")
        try {
            val serverUrl = devServerUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")

            TaratiWebSocketClient(
                httpClient = get(),
                serverUrl = serverUrl,
                authRepository = get()  // ✅ Obtiene token dinámicamente
            ).also {
                moduleLogger.info("✅ [Koin] TaratiWebSocketClient created successfully")
            }
        } catch (e: Exception) {
            moduleLogger.error("❌ [Koin] Failed to create TaratiWebSocketClient", e)
            throw e
        }
    }

    // ============ Online Game Client ============

    /**
     * Cliente de juego online (API de alto nivel)
     *
     * Envuelve TaratiWebSocketClient y proporciona una API más simple
     * para matchmaking, gameplay, spectating y chat.
     *
     * Singleton compartido por todos los ViewModels.
     */
    single {
        moduleLogger.info("🔧 [Koin] Creating OnlineGameClient...")
        try {
            OnlineGameClient(
                wsClient = get()
            ).also {
                moduleLogger.info("✅ [Koin] OnlineGameClient created successfully")
            }
        } catch (e: Exception) {
            moduleLogger.error("❌ [Koin] Failed to create OnlineGameClient", e)
            throw e
        }
    }

    // ============ Multiplayer (game6) Online Client ============

    /**
     * Cliente del juego multijugador online (M7). Reusa el **mismo** [TaratiWebSocketClient]: consume
     * su flujo de mensajes (filtrando los MP) y envía por su `send`. Singleton compartido por el
     * lobby de mesas y la partida MP online.
     */
    single {
        val ws = get<TaratiWebSocketClient>()
        MpOnlineClient(
            incoming = ws.messages,
            sendMessage = ws::send,
        )
    }

    // ============ ViewModels ============

    /**
     * AuthViewModel
     *
     * Gestiona autenticación y tokens.
     * Versión simplificada para Fase 7 - solo gestión de tokens.
     */
    /**
     * AuthViewModel — singleton de sesión.
     *
     * Registrado como `single` porque los tokens JWT son estado global compartido
     * por todas las pantallas (GameScreen, OnlineLobbyScreen, etc.). Si se registrara
     * como `viewModel`, cada pantalla obtendría su propia instancia con su propio
     * `_accessToken`, y un refresh en una instancia no se propagaría a las demás.
     *
     * Inyectar con `koinInject<IAuthViewModel>()` en composables.
     */
    single<IAuthViewModel> {
        AuthViewModel(
            authRepository = get(),
            httpClient = get(),
            entitlementsRepository = get(),
            achievementsManager = getOrNull(),
        )
    }

    // ============ Entitlements (ownership cross-platform C2) ============

    single { EntitlementSyncService(httpClient = get()) }

    single<EntitlementsRepository> {
        EntitlementsRepositoryImpl(syncService = get(), authRepository = get())
    }

    // Pantalla Supporter (pago Stripe C3) — scope de pantalla.
    viewModel {
        SupporterViewModel(entitlementsRepository = get())
    } bind ISupporterViewModel::class

    /**
     * ConnectionViewModel — singleton de sesión.
     *
     * Registrado como `single` en lugar de `viewModel` porque el estado de
     * conexión WebSocket es global: debe sobrevivir la navegación entre pantallas
     * (GameScreen → OnlineLobbyScreen → GameScreen) y no debe destruirse al salir
     * de una pantalla particular. Como singleton, `onCleared` nunca se llama al
     * navegar — solo al terminar el proceso.
     */
    single<IConnectionViewModel> {
        ConnectionViewModel(wsClient = get(), authViewModel = get())
    }

    /**
     * OnlineGameViewModel — singleton de sesión.
     *
     * Debe ser `single` (no `viewModel`) porque:
     * - `OnlineGameClient` es un `single` que emite a través de `currentGame`.
     * - Si hubiera múltiples instancias de este ViewModel (ej. GameScreen en back stack
     *   + nueva instancia al navegar), cada una añadiría su propio collector a
     *   `currentGame`, duplicando todos los eventos: Starting, InProgress, Finished.
     * - Como singleton, `init` se ejecuta una sola vez y hay exactamente un collector.
     *
     * Para inyectarlo en composables: `koinInject<IOnlineGameViewModel>()`.
     */
    single {
        moduleLogger.info("🔧 [Koin] Creating OnlineGameViewModel...")
        try {
            OnlineGameViewModel(
                onlineClient = get(),
            ).also {
                moduleLogger.info("✅ [Koin] OnlineGameViewModel created successfully")
            }
        } catch (e: Exception) {
            moduleLogger.error("❌ [Koin] Failed to create OnlineGameViewModel", e)
            throw e
        }
    } bind IOnlineGameViewModel::class

// ============ Online Lobby ============

    single { OnlineLobbyRepository(httpClient = get()) }

    viewModel {
        OnlineLobbyViewModel(
            repository = get(),
            authViewModel = get(),
        )
    } bind IOnlineLobbyViewModel::class

    // Lobby de mesas del juego multijugador (M7.2). Singleton: comparte el refresco/estado con el
    // MpOnlineClient (también single). La pantalla arranca/detiene el polling.
    single {
        val authVm = get<IAuthViewModel>()
        val repo = get<OnlineLobbyRepository>()
        MpLobbyViewModel(
            client = get(),
            getToken = {
                if (authVm.isTokenExpiringSoon()) authVm.refreshToken()
                authVm.accessToken
            },
            fetchTables = repo::getMpTables,
            fetchLiveGames = repo::getMpLiveGames,
        )
    }

    // ============ Torneos ============

    single { TournamentRepository(httpClient = get()) }

    viewModel {
        TournamentViewModel(
            repository = get(),
            authViewModel = get(),
            onlineGameViewModel = get(),
        )
    } bind ITournamentViewModel::class

    // ============ Social (Leaderboard + Perfil público) ============

    single { SocialRepository(httpClient = get()) }

    viewModel {
        LeaderboardViewModel(repository = get(), authViewModel = get())
    } bind ILeaderboardViewModel::class

    viewModel { (userId: String) ->
        PublicProfileViewModel(
            userId = userId,
            repository = get(),
            authViewModel = get(),
            onlineGameViewModel = get(),
        )
    } bind IPublicProfileViewModel::class

    // ============ Achievements ============

    viewModel {
        AchievementsViewModel(
            syncService = get(),
            authViewModel = get(),
        )
    } bind IAchievementsViewModel::class
}

/**
 * Configuración para desarrollo/testing
 *
 * Permite configurar el servidor y token manualmente sin modificar código.
 *
 * Uso:
 * ```kotlin
 * startKoin {
 *     modules(
 *         onlineModule,
 *         onlineDevModule("localhost:8080", "test_token")
 *     )
 * }
 * ```
 */
fun onlineDevModule(
    serverUrl: String,
    authRepository: AuthRepository
): Module = module {
// Override del WebSocketClient con configuración custom
    single {
        TaratiWebSocketClient(
            httpClient = get(),
            serverUrl = serverUrl,
            authRepository = authRepository
        )
    }
}