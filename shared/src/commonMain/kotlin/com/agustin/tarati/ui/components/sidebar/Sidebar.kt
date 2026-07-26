@file:OptIn(ExperimentalMaterial3Api::class)

package com.agustin.tarati.ui.components.sidebar


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.services.displayNameRes
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.colorNameRes
import com.agustin.tarati.core.domain.game.play.GameEndReason.DRAW_AGREEMENT
import com.agustin.tarati.core.domain.game.play.GameEndReason.FIFTY_MOVES
import com.agustin.tarati.core.domain.game.play.GameEndReason.MIT
import com.agustin.tarati.core.domain.game.play.GameEndReason.RESIGNATION
import com.agustin.tarati.core.domain.game.play.GameEndReason.STALEMIT
import com.agustin.tarati.core.domain.game.play.GameEndReason.TIMEOUT
import com.agustin.tarati.core.domain.game.play.GameEndReason.TRIPLE
import com.agustin.tarati.core.domain.game.play.GameEndReason.UNDETERMINED
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.utils.FeatureFlags
import com.agustin.tarati.features.game.GameEvents
import com.agustin.tarati.features.game.IGameModel
import com.agustin.tarati.features.game6.GameMode
import com.agustin.tarati.features.game6.GameModeSwitch
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.network.protocol.PlayerInfo
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.about
import com.agustin.tarati.shared.generated.resources.about_tarati
import com.agustin.tarati.shared.generated.resources.achievements
import com.agustin.tarati.shared.generated.resources.copied
import com.agustin.tarati.shared.generated.resources.copy_move_history
import com.agustin.tarati.shared.generated.resources.edit
import com.agustin.tarati.shared.generated.resources.jump_to_current_position
import com.agustin.tarati.shared.generated.resources.move_controls
import com.agustin.tarati.shared.generated.resources.new_game
import com.agustin.tarati.shared.generated.resources.online_lobby
import com.agustin.tarati.shared.generated.resources.player_ai
import com.agustin.tarati.shared.generated.resources.player_human
import com.agustin.tarati.shared.generated.resources.player_wins
import com.agustin.tarati.shared.generated.resources.redo
import com.agustin.tarati.shared.generated.resources.rotate_board
import com.agustin.tarati.shared.generated.resources.save_game
import com.agustin.tarati.shared.generated.resources.saved_games
import com.agustin.tarati.shared.generated.resources.settings
import com.agustin.tarati.shared.generated.resources.status_draw_agreement
import com.agustin.tarati.shared.generated.resources.status_draw_fifty
import com.agustin.tarati.shared.generated.resources.status_turn
import com.agustin.tarati.shared.generated.resources.status_undetermined
import com.agustin.tarati.shared.generated.resources.status_wins_mit
import com.agustin.tarati.shared.generated.resources.status_wins_resignation
import com.agustin.tarati.shared.generated.resources.status_wins_stalemit
import com.agustin.tarati.shared.generated.resources.status_wins_timeout
import com.agustin.tarati.shared.generated.resources.status_wins_triple
import com.agustin.tarati.shared.generated.resources.tarati
import com.agustin.tarati.shared.generated.resources.undo
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.game.draw.board.drawIndicatorPiece
import com.agustin.tarati.ui.components.movelist.MoveHistoryList
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.time.Duration.Companion.milliseconds

/**
 * Internal UI state for the sidebar — tracks which difficulty dropdowns are open.
 */
@Immutable
data class SidebarUIState(
    val isDifficultyExpandedWhite: Boolean = false,
    val isDifficultyExpandedBlack: Boolean = false,
)

