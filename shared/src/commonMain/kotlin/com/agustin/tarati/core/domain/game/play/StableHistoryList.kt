package com.agustin.tarati.core.domain.game.play

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * Wrapper de lista de historial de movimientos con contrato de estabilidad
 * para Compose y soporte de serialización de estado.
 *
 * ## Por qué no usar List<HistoryEntry> directamente
 * `List<T>` es una interfaz mutable en Kotlin — el compilador de Compose no
 * puede inferir su estabilidad y la trata como "runtime", lo que deshabilita
 * los skip de recomposición en los composables que la reciben. Esto causaría
 * recomposiciones innecesarias en el historial de movimientos del sidebar
 * cada vez que cualquier otro estado del juego cambiara.
 *
 * Al envolver la lista en una clase anotada con [@Stable], se comunica al
 * compilador de Compose que el contenido no cambiará de forma observable
 * entre recomposiciones para la misma instancia, habilitando los skips.
 *
 * ## Serialización para SavedStateHandle
 * La anotación [Serializable] permite persistir el historial como JSON en
 * `SavedStateHandle` (vía `putSerializable`/`getSerializable` de
 * SavedStateHandleExtensions), sobreviviendo rotaciones de pantalla y
 * muerte de proceso.
 */
@Stable
@Serializable
class StableHistoryList(
    private val entries: List<HistoryEntry>,
) {
    operator fun get(index: Int): HistoryEntry = entries[index]

    val size: Int get() = entries.size

    fun toList(): List<HistoryEntry> = entries

    fun getMoves(): List<Move> = entries.indices.map { entries[it].move }

    fun getMove(index: Int): Move = entries[index].move

    fun getGameState(index: Int): GameState = entries[index].gameState

    fun iterator(): Iterator<HistoryEntry> = entries.iterator()
}
