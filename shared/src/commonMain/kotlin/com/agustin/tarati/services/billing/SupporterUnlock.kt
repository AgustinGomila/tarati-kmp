package com.agustin.tarati.services.billing

import com.agustin.tarati.core.utils.isDebugBuild
import com.agustin.tarati.network.models.SUPPORTER_PRODUCT_ID
import com.agustin.tarati.ui.theme.GildedPalette

/**
 * Reglas de desbloqueo del modelo Supporter (fase C4).
 *
 * Los cosméticos históricamente premium (la paleta Gilded + las 6 piezas premium) son
 * ahora **supporter-only**: el entitlement `supporter` los desbloquea todos; sin él, solo
 * se desbloquean los que el usuario posee específicamente (compras à la carte previas en
 * Android, que siguen siendo válidas vía C2).
 *
 * Se reutiliza la regla [isUnlocked] de C2/C3 (`services/billing/EntitlementsRepository.kt`).
 */

/** Todos los `productId` de cosméticos premium (paletas + piezas) que el supporter desbloquea. */
val ALL_PREMIUM_PRODUCT_IDS: Set<String> =
    (PaletteProducts.ALL_PRODUCT_IDS + PieceProducts.ALL_PRODUCT_IDS).toSet()

/**
 * Ownership efectivo: si el usuario es supporter, desbloquea TODO el contenido premium;
 * si no, conserva solo lo que posee específicamente. Lo consumen los selectores vía
 * `purchasedProductIds` (la expresión `productId in purchasedIds` ya funciona).
 *
 * @param unlockAll Fuerza el desbloqueo de todo el contenido premium sin ownership. Por defecto
 *   es [isDebugBuild] → en builds **debug** se desbloquea todo para probar piezas/paletas especiales
 *   durante el desarrollo. Es un parámetro (no lee el global directamente) para que la lógica de
 *   supporter siga siendo pura y testeable con `unlockAll = false`.
 */
fun effectiveOwnedProducts(owned: Set<String>, unlockAll: Boolean = isDebugBuild): Set<String> =
    if (unlockAll || SUPPORTER_PRODUCT_ID in owned) owned + ALL_PREMIUM_PRODUCT_IDS else owned

/**
 * Nombres de paletas premium bloqueadas dado el [owned] crudo. Hoy la única paleta premium
 * es Gilded; queda bloqueada salvo que el ownership efectivo la incluya (supporter, compra o
 * [unlockAll] en debug).
 */
fun lockedPaletteNames(owned: Set<String>, unlockAll: Boolean = isDebugBuild): Set<String> =
    if (PaletteProducts.GILDED in effectiveOwnedProducts(owned, unlockAll)) emptySet()
    else setOf(GildedPalette.name)