@Composable
fun SidebarContent(
    gameManagerState: GameManagerState,
    playerSide: CobColor,
    evalConfigWhite: EvaluationConfig,
    evalConfigBlack: EvaluationConfig,
    aiEnabled: Boolean,
    boardOrientation: BoardOrientation,
    events: GameEvents,
    onNavigateToSettings: () -> Unit,
    onUndo: () -> Unit,
    viewModel: IGameModel,
    aiEngine: IAIEngine,
    /** Partida online activa, o null en modo local. */
    onlineGame: OnlineGame? = null,
    /** Estado de espectador activo. Mutuamente excluyente con [onlineGame]. */
    spectatingState: SpectatingState? = null,
    onOnlineLobby: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
) {
    var sidebarUIState by remember { mutableStateOf(SidebarUIState()) }

    // Collect per-band flags directly from the ViewModel so they react
    // immediately when the user toggles Human/AI mid-game.
    val whiteIsAI by viewModel.whiteIsAI.collectAsState()
    val blackIsAI by viewModel.blackIsAI.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val localUserName by viewModel.userName.collectAsState()

    val sidebarEvents = createSidebarEvents(
        gameModel = viewModel,
        gameEvents = events,
        gameManagerState = gameManagerState,
        onNavigateToSettings = onNavigateToSettings,
        onUndo = onUndo,
        onOnlineLobby = onOnlineLobby,
        onNavigateToAchievements = onNavigateToAchievements,
    )

    val sidebarGameState = SidebarGameState(
        gameManagerState = gameManagerState,
        playerSide = playerSide,
        difficultyWhite = evalConfigWhite.difficulty,
        difficultyBlack = evalConfigBlack.difficulty,
        isAIEnabled = aiEnabled,
        boardOrientation = boardOrientation,
        whiteIsAI = whiteIsAI,
        blackIsAI = blackIsAI,
        isEditing = isEditing,
        positionHistory = aiEngine.positionHistory,
    )

    Sidebar(
        modifier = Modifier.systemBarsPadding(),
        sidebarState = sidebarGameState,
        uiState = sidebarUIState,
        events = sidebarEvents,
        onUIStateChange = { sidebarUIState = it },
        onlineGame = onlineGame,
        spectatingState = spectatingState,
        localUserName = localUserName
    )
}

fun createSidebarEvents(
    gameModel: IGameModel,
    gameEvents: GameEvents,
    gameManagerState: GameManagerState,
    onNavigateToSettings: () -> Unit,
    onUndo: () -> Unit,
    onOnlineLobby: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
): SidebarEvents {
    return object : SidebarEvents {
        override fun onMoveToCurrent() = gameModel.moveToCurrentState()

        override fun onMoveToIndex(moveIndex: Int) = gameModel.moveToIndex(moveIndex)

        override fun onUndo() = onUndo()

        override fun onRedo() {
            gameEvents.putAIHistoryState(gameManagerState.gameState) {
                gameModel.redoMove()
            }
        }

        override fun onDifficultyChangeWhite(difficulty: Difficulty) =
            gameModel.updateDifficulty(WHITE, difficulty)

        override fun onDifficultyChangeBlack(difficulty: Difficulty) =
            gameModel.updateDifficulty(BLACK, difficulty)

        /**
         * Toggles the Human/AI assignment for [color] mid-game without
         * restarting the board. Marks the game so achievements are disabled.
         *
         * If the newly-assigned AI band has the current turn, the game is
         * paused so the TurnIndicator enters NEUTRAL (clickable) state.
         * The user must tap it to trigger the AI move — this prevents an
         * accidental band switch from immediately firing a move.
         *
         * If a Human band just took over the current turn, resume normally.
         */
        override fun onSetPlayerIsAI(color: CobColor, isAI: Boolean) {
            gameModel.updatePlayerType(color, isAI)
            gameEvents.onPlayerTypeChanged()

            val currentTurn = gameManagerState.gameState.currentTurn
            val newWhiteIsAI = if (color == WHITE) isAI else gameModel.whiteIsAI.value
            val newBlackIsAI = if (color == BLACK) isAI else gameModel.blackIsAI.value
            val isAITurn = (currentTurn == WHITE && newWhiteIsAI) ||
                    (currentTurn == BLACK && newBlackIsAI)

            if (isAITurn) {
                // Pause so the indicator shows NEUTRAL — user taps to trigger AI.
                gameEvents.stopGame()
            } else {
                // Human just took over this turn, ensure game is running.
                gameEvents.resumeGame()
            }
        }

        override fun onSettings() = onNavigateToSettings()
        override fun onNewGame(color: CobColor) = gameEvents.showNewGameDialog(color)
        override fun onEditBoard() = gameModel.toggleEditing()
        override fun onRotateBoard() = gameModel.rotateBoardManually()
        override fun onGamesLibrary() = gameEvents.showGamesLibrary()
        override fun onOnlineLobby() = onOnlineLobby()
        override fun onSaveGame() = gameEvents.saveGame()
        override fun onAboutClick() = gameEvents.showAboutDialog()
        override fun onCopyMoveHistory(moves: List<Move>) = gameEvents.copyMovesToClipboard(moves)
        override fun onShowAchievements() = gameEvents.showAchievementsUI(onNavigateToAchievements)
    }
}

