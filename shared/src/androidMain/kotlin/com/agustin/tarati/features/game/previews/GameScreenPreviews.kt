package com.agustin.tarati.features.game.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.toBoardOrientation
import com.agustin.tarati.core.domain.game.manager.GameManagerState.Companion.createInitialUiState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.PieceCounts
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.utils.logging.LoggingFactory
import com.agustin.tarati.features.library.previews.exampleMoveHistory
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.a_board_game_by_george_spencer_brown
import com.agustin.tarati.shared.generated.resources.app_name
import com.agustin.tarati.ui.components.bottombar.BottomGameBar
import com.agustin.tarati.ui.components.editor.EditActionEvents
import com.agustin.tarati.ui.components.editor.EditActionState
import com.agustin.tarati.ui.components.editor.EditColorEvents
import com.agustin.tarati.ui.components.editor.EditColorState
import com.agustin.tarati.ui.components.editor.previews.EditControlsPreview
import com.agustin.tarati.ui.components.editor.previews.boardRenderEventsEmpty
import com.agustin.tarati.ui.components.editor.previews.tapEventsEmpty
import com.agustin.tarati.ui.components.game.CreateBoard
import com.agustin.tarati.ui.components.game.CreateBoardState
import com.agustin.tarati.ui.components.game.animation.VisualGameState
import com.agustin.tarati.ui.components.game.draw.board.createEmptyBoardRenderData
import com.agustin.tarati.ui.components.sidebar.Sidebar
import com.agustin.tarati.ui.components.sidebar.SidebarEvents
import com.agustin.tarati.ui.components.sidebar.SidebarGameState
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.turnIndicator.IndicatorEvents
import com.agustin.tarati.ui.components.turnIndicator.TurnIndicator
import com.agustin.tarati.ui.components.turnIndicator.TurnIndicatorState
import com.agustin.tarati.ui.theme.AuroraPalette
import com.agustin.tarati.ui.theme.BoardPalette
import com.agustin.tarati.ui.theme.ChristmasPalette
import com.agustin.tarati.ui.theme.ClassicPalette
import com.agustin.tarati.ui.theme.DarkPalette
import com.agustin.tarati.ui.theme.EmberPalette
import com.agustin.tarati.ui.theme.GildedPalette
import com.agustin.tarati.ui.theme.GrayscalePalette
import com.agustin.tarati.ui.theme.HalloweenPalette
import com.agustin.tarati.ui.theme.NaturePalette
import com.agustin.tarati.ui.theme.TaratiBackground
import com.agustin.tarati.ui.theme.TaratiTheme

