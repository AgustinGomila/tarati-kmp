package com.agustin.tarati.features.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.database.dto.PGNHeader
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.rotateCW
import com.agustin.tarati.core.domain.game.manager.GameManager
import com.agustin.tarati.core.domain.game.manager.GameManagerState
import com.agustin.tarati.core.domain.game.manager.gameManagerState
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.getMatchResult
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.MatchResult
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.domain.game.play.getValue
import com.agustin.tarati.features.settings.SettingsRepository
import com.agustin.tarati.ui.components.editor.EditBoardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared game ViewModel. Platform-specific surface: [saveGameState], [persistEditingState],
 * [playerSettings] — overridden by [AndroidGameViewModel] and [DesktopGameViewModel].
 */
abstract class GameViewModel(
    private val sr: SettingsRepository,
    protected val aiEngine: IAIEngine,
) : ViewModel(),
    IGameModel {

    // ── GameManager ───────────────────────────────────────────────────────────

    private val gameManager: GameManager by lazy {
        GameManager(GameManagerState.createInitialUiState())
    }

    // ── IGameService: delegated state flows ───────────────────────────────

    override val gameState: StateFlow<GameState>
        get() = gameManager.gameState

    override val history: StateFlow<StableHistoryList>
        get() = gameManager.history

    override val moveIndex: StateFlow<Int>
        get() = gameManager.moveIndex

    override val gameStatus: StateFlow<GameStatus>
        get() = gameManager.gameStatus

    // ── IGameModel: composable state ──────────────────────────────────────

    override val gameManagerState: State<GameManagerState>
        @Composable get() = gameManager.gameManagerState

    // ── Board orientation ─────────────────────────────────────────────────────

    // Loaded from DataStore, not SavedStateHandle — the bundle would shadow the DataStore
    // value on config change, breaking cross-session persistence.
    private val _boardOrientation = MutableStateFlow(BoardOrientation.PORTRAIT_WHITE)
    override val boardOrientation: StateFlow<BoardOrientation> = _boardOrientation.asStateFlow()

    /** While true, [GameEffects] skips automatic orientation recalculation on screen rotation. */
    private val _isManuallyRotated = MutableStateFlow(false)
    override val isManuallyRotated: StateFlow<Boolean> = _isManuallyRotated.asStateFlow()

    override fun rotateBoardManually() {
        val next = _boardOrientation.value.rotateCW()
        _boardOrientation.update { next }
        _isManuallyRotated.update { true }
        viewModelScope.launch {
            sr.setBoardOrientation(next)
            sr.setManuallyRotated(true)
        }
    }

    override fun resetManualRotation() {
        _isManuallyRotated.update { false }
        viewModelScope.launch { sr.setManuallyRotated(false) }
    }

    // ── Clipboard / paste ─────────────────────────────────────────────────────

    private val _pasteRequested = MutableStateFlow(false)
    override val pasteRequested: StateFlow<Boolean> = _pasteRequested.asStateFlow()

    private val _boardPosition = MutableStateFlow("")
    override val boardPosition: StateFlow<String> = _boardPosition.asStateFlow()

    private val _showLogoTransition = MutableStateFlow(true)
    override val showLogoTransition: StateFlow<Boolean> = _showLogoTransition.asStateFlow()

    // ── User name ─────────────────────────────────────────────────────────────

    private val _userName = MutableStateFlow("")
    override val userName: StateFlow<String> = _userName.asStateFlow()

    // ── Player settings (platform-specific) ──────────────────────────────────

    protected abstract val playerSettings: IPlayerSettingsHolder

    // ── IGameService ──────────────────────────────────────────────────────────

    override fun updateAIEnabled(newAIEnabled: Boolean): Unit =
        playerSettings.updateAIEnabled(newAIEnabled)

    override fun boardPositionCopied() {
        _boardPosition.update { "" }
    }

    /** In-memory update only — does not persist; only [rotateBoardManually] does. */
    override fun updateBoardOrientation(newOrientation: BoardOrientation) {
        _boardOrientation.update { newOrientation }
    }

    override fun updateUserName(name: String) {
        _userName.update { name }
    }

    // ── IPlayerManager ────────────────────────────────────────────────────────

    override fun updatePlayerType(color: CobColor, isAI: Boolean): Unit =
        playerSettings.updatePlayerType(color, isAI)

    override fun updateDifficulty(color: CobColor, difficulty: Difficulty): Unit =
        playerSettings.updateDifficulty(color, difficulty)

    // ── Board moves ───────────────────────────────────────────────────────────

    override fun addMove(
        move: Move,
        nextState: GameState,
        onMoveRecord: () -> Unit,
    ): Unit = gameManager.addMove(move, nextState, onMoveRecord)

    override fun undoMove(): Unit = gameManager.undoMove()

    override fun redoMove(): Unit = gameManager.redoMove()

    override fun moveToCurrentState(): Unit = gameManager.moveToCurrentState()

    override fun moveToIndex(index: Int): Unit = gameManager.moveToIndex(index)

    // ── History helpers ───────────────────────────────────────────────────────

    private fun clearHistory() = gameManager.clearHistory()

    override fun updateHistory(moves: List<Move>, initialState: GameState): Unit =
        gameManager.updateHistory(moves, initialState)

    override fun setGame(gameState: GameState) {
        clearHistory()
        gameManager.setInitialGameState(gameState)
        gameManager.updateGameState(gameState)
    }

    // ── Edit Board ────────────────────────────────────────────────────────────

    private val editBoardManager by lazy { EditBoardManager() }

    /**
     * Persists the editing mode flag to the platform-specific session store.
     * Android: saves to SavedStateHandle so the flag survives screen rotation.
     * Desktop: no-op.
     */
    protected abstract fun persistEditingState(isEditing: Boolean)

    override fun toggleEditing() {
        editBoardManager.toggleEditing(
            currentTurn = gameManager.gameState.value.currentTurn,
            boardOrientation = _boardOrientation.value,
        )
        persistEditingState(editBoardManager.isEditing.value)
    }

    override fun toggleEditColor(): Unit = editBoardManager.toggleEditColor()

    override fun toggleEditTurn(): Unit = editBoardManager.toggleEditTurn()

    override fun rotateEditBoard(): Unit = editBoardManager.rotateEditBoard()

    override fun togglePlayerSide(): Unit = playerSettings.togglePlayerSide()

    override fun clearEditBoard() {
        val cleanState = editBoardManager.clearEditBoard()
        gameManager.updateGameState(cleanState)
    }

    override fun editPiece(vertex: Vertex) {
        val currentState = gameManager.gameState.value
        val newState = editBoardManager.editPiece(vertex, currentState)
        gameManager.updateGameState(newState)
    }

    override fun startGameFromEditedState() {
        val currentState = gameManager.gameState.value
        if (!editBoardManager.validateDistributionForGameStart(currentState)) return
        endEditing()
        val gameStateWithTurn = currentState.copy(currentTurn = editBoardManager.editTurn.value)
        setGame(gameStateWithTurn)
        _startedFromEditedBoard.update { true }
        resumeGame()
    }

    override fun copyBoardToClipboard() {
        _boardPosition.update { gameManager.gameState.value.toPositionNotation() }
    }

    override fun pasteBoardFromClipboard(isRequested: Boolean) {
        _pasteRequested.update { isRequested }
    }

    // ── IGameActions ──────────────────────────────────────────────────────────

    override fun suppressLogoTransition() {
        _showLogoTransition.update { false }
    }

    override fun gameOver(): Unit = gameManager.updateGameStatus(GameStatus.GAME_OVER)

    override fun stopGame() {
        gameManager.updateGameStatus(GameStatus.NO_PLAYING)
    }

    override fun resumeGame(): Unit = gameManager.updateGameStatus(GameStatus.PLAYING)

    override fun endEditing() {
        editBoardManager.endEditing()
        persistEditingState(false)
    }

    override fun updateGameState(gameState: GameState): Unit = gameManager.updateGameState(gameState)

    override fun startGame(playerSide: CobColor) {
        _showLogoTransition.update { true }
        endEditing()
        playerSettings.updatePlayerSide(playerSide)
        aiEngine.clearHistory()
        setGame(GameState.initialGameState())
        _startedFromEditedBoard.update { false }
        _startedFromImportedGame.update { false }
        resumeGame()
        saveGameState()
    }

    // ── IEditBoardManager ─────────────────────────────────────────────────────

    override val isEditing: StateFlow<Boolean> get() = editBoardManager.isEditing
    override val editColor: StateFlow<CobColor> get() = editBoardManager.editColor
    override val editTurn: StateFlow<CobColor> get() = editBoardManager.editTurn
    override val editBoardOrientation: StateFlow<BoardOrientation> get() = editBoardManager.editBoardOrientation

    // ── IPlayerManager ────────────────────────────────────────────────────────

    override val playerSide: StateFlow<CobColor> get() = playerSettings.playerSide
    override val aIEnabled: StateFlow<Boolean> get() = playerSettings.aIEnabled
    override val whiteIsAI: StateFlow<Boolean> get() = playerSettings.whiteIsAI
    override val blackIsAI: StateFlow<Boolean> get() = playerSettings.blackIsAI
    override val difficultyWhite: StateFlow<Difficulty> get() = playerSettings.difficultyWhite
    override val difficultyBlack: StateFlow<Difficulty> get() = playerSettings.difficultyBlack

    // ── IGamesLibraryManager ──────────────────────────────────────────────────

    override fun importGameFromMatchDto(matchDto: MatchDto) {
        _showLogoTransition.update { false }
        endEditing()

        val initialState = runCatching { GameState.parseBoardNotation(matchDto.game.initialBoardPosition) }
            .getOrElse { GameState.initialGameState() }

        gameManager.clearHistory(initialState)
        gameManager.setInitialGameState(initialState)
        updateHistory(matchDto.game.moveHistory, initialState)

        val savedBoardPosition = matchDto.game.boardPosition
        val targetIndex = gameManager.history.value.toList()
            .indexOfLast { it.gameState.toPositionNotation() == savedBoardPosition }
        gameManager.moveToIndex(targetIndex)

        _startedFromImportedGame.update { true }
        resumeGame()
    }

    override fun exportGameToMatchDto(whiteLabel: String, blackLabel: String): MatchDto {
        val gameState = gameManager.gameState.value
        val moveHistory = gameManager.history.value
        val matchState = gameState.getMatchState(aiEngine.positionHistory)
        val winner = matchState.winner
        val isGameOver = matchState.gameEndReason != GameEndReason.PLAYING

        val header = PGNHeader(
            white = whiteLabel,
            black = blackLabel,
            result = winner?.getMatchResult()?.getValue() ?: MatchResult.UNDEFINED.getValue(),
            termination = if (isGameOver) matchState.gameEndReason.key else PGNHeader().termination,
        )

        val initialState = gameManager.initialGameState

        return MatchDto(
            header = header,
            game = GameDto(
                initialBoardPosition = initialState.toPositionNotation(),
                boardPosition = gameState.toPositionNotation(),
                matchResult = winner?.getMatchResult() ?: MatchResult.UNDEFINED,
                moveHistory = moveHistory.getMoves(),
            ),
        )
    }

    // ── startedFromEditedBoard / startedFromImportedGame ──────────────────────

    private val _startedFromEditedBoard = MutableStateFlow(false)

    /** True when this game started from a custom board position set in the editor. */
    override val startedFromEditedBoard: StateFlow<Boolean> = _startedFromEditedBoard.asStateFlow()

    private val _startedFromImportedGame = MutableStateFlow(false)

    /** True when this game was loaded from a previously saved game. */
    override val startedFromImportedGame: StateFlow<Boolean> = _startedFromImportedGame.asStateFlow()

    // ── Platform-specific session state ───────────────────────────────────────

    /** Android: serializes to SavedStateHandle. Desktop: no-op. */
    abstract override fun saveGameState()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            _boardOrientation.value = BoardOrientation.valueOf(sr.boardOrientation.first())
            _isManuallyRotated.value = sr.isManuallyRotated.first()
        }
    }
}