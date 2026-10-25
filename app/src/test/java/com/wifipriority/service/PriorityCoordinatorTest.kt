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
import com.wifipriority.wifi.LinkObservation
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PlannedSuggestion
import com.wifipriority.wifi.PolicyState
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.ScanRefusal
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.ScanSource
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
    fun persistedSignature_unknownApproval_emptyList_callsAdd() = runTest {
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        val policy = MemoryPolicyStateStore()
        policy.save(PolicyState(
            suggestionSignature = "PHONE:$PHONE_SSID:WPA2,TRUCK:$TRUCK_SSID:WPA2",
            suggestionsInSystem = true,
        ))
        val connector = InventoryConnector(emptySet())
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
            status = MutableStateFlow(PriorityStatus()),
            approval = flowOf(SuggestionApproval.UNKNOWN),
            policyStore = policy,
            eventLog = events,
        ).run()
        assertTrue(connector.forces.isNotEmpty())
        assertTrue(connector.forces.all { it })
        val statusLines = events.recent.value.filter { it.kind.name == "SUGGESTION_STATUS" }
        assertTrue(statusLines.isNotEmpty())
        assertTrue(statusLines.all { it.detail.contains("add=") && it.detail.contains("registered=") })
        assertTrue(statusLines.any { it.detail.contains("add=0") })
        assertFalse(events.recent.value.joinToString("\n") { it.detail }.contains(PHONE_PASSWORD))
    }

    @Test
    fun exceptionDuringCycle_isLoggedAndTheNextScanStillAdds() = runTest {
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        var calls = 0
        val connector = object : NetworkConnector {
            private val connected = MutableStateFlow<String?>(null)
            override val connectedSsid: Flow<String?> = connected
            override suspend fun registeredSsids(): Set<String> = emptySet()
            override suspend fun applyTarget(target: WifiTarget) = Unit
            override suspend fun applySuggestions(
                suggestions: List<com.wifipriority.wifi.PlannedSuggestion>,
                disconnectSsids: Set<String>,
                force: Boolean,
            ): SuggestionApplyReport {
                calls += 1
                if (calls == 1) throw IllegalStateException("failed $PHONE_PASSWORD")
                return SuggestionApplyReport(
                    commit = SuggestionCommit.APPLIED,
                    addStatus = 0,
                    registeredSsids = emptySet(),
                )
            }
        }
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                emit(ScanSnapshot(setOf(PHONE_SSID, TRUCK_SSID), 0L))
                emit(ScanSnapshot(setOf(PHONE_SSID, TRUCK_SSID), 45_000L))
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = connector,
            credentials = credentials,
            engine = PriorityEngine(FakeClock()),
            status = MutableStateFlow(PriorityStatus()),
            approval = flowOf(SuggestionApproval.UNKNOWN),
            eventLog = events,
        ).run()
        assertTrue(calls >= 2)
        val text = events.recent.value.joinToString("\n") { "${it.kind} ${it.detail}" }
        assertTrue(text.contains("CYCLE_ERROR"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("add=0"))
        assertTrue(text.contains("registered="))
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
    fun connectedToPhone_doesNotRemoveTheTruck() = runTest {
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        val connector = object : NetworkConnector {
            private val connected = MutableStateFlow<String?>(PHONE_SSID)
            override val connectedSsid: Flow<String?> = connected
            override val link: Flow<LinkObservation> = MutableStateFlow(
                LinkObservation(PHONE_SSID, validated = true, fromOurSuggestion = true),
            )
            val planned = mutableListOf<List<PlannedSuggestion>>()
            override suspend fun applyTarget(target: WifiTarget) = Unit
            override suspend fun registeredSsids(): Set<String> = planned.lastOrNull()?.map { it.ssid }?.toSet().orEmpty()
            override suspend fun applySuggestions(
                suggestions: List<PlannedSuggestion>,
                disconnectSsids: Set<String>,
                force: Boolean,
            ): SuggestionApplyReport {
                planned += suggestions
                return SuggestionApplyReport(
                    addStatus = 0,
                    registeredSsids = suggestions.map { it.ssid }.toSet(),
                )
            }
        }
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
            status = MutableStateFlow(PriorityStatus()),
            eventLog = events,
        ).run()
        val text = events.recent.value.joinToString("\n") { "${it.kind} ${it.detail}" }
        assertFalse(text.contains("FORCE_RETURN_START"))
        assertTrue(connector.planned.last().any { it.role == WifiTarget.TRUCK })
        assertTrue(connector.planned.last().any { it.role == WifiTarget.PHONE })
    }

    @Test
    fun refusedForTwoMinutes_withOldResults_clearsVisibilityWithoutAPhoneMiss() = runTest {
        val clock = FakeClock()
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        val status = MutableStateFlow(PriorityStatus())
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                clock.set(0L)
                emit(ScanSnapshot(setOf(PHONE_SSID, TRUCK_SSID), 0L))
                clock.set(ScanPolicy.STALE_AFTER_MILLIS)
                emit(
                    ScanSnapshot(
                        emptySet(),
                        ScanPolicy.STALE_AFTER_MILLIS,
                        fresh = false,
                        refusal = ScanRefusal.START_FAILED,
                        resultsAgeMillis = ScanPolicy.CACHED_RESULT_MAX_AGE_MILLIS + 5_000,
                        platformThrottle = false,
                    ),
                )
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = FakeNetworkConnector(credentials),
            credentials = credentials,
            engine = PriorityEngine(clock),
            status = status,
            clock = clock,
            eventLog = events,
        ).run()
        assertEquals(WifiTarget.PHONE, status.value.target)
        assertEquals(emptySet<String>(), status.value.visibleSsids)
        val text = events.recent.value.joinToString("\n") { "${it.kind} ${it.detail}" }
        assertTrue(text.contains("SCAN_STALE"))
        assertTrue(text.contains("START_FAILED"))
        assertTrue(text.contains("platformThrottle=false"))
        assertTrue(text.contains("held"))
        assertFalse(text.contains("PHONE_ABSENT"))
    }

    @Test
    fun youngCachedResults_driveTheDecisionWhenStartScanIsRefused() = runTest {
        val clock = FakeClock()
        val credentials = InMemoryCredentialStore(enabledConfig())
        val events = MemoryEventLog()
        val status = MutableStateFlow(PriorityStatus())
        val scans = object : ScanSource {
            override val scans: Flow<ScanSnapshot> = flow {
                clock.set(0L)
                emit(ScanSnapshot(setOf(PHONE_SSID, TRUCK_SSID), 0L))
                clock.set(45_000L)
                emit(
                    ScanSnapshot(
                        setOf(TRUCK_SSID),
                        45_000L,
                        fresh = false,
                        refusal = ScanRefusal.NOT_UPDATED,
                        resultsAgeMillis = 5_000L,
                    ),
                )
            }
        }
        PriorityCoordinator(
            scans = scans,
            connector = FakeNetworkConnector(credentials),
            credentials = credentials,
            engine = PriorityEngine(clock),
            status = status,
            clock = clock,
            eventLog = events,
        ).run()
        assertEquals(WifiTarget.PHONE, status.value.target)
        assertEquals(setOf(TRUCK_SSID), status.value.visibleSsids)
        val text = events.recent.value.joinToString("\n") { "${it.kind} ${it.detail}" }
        assertTrue(text.contains("cached"))
        assertTrue(text.contains("NOT_UPDATED"))
        assertFalse(text.contains("SCAN_STALE"))
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