/**
 * Composable raíz de todos los previews de GameScreen.
 *
 * ## Aislamiento de paleta entre previews
 * La paleta se pasa directamente a [TaratiTheme] mediante `config.palette`.
 * `TaratiTheme` solo lee [PaletteManager.currentPalette]
 * cuando `palette == null` (ruta de producción). Al pasarla explícitamente, este
 * composable no se suscribe al State global del PaletteManager, por lo que los
 * cambios de paleta de otros previews no lo invalidan. Esto elimina el bug donde
 * al hacer scroll en el panel de previews todos adoptaban la paleta del último
 * en renderizarse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreenPreviewContent(config: GameScreenPreviewConfig = GameScreenPreviewConfig()) {
    TaratiTheme(darkTheme = config.darkTheme, palette = config.palette) {
        val drawerState = rememberDrawerState(initialValue = config.drawerStateValue)

        val gameManagerState by remember {
            mutableStateOf(
                createInitialUiState().copy(
                    history = StableHistoryList(exampleMoveHistory),
                    moveIndex = 2,
                ),
            )
        }

        var gameState by remember {
            mutableStateOf(config.gameState ?: previewRandomMidGameState)
        }

        var playerSide by remember { mutableStateOf(config.playerSide) }
        var isEditing by remember { mutableStateOf(config.isEditing) }
        var isTutorial by remember { mutableStateOf(config.isTutorialActive) }
        var boardVisualState by remember { mutableStateOf(config.boardVisualState) }

        var sidebarUIState by remember { mutableStateOf(config.sidebarUIState) }

        val sidebarEvents =
            createPreviewSidebarEvents(
                currentIsEditing = isEditing,
                onGameStateUpdate = { gameState = it },
                onPlayerSideUpdate = { playerSide = it },
                onEditingUpdate = { isEditing = it },
            )

        val sidebarGameState =
            SidebarGameState(
                gameManagerState = gameManagerState,
                playerSide = playerSide,
                difficultyWhite = Difficulty.DEFAULT,
                difficultyBlack = Difficulty.DEFAULT,
                isAIEnabled = true,
                boardOrientation = toBoardOrientation(config.landScape, playerSide),
                whiteIsAI = false,
                blackIsAI = true,
            )

        val createBoardState =
            CreateBoardState(
                gameState = gameState,
                playerSide = playerSide,
                aiEnabled = true,
                whiteIsAI = false,
                blackIsAI = true,
                isEditing = isEditing,
                isTutorialActive = isTutorial,
                isAIThinking = false,
                boardOrientation = toBoardOrientation(config.landScape, playerSide),
                editBoardOrientation = toBoardOrientation(config.landScape, playerSide),
                boardVisualState = boardVisualState,
            )

        val boardRenderData = createEmptyBoardRenderData().copy(
            gameState = gameState,
            visualState = VisualGameState(
                cobs = gameState.cobs,
                currentTurn = gameState.currentTurn,
            ),
        )

        val indicatorState = TurnIndicatorState.HUMAN_TURN
        val indicatorEvents = createPreviewIndicatorEvents()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Sidebar(
                    modifier = Modifier.systemBarsPadding(),
                    sidebarState = sidebarGameState,
                    uiState = sidebarUIState,
                    events = sidebarEvents,
                    onUIStateChange = { newState -> sidebarUIState = newState },
                )
            },
        ) {
            TaratiBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TaratiTopBar(
                            drawerState = drawerState,
                            title = localizedString(Res.string.app_name),
                            isEditing = isEditing,
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            if (!config.landScape && !isEditing) {
                                LocalizedText(
                                    resource = Res.string.a_board_game_by_george_spencer_brown,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                            }

                            CreateBoard(
                                modifier = Modifier.weight(1f),
                                state = createBoardState,
                                boardVisualState = boardVisualState,
                                boardRenderData = boardRenderData,
                                boardRenderEvents = boardRenderEventsEmpty,
                                tapEvents = tapEventsEmpty,
                                tutorial = { },
                                content = {
                                    EditControlsPreview(
                                        isLandscapeScreen = config.landScape,
                                        colorState = EditColorState(
                                            playerSide = playerSide,
                                            editColor = WHITE,
                                            editTurn = WHITE,
                                        ),
                                        colorEvents = EditColorEvents(),
                                        actionState = EditActionState(
                                            pieceCounts = PieceCounts(4, 4),
                                        ),
                                        actionEvents = EditActionEvents(),
                                    )
                                },
                                turnIndicator = {
                                    TurnIndicator(
                                        modifier = it,
                                        state = indicatorState,
                                        currentTurn = gameState.currentTurn,
                                        indicatorEvents = indicatorEvents,
                                    )
                                },
                            )
                        }

                        if (!isEditing) {
                            BottomGameBar(
                                history = gameManagerState.history,
                                moveIndex = gameManagerState.moveIndex,
                                onUndo = {},
                                onRedo = {},
                                onMoveToCurrent = {},
                                modifier = Modifier.fillMaxSize(),
                                initialHistoryExpanded = config.historyPanelOpen,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Factories de previews ─────────────────────────────────────────────────────
//
// La paleta viaja a través de GameScreenPreviewConfig.palette hasta TaratiTheme.
// NO se llama a PaletteManager.setPalette en ningún lugar.

@Composable
fun GameScreenPreview_WithDrawer_Portrait(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            drawerStateValue = DrawerValue.Open,
            gameState = previewRandomMidGameState,
        ),
    )
}

/** Overload que acepta un [GameScreenPreviewConfig] completo, útil para previews de
 *  Play Store que necesitan configuración específica de estado (ej. dropdown expandido). */
