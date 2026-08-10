package com.agustin.tarati.web.worker

import com.agustin.tarati.core.domain.game6.ai.MpBotLevel
import com.agustin.tarati.core.domain.game6.ai.MpBotRunner
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import kotlinx.coroutines.CancellationException

/**
 * [MpBotRunner] que calcula la jugada del bot multijugador en el **engine worker** compartido
 * ([EngineWorkerClient]), sacando la búsqueda `max^n` del hilo principal del navegador → la UI queda
 * fluida durante el turno de los bots (el MP local satura el main con varios asientos IA).
 *
 * Envía un job [EngineJobKind.MP_BEST_MOVE] con el [MpGameState] + [MpBotLevel]. Si el worker no está
 * disponible o falla, cae de forma transparente a [fallback] (cómputo en el hilo principal).
 */
class WorkerMpBotRunner(
    private val fallback: MpBotRunner,
) : MpBotRunner {

    override suspend fun chooseMove(state: MpGameState, level: MpBotLevel): MpMove? {
        if (!EngineWorkerClient.available) {
            return fallback.chooseMove(state, level)
        }
        return try {
            val reply = EngineWorkerClient.submit(
                buildJob = { id ->
                    EngineJob(
                        id = id,
                        kind = EngineJobKind.MP_BEST_MOVE,
                        mpState = state,
                        mpLevel = level,
                    )
                },
                inactivityTimeoutMs = MP_MOVE_TIMEOUT_MS,
            )
            // mpMove nulo = "sin jugadas legales" (resultado válido), distinto de un fallo (kind=ERROR,
            // que completa con excepción y cae al fallback).
            reply.mpMove
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fallback.chooseMove(state, level)
        }
    }

    private companion object {
        /**
         * Ventana de inactividad. La búsqueda max^n está acotada por `NODE_BUDGET` → es rápida
         * (unos pocos segundos en el peor caso), así que 10 s dan margen de sobra y a la vez detectan
         * un worker muerto sin hacer esperar 30 s a la siguiente jugada.
         */
        const val MP_MOVE_TIMEOUT_MS = 10_000L
    }
}
