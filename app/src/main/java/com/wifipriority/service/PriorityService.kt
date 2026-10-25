package com.wifipriority.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wifipriority.WifiStack
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service type `connectedDevice`: the tablet stays associated with an
 * external hotspot (the phone, failing over to the truck) over Wi-Fi.
 * `CHANGE_WIFI_STATE` satisfies that type's permission requirement on API 34+.
 * `location` would describe a location tracker, which this app is not, and
 * `specialUse` expects a Play Console declaration this sideloaded app does not have.
 *
 * The loop is a coroutine delay between scans. It does not hold a wakelock.
 */
class PriorityService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private var loopJob: Job? = null
    private var runtime: WifiStack.AppRuntime? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PriorityNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = PriorityNotifications.statusNotification(this, PriorityStatusRepository.status.value)
        try {
            ServiceCompat.startForeground(
                this,
                PriorityNotifications.STATUS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (security: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_DISABLE) {
            scope.launch { disableAndStop() }
            return START_NOT_STICKY
        }
        if (loopJob == null) {
            loopJob = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        runtime?.suggestionConnector?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val created = WifiStack.appRuntime(this)
        runtime = created
        val config = created.credentials.load()
        if (!shouldStartService(config.enabled)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        created.suggestionConnector?.start()
        scope.launch {
            PriorityStatusRepository.status.collect { status ->
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(
                    PriorityNotifications.STATUS_ID,
                    PriorityNotifications.statusNotification(this@PriorityService, status),
                )
                PriorityNotifications.updateApproval(this@PriorityService, status)
            }
        }
        created.coordinator.run()
    }

    private suspend fun disableAndStop() {
        val current = runtime ?: WifiStack.appRuntime(this).also { runtime = it }
        current.connector.applySuggestions(emptyList(), emptySet(), force = true)
        current.connector.applyTarget(WifiTarget.NONE)
        current.suggestionConnector?.stop()
        loopJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_DISABLE = "com.wifipriority.action.DISABLE"

        fun start(context: Context) {
            val intent = Intent(context, PriorityService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disable(context: Context) {
            val intent = Intent(context, PriorityService::class.java).setAction(ACTION_DISABLE)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
