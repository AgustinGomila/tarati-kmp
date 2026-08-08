package com.agustin.tarati.core.domain.analysis

/**
 * Caché del análisis post-partida por `gameId`. Evita recomputar el gráfico al
 * reabrir una partida.
 *
 * Implementaciones:
 * - `RoomAnalysisCacheRepository` (Android/Desktop/iOS): persiste en SQLite, sobrevive
 *   reinicios; invalida por [AnalysisConfig.ANALYSIS_VERSION].
 * - [InMemoryAnalysisCacheRepository] (Web y fallback): sólo durante la sesión.
 */
interface AnalysisCacheRepository {
    /** Análisis cacheado de [gameId] de la versión vigente, o `null` si no hay. */
    suspend fun get(gameId: String): GameAnalysis?

    /** Guarda (o reemplaza) el análisis de [gameId]. */
    suspend fun put(gameId: String, analysis: GameAnalysis)
}

/** Caché en memoria (vida del proceso). Usada en Web y como fallback. */
class InMemoryAnalysisCacheRepository : AnalysisCacheRepository {
    private val map = mutableMapOf<String, GameAnalysis>()
    override suspend fun get(gameId: String): GameAnalysis? = map[gameId]
    override suspend fun put(gameId: String, analysis: GameAnalysis) {
        map[gameId] = analysis
    }
}
