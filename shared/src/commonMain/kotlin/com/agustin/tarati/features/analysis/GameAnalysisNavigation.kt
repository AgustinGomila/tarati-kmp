package com.agustin.tarati.features.analysis

import com.agustin.tarati.core.data.database.dto.MatchDto

/**
 * Abre el detalle/análisis completo (pantalla de detalles con gráfico por-ply y clasificación
 * de jugadas) de la partida en curso, eligiendo la fuente según el modo:
 *
 * - **Online de un usuario registrado** ([onlineGameId] no-null): intenta el detalle **persistente
 *   remoto** — la partida guardada en la DB del servidor —, cargándolo con [loadOnlineMatch] y
 *   navegando por su `gameId` real.
 * - **Offline** (vs IA, IA-vs-IA, humano-humano) o si el remoto aún no está disponible: arma un
 *   [MatchDto] **temporal** con [buildOfflineMatch] (sin persistencia) y navega por un id derivado.
 *   Funciona en toda plataforma —incluida web, donde no hay biblioteca local— y para invitados.
 *
 * El id temporal se deriva del hash del [MatchDto.game] cuando el DTO no tiene [MatchDto.id],
 * misma convención que usa [GameAnalysisSection] para cachear el análisis en memoria.
 */
suspend fun openGameAnalysis(
    onlineGameId: String?,
    buildOfflineMatch: () -> MatchDto,
    loadOnlineMatch: suspend (String) -> MatchDto?,
    updateCurrentMatch: (MatchDto) -> Unit,
    navigateToDetails: (gameId: String) -> Unit,
) {
    if (onlineGameId != null) {
        val remote = loadOnlineMatch(onlineGameId)
        if (remote != null) {
            updateCurrentMatch(remote)
            navigateToDetails(onlineGameId)
            return
        }
    }
    val local = buildOfflineMatch()
    updateCurrentMatch(local)
    navigateToDetails(local.id ?: local.game.hashCode().toString())
}
