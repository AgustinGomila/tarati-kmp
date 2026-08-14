package com.agustin.tarati.core.domain.repository

import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.repositories.SavedGame
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    /**
     * Indica si esta implementación persiste partidas localmente. `false` en
     * plataformas sin biblioteca local (web / browser, `NoOpGameRepository`),
     * donde guardar y listar partidas son no-op. La UI usa este flag para no
     * ofrecer los controles de persistencia local (guardar / partidas guardadas).
     */
    val isPersistenceSupported: Boolean get() = true

    val savedGames: Flow<List<SavedGame>>

    /**
     * Flow reactivo que emite la lista de partidas cuyas columnas de
     * búsqueda contienen [query] (sin distinción de mayúsculas/minúsculas,
     * ya que SQLite LIKE es case-insensitive para ASCII por defecto).
     *
     * Llamar con [query] vacío no tiene un comportamiento definido en esta
     * interfaz; los callers deben redirigir a [savedGames] en ese caso
     * (lo hace [GamesLibraryViewModel]).
     */
    fun searchGames(query: String): Flow<List<SavedGame>>

    suspend fun saveGame(dto: MatchDto)

    suspend fun loadGame(gameId: String): MatchDto?

    suspend fun deleteGame(gameId: String)
}