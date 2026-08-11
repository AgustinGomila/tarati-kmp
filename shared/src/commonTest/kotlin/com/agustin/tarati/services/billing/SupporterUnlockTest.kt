package com.agustin.tarati.services.billing

import com.agustin.tarati.network.models.SUPPORTER_PRODUCT_ID
import com.agustin.tarati.ui.theme.GildedPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de la regla de desbloqueo supporter (C4) — [effectiveOwnedProducts] y
 * [lockedPaletteNames]. Funciones puras: sin red, sin dispatcher, sin mocks.
 */
class SupporterUnlockTest {

    @Test
    fun `supporter unlocks every premium product`() {
        val effective = effectiveOwnedProducts(setOf(SUPPORTER_PRODUCT_ID))

        assertTrue(ALL_PREMIUM_PRODUCT_IDS.all { it in effective })
        assertTrue(PaletteProducts.GILDED in effective)
        assertTrue(PieceProducts.HEXAGON in effective)
    }

    // Nota: la lógica de supporter se prueba con `unlockAll = false` para no depender del valor de
    // `isDebugBuild` en el entorno de test (que es debug → true).

    @Test
    fun `without supporter ownership is unchanged`() {
        assertEquals(emptySet(), effectiveOwnedProducts(emptySet(), unlockAll = false))
        assertEquals(
            setOf(PieceProducts.SQUARE),
            effectiveOwnedProducts(setOf(PieceProducts.SQUARE), unlockAll = false),
        )
    }

    @Test
    fun `specific ownership unlocks only that product`() {
        val effective = effectiveOwnedProducts(setOf(PaletteProducts.GILDED), unlockAll = false)

        assertTrue(PaletteProducts.GILDED in effective)
        assertFalse(PieceProducts.HEXAGON in effective)
    }

    @Test
    fun `debug build unlocks every premium product`() {
        val effective = effectiveOwnedProducts(emptySet(), unlockAll = true)

        assertTrue(ALL_PREMIUM_PRODUCT_IDS.all { it in effective })
        assertTrue(lockedPaletteNames(emptySet(), unlockAll = true).isEmpty())
    }

    @Test
    fun `gilded is locked for a non-supporter without the purchase`() {
        assertEquals(setOf(GildedPalette.name), lockedPaletteNames(emptySet(), unlockAll = false))
    }

    @Test
    fun `gilded is unlocked for a supporter`() {
        assertTrue(lockedPaletteNames(setOf(SUPPORTER_PRODUCT_ID)).isEmpty())
    }

    @Test
    fun `gilded is unlocked when specifically owned`() {
        assertTrue(lockedPaletteNames(setOf(PaletteProducts.GILDED)).isEmpty())
    }
}
