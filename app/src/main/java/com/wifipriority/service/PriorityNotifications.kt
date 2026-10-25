package com.wifipriority.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.wifipriority.MainActivity
import com.wifipriority.R
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.WifiTarget
import java.text.DateFormat
import java.util.Date

object PriorityNotifications {
    const val STATUS_ID = 1001
    const val APPROVAL_ID = 1002
    private const val STATUS_CHANNEL = "wifi_priority_status"
    private const val ALERT_CHANNEL = "wifi_priority_alerts"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(
            STATUS_CHANNEL,
            context.getString(R.string.channel_status),
            NotificationManager.IMPORTANCE_LOW,
        )
        val alerts = NotificationChannel(
            ALERT_CHANNEL,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(alerts)
    }

    fun statusNotification(context: Context, status: PriorityStatus): Notification {
        val text = if (!status.wifiEnabled) {
            context.getString(R.string.wifi_off_status)
        } else {
            context.getString(
                R.string.status_notification,
                networkLabel(context, status),
                switchLabel(status.lastSwitchTimeMillis),
            )
        }
        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (!status.wifiEnabled) {
            builder.addAction(
                R.drawable.ic_stat_wifi,
                context.getString(R.string.open_wifi_settings),
                wifiPanel(context),
            )
        }
        return builder.build()
    }

    fun updateApproval(context: Context, status: PriorityStatus) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (status.suggestionApproval != SuggestionApproval.PENDING &&
            status.suggestionApproval != SuggestionApproval.REJECTED
        ) {
            manager.cancel(APPROVAL_ID)
            return
        }
        val text = if (status.suggestionApproval == SuggestionApproval.REJECTED) {
            context.getString(R.string.suggestions_rejected)
        } else {
            context.getString(R.string.suggestions_pending)
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(context.getString(R.string.suggestions_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(wifiSettings(context))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_stat_wifi,
                context.getString(R.string.open_wifi_settings),
                wifiSettings(context),
            )
            .build()
        manager.notify(APPROVAL_ID, notification)
    }

    private fun networkLabel(context: Context, status: PriorityStatus): String {
        val ssid = status.connectedSsid
        if (!ssid.isNullOrBlank()) return ssid
        return when (status.target) {
            WifiTarget.PHONE -> context.getString(R.string.target_phone)
            WifiTarget.TRUCK -> context.getString(R.string.target_truck)
            WifiTarget.NONE -> context.getString(R.string.target_none)
        }
    }

    private fun switchLabel(millis: Long?): String {
        if (millis == null) return "—"
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun wifiPanel(context: Context): PendingIntent {
        val intent = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun wifiSettings(context: Context): PendingIntent {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
