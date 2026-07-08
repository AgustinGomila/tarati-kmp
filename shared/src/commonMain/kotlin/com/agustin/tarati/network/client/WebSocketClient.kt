package com.agustin.tarati.network.client


import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.network.protocol.ClientMessage
import com.agustin.tarati.network.protocol.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Cliente WebSocket compartido para Tarati Online
 *
 * Gestiona la conexión WebSocket con el servidor, incluyendo:
 * - Conexión y reconexión automática
 * - Serialización/deserialización de mensajes
 * - Heartbeat automático para mantener conexión viva
 * - Manejo de errores y desconexiones
 *
 * Este cliente es completamente KMP y funciona en Android, iOS, Desktop y Web.
 *
 * @param httpClient Cliente HTTP de Ktor configurado con WebSocket plugin
 * @param serverUrl URL del servidor (ej: "localhost:8080")
 * @param authRepository Repositorio para obtener el token JWT actual
 */
class TaratiWebSocketClient(
    private val httpClient: HttpClient,
    private val serverUrl: String,
    private val authRepository: AuthRepository
) {
    private var session: DefaultClientWebSocketSession? = null
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null  // ← Job para mantener la conexión viva
    private val logger = getLogger("TaratiWebSocketClient")

    // Scope propio del cliente (vive lo que vive el singleton). Reemplaza GlobalScope:
    // SupervisorJob aísla fallos y permite reusar el scope tras disconnect()/connect().
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _messages = MutableSharedFlow<ServerMessage>(
        replay = 0,
        extraBufferCapacity = 64
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Disconnected
    )

    /**
     * Flow de mensajes recibidos del servidor
     * Los consumidores deben suscribirse a este flow para recibir actualizaciones
     */
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    /**
     * Flow del estado de la conexión
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Configuración JSON para serialización
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        allowStructuredMapKeys = true
    }

    /**
     * Estados posibles de la conexión
     */
    sealed class ConnectionState {
        /**
         * Desconectado del servidor
         */
        data object Disconnected : ConnectionState()

        /**
         * Intentando conectar
         */
        data object Connecting : ConnectionState()

        /**
         * Conectado y operacional
         */
        data object Connected : ConnectionState()

        /**
         * Error en la conexión
         *
         * @property message Mensaje de error
         */
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Establece conexión con el servidor.
     *
     * @param token Token JWT explícito (sesiones guest en memoria). Si es null, se lee del repositorio.
     * @throws Exception si la conexión falla o no hay token disponible.
     */
    suspend fun connect(token: String? = null) {
        if (_connectionState.value == ConnectionState.Connected) {
            logger.debug("Already connected, skipping")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        logger.debug("Connecting to $serverUrl...")

        val authToken = token ?: authRepository.getToken()
        ?: throw IllegalStateException("No auth token available. Please login first.")

        // Señaliza el resultado del handshake inicial: se completa al conectar o
        // excepcionalmente al fallar. Reemplaza el busy-wait polling sobre el estado.
        val handshake = CompletableDeferred<Unit>()

        // Lanzar conexión en background job sobre el scope del cliente.
        connectionJob = scope.launch {
            try {
                httpClient.webSocket(
                    urlString = "$serverUrl/ws/game",
                    request = {
                        header("Authorization", "Bearer $authToken")
                        // El browser WebSocket API no puede enviar headers custom —
                        // incluir token también como query param para WASM/web.
                        // El servidor acepta ambos (WebSocketAuth.kt).
                        url.parameters.append("token", authToken)
                    }
                ) {
                    session = this
                    _connectionState.value = ConnectionState.Connected
                    handshake.complete(Unit)
                    logger.debug("Connected successfully")

                    // Iniciar heartbeat
                    heartbeatJob = launch {
                        startHeartbeat()
                    }

                    // Escuchar mensajes (bloquea hasta que se cierra)
                    try {
                        listenForMessages()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.debug("Listening stopped: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Connection error: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
                handshake.completeExceptionally(e)
            } finally {
                // No pisar un estado Error con Disconnected: un fallo previo ya dejó
                // el estado correcto y completó el handshake. Solo marcar Disconnected
                // cuando la conexión llegó a establecerse y luego se cerró.
                if (_connectionState.value !is ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
                heartbeatJob?.cancel()
                session = null
                if (!handshake.isCompleted) {
                    handshake.completeExceptionally(
                        CancellationException("Connection closed before established")
                    )
                }
            }
        }

        // Esperar el handshake con timeout, sin polling.
        try {
            withTimeout(CONNECT_TIMEOUT) { handshake.await() }
        } catch (e: TimeoutCancellationException) {
            connectionJob?.cancel()
            _connectionState.value = ConnectionState.Error("Connection timeout")
            throw Exception("Connection timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Connection failed: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Envía un heartbeat cada 30 segundos para mantener la conexión viva
     *
     * Muchos proxies y load balancers cierran conexiones idle después
     * de cierto tiempo. El heartbeat previene esto.
     */
    private suspend fun DefaultClientWebSocketSession.startHeartbeat() {
        while (isActive) {
            delay(30_000.milliseconds) // 30 segundos
            try {
                send(ClientMessage.Heartbeat)
                logger.debug("Heartbeat sent")
            } catch (e: CancellationException) {
                throw e  // heartbeat cancelado al cerrar la conexión
            } catch (e: Exception) {
                logger.error("Heartbeat failed: ${e.message}")
                break
            }
        }
    }

    /**
     * Escucha mensajes entrantes del servidor
     *
     * Loop infinito que procesa frames WebSocket:
     * - Frame.Text: mensajes JSON del servidor
     * - Frame.Close: servidor cerró la conexión
     * - Otros frames: ignorados
     */
    private suspend fun DefaultClientWebSocketSession.listenForMessages() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        try {
                            val message = json.decodeFromString<ServerMessage>(text)
                            logger.debug("Received: ${message::class.simpleName}")
                            _messages.emit(message)
                        } catch (e: CancellationException) {
                            throw e  // no tragar cancelación durante emit
                        } catch (e: Exception) {
                            logger.debug("Failed to parse message: ${e.message}")
                        }
                    }

                    is Frame.Close -> {
                        logger.debug("Connection closed by server")
                        break
                    }

                    else -> {
                        // Ignorar otros tipos de frame
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error in message loop: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection lost")
        }
    }

    /**
     * Envía un mensaje al servidor
     *
     * Serializa el mensaje a JSON y lo envía como Frame.Text.
     * Si no hay conexión activa, lanza IllegalStateException.
     *
     * @param message Mensaje a enviar
     * @throws IllegalStateException si no está conectado
     * @throws Exception si el envío falla
     */
    suspend fun send(message: ClientMessage) {
        val currentSession = session
            ?: throw IllegalStateException("Not connected to server")

        if (_connectionState.value != ConnectionState.Connected) {
            throw IllegalStateException("Connection is not in Connected state")
        }

        try {
            val jsonText = json.encodeToString(message)
            currentSession.send(Frame.Text(jsonText))
            logger.debug("Sent: ${message::class.simpleName}")
        } catch (e: CancellationException) {
            throw e  // propagar cancelación sin loguear un error espurio
        } catch (e: Exception) {
            logger.error("Failed to send message: ${e.message}")
            throw e
        }
    }

    /**
     * Cierra la conexión con el servidor
     *
     * Limpia recursos y cancela el heartbeat.
     * Después de llamar a disconnect(), se puede volver a llamar a connect().
     */
    fun disconnect() {
        heartbeatJob?.cancel()
        connectionJob?.cancel()  // ← Cancelar el job de conexión
        session = null
        _connectionState.value = ConnectionState.Disconnected
        logger.debug("Disconnected")
    }

    /**
     * Verifica si está actualmente conectado
     *
     * @return true si la conexión está activa y operacional
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.Connected
    }

    private companion object {
        // Tiempo máximo de espera del handshake WebSocket inicial.
        val CONNECT_TIMEOUT = 5.seconds
    }
}

/**
 * Factory para crear instancias de TaratiWebSocketClient
 *
 * Encapsula la creación del HttpClient con la configuración necesaria.
 */
object TaratiWebSocketClientFactory {

    /**
     * Crea un cliente WebSocket configurado
     *
     * @param serverUrl URL del servidor
     * @param authRepository Repositorio para obtener token JWT
     * @return Cliente WebSocket listo para usar
     */
    fun create(
        serverUrl: String,
        authRepository: AuthRepository
    ): TaratiWebSocketClient {
        val httpClient = HttpClient {
            install(WebSockets) {
                pingIntervalMillis = 20_000 // Ping cada 20 segundos
                maxFrameSize = Long.MAX_VALUE
            }
        }

        return TaratiWebSocketClient(
            httpClient = httpClient,
            serverUrl = serverUrl,
            authRepository = authRepository
        )
    }
}