@Composable
fun GameScreenPreview_WithDrawer_Portrait(config: GameScreenPreviewConfig) {
    GameScreenPreviewContent(config = config)
}

@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            darkTheme = true,
            drawerStateValue = DrawerValue.Open,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_Drawer_Closed_Portrait(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            darkTheme = true,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_DrawerClosed_Portrait(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            isEditing = true,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_GameInProgress(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            drawerStateValue = DrawerValue.Closed,
            gameState = previewRandomMidGameState,
        ),
    )
}

/** Overload that accepts a full [GameScreenPreviewConfig], e.g. to open the history panel. */
@Composable
fun GameScreenPreview_GameInProgress(config: GameScreenPreviewConfig) {
    GameScreenPreviewContent(config = config)
}

@Composable
fun GameScreenPreview_WithDrawer_Landscape(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            drawerStateValue = DrawerValue.Closed,
            landScape = true,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_WithDrawer_Landscape_Dark(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            darkTheme = true,
            drawerStateValue = DrawerValue.Open,
            landScape = true,
            gameState = previewRandomMidGameState,
        ),
    )
}

@Composable
fun GameScreenPreview_DrawerClosed_Landscape(palette: BoardPalette = ClassicPalette) {
    GameScreenPreviewContent(
        config = GameScreenPreviewConfig(
            palette = palette,
            landScape = true,
            isEditing = true,
            gameState = previewRandomMidGameState,
        ),
    )
}

// ── @Preview por paleta ───────────────────────────────────────────────────────

