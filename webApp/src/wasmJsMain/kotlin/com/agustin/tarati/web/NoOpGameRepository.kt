package com.agustin.tarati.web

import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.repositories.SavedGame
import com.agustin.tarati.core.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Implementación no-op de [GameRepository] para web. La biblioteca local no aplica en browser. */
class NoOpGameRepository : GameRepository {
    override val isPersistenceSupported: Boolean = false
    override val savedGames: Flow<List<SavedGame>> = flowOf(emptyList())
    override fun searchGames(query: String): Flow<List<SavedGame>> = flowOf(emptyList())
    override suspend fun saveGame(dto: MatchDto): Unit = Unit
    override suspend fun loadGame(gameId: String): MatchDto? = null
    override suspend fun deleteGame(gameId: String): Unit = Unit
}
