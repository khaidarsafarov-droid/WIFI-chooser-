package com.wifipriority.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wifipriority.WifiStack
import com.wifipriority.wifi.EnabledLatch
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service type `connectedDevice`: the tablet stays associated with an
 * external hotspot (the phone, failing over to the truck) over Wi-Fi.
 * `CHANGE_WIFI_STATE` satisfies that type's permission requirement on API 34+.
 * `location` would describe a location tracker, which this app is not, and
 * `specialUse` expects a Play Console declaration this sideloaded app does not have.
 *
 * `connectedDevice` is not one of the types Android 15/16 limits to 6 hours
 * (`dataSync`, `mediaProcessing`), and it is not on the `BOOT_COMPLETED` denylist
 * (`dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`,
 * `microphone`). [startForeground] runs before any disk read so the
 * `startForegroundService` timer is met. One UI can still freeze the process
 * if the app is a sleeping app; the status screen asks for the battery exemption
 * and the never-sleeping list.
 *
 * The scan loop is a coroutine delay. It does not hold a wakelock. A partial
 * wakelock is held only while a recovery deadline (force-return, unvalidated
 * watchdog, or a failed suggestion add) is pending.
 */
class PriorityService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private var loopJob: Job? = null
    private var runtime: WifiStack.AppRuntime? = null
    private val gate = ServiceRunGate()

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
        } catch (error: Exception) {
            if (!isForegroundStartBlocked(error)) throw error
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_DISABLE) {
            val token = gate.onDisable()
            scope.launch {
                disableAndStop(token)
                if (gate.shouldEnsureLoop(loopJob?.isActive == true)) {
                    loopJob = scope.launch { runLoop() }
                }
            }
            return START_NOT_STICKY
        }
        gate.onStart()
        if (gate.shouldEnsureLoop(loopJob?.isActive == true)) {
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
        val token = gate.current()
        val waits = DeadlineWait(this)
        runtime?.suggestionConnector?.stop()
        val created = WifiStack.appRuntime(this, awaitDeadline = waits::await)
        runtime = created
        val config = created.credentials.load()
        EnabledLatch.write(this, config.enabled)
        if (!shouldStartService(config.enabled)) {
            if (gate.isCurrent(token)) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        created.suggestionConnector?.start()
        coroutineScope {
            val notes = launch {
                PriorityStatusRepository.status.collect { status ->
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(
                        PriorityNotifications.STATUS_ID,
                        PriorityNotifications.statusNotification(this@PriorityService, status),
                    )
                    PriorityNotifications.updateApproval(this@PriorityService, status)
                }
            }
            try {
                created.coordinator.run()
            } finally {
                notes.cancel()
            }
        }
    }

    private suspend fun disableAndStop(token: Int) {
        if (!gate.shouldStop(token)) return
        val current = runtime ?: WifiStack.appRuntime(this).also { runtime = it }
        if (!gate.shouldStop(token)) return
        current.connector.applySuggestions(emptyList(), emptySet(), force = true)
        current.connector.applyTarget(WifiTarget.NONE)
        current.suggestionConnector?.stop()
        loopJob?.cancel()
        if (!gate.shouldStop(token)) {
            loopJob = scope.launch { runLoop() }
            return
        }
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
