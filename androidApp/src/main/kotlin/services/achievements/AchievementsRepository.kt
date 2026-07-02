package com.agustin.tarati.services.achievements

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agustin.tarati.BuildConfig
import com.agustin.tarati.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.achievementsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "achievements")

/**
 * Persists achievement counters and seasonal unlock flags across sessions using DataStore.
 *
 * All DataStore operations are `suspend` and must be called from a coroutine.
 * [GameEvents] launches them on [kotlinx.coroutines.Dispatchers.IO]
 * so they never block the main thread during gameplay.
 *
 * ## Reinstall resilience
 * On fresh installs the local DataStore is empty while Google Play Games retains
 * the server-side progress. [needsServerSync] / [markServerSyncDone] track whether
 * a sync has been performed for this installation. [AchievementsManager.syncFromServerIfNeeded]
 * reads Play Games progress and calls the [ensureAtLeast] family of functions to
 * restore the local floor, preventing [setSteps] calls with stale-low values that
 * Play Games silently ignores.
 *
 * ## Seasonal and special-event palette unlocks
 * [halloweenUnlocked], [christmasUnlocked], [auroraUnlocked] and [emberUnlocked]
 * are observable [Flow]s consumed by
 * [SettingsViewModel] to filter the available
 * palette list in real time.
 * In debug builds all four emit `true` unconditionally so every unlockable palette is
 * always available in the selector. [BuildConfig.DEBUG] is `false` in release builds.
 */
class AchievementsRepository(private val context: Context) {

    // ── Numeric counter keys ──────────────────────────────────────────────────

    private val totalCapturesKey = intPreferencesKey("total_captures")
    private val totalPromotionsKey = intPreferencesKey("total_promotions")
    private val totalWinsKey = intPreferencesKey("total_wins")
    private val totalGamesKey = intPreferencesKey("total_games")

    // ── Session-cache keys (step values already submitted to Play Games) ───────
    // Only used to avoid redundant network calls within a single session.
    // NOT a cross-session gate — see AchievementsManager.setSteps.

    private val theFlipperCachedKey = intPreferencesKey("cached_the_flipper")
    private val rokMasterCachedKey = intPreferencesKey("cached_rok_master")
    private val unstoppableCachedKey = intPreferencesKey("cached_unstoppable")
    private val grandmasterCachedKey = intPreferencesKey("cached_grandmaster")
    private val play10GamesCachedKey = intPreferencesKey("cached_play_10_games")

    // ── Reinstall-sync key ────────────────────────────────────────────────────

    /**
     * Absent on reinstall (DataStore wiped with app data).
     * Present (value "done") iff a Play Games sync has been performed this install.
     */
    private val serverSyncDoneKey = stringPreferencesKey("server_sync_done_v1")

    /**
     * Tarati server userId of the last account reconciled on this device.
     * Used by [AchievementsManager.reconcileAchievements] to avoid pushing the
     * previous user's Play Games achievements into a different Tarati account
     * after an account switch. Null until the first reconciliation.
     */
    private val lastReconciledUserIdKey = stringPreferencesKey("last_reconciled_user_id_v1")

    // ── Seasonal unlock keys ──────────────────────────────────────────────────

    private val halloweenUnlockedKey = booleanPreferencesKey("seasonal_halloween_unlocked")
    private val christmasUnlockedKey = booleanPreferencesKey("seasonal_christmas_unlocked")

    // Special event palette unlocks (4/4–6/4 annual window)
    private val auroraUnlockedKey = booleanPreferencesKey("special_event_aurora_unlocked")
    private val emberUnlockedKey = booleanPreferencesKey("special_event_ember_unlocked")

    // ── Public counter API ────────────────────────────────────────────────────