/**
 * Panel lateral de navegación del juego.
 *
 * ## Visibilidad de controles en landscape
 * Todos los controles — incluyendo [PlayerConfigSection] y [AboutFooter] — son
 * siempre visibles independientemente de la orientación. En landscape la altura
 * disponible es menor, pero el `verticalScroll` permite acceder a cualquier
 * sección desplazándose. Ocultar controles en landscape no es una opción: la
 * selección de tipo de jugador (Humano / IA) es indispensable en todo momento.
 *
 * La lista de historial de movimientos ([NavigableHistoryList]) sí omite el panel
 * desplegable de historial en landscape — solo muestra los botones Undo/Redo —
 * porque sería excesivamente alto para la orientación horizontal. El acceso al
 * historial completo está disponible a través del BottomGameBar.
 */
@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    sidebarState: SidebarGameState,
    uiState: SidebarUIState = SidebarUIState(),
    events: SidebarEvents,
    onUIStateChange: (SidebarUIState) -> Unit = {},
    onlineGame: OnlineGame? = null,
    spectatingState: SpectatingState? = null,
    localUserName: String? = null,
) {
    val windowInfo = LocalWindowInfo.current
    val isLandscape = windowInfo.containerSize.width > windowInfo.containerSize.height

    SidebarShell(
        modifier = modifier,
        header = {
            SidebarHeader(
                onSettings = events::onSettings,
                onShowAchievements = events::onShowAchievements,
            )
        },
        controls = {
            GameModeSwitch(current = GameMode.SINGLE)
            GameControlsSection(
                boardOrientation = sidebarState.boardOrientation,
                isEditing = sidebarState.isEditing,
                onNewGame = { events.onNewGame(sidebarState.playerSide) },
                onEditBoard = events::onEditBoard,
                onRotateBoard = events::onRotateBoard,
            )
        },
        playerConfig = {

            // PlayerConfigSection is always visible regardless of orientation.
            // Hiding it in landscape was a bug: player-type selection (Human / AI)
            // and difficulty must be accessible at all times.
            //
            // En modo preview con un dropdown expandido, el desbordamiento de las
            // opciones debe renderizarse encima de los siblings posteriores
            // (MoveHistorySection). Modifier.zIndex solo afecta el orden de dibujo
            // dentro del mismo padre — en producción el valor es 0f (sin cambio).
            when {
                onlineGame != null && onlineGame.status == OnlineGameStatus.InProgress -> {
                    // Partida online activa: banner de identidad fija (yo vs oponente).
                    OnlinePlayerBanner(
                        onlineGame = onlineGame,
                        localName = localUserName.orEmpty(),
                    )
                }

                spectatingState != null -> {
                    // Modo espectador: banner con ambos jugadores de la partida observada.
                    SpectatingPlayerBanner(
                        whitePlayer = spectatingState.whitePlayer,
                        blackPlayer = spectatingState.blackPlayer,
                    )
                }

                else -> {
                    // Modo local: selector de tipo y dificultad por banda.
                    val anyDropdownExpanded = uiState.isDifficultyExpandedWhite ||
                            uiState.isDifficultyExpandedBlack
                    Box(
                        modifier = if (LocalInspectionMode.current && anyDropdownExpanded)
                            Modifier.zIndex(1f) else Modifier,
                    ) {
                        PlayerConfigSection(
                            whiteIsAI = sidebarState.whiteIsAI,
                            blackIsAI = sidebarState.blackIsAI,
                            difficultyWhite = sidebarState.difficultyWhite,
                            difficultyBlack = sidebarState.difficultyBlack,
                            onDifficultyChangeWhite = events::onDifficultyChangeWhite,
                            onDifficultyChangeBlack = events::onDifficultyChangeBlack,
                            onSetPlayerIsAI = events::onSetPlayerIsAI,
                            isDifficultyExpandedWhite = uiState.isDifficultyExpandedWhite,
                            isDifficultyExpandedBlack = uiState.isDifficultyExpandedBlack,
                            onExpandWhite = { onUIStateChange(uiState.copy(isDifficultyExpandedWhite = it)) },
                            onExpandBlack = { onUIStateChange(uiState.copy(isDifficultyExpandedBlack = it)) },
                        )
                    }
                }
            }
        },
        moveHistory = {
            MoveHistorySection(
                modifier = Modifier.weight(1f),
                isLandscape = isLandscape,
                sidebarState = sidebarState,
                onMoveToIndex = events::onMoveToIndex,
                onUndo = events::onUndo,
                onRedo = events::onRedo,
                onMoveToCurrent = events::onMoveToCurrent,
                onCopyMoveHistory = events::onCopyMoveHistory,
                onGamesLibrary = events::onGamesLibrary,
                onOnlineLobby = events::onOnlineLobby,
                onSaveGame = events::onSaveGame,
            )
        },
        footer = {
            AboutFooter(onAboutClick = events::onAboutClick)
        },
    )
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun SidebarHeader(
    onSettings: () -> Unit,
    onShowAchievements: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = localizedString(Res.string.tarati),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TooltipIconButton(
                tooltip = localizedString(Res.string.achievements),
                onClick = onShowAchievements,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = TaratiIcons.EmojiEvents,
                    contentDescription = localizedString(Res.string.achievements),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TooltipIconButton(
                tooltip = localizedString(Res.string.settings),
                onClick = onSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = TaratiIcons.Settings,
                    contentDescription = localizedString(Res.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Game controls ─────────────────────────────────────────────────────────────

@Composable
private fun GameControlsSection(
    boardOrientation: BoardOrientation,
    isEditing: Boolean,
    onNewGame: () -> Unit,
    onEditBoard: () -> Unit,
    onRotateBoard: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // New Game button — fills available space
        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
        ) {
            LocalizedText(Res.string.new_game, style = MaterialTheme.typography.bodyMedium)
        }

        // Board editor — resaltado con primary cuando está activo
        val editBg = if (isEditing) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
        val editTint = if (isEditing) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
        TooltipIconButton(
            tooltip = stringResource(Res.string.edit),
            onClick = onEditBoard,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(editBg),
        ) {
            Icon(
                TaratiIcons.SquareFoot,
                stringResource(Res.string.edit),
                tint = editTint,
            )
        }

        // Rotate board — deshabilitado mientras el editor está activo
        RotateBoardButton(
            boardOrientation = boardOrientation,
            enabled = !isEditing,
            onClick = onRotateBoard,
        )
    }
}

@Composable
private fun RotateBoardButton(
    modifier: Modifier = Modifier,
    boardOrientation: BoardOrientation,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val deg = when (boardOrientation) {
        BoardOrientation.PORTRAIT_WHITE -> 0f
        BoardOrientation.LANDSCAPE_WHITE -> 90f
        BoardOrientation.PORTRAIT_BLACK -> 180f
        BoardOrientation.LANDSCAPE_BLACK -> 270f
    }
    val disabledAlpha = 0.38f
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        .let { if (enabled) it else it.copy(alpha = disabledAlpha) }
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
        .let { if (enabled) it else it.copy(alpha = disabledAlpha) }
    val rotateLabel = stringResource(Res.string.rotate_board)
    TooltipIconButton(
        tooltip = rotateLabel,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor),
    ) {
        // El botón solo dibuja una flecha en Canvas (sin Icon), por lo que la
        // descripción accesible se aporta vía semantics para lectores de pantalla.
        Canvas(
            Modifier
                .size(20.dp)
                .semantics { contentDescription = rotateLabel }
        ) { drawDirectionArrow(iconColor, deg) }
    }
}

private fun DrawScope.drawDirectionArrow(color: Color, rotationDeg: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.42f
    val rotRad = rotationDeg * PI / 180.0
    val points = listOf(270f, 30f, 150f).map { a ->
        val rad = (a * PI / 180.0).toFloat() + rotRad
        Offset((cx + r * kotlin.math.cos(rad)).toFloat(), (cy + r * kotlin.math.sin(rad)).toFloat())
    }
    drawPath(Path().apply {
        moveTo(points[0].x, points[0].y); lineTo(points[1].x, points[1].y)
        lineTo(points[2].x, points[2].y); close()
    }, color = color, style = Fill)
}

// ── Per-band player configuration ────────────────────────────────────────────

/**
 * Two-row configuration panel, one row per color band.
 *
 * Row layout:  [cob disc]  [Human ↔ AI toggle chip]  [difficulty dropdown — AI only]
 *
 * The toggle chip shows clearly whether the band is Human (Person icon + "Human" label)
 * or AI (SmartToy icon + "AI" label) and switches on tap. The difficulty dropdown
 * appears immediately to the right of the chip when the band is AI, and disappears
 * when it switches back to Human — no restart required.
 */
@Composable
private fun PlayerConfigSection(
    whiteIsAI: Boolean,
    blackIsAI: Boolean,
    difficultyWhite: Difficulty,
    difficultyBlack: Difficulty,
    onDifficultyChangeWhite: (Difficulty) -> Unit,
    onDifficultyChangeBlack: (Difficulty) -> Unit,
    onSetPlayerIsAI: (CobColor, Boolean) -> Unit,
    isDifficultyExpandedWhite: Boolean,
    isDifficultyExpandedBlack: Boolean,
    onExpandWhite: (Boolean) -> Unit,
    onExpandBlack: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayerBandRow(
            color = WHITE,
            isAI = whiteIsAI,
            difficulty = difficultyWhite,
            onDifficultyChange = onDifficultyChangeWhite,
            onToggle = { onSetPlayerIsAI(WHITE, !whiteIsAI) },
            isDifficultyExpanded = isDifficultyExpandedWhite,
            onExpandChange = onExpandWhite,
        )
        PlayerBandRow(
            color = BLACK,
            isAI = blackIsAI,
            difficulty = difficultyBlack,
            onDifficultyChange = onDifficultyChangeBlack,
            onToggle = { onSetPlayerIsAI(BLACK, !blackIsAI) },
            isDifficultyExpanded = isDifficultyExpandedBlack,
            onExpandChange = onExpandBlack,
        )
    }
}

/**
 * Single row for one color band.
 *
 * [BandIndicator] — small cob disc identifying the color.
 * [PlayerTypeChip] — pill-shaped chip that shows the current mode (Human / AI)
 *   with an icon; tapping it toggles the mode.
 * [CompactDifficultySelector] — dropdown shown only when [isAI] is true.
 */
@Composable
private fun PlayerBandRow(
    color: CobColor,
    isAI: Boolean,
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
    onToggle: () -> Unit,
    isDifficultyExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BandIndicator(color)
        PlayerTypeChip(isAI = isAI, onToggle = onToggle)

        if (isAI) {
            Box(modifier = Modifier.weight(1f)) {
                CompactDifficultySelector(
                    expanded = isDifficultyExpanded,
                    onExpandedChange = onExpandChange,
                    difficulty = difficulty,
                    onDifficultyChange = onDifficultyChange,
                )
            }
        }
    }
}

