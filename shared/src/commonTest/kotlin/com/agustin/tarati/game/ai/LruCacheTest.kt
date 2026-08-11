package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.cache.LruCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [LruCache].
 *
 * Verifies the LRU contract on which TranspositionTable and
 * HybridEvaluationCache rely: capacity-bound eviction of the least recently
 * used entry, access promotion via get/put, and non-evicting updates.
 */
class LruCacheTest {

    // ── Eviction ─────────────────────────────────────────────────────────────

    @Test
    fun `evicts the least recently used entry when capacity is exceeded`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["c"] = 3 // evicts "a" (oldest, never accessed)

        assertNull(cache["a"], "Oldest entry evicted")
        assertEquals(2, cache["b"], "Recent entries survive")
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size, "Size stays at capacity")
    }

    @Test
    fun `get promotes an entry so it survives the next eviction`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["a"] // promote "a" → "b" becomes the LRU
        cache["c"] = 3 // evicts "b"

        assertEquals(1, cache["a"], "Promoted entry survives")
        assertNull(cache["b"], "Unpromoted entry evicted")
        assertEquals(3, cache["c"])
    }

    // ── Updates ──────────────────────────────────────────────────────────────

    @Test
    fun `updating an existing key does not evict and promotes it`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["a"] = 10 // update at full capacity → no eviction, "a" promoted

        assertEquals(2, cache.size, "Both entries present after update")
        assertEquals(10, cache["a"], "Value updated")
        assertEquals(2, cache["b"])
    }

    @Test
    fun `update promotes the key so the other entry is evicted next`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["a"] = 10 // "b" becomes the LRU
        cache["c"] = 3 // evicts "b"

        assertEquals(10, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }

    // ── Basics ───────────────────────────────────────────────────────────────

    @Test
    fun `remove deletes the entry and returns its value`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1

        assertEquals(1, cache.remove("a"), "remove returns the stored value")
        assertNull(cache["a"], "Entry gone after remove")
        assertNull(cache.remove("zz"), "remove on absent key returns null")
    }

    @Test
    fun `containsKey does not promote the entry`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        assertTrue(cache.containsKey("a")) // must NOT promote "a"
        cache["c"] = 3 // evicts "a" (still the LRU)

        assertFalse(cache.containsKey("a"), "containsKey did not promote")
        assertEquals(2, cache["b"])
    }

    @Test
    fun `clear empties the cache`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache["a"])
    }
}