    suspend fun incrementTotalCaptures(amount: Int): Int = increment(totalCapturesKey, amount)
    suspend fun incrementTotalPromotions(): Int = increment(totalPromotionsKey, 1)
    suspend fun incrementTotalWins(): Int = increment(totalWinsKey, 1)
    suspend fun incrementTotalGames(): Int = increment(totalGamesKey, 1)

    // Lectura de los contadores locales — usada por la reconciliación bidireccional
    // ([AchievementsManager.reconcileAchievements]) para incluir el piso local en el max.

    suspend fun getTotalCaptures(): Int = getInt(totalCapturesKey)
    suspend fun getTotalPromotions(): Int = getInt(totalPromotionsKey)
    suspend fun getTotalWins(): Int = getInt(totalWinsKey)
    suspend fun getTotalGames(): Int = getInt(totalGamesKey)

    // ── Seasonal unlock API ───────────────────────────────────────────────────

    /**
     * Observable flow consumed by [SettingsViewModel]
     * to filter the palette list in real time. Emits true once the Halloween palette has
     * been permanently unlocked by winning on Champion difficulty on Halloween day.
     * Always emits true in debug builds.
     */
    private val halloweenUnlocked: Flow<Boolean> =
        context.achievementsDataStore.data.map {
            BuildConfig.DEBUG || (it[halloweenUnlockedKey] ?: false)
        }

    /**
     * Observable flow consumed by [SettingsViewModel]
     * to filter the palette list in real time. Emits true once the Christmas palette has
     * been permanently unlocked by winning on Champion difficulty on Christmas day.
     * Always emits true in debug builds.
     */
    private val christmasUnlocked: Flow<Boolean> =
        context.achievementsDataStore.data.map {
            BuildConfig.DEBUG || (it[christmasUnlockedKey] ?: false)
        }

    /**
     * Observable flow consumed by [SettingsViewModel]
     * to filter the palette list.
     * Emits true once the Aurora palette has been permanently unlocked by completing
     * the "The First Light" special event during the 4/4–6/4 annual window.
     * Always emits true in debug builds.
     */
    val auroraUnlocked: Flow<Boolean> =
        context.achievementsDataStore.data.map {
            BuildConfig.DEBUG || (it[auroraUnlockedKey] ?: false)
        }

    /**
     * Observable flow consumed by [SettingsViewModel]
     * to filter the palette list.
     * Emits true once the Ember palette has been permanently unlocked by completing
     * the "The Dark Side" special event during the 4/4–6/4 annual window.
     * Always emits true in debug builds.
     */
    val emberUnlocked: Flow<Boolean> =
        context.achievementsDataStore.data.map {
            BuildConfig.DEBUG || (it[emberUnlockedKey] ?: false)
        }

    /**
     * Permanently unlocks the Halloween palette. Idempotent — safe to call multiple times.
     * Called by [AchievementsManager] after a Champion win on Halloween day.
     */
    suspend fun unlockHalloween() {
        context.achievementsDataStore.edit { prefs -> prefs[halloweenUnlockedKey] = true }
    }

    /**
     * Permanently unlocks the Christmas palette. Idempotent — safe to call multiple times.
     * Called by [AchievementsManager] after a Champion win on Christmas day.
     */
    suspend fun unlockChristmas() {
        context.achievementsDataStore.edit { prefs -> prefs[christmasUnlockedKey] = true }
    }

    /**
     * Permanently unlocks the Aurora palette. Idempotent — safe to call multiple times.
     * Called by [SpecialEventManager]
     * when "The First Light" condition is met.
     */
    suspend fun unlockAurora() {
        context.achievementsDataStore.edit { prefs -> prefs[auroraUnlockedKey] = true }
    }

    /**
     * Permanently unlocks the Ember palette. Idempotent — safe to call multiple times.
     * Called by [SpecialEventManager]
     * when "The Dark Side" condition is met.
     */
    suspend fun unlockEmber() {
        context.achievementsDataStore.edit { prefs -> prefs[emberUnlockedKey] = true }
    }

