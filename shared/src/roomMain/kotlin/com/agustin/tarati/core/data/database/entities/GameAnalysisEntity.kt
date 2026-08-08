package com.agustin.tarati.core.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Análisis post-partida cacheado, indexado por `gameId`.
 *
 * [payload] es el JSON de `GameAnalysis`; [version] es
 * `AnalysisConfig.ANALYSIS_VERSION` al momento de guardar (permite invalidar
 * cachés de una versión previa del motor).
 */
@Entity(tableName = "game_analysis")
data class GameAnalysisEntity(
    @PrimaryKey val gameId: String,
    val version: Int,
    val payload: String,
    val analyzedAt: Long,
)
