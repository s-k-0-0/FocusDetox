package com.example.focusdetox.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    private val channelId = "detox_alerts_channel"
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Detox Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends alert notifications when distracting apps or scrolling are blocked."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendBlockedNotification(packageName: String, reason: String) {
        val appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Focus Mode Active")
            .setContentText("$appName blocked: $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}