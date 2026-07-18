package com.agustin.tarati.core.domain.game.manager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.agustin.tarati.core.domain.game.manager.GameManagerState.Companion.createInitialUiState
import kotlinx.coroutines.flow.combine

/**
 * Extensiones Compose para GameManager.
 * Agrega funcionalidad específica de Compose (observación como [State]).
 */

/**
 * Estado combinado como [State] para Compose.
 *
 * Combina los cuatro StateFlows individuales en un único State<GameManagerState>
 * que se puede observar desde Composables.
 *
 * Usar esto en lugar de observar los StateFlows individuales cuando se necesita
 * el estado completo del GameManager.
 */
val GameManager.gameManagerState: State<GameManagerState>
    @Composable get() =
        combine(
            gameState,
            gameStatus,
            history,
            moveIndex,
        ) { gameState, status, history, index ->
            GameManagerState(
                gameState = gameState,
                gameStatus = status,
                history = history,
                moveIndex = index,
            )
        }.collectAsState(initial = createInitialUiState())