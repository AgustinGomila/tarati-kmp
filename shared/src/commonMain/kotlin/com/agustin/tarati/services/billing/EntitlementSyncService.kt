package com.agustin.tarati.services.billing

import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.authPost
import com.agustin.tarati.network.models.EntitlementsResponse
import com.agustin.tarati.network.models.GooglePlayPurchaseRequest
import com.agustin.tarati.network.models.PolarCheckoutRequest
import com.agustin.tarati.network.models.PolarCheckoutResponse
import com.agustin.tarati.network.models.StripeCheckoutRequest
import com.agustin.tarati.network.models.StripeCheckoutResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Cliente HTTP para los endpoints de entitlements del servidor de Tarati.
 *
 * Molde de [com.agustin.tarati.services.achievements.AchievementSyncService]:
 * los fallos de red se capturan internamente y se reportan como [Result.Failure]
 * o [Boolean] false — el caller decide cómo reaccionar.
 */
class EntitlementSyncService(private val httpClient: HttpClient) {

    private val baseUrl = devServerUrl

    /**
     * Obtiene los `productId` activos del usuario autenticado.
     * Usado para poblar el estado local de ownership al iniciar sesión.
     */
    suspend fun getActive(token: String): Result<List<String>> =
        httpClient.authGet<EntitlementsResponse>("$baseUrl/api/entitlements", token)
            .map { it.productIds }

    /**
     * Envía un recibo de Google Play para validación server-side.
     * @return true si el servidor validó y concedió el producto (HTTP 2xx).
     */
    suspend fun validateGooglePlay(token: String, productId: String, purchaseToken: String): Boolean =
        httpClient.authPost<Unit>("$baseUrl/api/entitlements/google-play", token) {
            contentType(ContentType.Application.Json)
            setBody(GooglePlayPurchaseRequest(productId, purchaseToken))
        }.isSuccess

    /**
     * Crea un Stripe Checkout Session para el pago Supporter (fase C3, Desktop/Web).
     * @return La URL de Checkout a abrir en el browser, o [Result.failure] si el server
     *         no pudo crearlo (503 sin credenciales, 502 error de Stripe, red).
     */
    suspend fun createStripeCheckout(token: String, amountCents: Int, interval: String): Result<String> =
        httpClient.authPost<StripeCheckoutResponse>("$baseUrl/api/checkout/stripe", token) {
            contentType(ContentType.Application.Json)
            setBody(StripeCheckoutRequest(amountCents, interval))
        }.map { it.checkoutUrl }

    /**
     * Crea un Checkout de Polar (proveedor activo Web/Desktop). Sin monto: Polar lo cobra en
     * su página. @return la URL a abrir en el browser, o [Result.failure].
     */
    suspend fun createPolarCheckout(token: String, interval: String): Result<String> =
        httpClient.authPost<PolarCheckoutResponse>("$baseUrl/api/checkout/polar", token) {
            contentType(ContentType.Application.Json)
            setBody(PolarCheckoutRequest(interval))
        }.map { it.checkoutUrl }
}
