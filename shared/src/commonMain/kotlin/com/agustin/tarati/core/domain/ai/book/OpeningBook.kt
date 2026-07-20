package com.agustin.tarati.core.domain.ai.book

import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.Move

/**
 * Opening book: para posiciones de apertura conocidas devuelve la jugada empíricamente más fuerte,
 * evitando que el motor busque desde cero. Los datos ([OPENING_BOOK_ENTRIES]) se minan del corpus de
 * partidas con `:tools:opening-miner` y se compilan a Kotlin (funciona en los 4 targets y en el
 * servidor headless, sin runtime de recursos).
 *
 * La clave es el **hash canónico bajo la simetría bilateral del tablero** (ver `BoardSymmetry`): las
 * aperturas espejo comparten entrada. El lookup canonicaliza la posición actual igual que el minero y,
 * si el representante canónico es el espejo, des-refleja la jugada del book a la orientación real.
 *
 * Gateado por [com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig.openingBookEnabled].
 */
object OpeningBook {

    /** Cantidad de posiciones en el book (para diagnóstico/tests). */
    val size: Int get() = OPENING_BOOK_ENTRIES.size

    /**
     * Jugada recomendada para [state], o `null` si la posición no está en el book (el motor busca) o
     * si la jugada del book no es legal en el estado actual (defensa ante un book desactualizado).
     */
    fun lookup(state: GameState): Move? {
        if (OPENING_BOOK_ENTRIES.isEmpty()) return null

        val hash = state.hashBoard()
        val mirroredHash = state.mirroredHashBoard()
        // Mismo criterio de representante que GameState.canonicalMove: el menor hash es el canónico.
        val useMirror = mirroredHash < hash
        val key = if (useMirror) mirroredHash else hash

        val bookMoveName = OPENING_BOOK_ENTRIES[key] ?: return null

        // El nombre del book está en el marco canónico. Se busca la jugada legal cuya representación
        // canónica coincide: si el representante es el espejo, se compara contra el nombre reflejado.
        return state.allMovesForTurn().firstOrNull { move ->
            val canonicalName = if (useMirror) move.mirrored().name else move.name
            canonicalName == bookMoveName
        }
    }
}
