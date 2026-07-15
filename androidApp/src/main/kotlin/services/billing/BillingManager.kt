package com.agustin.tarati.services.billing

import android.app.Activity
import com.agustin.tarati.BuildConfig
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.services.achievements.ActivityProvider
import com.agustin.tarati.services.billing.BillingManager.Companion.MAX_RETRY_DELAY_MS
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementación de [IBillingManager] sobre [BillingClient] de Google Play.
 *
 * ## Ciclo de conexión
 * Se conecta en el constructor. Si la conexión falla o se pierde, reintenta con
 * backoff exponencial (1s, 2s, 4s … hasta [MAX_RETRY_DELAY_MS]).
 * En producción el cliente suele reconectarse al instante porque el proceso de
 * Google Play Services ya está corriendo en el dispositivo.
 *
 * ## Compras no consumibles
 * Los tipos de pieza son compras únicas (`ProductType.INAPP`). Tras completar el pago
 * Google Play emite un [Purchase] con `purchaseState == PURCHASED` que hay que
 * reconocer (`acknowledgePurchase`) en menos de 3 días o Google Play hace el reembolso.
 * [processPurchases] maneja el reconocimiento automático.
 *
 * ## Thread-safety
 * `BillingClient` exige ser llamado desde el hilo principal para `launchBillingFlow`.
 * El resto de las operaciones usan `Dispatchers.IO` para no bloquear la UI.
 */
