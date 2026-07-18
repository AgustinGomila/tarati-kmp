package com.agustin.tarati.game.ai

import com.agustin.tarati.core.domain.ai.cache.LruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertNull("Oldest entry evicted", cache["a"])
        assertEquals("Recent entries survive", 2, cache["b"])
        assertEquals(3, cache["c"])
        assertEquals("Size stays at capacity", 2, cache.size)
    }

    @Test
    fun `get promotes an entry so it survives the next eviction`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["a"] // promote "a" → "b" becomes the LRU
        cache["c"] = 3 // evicts "b"

        assertEquals("Promoted entry survives", 1, cache["a"])
        assertNull("Unpromoted entry evicted", cache["b"])
        assertEquals(3, cache["c"])
    }

    // ── Updates ──────────────────────────────────────────────────────────────

    @Test
    fun `updating an existing key does not evict and promotes it`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        cache["a"] = 10 // update at full capacity → no eviction, "a" promoted

        assertEquals("Both entries present after update", 2, cache.size)
        assertEquals("Value updated", 10, cache["a"])
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

        assertEquals("remove returns the stored value", 1, cache.remove("a"))
        assertNull("Entry gone after remove", cache["a"])
        assertNull("remove on absent key returns null", cache.remove("zz"))
    }

    @Test
    fun `containsKey does not promote the entry`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2

        assertTrue(cache.containsKey("a")) // must NOT promote "a"
        cache["c"] = 3 // evicts "a" (still the LRU)

        assertFalse("containsKey did not promote", cache.containsKey("a"))
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
