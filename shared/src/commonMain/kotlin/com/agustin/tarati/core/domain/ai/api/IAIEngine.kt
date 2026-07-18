package com.agustin.tarati.core.domain.ai.api


import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState

/**
 * Context passed to [IAIEngine.shouldAcceptDraw] so the engine has all the
 * information it needs to make a nuanced draw decision without coupling the
 * interface to server-specific types.
 *
 * @param gameState    Current board position.
 * @param engineColor  The color the engine is playing.
 * @param botRating    ELO of the bot in the current time control.
 * @param opponentRating ELO of the human opponent in the current time control.
 */
data class DrawContext(
    val gameState: GameState,
    val engineColor: CobColor,
    val botRating: Int,
    val opponentRating: Int,
)

interface IAIEngine {
    val name: String

    /**
     * Historial de posiciones (hash → nº de ocurrencias) para la detección de
     * triple repetición. Solo lectura desde afuera: se muta exclusivamente vía
     * [putState] / [removeState] / [clearHistory].
     */
    val positionHistory: Map<String, Int>

    suspend fun getNextMove(gameState: GameState): MoveEval

    /**
     * Decides whether the engine should accept a draw offer.
     *
     * The default is `true` (always accept) — safe for any implementation
     * that doesn't override this, and correct for the weakest bots.
     *
     * @see DrawContext for the full set of available inputs.
     */
    fun shouldAcceptDraw(context: DrawContext): Boolean = true

    fun clearHistory()

    fun putState(
        gameState: GameState,
        moveBy: CobColor,
    ): CobColor?

    fun removeState(gameState: GameState)

    fun setConfig(config: EvaluationConfig)

    fun getDiagnostics(): AIDiagnostics?
}