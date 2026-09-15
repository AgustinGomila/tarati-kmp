package com.agustin.tarati.ui.components.game.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.game.board.BoardOrientation
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.board.VisualPositionCache
import com.agustin.tarati.core.domain.game.board.buildPositionCache
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.services.sound.ISoundService
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationType
import com.agustin.tarati.ui.components.game.highlights.HighlightAnimation
import com.agustin.tarati.ui.components.game.highlights.createCaptureDynamicHighlight
import com.agustin.tarati.ui.components.game.highlights.createCaptureHighlight
import com.agustin.tarati.ui.components.game.highlights.createForceArcDynamicHighlight
import com.agustin.tarati.ui.components.game.highlights.createForceArcImpactHighlight
import com.agustin.tarati.ui.components.game.highlights.createMoveDynamicHighlight
import com.agustin.tarati.ui.components.game.highlights.createRegionHighlight
import com.agustin.tarati.ui.components.game.highlights.createUpgradeHighlight
import com.agustin.tarati.ui.components.game.highlights.createValidMovesHighlights
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Gestiona el pipeline de animaciones del tablero: movimiento de piezas,
 * capturas, promociones y secuencias de highlights.
 *
 * ## KMP
 * - SimpleDateFormat + Date() → Clock.System.now().toString() (ISO 8601 nativo)
 * - Thread.currentThread().name → eliminado de logs (no disponible en KMP common)
 * - Clock.System.currentTimeMillis() → Clock.System.now().toEpochMilliseconds()
 */
