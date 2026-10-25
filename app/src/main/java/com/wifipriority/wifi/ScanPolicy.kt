package com.wifipriority.wifi

/**
 * Scan cadence and safety limits agreed for this app.
 *
 * A 45-second cadence produces at most three scans inside any two-minute window,
 * which sits under [THROTTLE_MAX_SCANS]. The throttle still rejects a faster burst.
 */
object ScanPolicy {
    const val INTERVAL_MILLIS: Long = 45_000L

    const val THROTTLE_MAX_SCANS: Int = 4

    const val THROTTLE_WINDOW_MILLIS: Long = 2 * 60 * 1000L

    /** Phone must be missing this many scans in a row before falling back to the truck. */
    const val PHONE_MISSES_BEFORE_TRUCK: Int = 2

    /**
     * `getScanResults()` newer than this can still drive a decision when `startScan`
     * was refused. Older than this, the sighting is stale.
     */
    const val CACHED_RESULT_MAX_AGE_MILLIS: Long = 2 * 60 * 1000L

    /** How long refused scans may keep the last fresh sighting before it is dropped. */
    const val STALE_AFTER_MILLIS: Long = 2 * 60 * 1000L
}
