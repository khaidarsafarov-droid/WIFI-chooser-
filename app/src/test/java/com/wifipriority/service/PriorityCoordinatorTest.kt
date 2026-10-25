@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wifipriority.service

import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.mock.FakeClock
import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PriorityCoordinatorTest {
    @Test
    fun scriptedDisappearAndReturn_drivesConnector() = runTest(timeout = 5.seconds) {
        val clock = Clock { testScheduler.currentTime }
        val config = enabledConfig()
        val store = InMemoryCredentialStore(config)
        val connector = FakeNetworkConnector(store)
        val status = MutableStateFlow(PriorityStatus())
        val coordinator = PriorityCoordinator(
            scans = MockScanSource.forScenario(MockScenario.PHONE_DISAPPEARS, PHONE_SSID, TRUCK_SSID, clock),
            connector = connector,
            credentials = store,
            engine = PriorityEngine(clock),
            status = status,
        )
        coordinator.run()
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), connector.appliedTargets())
        assertEquals(WifiTarget.TRUCK, status.value.target)
        assertEquals(TRUCK_SSID, status.value.connectedSsid)
        assertEquals(setOf(TRUCK_SSID), status.value.visibleSsids)
        assertTrue(status.value.wifiEnabled)
    }

    @Test
    fun wifiDisabledSnapshots_doNotAdvanceTheEngine() = runTest {
        val store = InMemoryCredentialStore(enabledConfig())
        val connector = FakeNetworkConnector(store)
        val status = MutableStateFlow(PriorityStatus())
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                emit(ScanSnapshot(emptySet(), 0L, wifiEnabled = false))
                emit(ScanSnapshot(emptySet(), 45_000L, wifiEnabled = false))
                emit(ScanSnapshot(setOf(TRUCK_SSID), 90_000L, wifiEnabled = true))
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = connector,
            credentials = store,
            engine = PriorityEngine(FakeClock()),
            status = status,
        ).run()
        assertEquals(emptyList<WifiTarget>(), connector.appliedTargets())
        assertEquals(WifiTarget.NONE, status.value.target)
        assertTrue(status.value.wifiEnabled)
    }

    @Test
    fun disabledConfig_doesNotApply() = runTest(timeout = 5.seconds) {
        val clock = Clock { testScheduler.currentTime }
        val store = InMemoryCredentialStore(enabledConfig(enabled = false))
        val connector = FakeNetworkConnector(store)
        val status = MutableStateFlow(PriorityStatus())
        PriorityCoordinator(
            scans = MockScanSource.forScenario(MockScenario.BOTH_VISIBLE, PHONE_SSID, TRUCK_SSID, clock),
            connector = connector,
            credentials = store,
            engine = PriorityEngine(clock),
            status = status,
            approval = flowOf(SuggestionApproval.PENDING),
        ).run()
        assertEquals(emptyList<WifiTarget>(), connector.appliedTargets())
        assertFalse(status.value.enabled)
        assertEquals(SuggestionApproval.PENDING, status.value.suggestionApproval)
    }

    @Test
    fun shouldStartService_followsEnabledToggle() {
        assertTrue(shouldStartService(true))
        assertFalse(shouldStartService(false))
    }
}
