package com.wifipriority.wifi

/**
 * Sliding-window guard: at most [maxScans] acquisitions inside [windowMillis].
 *
 * A scan whose age is greater than or equal to [windowMillis] no longer counts.
 * [tryAcquire] records the attempt only when it is allowed. The stage-2 scanner
 * calls this before `WifiManager.startScan()`.
 */
class ScanThrottle(
    private val clock: Clock,
    private val maxScans: Int = ScanPolicy.THROTTLE_MAX_SCANS,
    private val windowMillis: Long = ScanPolicy.THROTTLE_WINDOW_MILLIS,
) {
    init {
        require(maxScans > 0) { "maxScans must be positive" }
        require(windowMillis > 0L) { "windowMillis must be positive" }
    }

    private val attempts = ArrayDeque<Long>()

    fun tryAcquire(): Boolean = synchronized(attempts) {
        val now = clock.nowMillis()
        while (attempts.isNotEmpty() && now - attempts.first() >= windowMillis) {
            attempts.removeFirst()
        }
        if (attempts.size >= maxScans) {
            false
        } else {
            attempts.addLast(now)
            true
        }
    }

    /** Acquisitions still inside the window. Visible for tests. */
    fun size(): Int = synchronized(attempts) {
        val now = clock.nowMillis()
        while (attempts.isNotEmpty() && now - attempts.first() >= windowMillis) {
            attempts.removeFirst()
        }
        attempts.size
    }
}
