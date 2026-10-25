package com.wifipriority.mock

import app.cash.turbine.test
import com.wifipriority.PHONE_PASSWORD
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_PASSWORD
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import com.wifipriority.wifi.NetworkCredential
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCollaboratorsTest {
    @Test
    fun saveAndLoad_roundTripsSsidsAndPasswords() = runTest {
        val store = InMemoryCredentialStore()
        val saved = enabledConfig()
        store.save(saved)
        assertEquals(saved, store.load())
        assertEquals(PHONE_SSID, store.load().phoneSsid())
        assertEquals(TRUCK_SSID, store.load().truckSsid())
        assertEquals(PHONE_PASSWORD, store.load().phone?.password)
        assertEquals(TRUCK_PASSWORD, store.load().truck?.password)
    }

    @Test
    fun configFlow_emitsSavedConfig() = runTest {
        val store = InMemoryCredentialStore()
        store.config.test {
            assertFalse(awaitItem().enabled)
            val saved = enabledConfig()
            store.save(saved)
            assertEquals(saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toString_redactsPasswords() {
        val credential = NetworkCredential(PHONE_SSID, "super-secret-value")
        val rendered = credential.toString()
        assertTrue(rendered.contains(PHONE_SSID))
        assertFalse(rendered.contains("super-secret-value"))
        val config = enabledConfig(phonePassword = "super-secret-value", truckPassword = "other-secret")
        assertFalse(config.toString().contains("super-secret-value"))
        assertFalse(config.toString().contains("other-secret"))
    }

    @Test
    fun applyPhone_reportsPhoneSsid() = runTest {
        val connector = FakeNetworkConnector(InMemoryCredentialStore(enabledConfig()))
        connector.applyTarget(WifiTarget.PHONE)
        assertEquals(PHONE_SSID, connector.connectedSsid.first())
        assertEquals(listOf(WifiTarget.PHONE), connector.appliedTargets())
    }

    @Test
    fun applyTruckThenNone_clearsSsid() = runTest {
        val connector = FakeNetworkConnector(InMemoryCredentialStore(enabledConfig()))
        connector.applyTarget(WifiTarget.TRUCK)
        assertEquals(TRUCK_SSID, connector.connectedSsid.first())
        connector.applyTarget(WifiTarget.NONE)
        assertNull(connector.connectedSsid.first())
        assertEquals(listOf(WifiTarget.TRUCK, WifiTarget.NONE), connector.appliedTargets())
    }

    @Test
    fun fakeClock_advancesOnlyWhenAsked() {
        val clock = FakeClock(50L)
        assertEquals(50L, clock.nowMillis())
        clock.advanceBy(45_000L)
        assertEquals(45_050L, clock.nowMillis())
        clock.set(10L)
        assertEquals(10L, clock.nowMillis())
    }
}
