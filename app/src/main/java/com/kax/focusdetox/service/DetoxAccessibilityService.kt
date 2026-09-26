package com.kax.focusdetox.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.kax.focusdetox.R
import com.kax.focusdetox.data.DetoxSettings
import com.kax.focusdetox.data.DetoxSettingsRepository
import com.kax.focusdetox.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DetoxAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionTimerJob: Job? = null
    private var currentForegroundPackage: String? = null

    companion object {
        private const val CHANNEL_ID = "focus_detox_session_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newPackage = event.packageName?.toString() ?: return

            // ignore system launcher, system UI, or app own screens
            if (isIgnoredPackage(newPackage)) return

            // triggered only when the active app actually changes
            if (newPackage != currentForegroundPackage) {
                currentForegroundPackage = newPackage
                onAppSwitched(newPackage)
            }
        }
    }

    private fun onAppSwitched(newPackage: String) {
        // 1 cancel existing session timer as user left the previous app
        sessionTimerJob?.cancel()
        sessionTimerJob = null

        //Check if the newly opened app has a session timer/limit active
        if (isAppLimited(newPackage)) {
            val limitMillis = getSessionLimitForApp(newPackage) // e.g. 60,000ms for 1 min
            startSessionTimer(newPackage, limitMillis)
        }
    }

    private fun startSessionTimer(packageName: String, limitMillis: Long) {
        sessionTimerJob = serviceScope.launch {
            delay(limitMillis.milliseconds)

            // verify user is STILL in this app when timer expires!
            if (currentForegroundPackage == packageName) {
                // expired notification
                showSessionExpiredNotification(packageName)


                blockAppAndGoHome(packageName)
            }
        }
    }

    private fun blockAppAndGoHome(packageName: String) {
        // Redirect to Android Home Screen
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
            .setContentTitle("Session Time Up! ⏳")
            .setContentText("Your time limit on $appName has ended. Great job staying focused!")
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
                description = "Notifications sent when app session limits are reached."
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        return packageName == this.packageName ||
                packageName == "com.android.systemui" ||
                packageName.contains("launcher")
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


    private fun isAppLimited(packageName: String): Boolean = true
    private fun getSessionLimitForApp(packageName: String): Long = 60_000L

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