    // ── Server-sync support ───────────────────────────────────────────────────

    /**
     * Returns true if a Play Games server sync has never been performed for this
     * installation. Used by [AchievementsManager.syncFromServerIfNeeded].
     */
    suspend fun needsServerSync(): Boolean = getString(serverSyncDoneKey) == null

    /**
     * Marks the server sync as completed for this installation.
     * Called by [AchievementsManager] after a successful sync.
     */
    suspend fun markServerSyncDone() {
        context.achievementsDataStore.edit { prefs -> prefs[serverSyncDoneKey] = "done" }
    }

    /**
     * Returns the Tarati server userId of the last account reconciled on this
     * device, or null if no reconciliation has run yet.
     */
    suspend fun getLastReconciledUserId(): String? = getString(lastReconciledUserIdKey)

    /**
     * Records the Tarati server userId just reconciled on this device.
     */
    suspend fun setLastReconciledUserId(userId: String) {
        context.achievementsDataStore.edit { prefs -> prefs[lastReconciledUserIdKey] = userId }
    }

    /**
     * Raises [key] to [serverValue] if the server value is higher than the local one.
     *
     * Called once per installation with values retrieved from the Play Games API.
     * Restores the local floor so subsequent [setSteps] calls always pass a value
     * Play Games will accept.
     */
    private suspend fun ensureAtLeast(key: Preferences.Key<Int>, serverValue: Int) {
        context.achievementsDataStore.edit { prefs ->
            val current = prefs[key] ?: 0
            if (serverValue > current) prefs[key] = serverValue
        }
    }

    suspend fun ensureTotalCapturesAtLeast(value: Int): Unit = ensureAtLeast(totalCapturesKey, value)
    suspend fun ensureTotalPromotionsAtLeast(value: Int): Unit = ensureAtLeast(totalPromotionsKey, value)
    suspend fun ensureTotalWinsAtLeast(value: Int): Unit = ensureAtLeast(totalWinsKey, value)
    suspend fun ensureTotalGamesAtLeast(value: Int): Unit = ensureAtLeast(totalGamesKey, value)

    // ── Cached-steps API ─────────────────────────────────────────────────────

    /**
     * Returns the last step value submitted to Play Games within this session.
     * Returns 0 after reinstall (cache is wiped with DataStore).
     */
    suspend fun getCachedSteps(achievementResId: Int): Int {
        val key = cachedKeyFor(achievementResId) ?: return 0
        return getInt(key)
    }

    /**
     * Records a step value just submitted to Play Games so redundant calls can be
     * skipped within the same session.
     */
    suspend fun updateCachedSteps(achievementResId: Int, steps: Int) {
        val key = cachedKeyFor(achievementResId) ?: return
        set(key, steps)
    }

    private fun cachedKeyFor(achievementResId: Int): Preferences.Key<Int>? =
        when (achievementResId) {
            R.string.achievement_the_flipper -> theFlipperCachedKey
            R.string.achievement_rok_master -> rokMasterCachedKey
            R.string.achievement_unstoppable -> unstoppableCachedKey
            R.string.achievement_grandmaster -> grandmasterCachedKey
            R.string.achievement_play_10_games -> play10GamesCachedKey
            else -> null
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun increment(key: Preferences.Key<Int>, amount: Int): Int {
        var newValue = 0
        context.achievementsDataStore.edit { prefs ->
            newValue = (prefs[key] ?: 0) + amount
            prefs[key] = newValue
        }
        return newValue
    }

    private suspend fun getInt(key: Preferences.Key<Int>): Int {
        return context.achievementsDataStore.data.first()[key] ?: 0
    }

    private suspend fun getString(key: Preferences.Key<String>): String? {
        return context.achievementsDataStore.data.first()[key]
    }

    private suspend fun set(key: Preferences.Key<Int>, value: Int) {
        context.achievementsDataStore.edit { prefs -> prefs[key] = value }
    }
}