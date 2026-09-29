package com.kax.focusdetox.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.kax.focusdetox.R
import com.kax.focusdetox.data.AppLimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────────────────
//  DetoxAccessibilityService
//
//  Core responsibilities:
//   • Detect foreground app changes via AccessibilityEvent
//   • Enforce per-app session time limits
//   • Redirect user to Home when limit is reached
//
//  NEW FEATURES (see bottom of file for full explanation):
//   1. Grace Period / Snooze  — one short extension per session
//   2. Daily Usage Accumulation — limits based on total daily time, not per-session
//   3. Focus Score Tracking — gamified productivity score saved daily
// ─────────────────────────────────────────────────────────────────────────────

class DetoxAccessibilityService : AccessibilityService() {

    // ── Coroutine scope tied to service lifecycle ─────────────────────────────
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Timer jobs ────────────────────────────────────────────────────────────
    private var sessionTimerJob: Job? = null
    private var warningJob: Job? = null       // fires a heads-up before hard block
    private var accumulationJob: Job? = null  // ticks every second for daily usage

    // ── State tracking ────────────────────────────────────────────────────────
    private var currentForegroundPackage: String? = null
    private var currentAppStartTime: Long = 0L
    private var snoozeUsedForCurrentSession: Boolean = false

    // ── SharedPreferences (lightweight storage; swap for DataStore if preferred) ─
    private lateinit var prefs: SharedPreferences

