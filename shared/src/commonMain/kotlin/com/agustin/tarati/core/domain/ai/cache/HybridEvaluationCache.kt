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
 *
 * ## Conciencia de la config ([setConfigToken])
 * Las evaluaciones y el orden de jugadas **dependen de los pesos de
 * [com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig]** (una posición vale distinto en
 * MEDIUM que en HARD). Por eso todas las claves llevan como prefijo un *token* que identifica la
 * config activa: entradas calculadas bajo una config **no** se reusan bajo otra. Es imprescindible
 * cuando un mismo motor evalúa con configs distintas — p. ej. IA-vs-IA con niveles distintos, donde
 * ambos lados comparten esta caché — para no devolver evaluaciones de la config equivocada.
 * [com.agustin.tarati.core.domain.ai.engine.TaratiAI.setConfig] actualiza el token en cada cambio.
 */
class HybridEvaluationCache(
    maxSize: Int = 2000,
    quickCacheSize: Int = 500,
    val positionHistory: Map<String, Int> = emptyMap(),
) {
    private val fullEvaluationCache = LruCache<String, Double>(maxSize)
    private val quickEvaluationCache = LruCache<String, Double>(quickCacheSize)
    private val moveOrderingCache = LruCache<String, List<String>>(1000)

    // Discriminador de la config activa, antepuesto a toda clave (ver KDoc "Conciencia de la config").
    // Vacío hasta el primer [setConfigToken]; con una sola config es indistinto, así que el default es
    // seguro para usos que nunca cambian de config.
    private var configToken: String = ""

    /**
     * Fija el token que identifica la [EvaluationConfig] activa. Debe llamarse cuando la config del
     * motor cambia (lo hace [com.agustin.tarati.core.domain.ai.engine.TaratiAI.setConfig]). No borra
     * las entradas existentes: quedan namespaced por su propio token y conviven con las de otra config.
     */
    fun setConfigToken(token: String) {
        configToken = token
    }

    fun getFullEvaluation(gameState: GameState): Double? {
        return fullEvaluationCache[evalKey(gameState)]?.also {
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
        fullEvaluationCache[evalKey(gameState)] = score
    }

    fun getQuickEvaluation(gameState: GameState): Double? = quickEvaluationCache[evalKey(gameState)]

    fun putQuickEvaluation(
        gameState: GameState,
        score: Double,
    ) {
        quickEvaluationCache[evalKey(gameState)] = score
    }

    // Clave de evaluación estática (full/quick): token de config + hash de la posición.
    private fun evalKey(gameState: GameState): String = "$configToken|${gameState.hashBoard()}"

    private fun getCacheKey(
        gameState: GameState,
        isMaximizing: Boolean,
        depth: Int,
    ): String {
        val hash = gameState.hashBoard()
        val repetitionKey = positionHistory[hash] ?: 0
        return "$configToken|$hash:$isMaximizing:$depth:$repetitionKey"
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