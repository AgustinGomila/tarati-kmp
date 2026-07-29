package features.settings

import androidx.lifecycle.viewModelScope
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.services.achievements.IAchievementsRepository
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.IBillingManager
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.services.billing.PaletteProducts
import com.agustin.tarati.services.billing.PurchaseResult
import com.agustin.tarati.services.billing.effectiveOwnedProducts
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.GildedPalette
import com.agustin.tarati.ui.theme.PaletteList
import com.agustin.tarati.ui.theme.SeasonalThemeManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.agustin.tarati.ui.theme.availablePalettes as allAvailablePalettes

/**
 * SettingsViewModel para Android con funcionalidad completa de billing y achievements.
 * 
 * Extiende [SettingsViewModel] base de shared y agrega:
 * - Gestión de compras in-app (IBillingManager)
 * - Logros de Google Play Games (IAchievementsRepository)
 * - Filtrado de paletas según achievements/compras
 * - Temas estacionales
 */
class AndroidSettingsViewModel(
    repository: SettingsRepository,
    achievementsRepository: IAchievementsRepository,
    private val billingManager: IBillingManager,
    entitlementsRepository: EntitlementsRepository,
) : SettingsViewModel(repository, entitlementsRepository) {

    // ── Billing ───────────────────────────────────────────────────────────────

    // Compras locales de Google Play (billing) ∪ entitlements del servidor (cross-platform),
    // expandido con la regla supporter (supporter desbloquea todo lo premium).
    override val purchasedProductIds: StateFlow<Set<String>> = combine(
        billingManager.purchasedProductIds,
        entitlementsRepository.entitlements,
    ) { local, server -> effectiveOwnedProducts(local + server) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet(),
    )

    // True si Gilded está desbloqueada: comprada (billing), poseída (server) o supporter.
    private val gildedUnlockedFlow = combine(
        billingManager.purchasedProductIds,
        entitlementsRepository.entitlements,
    ) { local, server -> PaletteProducts.GILDED in effectiveOwnedProducts(local + server) }

    // ── Paletas disponibles (filtradas por achievements y fecha) ──────────────

    // Paletas disponibles según achievements + billing (sin Gilded si no está comprado).
    private val achievementsPaletteListFlow = combine(
        achievementsRepository.halloweenUnlocked,
        achievementsRepository.christmasUnlocked,
        achievementsRepository.auroraUnlocked,
        achievementsRepository.emberUnlocked,
    ) { halloween, christmas, aurora, ember ->
        allAvailablePalettes.filter { palette ->
            palette.name != GildedPalette.name &&
                    SeasonalThemeManager.isPaletteAvailable(
                        paletteName = palette.name,
                        halloweenUnlocked = halloween,
                        christmasUnlocked = christmas,
                        auroraUnlocked = aurora,
                        emberUnlocked = ember,
                    )
        }
    }

    override val availablePalettes: StateFlow<PaletteList> = combine(
        achievementsPaletteListFlow,
        gildedUnlockedFlow,
    ) { basePalettes, gildedUnlocked ->
        PaletteList(items = if (gildedUnlocked) basePalettes + GildedPalette else basePalettes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PaletteList(
            items = allAvailablePalettes.filter { palette ->
                palette.name != GildedPalette.name &&
                        SeasonalThemeManager.isPaletteAvailable(
                            paletteName = palette.name,
                            halloweenUnlocked = false,
                            christmasUnlocked = false,
                            auroraUnlocked = false,
                            emberUnlocked = false,
                        )
            },
        ),
    )

    // Siempre incluye Gilded — para que el selector lo muestre aunque esté bloqueado.
    override val allPalettesForSelector: StateFlow<PaletteList> = availablePalettes
        .map { available ->
            if (available.items.any { it.name == GildedPalette.name }) available
            else PaletteList(available.items + GildedPalette)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PaletteList(
                items = allAvailablePalettes.filter { palette ->
                    palette.name != GildedPalette.name &&
                            SeasonalThemeManager.isPaletteAvailable(
                                paletteName = palette.name,
                                halloweenUnlocked = false,
                                christmasUnlocked = false,
                                auroraUnlocked = false,
                                emberUnlocked = false,
                            )
                } + GildedPalette,
            ),
        )

    override val lockedPalettes: StateFlow<LockedPalettes> = gildedUnlockedFlow
        .map { gildedUnlocked ->
            if (gildedUnlocked) LockedPalettes.None
            else LockedPalettes(setOf(GildedPalette.name))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LockedPalettes(setOf(GildedPalette.name)),
        )

    // ── Inicialización: billing purchases ─────────────────────────────────────

    init {
        // Cuando una compra de tipo de pieza se completa, activar el tipo comprado.
        viewModelScope.launch {
            billingManager.purchaseResult.collect { result ->
                if (result is PurchaseResult.Success) {
                    // Activar tipo de pieza si corresponde
                    val pieceType = PieceTypes.all
                        .firstOrNull { it.productId == result.productId }
                    if (pieceType != null) setPieceType(pieceType.id)
                    // Activar paleta Gilded si fue la compra
                    if (result.productId == PaletteProducts.GILDED) setPalette(GildedPalette.name)
                }
            }
        }
    }

    // ── Billing ───────────────────────────────────────────────────────────────

    override fun launchPurchaseFlow(productId: String) {
        billingManager.launchPurchaseFlow(productId)
    }

    // ── Lógica de tema estacional ─────────────────────────────────────────────

    /**
     * Aplica el tema estacional del día si no se ha aplicado ya hoy.
     *
     * Solo modifica la paleta si existe un tema para hoy Y no se aplicó ya
     * (la clave "MM-dd" persisitida en DataStore garantiza idempotencia diaria).
     * Guarda la paleta pre-estacional para poder restaurarla al terminar el día.
     */
    fun applySeasonalThemeIfNeeded(
        repository: SettingsRepository,
        currentPaletteName: String,
        lastAppliedDate: String,
        preSeasonalPalette: String,
    ) {
        val todayKey = SeasonalThemeManager.todayKey()
        val seasonalPalette = SeasonalThemeManager.getSeasonalPaletteForToday() ?: return

        if (lastAppliedDate == todayKey) return  // ya aplicado hoy

        viewModelScope.launch {
            // Solo guardar pre-estacional si no hay uno ya guardado.
            if (preSeasonalPalette.isBlank() && !SeasonalThemeManager.isSeasonalPalette(currentPaletteName)) {
                repository.setPreSeasonalPalette(currentPaletteName)
            }
            repository.setPalette(seasonalPalette)
            repository.setSeasonalAutoAppliedDate(todayKey)
        }
    }

    /**
     * Restaura la paleta pre-estacional cuando el día estacional termina.
     */
    fun restorePreSeasonalPaletteIfNeeded(
        repository: SettingsRepository,
        currentPaletteName: String,
        preSeasonalPalette: String,
    ) {
        if (preSeasonalPalette.isBlank()) return
        if (!SeasonalThemeManager.isSeasonalPalette(currentPaletteName)) return

        viewModelScope.launch {
            repository.setPalette(preSeasonalPalette)
            repository.clearPreSeasonalPalette()
        }
    }
}