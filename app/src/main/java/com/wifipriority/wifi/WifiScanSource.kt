package com.wifipriority.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Scans on [ScanPolicy.INTERVAL_MILLIS]. `startScan()` is deprecated; this app
 * calls it anyway because scan throttling is turned off in developer options.
 * [ScanThrottle] still allows at most 4 scans in any 2 minutes.
 *
 * SSIDs come from [ScanResult.getWifiSsid], not the deprecated `SSID` field.
 * When the radio is off, snapshots are marked `wifiEnabled = false` and the
 * engine is not advanced.
 */
class WifiScanSource(
    context: Context,
    private val clock: Clock,
    private val throttle: ScanThrottle = ScanThrottle(clock),
    private val intervalMillis: Long = ScanPolicy.INTERVAL_MILLIS,
    private val scanTimeoutMillis: Long = SCAN_TIMEOUT_MILLIS,
) : ScanSource {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    override val scans: Flow<ScanSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            val radioOn = wifiManager.isWifiEnabled
            if (!radioOn) {
                emit(ScanSnapshot(emptySet(), clock.nowMillis(), wifiEnabled = false))
                delay(intervalMillis)
                continue
            }
            val permitted = hasScanPermissions()
            if (!WifiScanGate.allow(wifiEnabled = true, hasPermissions = permitted, throttle = throttle)) {
                emit(ScanSnapshot(emptySet(), clock.nowMillis(), wifiEnabled = true, fresh = false))
                delay(intervalMillis)
                continue
            }
            val read = awaitFreshResults()
            if (read != null) {
                emit(
                    ScanSnapshot(
                        visibleSsids = read.ssids,
                        timestampMillis = clock.nowMillis(),
                        wifiEnabled = true,
                        securityBySsid = read.security,
                    ),
                )
            } else {
                emit(ScanSnapshot(emptySet(), clock.nowMillis(), wifiEnabled = true, fresh = false))
            }
            delay(intervalMillis)
        }
    }

    private fun hasScanPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Registers for [WifiManager.SCAN_RESULTS_AVAILABLE_ACTION] before the scan
     * so the result cannot be missed, then reads [WifiManager.getScanResults].
     * Returns null when the platform refused the scan or the results are stale.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFreshResults(): ScanRead? {
        val fresh = withTimeoutOrNull(scanTimeoutMillis) {
            suspendCancellableCoroutine { cont ->
                lateinit var receiver: BroadcastReceiver
                fun finish(read: ScanRead?) {
                    unregister(receiver)
                    if (cont.isActive) cont.resume(read)
                }
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                        val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                        finish(if (updated) readScan() else null)
                    }
                }
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                cont.invokeOnCancellation { unregister(receiver) }
                val started = startScan()
                if (!started) finish(null)
            }
        }
        return fresh
    }

    /**
     * Deliberate use of the deprecated scan API. The platform throttles
     * [WifiManager.startScan]; this sideloaded tablet disables that throttle
     * in developer options and still passes through [ScanThrottle].
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun startScan(): Boolean {
        return try {
            wifiManager.startScan()
        } catch (error: SecurityException) {
            Log.w(TAG, "startScan was rejected")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun readScan(): ScanRead {
        return try {
            val security = linkedMapOf<String, WifiSecurity>()
            val ssids = linkedSetOf<String>()
            for (result in wifiManager.scanResults) {
                val ssid = result.visibleSsid() ?: continue
                ssids += ssid
                security[ssid] = mergeSecurity(security[ssid], securityFromCapabilities(result.capabilities))
            }
            ScanRead(ssids, security)
        } catch (error: SecurityException) {
            Log.w(TAG, "getScanResults was rejected")
            ScanRead(emptySet(), emptyMap())
        }
    }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    private companion object {
        const val TAG = "WifiScanSource"
        const val SCAN_TIMEOUT_MILLIS = 10_000L
        val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
        )
    }
}

private data class ScanRead(
    val ssids: Set<String>,
    val security: Map<String, WifiSecurity>,
)

private fun ScanResult.visibleSsid(): String? {
    val wifiSsid = wifiSsid ?: return null
    return ssidFromOctets(wifiSsid.bytes, wifiSsid.toString())
}
