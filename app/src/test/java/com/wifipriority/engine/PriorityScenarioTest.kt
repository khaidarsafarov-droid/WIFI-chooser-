@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wifipriority.engine

import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the scripted mock scans through [PriorityEngine] on a virtual clock,
 * then applies only the decisions that ask for a connector update.
 */
class PriorityScenarioTest {
    @Test
    fun scriptedBothVisible_appliesPhoneOnly() = runTest(timeout = 5.seconds) {
        val (steps, connector) = play(MockScenario.BOTH_VISIBLE)
        assertTrue(steps.all { it.decision.target == WifiTarget.PHONE })
        assertEquals(listOf(WifiTarget.PHONE), connector.appliedTargets())
        assertEquals(PHONE_SSID, connector.connectedSsid.first())
    }

    @Test
    fun scriptedPhoneDisappears_truckWithinTwoScans() = runTest(timeout = 5.seconds) {
        val (steps, connector) = play(MockScenario.PHONE_DISAPPEARS)
        assertEquals(WifiTarget.PHONE, steps[0].decision.target)
        assertEquals(WifiTarget.PHONE, steps[1].decision.target)
        assertFalse(steps[1].decision.shouldApply)
        assertEquals(WifiTarget.TRUCK, steps[2].decision.target)
        assertTrue(steps[2].decision.shouldApply)
        val elapsed = steps[2].decision.lastSwitchTimeMillis!! - steps[0].decision.lastSwitchTimeMillis!!
        assertEquals(2 * ScanPolicy.INTERVAL_MILLIS, elapsed)
        assertTrue(elapsed <= 2 * 60 * 1000L)
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), connector.appliedTargets())
        assertEquals(TRUCK_SSID, connector.connectedSsid.first())
    }

    @Test
    fun scriptedPhoneReturns_phoneOnNextScan() = runTest(timeout = 5.seconds) {
        val (steps, connector) = play(MockScenario.PHONE_RETURNS)
        assertEquals(WifiTarget.TRUCK, steps[1].decision.target)
        assertEquals(WifiTarget.PHONE, steps[2].decision.target)
        assertTrue(steps[2].decision.shouldApply)
        val elapsed = steps[2].decision.lastSwitchTimeMillis!! - steps[1].decision.lastSwitchTimeMillis!!
        assertEquals(ScanPolicy.INTERVAL_MILLIS, elapsed)
        assertTrue(elapsed <= 60_000L)
        assertEquals(listOf(WifiTarget.TRUCK, WifiTarget.PHONE), connector.appliedTargets())
        assertEquals(PHONE_SSID, connector.connectedSsid.first())
    }

    @Test
    fun scriptedFlapping_neverAppliesTruck() = runTest(timeout = 5.seconds) {
        val (steps, connector) = play(MockScenario.PHONE_FLAPPING)
        assertTrue(steps.all { it.decision.target == WifiTarget.PHONE })
        assertFalse(connector.appliedTargets().contains(WifiTarget.TRUCK))
        assertEquals(listOf(WifiTarget.PHONE), connector.appliedTargets())
        assertEquals(PHONE_SSID, connector.connectedSsid.first())
    }

    @Test
    fun scriptedNothingVisible_appliesNothing() = runTest(timeout = 5.seconds) {
        val (steps, connector) = play(MockScenario.NOTHING_VISIBLE)
        assertTrue(steps.all { it.decision.target == WifiTarget.NONE && !it.decision.shouldApply })
        assertEquals(emptyList<WifiTarget>(), connector.appliedTargets())
        assertNull(connector.connectedSsid.first())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.play(
        scenario: MockScenario,
    ): Pair<List<EngineStep>, FakeNetworkConnector> {
        val clock = Clock { testScheduler.currentTime }
        val config = enabledConfig()
        val store = InMemoryCredentialStore(config)
        val connector = FakeNetworkConnector(store)
        val engine = PriorityEngine(clock)
        val source = MockScanSource.forScenario(scenario, PHONE_SSID, TRUCK_SSID, clock)
        val steps = mutableListOf<EngineStep>()
        var state = EngineState()
        source.scans.collect { snapshot ->
            val step = engine.onScan(state, snapshot, config)
            state = step.state
            steps += step
            if (step.decision.shouldApply) {
                connector.applyTarget(step.decision.target)
            }
        }
        return steps to connector
    }
}
