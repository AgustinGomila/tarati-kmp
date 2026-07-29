package com.agustin.tarati.services.notifications

import androidx.compose.runtime.Composable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus central de mensajes para la UI.
 *
 * - **Toasts**: encolados en un [Channel]; [ToastHost] los consume secuencialmente.
 *   Cualquier acción del toast puede cerrar el toast activo anticipadamente vía
 *   [dismissCurrentToast], que emite en [dismissRequests].
 * - **Alerts**: contenido composable guardado en [StateFlow]; [AlertHost] lo renderiza.
 *   Solo puede haber una alerta visible a la vez — una llamada nueva sobreescribe la anterior.
 *
 * Registrado como `single` en Koin. Cualquier ViewModel o composable puede inyectarlo.
 */
class UIMessageBus {

    // ── Toasts ────────────────────────────────────────────────────────────────

    /** Canal de lectura consumido por [ToastHost]. */
    val toasts: ReceiveChannel<UIMessage.Toast>
        field = Channel<UIMessage.Toast>(capacity = Channel.BUFFERED)

    fun toast(message: UIMessage.Toast) {
        toasts.trySend(message)
    }

    /** Señal para que [ToastHost] descarte el toast actual antes de que expire su duración. */
    private val _dismissRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismissRequests: SharedFlow<Unit> = _dismissRequest.asSharedFlow()

    /** Cierra el toast activo inmediatamente, sin esperar a que expire. */
    fun dismissCurrentToast() {
        _dismissRequest.tryEmit(Unit)
    }

    /** Descarta el toast activo y vacía cualquier toast encolado pendiente. */
    fun clearAllToasts() {
        while (toasts.tryReceive().isSuccess) { /* drain */
        }
        _dismissRequest.tryEmit(Unit)
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /**
     * Contenido composable de la alerta actualmente visible.
     * El lambda recibe un callback `dismiss` que debe llamarse al cerrar.
     */
    private val _alertContent =
        MutableStateFlow<(@Composable (dismiss: () -> Unit) -> Unit)?>(null)

    val alertContent: StateFlow<(@Composable (dismiss: () -> Unit) -> Unit)?> =
        _alertContent.asStateFlow()

    fun alert(content: @Composable (dismiss: () -> Unit) -> Unit) {
        _alertContent.value = content
    }

    fun clearAlert() {
        _alertContent.value = null
    }
}
