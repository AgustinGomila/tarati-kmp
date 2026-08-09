package com.agustin.tarati.ui.screens.main

import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.database.dto.PGNHeader
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.clipboard.IClipboardService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class GameClipboardHelperTest {
    @Test
    fun `should copy board position`(): TestResult =
        runTest {
            val mockService = mockk<IClipboardService>()
            val helper = GameClipboardHelper(mockService)

            // Simulamos que el copyText funciona correctamente
            coEvery { mockService.copyText(any(), any()) } returns true

            // Llamamos al método que queremos probar
            helper.copyBoardPosition(initialGameState().toPositionNotation())

            // Verificamos que se llamó a copyText con los parámetros correctos
            coVerify {
                mockService.copyText(
                    label = "tarati-pos",
                    text = initialGameState().toPositionNotation(),
                )
            }
        }

    @Test
    fun `copyMatch usa el header del matchDto y no lo re-deriva del estado en vivo`(): TestResult =
        runTest {
            val mockService = mockk<IClipboardService>()
            val helper = GameClipboardHelper(mockService)

            val captured = slot<String>()
            coEvery { mockService.copyText(any(), capture(captured)) } returns true

            // Partida IA-vs-IA: el header trae los labels reales por-banda y el resultado.
            val match = MatchDto(
                header = PGNHeader(
                    white = "AI (Medium)",
                    black = "AI (Medium)",
                    result = "1-0",
                    termination = "Normal",
                ),
                game = GameDto(boardPosition = "A1w/B1W b"),
            )

            helper.copyMatch(match)

            val pgn = captured.captured
            assertTrue("[White \"AI (Medium)\"]" in pgn, "usa el label de Blancas del matchDto")
            assertTrue("[Black \"AI (Medium)\"]" in pgn, "usa el label de Negras del matchDto")
            assertTrue("[Termination \"Normal\"]" in pgn)
            assertTrue(pgn.trimEnd().endsWith("A1w/B1W b"), "adjunta la posición final del matchDto")
            coVerify { mockService.copyText(label = "tarati-pgn", text = any()) }
        }
}