// ── Spectating player banner ──────────────────────────────────────────────────

/**
 * Banner de identidad para modo espectador. Muestra ambos jugadores remotos
 * con sus nombres y ratings, sin ningún control de configuración.
 */
@Composable
private fun SpectatingPlayerBanner(
    whitePlayer: PlayerInfo,
    blackPlayer: PlayerInfo,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpectatingPlayerRow(cobColor = WHITE, player = whitePlayer)
        SpectatingPlayerRow(cobColor = BLACK, player = blackPlayer)
    }
}

@Composable
private fun SpectatingPlayerRow(cobColor: CobColor, player: PlayerInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BandIndicator(cobColor)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = TaratiIcons.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column {
                Text(
                    text = player.username,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = player.rating.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Small filled cob disc — identifies the color band at a glance.
 */
@Composable
internal fun BandIndicator(color: CobColor) {
    val bc = getBoardColors()
    Canvas(Modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        val c = Offset(r, r)
        drawIndicatorPiece(position = c, radius = r, cobColor = color, colors = bc)
    }
}

/**
 * Pill-shaped chip that shows the current player-type assignment clearly:
 *
 * - Human mode: [Person icon]  "Human"  — neutral surface background
 * - AI mode:    [SmartToy icon] "AI"    — primary background
 *
 * Tapping the chip toggles between the two modes.
 */
@Composable
private fun PlayerTypeChip(isAI: Boolean, onToggle: () -> Unit) {
    val bgColor = if (isAI) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isAI) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (isAI) TaratiIcons.SmartToy else TaratiIcons.Person
    val label = if (isAI) localizedString(Res.string.player_ai)
    else localizedString(Res.string.player_human)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Compact borderless difficulty dropdown, sized to fill whatever space remains
 * in the row after the band indicator and player-type chip.
 */
@Composable
private fun CompactDifficultySelector(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    // En modo preview, ExposedDropdownMenu usa un Popup que no se renderiza.
    // Con LocalInspectionMode renderizamos las opciones inline para que los
    // previews de Play Store muestren el dropdown expandido correctamente.
    val inPreview = LocalInspectionMode.current

    if (inPreview && expanded) {
        DifficultyInlineExpanded(
            difficulty = difficulty,
            onDifficultyChange = onDifficultyChange,
            onExpandedChange = onExpandedChange,
        )
    } else {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
            DifficultyTextField(
                difficulty = difficulty,
                expanded = expanded,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded, { onExpandedChange(false) }) {
                DifficultyMenuItems(onDifficultyChange, onExpandedChange)
            }
        }
    }
}

/** Campo de texto del selector — compartido entre la versión normal y la inline. */
@Composable
private fun DifficultyTextField(
    difficulty: Difficulty,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = localizedString(difficulty.displayNameRes),
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

/** Ítems del menú — compartidos entre el popup normal y el modo inline de preview. */
@Composable
private fun DifficultyMenuItems(
    onDifficultyChange: (Difficulty) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    Difficulty.ALL.forEach { opt ->
        DropdownMenuItem(
            text = {
                Text(localizedString(opt.displayNameRes), color = MaterialTheme.colorScheme.onSurface)
            },
            onClick = { onDifficultyChange(opt); onExpandedChange(false) },
        )
    }
}

/**
 * Versión inline del selector de dificultad para previews de Compose.
 *
 * El campo de texto ocupa su altura normal en el layout. Las opciones se
 * renderizan en un [Box] cuya altura reportada al padre es **cero** (via
 * [Modifier.layout]), de modo que el contenido posterior no se desplaza.
 * Las opciones desbordan hacia abajo visualmente, replicando el comportamiento
 * de un [androidx.compose.ui.window.Popup] sin usar ventanas del sistema.
 *
 * Para que las opciones queden por encima de los siblings posteriores del
 * sidebar (p. ej. [MoveHistorySection]), el llamador debe asegurarse de que
 * la sección padre tenga un [Modifier.zIndex] mayor (ver [SidebarContent]).
 */
@Composable
private fun DifficultyInlineExpanded(
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DifficultyTextField(
            difficulty = difficulty,
            expanded = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Cero altura reportada al padre → no desplaza el contenido posterior.
        // Las opciones desbordan visualmente hacia abajo sobre los controles
        // de abajo, igual que lo haría un Popup real.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val p = measurable.measure(
                        constraints.copy(maxHeight = Constraints.Infinity)
                    )
                    layout(p.width, 0) { p.place(0, 0) }
                },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
            ) {
                Column {
                    DifficultyMenuItems(onDifficultyChange, onExpandedChange)
                }
            }
        }
    }
}

