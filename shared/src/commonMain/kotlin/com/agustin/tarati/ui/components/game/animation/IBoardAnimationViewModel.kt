package com.agustin.tarati.ui.components.game.animation

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Stable
interface IBoardAnimationViewModel {
    val soundService: ISoundService

    val animatedPieces: StateFlow<Map<Vertex, AnimatedCob>>
    val currentHighlights: StateFlow<List<HighlightAnimation>>
    val visualState: StateFlow<VisualGameState>

    /**
     * Emite [Unit] exactamente una vez por fin de partida, **después** de que la
     * animación del movimiento terminal ha concluido por completo.
     *
     * Los suscriptores deben usar este flow —no el cambio de
     * [core.domain.game.play.GameStatus]— para
     * disparar la secuencia de highlight de fin de partida y el diálogo de resultado,
     * garantizando así que ambos se muestran tras la animación visual y no de forma
     * prematura.
     *
     * Casos de uso:
     * - **Movimiento normal que termina la partida:** emitido desde el consumidor
     *   del `moveChannel` en [BoardAnimationViewModel] al finalizar
     *   [BoardAnimationViewModel.animateMoveSequence].
     * - **Fin de partida sin movimiento animado** (ej. tablas por 50 movimientos
     *   reclamadas por la IA): emitido inmediatamente vía [notifyGameOver], invocado
     *   desde [AnimationCoordinator] al recibir [AnimationEvent.NotifyGameOver].
     */
    /** Número de movimientos encolados o actualmente animándose en [moveChannel]. */
    val pendingMoveCount: StateFlow<Int>

    val gameOverReady: SharedFlow<Unit>

    fun animateGameOver(matchState: MatchState)

    fun animateParallel(
        highlights: List<HighlightAnimation>,
        source: String = "unknown",
    )

    fun animateSerie(
        sequences: List<List<HighlightAnimation>>,
        source: String = "unknown",
        mode: SequenceLoadMode = SequenceLoadMode.CANCEL_CURRENT,
    )

    fun clearQueue()

    fun forceSync()

    fun loadTutorialStep(
        sequences: List<List<HighlightAnimation>>,
        source: String = "unknown",
    )

    /**
     * Señaliza [gameOverReady] de forma inmediata, para fins de partida que no
     * provienen de un movimiento animado (p. ej. [AnimationEvent.NotifyGameOver]).
     */
    fun notifyGameOver()

    fun processMove(
        move: Move,
        oldGameState: GameState,
        newGameState: GameState,
        isGameOver: Boolean = false,
    )

    /**
     * Registra que el próximo movimiento [move] debe animarse **desde** el offset
     * absoluto [startOffset] (px del contenedor) en vez de desde el vértice de origen.
     * Lo usa arrastrar-y-soltar: la pieza vuela desde el punto donde se soltó hasta el
     * destino. Se consume una única vez al animar ese movimiento; default no-op.
     */
    fun setNextMoveStartOffset(move: Move, startOffset: Offset) {}

    fun reset()

    fun stopHighlights()

    fun syncState(gameState: GameState)

    val boardSize: StateFlow<Size>

    fun updateBoardOrientation(orientation: BoardOrientation)

    fun updateBoardSize(size: Size)

    fun updateAnimateEffects(animate: Boolean)

    fun updateConversionAnimationStyle(style: ConversionAnimationStyle)
}