class BoardAnimationViewModel(
    override val soundService: ISoundService,
) : ViewModel(),
    IBoardAnimationViewModel {

    private val logger = getLogger("BoardAnimationViewModel")

    private val tiltMap = TiltStateMap()

    private val _boardOrientation = MutableStateFlow(BoardOrientation.PORTRAIT_WHITE)
    private val _boardSize = MutableStateFlow(Size.Zero)
    override val boardSize: StateFlow<Size> = _boardSize.asStateFlow()
    private var _positionCache: VisualPositionCache =
        buildPositionCache(Size.Zero, BoardOrientation.PORTRAIT_WHITE)

    override fun updateBoardSize(size: Size) {
        _boardSize.update { size }
        _positionCache = buildPositionCache(size, _boardOrientation.value)
    }

    override fun updateBoardOrientation(orientation: BoardOrientation) {
        _boardOrientation.update { orientation }
        _positionCache = buildPositionCache(_boardSize.value, orientation)
    }

    private val moveChannel = Channel<MoveRequest>(Channel.UNLIMITED)

    private val _pendingMoveCount = MutableStateFlow(0)
    override val pendingMoveCount: StateFlow<Int> = _pendingMoveCount.asStateFlow()

    private val moveDuration = 100L
    private val captureDuration = 70L
    private val upgradeDuration = 70L
    private val animationSteps: Int = 6

    private val _gameOverReady = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val gameOverReady: SharedFlow<Unit> = _gameOverReady.asSharedFlow()

    override fun notifyGameOver() {
        viewModelScope.launch {
            pendingMoveCount.first { it == 0 }
            _gameOverReady.emit(Unit)
        }
    }

    init {
        viewModelScope.launch {
            for (req in moveChannel) {
                logger.debug(getLogMessage("MOVE-START", mapOf("from" to req.move.from, "to" to req.move.to)))
                try {
                    animateMoveSequence(req.move, req.oldGameState, req.newGameState, req.isGameOver)
                    logger.debug(getLogMessage("MOVE-FINISH", mapOf("from" to req.move.from, "to" to req.move.to)))
                } catch (_: CancellationException) {
                    logger.warn(getLogMessage("MOVE-CANCEL", mapOf("from" to req.move.from, "to" to req.move.to)))
                } catch (t: Throwable) {
                    logger.error(getLogMessage("MOVE-ERROR", mapOf("err" to (t.message ?: t::class.simpleName))))
                } finally {
                    _pendingMoveCount.update { maxOf(0, it - 1) }
                }
            }
        }
    }

    private val stateMutex = Mutex()

    private val _visualState = MutableStateFlow(VisualGameState())
    override val visualState: StateFlow<VisualGameState> = _visualState.asStateFlow()

    private suspend fun setAnimatedPieces(newMap: Map<Vertex, AnimatedCob>) {
        stateMutex.withLock {
            _animatedPiecesMap.clear()
            _animatedPiecesMap.putAll(newMap)
            publishAnimatedPieces()
        }
    }

    private suspend fun putAnimatedPiece(vertex: Vertex, animatedCob: AnimatedCob) {
        stateMutex.withLock {
            _animatedPiecesMap[vertex] = animatedCob
            publishAnimatedPieces()
        }
    }

    private suspend fun updateAnimatedPiece(vertex: Vertex, transform: (AnimatedCob?) -> AnimatedCob) {
        stateMutex.withLock {
            _animatedPiecesMap[vertex] = transform(_animatedPiecesMap[vertex])
            publishAnimatedPieces()
        }
    }

    private suspend fun removeAnimatedPiece(vertex: Vertex) {
        stateMutex.withLock {
            _animatedPiecesMap.remove(vertex)
            publishAnimatedPieces()
        }
    }

    private suspend fun updateVisualState(transform: (VisualGameState) -> VisualGameState) {
        stateMutex.withLock {
            _visualState.update { transform(_visualState.value) }
        }
    }

    private val pendingVisualUpdates = mutableMapOf<Vertex, Cob>()

    private suspend fun applyPendingForVertexIfAny(vertex: Vertex) {
        stateMutex.withLock {
            val pendingCob = pendingVisualUpdates.remove(vertex) ?: return@withLock
            _visualState.update { curr ->
                curr.copy(cobs = curr.cobs + (vertex to pendingCob))
            }
            logger.info(getLogMessage("APPLIED-PENDING", mapOf("name" to vertex)))
        }
    }

    private val _animateEffects = MutableStateFlow(false)

    override fun updateAnimateEffects(animate: Boolean) {
        _animateEffects.update { animate }
    }

    private val _conversionAnimationStyle = MutableStateFlow(ConversionAnimationStyle.SURPRISE)

    override fun updateConversionAnimationStyle(style: ConversionAnimationStyle) {
        _conversionAnimationStyle.update { style }
    }

    override fun processMove(
        move: Move,
        oldGameState: GameState,
        newGameState: GameState,
        isGameOver: Boolean,
    ) {
        val req = MoveRequest(move, oldGameState, newGameState, isGameOver)
        _pendingMoveCount.update { it + 1 }
        viewModelScope.launch {
            moveChannel.send(req)
            logger.debug(getLogMessage("MOVE-ENQUEUE", mapOf("from" to move.from, "to" to move.to)))
        }
    }

    // ── Arrastrar y soltar: offset de inicio del próximo movimiento ──────────────
    // Se guarda keyed por el Move para que la cola FIFO no lo aplique a otro
    // movimiento (p. ej. una jugada de IA encolada antes). Set y consumo ocurren
    // ambos en viewModelScope (Main) → sin necesidad de sincronización.
    private var pendingStartOverride: Pair<Move, Offset>? = null

    override fun setNextMoveStartOffset(move: Move, startOffset: Offset) {
        pendingStartOverride = move to startOffset
    }

    /** Devuelve y consume el override si coincide con [move]; si no coincide, lo deja intacto. */
    private fun consumeStartOverride(move: Move): Offset? {
        val pending = pendingStartOverride ?: return null
        if (pending.first != move) return null
        pendingStartOverride = null
        return pending.second
    }

    private fun getLogMessage(
        tag: String,
        extra: Map<String, Any?> = emptyMap(),
    ): String {
        val base = mapOf("ts" to nowIso(), "tag" to tag)
        return (base + extra).toString()
    }

    private suspend fun animateMoveSequence(
        move: Move,
        oldGameState: GameState,
        newGameState: GameState,
        isGameOver: Boolean,
    ) {
        when {
            move.isCaptureMove(oldGameState, newGameState) -> soundService.playCaptureSound()
            move.isUpgradeMove(oldGameState, newGameState) -> soundService.playUpgradeSound()
            else -> soundService.playMoveSound()
        }

        val effectiveStyle = when (_conversionAnimationStyle.value) {
            ConversionAnimationStyle.SURPRISE ->
                listOf(ConversionAnimationStyle.TRANSFORMATION, ConversionAnimationStyle.FLIP).random()

            else -> _conversionAnimationStyle.value
        }

        val captures = oldGameState.detectCaptures(move, newGameState)
        val upgrades = oldGameState.detectUpgrades(newGameState)

        animateMovement(move, captures, newGameState, effectiveStyle)
        if (captures.isNotEmpty()) animateDetectedCaptures(captures, effectiveStyle)
        if (upgrades.isNotEmpty()) animateDetectedUpgrades(upgrades)

        if (isGameOver) {
            soundService.playGameOverSound()
            _gameOverReady.emit(Unit)
        }
    }

    private suspend fun animateMovement(
        move: Move,
        conversions: List<Pair<Vertex, Cob>>,
        newGameState: GameState,
        effectiveStyle: ConversionAnimationStyle,
    ) {
        val cob = newGameState.cobs[move.to] ?: return

        val fromTiltDeg = tiltMap.get(move.from)
        val toTiltDeg = TiltStateMap.randomTilt()

        // Arrastrar-y-soltar: si hay un offset registrado para este movimiento, la pieza
        // arranca desde el punto de soltado en lugar del vértice de origen.
        val startOverride = consumeStartOverride(move)

        val animatedCob = AnimatedCob(
            vertex = move.to,
            cob = cob,
            currentPos = move.from,
            targetPos = move.to,
            animationProgress = 0f,
            fromTiltDeg = fromTiltDeg,
            toTiltDeg = toTiltDeg,
            startOverride = startOverride,
        )

        updateVisualState { curr ->
            val m = curr.cobs.toMutableMap()
            m.remove(move.from)
            m.remove(move.to)
            curr.copy(cobs = m)
        }

        putAnimatedPiece(move.to, animatedCob)
        val conversionVertex = conversions.map { it.first }.toList()

        val duration = moveDuration
        val steps = animationSteps
        val stepDelay = duration / steps

        val fromPos = startOverride ?: _positionCache[move.from]
        val toPos = _positionCache[move.to]

        repeat(steps) { step ->
            val progress = (step + 1) / steps.toFloat()
            updateAnimatedPiece(move.to) { prev -> (prev ?: animatedCob).copy(animationProgress = progress) }

            if (_animateEffects.value) {
                val currentX = fromPos.x + (toPos.x - fromPos.x) * progress
                val currentY = fromPos.y + (toPos.y - fromPos.y) * progress
                animateMoveHighlights(
                    currentPos = Offset(currentX, currentY),
                    targetPos = toPos,
                    moveTo = move.to,
                    conversionVertex = conversionVertex,
                    effectiveStyle = effectiveStyle,
                )
            }
            delay(stepDelay.milliseconds)
            logFrame("movement-frame", extra = mapOf("moveTo" to move.to, "step" to step))
        }

        if (_animateEffects.value) {
            animateMovePostEffects(move, cob, newGameState)
        }

        tiltMap.transfer(animatedCob.currentPos, animatedCob.targetPos, animatedCob.toTiltDeg)

        updateVisualState { current ->
            val m = current.cobs.toMutableMap()
            m.remove(animatedCob.currentPos)
            m[animatedCob.targetPos] = animatedCob.cob
            val newTilts = current.tiltAngles.toMutableMap()
            newTilts.remove(animatedCob.currentPos)
            newTilts[animatedCob.targetPos] = animatedCob.toTiltDeg
            current.copy(cobs = m, tiltAngles = newTilts)
        }

        removeAnimatedPiece(animatedCob.vertex)
        applyPendingForVertexIfAny(animatedCob.targetPos)
        applyPendingForVertexIfAny(animatedCob.currentPos)
    }

    private fun animateMoveHighlights(
        currentPos: Offset,
        targetPos: Offset,
        moveTo: Vertex,
        conversionVertex: List<Vertex>,
        effectiveStyle: ConversionAnimationStyle,
    ) {
        val highlights = mutableListOf<HighlightAnimation>()

        highlights.addAll(
            createMoveDynamicHighlight(
                fromPos = currentPos,
                toPos = targetPos,
                toVertex = moveTo,
            ),
        )

        conversionVertex.forEach { vertex ->
            val capturePos = _positionCache[vertex]
            highlights.addAll(
                when (effectiveStyle) {
                    ConversionAnimationStyle.TRANSFORMATION -> createForceArcDynamicHighlight(currentPos, capturePos)
                    ConversionAnimationStyle.FLIP -> createCaptureDynamicHighlight(currentPos, capturePos)
                    ConversionAnimationStyle.SURPRISE -> createCaptureDynamicHighlight(currentPos, capturePos)
                },
            )
        }

        animateParallel(highlights, "move-highlights")
    }

    private fun animateMovePostEffects(
        move: Move,
        cob: Cob,
        newGameState: GameState,
    ) {
        val highlights = createValidMovesHighlights(newGameState.getValidVertex(move.to, cob)).toMutableList()
        newGameState.findClosedRegions(move.to, cob.color).let {
            it.forEach { region -> highlights.add(createRegionHighlight(region)) }
        }
        animateParallel(highlights, "post-move")
    }

    private suspend fun animateDetectedCaptures(
        captures: List<Pair<Vertex, Cob>>,
        effectiveStyle: ConversionAnimationStyle,
    ) {
        if (captures.isEmpty()) return

        val duration = captureDuration
        val steps = animationSteps
        val stepDelay = duration / steps

        captures.forEach { (vertex, newCob) ->
            val currentTilt = tiltMap.get(vertex)

            val animatedCob = AnimatedCob(
                vertex = vertex,
                cob = newCob,
                currentPos = vertex,
                targetPos = vertex,
                targetColor = newCob.color,
                conversionProgress = 0f,
                isConverting = true,
                fromTiltDeg = currentTilt,
                toTiltDeg = currentTilt,
                conversionType = when (effectiveStyle) {
                    ConversionAnimationStyle.TRANSFORMATION -> listOf(
                        ConversionAnimationType.FROM_CENTER,
                        ConversionAnimationType.FROM_BORDER,
                    ).random()

                    ConversionAnimationStyle.FLIP -> ConversionAnimationType.FLIP
                    ConversionAnimationStyle.SURPRISE -> ConversionAnimationType.FROM_CENTER
                },
            )

            putAnimatedPiece(vertex, animatedCob)

            repeat(steps) { step ->
                val progress = (step + 1) / steps.toFloat()
                updateAnimatedPiece(vertex) { prev ->
                    (prev ?: animatedCob).copy(conversionProgress = progress)
                }

                if (_animateEffects.value && step == steps / 2) {
                    val captureEffects = when (effectiveStyle) {
                        ConversionAnimationStyle.TRANSFORMATION -> {
                            val bPos = _positionCache[vertex]
                            createForceArcImpactHighlight(bPos)
                        }

                        ConversionAnimationStyle.FLIP -> createCaptureHighlight(vertex)
                        ConversionAnimationStyle.SURPRISE -> createCaptureHighlight(vertex)
                    }
                    animateParallel(captureEffects, "move-capture")
                }
                delay(stepDelay.milliseconds)
                logFrame("capture-frame", extra = mapOf("moveTo" to vertex, "step" to step))
            }

            updateVisualState { current ->
                val m = current.cobs.toMutableMap()
                m[vertex] = newCob
                current.copy(cobs = m)
            }

            removeAnimatedPiece(vertex)
            applyPendingForVertexIfAny(vertex)
        }
    }

    private suspend fun animateDetectedUpgrades(upgrades: List<Pair<Vertex, Cob>>) {
        if (upgrades.isEmpty()) return

        val duration = upgradeDuration
        val steps = animationSteps
        val stepDelay = duration / steps

        upgrades.forEach { (vertex, newCob) ->
            val currentlyAnimating = _animatedPiecesMap.containsKey(vertex)
            if (!currentlyAnimating) {
                val currentTilt = tiltMap.get(vertex)
                val animatedCob = AnimatedCob(
                    vertex = vertex,
                    cob = newCob,
                    currentPos = vertex,
                    targetPos = vertex,
                    upgradeProgress = 0f,
                    fromTiltDeg = currentTilt,
                    toTiltDeg = currentTilt,
                    conversionType = ConversionAnimationType.entries.toTypedArray().random(),
                )

                putAnimatedPiece(vertex, animatedCob)

                repeat(steps) { step ->
                    val progress = (step + 1) / steps.toFloat()
                    updateAnimatedPiece(vertex) { prev ->
                        (prev ?: animatedCob).copy(upgradeProgress = progress)
                    }

                    if (_animateEffects.value && step == steps / 3) {
                        animateParallel(createUpgradeHighlight(vertex), "move-upgrade")
                    }
                    delay(stepDelay.milliseconds)
                    logFrame("upgrade-frame", extra = mapOf("moveTo" to vertex, "step" to step))
                }

                updateVisualState { current ->
                    val m = current.cobs.toMutableMap()
                    m[vertex] = newCob
                    current.copy(cobs = m)
                }

                removeAnimatedPiece(vertex)
                applyPendingForVertexIfAny(vertex)
            } else {
                updateVisualState { current ->
                    val m = current.cobs.toMutableMap()
                    m[vertex] = newCob
                    current.copy(cobs = m)
                }
            }
        }
    }

    private val _animatedPiecesMap = HashMap<Vertex, AnimatedCob>()
    private val _animatedPieces = MutableStateFlow<Map<Vertex, AnimatedCob>>(emptyMap())
    override val animatedPieces: StateFlow<Map<Vertex, AnimatedCob>> = _animatedPieces.asStateFlow()

    private fun publishAnimatedPieces() {
        _animatedPieces.value = HashMap(_animatedPiecesMap)
    }

    private var previousVisualState: VisualGameState? = null

    override fun syncState(gameState: GameState) {
        viewModelScope.launch {
            tiltMap.initMissing(gameState.cobs.keys)
            if (_animatedPieces.value.isEmpty()) {
                tiltMap.retainAll(gameState.cobs.keys)
            }

            if (_animatedPieces.value.isEmpty()) {
                updateVisualState {
                    VisualGameState(
                        cobs = gameState.cobs.toMap(),
                        currentTurn = gameState.currentTurn,
                        tiltAngles = tiltMap.snapshot(),
                    )
                }
                return@launch
            }

            val safeApply = mutableMapOf<Vertex, Cob>()
            val animatedKeys = _animatedPieces.value.keys.toSet()
            val animatedTargets = _animatedPieces.value.values.map { it.targetPos }.toSet()
            val animatedSources = _animatedPieces.value.values.map { it.currentPos }.toSet()

            for ((vertex, cob) in gameState.cobs) {
                val involved = vertex in animatedKeys || vertex in animatedTargets || vertex in animatedSources
                if (!involved) {
                    safeApply[vertex] = cob
                } else {
                    stateMutex.withLock {
                        pendingVisualUpdates[vertex] = cob
                    }
                    logger.info(getLogMessage("PENDING-STORE", mapOf("name" to vertex)))
                }
            }

            if (safeApply.isNotEmpty()) {
                updateVisualState { curr ->
                    val m = curr.cobs.toMutableMap()
                    m.putAll(safeApply)
                    curr.copy(cobs = m, currentTurn = gameState.currentTurn)
                }
            }
        }
    }

    private val _currentHighlights = MutableStateFlow<List<HighlightAnimation>>(emptyList())
    override val currentHighlights: StateFlow<List<HighlightAnimation>> = _currentHighlights.asStateFlow()

    internal fun updateCurrentHighlights(newHighlights: List<HighlightAnimation>) {
        _currentHighlights.update { newHighlights }
    }

    private val highlightQueue = HighlightQueueManager(
        scope = viewModelScope,
        animateEffects = _animateEffects,
        onHighlightsChanged = ::updateCurrentHighlights,
    )

    override fun stopHighlights(): Unit = highlightQueue.stopHighlights()

    override fun reset() {
        forceSync()
        viewModelScope.launch {
            updateVisualState { VisualGameState() }
            previousVisualState = null
        }
    }

    override fun forceSync() {
        pendingStartOverride = null
        clearQueue()
        stopHighlights()
        viewModelScope.launch {
            setAnimatedPieces(emptyMap())
            updateVisualState { VisualGameState() }
        }
    }

    override fun animateParallel(
        highlights: List<HighlightAnimation>,
        source: String,
    ): Unit = highlightQueue.animateParallel(highlights, source, _currentHighlights.value)

    override fun animateSerie(
        sequences: List<List<HighlightAnimation>>,
        source: String,
        mode: SequenceLoadMode,
    ): Unit = highlightQueue.animateSerie(sequences, source, mode)

    override fun clearQueue(): Unit = highlightQueue.clearQueue()

    override fun loadTutorialStep(
        sequences: List<List<HighlightAnimation>>,
        source: String,
    ): Unit = highlightQueue.loadTutorialStep(sequences, source)

    override fun animateGameOver(matchState: MatchState): Unit = highlightQueue.animateGameOver(matchState)

    // ── Logging ───────────────────────────────────────────────────────────────
    private fun nowIso(): String = Clock.System.now().toString()

    private fun logFrame(
        tag: String = "anim",
        extra: Map<String, Any?> = emptyMap(),
        dumpStates: Boolean = false,
    ) {
        val small = mutableMapOf<String, Any?>("ts" to nowIso(), "tag" to tag)
        small.putAll(extra)
        if (dumpStates) {
            small["visualState"] = _visualState.value.cobs.mapValues { it.value.toString() }
            small["animatedPieces"] = _animatedPieces.value.mapValues { it.value.copy(cob = it.value.cob) }
        }
        logger.debug(small.toString())
    }
}

// ── Helpers de duración ───────────────────────────────────────────────────────

fun getAnimationDuration(highlight: HighlightAnimation): Long =
    when (highlight) {
        is HighlightAnimation.Vertex -> highlight.highlight.duration
        is HighlightAnimation.Arrow -> highlight.highlight.duration
        is HighlightAnimation.FireballEdge -> highlight.highlight.duration
        is HighlightAnimation.ElectricEdge -> highlight.highlight.duration
        is HighlightAnimation.Region -> highlight.highlight.duration
        is HighlightAnimation.DynamicFireballEdge -> highlight.highlight.duration
        is HighlightAnimation.DynamicElectricEdge -> highlight.highlight.duration
        is HighlightAnimation.DynamicForceArc -> highlight.highlight.duration
        is HighlightAnimation.DynamicForceArcImpact -> highlight.highlight.duration
        is HighlightAnimation.ForceArcEdge -> highlight.highlight.duration
        is HighlightAnimation.Pause -> highlight.duration
    }