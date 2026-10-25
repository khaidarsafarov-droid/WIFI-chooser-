package com.wifipriority.wifi

/**
 * Decides whether this loop iteration may call `WifiManager.startScan()`.
 * A disabled radio or missing permission does not consume a [ScanThrottle] slot.
 */
object WifiScanGate {
    fun allow(wifiEnabled: Boolean, hasPermissions: Boolean, throttle: ScanThrottle): Boolean {
        if (!wifiEnabled || !hasPermissions) return false
        return throttle.tryAcquire()
    }
}
