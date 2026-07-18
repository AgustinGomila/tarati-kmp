package com.agustin.tarati.core.domain.ai.cache

import com.agustin.tarati.core.domain.ai.api.CacheStats
import com.agustin.tarati.core.domain.game.play.GameState

/**
 * Caché de tres niveles para la evaluación de posiciones del motor de IA.
 *
 * ## Por qué tres cachés separados
 * Cada tipo de dato tiene un ciclo de vida y un costo de cómputo distinto:
 *
 * - **fullEvaluationCache** (2000 entradas): evaluaciones completas del tablero.
 *   Son costosas de calcular (O(n) sobre todas las piezas y regiones) pero muy
 *   reutilizables, ya que la misma posición aparece en múltiples ramas del árbol.
 *
 * - **quickEvaluationCache** (500 entradas): evaluaciones rápidas usadas en
 *   el ordenamiento de movimientos. Son más baratas de calcular y necesitan
 *   rotación más frecuente por el alto volumen de llamadas en [MoveEvaluator].
 *
 * - **moveOrderingCache** (1000 entradas): el orden de movimientos calculado por
 *   [MoveEvaluator.sortMoves]. Evita reordenar los mismos movimientos para la
 *   misma posición en la misma profundidad durante la profundización iterativa.
 *
 * ## LRU mediante LruCache
 * Migrado de LinkedHashMap (JVM) a LruCache (KMP). Los tres cachés implementan
 * LRU (Least Recently Used) con evicción automática. Esta técnica evita la
 * dependencia de una librería externa y tiene overhead O(1) por acceso, siendo
 * apropiada para el tamaño de estas cachés en mobile.
 *
 * ## Relación con TranspositionTable
 * [HybridEvaluationCache] y [TranspositionTable] son complementarias:
 * la tabla de transposición almacena resultados completos de búsqueda con
 * profundidad (MoveEval), mientras que esta caché almacena evaluaciones
 * estáticas de posición y ordenamiento. Un hit en la transposición evita
 * la búsqueda completa; un hit aquí evita recomputar la evaluación del nodo.
 */
class HybridEvaluationCache(
    maxSize: Int = 2000,
    quickCacheSize: Int = 500,
    val positionHistory: Map<String, Int> = emptyMap(),
) {
    private val fullEvaluationCache = LruCache<String, Double>(maxSize)
    private val quickEvaluationCache = LruCache<String, Double>(quickCacheSize)
    private val moveOrderingCache = LruCache<String, List<String>>(1000)

    fun getFullEvaluation(gameState: GameState): Double? {
        val hash = gameState.hashBoard()
        return fullEvaluationCache[hash]?.also {
            recordAccess(hit = true)
        } ?: run {
            recordAccess(hit = false)
            null
        }
    }

    fun putFullEvaluation(
        gameState: GameState,
        score: Double,
    ) {
        fullEvaluationCache[gameState.hashBoard()] = score
    }

    fun getQuickEvaluation(gameState: GameState): Double? = quickEvaluationCache[gameState.hashBoard()]

    fun putQuickEvaluation(
        gameState: GameState,
        score: Double,
    ) {
        quickEvaluationCache[gameState.hashBoard()] = score
    }

    private fun getCacheKey(
        gameState: GameState,
        isMaximizing: Boolean,
        depth: Int,
    ): String {
        val hash = gameState.hashBoard()
        val repetitionKey = positionHistory[hash] ?: 0
        return "$hash:$isMaximizing:$depth:$repetitionKey"
    }

    fun getMoveOrdering(
        gameState: GameState,
        isMaximizing: Boolean,
        depth: Int,
    ): List<String>? = moveOrderingCache[getCacheKey(gameState, isMaximizing, depth)]

    fun putMoveOrdering(
        gameState: GameState,
        isMaximizing: Boolean,
        moves: List<String>,
        depth: Int,
    ) {
        moveOrderingCache[getCacheKey(gameState, isMaximizing, depth)] = moves
    }

    fun clear() {
        fullEvaluationCache.clear()
        quickEvaluationCache.clear()
        moveOrderingCache.clear()
    }

    fun getStats(): CacheStats =
        CacheStats(
            fullEvaluationSize = fullEvaluationCache.size,
            quickEvaluationSize = quickEvaluationCache.size,
            moveOrderingSize = moveOrderingCache.size,
            hitRate = calculateHitRate(),
        )

    private var accessCount = 0
    private var hitCount = 0

    private fun recordAccess(hit: Boolean) {
        accessCount++
        if (hit) hitCount++
    }

    private fun calculateHitRate(): Double = if (accessCount == 0) 0.0 else hitCount.toDouble() / accessCount
}