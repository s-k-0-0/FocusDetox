package com.kax.focusdetox.data

import android.content.Context
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("focusdetox_settings", Context.MODE_PRIVATE)

    // ── Strict mode & PIN ─────────────────────────────────────────────────────

    var isStrictModeEnabled: Boolean
        get() = prefs.getBoolean("strict_mode", false)
        set(v) { prefs.edit().putBoolean("strict_mode", v).apply() }

    fun setPin(pin: String) {
        prefs.edit().putString("pin_hash", sha256(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove("pin_hash").apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString("pin_hash", null) ?: return true
        return sha256(pin) == stored
    }

    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    // ── Notifications ─────────────────────────────────────────────────────────

    var warningNotifEnabled: Boolean
        get() = prefs.getBoolean("warning_notif", true)
        set(v) { prefs.edit().putBoolean("warning_notif", v).apply() }

    var blockNotifEnabled: Boolean
        get() = prefs.getBoolean("block_notif", true)
        set(v) { prefs.edit().putBoolean("block_notif", v).apply() }

    var snoozeEnabled: Boolean
        get() = prefs.getBoolean("snooze_enabled", true)
        set(v) { prefs.edit().putBoolean("snooze_enabled", v).apply() }

    // ── Theme ─────────────────────────────────────────────────────────────────

    /** 0 = system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) { prefs.edit().putInt("theme_mode", v).apply() }

    // ── Pro ───────────────────────────────────────────────────────────────────

    var isPro: Boolean
        get() = prefs.getBoolean("is_pro", false)
        set(v) { prefs.edit().putBoolean("is_pro", v).apply() }

    // ── Focus score (resets each day) ─────────────────────────────────────────

    fun getFocusScore(): Int {
        resetScoreIfNewDay()
        return prefs.getInt("focus_score", 0)
    }

    fun addFocusScore(delta: Int) {
        resetScoreIfNewDay()
        val new = (prefs.getInt("focus_score", 0) + delta).coerceAtLeast(0)
        prefs.edit().putInt("focus_score", new).apply()
    }

    private fun resetScoreIfNewDay() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (prefs.getString("score_date", "") != today) {
            prefs.edit().putInt("focus_score", 0).putString("score_date", today).apply()
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}