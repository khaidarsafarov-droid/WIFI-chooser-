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
)

/**
 * Source of scan snapshots.
 *
 * The stage-2 implementation will call `WifiManager.startScan()` on the
 * [ScanPolicy.INTERVAL_MILLIS] cadence (a deliberate, documented exception to
 * the platform's scan-throttling guidance; the user turns system throttling off
 * in developer options) and will emit `SCAN_RESULTS_AVAILABLE_ACTION` results.
 * [ScanThrottle] still caps the app at [ScanPolicy.THROTTLE_MAX_SCANS] scans
 * per [ScanPolicy.THROTTLE_WINDOW_MILLIS]. Stage 1 does not call `startScan`.
 */
interface ScanSource {
    val scans: Flow<ScanSnapshot>
}
