package com.agustin.tarati.core.domain.game6.play

import androidx.compose.runtime.Immutable
import com.agustin.tarati.core.domain.game.board.Vertex
import kotlinx.serialization.Serializable

/**
 * Un movimiento del juego multijugador: mover la pieza de [from] a un punto de parada adyacente
 * [to]. Namespaced (independiente del `Move` de Tarati) para aislar el motor nuevo.
 */
@Serializable
@Immutable
data class MpMove(
    val from: Vertex,
    val to: Vertex,
) {
    val name: String get() = "${from.name}-${to.name}"
}
