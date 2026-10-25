package com.wifipriority.wifi

import kotlinx.coroutines.flow.Flow

/**
 * One Wi-Fi scan. [visibleSsids] is the set of SSIDs observed in that scan.
 * Decisions use visibility only, not signal strength.
 *
 * [timestampMillis] comes from the same [Clock] the rest of the stack uses.
 */
data class ScanSnapshot(
    val visibleSsids: Set<String>,
    val timestampMillis: Long,
    /** False when the radio is off. Those snapshots must not advance the engine. */
    val wifiEnabled: Boolean = true,
    /** Security parsed from scan capabilities, keyed by SSID. Empty for mocks. */
    val securityBySsid: Map<String, WifiSecurity> = emptyMap(),
)

/**
 * Source of scan snapshots.
 *
 * [com.wifipriority.wifi.WifiScanSource] calls `WifiManager.startScan()` on the
 * [ScanPolicy.INTERVAL_MILLIS] cadence. That call is deprecated; it is a deliberate
 * exception because the user turns system scan throttling off in developer options.
 * Results arrive through `SCAN_RESULTS_AVAILABLE_ACTION` and `getScanResults()`.
 * [ScanThrottle] still caps the app at [ScanPolicy.THROTTLE_MAX_SCANS] scans per
 * [ScanPolicy.THROTTLE_WINDOW_MILLIS].
 */
interface ScanSource {
    val scans: Flow<ScanSnapshot>
}
