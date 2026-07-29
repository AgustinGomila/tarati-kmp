package com.agustin.tarati.features.game6

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.MpNotation
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.rules.MpMatch
import com.agustin.tarati.core.domain.game6.rules.MpRules
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.network.models.MpGameDetailDto
import com.agustin.tarati.network.models.MpPlayerDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del visor de replay de una partida MP terminada. La posición visualizada es
 * [MpGameDetailUiState.state]; [moveIndex] es el cursor (−1 = posición inicial; `history.size − 1` =
 * última jugada). [lastMove]/[converted] solo se pueblan al **avanzar una jugada** (animación de
 * deslizamiento/flip); en saltos y retrocesos van vacíos → el tablero hace snap.
 */
@Immutable
data class MpGameDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val players: List<MpPlayerDto> = emptyList(),
    val result: MpResult? = null,
    val history: List<PlayerMove> = emptyList(),
    val moveIndex: Int = -1,
    val state: MpGameState? = null,
    val lastMove: MpMove? = null,
    val converted: Map<Vertex, PlayerColor> = emptyMap(),
    /** Epoch millis de fin de la partida (para la fecha en la tarjeta de información). */
    val endedAtMs: Long = 0,
)

@Stable
interface IMpGameDetailViewModel {
    val state: StateFlow<MpGameDetailUiState>

    /** Carga la partida [gameId] y reconstruye los estados jugada a jugada. */
    fun loadGame(gameId: String)

    /** Salta a la posición **tras** la jugada [index] (−1 = inicial); snap sin animación. */
    fun moveToIndex(index: Int)

    /** Retrocede una jugada (snap). */
    fun prev()

    /** Avanza una jugada (con animación de la jugada aplicada). */
    fun next()

    /** Salta a la posición inicial. */
    fun first()

    /** Salta a la última jugada. */
    fun last()
}

/**
 * ViewModel del visor de replay de una partida MP terminada (Tarati Six). Clase plana con scope
 * inyectable (igual que [MpLeaderboardViewModel]/[MpLobbyViewModel], no un androidx `ViewModel`) →
 * testeable sin `Dispatchers.setMain`. `fetchDetail` inyectado (default no-op) para mockearlo.
 *
 * Reconstruye el replay de forma determinista y **client-side**: [MpSetup.initialState] como base +
 * [MpNotation.parseHistory] → reaplicar cada jugada con [MpMatch] (mismo motor que el juego online).
 * Los retiros por timeout/desconexión no viajan en el historial serializado, así que no se reproducen
 * a mitad del replay; el [MpGameDetailUiState.result] final (del DTO) siempre es fiel.
 */
@Stable
class MpGameDetailViewModel(
    private val getToken: suspend () -> String?,
    private val fetchDetail: suspend (token: String, gameId: String) -> Result<MpGameDetailDto> =
        { _, _ -> Result.failure(IllegalStateException("no fetcher")) },
    scope: CoroutineScope? = null,
) : IMpGameDetailViewModel {
    private val logger = getLogger("MpGameDetailViewModel")
    private val _scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(MpGameDetailUiState())
    override val state: StateFlow<MpGameDetailUiState> = _state.asStateFlow()

    /** Snapshots reconstruidos: `snapshots[0]` = inicial; `snapshots[i+1]` = tras la jugada `i`. */
    private var snapshots: List<MpGameState> = emptyList()

    /** Dueño previo de las piezas que convierte la jugada `i` (para animar al avanzar). */
    private var convertedPerPly: List<Map<Vertex, PlayerColor>> = emptyList()

    override fun loadGame(gameId: String) {
        _scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = getToken() ?: run {
                _state.update { it.copy(isLoading = false, error = "no_session") }
                return@launch
            }
            fetchDetail(token, gameId)
                .onSuccess { detail -> reconstruct(detail) }
                .onFailure { e ->
                    logger.debug("getMpGameDetail failed: ${e.message}")
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Reaplica el historial serializado para producir los snapshots y muestra la posición final. */
    private fun reconstruct(detail: MpGameDetailDto) {
        val initial = MpSetup.initialState(detail.playerCount)
        val moves = runCatching { MpNotation.parseHistory(detail.history) }.getOrElse { emptyList() }

        val match = MpMatch(initial)
        val states = ArrayList<MpGameState>(moves.size + 1).apply { add(initial) }
        val conversions = ArrayList<Map<Vertex, PlayerColor>>(moves.size)
        for (pm in moves) {
            val before = match.state
            if (before.isGameOver) break // defensivo: el motor cerró antes del historial completo
            val converted = MpRules.captureTargets(before.pieces, pm.move)
                .associateWith { before.pieces.getValue(it).owner }
            conversions += converted
            states += match.applyMove(pm.move)
        }

        snapshots = states
        convertedPerPly = conversions
        val lastIndex = states.size - 2 // moveIndex de la última jugada (−1 si no hubo jugadas)

        _state.update {
            it.copy(
                isLoading = false,
                error = null,
                players = detail.players,
                result = detail.result,
                history = moves.take(states.size - 1),
                moveIndex = lastIndex,
                state = states.last(),
                lastMove = null,
                converted = emptyMap(),
                endedAtMs = detail.endedAtMs,
            )
        }
    }

    override fun moveToIndex(index: Int): Unit = snapTo(index)

    override fun prev(): Unit = snapTo(_state.value.moveIndex - 1)

    override fun first(): Unit = snapTo(-1)

    override fun last(): Unit = snapTo(snapshots.size - 2)

    override fun next() {
        val target = _state.value.moveIndex + 1
        if (snapshots.isEmpty() || target !in 0..(snapshots.size - 2)) return
        _state.update {
            it.copy(
                moveIndex = target,
                state = snapshots[target + 1],
                lastMove = it.history.getOrNull(target)?.move,
                converted = convertedPerPly.getOrElse(target) { emptyMap() },
            )
        }
    }

    /** Salta a [index] sin animación (snap): limpia último movimiento y conversiones. */
    private fun snapTo(index: Int) {
        if (snapshots.isEmpty()) return
        val clamped = index.coerceIn(-1, snapshots.size - 2)
        if (clamped == _state.value.moveIndex) return
        _state.update {
            it.copy(
                moveIndex = clamped,
                state = snapshots[clamped + 1],
                lastMove = null,
                converted = emptyMap(),
            )
        }
    }
}
