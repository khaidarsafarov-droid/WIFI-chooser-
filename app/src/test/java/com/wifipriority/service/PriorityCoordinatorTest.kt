@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wifipriority.service

import com.wifipriority.PHONE_PASSWORD
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
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PolicyState
import com.wifipriority.wifi.SuggestionApplyReport
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.SuggestionCommit
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
    fun emptySystemList_readdsDespiteSavedSignature() = runTest {
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        val policy = MemoryPolicyStateStore()
        policy.save(PolicyState(suggestionSignature = "PHONE:$PHONE_SSID:WPA2,TRUCK:$TRUCK_SSID:WPA2", suggestionsInSystem = true))
        val connector = InventoryConnector(emptySet())
        val status = MutableStateFlow(PriorityStatus())
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                emit(ScanSnapshot(setOf(PHONE_SSID, TRUCK_SSID), 0L))
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = connector,
            credentials = credentials,
            engine = PriorityEngine(FakeClock()),
            status = status,
            policyStore = policy,
            eventLog = events,
        ).run()
        assertTrue(connector.forces.isNotEmpty())
        assertTrue(connector.forces.all { it })
        assertTrue(status.value.suggestionsMissing)
        assertFalse(policy.load().suggestionsInSystem)
        val text = events.recent.value.joinToString("\n") { "${it.kind} ${it.detail}" }
        assertTrue(text.contains("SUGGESTION_STATUS"))
        assertTrue(text.contains("missing="))
        assertTrue(text.contains("SCAN"))
        assertTrue(text.contains("DECISION"))
        assertFalse(text.contains(PHONE_PASSWORD))
    }

    @Test
    fun refusedScan_doesNotCountAsAPhoneMiss() = runTest {
        val credentials = InMemoryCredentialStore(enabledConfig())
        val connector = FakeNetworkConnector(credentials)
        val status = MutableStateFlow(PriorityStatus())
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                emit(ScanSnapshot(setOf(PHONE_SSID), 0L))
                emit(ScanSnapshot(emptySet(), 45_000L, fresh = false))
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = connector,
            credentials = credentials,
            engine = PriorityEngine(FakeClock()),
            status = status,
        ).run()
        assertEquals(WifiTarget.PHONE, status.value.target)
        assertEquals(setOf(PHONE_SSID), status.value.visibleSsids)
    }

    @Test
    fun shouldStartService_followsEnabledToggle() {
        assertTrue(shouldStartService(true))
        assertFalse(shouldStartService(false))
    }
}

private class InventoryConnector(
    private val registered: Set<String>,
) : NetworkConnector {
    val forces = mutableListOf<Boolean>()
    private val connected = MutableStateFlow<String?>(null)

    override val connectedSsid: Flow<String?> = connected

    override suspend fun registeredSsids(): Set<String> = registered

    override suspend fun applyTarget(target: WifiTarget) = Unit

    override suspend fun applySuggestions(
        suggestions: List<com.wifipriority.wifi.PlannedSuggestion>,
        disconnectSsids: Set<String>,
        force: Boolean,
    ): SuggestionApplyReport {
        forces += force
        return SuggestionApplyReport(
            commit = SuggestionCommit.APPLIED,
            addStatus = 0,
            registeredSsids = registered,
        )
    }
}
