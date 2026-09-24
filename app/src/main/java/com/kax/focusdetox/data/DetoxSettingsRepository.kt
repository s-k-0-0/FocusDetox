package com.kax.focusdetox.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "detox_settings")

data class DetoxSettings(
    val isAppLockEnabled: Boolean = true,
    val isSessionTimerEnabled: Boolean = true,
    val maxSessionMinutes: Int = 5,
    val isNotificationEnabled: Boolean = true,
    val blockedPackages: Set<String> = emptySet(),
    val appLockUntilMap: Map<String, Long> = emptyMap()
)

class DetoxSettingsRepository(private val context: Context) {

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SESSION_TIMER_ENABLED = booleanPreferencesKey("session_timer_enabled")
        val MAX_SESSION_MINUTES = intPreferencesKey("max_session_minutes")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        val TIMED_LOCKS = stringSetPreferencesKey("timed_locks_v1")
    }

    val settingsFlow: Flow<DetoxSettings> = context.dataStore.data.map { prefs ->
        val rawTimedLocks = prefs[Keys.TIMED_LOCKS] ?: emptySet()
        val currentTime = System.currentTimeMillis()

        val parsedLocks = rawTimedLocks.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                val pkg = parts[0]
                val expireAt = parts[1].toLongOrNull() ?: 0L
                if (expireAt > currentTime) pkg to expireAt else null
            } else null
        }.toMap()

        DetoxSettings(
            isAppLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: true,
            isSessionTimerEnabled = prefs[Keys.SESSION_TIMER_ENABLED] ?: true,
            maxSessionMinutes = prefs[Keys.MAX_SESSION_MINUTES] ?: 5,
            isNotificationEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
            blockedPackages = prefs[Keys.BLOCKED_PACKAGES] ?: emptySet(),
            appLockUntilMap = parsedLocks
        )
    }

    suspend fun updateAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun updateSessionTimerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SESSION_TIMER_ENABLED] = enabled }
    }

    suspend fun updateMaxSessionMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.MAX_SESSION_MINUTES] = minutes }
    }

    suspend fun updateNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun togglePackageBlocked(packageName: String, shouldBlock: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BLOCKED_PACKAGES] ?: emptySet()
            val updated = current.toMutableSet()
            if (shouldBlock) updated.add(packageName) else updated.remove(packageName)
            prefs[Keys.BLOCKED_PACKAGES] = updated
        }
    }
    suspend fun setTemporaryAppLock(packageName: String, durationMinutes: Int) {
        context.dataStore.edit { prefs ->
            val currentLocks = prefs[Keys.TIMED_LOCKS] ?: emptySet()
            val currentTime = System.currentTimeMillis()
            val expireAt = currentTime + (durationMinutes * 60 * 1000L)

            val updated = currentLocks.filterNot { it.startsWith("$packageName|") }.toMutableSet()
            if (durationMinutes > 0) {
                updated.add("$packageName|$expireAt")
            }
            prefs[Keys.TIMED_LOCKS] = updated
        }
    }

    suspend fun removeTemporaryAppLock(packageName: String) {
        setTemporaryAppLock(packageName, 0)
    }
}