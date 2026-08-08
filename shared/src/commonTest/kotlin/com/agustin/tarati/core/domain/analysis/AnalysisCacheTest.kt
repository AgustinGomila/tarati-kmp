package com.agustin.tarati.core.domain.analysis

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifica la serialización de [GameAnalysis] (base de la persistencia Room) y el
 * comportamiento de [InMemoryAnalysisCacheRepository].
 */
class AnalysisCacheTest {

    @Test
    fun `GameAnalysis ida y vuelta por JSON`() {
        val original = GameAnalysis(
            initial = PlyEval(0.5f, 0.0),
            perMove = listOf(PlyEval(0.7f, 120.0), PlyEval(0.3f, -200.0)),
        )
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<GameAnalysis>(json)
        assertEquals(original, restored)
        assertEquals(original.series, restored.series)
    }

    @Test
    fun `la cache en memoria guarda y devuelve por gameId`(): TestResult = runTest {
        val repo = InMemoryAnalysisCacheRepository()
        assertNull(repo.get("g1"))
        val analysis = GameAnalysis(initial = PlyEval(0.5f, 0.0), perMove = emptyList())
        repo.put("g1", analysis)
        assertEquals(analysis, repo.get("g1"))
        assertNull(repo.get("otro"))
    }
}
