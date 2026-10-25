@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wifipriority.service

import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.LinkObservation
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PlannedSuggestion
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.SuggestionCommit
import com.wifipriority.wifi.SuggestionPlanner
import com.wifipriority.wifi.WifiTarget
import com.wifipriority.wifi.suggestionAddKind
import com.wifipriority.wifi.suggestionCommitFor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggestion deadlines are only useful if the coordinator evaluates them
 * without a fresh scan or a link change. Scans often emit nothing
 * (`startScan` refused, results not updated, permission gate), and a stable
 * link does not emit again.
 */
class PriorityCoordinatorRecoveryTest {
    @Test
    fun forceReturn_reregistersTruckWhenScansStop() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val connector = RecordingConnector(
            LinkObservation(TRUCK_SSID, validated = true, fromOurSuggestion = true),
        )
        val job = launch {
            coordinator(clock, connector, hanging(setOf(PHONE_SSID, TRUCK_SSID), clock)).run()
        }
        runCurrent()
        assertTrue(connector.applied.last().none { it.role == WifiTarget.TRUCK })
        advanceTimeBy(SuggestionPlanner.FORCE_RETURN_MAX_MILLIS)
        runCurrent()
        assertTrue(connector.applied.last().any { it.role == WifiTarget.TRUCK && it.ssid == TRUCK_SSID })
        job.cancel()
    }

    @Test
    fun unvalidatedPhone_releasesPhoneWithoutAnotherScan() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val connector = RecordingConnector(
            LinkObservation(PHONE_SSID, validated = false, fromOurSuggestion = true),
        )
        val job = launch {
            coordinator(clock, connector, hanging(setOf(PHONE_SSID, TRUCK_SSID), clock)).run()
        }
        runCurrent()
        advanceTimeBy(SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS)
        runCurrent()
        assertTrue(connector.disconnects.last().contains(PHONE_SSID))
        assertTrue(connector.applied.last().none { it.role == WifiTarget.PHONE })
        assertTrue(connector.applied.last().any { it.role == WifiTarget.TRUCK })
        job.cancel()
    }

    @Test
    fun truckPasswordChange_reappliesWithoutAnotherScan() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val store = InMemoryCredentialStore(enabledConfig())
        val connector = RecordingConnector(
            LinkObservation(PHONE_SSID, validated = true, fromOurSuggestion = true),
        )
        val job = launch {
            coordinator(clock, connector, hanging(setOf(PHONE_SSID, TRUCK_SSID), clock), store).run()
        }
        runCurrent()
        val updated = "new-truck-password"
        store.save(enabledConfig(truckPassword = updated))
        runCurrent()
        val truck = connector.applied.last().filter { it.role == WifiTarget.TRUCK }
        assertTrue(truck.isNotEmpty())
        assertEquals(updated, truck.first().passphrase)
        assertTrue(connector.applied.last().joinToString { it.passphrase }.let { joined ->
            // The recorded list is the in-memory plan, not the event log.
            joined.contains(updated)
        })
        job.cancel()
    }

    @Test
    fun approvalGranted_reappliesSuggestions() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val connector = RecordingConnector(
            LinkObservation(PHONE_SSID, validated = true, fromOurSuggestion = true),
        )
        val approval = MutableStateFlow(SuggestionApproval.REJECTED)
        val job = launch {
            coordinator(
                clock,
                connector,
                hanging(setOf(PHONE_SSID, TRUCK_SSID), clock),
                approval = approval,
            ).run()
        }
        runCurrent()
        val before = connector.applied.size
        approval.value = SuggestionApproval.APPROVED
        runCurrent()
        assertTrue(connector.applied.size > before)
        assertTrue(connector.applied.last().any { it.role == WifiTarget.TRUCK })
        job.cancel()
    }

    @Test
    fun failedSuggestionAdd_retriesWithoutAnotherScan() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val connector = RecordingConnector(
            LinkObservation(PHONE_SSID, validated = true, fromOurSuggestion = true),
            addStatusCode = 1,
        )
        val job = launch {
            coordinator(clock, connector, hanging(setOf(PHONE_SSID, TRUCK_SSID), clock)).run()
        }
        runCurrent()
        val before = connector.applied.size
        advanceTimeBy(SuggestionPlanner.SUGGESTION_RETRY_MILLIS)
        runCurrent()
        assertTrue(connector.applied.size > before)
        assertTrue(connector.applied.last().any { it.role == WifiTarget.TRUCK })
        job.cancel()
    }

    private fun coordinator(
        clock: Clock,
        connector: RecordingConnector,
        scans: ScanSource,
        store: InMemoryCredentialStore = InMemoryCredentialStore(enabledConfig()),
        approval: Flow<SuggestionApproval> = kotlinx.coroutines.flow.flowOf(SuggestionApproval.UNKNOWN),
    ) = PriorityCoordinator(
        scans = scans,
        connector = connector,
        credentials = store,
        engine = PriorityEngine(clock),
        status = MutableStateFlow(PriorityStatus()),
        approval = approval,
        clock = clock,
    )

    private fun hanging(visible: Set<String>, clock: Clock): ScanSource = object : ScanSource {
        override val scans: Flow<ScanSnapshot> = kotlinx.coroutines.flow.flow {
            emit(ScanSnapshot(visible, clock.nowMillis()))
            awaitCancellation()
        }
    }
}

private class RecordingConnector(
    initial: LinkObservation,
    private val addStatusCode: Int? = null,
) : NetworkConnector {
    private val linkState = MutableStateFlow(initial)
    override val link: Flow<LinkObservation> = linkState.asStateFlow()
    override val connectedSsid: Flow<String?> = linkState.map { it.connectedSsid }
    override val suggestionAddStatus: Flow<Int?> = MutableStateFlow(addStatusCode)

    val applied = mutableListOf<List<PlannedSuggestion>>()
    val disconnects = mutableListOf<Set<String>>()

    override suspend fun applyTarget(target: WifiTarget) = Unit

    override suspend fun applySuggestions(
        suggestions: List<PlannedSuggestion>,
        disconnectSsids: Set<String>,
        force: Boolean,
    ): SuggestionCommit {
        applied += suggestions
        disconnects += disconnectSsids
        val kind = addStatusCode?.let(::suggestionAddKind) ?: return SuggestionCommit.APPLIED
        return suggestionCommitFor(kind, removedOnly = false)
    }
}
