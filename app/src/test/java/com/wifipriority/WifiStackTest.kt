package com.wifipriority

import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.wifi.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiStackTest {
    @Test
    fun useMockWifi_defaultsToTrue() {
        assertTrue(BuildConfig.USE_MOCK_WIFI)
    }

    @Test
    fun factories_returnMockImplementations() {
        val clock = WallClock
        val source = WifiStack.scanSource(clock, MockScenario.BOTH_VISIBLE, PHONE_SSID, TRUCK_SSID)
        val store = WifiStack.credentialStore(enabledConfig())
        val connector = WifiStack.networkConnector(store)
        assertTrue(source is MockScanSource)
        assertTrue(store is InMemoryCredentialStore)
        assertTrue(connector is FakeNetworkConnector)
        assertEquals(ScanFrames.BOTH, (source as MockScanSource).frames.first())
    }
}

private object ScanFrames {
    val BOTH = setOf(PHONE_SSID, TRUCK_SSID)
}
