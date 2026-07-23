package com.agustin.tarati.ui.components.editor.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.pieces.PieceCounts
import com.agustin.tarati.core.domain.game.pieces.opponent
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.LoggingFactory
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.ui.components.editor.BottomControls
import com.agustin.tarati.ui.components.editor.CreateEditControls
import com.agustin.tarati.ui.components.editor.EditActionEvents
import com.agustin.tarati.ui.components.editor.EditActionState
import com.agustin.tarati.ui.components.editor.EditColorEvents
import com.agustin.tarati.ui.components.editor.EditColorState
import com.agustin.tarati.ui.components.editor.LeftControls
import com.agustin.tarati.ui.components.editor.RightControls
import com.agustin.tarati.ui.components.editor.TopControls
import com.agustin.tarati.ui.components.game.Board
import com.agustin.tarati.ui.components.game.BoardEvents
import com.agustin.tarati.ui.components.game.BoardState
import com.agustin.tarati.ui.components.game.behaviors.TapEvents
import com.agustin.tarati.ui.components.game.draw.board.BoardRenderEvents
import com.agustin.tarati.ui.components.game.draw.board.createEmptyBoardRenderData
import com.agustin.tarati.ui.theme.TaratiTheme

@Preview(showBackground = true)
@Composable
fun EditingModePreviewContent(
    darkTheme: Boolean = false,
    isLandscape: Boolean = false,
    boardOrientation: BoardOrientation = if (isLandscape) BoardOrientation.LANDSCAPE_BLACK else BoardOrientation.PORTRAIT_WHITE,
) {
    TaratiTheme(darkTheme = darkTheme) {
        val exampleGameState = initialGameState()

        var isEditing by remember { mutableStateOf(true) }
        var aiEnabled by remember { mutableStateOf(false) }
        var editColor by remember { mutableStateOf(WHITE) }
        var editTurn by remember { mutableStateOf(WHITE) }
        var playerSide by remember { mutableStateOf(WHITE) }

        val pieceCounts = PieceCounts(4, 4)
        val isValidDistribution = true
        val isCompletedDistribution = true

        // Crear estado para Board
        val boardState =
            BoardState(
                gameState = exampleGameState,
                aiEnabled = aiEnabled,
                boardOrientation = boardOrientation,
                boardVisualState =
                    BoardVisualState(
                        edgesVisibles = true,
                    ),
                isEditing = isEditing,
            )

        // Crear eventos para Board
        createPreviewBoardEvents()

        Box(modifier = Modifier.fillMaxSize()) {
            Board(
                modifier = Modifier.fillMaxSize(),
                playerSide = playerSide,
                boardState = boardState,
                boardData = createEmptyBoardRenderData().copy(gameState = exampleGameState),
                boardEvents = boardRenderEventsEmpty,
                tapEvents = tapEventsEmpty,
            )

            EditControlsPreview(
                isLandscapeScreen = isLandscape,
                colorState =
                    EditColorState(
                        playerSide = playerSide,
                        editColor = editColor,
                        editTurn = editTurn,
                    ),
                colorEvents =
                    EditColorEvents(
                        onColorToggle = { editColor = editColor.opponent },
                        onPlayerSideToggle = { playerSide = playerSide.opponent },
                        onTurnToggle = { editTurn = editTurn.opponent },
                    ),
                actionState =
                    EditActionState(
                        pieceCounts = pieceCounts,
                        isValidDistribution = isValidDistribution,
                        isCompletedDistribution = isCompletedDistribution,
                    ),
                actionEvents =
                    EditActionEvents(
                        onClearBoard = { /* No-op en preview */ },
                    ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
fun EditControlsPreview_Portrait() {
    TaratiTheme {
        EditControlsPreview(isLandscapeScreen = false)
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 200)
@Composable
fun EditControlsPreview_Landscape() {
    TaratiTheme {
        EditControlsPreview(isLandscapeScreen = true)
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
fun EditControlsPreview_InvalidDistribution() {
    TaratiTheme {
        EditControlsPreview(
            isLandscapeScreen = false,
            actionState =
                EditActionState(
                    pieceCounts = PieceCounts(8, 0),
                    isValidDistribution = false,
                    isCompletedDistribution = false,
                ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
fun EditControlsPreview_CompletedDistribution() {
    TaratiTheme {
        EditControlsPreview(
            isLandscapeScreen = false,
            actionState =
                EditActionState(
                    pieceCounts = PieceCounts(7, 1),
                ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
fun EditControlsPreview_BlackColor() {
    TaratiTheme {
        EditControlsPreview(
            isLandscapeScreen = false,
            colorState =
                EditColorState(
                    editColor = BLACK,
                    playerSide = BLACK,
                    editTurn = BLACK,
                ),
        )
    }
}

@Composable
fun EditControlsPreview(
    isLandscapeScreen: Boolean,
    colorState: EditColorState = EditColorState(),
    colorEvents: EditColorEvents = EditColorEvents(),
    actionState: EditActionState = EditActionState(),
    actionEvents: EditActionEvents = EditActionEvents(),
) {
    CreateEditControls(
        isLandscapeScreen = isLandscapeScreen,
        colorState = colorState,
        colorEvents = colorEvents,
        actionState = actionState,
        actionEvents = actionEvents,
    )
}

@Preview(showBackground = true, widthDp = 200, heightDp = 300)
@Composable
fun LeftControlsPreview() {
    TaratiTheme {
        LeftControls(
            state =
                EditColorState(
                    playerSide = WHITE,
                    editColor = WHITE,
                    editTurn = WHITE,
                ),
            events = EditColorEvents(),
        )
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 300)
@Composable
fun RightControlsPreview() {
    TaratiTheme {
        RightControls(
            state =
                EditActionState(
                    pieceCounts = PieceCounts(4, 4),
                ),
            events = EditActionEvents(),
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 100)
@Composable
fun TopControlsPreview() {
    TaratiTheme {
        TopControls(
            state =
                EditColorState(
                    playerSide = WHITE,
                    editColor = WHITE,
                    editTurn = WHITE,
                ),
            events = EditColorEvents(),
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 100)
@Composable
fun BottomControlsPreview() {
    TaratiTheme {
        BottomControls(
            state =
                EditActionState(
                    pieceCounts = PieceCounts(4, 4),
                ),
            events = EditActionEvents(),
        )
    }
}

private val previewLog = LoggingFactory.getLogger("EditorPreview")

fun createPreviewBoardEvents(): BoardEvents =
    object : BoardEvents {
        override fun onMove(move: Move) = previewLog.debug("Move from ${move.from} to ${move.to}")

        override fun onEditPiece(from: Vertex) = previewLog.debug("Edit piece at $from")

        override fun onResetCompleted() = previewLog.debug("Reset completed")
    }

val tapEventsEmpty: TapEvents =
    object : TapEvents {
        override fun onSelected(from: Vertex, valid: List<Vertex>) {}

        override fun onMove(move: Move) {}

        override fun onInvalid(from: Vertex, valid: List<Vertex>) {}

        override fun onEditPieceRequested(from: Vertex) {}

        override fun onCancel() {}

        override fun onPreMoveSelected(from: Vertex, valid: List<Vertex>) {}

        override fun onPreMoveSet(move: Move) {}

        override fun onPreMoveCancel() {}
    }

val boardRenderEventsEmpty: BoardRenderEvents =
    object : BoardRenderEvents {
        override fun onReset() {}

        override fun onBoardSizeChange(size: Size) {}

        override fun onUpdateBoardOrientation(orientation: BoardOrientation) {}

        override fun onSyncState(gameState: GameState) {}
    }
