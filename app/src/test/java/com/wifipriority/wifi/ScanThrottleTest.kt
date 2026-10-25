package com.wifipriority.wifi

import com.wifipriority.mock.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanThrottleTest {
    private val clock = FakeClock()
    private val throttle = ScanThrottle(clock)

    @Test
    fun allowsAtMostFourScansPerTwoMinutes() {
        val granted = mutableListOf<Long>()
        val attempts = listOf(
            0L,
            10_000L,
            20_000L,
            30_000L,
            40_000L,
            50_000L,
            120_000L,
            130_000L,
        )
        for (time in attempts) {
            clock.set(time)
            if (throttle.tryAcquire()) {
                granted += time
            }
        }

        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L, 120_000L, 130_000L), granted)
        for (time in granted) {
            val inWindow = granted.count { earlier -> time - earlier in 0 until ScanPolicy.THROTTLE_WINDOW_MILLIS }
            assertTrue("window ending at $time held $inWindow scans", inWindow <= ScanPolicy.THROTTLE_MAX_SCANS)
        }
        assertTrue(granted.size > ScanPolicy.THROTTLE_MAX_SCANS)
    }

    @Test
    fun fifthScanInsideWindow_isRejected() {
        repeat(ScanPolicy.THROTTLE_MAX_SCANS) { index ->
            clock.set(index * 1_000L)
            assertTrue(throttle.tryAcquire())
        }
        clock.set(ScanPolicy.THROTTLE_MAX_SCANS * 1_000L)
        assertFalse(throttle.tryAcquire())
        assertEquals(ScanPolicy.THROTTLE_MAX_SCANS, throttle.size())
    }

    @Test
    fun scanExactlyTwoMinutesOld_noLongerCounts() {
        repeat(ScanPolicy.THROTTLE_MAX_SCANS) {
            clock.set(0L)
            assertTrue(throttle.tryAcquire())
        }
        clock.set(0L)
        assertFalse(throttle.tryAcquire())

        clock.set(ScanPolicy.THROTTLE_WINDOW_MILLIS - 1)
        assertFalse(throttle.tryAcquire())

        clock.set(ScanPolicy.THROTTLE_WINDOW_MILLIS)
        assertTrue(throttle.tryAcquire())
        assertEquals(1, throttle.size())
    }
}
