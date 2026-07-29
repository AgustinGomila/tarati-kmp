package com.agustin.tarati.services.achievements

import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.achievement.Achievement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Implementación de [IAchievementsRepository] para Android usando Google Play Games Services.
 *
 * ## Autenticación
 * Esta implementación asume que el usuario ya está autenticado con Google Play Games.
 * La autenticación debe manejarse en [MainActivity] o [TaratiApplication] antes de
 * usar este repositorio.
 *
 * ## IDs de Achievements
 * Los IDs definidos en [AchievementIds] deben coincidir exactamente con los IDs
 * configurados en Google Play Console.
 *
 * ## Sincronización
 * Los achievements se cargan automáticamente al crear la instancia y se sincronizan
 * cuando se llama a [sync]. Las actualizaciones locales se reflejan inmediatamente
 * en los flows, mientras que la sincronización con Google Play ocurre en background.
 *
 * @param gamesSignInClient Cliente de autenticación de Google Play Games
 * @param achievementsClient Cliente de achievements de Google Play Games
 */
class AchievementsRepositoryImpl(
    private val gamesSignInClient: GamesSignInClient,
    private val achievementsClient: AchievementsClient,
) : IAchievementsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logger = getLogger("AchievementsRepositoryImpl")

    // Estado interno: mapa de achievement ID → progreso
    private val _achievements = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val achievements: StateFlow<Map<String, Float>> = _achievements.asStateFlow()

    // Flows derivados para paletas específicas
    override val halloweenUnlocked: StateFlow<Boolean> =
        achievements
            .map { map -> map[AchievementIds.HALLOWEEN_PALETTE] == 1.0f }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override val christmasUnlocked: StateFlow<Boolean> =
        achievements
            .map { map -> map[AchievementIds.CHRISTMAS_PALETTE] == 1.0f }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override val auroraUnlocked: StateFlow<Boolean> =
        achievements
            .map { map -> map[AchievementIds.AURORA_PALETTE] == 1.0f }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override val emberUnlocked: StateFlow<Boolean> =
        achievements
            .map { map -> map[AchievementIds.EMBER_PALETTE] == 1.0f }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Cargar achievements al inicializar
        scope.launch {
            loadAchievements()
        }
    }

    /**
     * Carga todos los achievements del usuario desde Google Play Games.
     *
     * Este método verifica primero si el usuario está autenticado, luego
     * carga los achievements y actualiza el estado interno.
     */
    private suspend fun loadAchievements() {
        try {
            // Verificar autenticación
            val isAuthenticated = gamesSignInClient.isAuthenticated().await()
            if (!isAuthenticated.isAuthenticated) {
                // Usuario no autenticado - mantener mapa vacío
                return
            }

            // Cargar achievements desde Google Play
            val achievementBuffer = achievementsClient.load(false).await()

            val achievementMap = mutableMapOf<String, Float>()
            achievementBuffer.get()?.use { buffer ->
                buffer.forEach { achievement ->
                    val progress = calculateProgress(achievement)
                    achievementMap[achievement.achievementId] = progress
                }
            }

            // Actualizar estado
            _achievements.value = achievementMap

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log error pero no crashear - continuar con mapa vacío o actual
            logger.error("Error loading achievements", e)
        }
    }

    /**
     * Calcula el progreso de un achievement (0.0 a 1.0).
     */
    private fun calculateProgress(achievement: Achievement): Float {
        return when (achievement.state) {
            Achievement.STATE_UNLOCKED -> 1.0f
            Achievement.STATE_REVEALED -> {
                if (achievement.type == Achievement.TYPE_INCREMENTAL) {
                    val current = achievement.currentSteps
                    val total = achievement.totalSteps
                    if (total > 0) current.toFloat() / total.toFloat() else 0f
                } else {
                    0f
                }
            }

            else -> 0f
        }
    }

    override suspend fun unlockAchievement(achievementId: String) {
        try {
            // Desbloquear en Google Play Games
            achievementsClient.unlock(achievementId)

            // Actualizar estado local inmediatamente
            _achievements.value += (achievementId to 1.0f)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log error pero continuar
            logger.error("Error unlocking achievement $achievementId", e)
        }
    }

    override suspend fun updateProgress(achievementId: String, progress: Float) {
        try {
            // Convertir progreso (0.0-1.0) a steps
            // Asumiendo que todos los achievements incrementales tienen 100 steps
            val steps = (progress * 100).toInt()

            // Actualizar en Google Play Games
            achievementsClient.setSteps(achievementId, steps)

            // Actualizar estado local inmediatamente
            _achievements.value += (achievementId to progress)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log error pero continuar
            logger.error("Error updating progress for $achievementId", e)
        }
    }

    override suspend fun sync() {
        loadAchievements()
    }
}

/**
 * IDs de achievements configurados en Google Play Console.
 *
 * Estos valores coinciden exactamente con los IDs en:
 * `res/values/games-ids.xml` generado por Google Play Console.
 */
object AchievementIds {
    /**
     * Achievement: Halloween Theme
     * Se desbloquea ganando en dificultad Champion el 31 de octubre.
     */
    const val HALLOWEEN_PALETTE: String = "CgkIgobx5M4CEAIQEQ"

    /**
     * Achievement: Christmas Theme
     * Se desbloquea ganando en dificultad Champion el 25 de diciembre.
     */
    const val CHRISTMAS_PALETTE: String = "CgkIgobx5M4CEAIQEg"

    /**
     * Achievement: The First Light (Aurora Palette)
     * Criterio de desbloqueo definido por el juego.
     */
    const val AURORA_PALETTE: String = "CgkIgobx5M4CEAIQFg"

    /**
     * Achievement: The Dark Side (Ember Palette)
     * Criterio de desbloqueo definido por el juego.
     */
    const val EMBER_PALETTE: String = "CgkIgobx5M4CEAIQFw"

    // Otros achievements del juego
    const val WELCOME_TO_TARATI: String = "CgkIgobx5M4CEAIQCQ"
    const val FIRST_CAPTURE: String = "CgkIgobx5M4CEAIQAQ"
    const val FIRST_PROMOTION: String = "CgkIgobx5M4CEAIQBw"
    const val FIRST_VICTORY: String = "CgkIgobx5M4CEAIQDw"
    const val PLAY_10_GAMES: String = "CgkIgobx5M4CEAIQCA"
    const val THE_FLIPPER: String = "CgkIgobx5M4CEAIQAg"
    const val ROK_MASTER: String = "CgkIgobx5M4CEAIQDg"
    const val UNSTOPPABLE: String = "CgkIgobx5M4CEAIQAw"
    const val CHAMPION: String = "CgkIgobx5M4CEAIQCg"
    const val MIT: String = "CgkIgobx5M4CEAIQDA"
    const val STALEMIT: String = "CgkIgobx5M4CEAIQBg"
    const val ETERNAL_LOOP: String = "CgkIgobx5M4CEAIQBA"
    const val FIFTY_MOVE_RULE: String = "CgkIgobx5M4CEAIQCw"
    const val DEAD_BUT_DANGEROUS: String = "CgkIgobx5M4CEAIQDQ"
    const val GRANDMASTER: String = "CgkIgobx5M4CEAIQBQ"
    const val APPRENTICE: String = "CgkIgobx5M4CEAIQFQ"
    const val STRATEGIST: String = "CgkIgobx5M4CEAIQFA"
    const val TACTICIAN: String = "CgkIgobx5M4CEAIQEw"
}