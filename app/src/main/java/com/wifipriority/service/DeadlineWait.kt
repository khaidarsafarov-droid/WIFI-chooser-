package com.wifipriority.service

import android.content.Context
import android.os.PowerManager
import com.wifipriority.wifi.SuggestionPlanner
import kotlinx.coroutines.delay

/**
 * Waits for a recovery deadline and holds a partial wakelock so the wait still
 * elapses with the screen off. The lock is released when the wait ends or is
 * cancelled. It is not held while the link is healthy: the coordinator only
 * waits here when a force-return, unvalidated watchdog, or suggestion retry
 * is pending, and never longer than [SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS].
 *
 * Doze ignores wakelocks unless the user has exempted the app from battery
 * optimization. The settings screen already asks for that exemption.
 */
class DeadlineWait(context: Context) {
    private val power = context.applicationContext.getSystemService(PowerManager::class.java)

    suspend fun await(millis: Long) {
        val wait = millis.coerceIn(0L, SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS)
        if (wait == 0L) return
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(false)
        }
        lock.acquire(wait + SAFETY_MILLIS)
        try {
            delay(wait)
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private companion object {
        const val TAG = "wifipriority:deadline"
        const val SAFETY_MILLIS = 5_000L
    }
}
