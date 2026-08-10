package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.board.BoardGraph
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Niveles de fuerza del bot multijugador, mapeados a la profundidad de búsqueda max^n ([MpMaxN]).
 * Análogo al `Difficulty` del juego 1 (Easy→Champion). [EASY] es la evaluación a 1 ply (greedy puro);
 * los tiers superiores buscan más plies.
 *
 * `@Serializable`: viaja en el job del engine worker (Web) para el offload de [MpBotRunner].
 *
 * @property depth plies (jugadas de asiento) que mira la búsqueda.
 */
@Serializable
enum class MpBotLevel(val depth: Int) {
    EASY(1),
    MEDIUM(2),
    HARD(3),
    CHAMPION(4);

    companion object {
        val DEFAULT: MpBotLevel = MEDIUM
    }
}

/**
 * Punto de entrada único de la IA multijugador: despacha al motor según el [MpBotLevel]. [EASY]
 * delega en [MpGreedyBot] (1 ply, mismo comportamiento y desempate que el bot original = piso del
 * ladder); el resto usa la búsqueda [MpMaxN]. Compartido por el cliente local ([MpLocalGameViewModel])
 * y el servidor (`MpGameSessionManager`).
 */
object MpBot {

    /**
     * Elige el movimiento del jugador en turno con la fuerza [level], o `null` si no hay jugadas
     * legales. Ante empate, desempata al azar con [random].
     */
    fun chooseMove(
        state: MpGameState,
        level: MpBotLevel = MpBotLevel.DEFAULT,
        board: BoardGraph = Board25,
        random: Random = Random.Default,
    ): MpMove? = when (level) {
        MpBotLevel.EASY -> MpGreedyBot.chooseMove(state, board, random)
        else -> MpMaxN.chooseMove(state, level, board, random)
    }
}
