package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Calcula la jugada del bot multijugador **fuera del hilo de UI**. Punto de extensión por plataforma,
 * espejo de [com.agustin.tarati.core.domain.ai.runner.AiMoveRunner] del juego clásico:
 * - [DefaultMpBotRunner] (Android/Desktop/iOS y fallback): corre en [Dispatchers.Default] — hilos reales.
 * - En Web, un runner basado en **Web Worker** saca la búsqueda `max^n` ([MpMaxN]) del hilo principal
 *   del navegador (donde `Dispatchers.Default` es el mismo event-loop y bloquea la UI durante el turno
 *   del bot).
 *
 * El payload es autosuficiente y serializable ([MpGameState] + [MpBotLevel]); a diferencia del motor
 * clásico, la IA MP no necesita historial externo.
 */
interface MpBotRunner {
    suspend fun chooseMove(state: MpGameState, level: MpBotLevel): MpMove?
}

/**
 * Runner por defecto: ejecuta [MpBot.chooseMove] en [Dispatchers.Default]. El [random] desempata las
 * jugadas de igual score (inyectable para tests deterministas del VM; en producción, aleatorio).
 */
class DefaultMpBotRunner(
    private val random: Random = Random.Default,
) : MpBotRunner {
    override suspend fun chooseMove(state: MpGameState, level: MpBotLevel): MpMove? =
        withContext(Dispatchers.Default) { MpBot.chooseMove(state, level, random = random) }
}
