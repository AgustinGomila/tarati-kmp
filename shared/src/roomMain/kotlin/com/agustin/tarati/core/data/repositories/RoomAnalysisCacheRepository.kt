package com.agustin.tarati.core.data.repositories

import com.agustin.tarati.core.data.database.dao.AnalysisDao
import com.agustin.tarati.core.data.database.entities.GameAnalysisEntity
import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisConfig
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Persistencia del análisis post-partida en Room (Android/Desktop/iOS). Serializa
 * [GameAnalysis] a JSON en la columna `payload` e invalida por
 * [AnalysisConfig.ANALYSIS_VERSION]: un análisis guardado con una versión previa
 * del motor se descarta (devuelve `null`), forzando el recómputo.
 */
class RoomAnalysisCacheRepository(
    private val dao: AnalysisDao,
) : AnalysisCacheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(gameId: String): GameAnalysis? {
        val entity = dao.getById(gameId) ?: return null
        if (entity.version != AnalysisConfig.ANALYSIS_VERSION) return null
        return runCatching { json.decodeFromString<GameAnalysis>(entity.payload) }.getOrNull()
    }

    override suspend fun put(gameId: String, analysis: GameAnalysis) {
        dao.upsert(
            GameAnalysisEntity(
                gameId = gameId,
                version = AnalysisConfig.ANALYSIS_VERSION,
                payload = json.encodeToString(analysis),
                analyzedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}
