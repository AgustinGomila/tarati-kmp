package com.agustin.tarati.services.achievements

import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.board.GameBoard.deadVertices
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameEndReason
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.MatchState
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.ui.theme.SeasonalThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lógica de detección de logros compartida entre todas las plataformas.
 *
 * Transforma eventos de juego ([onMoveApplied], [onGameOver], [onTutorialCompleted])
 * en llamadas a [onUnlock] / [onProgress] — implementadas por la subclase con
 * el mecanismo propio de la plataforma (Google Play Games + servidor en Android,
 * solo servidor en Desktop/Web).
 *
 * ## Contadores incrementales
 * [incrementCaptures], [incrementPromotions], [incrementWins] e [incrementGames]
 * incrementan y persisten los contadores. Las subclases sobreescriben estos métodos
 * para persistir en DataStore (Android) o in-memory (Desktop/Web inicializado desde
 * servidor vía [ServerAchievementsManager.syncFromServer]).
 *
 * ## Logros estacionales
 * Una victoria en [Difficulty.CHAMPION] en Halloween o Navidad activa [onChampionWin].
 * La base desbloquea el achievement correspondiente vía [onUnlock]. Android sobreescribe
 * este hook para también persistir el unlock de paleta en DataStore.
 */
abstract class BaseAchievementsManager(
    private val aiEngine: IAIEngine,
) : IAchievementsManager {

    // ── Estado de paletas desbloqueadas ───────────────────────────────────────

    protected val unlockedPalettes: MutableStateFlow<Set<AchievementId>> =
        MutableStateFlow<Set<AchievementId>>(emptySet())
    override val unlockedPaletteAchievements: StateFlow<Set<AchievementId>> =
        unlockedPalettes.asStateFlow()

    // ── Contadores in-memory — las subclases los inicializan ──────────────────

    protected var totalCaptures: Int = 0
    protected var totalPromotions: Int = 0
    protected var totalWins: Int = 0
    protected var totalGames: Int = 0

    // ── Hooks de entrega — implementados por la subclase ─────────────────────

    /** Entrega el unlock de un logro one-shot al canal de la plataforma. */
    protected abstract suspend fun onUnlock(achievementId: AchievementId)

    /**
     * Entrega el progreso de un logro incremental al canal de la plataforma.
     *
     * @param steps     Progreso actual (ya incrementado con este evento).
     * @param maxSteps  Máximo posible — la subclase no debe enviar más allá.
     */
    protected abstract suspend fun onProgress(achievementId: AchievementId, steps: Int, maxSteps: Int)

    // ── Persistencia de contadores — subclases sobreescriben ─────────────────

    protected open suspend fun incrementCaptures(amount: Int): Int {
        totalCaptures += amount
        return totalCaptures
    }

    protected open suspend fun incrementPromotions(): Int {
        totalPromotions += 1
        return totalPromotions
    }

    protected open suspend fun incrementWins(): Int {
        totalWins += 1
        return totalWins
    }

    protected open suspend fun incrementGames(): Int {
        totalGames += 1
        return totalGames
    }

    // ── Detección de logros ───────────────────────────────────────────────────

    override suspend fun onMoveApplied(move: Move, previousState: GameState, newState: GameState) {
        val (rokFlips, cobFlips) = move.countFlipsByType(previousState, newState)
        val totalFlips = rokFlips + cobFlips
        val isUpgrade = move.isUpgradeMove(previousState, newState)
        val isDeadCobPromotion = move.isPromotion() &&
                deadVertices[previousState.currentTurn]?.contains(move.from) == true

        if (totalFlips > 0) {
            onUnlock(AchievementId.FIRST_CAPTURE)
            val newTotal = incrementCaptures(totalFlips)
            onProgress(AchievementId.THE_FLIPPER, newTotal, maxSteps = 50)
        }

        if (isUpgrade) {
            onUnlock(AchievementId.FIRST_PROMOTION)
            val newTotal = incrementPromotions()
            onProgress(AchievementId.ROK_MASTER, newTotal, maxSteps = 25)
        }

        // Dead but Dangerous: una pieza muerta asciende y la victoria ocurre en ese mismo movimiento.
        if (isDeadCobPromotion && newState.isGameOver(aiEngine.positionHistory)) {
            val winner = newState.getMatchState(aiEngine.positionHistory).winner
            if (winner == previousState.currentTurn) {
                onUnlock(AchievementId.DEAD_BUT_DANGEROUS)
            }
        }
    }

    override suspend fun onGameOver(matchState: MatchState, playerSide: CobColor, difficulty: Difficulty?) {
        val humanWon = matchState.winner == playerSide

        if (humanWon) {
            onUnlock(AchievementId.FIRST_VICTORY)

            when (matchState.gameEndReason) {
                GameEndReason.MIT -> onUnlock(AchievementId.MIT)
                GameEndReason.STALEMIT -> onUnlock(AchievementId.STALEMIT)
                GameEndReason.TRIPLE -> onUnlock(AchievementId.ETERNAL_LOOP)
                else -> Unit
            }

            // Logros de dificultad solo aplican en partidas locales contra IA.
            // difficulty == null indica partida online.
            when (difficulty) {
                Difficulty.EASY -> onUnlock(AchievementId.APPRENTICE)
                Difficulty.MEDIUM -> onUnlock(AchievementId.STRATEGIST)
                Difficulty.HARD -> onUnlock(AchievementId.TACTICIAN)
                Difficulty.CHAMPION -> {
                    onUnlock(AchievementId.CHAMPION)
                    onChampionWin()
                }

                null -> Unit
            }

            val wins = incrementWins()
            onProgress(AchievementId.UNSTOPPABLE, wins, maxSteps = 10)
            onProgress(AchievementId.GRANDMASTER, wins, maxSteps = 50)
        }

        if (matchState.gameEndReason == GameEndReason.FIFTY_MOVES) {
            onUnlock(AchievementId.FIFTY_MOVE_RULE)
        }

        val games = incrementGames()
        onProgress(AchievementId.PLAY_10_GAMES, games, maxSteps = 10)
    }

    override suspend fun onTutorialCompleted() {
        onUnlock(AchievementId.WELCOME_TO_TARATI)
    }

    /** No-op por defecto. Subclases sobreescriben con la UI de la plataforma. */
    override fun showAchievementsUI(onNavigateToScreen: () -> Unit): Unit = onNavigateToScreen()

    /**
     * Llamado en cada victoria en [Difficulty.CHAMPION].
     *
     * La base desbloquea el logro estacional correspondiente al día actual.
     * Android sobreescribe para también persistir el unlock de paleta en DataStore.
     */
    protected open suspend fun onChampionWin() {
        when {
            SeasonalThemeManager.isHalloweenDay() -> {
                onUnlock(AchievementId.HALLOWEEN_THEME)
                unlockedPalettes.update { it + AchievementId.HALLOWEEN_THEME }
            }

            SeasonalThemeManager.isChristmasDay() -> {
                onUnlock(AchievementId.CHRISTMAS_THEME)
                unlockedPalettes.update { it + AchievementId.CHRISTMAS_THEME }
            }
        }
    }
}
