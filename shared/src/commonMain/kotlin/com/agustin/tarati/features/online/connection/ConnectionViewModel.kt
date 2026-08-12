package com.agustin.tarati.features.online.connection


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.features.online.auth.validToken
import com.agustin.tarati.network.client.TaratiWebSocketClient
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.connection_replaced_by_other_device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

/**
 * Gestiona el estado de la conexión WebSocket al servidor de Tarati.
 *
 * Responsabilidades:
 *  - Conectar/desconectar el WebSocket.
 *  - Mantener [connectionState] actualizado ante cambios de red.
 *  - Cachear credenciales para reintento automático.
 *
 * La navegación entre pantallas (lobby, historial, etc.) es responsabilidad
 * del NavController; este ViewModel no conoce ni gestiona ubicaciones de UI.
 *
 * @param wsClient Cliente WebSocket compartido (singleton Koin).
 */
class ConnectionViewModel(
    private val wsClient: TaratiWebSocketClient,
    private val authViewModel: IAuthViewModel,
) : ViewModel(), IConnectionViewModel {

    private val logger = getLogger("ConnectionViewModel")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val accessToken: String?
        get() = lastAuthToken

    // Credenciales cacheadas para reintento
    private var lastServerUrl: String? = null
    private var lastAuthToken: String? = null
    private var lastUserInfo: OnlineUserInfo? = null

    // Job de reconexión automática — uno a la vez
    private var reconnectJob: Job? = null

    /** Delays entre intentos de reconexión (ms). Backoff exponencial, capped en 30s. */
    private val reconnectDelays: List<Long> = listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

    init {
        // Sincronizar con el estado de bajo nivel del WebSocketClient.
        viewModelScope.launch {
            wsClient.connectionState.collect { wsState -> syncWebSocketState(wsState) }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    override suspend fun connectToServer(
        serverUrl: String,
        authToken: String,
    ): Result<OnlineUserInfo> {
        logger.debug("connectToServer: $serverUrl")
        lastServerUrl = serverUrl
        // Token fresco (renovado si está por expirar); el pasado queda como fallback (p. ej. guest).
        val token = authViewModel.validToken() ?: authToken
        lastAuthToken = token
        _connectionState.value = ConnectionState.Connecting

        return try {
            wsClient.connect(token)

            val userInfo = resolveUserInfo()
            lastUserInfo = userInfo
            _connectionState.value = ConnectionState.Online(userInfo = userInfo)
            logger.debug("Connected as ${userInfo.username}")
            Result.success(userInfo)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Connection failed: ${e.message}")
            _connectionState.value = ConnectionState.Error(
                message = e.message ?: "Unknown error",
            )
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        logger.debug("disconnect")
        reconnectJob?.cancel()
        reconnectJob = null
        // Marcar Offline antes de desconectar el WS para que syncWebSocketState
        // no interprete el Disconnected entrante como caída inesperada.
        _connectionState.value = ConnectionState.Offline
        lastServerUrl = null
        lastAuthToken = null
        lastUserInfo = null
        wsClient.disconnect()
    }

    override suspend fun retryConnection() {
        logger.debug("retryConnection")
        val url = lastServerUrl
        val token = lastAuthToken
        if (url == null || token == null) {
            logger.debug("Cannot retry: no cached credentials")
            _connectionState.value = ConnectionState.Error(
                message = "No cached credentials for retry",
                isRecoverable = false,
            )
            return
        }
        connectToServer(url, token)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sincroniza [connectionState] ante cambios del WebSocketClient de bajo nivel.
     *
     * Durante [ConnectionState.Reconnecting] solo acepta [TaratiWebSocketClient.ConnectionState.Connected]
     * para evitar que los estados intermedios del WS interrumpan el bucle de reconexión.
     */
    private fun syncWebSocketState(wsState: TaratiWebSocketClient.ConnectionState) {
        logger.debug("wsState: ${wsState::class.simpleName}")

        // Reemplazo por otra conexión de la misma cuenta (web + desktop, p. ej.): el servidor
        // cerró este WS con VIOLATED_POLICY. NO reconectar — de lo contrario ambos dispositivos
        // se expulsan en bucle. Cortar cualquier intento en curso y avisar al usuario.
        if (wsState is TaratiWebSocketClient.ConnectionState.Replaced) {
            if (_connectionState.value == ConnectionState.Offline) return  // disconnect intencional
            logger.debug("Connection replaced by another device — not reconnecting")
            reconnectJob?.cancel()
            reconnectJob = null
            viewModelScope.launch {
                val msg = runCatching { getString(Res.string.connection_replaced_by_other_device) }
                    .getOrDefault("Session is open on another device")
                _connectionState.value = ConnectionState.Error(message = msg)
            }
            return
        }

        // Durante reconexión automática, solo la conexión exitosa cambia el estado.
        if (_connectionState.value is ConnectionState.Reconnecting) {
            if (wsState is TaratiWebSocketClient.ConnectionState.Connected) {
                val userInfo = lastUserInfo
                if (userInfo != null) {
                    reconnectJob?.cancel()
                    reconnectJob = null
                    _connectionState.value = ConnectionState.Online(userInfo)
                    logger.debug("Reconnected successfully")
                }
            }
            return
        }

        when (wsState) {
            is TaratiWebSocketClient.ConnectionState.Disconnected -> {
                val current = _connectionState.value
                if (current is ConnectionState.Online) {
                    logger.debug("Unexpected drop — starting auto-reconnect")
                    autoReconnect(current.userInfo)
                }
            }

            is TaratiWebSocketClient.ConnectionState.Connecting -> {
                if (_connectionState.value !is ConnectionState.Connecting &&
                    _connectionState.value !is ConnectionState.Online
                ) {
                    _connectionState.value = ConnectionState.Connecting
                }
            }

            is TaratiWebSocketClient.ConnectionState.Connected -> {
                val userInfo = lastUserInfo
                if (userInfo != null && _connectionState.value !is ConnectionState.Online) {
                    _connectionState.value = ConnectionState.Online(userInfo = userInfo)
                }
            }

            is TaratiWebSocketClient.ConnectionState.Error -> {
                // Ignorar errores de bajo nivel durante disconnect intencional.
                // disconnect() setea Offline antes de cancelar el job; la cancelación
                // del coroutine puede lanzar una excepción que llega aquí como Error.
                if (_connectionState.value == ConnectionState.Offline) return
                _connectionState.value = ConnectionState.Error(
                    message = wsState.message,
                )
            }
        }
    }

    /**
     * Bucle de reconexión automática con backoff exponencial.
     *
     * Reemplaza cualquier intento previo en curso. Se cancela limpiamente
     * si [disconnect] es llamado mientras está activo.
     */
    private fun autoReconnect(userInfo: OnlineUserInfo) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for ((index, delayMs) in reconnectDelays.withIndex()) {
                _connectionState.value = ConnectionState.Reconnecting(userInfo, attempt = index + 1)
                delay(delayMs.milliseconds)
                if (_connectionState.value !is ConnectionState.Reconnecting) return@launch
                try {
                    lastUserInfo = userInfo
                    // Token fresco en cada reintento (renovado si está por expirar).
                    val token = authViewModel.validToken() ?: lastAuthToken
                    lastAuthToken = token
                    wsClient.connect(token)
                    // Éxito: syncWebSocketState(Connected) habrá seteado Online ya
                    logger.debug("Auto-reconnect attempt ${index + 1} succeeded")
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.debug("Auto-reconnect attempt ${index + 1} failed: ${e.message}")
                }
            }
            logger.debug("Auto-reconnect exhausted — going Offline")
            _connectionState.value = ConnectionState.Offline
        }
    }

    /** Construye [OnlineUserInfo] a partir del usuario autenticado en [authViewModel]. */
    private fun resolveUserInfo(): OnlineUserInfo {
        val user = authViewModel.currentUser
        return if (user != null) {
            OnlineUserInfo(
                userId = user.userId,
                username = user.username,
                displayName = user.displayName,
                rating = user.rating,
            )
        } else {
            OnlineUserInfo(userId = "", username = "Unknown")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    // No hay onCleared: ConnectionViewModel es un singleton de sesión. El WebSocket
    // solo se cierra explícitamente via disconnect() — nunca por navegación.
}