    // ─── Notification IDs & channel ──────────────────────────────────────────
    companion object {
        private const val CHANNEL_ID             = "focus_detox_session_channel"
        private const val NOTIF_SESSION_EXPIRED  = 1001
        private const val NOTIF_WARNING          = 1002
        private const val NOTIF_SNOOZE_GRANTED   = 1003

        // SharedPreferences keys
        private const val PREFS_NAME            = "detox_prefs"
        const val KEY_DAILY_USAGE_PREFIX        = "daily_usage_"   // + packageName
        const val KEY_FOCUS_SCORE               = "focus_score"
        const val KEY_LAST_SCORE_DATE           = "last_score_date"

        // Configurable defaults (replace with DataStore / Room lookups)
        private const val DEFAULT_DAILY_LIMIT_MS  = 30 * 60 * 1000L  // 30 min / day
        private const val WARNING_BEFORE_MS        = 60_000L           // warn 1 min before
        private const val SNOOZE_DURATION_MS       = 5 * 60 * 1000L   // 5-min snooze

        // Limits are now user-defined and loaded from Room via AppLimitRepository.
        // No hardcoded package list — the user picks apps in AppPickerScreen.

        // ── Packages whose usage earns Focus Score points ─────────────────────
        private val PRODUCTIVE_PACKAGES: Set<String> = setOf(
            "com.google.android.gm",         // Gmail
            "com.microsoft.office.word",
            "com.microsoft.office.excel",
            "com.google.android.apps.docs",
            "com.google.android.apps.sheets",
            "com.duolingo",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        resetDailyScoreIfNewDay()
    }

    override fun onDestroy() {
        super.onDestroy()
        flushCurrentAppAccumulatedTime()  // save any unsaved usage before dying
        serviceScope.cancel()
    }

    override fun onInterrupt() {
        flushCurrentAppAccumulatedTime()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Accessibility event entry point
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val newPackage = event.packageName?.toString() ?: return
        if (isIgnoredPackage(newPackage)) return
        if (newPackage == currentForegroundPackage) return  // same app, nothing to do

        serviceScope.launch {
            onAppSwitched(newPackage)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  App switch handler
    // ─────────────────────────────────────────────────────────────────────────

     suspend fun onAppSwitched(newPackage: String) {
        // ── 1. Save elapsed time for the app we're LEAVING ───────────────────
        flushCurrentAppAccumulatedTime()

        // ── 2. Cancel all pending jobs for the previous app ──────────────────
        cancelAllTimers()

        // ── 3. Update state to the new app ───────────────────────────────────
        currentForegroundPackage = newPackage
        currentAppStartTime = System.currentTimeMillis()
        snoozeUsedForCurrentSession = false

        // ── 4. Feature 2 — accumulate usage every second (tick) ──────────────
        startAccumulationTick(newPackage)

        // ── 5. Enforce daily time limit if this app is configured ─────────────
        val dailyLimit = getDailyLimitFor(newPackage) ?: return  // null = no limit set
        val alreadyUsed = getDailyUsageMs(newPackage)
        val remaining = dailyLimit - alreadyUsed

        if (remaining <= 0) {
            // Already hit the daily limit — block immediately
            showSessionExpiredNotification(newPackage, exceeded = true)
            blockAppAndGoHome(newPackage)
            return
        }

        // ── 6. Warn the user before the hard block ────────────────────────────
        if (remaining > WARNING_BEFORE_MS) {
            scheduleWarning(newPackage, remaining - WARNING_BEFORE_MS)
        }

        // ── 7. Schedule the hard block ────────────────────────────────────────
        scheduleBlock(newPackage, remaining)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Timer scheduling
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleWarning(packageName: String, delayMs: Long) {
        warningJob = serviceScope.launch {
            delay(delayMs.milliseconds)
            if (currentForegroundPackage == packageName) {
                showWarningNotification(packageName)
            }
        }
    }

    private fun scheduleBlock(packageName: String, delayMs: Long) {
        val targetPackage = packageName  // local capture avoids closure mutation bug
        sessionTimerJob = serviceScope.launch {
            delay(delayMs.milliseconds)
            if (currentForegroundPackage == targetPackage) {
                showSessionExpiredNotification(targetPackage, exceeded = false)
                blockAppAndGoHome(targetPackage)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 1 — Grace Period / Snooze
    //
    //  When the session-expired notification fires, the user can tap a
    //  "Give me 5 more minutes" action. This method is called from a
    //  BroadcastReceiver that intercepts that action.
    //
    //  Rules:
    //   • One snooze per app session (once the user closes and reopens the app,
    //     the snooze resets).
    //   • Snooze time still counts toward the daily usage total.
    //   • A secondary notification confirms the snooze was granted.
    // ─────────────────────────────────────────────────────────────────────────

    fun onSnoozeRequested(packageName: String) {
        if (snoozeUsedForCurrentSession) {
            // Snooze already used — do not grant another
            showNoSnoozeLeftNotification(packageName)
            return
        }
        if (currentForegroundPackage != packageName) return  // user has left anyway

        snoozeUsedForCurrentSession = true
        cancelAllTimers()

        // Restart accumulation & block for the snooze window
        startAccumulationTick(packageName)
        scheduleBlock(packageName, SNOOZE_DURATION_MS)
        showSnoozeGrantedNotification(packageName)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 2 — Daily Usage Accumulation
    //
    //  Rather than a simple per-session countdown, the service tracks total
    //  daily time spent in each limited app and blocks once the daily budget
    //  is exhausted — regardless of how many sessions the user breaks it into.
    //
    //  Storage: SharedPreferences keyed by "<date>_<packageName>".
    //  (Swap for Room or DataStore for multi-process safety in production.)
    //
    //  The "tick" job increments usage every second while the app is in the
    //  foreground. On app-switch or service death, any unsaved seconds are
    //  flushed via flushCurrentAppAccumulatedTime().
    // ─────────────────────────────────────────────────────────────────────────

    private fun startAccumulationTick(packageName: String) {
        // Feature 3 — also score productive app usage
        val isProductive = PRODUCTIVE_PACKAGES.contains(packageName)

        accumulationJob = serviceScope.launch {
            while (true) {
                delay(1_000)  // tick every second
                if (currentForegroundPackage != packageName) break

                incrementDailyUsage(packageName, 1_000L)

                if (isProductive) {
                    incrementFocusScore(points = 1)  // 1 point per productive second
                }
            }
        }
    }

    private fun flushCurrentAppAccumulatedTime() {
        val pkg = currentForegroundPackage ?: return
        if (currentAppStartTime == 0L) return

        val elapsed = System.currentTimeMillis() - currentAppStartTime
        if (elapsed > 0) {
            // The tick job already wrote most of this; flush any sub-second remainder
            // (in practice, ticks cover full seconds, so this is a safety net)
            incrementDailyUsage(pkg, elapsed % 1_000)
        }
        currentAppStartTime = 0L
    }

    // ── SharedPreferences helpers ─────────────────────────────────────────────

    private fun dailyKey(packageName: String): String {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "${KEY_DAILY_USAGE_PREFIX}${today}_$packageName"
    }

    private fun getDailyUsageMs(packageName: String): Long =
        prefs.getLong(dailyKey(packageName), 0L)

    private fun incrementDailyUsage(packageName: String, byMs: Long) {
        val key = dailyKey(packageName)
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + byMs).apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 3 — Focus Score Tracking
    //
    //  The service keeps a daily integer "Focus Score":
    //   +1 point  per second in a productive app (see PRODUCTIVE_PACKAGES)
    //   −5 points each time a limited (distracting) app blocks you
    //
    //  The score resets to 0 each day. Expose it in your UI by reading the
    //  KEY_FOCUS_SCORE key from SharedPreferences. You can show a streak,
    //  a daily high-score leaderboard, or milestone badges.
    // ─────────────────────────────────────────────────────────────────────────

    private fun incrementFocusScore(points: Int) {
        val current = prefs.getInt(KEY_FOCUS_SCORE, 0)
        prefs.edit().putInt(KEY_FOCUS_SCORE, (current + points).coerceAtLeast(0)).apply()
    }

    private fun penalizeFocusScore(points: Int = 5) {
        incrementFocusScore(-points)
    }

    private fun resetDailyScoreIfNewDay() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_SCORE_DATE, "")
        if (lastDate != today) {
            prefs.edit()
                .putInt(KEY_FOCUS_SCORE, 0)
                .putString(KEY_LAST_SCORE_DATE, today)
                .apply()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Block + redirect
    // ─────────────────────────────────────────────────────────────────────────

    private fun blockAppAndGoHome(packageName: String) {
        penalizeFocusScore()  // Feature 3: losing a session costs points

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // TODO: optionally launch your Jetpack Compose block-screen overlay here
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Notifications
    // ─────────────────────────────────────────────────────────────────────────

    private fun showWarningNotification(packageName: String) {
        val appName = getAppName(packageName)
        notify(
            id = NOTIF_WARNING,
            title = "⚠️ 1 Minute Left on $appName",
            text = "Your daily limit for $appName is almost up. Wrap up now."
        )
    }

    private fun showSessionExpiredNotification(packageName: String, exceeded: Boolean) {
        val appName = getAppName(packageName)
        val title = if (exceeded) "⛔ Daily Limit Already Reached" else "⏳ Time's Up on $appName"
        val text = if (exceeded)
            "You've already used your full daily budget for $appName."
        else
            "Your daily time on $appName has ended. Great job protecting your focus!"
        notify(NOTIF_SESSION_EXPIRED, title, text)
    }

    private fun showSnoozeGrantedNotification(packageName: String) {
        val appName = getAppName(packageName)
        notify(
            id = NOTIF_SNOOZE_GRANTED,
            title = "⏱ Snooze: 5 more minutes on $appName",
            text = "Last snooze for this session. This counts toward your daily total."
        )
    }

    private fun showNoSnoozeLeftNotification(packageName: String) {
        val appName = getAppName(packageName)
        notify(
            id = NOTIF_SNOOZE_GRANTED,
            title = "🚫 No Snooze Left for $appName",
            text = "You've already used your snooze for this session."
        )
    }

    private fun notify(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(id, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warnings and blocks when app time limits are reached."
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun cancelAllTimers() {
        sessionTimerJob?.cancel();  sessionTimerJob = null
        warningJob?.cancel();       warningJob = null
        accumulationJob?.cancel();  accumulationJob = null
    }

    // ── Room-backed limit lookup ──────────────────────────────────────────────
    // repository is lazy so Room only opens once the first event fires.
    private val repository by lazy { AppLimitRepository(applicationContext) }

    private suspend fun getDailyLimitFor(packageName: String): Long? =
        repository.getLimitFor(packageName)?.dailyLimitMs

    private fun isIgnoredPackage(packageName: String): Boolean =
        packageName == this.packageName ||
                packageName == "com.android.systemui" ||
                packageName.contains("launcher", ignoreCase = true)

    private fun getAppName(packageName: String): String =
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SnoozeReceiver  (register this in your AndroidManifest.xml)
//
//  Intercept the snooze action from the expired notification and forward it
//  to the service. For a real app, use a bound service or a local broadcast
//  manager; this is a minimal standalone example.
// ─────────────────────────────────────────────────────────────────────────────

// class SnoozeReceiver : BroadcastReceiver() {
//     override fun onReceive(context: Context, intent: Intent) {
//         val packageName = intent.getStringExtra("package_name") ?: return
//         // post to your service via a bound connection or EventBus
//     }
// }