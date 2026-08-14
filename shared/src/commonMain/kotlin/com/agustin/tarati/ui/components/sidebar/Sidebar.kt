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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.repository.GameRepository
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
import com.agustin.tarati.shared.generated.resources.edit
import com.agustin.tarati.shared.generated.resources.new_game
import com.agustin.tarati.shared.generated.resources.player_ai
import com.agustin.tarati.shared.generated.resources.player_human
import com.agustin.tarati.shared.generated.resources.rotate_board
import com.agustin.tarati.shared.generated.resources.settings
import com.agustin.tarati.shared.generated.resources.tarati
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.game.draw.board.drawIndicatorPiece
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.PI

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
    gameRepository: GameRepository = koinInject(),
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

    // Navegación por historial deshabilitada mientras corre una online (en curso) o se especta;
    // habilitada offline o cuando la online termina (para navegar su desarrollo).
    val navigationEnabled = (onlineGame == null || onlineGame.status != OnlineGameStatus.InProgress) &&
            spectatingState == null

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
        navigationEnabled = navigationEnabled,
    )

    Sidebar(
        modifier = Modifier.systemBarsPadding(),
        sidebarState = sidebarGameState,
        uiState = sidebarUIState,
        events = sidebarEvents,
        onUIStateChange = { sidebarUIState = it },
        onlineGame = onlineGame,
        spectatingState = spectatingState,
        localUserName = localUserName,
        // La biblioteca local (guardar / partidas guardadas) no existe en web:
        // ocultamos esos controles cuando el repositorio no persiste localmente.
        canPersistGames = gameRepository.isPersistenceSupported,
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
    /** Si es `false` (web), se ocultan los controles de persistencia local (guardar / partidas guardadas). */
    canPersistGames: Boolean = true,
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
                canPersistGames = canPersistGames,
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
