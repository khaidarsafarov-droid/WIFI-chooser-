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
    /**
     * False when `startScan` was refused, the platform said results were not updated,
     * or the app throttle skipped the scan. A false snapshot counts as a miss only
     * when [resultsAgeMillis] says `getScanResults()` is still new enough to trust.
     */
    val fresh: Boolean = true,
    /** Why [fresh] is false. Null on a fresh scan. */
    val refusal: ScanRefusal? = null,
    /**
     * Age of the newest `ScanResult.timestamp` at this attempt.
     * Null when the platform list could not be read.
     */
    val resultsAgeMillis: Long? = null,
    /** `WifiManager.isScanThrottleEnabled` at the attempt. Null off-device. */
    val platformThrottle: Boolean? = null,
)

/** Why this loop did not get a new scan. */
enum class ScanRefusal {
    THROTTLE,
    PERMISSION,
    START_FAILED,
    NOT_UPDATED,
    TIMEOUT,
}

/**
 * `ScanResult.timestamp` is microseconds since boot. [elapsedRealtimeMillis] is
 * `SystemClock.elapsedRealtime()`.
 */
fun newestScanAgeMillis(elapsedRealtimeMillis: Long, timestampsMicros: List<Long>): Long? {
    val newest = timestampsMicros.maxOrNull() ?: return null
    return elapsedRealtimeMillis - newest / 1_000L
}

/** A cached platform scan young enough to drive a decision. */
fun cachedResultsUsable(
    ageMillis: Long?,
    maxAgeMillis: Long = ScanPolicy.CACHED_RESULT_MAX_AGE_MILLIS,
): Boolean = ageMillis != null && ageMillis >= 0L && ageMillis <= maxAgeMillis

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
