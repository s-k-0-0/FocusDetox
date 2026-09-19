package com.example.focusdetox.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "detox_settings")

data class DetoxSettings(
    val isAppLockEnabled: Boolean = true,
    val isSessionTimerEnabled: Boolean = true,
    val maxSessionMinutes: Int = 5, // Continuous minutes allowed before lockout
    val isNotificationEnabled: Boolean = true,
    val blockedPackages: Set<String> = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.twitter.android",
        "com.google.android.youtube"
    )
)

class DetoxSettingsRepository(private val context: Context) {

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SESSION_TIMER_ENABLED = booleanPreferencesKey("session_timer_enabled")
        val MAX_SESSION_MINUTES = intPreferencesKey("max_session_minutes")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    }

    val settingsFlow: Flow<DetoxSettings> = context.dataStore.data.map { prefs ->
        DetoxSettings(
            isAppLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: true,
            isSessionTimerEnabled = prefs[Keys.SESSION_TIMER_ENABLED] ?: true,
            maxSessionMinutes = prefs[Keys.MAX_SESSION_MINUTES] ?: 5,
            isNotificationEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
            blockedPackages = prefs[Keys.BLOCKED_PACKAGES] ?: setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.twitter.android",
                "com.google.android.youtube"
            )
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
            val current = prefs[Keys.BLOCKED_PACKAGES] ?: setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.twitter.android",
                "com.google.android.youtube"
            )
            val updated = current.toMutableSet()
            if (shouldBlock) updated.add(packageName) else updated.remove(packageName)
            prefs[Keys.BLOCKED_PACKAGES] = updated
        }
    }
}