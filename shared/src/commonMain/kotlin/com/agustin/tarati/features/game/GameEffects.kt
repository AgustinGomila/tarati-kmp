package com.agustin.tarati.features.game


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.toBoardOrientation
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.pieces.PieceCounts
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameStatus
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.domain.tutorial.TutorialState
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.connection.ConnectionState
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.features.online.ui.OnlineSearchBar
import com.agustin.tarati.features.online.ui.SpectatingPill
import com.agustin.tarati.features.seasonal.ISpecialEventManager
import com.agustin.tarati.features.seasonal.SpecialEventOverlay
import com.agustin.tarati.features.settings.BoardVisualState
import com.agustin.tarati.network.models.MatchmakingState
import com.agustin.tarati.services.clock.ClockViewModel
import com.agustin.tarati.services.clock.IClockService
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.tarati
import com.agustin.tarati.ui.components.bottombar.BottomGameBar
import com.agustin.tarati.ui.components.bottombar.rememberBoardTilt
import com.agustin.tarati.ui.components.editor.DistributionState
import com.agustin.tarati.ui.components.editor.EditActionState
import com.agustin.tarati.ui.components.editor.EditColorState
import com.agustin.tarati.ui.components.editor.EditControls
import com.agustin.tarati.ui.components.editor.IEditBoard
import com.agustin.tarati.ui.components.editor.IEditBoardManager
import com.agustin.tarati.ui.components.game.BoardEvents
import com.agustin.tarati.ui.components.game.CreateBoard
import com.agustin.tarati.ui.components.game.CreateBoardState
import com.agustin.tarati.ui.components.game.animation.BoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.BoardGeometryViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardGeometryViewModel
import com.agustin.tarati.ui.components.game.behaviors.BoardSelectionViewModel
import com.agustin.tarati.ui.components.game.behaviors.IBoardSelectionViewModel
import com.agustin.tarati.ui.components.game.behaviors.TapEvents
import com.agustin.tarati.ui.components.game.draw.board.BoardRenderData
import com.agustin.tarati.ui.components.game.draw.board.BoardRenderEvents
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import com.agustin.tarati.ui.components.game.highlights.createSelectionCaptureHighlights
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.components.turnIndicator.FiftyMoveClaimBadge
import com.agustin.tarati.ui.components.turnIndicator.IndicatorEvents
import com.agustin.tarati.ui.components.turnIndicator.TurnIndicator
import com.agustin.tarati.ui.components.turnIndicator.TurnIndicatorState
import com.agustin.tarati.ui.components.tutorial.ITutorialViewModel
import com.agustin.tarati.ui.components.tutorial.TutorialEvents
import com.agustin.tarati.ui.components.tutorial.TutorialOverlay
import com.agustin.tarati.ui.components.tutorial.TutorialViewModel
import com.agustin.tarati.ui.layout.LocalScreenLayout
import com.agustin.tarati.ui.layout.ScreenLayout
import com.agustin.tarati.ui.theme.TaratiLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Flag de **nivel de proceso** que recuerda si la transición del logo (splash → indicador de turno) ya
 * se reprodujo en esta ejecución de la app. `rememberSaveable` solo sobrevive rotaciones, pero al
 * cambiar de modo (single↔multi) el subárbol de [GameEffects] se desmonta y su estado se pierde → la
 * animación se re-disparaba. Con esta bandera de proceso, la transición aparece **solo al iniciar la
 * app** y nunca más durante la ejecución (los remontajes ya la ven en su posición final).
 */
private object LogoTransitionState {
    var played = false
}

