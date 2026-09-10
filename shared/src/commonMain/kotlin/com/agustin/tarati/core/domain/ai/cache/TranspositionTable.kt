package com.agustin.tarati.core.domain.ai.cache

import com.agustin.tarati.core.domain.ai.evaluator.MoveEval

/**
 * Tabla de transposición para el motor Minimax.
 *
 * ## Propósito
 * En Tarati, el mismo estado del tablero puede alcanzarse por distintas
 * secuencias de movimientos (transposiciones). Sin esta tabla, el árbol Minimax
 * evaluaría el mismo nodo repetidamente. La tabla actúa como una caché de
 * resultados de búsqueda indexados por hash de posición.
 *
 * ## Condición de validez por profundidad
 * Una entrada solo se usa si fue calculada a una profundidad **mayor o igual**
 * a la profundidad actual de búsqueda (`it.depth >= depth`). Una entrada de
 * profundidad menor sería una aproximación menos precisa y podría corromper
 * el resultado — especialmente en profundización iterativa, donde la misma
 * posición se busca a profundidades crecientes.
 *
 * ## Conciencia de la config ([setConfigToken])
 * Las entradas dependen de la [com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig] con la
 * que se calcularon: distintos pesos (Easy/Medium/Hard/Champion) y distinta profundidad producen
 * resultados de búsqueda distintos, y reutilizar uno bajo otra config degradaría el juego. Por eso
 * toda clave lleva como prefijo un *token* que identifica la config activa: las entradas de configs
 * distintas quedan namespaced y conviven sin pisarse, sin necesidad de vaciar la tabla al cambiar de
 * dificultad (imprescindible en IA-vs-IA de niveles distintos, donde ambos lados comparten la tabla).
 * [com.agustin.tarati.core.domain.ai.engine.TaratiAI.setConfig] actualiza el token en cada cambio.
 *
 * ## LRU mediante LruCache
 * Migrado de LinkedHashMap (JVM) a LruCache (KMP): capacidad máxima con evicción
 * del elemento menos recientemente usado. Mantiene el uso de memoria acotado
 * en dispositivos con RAM limitada sin lógica adicional.
 */
class TranspositionTable(
    maxSize: Int = 10000,
) {
    private val table = LruCache<String, TranspositionEntry>(maxSize)

    // Discriminador de la config activa, antepuesto a toda clave (ver KDoc "Conciencia de la config").
    private var configToken: String = ""

    /**
     * Fija el token que identifica la [EvaluationConfig] activa. Debe llamarse cuando la config del
     * motor cambia (lo hace [com.agustin.tarati.core.domain.ai.engine.TaratiAI.setConfig]).
     */
    fun setConfigToken(token: String) {
        configToken = token
    }

    fun size(): Int = table.size

    fun get(
        key: String,
        depth: Int,
    ): MoveEval? = table["$configToken|$key"]?.takeIf { it.depth >= depth }?.result

    fun put(
        key: String,
        depth: Int,
        result: MoveEval,
    ) {
        table["$configToken|$key"] = TranspositionEntry(depth, result)
    }

    fun clear() {
        table.clear()
    }

    private data class TranspositionEntry(
        val depth: Int,
        val result: MoveEval,
    )
}