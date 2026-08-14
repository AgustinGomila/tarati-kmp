package com.agustin.tarati.services.ai

import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.evaluator.MoveEval
import com.agustin.tarati.core.domain.ai.runner.AiMoveRunner
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.B1
import com.agustin.tarati.core.domain.game.board.GameBoard.C1
import com.agustin.tarati.core.domain.game.board.GameBoard.C8
import com.agustin.tarati.core.domain.game.board.GameBoard.D4
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica el fix de la carrera "jugada de IA de la partida anterior aplicada al tablero nuevo":
 * [AIViewModel.cancelThinking] cancela el cómputo en vuelo y la guarda de generación evita que el
 * `finally` de un job cancelado (que termina tarde) pise el estado de un cómputo posterior.
 *
 * El cómputo de la IA corre en `viewModelScope`, que sobrevive al reset del tablero: sin cancelarlo,
 * su resultado —calculado para la posición anterior— volvía y se aplicaba sobre la partida nueva.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AIViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var engine: IAIEngine
    private lateinit var runner: GatedRunner
    private lateinit var viewModel: AIViewModel

    private val state = GameState(cobs = mapOf(C1 to Cob(WHITE)), currentTurn = WHITE)

    /**
     * Runner de prueba: [bestMove] queda suspendido en [gate] hasta que el test la completa, así se
     * controla el momento exacto en que "termina de pensar". Con [nonCancellable] el `await` corre
     * en [NonCancellable], modelando un cómputo CPU-bound que no observa la cancelación hasta el
     * final (el modo de fallo real cuando el motor corre en el hilo principal saturado en Web).
     */
    private class GatedRunner : AiMoveRunner {
        var gate = CompletableDeferred<Unit>()
        var move: Move? = Move(C1 to B1)
        var nonCancellable = false

        override suspend fun bestMove(
            gameState: GameState,
            difficulty: Difficulty,
            positionHistory: Map<String, Int>,
        ): MoveEval {
            val g = gate
            val m = move
            if (nonCancellable) withContext(NonCancellable) { g.await() } else g.await()
            return MoveEval(score = 0.0, move = m)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engine = mockk(relaxed = true)
        every { engine.positionHistory } returns emptyMap()
        runner = GatedRunner()
        viewModel = AIViewModel(aiEngine = engine, moveRunner = runner)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits the computed move on normal completion`(): TestResult = runTest(testDispatcher) {
        val emissions = mutableListOf<Move>()
        val collect = launch { viewModel.pendingAIMove.collect { emissions += it } }
        advanceUntilIdle()

        viewModel.requestAIMove(state, Difficulty.DEFAULT)
        advanceUntilIdle()
        assertTrue(viewModel.isAIThinking.value, "debe estar pensando mientras el cómputo corre")

        runner.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(runner.move), emissions)
        assertFalse(viewModel.isAIThinking.value)
        collect.cancel()
    }

    @Test
    fun `cancelThinking stops the in-flight computation from emitting`(): TestResult = runTest(testDispatcher) {
        val emissions = mutableListOf<Move>()
        val collect = launch { viewModel.pendingAIMove.collect { emissions += it } }
        advanceUntilIdle()

        viewModel.requestAIMove(state, Difficulty.DEFAULT)
        advanceUntilIdle()
        assertTrue(viewModel.isAIThinking.value)

        // Se inicia una partida nueva → se cancela el cómputo en vuelo.
        viewModel.cancelThinking()
        advanceUntilIdle()
        assertFalse(viewModel.isAIThinking.value)

        // El cómputo viejo "termina" tarde: no debe emitir su jugada (stale) al tablero nuevo.
        runner.gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(emissions.isEmpty(), "una jugada cancelada no debe emitirse")
        assertFalse(viewModel.isAIThinking.value)
        collect.cancel()
    }

    @Test
    fun `late cancelled job does not clear thinking of a newer computation`(): TestResult = runTest(testDispatcher) {
        val emissions = mutableListOf<Move>()
        val collect = launch { viewModel.pendingAIMove.collect { emissions += it } }
        advanceUntilIdle()

        // Cómputo viejo, CPU-bound: no observa la cancelación hasta completar su compuerta.
        runner.nonCancellable = true
        val oldGate = runner.gate
        val oldMove = Move(D4 to C8)
        runner.move = oldMove
        viewModel.requestAIMove(state, Difficulty.DEFAULT)
        advanceUntilIdle()

        // Partida nueva: cancela el viejo (sigue atascado) y arranca un cómputo nuevo.
        viewModel.cancelThinking()
        val newGate = CompletableDeferred<Unit>()
        val newMove = Move(C1 to B1)
        runner.gate = newGate
        runner.move = newMove
        runner.nonCancellable = false
        viewModel.requestAIMove(state, Difficulty.DEFAULT)
        advanceUntilIdle()
        assertTrue(viewModel.isAIThinking.value, "el cómputo nuevo está pensando")

        // El job viejo (cancelado) termina tarde: su finally NO debe bajar la bandera del nuevo,
        // ni emitir su jugada stale.
        oldGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.isAIThinking.value, "el job cancelado no debe apagar el 'pensando' del nuevo")
        assertFalse(emissions.contains(oldMove), "el job cancelado no debe emitir su jugada")

        // El cómputo nuevo completa normalmente.
        newGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(newMove), emissions)
        assertFalse(viewModel.isAIThinking.value)
        collect.cancel()
    }

    @Test
    fun `stale move from previous game is illegal in the fresh initial position`() {
        // Predicado del que depende la red de seguridad del colector (GameScreenSideEffects):
        // la jugada del incidente (D4→C8, pieza NEGRA) no es legal en la posición inicial con
        // Blancas al turno, así que el guard `move !in gameState.allMovesForTurn()` la descarta.
        val fresh = initialGameState(WHITE)
        val staleMove = Move(D4 to C8)

        assertTrue(fresh.allMovesForTurn().isNotEmpty())
        assertFalse(staleMove in fresh.allMovesForTurn())
        // D4 sostiene una pieza negra en la posición inicial: nunca es una jugada de Blancas.
        assertEquals(BLACK, fresh.cobs[D4]?.color)
    }
}
