package com.kax.focusdetox.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kax.focusdetox.R
import com.kax.focusdetox.data.DetoxSettings
import com.kax.focusdetox.data.DetoxSettingsRepository
import com.kax.focusdetox.data.dataStore
import com.kax.focusdetox.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

class DetoxAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionTimerJob: Job? = null
    private var currentForegroundPackage: String? = null

    // In-memory cache synced with DetoxSettingsRepository
    private var blockedAppsMap: Map<String, Long> = emptyMap()

    companion object {
        private const val CHANNEL_ID = "focus_detox_session_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeBlockedApps()
    }

    private fun observeBlockedApps() {
        val repository = DetoxSettingsRepository(applicationContext)

        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                // Master kill-switches
                if (!settings.isAppLockEnabled || !settings.isSessionTimerEnabled) {
                    blockedAppsMap = emptyMap()
                    return@collect
                }

                val limitMillis = settings.maxSessionMinutes * 60 * 1000L

                // Maps ONLY explicitly selected apps.
                // Defaults to emptyMap() when blockedPackages is empty.
                blockedAppsMap = settings.blockedPackages.associateWith { limitMillis }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newPackage = event.packageName?.toString() ?: return

            // Filters out Gboard, system UI, launchers, and non-launchable popups
            if (!isValidApplication(newPackage)) return

            if (newPackage != currentForegroundPackage) {
                currentForegroundPackage = newPackage
                onAppSwitched(newPackage)
            }
        }
    }

    private fun onAppSwitched(newPackage: String) {
        // Always cancel active timers when switching windows
        sessionTimerJob?.cancel()
        sessionTimerJob = null

        // SAFEGUARD: If no apps are selected or app is not in list, STOP
        if (blockedAppsMap.isEmpty() || !blockedAppsMap.containsKey(newPackage)) {
            return
        }

        val limitMillis = blockedAppsMap[newPackage] ?: return
        if (limitMillis <= 0L) return

        startSessionTimer(newPackage, limitMillis)
    }

    private fun startSessionTimer(packageName: String, limitMillis: Long) {
        sessionTimerJob = serviceScope.launch {
            delay(limitMillis)

            // Verify user is still inside this target app when time expires
            if (currentForegroundPackage == packageName) {
                showSessionExpiredNotification(packageName)
                blockAppAndGoHome()
            }
        }
    }

    private fun isValidApplication(packageName: String): Boolean {
        if (packageName == this.packageName) return false
        if (packageName == "com.android.systemui" || packageName == "android") return false
        if (isInputMethod(packageName)) return false
        if (isDefaultLauncher(packageName)) return false

        return packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun isInputMethod(packageName: String): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val enabledImes = imm?.enabledInputMethodList ?: return false
            enabledImes.any { it.packageName == packageName }
        } catch (e: Exception) {
            false
        }
    }

    private fun isDefaultLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun blockAppAndGoHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showSessionExpiredNotification(packageName: String) {
        val appName = getAppName(packageName)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Session Time Up ⏳")
            .setContentText("Your time limit on $appName has ended.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Expiry Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications sent when app session limits expire."
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}