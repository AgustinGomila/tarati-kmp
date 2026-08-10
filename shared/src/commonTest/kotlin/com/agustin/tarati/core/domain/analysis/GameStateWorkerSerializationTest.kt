package com.agustin.tarati.core.domain.analysis

import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contrato de serialización del que depende el **engine worker** de Web (`web/worker/EngineWorkerProtocol`).
 *
 * El job `bestMove` cruza un [GameState] completo hacia el worker. `GameState.cobs` es un
 * `Map<Vertex, Cob>` con **clave estructurada** (Vertex es una `class`), que kotlinx **no** serializa a
 * JSON salvo con `allowStructuredMapKeys = true`. Sin ese flag el encode lanza y el worker de la IA cae al
 * fallback del hilo principal en cada jugada (regresión que traba la rotación del TurnIndicator en Champion).
 *
 * Estos tests fijan el contrato: con los mismos flags que `workerJson`, un [GameState] y un [Move]
 * round-trips. Corren en todos los targets (incl. wasmJs), replicando el entorno real del worker.
 */
class GameStateWorkerSerializationTest {

    // Misma configuración que `workerJson` en el engine worker.
    private val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    @Test
    fun gameState_with_structured_map_key_round_trips() {
        val original: GameState = initialGameState()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<GameState>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun move_round_trips() {
        val original = initialGameState().allMovesForTurn().first()
        val decoded = json.decodeFromString<Move>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun move_eval_move_field_round_trips() {
        // El reply `bestMove` del worker devuelve el Move dentro del payload.
        val move = initialGameState().allMovesForTurn().first()
        val decoded = json.decodeFromString<Move>(json.encodeToString(move))
        assertNotNull(MoveEval(score = 0.0, move = decoded).move)
    }
}