class BillingManager(
    context: android.content.Context,
    private val activityProvider: ActivityProvider,
    private val entitlementsRepository: EntitlementsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : IBillingManager, PurchasesUpdatedListener {

    // BillingClient se construye aquí para que `this` (PurchasesUpdatedListener)
    // esté disponible sin dependencia circular en el grafo de Koin.
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private val logger = getLogger()

    private val _billingReady = MutableStateFlow(false)
    override val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _purchasedProductIds = MutableStateFlow<Set<String>>(emptySet())
    override val purchasedProductIds: StateFlow<Set<String>> = _purchasedProductIds.asStateFlow()

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1)
    override val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    // ProductDetails en caché: product id → detalles (necesarios para launchBillingFlow).
    private val productDetailsCache =
        mutableMapOf<String, ProductDetails>()

    init {
        // En builds de debug todos los productos se marcan como comprados desde el arranque,
        // permitiendo probar las piezas premium sin pasar por el flujo de pago.
        // BuildConfig.DEBUG siempre es false en release → nunca llega a producción.
        if (BuildConfig.DEBUG) {
            _purchasedProductIds.value =
                (PieceProducts.ALL_PRODUCT_IDS + PaletteProducts.ALL_PRODUCT_IDS).toSet()
            logger.debug("BillingManager: DEBUG — all piece and palette products unlocked")
        }

        connect()
    }

    // ── Conexión y reconexión ─────────────────────────────────────────────────

    private fun connect(retryDelayMs: Long = INITIAL_RETRY_DELAY_MS) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    _billingReady.value = true
                    logger.debug("BillingManager: connected")
                    scope.launch { queryOwnedPurchases() }
                    scope.launch { warmProductDetailsCache() }
                } else {
                    logger.debug("BillingManager: setup failed (${result.responseCode})")
                    scheduleReconnect(retryDelayMs)
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingReady.value = false
                logger.debug("BillingManager: disconnected — scheduling reconnect")
                scheduleReconnect(retryDelayMs)
            }
        })
    }

    private fun scheduleReconnect(delayMs: Long) {
        scope.launch {
            delay(delayMs.milliseconds)
            connect(minOf(delayMs * 2, MAX_RETRY_DELAY_MS))
        }
    }

    // ── PurchasesUpdatedListener ──────────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                scope.launch { processPurchases(purchases.orEmpty()) }
            }

            BillingResponseCode.USER_CANCELED -> {
                _purchaseResult.tryEmit(PurchaseResult.Cancelled)
            }

            else -> {
                _purchaseResult.tryEmit(
                    PurchaseResult.Error(result.responseCode, result.debugMessage),
                )
            }
        }
    }

    // ── IBillingManager ───────────────────────────────────────────────────────

    override fun launchPurchaseFlow(productId: String) {
        if (!_billingReady.value) {
            _purchaseResult.tryEmit(
                PurchaseResult.Error(
                    BillingResponseCode.SERVICE_DISCONNECTED,
                    "BillingClient not ready",
                ),
            )
            return
        }

        val activity = activityProvider.get()
        if (activity == null) {
            _purchaseResult.tryEmit(
                PurchaseResult.Error(
                    BillingResponseCode.SERVICE_UNAVAILABLE,
                    "No foreground Activity available",
                ),
            )
            return
        }

        val productDetails = productDetailsCache[productId]
        if (productDetails == null) {
            scope.launch {
                warmProductDetailsCache()
                val details = productDetailsCache[productId]
                if (details != null) {
                    withContext(Dispatchers.Main) { launchFlow(activity, details) }
                } else {
                    _purchaseResult.tryEmit(
                        PurchaseResult.Error(
                            BillingResponseCode.ITEM_UNAVAILABLE,
                            "ProductDetails not found for $productId",
                        ),
                    )
                }
            }
            return
        }

        scope.launch(Dispatchers.Main) { launchFlow(activity, productDetails) }
    }

    override suspend fun queryOwnedPurchases() {
        if (!_billingReady.value) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()

        val (result, purchases) = billingClient.queryPurchasesAsync(params)
        if (result.responseCode == BillingResponseCode.OK) {
            processPurchases(purchases)
        } else {
            logger.debug("BillingManager: queryPurchases failed (${result.responseCode})")
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Lanza el diálogo de compra de Google Play. Debe llamarse desde el hilo principal.
     */
    private fun launchFlow(
        activity: Activity,
        productDetails: ProductDetails,
    ) {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    /**
     * Procesa una lista de compras: reconoce las pendientes y actualiza
     * [purchasedProductIds] con las PURCHASED.
     */
    private suspend fun processPurchases(purchases: List<Purchase>) {
        val owned = mutableSetOf<String>()

        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue

            // Reconocer compras no reconocidas (obligatorio en < 3 días).
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ackResult = billingClient.acknowledgePurchase(ackParams)
                if (ackResult.responseCode != BillingResponseCode.OK) {
                    logger.debug(
                        "BillingManager: acknowledgePurchase failed for ${purchase.products} " +
                                "(${ackResult.responseCode})",
                    )
                }
            }

            owned.addAll(purchase.products)

            // Validar el recibo en el servidor para ownership cross-platform (C2).
            // No bloquea el estado local: si no hay sesión de Tarati o falla la red,
            // la compra sigue reconocida localmente vía [purchasedProductIds].
            for (productId in purchase.products) {
                scope.launch {
                    entitlementsRepository.validateGooglePlay(productId, purchase.purchaseToken)
                }
            }
        }

        _purchasedProductIds.value = owned

        // Emitir Success para el último producto recién comprado (no reconocido previamente).
        val newlyPurchased = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .flatMap { it.products }

        newlyPurchased.forEach { productId ->
            _purchaseResult.tryEmit(PurchaseResult.Success(productId))
        }
    }

    /**
     * Carga [ProductDetails] de todos los productos
     * de pieza desde Google Play y los guarda en [productDetailsCache].
     *
     * Se llama una vez al conectar y bajo demanda si el caché está vacío.
     */
    private suspend fun warmProductDetailsCache() {
        // Incluye el tier `supporter` (managed, precio fijo de consola) además de los à la carte.
        val ids = PieceProducts.ALL_PRODUCT_IDS + PaletteProducts.ALL_PRODUCT_IDS + SUPPORTER_PRODUCT_ID
        val productList = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val (result, details) = billingClient.queryProductDetails(params)
        if (result.responseCode == BillingResponseCode.OK) {
            details?.forEach { productDetailsCache[it.productId] = it }
            logger.debug("BillingManager: cached ${details?.size} product details")
        } else {
            logger.debug("BillingManager: queryProductDetails failed (${result.responseCode})")
        }
    }

    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 32_000L
    }
}