// Dark
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_DarkPalette(): Unit = GameScreenPreview_WithDrawer_Portrait(DarkPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_DarkPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(DarkPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_DarkPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(DarkPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_DarkPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(DarkPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_DarkPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(DarkPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_DarkPalette(): Unit = GameScreenPreview_GameInProgress(DarkPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkPalette(): Unit = GameScreenPreview_WithDrawer_Landscape(DarkPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_Dark_DarkPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(DarkPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_DarkPalette(): Unit = GameScreenPreview_DrawerClosed_Landscape(DarkPalette)

// Nature
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_NaturePalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(NaturePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_NaturePalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(NaturePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_NaturePalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(NaturePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_NaturePalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(NaturePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_NaturePalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(NaturePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_NaturePalette(): Unit = GameScreenPreview_GameInProgress(NaturePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_NaturePalette(): Unit = GameScreenPreview_WithDrawer_Landscape(NaturePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_Dark_NaturePalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(NaturePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_NaturePalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(NaturePalette)

// Grayscale
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_GrayscalePalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_GrayscalePalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_GrayscalePalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_GrayscalePalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_GrayscalePalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_GrayscalePalette(): Unit = GameScreenPreview_GameInProgress(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_GrayscalePalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_Dark_GrayscalePalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(GrayscalePalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_GrayscalePalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(GrayscalePalette)

// Halloween
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_HalloweenPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_HalloweenPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_HalloweenPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_HalloweenPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_HalloweenPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_HalloweenPalette(): Unit = GameScreenPreview_GameInProgress(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_HalloweenPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_Dark_HalloweenPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(HalloweenPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_HalloweenPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(HalloweenPalette)

// Christmas
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_ChristmasPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_ChristmasPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_ChristmasPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_ChristmasPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_ChristmasPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_ChristmasPalette(): Unit = GameScreenPreview_GameInProgress(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_ChristmasPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkChristmasPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(ChristmasPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_ChristmasPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(ChristmasPalette)

// Classic
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_ClassicPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_ClassicPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_ClassicPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_ClassicPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_ClassicPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_ClassicPalette(): Unit = GameScreenPreview_GameInProgress(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_ClassicPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkClassicPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(ClassicPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_ClassicPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(ClassicPalette)

// Ember
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_EmberPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(EmberPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_EmberPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(EmberPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_EmberPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(EmberPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_EmberPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(EmberPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_EmberPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(EmberPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_EmberPalette(): Unit = GameScreenPreview_GameInProgress(EmberPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_EmberPalette(): Unit = GameScreenPreview_WithDrawer_Landscape(EmberPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkEmberPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(EmberPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_EmberPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(EmberPalette)

// Gilded
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_GildedPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(GildedPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_GildedPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(GildedPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_GildedPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(GildedPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_GildedPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(GildedPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_GildedPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(GildedPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_GildedPalette(): Unit = GameScreenPreview_GameInProgress(GildedPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_GildedPalette(): Unit = GameScreenPreview_WithDrawer_Landscape(GildedPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkGildedPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(GildedPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_GildedPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(GildedPalette)

// Aurora
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Light_AuroraPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_WithDrawer_Portrait_Dark_AuroraPalette(): Unit =
    GameScreenPreview_WithDrawer_Portrait_Dark(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Light_AuroraPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_Drawer_Closed_Portrait_Dark_AuroraPalette(): Unit =
    GameScreenPreview_Drawer_Closed_Portrait_Dark(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview_DrawerClosed_Portrait_Light_AuroraPalette(): Unit =
    GameScreenPreview_DrawerClosed_Portrait(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_GameInProgress_AuroraPalette(): Unit = GameScreenPreview_GameInProgress(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_AuroraPalette(): Unit = GameScreenPreview_WithDrawer_Landscape(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_WithDrawer_Landscape_DarkAuroraPalette(): Unit =
    GameScreenPreview_WithDrawer_Landscape_Dark(AuroraPalette)

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
fun GameScreenPreview_DrawerClosed_Landscape_AuroraPalette(): Unit =
    GameScreenPreview_DrawerClosed_Landscape(AuroraPalette)

// ── Helpers de eventos ────────────────────────────────────────────────────────

private val previewLog = LoggingFactory.getLogger("GameScreenPreview")

@Composable
private fun createPreviewSidebarEvents(
    currentIsEditing: Boolean,
    onGameStateUpdate: (GameState) -> Unit,
    onPlayerSideUpdate: (CobColor) -> Unit,
    onEditingUpdate: (Boolean) -> Unit,
): SidebarEvents =
    object : SidebarEvents {
        override fun onMoveToCurrent() = onGameStateUpdate(initialGameState())
        override fun onMoveToIndex(moveIndex: Int) = previewLog.debug("Move to index: $moveIndex")
        override fun onUndo() {}
        override fun onRedo() {}
        override fun onDifficultyChangeWhite(difficulty: Difficulty) = previewLog.debug("White difficulty: $difficulty")
        override fun onDifficultyChangeBlack(difficulty: Difficulty) = previewLog.debug("Black difficulty: $difficulty")
        override fun onSetPlayerIsAI(color: CobColor, isAI: Boolean) = previewLog.debug("AI toggled")
        override fun onSettings() = previewLog.debug("Settings clicked")
        override fun onNewGame(color: CobColor) {
            onPlayerSideUpdate(color)
            onGameStateUpdate(initialGameState())
        }

        override fun onEditBoard() {
            onEditingUpdate(!currentIsEditing)
        }

        override fun onRotateBoard() = previewLog.debug("Board rotation clicked")
        override fun onGamesLibrary() = previewLog.debug("Games library clicked")
        override fun onOnlineLobby() = previewLog.debug("Online lobby clicked")
        override fun onSaveGame() = previewLog.debug("Save game clicked")
        override fun onAboutClick() = previewLog.debug("About clicked")
        override fun onCopyMoveHistory(moves: List<Move>) = previewLog.debug("History copied")
        override fun onShowAchievements() = previewLog.debug("Achievements clicked")
    }

private fun createPreviewIndicatorEvents(): IndicatorEvents =
    object : IndicatorEvents {
        override fun onTouch() = previewLog.debug("Indicator touched")
    }