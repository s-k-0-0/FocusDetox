package com.example.focusdetox.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.focusdetox.data.DetoxSettings
import com.example.focusdetox.data.DetoxSettingsRepository
import com.example.focusdetox.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DetoxAccessibilityService : AccessibilityService() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var repository: DetoxSettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var activeSettings = DetoxSettings()

    private val homeIntent by lazy {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    // Session Timer State
    private var currentActivePackage: String? = null
    private var sessionStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val sessionCheckRunnable = object : Runnable {
        override fun run() {
            checkActiveSession()
            handler.postDelayed(this, 10000) // Check every 10 seconds
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        notificationHelper = NotificationHelper(this)
        repository = DetoxSettingsRepository(this)

        serviceScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                activeSettings = settings
            }
        }

        handler.postDelayed(sessionCheckRunnable, 10000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val packageName = event.packageName?.toString() ?: return

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (activeSettings.blockedPackages.contains(packageName)) {
                if (activeSettings.isAppLockEnabled) {
                    triggerAppBlock(packageName, "App is locked")
                    return
                }

                // Start tracking active session duration
                if (currentActivePackage != packageName) {
                    currentActivePackage = packageName
                    sessionStartTime = System.currentTimeMillis()
                }
            } else {
                // User left blocked apps -> reset session
                currentActivePackage = null
            }
        }
    }

    private fun checkActiveSession() {
        val pkg = currentActivePackage ?: return
        if (!activeSettings.isSessionTimerEnabled) return

        val elapsedMinutes = (System.currentTimeMillis() - sessionStartTime) / 60000L
        if (elapsedMinutes >= activeSettings.maxSessionMinutes) {
            currentActivePackage = null
            triggerAppBlock(pkg, "Session time limit of ${activeSettings.maxSessionMinutes} mins reached")
        }
    }

    private fun triggerAppBlock(packageName: String, reason: String) {
        startActivity(homeIntent)
        if (activeSettings.isNotificationEnabled) {
            notificationHelper.sendBlockedNotification(packageName, reason)
        }
    }

    override fun onInterrupt() {
        currentActivePackage = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(sessionCheckRunnable)
        serviceScope.cancel()
    }
}