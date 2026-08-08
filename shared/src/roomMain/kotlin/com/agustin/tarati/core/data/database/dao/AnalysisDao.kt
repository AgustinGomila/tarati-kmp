package com.agustin.tarati.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agustin.tarati.core.data.database.entities.GameAnalysisEntity

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM game_analysis WHERE gameId = :gameId")
    suspend fun getById(gameId: String): GameAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GameAnalysisEntity)

    @Query("DELETE FROM game_analysis WHERE gameId = :gameId")
    suspend fun deleteById(gameId: String)
}