// ── Move history + compact status row ────────────────────────────────────────

/**
 * Sección de historial de movimientos y controles de navegación.
 */
@Composable
private fun MoveHistorySection(
    modifier: Modifier,
    isLandscape: Boolean,
    sidebarState: SidebarGameState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)? = null,
    onCopyMoveHistory: (moves: List<Move>) -> Unit,
    onGamesLibrary: () -> Unit,
    onOnlineLobby: () -> Unit,
    onSaveGame: () -> Unit,
) {
    var isCopying by remember { mutableStateOf(false) }
    val gameManagerState = sidebarState.gameManagerState
    val currentMoveIndex = gameManagerState.moveIndex
    val moves = gameManagerState.history.getMoves()

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val matchState = remember(gameManagerState.gameState, sidebarState.positionHistory) {
            gameManagerState.gameState.getMatchState(sidebarState.positionHistory)
        }
        GameStatusRow(matchState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedString(Res.string.move_controls).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            TooltipIconButton(
                tooltip = localizedString(Res.string.copy_move_history),
                onClick = {
                    isCopying = true; onCopyMoveHistory(moves)
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000.milliseconds)
                        isCopying = false
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isCopying) TaratiIcons.Done else TaratiIcons.ContentCopy,
                    if (isCopying) localizedString(Res.string.copied)
                    else localizedString(Res.string.copy_move_history),
                    tint = if (isCopying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            TooltipIconButton(
                tooltip = localizedString(Res.string.save_game),
                onClick = onSaveGame,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    TaratiIcons.Save, localizedString(Res.string.save_game),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            TooltipIconButton(
                tooltip = localizedString(Res.string.saved_games),
                onClick = onGamesLibrary,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    TaratiIcons.MenuBook,
                    localizedString(Res.string.saved_games),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (FeatureFlags.ONLINE_ENABLED) {
                TooltipIconButton(
                    tooltip = localizedString(Res.string.online_lobby),
                    onClick = onOnlineLobby,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        TaratiIcons.Public,
                        localizedString(Res.string.online_lobby),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        NavigableHistoryList(
            modifier, isLandscape, currentMoveIndex,
            gameManagerState.history, onUndo, onRedo, onMoveToCurrent,
            onMoveToIndex = onMoveToIndex
        )
    }
}

// ── Compact game status chip ──────────────────────────────────────────────────

@Composable
private fun GameStatusRow(
    matchState: MatchState,
) {
    val winner = matchState.winner
    val result = matchState.gameEndReason
    val gameState = matchState.gameState
    val isOver = winner != null || result in listOf(FIFTY_MOVES, DRAW_AGREEMENT)
    val side = winner ?: gameState.currentTurn

    val bgColor = when (side) {
        WHITE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        BLACK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    }
    val fgColor = when (side) {
        WHITE -> MaterialTheme.colorScheme.onPrimaryContainer
        BLACK -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val text = when {
        result == FIFTY_MOVES -> localizedString(Res.string.status_draw_fifty)
        result == UNDETERMINED -> localizedString(Res.string.status_undetermined)
        result == DRAW_AGREEMENT -> localizedString(Res.string.status_draw_agreement)
        winner != null -> {
            val n = localizedString(winner.colorNameRes)
            when (result) {
                MIT -> localizedString(Res.string.status_wins_mit, n)
                STALEMIT -> localizedString(Res.string.status_wins_stalemit, n)
                TRIPLE -> localizedString(Res.string.status_wins_triple, n)
                TIMEOUT -> localizedString(Res.string.status_wins_timeout, n)
                RESIGNATION -> localizedString(Res.string.status_wins_resignation, n)
                else -> localizedString(Res.string.player_wins, n)
            }
        }

        else -> localizedString(
            Res.string.status_turn,
            localizedString(gameState.currentTurn.colorNameRes)
        )
    }

    val bc = getBoardColors()
    val dotColor = if (side == WHITE) bc.whiteCobColor else bc.blackCobColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(dotColor, size.minDimension / 2f)
        }
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isOver) FontWeight.SemiBold else FontWeight.Normal,
            color = fgColor
        )
    }
}

// ── Navigable history list ────────────────────────────────────────────────────

/**
 * Altura mínima de pantalla (en dp) necesaria para mostrar el panel de historial
 * de movimientos cuando el dispositivo está en landscape.
 *
 * Los teléfonos en landscape tienen ~360–411 dp de alto; las tablets en landscape
 * tienen 600 dp o más. El umbral de 500 dp separa ambos casos de forma robusta.
 */
private const val HISTORY_MIN_HEIGHT_DP = 500

/**
 * Controles de navegación por historial.
 *
 * El panel expandible de historial se muestra cuando hay suficiente espacio
 * vertical disponible, independientemente de la orientación:
 * - Portrait: siempre visible.
 * - Landscape en teléfono (~360–411 dp): solo Undo/Redo. El historial completo
 *   está disponible en el BottomGameBar.
 * - Landscape en tablet (≥ 500 dp): también visible, ya que hay espacio suficiente.
 */
@Composable
private fun NavigableHistoryList(
    modifier: Modifier, isLandscape: Boolean, currentMoveIndex: Int,
    history: StableHistoryList, onUndo: () -> Unit, onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)? = null,
) {
    val moves = history.getMoves()
    val density = LocalDensity.current
    val screenHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val showHistoryPanel = !isLandscape || screenHeightDp >= HISTORY_MIN_HEIGHT_DP.dp

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onUndo, enabled = currentMoveIndex >= 0,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
        ) {
            Icon(TaratiIcons.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp)); LocalizedText(Res.string.undo)
        }
        OutlinedButton(
            onRedo, enabled = currentMoveIndex < moves.size - 1,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
        ) {
            LocalizedText(Res.string.redo); Spacer(Modifier.width(4.dp))
            Icon(TaratiIcons.ArrowForward, null, Modifier.size(18.dp))
        }
    }

    if (showHistoryPanel) {
        Card(
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            MoveHistoryList(
                history = history,
                moveIndex = currentMoveIndex,
                onMoveClick = onMoveToIndex,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }

    if (currentMoveIndex != moves.size - 1)
        Button(onMoveToCurrent, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            LocalizedText(Res.string.jump_to_current_position)
        }
}

// ── Footer ────────────────────────────────────────────────────────────────────

@Composable
private fun AboutFooter(onAboutClick: () -> Unit) {
    TextButton(onAboutClick, Modifier.fillMaxWidth()) {
        Icon(
            TaratiIcons.Info, localizedString(Res.string.about),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(localizedString(Res.string.about_tarati), color = MaterialTheme.colorScheme.primary)
    }
}