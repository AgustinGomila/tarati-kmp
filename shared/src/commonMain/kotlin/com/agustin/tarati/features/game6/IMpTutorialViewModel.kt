package com.agustin.tarati.features.game6

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialProgress
import com.agustin.tarati.core.domain.game6.tutorial.MpTutorialState
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel del tutorial multijugador (tablero `25`). Posee su propio estado del tablero (posición,
 * selección y cues) de modo que, cuando el tutorial está activo, `MpGameScreen` alimenta el
 * `Board25Pane` desde aquí en lugar de la partida local — que queda intacta detrás y reaparece al
 * cerrar el recorrido.
 */
interface IMpTutorialViewModel {
    /** Estado del recorrido (paso actual o cierre). */
    val tutorialState: StateFlow<MpTutorialState>

    /** Progreso 1-based (para la barra y el contador de la burbuja). */
    val progress: MpTutorialProgress

    /** Posición a dibujar en el tablero durante el paso actual (`null` fuera del recorrido). */
    val displayState: StateFlow<MpGameState?>

    /** Vértice resaltado (la pieza sugerida). */
    val selection: StateFlow<Vertex?>

    /** Destinos resaltados del paso actual. */
    val legalTargets: StateFlow<Set<Vertex>>

    /** Piezas convertidas por el último movimiento aceptado → su dueño anterior (para animar el flip). */
    val converted: StateFlow<Map<Vertex, PlayerColor>>

    /** Último movimiento aplicado en el paso actual (para animar el desplazamiento). */
    val lastMove: StateFlow<MpMove?>

    /** Carga los pasos y arranca el recorrido desde el primero. */
    fun start()

    /** Procesa un tap del usuario (solo relevante en pasos interactivos). */
    fun onVertexTap(vertex: Vertex)

    /** Avanza al siguiente paso (si es el último, completa el recorrido). */
    fun next()

    /** Retrocede un paso. */
    fun previous()

    /** Repite el paso actual (reinicia su posición y cues). */
    fun repeatCurrent()

    /**
     * Aplica el movimiento esperado del paso interactivo actual (usado al pulsar "Siguiente" sin
     * resolverlo). Devuelve `true` si aplicó el movimiento (el auto-avance mostrará el resultado y
     * pasará al siguiente paso); `false` si no había nada que aplicar (paso ya resuelto o no interactivo).
     */
    fun skipInteractive(): Boolean

    /** Cierra el recorrido sin marcarlo como completado. */
    fun close()

    /** Descarta el recorrido por completo. */
    fun reset()

    /** `true` si el recorrido llegó al final. */
    fun isCompleted(): Boolean
}