@Composable
fun GameEffects(
    drawerState: DrawerState,
    isLandscape: Boolean,
    isManuallyRotated: Boolean,
    gameState: GameState,
    aiThinkingDependencies: AiThinkingDependencies,
    onAITurn: (gameState: GameState) -> Unit,
    onBoardOrientationChanged: (BoardOrientation) -> Unit,
    isTutorialActive: Boolean,
    tutorialState: TutorialState,
    onTutorialEnd: () -> Unit,
    aiEngine: IAIEngine = koinInject(),
) {
    LaunchedEffect(tutorialState) {
        if (tutorialState == TutorialState.Completed) {
            onTutorialEnd()
        }
    }

    // Automatic orientation: only runs when the screen rotates or the assigned player
    // side changes (online game). Intentionally does NOT react to whiteIsAI/blackIsAI
    // changes — toggling Human/AI mid-game must not rotate the board. isManuallyRotated
    // is intentionally NOT reset on new game — the Sidebar orientation choice persists.
    LaunchedEffect(isLandscape, isManuallyRotated, aiThinkingDependencies.orientationSide) {
        if (!isManuallyRotated) {
            val orientationSide = aiThinkingDependencies.orientationSide ?: when {
                !aiThinkingDependencies.whiteIsAI -> CobColor.WHITE
                !aiThinkingDependencies.blackIsAI -> CobColor.BLACK
                else -> CobColor.WHITE
            }
            onBoardOrientationChanged(toBoardOrientation(isLandscape, orientationSide))
        }
    }

    // Efecto para ejecutar pensamiento de IA
    LaunchedEffect(aiThinkingDependencies) {
        if (!aiThinkingDependencies.isAITurn ||
            aiThinkingDependencies.isEditing ||
            aiThinkingDependencies.gameStatus != GameStatus.PLAYING
        ) {
            getLogger().debug(
                "DEBUG: AI blocked - gameStatus: ${aiThinkingDependencies.gameStatus}, isAITurn: ${aiThinkingDependencies.isAITurn}",
            )
            return@LaunchedEffect
        }

        if (gameState.isGameOver(aiEngine.positionHistory)) return@LaunchedEffect

        // Apply the difficulty config for the side that is about to move before
        // requesting a move calculation — this is how per-side difficulty works
        // in AI vs AI: each time the turn changes the engine is reconfigured.
        aiEngine.setConfig(aiThinkingDependencies.currentTurnConfig)

        // WASM: give the animation system time to render the previous move before
        // the AI search occupies the main thread. AiThinkingDependencies does not
        // include isAIThinking, so this delay is not cancelled by the mid-think
        // state changes. On Android/Desktop AI_MOVE_DELAY_MS = 0 (background thread).
        if (AI_MOVE_DELAY_MS > 0L) delay(AI_MOVE_DELAY_MS.milliseconds)

        onAITurn(gameState)
    }

    // Efecto para drawer y tutorial
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.isOpen && isTutorialActive) {
            onTutorialEnd()
        }
    }
}

/**
 * Contenido principal de la pantalla de juego.
 *
 * ## Estructura de layout
 * ```
 * TaratiBackground  ← fondo decorativo (GameScreen)
 * └── Scaffold (containerColor = Transparent)
 *       ├── topBar : TaratiTopBar
 *       └── content
 *             └── Box (fillMaxSize)
 *                   ├── Column                  ← board
 *                   │     └── CreateBoard (weight=1f)
 *                   ├── TurnIndicator           ← overlay TopEnd: indicador de turno
 *                   └── BottomGameBar           ← overlay, último hijo del Box
 *                         (sin edición, sin tutorial)
 * ```
 *
 * ## Por qué BottomGameBar no desplaza el tablero
 * `BottomGameBar` es el **último hijo** del `Box` principal. En Compose, los
 * hijos de un `Box` se superponen en orden de declaración y no afectan el
 * tamaño de sus hermanos. El tablero (`CreateBoard` con `weight=1f`) ocupa
 * siempre el 100% del espacio disponible tras el topBar; el `BottomGameBar`
 * flota encima sin modificar ese tamaño.
 *
 * @param history         Historial estable pasado a [BottomGameBar].
 * @param onUndo          Lambda que ejecuta undo sincronizando el historial del engine.
 * @param onRedo          Lambda que ejecuta redo sincronizando el historial del engine.
 * @param onMoveToCurrent Lambda que salta al último movimiento de la partida.
 * @param onMoveToIndex   Lambda invocada al hacer clic en un movimiento de la lista del FAB.
 * @param showTurnIndicator Si false, el círculo indicador de turno no se renderiza.
 */
