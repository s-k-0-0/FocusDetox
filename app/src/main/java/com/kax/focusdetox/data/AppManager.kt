package com.kax.focusdetox.data

import android.content.Context
import androidx.core.content.edit

object AppLimitManager {

    private const val PREFS_NAME = "detox_prefs"
    private const val KEY_APP_LIMIT_PREFIX = "app_limit_"

    /**
     * Call this when a user selects an app in "Manage Apps" and sets a limit.
     * @param limitMinutes e.g., 15 for a 15-minute daily budget.
     */
    fun setAppDailyLimit(context: Context, packageName: String, limitMinutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val limitMs = limitMinutes * 60 * 1000L
        prefs.edit {
            putLong("${KEY_APP_LIMIT_PREFIX}$packageName", limitMs)
        }
    }

    /**
     * Call this when a user unchecks or removes an app from the blocked list.
     */
    fun removeAppDailyLimit(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove("${KEY_APP_LIMIT_PREFIX}$packageName")
        }
    }

    /**
     * Use this in your UI to fetch all manually blocked package names.
     */
    fun getBlockedAppPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith(KEY_APP_LIMIT_PREFIX) }
            .map { it.removePrefix(KEY_APP_LIMIT_PREFIX) }
            .toSet()
    }
}