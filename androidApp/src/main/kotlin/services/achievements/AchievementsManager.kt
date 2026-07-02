package com.agustin.tarati.services.achievements

import android.content.Context
import android.content.Intent
import com.agustin.tarati.R
import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.utils.logging.LoggingFactory.getLogger
import com.agustin.tarati.features.online.auth.AuthRepository
import com.agustin.tarati.network.models.ServerAchievementDto
import com.agustin.tarati.ui.theme.SeasonalThemeManager
import com.google.android.gms.games.PlayGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gestiona los logros de Tarati en Android.
 *
 * Extiende [BaseAchievementsManager] con dos canales de entrega paralelos:
 * - **Google Play Games** vía [IAchievementsReporter]
 * - **Servidor de Tarati** vía [AchievementSyncService]
 *
 * ## Reconciliación bidireccional cross-platform
 * [reconcileAchievements] fusiona el estado de Play Games y del servidor de Tarati
 * en ambos sentidos: un logro conseguido en cualquier plataforma (Web/Desktop →
 * servidor) se destraba en Play Games, y un logro conseguido en Android (Play Games)
 * se sube al servidor para que sea visible en el resto de plataformas. La fusión es
 * por **unión** (unlocked) y **máximo** (pasos incrementales); nunca revoca ni
 * retrocede. Se dispara al login/restore (vía [syncFromServer], invocado desde
 * `AuthViewModel`) y al volver la app a foreground ([ui.MainActivity.onResume]).
 *
 * ## Cambio de cuenta
 * [AchievementsRepository.getLastReconciledUserId] registra la última cuenta Tarati
 * reconciliada. Si la sesión actual pertenece a otro usuario, la reconciliación no
 * sube lo de Play Games al servidor (evita contaminar la cuenta nueva con logros del
 * usuario anterior); la bajada servidor → Play Games sí ocurre.
 *
 * ## Caché de sesión (GPlay)
 * [setStepsGPlay] usa [AchievementsRepository.getCachedSteps] para evitar llamadas
 * redundantes a la red dentro de la misma sesión. El servidor siempre recibe
 * el valor actualizado independientemente del caché.
 */