@ExperimentalMaterial3Api
@Composable
fun MainContent(
    drawerState: DrawerState,
    isLandscape: Boolean,
    boardSize: Size,
    boardOrientation: BoardOrientation,
    editBoardOrientation: BoardOrientation,
    gameState: GameState,
    moveIndex: Int,
    history: StableHistoryList,
    turnState: TurnIndicatorState,
    distributionState: DistributionState,
    isTutorialActive: Boolean,
    isAIThinking: Boolean,
    boardVisualState: BoardVisualState,
    preMovesEnabled: Boolean,
    pieceCounts: PieceCounts,
    boardEvents: BoardEvents,
    events: GameEvents,
    playerManager: IPlayerManager,
    boardManager: IEditBoardManager,
    editBoard: IEditBoard,
    onTouchIndicator: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)? = null,
    canClaimDraw: Boolean = false,
    onClaimFiftyMoveDraw: () -> Unit = {},
    showLogoTransition: Boolean = true,
    animationViewModel: IBoardAnimationViewModel = koinViewModel<BoardAnimationViewModel>(),
    geometryViewModel: IBoardGeometryViewModel = koinViewModel<BoardGeometryViewModel>(),
    selectViewModel: IBoardSelectionViewModel = koinViewModel<BoardSelectionViewModel>(),
    tutorialViewModel: ITutorialViewModel = koinViewModel<TutorialViewModel>(),
    specialEventManager: ISpecialEventManager = koinInject(),
    /** Compact online indicator rendered to the left of the FAB. */
    onlineContent: (@Composable () -> Unit)? = null,
    /** Null while a game is in progress — hides the search bar. */
    connectionState: ConnectionState? = null,
    matchmakingState: MatchmakingState = MatchmakingState.Idle,
    onCreateSearch: () -> Unit = {},
    onCancelSearch: () -> Unit = {},
    spectatingState: SpectatingState? = null,
    onStopSpectating: () -> Unit = {},
    showTurnIndicator: Boolean = true,
    /** `false` grisa undo/redo/saltar del FAB (online en curso / espectador); ver [BottomGameBar]. */
    navigationEnabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Landscape: track history panel open to animate board shift
    var isHistoryPanelOpen by remember { mutableStateOf(false) }
    // Portrait: track FAB and history panel for inertial tilt
    var isFabExpanded by remember { mutableStateOf(false) }
    // Padding end shrinks the Column from the right so the board re-centres
    // within the remaining space. Using layout padding (not graphicsLayer
    // translationX) guarantees the board never clips outside the screen.
    val boardEndPadding by animateDpAsState(
        targetValue = if (isLandscape && isHistoryPanelOpen) 320.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "board_landscape_shift",
    )

    // ── Tilt inercial del tablero ─────────────────────────────────────────────
    // Cabeceo compartido con el juego multijugador: kick corto (80 ms) + rebote elástico al expandir
    // el FAB (rotationY) o abrir el panel de historial (rotationX). Ver [rememberBoardTilt].
    val boardTilt = rememberBoardTilt(
        isFabExpanded = isFabExpanded,
        isHistoryPanelOpen = isHistoryPanelOpen,
    )

    // ── Logo transition ──────────────────────────────────────────────────────
    // transitionDone arranca siempre en false. Dos caminos lo ponen en true:
    //   A) La animación se completa normalmente (inicio de app normal).
    //   B) showLogoTransition llega a false antes o durante la espera de centros
    //      (tutorial automático en primer inicio, importación de partida).
    // Sembrado desde el flag de proceso: si ya se reprodujo (p. ej. tras un cambio de modo), arranca en
    // true → no re-anima. `rememberSaveable` además lo conserva ante rotaciones dentro del mismo mount.
    var transitionDone by rememberSaveable { mutableStateOf(LogoTransitionState.played) }

    // Centre of the TurnIndicator circle — updated every frame until stable.
    var indicatorCentreWindow by remember { mutableStateOf<Offset?>(null) }
    // Centre of the overlay Box — captured once (it never moves).
    var overlayCentreWindow by remember { mutableStateOf<Offset?>(null) }

    // Splash logo size (matches SplashScreen). Target = size * 0.8f inside indicator.
    val splashLogoSizeDp = 100.dp
    val splashLogoSizePx = with(density) { splashLogoSizeDp.toPx() }
    val targetLogoSizePx = with(density) { 42.dp.toPx() }

    // Single animatable drives position + size together; alpha fades out at end. Si la transición ya
    // se reprodujo en este proceso, arrancan en su estado FINAL (logo en el indicador y ya desvanecido)
    // → al remontar (cambio de modo) no hay desplazamiento ni parpadeo.
    val logoProgress = remember { Animatable(if (LogoTransitionState.played) 1f else 0f) }
    val logoAlpha = remember { Animatable(if (LogoTransitionState.played) 0f else 1f) }

    // Supresión reactiva: cuando showLogoTransition se vuelve false (tutorial
    // automático o importación de partida), marca la transición como completada
    // inmediatamente, incluso si LaunchedEffect(Unit) ya está esperando los centros.
    LaunchedEffect(showLogoTransition) {
        if (!showLogoTransition) {
            transitionDone = true
            LogoTransitionState.played = true // el arranque ya pasó → no reproducir en remontajes
        }
    }

    // Fire once on entry. Chequea transitionDone después de cada suspensión
    // para detectar supresiones que llegan mientras se espera el layout.
    LaunchedEffect(Unit) {
        if (transitionDone) return@LaunchedEffect

        snapshotFlow { overlayCentreWindow to transitionDone }
            .first { (centre, done) -> centre != null || done }
        if (transitionDone) return@LaunchedEffect

        snapshotFlow { indicatorCentreWindow to transitionDone }
            .first { (centre, done) -> centre != null || done }
        if (transitionDone) return@LaunchedEffect

        delay(50.milliseconds)
        if (transitionDone) return@LaunchedEffect

        logoProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
        transitionDone = true
        LogoTransitionState.played = true // reproducida una vez → no repetir en esta ejecución
        logoAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200),
        )
    }

    // Obtener estados actualizados
    val editColor by boardManager.editColor.collectAsState()
    val editTurn by boardManager.editTurn.collectAsState()
    val isEditing by boardManager.isEditing.collectAsState()

    val playerSide by playerManager.playerSide.collectAsState()
    val aIEnabled by playerManager.aIEnabled.collectAsState()
    val whiteIsAI by playerManager.whiteIsAI.collectAsState()
    val blackIsAI by playerManager.blackIsAI.collectAsState()

    // Datos de renderizado
    val selectedVertex by selectViewModel.selectedVertex.collectAsState()
    val validAdjacentVertexes by selectViewModel.validAdjacentVertexes.collectAsState()
    val preMoveFromVertex by selectViewModel.preMoveFromVertex.collectAsState()
    val preMoveValidTargets by selectViewModel.preMoveValidTargets.collectAsState()
    val pendingPreMove by selectViewModel.pendingPreMove.collectAsState()

    val visualState by animationViewModel.visualState.collectAsState()
    val animatedPieces by animationViewModel.animatedPieces.collectAsState()
    val currentHighlight by animationViewModel.currentHighlights.collectAsState()

    // Controles de tiempo
    val clockService: IClockService = koinViewModel<ClockViewModel>()
    val clockState by clockService.clockState.collectAsState()

    // Piezas enemigas capturables desde el vértice seleccionado.
    val selectionCaptureHighlights = remember(selectedVertex, validAdjacentVertexes, gameState) {
        val from = selectedVertex
            ?: return@remember emptyList<HighlightAnimation>()
        validAdjacentVertexes
            .flatMap { dest ->
                val next = gameState.applyMove(Move(from to dest))
                gameState.detectCaptures(Move(from to dest), next).map { it.first }
            }
            .distinct()
            .let {
                createSelectionCaptureHighlights(
                    it
                )
            }
    }


    val boardRenderData = BoardRenderData(
        gameState = gameState,
        selectedVertex = selectedVertex,
        validAdjacentVertexes = validAdjacentVertexes,
        animatedPieces = animatedPieces,
        currentHighlights = currentHighlight + selectionCaptureHighlights,
        visualState = visualState,
        preMoveFromVertex = preMoveFromVertex,
        preMoveValidTargets = preMoveValidTargets,
        pendingPreMove = pendingPreMove,
    )

    LaunchedEffect(isEditing) {
        drawerState.closeIfOpen(scope)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // La barra superior solo se muestra en pantallas compactas (Android/teléfono), donde
            // aporta la hamburguesa que abre el drawer del sidebar y, aprovechando ese espacio, la
            // búsqueda online. En pantallas anchas el sidebar es permanente y ya ofrece el título y
            // el acceso "Online", así que la barra se omite para ganar altura de tablero (paridad
            // con el juego multijugador, que no tiene barra superior).
            if (LocalScreenLayout.current != ScreenLayout.Expanded) {
                TaratiTopBar(
                    drawerState = drawerState,
                    title = localizedString(Res.string.tarati),
                    navigationType = TopBarNavigationType.Menu,
                    isEditing = isEditing,
                    actions = {
                        // Pill espectador — visible cuando se está observando una partida
                        if (spectatingState != null) {
                            SpectatingPill(
                                state = spectatingState,
                                onStop = onStopSpectating,
                            )
                        }
                        // Search bar — oculta durante partida propia activa
                        val state = connectionState
                        if (state != null) {
                            OnlineSearchBar(
                                connectionState = state,
                                matchmakingState = matchmakingState,
                                onCreateSearch = onCreateSearch,
                                onCancelSearch = onCancelSearch,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {},
    ) { innerPadding ->

        // ── Box principal: tablero + overlay flotante ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            // ── Contenido del tablero ────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(end = boardEndPadding),
            ) {
                CreateBoard(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            rotationX = boardTilt.rotationX
                            rotationY = boardTilt.rotationY
                            // cameraDistance en px: valor estándar de Material para perspectiva sutil.
                            // Sin esto, rotaciones > ~10° producen distorsión de perspectiva excesiva.
                            cameraDistance = 12f * density.density
                        },
                    state = CreateBoardState(
                        gameState = gameState,
                        aiEnabled = aIEnabled,
                        whiteIsAI = if (isTutorialActive) false else whiteIsAI,
                        blackIsAI = if (isTutorialActive) false else blackIsAI,
                        playerSide = playerSide,
                        isEditing = isEditing,
                        isTutorialActive = isTutorialActive,
                        isAIThinking = isAIThinking,
                        boardOrientation = boardOrientation,
                        editBoardOrientation = editBoardOrientation,
                        boardVisualState = boardVisualState,
                    ),
                    boardRenderData = boardRenderData,
                    tapEvents = remember(selectViewModel, boardEvents) {
                        createTapEvents(selectViewModel, boardEvents)
                    },
                    boardRenderEvents = createBoardRenderEvents(
                        selectViewModel,
                        animationViewModel,
                        geometryViewModel
                    ),
                    boardVisualState = boardVisualState,
                    tutorial = {
                        if (isTutorialActive) {
                            TutorialOverlay(
                                boardSize = boardSize,
                                boardOrientation = boardOrientation,
                                labelsVisible = boardVisualState.labelsVisibles,
                                tutorialEvents = TutorialEvents(
                                    onPreStepTutorial = events::preStepTutorial,
                                    onSkipTutorial = events::endTutorial,
                                    onFinishTutorial = events::resetTutorial,
                                    onSkipInteractiveStep = {
                                        val expectedMove = tutorialViewModel.tutorialManager
                                            .getExpectedMoves()
                                            .firstOrNull()
                                        if (expectedMove != null) {
                                            events.applyTutorialMove(expectedMove, gameState)
                                        }
                                    },
                                ),
                                tutorialViewModel = tutorialViewModel,
                                updateGameState = editBoard::setGame,
                            )
                        }
                    },
                    content = {
                        EditControls(
                            // Los controles del editor deben seguir la orientación del
                            // DISPOSITIVO (isLandscape), no la del tablero del Sidebar
                            // (boardOrientation). El tablero sí sigue editBoardOrientation
                            // (definido en CreateBoardState arriba), que refleja la
                            // orientación seleccionada en el Sidebar.
                            isLandscapeScreen = isLandscape,
                            EditColorState(
                                playerSide = playerSide,
                                editTurn = editTurn,
                                editColor = editColor,
                            ),
                            EditActionState(
                                pieceCounts = pieceCounts,
                                isValidDistribution = distributionState.isValid,
                                isCompletedDistribution = distributionState.isCompleted,
                            ),
                            editBoard = editBoard,
                        )
                    },
                    giftOverlay = { giftModifier ->
                        SpecialEventOverlay(
                            manager = specialEventManager,
                            modifier = giftModifier,
                        )
                    },
                    turnIndicator = {},
                    clockState = clockState,
                    preMovesEnabled = preMovesEnabled,
                )
            }

            // ── TurnIndicator + FiftyMoveClaimBadge: overlays TopEnd ───────────
            // Se muestran fuera de modo edición y tutorial. Al ser hijos de este Box,
            // se renderizan sobre el tablero sin afectar su tamaño ni layout.
            // El padding superior es fijo: la distancia al borde visual superior
            // (bajo la top bar, ya descontada por innerPadding) es la misma en
            // cualquier disposición.
            if (!isEditing && !isTutorialActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (canClaimDraw) {
                        FiftyMoveClaimBadge(onClick = onClaimFiftyMoveDraw)
                    }
                    if (showTurnIndicator) {
                        // key(Unit) ancla la identidad de composición del TurnIndicator,
                        // evitando que cambios en el slot table recreen el
                        // InfiniteTransition y congelen la dirección de rotación.
                        key(Unit) {
                            TurnIndicator(
                                state = turnState,
                                currentTurn = gameState.currentTurn,
                                logoVisible = transitionDone,
                                onCirclePositioned = { centre ->
                                    indicatorCentreWindow = centre
                                },
                                indicatorEvents = remember(onTouchIndicator) {
                                    object : IndicatorEvents {
                                        override fun onTouch() = onTouchIndicator()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── BottomGameBar: overlay flotante sobre el tablero ──────────────
            // Se muestra en cualquier orientación, fuera de modo edición y tutorial.
            // Al ser el último hijo de este Box, se renderiza sobre el tablero
            // sin afectar su tamaño ni su layout.
            if (!isEditing && !isTutorialActive) {
                BottomGameBar(
                    history = history,
                    moveIndex = moveIndex,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onMoveToCurrent = onMoveToCurrent,
                    onMoveClick = onMoveToIndex,
                    modifier = Modifier.fillMaxSize(),
                    isLandscape = isLandscape,
                    navigationEnabled = navigationEnabled,
                    onHistoryOpenChange = { isHistoryPanelOpen = it },
                    onFabExpandedChange = { isFabExpanded = it },
                    onlineContent = onlineContent?.let { { it() } },
                )
            }
        }
    }

    // ── Logo transition overlay ──────────────────────────────────────────────
    // The overlay Box captures its own centre in window coordinates — the same
    // space as positionInWindow() on the TurnIndicator. The delta between the two
    // is exact regardless of status bar, navigation bar, or insets.
    // Size is animated via graphicsLayer scale (not Modifier.size) so layout is
    // stable and the scale pivot is always the image centre.
    if (!transitionDone) {
        val scale = targetLogoSizePx / splashLogoSizePx +
                (1f - targetLogoSizePx / splashLogoSizePx) * (1f - logoProgress.value)

        val oc = overlayCentreWindow
        val ic = indicatorCentreWindow
        val deltaX = if (oc != null && ic != null) (ic.x - oc.x) * logoProgress.value else 0f
        val deltaY = if (oc != null && ic != null) (ic.y - oc.y) * logoProgress.value else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    if (overlayCentreWindow == null) {
                        val pos = coords.positionInWindow()
                        overlayCentreWindow = Offset(
                            x = pos.x + coords.size.width / 2f,
                            y = pos.y + coords.size.height / 2f,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            TaratiLogo(
                size = splashLogoSizeDp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = deltaX
                        translationY = deltaY
                        alpha = logoAlpha.value
                    },
            )
        }
    }
}

private fun createBoardRenderEvents(
    selectViewModel: IBoardSelectionViewModel,
    animationViewModel: IBoardAnimationViewModel,
    geometryViewModel: IBoardGeometryViewModel,
): BoardRenderEvents = object :
    BoardRenderEvents {
    override fun onReset() {
        selectViewModel.resetSelection()
        animationViewModel.reset()
    }

    override fun onBoardSizeChange(size: Size) {
        geometryViewModel.updateBoardSize(size)
        animationViewModel.updateBoardSize(size)
    }

    override fun onUpdateBoardOrientation(orientation: BoardOrientation) {
        geometryViewModel.updateBoardOrientation(orientation)
        animationViewModel.updateBoardOrientation(orientation)
    }

    override fun onSyncState(gameState: GameState) =
        animationViewModel.syncState(gameState)
}

private fun createTapEvents(
    selectViewModel: IBoardSelectionViewModel,
    events: BoardEvents,
): TapEvents = object :
    TapEvents {
    override fun onSelected(from: Vertex, valid: List<Vertex>) {
        selectViewModel.updateSelectedVertex(from)
        selectViewModel.updateValidAdjacentVertexes(valid)
    }

    override fun onMove(move: Move) {
        events.onMove(move)
        selectViewModel.resetSelection()
    }

    override fun onInvalid(from: Vertex, valid: List<Vertex>) {
        selectViewModel.updateSelectedVertex(from)
        selectViewModel.updateValidAdjacentVertexes(valid)
    }

    override fun onEditPieceRequested(from: Vertex) = events.onEditPiece(from)

    override fun onCancel() = selectViewModel.resetSelection()

    // ── Pre-movimiento ─────────────────────────────────────────────────────
    //
    // Estos tres callbacks NO tocan `selectedVertex` / `validAdjacentVertexes`:
    // esos canales están reservados para el flujo normal (turno humano).
    // El estado de pre-move vive en campos separados del ViewModel.

    override fun onPreMoveSelected(from: Vertex, valid: List<Vertex>) {
        selectViewModel.updatePreMoveFrom(from)
        selectViewModel.updatePreMoveValidTargets(valid)
        // Un nuevo "from" reemplaza cualquier pre-move confirmado previo
        // del usuario — el gesto de pre-seleccionar una pieza distinta
        // es semánticamente un "empezar de nuevo".
        selectViewModel.setPendingPreMove(null)
    }

    override fun onPreMoveSet(move: Move) {
        selectViewModel.setPendingPreMove(move)
        // La fase de pre-selección terminó: limpiamos los "hints" visuales
        // (targets). El paso 7 dibujará el pre-move vía pendingPreMove.
        selectViewModel.updatePreMoveFrom(null)
        selectViewModel.updatePreMoveValidTargets(emptyList())
    }

    override fun onPreMoveCancel() = selectViewModel.resetPreMove()
}
