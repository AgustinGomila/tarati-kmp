package com.agustin.tarati.core.domain.ai.cache

/**
 * Implementación LRU (Least Recently Used) cache para Kotlin Multiplatform.
 *
 * ## Propósito
 * En KMP, LinkedHashMap no tiene el constructor con accessOrder ni permite
 * sobrescribir removeEldestEntry(). Esta clase replica ese comportamiento:
 * mantiene un límite de capacidad y elimina automáticamente el elemento
 * menos recientemente usado cuando se alcanza el límite.
 *
 * ## Implementación — O(1) amortizado por acceso
 * Un único LinkedHashMap (orden de inserción) simula el orden de acceso:
 * cada get/put reinserta la clave (remove + put), moviéndola al final del
 * orden de iteración. La entrada menos recientemente usada queda siempre
 * primera, y se desaloja con el iterador en O(1). Sin lista auxiliar de
 * orden de acceso (que costaría O(n) por acceso).
 *
 * ## Uso
 * ```kotlin
 * val cache = LruCache<String, Double>(maxSize = 100)
 * cache.put("key", 42.0)
 * val value = cache.get("key")  // Marca "key" como recientemente usado
 * ```
 *
 * @param maxSize Capacidad máxima del cache. Cuando se excede, se elimina
 *                el elemento menos recientemente usado.
 */
class LruCache<K, V>(
    private val maxSize: Int
) {
    // LinkedHashMap mantiene orden de inserción; la reinserión en cada acceso
    // lo convierte en orden de acceso (el primero es el candidato a evicción).
    private val map = LinkedHashMap<K, V>()

    val size: Int
        get() = map.size

    /**
     * Obtiene un valor y lo marca como recientemente usado.
     * Si la key no existe, retorna null.
     */
    operator fun get(key: K): V? {
        val value = map.remove(key) ?: return null

        // Reinsertar al final (más reciente)
        map[key] = value

        return value
    }

    /**
     * Inserta o actualiza un valor (moviéndolo al final, más reciente).
     * Si se alcanza maxSize, elimina el elemento menos recientemente usado.
     */
    fun put(key: K, value: V) {
        // Quitar la entrada previa si existe — la reinserción la mueve al final
        // y una actualización no debe disparar evicción.
        map.remove(key)

        if (map.size >= maxSize) {
            // Eliminar el elemento menos recientemente usado (primero en iteración)
            val eldest = map.keys.iterator()
            if (eldest.hasNext()) {
                eldest.next()
                eldest.remove()
            }
        }

        map[key] = value
    }

    /**
     * Permite usar sintaxis de corchetes: cache[key] = value
     */
    operator fun set(key: K, value: V) {
        put(key, value)
    }

    /**
     * Elimina todas las entradas del cache.
     */
    fun clear() {
        map.clear()
    }

    /**
     * Verifica si una key existe en el cache (sin marcarla como accedida).
     */
    fun containsKey(key: K): Boolean = map.containsKey(key)

    /**
     * Elimina una key específica del cache.
     */
    fun remove(key: K): V? = map.remove(key)
}
