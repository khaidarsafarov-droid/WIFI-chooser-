package com.wifipriority.wifi

import com.wifipriority.mock.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanGateTest {
    @Test
    fun wifiOffOrMissingPermission_doesNotConsumeThrottle() {
        val throttle = ScanThrottle(FakeClock())
        repeat(6) {
            assertFalse(WifiScanGate.allow(wifiEnabled = false, hasPermissions = true, throttle))
            assertFalse(WifiScanGate.allow(wifiEnabled = true, hasPermissions = false, throttle))
        }
        repeat(ScanPolicy.THROTTLE_MAX_SCANS) {
            assertTrue(WifiScanGate.allow(wifiEnabled = true, hasPermissions = true, throttle))
        }
        assertFalse(WifiScanGate.allow(wifiEnabled = true, hasPermissions = true, throttle))
    }
}