class AchievementsManager(
    private val context: Context,
    private val repository: AchievementsRepository,
    private val activityProvider: ActivityProvider,
    private val reporter: IAchievementsReporter,
    aiEngine: IAIEngine,
    private val syncService: AchievementSyncService,
    private val authRepository: AuthRepository,
) : BaseAchievementsManager(aiEngine) {

    private val logger = getLogger(javaClass.simpleName)
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Contadores: DataStore como fuente de verdad ───────────────────────────

    override suspend fun incrementCaptures(amount: Int): Int = repository.incrementTotalCaptures(amount)
    override suspend fun incrementPromotions(): Int = repository.incrementTotalPromotions()
    override suspend fun incrementWins(): Int = repository.incrementTotalWins()
    override suspend fun incrementGames(): Int = repository.incrementTotalGames()

    // ── Entrega de logros: GPlay + servidor ───────────────────────────────────

    override suspend fun onUnlock(achievementId: AchievementId) {
        val resId = achievementId.toAndroidResId() ?: return
        reporter.unlock(resId)
        val token = authRepository.getToken()
        if (token != null) {
            scope.launch { syncService.unlock(token, achievementId) }
        }
    }

    override suspend fun onProgress(achievementId: AchievementId, steps: Int, maxSteps: Int) {
        val resId = achievementId.toAndroidResId() ?: return
        val clamped = steps.coerceAtMost(maxSteps)
        setStepsGPlay(resId, clamped)
        val token = authRepository.getToken()
        if (token != null) {
            scope.launch { syncService.progress(token, achievementId, clamped) }
        }
    }

    /** Paleta estacional: desbloqueo en GPlay + servidor (desde super) + DataStore local. */
    override suspend fun onChampionWin() {
        super.onChampionWin()  // → onUnlock(HALLOWEEN/CHRISTMAS) → GPlay + servidor
        when {
            SeasonalThemeManager.isHalloweenDay() -> repository.unlockHalloween()
            SeasonalThemeManager.isChristmasDay() -> repository.unlockChristmas()
        }
    }

    // ── Reconciliación bidireccional ──────────────────────────────────────────

    @Volatile
    private var reconciling = false

    /**
     * Hook cross-platform de login/restore: `AuthViewModel` llama [syncFromServer]
     * en todas las plataformas al establecerse la sesión. En Android eso reconcilia
     * Play Games ↔ servidor en ambos sentidos.
     */
    override suspend fun syncFromServer() = reconcileAchievements()

    /**
     * Fusiona el estado de logros de Play Games y del servidor de Tarati en ambos
     * sentidos. No-op si falta la Activity, la sesión de Play Games o el token de
     * servidor — reintenta en el próximo disparo (login o foreground).
     *
     * Reglas de fusión (nunca revoca ni retrocede):
     * - **One-shot**: `unlocked = GPlay ∪ servidor`. Se propaga al que falte.
     * - **Incremental**: `steps = max(GPlay, servidor, local)`, acotado al máximo.
     *   Se empuja el máximo a Play Games, al servidor y al DataStore local.
     *
     * La subida GPlay → servidor se omite si la cuenta Tarati cambió respecto de la
     * última reconciliada (ver [AchievementsRepository.getLastReconciledUserId]).
     */
    suspend fun reconcileAchievements() {
        val token = authRepository.getToken() ?: return
        if (reconciling) return
        reconciling = true
        try {
            val userId = userIdFromToken(token)
            val lastUserId = repository.getLastReconciledUserId()
            // Sin subir lo de Play Games a una cuenta Tarati distinta de la última reconciliada.
            val allowUpstreamToServer = lastUserId == null || lastUserId == userId

            // El servidor es la fuente de verdad cross-platform: sin él no hay nada que
            // reconciliar. Play Games puede faltar (sin sesión Google) y aun así se aplica
            // servidor → local para que los logros ganados en otra plataforma estén en Android.
            val serverDtos = syncService.getAll(token).getOrElse { e ->
                logger.warn("reconcile: server unavailable — ${e.message}")
                return
            }
            val snapshots = loadGPlaySnapshots()

            val gplayById: Map<AchievementId, AchievementSnapshot> = snapshots
                .mapNotNull { snap -> achievementIdFromGPlayId(snap.id)?.let { it to snap } }
                .toMap()
            val serverById: Map<AchievementId, ServerAchievementDto> = serverDtos
                .mapNotNull { dto -> AchievementId.fromId(dto.achievementId)?.let { it to dto } }
                .toMap()

            for (id in AchievementId.entries) {
                val resId = id.toAndroidResId() ?: continue
                val maxSteps = INCREMENTAL_MAX[id]
                val gplay = gplayById[id]
                val server = serverById[id]

                if (maxSteps != null) {
                    val gplaySteps = gplay?.currentSteps ?: 0
                    val serverSteps = server?.currentSteps ?: 0
                    val localSteps = localCounter(id)
                    val merged = maxOf(gplaySteps, serverSteps, localSteps).coerceAtMost(maxSteps)

                    if (merged > gplaySteps) setStepsGPlay(resId, merged)
                    if (allowUpstreamToServer && merged > serverSteps) {
                        syncService.progress(token, id, merged)
                    }
                    ensureLocalCounterAtLeast(id, merged)
                } else {
                    val gplayUnlocked = gplay?.isUnlocked == true
                    val serverUnlocked = server?.unlockedAt != null
                    if (gplayUnlocked || serverUnlocked) {
                        if (!gplayUnlocked) reporter.unlock(resId)
                        if (allowUpstreamToServer && !serverUnlocked) {
                            syncService.unlock(token, id)
                        }
                        unlockPaletteLocal(id)  // no-op salvo para las cuatro paletas
                    }
                }
            }

            if (userId != null) repository.setLastReconciledUserId(userId)
            repository.markServerSyncDone()
            logger.debug("reconcile: completed (upstreamToServer=$allowUpstreamToServer)")
        } finally {
            reconciling = false
        }
    }

    /**
     * Suspende hasta obtener los snapshots de Play Games. Lista vacía si no hay Activity
     * o la carga falla — la reconciliación continúa aplicando servidor → local y los
     * envíos a Play Games hacen no-op de forma segura.
     */
    private suspend fun loadGPlaySnapshots(): List<AchievementSnapshot> =
        suspendCancellableCoroutine { cont ->
            reporter.loadAchievements(
                onResult = { if (cont.isActive) cont.resume(it) },
                onFailure = { if (cont.isActive) cont.resume(emptyList()) },
            )
        }

    /** Play Games ID (`CgkI…`) → [AchievementId] canónico. Inverso de [toAndroidResId]. */
    private fun achievementIdFromGPlayId(gplayId: String): AchievementId? =
        gplayIdToAchievementId[gplayId]

    private val gplayIdToAchievementId: Map<String, AchievementId> by lazy {
        AchievementId.entries.mapNotNull { id ->
            id.toAndroidResId()?.let { resId -> context.getString(resId) to id }
        }.toMap()
    }

    /** Lee el contador local que respalda un logro incremental. */
    private suspend fun localCounter(id: AchievementId): Int = when (id) {
        AchievementId.THE_FLIPPER -> repository.getTotalCaptures()
        AchievementId.ROK_MASTER -> repository.getTotalPromotions()
        AchievementId.UNSTOPPABLE, AchievementId.GRANDMASTER -> repository.getTotalWins()
        AchievementId.PLAY_10_GAMES -> repository.getTotalGames()
        else -> 0
    }

    /** Eleva el contador local que respalda un logro incremental hasta [value]. */
    private suspend fun ensureLocalCounterAtLeast(id: AchievementId, value: Int) = when (id) {
        AchievementId.THE_FLIPPER -> repository.ensureTotalCapturesAtLeast(value)
        AchievementId.ROK_MASTER -> repository.ensureTotalPromotionsAtLeast(value)
        AchievementId.UNSTOPPABLE, AchievementId.GRANDMASTER -> repository.ensureTotalWinsAtLeast(value)
        AchievementId.PLAY_10_GAMES -> repository.ensureTotalGamesAtLeast(value)
        else -> Unit
    }

    /** Persiste el unlock de paleta en DataStore para los logros de paleta; no-op para el resto. */
    private suspend fun unlockPaletteLocal(id: AchievementId) = when (id) {
        AchievementId.HALLOWEEN_THEME -> repository.unlockHalloween()
        AchievementId.CHRISTMAS_THEME -> repository.unlockChristmas()
        AchievementId.THE_FIRST_LIGHT -> repository.unlockAurora()
        AchievementId.THE_DARK_SIDE -> repository.unlockEmber()
        else -> Unit
    }

    /** Extrae el claim `sub` (userId) del JWT sin verificar la firma. Null si no se puede parsear. */
    private fun userIdFromToken(token: String): String? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val decoded = base64UrlDecode(payload)
        Regex(""""sub"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
    }.getOrNull()

    /** Decodifica base64 URL-safe a texto UTF-8 en Kotlin puro (sin dependencias de Android). */
    private fun base64UrlDecode(input: String): String {
        val padded = buildString {
            append(input.replace('-', '+').replace('_', '/'))
            repeat((4 - input.length % 4) % 4) { append('=') }
        }
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i + 3 < padded.length) {
            val n = (table.indexOf(padded[i]) shl 18) or
                    (table.indexOf(padded[i + 1]) shl 12) or
                    (if (padded[i + 2] == '=') 0 else table.indexOf(padded[i + 2]) shl 6) or
                    (if (padded[i + 3] == '=') 0 else table.indexOf(padded[i + 3]))
            bytes.add((n shr 16).toByte())
            if (padded[i + 2] != '=') bytes.add((n shr 8 and 0xFF).toByte())
            if (padded[i + 3] != '=') bytes.add((n and 0xFF).toByte())
            i += 4
        }
        return bytes.toByteArray().decodeToString()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    override fun showAchievementsUI(onNavigateToScreen: () -> Unit) {
        val activity = activityProvider.get() ?: run {
            getLogger().debug("showAchievementsUI skipped: no activity")
            onNavigateToScreen()
            return
        }

        // Verifica si el usuario tiene sesión Google activa antes de intentar abrir Play Games.
        PlayGames.getGamesSignInClient(activity)
            .isAuthenticated
            .addOnSuccessListener { result ->
                if (result.isAuthenticated) {
                    PlayGames.getAchievementsClient(activity)
                        .achievementsIntent
                        .addOnSuccessListener { intent -> launchAchievementsIntent(intent) }
                        .addOnFailureListener { e ->
                            getLogger().error("showAchievementsUI failed: ${e.message}", e)
                            onNavigateToScreen()
                        }
                } else {
                    getLogger().debug("showAchievementsUI: user not signed in to Play Games — navigating to own screen")
                    onNavigateToScreen()
                }
            }
            .addOnFailureListener { e ->
                getLogger().debug("showAchievementsUI: could not check sign-in status — ${e.message}")
                onNavigateToScreen()
            }
    }

    /**
     * Lanza el Intent de logros vía [ActivityProvider.intentLauncher] para
     * evitar el bloqueo IntentRedirect Hardening en Android 14+.
     */
    private fun launchAchievementsIntent(intent: Intent) {
        val launcher = activityProvider.intentLauncher
        if (launcher != null) {
            launcher(intent)
        } else {
            activityProvider.get()?.startActivity(intent)
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Envía pasos a Play Games con caché de sesión para evitar llamadas redundantes.
     * El servidor recibe el valor actualizado independientemente del caché.
     */
    private suspend fun setStepsGPlay(achievementResId: Int, steps: Int) {
        val cachedThisSession = repository.getCachedSteps(achievementResId)
        if (steps <= cachedThisSession) return
        val submitted = reporter.setSteps(achievementResId, steps)
        if (submitted) repository.updateCachedSteps(achievementResId, steps)
    }
}

// ── Logros incrementales y su máximo ─────────────────────────────────────────

/**
 * Máximo de pasos de cada logro incremental (espejo de `AchievementsMetadata.maxSteps`
 * en el cliente y de `AchievementDao.INCREMENTAL_THRESHOLDS` en el servidor).
 * Un logro presente aquí se trata como incremental en [AchievementsManager.reconcileAchievements];
 * el resto, como one-shot. Mantener sincronizado con ambas fuentes.
 */
private val INCREMENTAL_MAX: Map<AchievementId, Int> = mapOf(
    AchievementId.PLAY_10_GAMES to 10,
    AchievementId.THE_FLIPPER to 50,
    AchievementId.ROK_MASTER to 25,
    AchievementId.UNSTOPPABLE to 10,
    AchievementId.GRANDMASTER to 50,
)

// ── Mapeo AchievementId ↔ Android resource ID ────────────────────────────────

/**
 * Traduce un [AchievementId] canónico al resource ID de Google Play Games.
 * Retorna null para logros que no tienen contraparte en Play Games (actualmente ninguno).
 */
private fun AchievementId.toAndroidResId(): Int? = when (this) {
    AchievementId.WELCOME_TO_TARATI -> R.string.achievement_welcome_to_tarati
    AchievementId.FIRST_CAPTURE -> R.string.achievement_first_capture
    AchievementId.FIRST_PROMOTION -> R.string.achievement_first_promotion
    AchievementId.FIRST_VICTORY -> R.string.achievement_first_victory
    AchievementId.PLAY_10_GAMES -> R.string.achievement_play_10_games
    AchievementId.THE_FLIPPER -> R.string.achievement_the_flipper
    AchievementId.ROK_MASTER -> R.string.achievement_rok_master
    AchievementId.UNSTOPPABLE -> R.string.achievement_unstoppable
    AchievementId.CHAMPION -> R.string.achievement_champion
    AchievementId.MIT -> R.string.achievement_mit
    AchievementId.STALEMIT -> R.string.achievement_stalemit
    AchievementId.ETERNAL_LOOP -> R.string.achievement_eternal_loop
    AchievementId.FIFTY_MOVE_RULE -> R.string.achievement_fifty_move_rule
    AchievementId.DEAD_BUT_DANGEROUS -> R.string.achievement_dead_but_dangerous
    AchievementId.GRANDMASTER -> R.string.achievement_grandmaster
    AchievementId.HALLOWEEN_THEME -> R.string.achievement_halloween_theme
    AchievementId.CHRISTMAS_THEME -> R.string.achievement_christmas_theme
    AchievementId.APPRENTICE -> R.string.achievement_apprentice
    AchievementId.STRATEGIST -> R.string.achievement_strategist
    AchievementId.TACTICIAN -> R.string.achievement_tactician
    AchievementId.THE_FIRST_LIGHT -> R.string.achievement_the_first_light
    AchievementId.THE_DARK_SIDE -> R.string.achievement_the_dark_side
}
