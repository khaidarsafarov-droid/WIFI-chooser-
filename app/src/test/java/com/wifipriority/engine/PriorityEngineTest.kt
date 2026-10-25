package com.wifipriority.engine

import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import com.wifipriority.mock.FakeClock
import com.wifipriority.scan
import com.wifipriority.wifi.NetworkCredential
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.WifiTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityEngineTest {
    private val clock = FakeClock()
    private val engine = PriorityEngine(clock)
    private val config = enabledConfig()

    @Test
    fun bothVisible_targetsPhone() {
        val step = engine.scan(
            state = EngineState(),
            clock = clock,
            visible = setOf(PHONE_SSID, TRUCK_SSID),
            config = config,
        )

        assertEquals(WifiTarget.PHONE, step.decision.target)
        assertTrue(step.decision.shouldApply)
        assertEquals(DecisionReason.PHONE_VISIBLE, step.decision.reason)
        assertEquals(0, step.decision.consecutivePhoneMisses)
        assertEquals(0L, step.decision.lastSwitchTimeMillis)

        val again = engine.scan(
            state = step.state,
            clock = clock,
            visible = setOf(PHONE_SSID, TRUCK_SSID),
            config = config,
        )
        assertEquals(WifiTarget.PHONE, again.decision.target)
        assertFalse(again.decision.shouldApply)
    }

    @Test
    fun phoneAbsentForTwoScans_targetsTruckWithinTwoMinutesAt45s() {
        var state = EngineState()
        val first = engine.scan(state, clock, setOf(PHONE_SSID, TRUCK_SSID), config)
        state = first.state
        assertEquals(WifiTarget.PHONE, first.decision.target)

        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        val singleMiss = engine.scan(state, clock, setOf(TRUCK_SSID), config)
        state = singleMiss.state
        assertEquals(WifiTarget.PHONE, singleMiss.decision.target)
        assertFalse(singleMiss.decision.shouldApply)
        assertEquals(DecisionReason.SINGLE_MISS_HOLD, singleMiss.decision.reason)
        assertEquals(1, singleMiss.decision.consecutivePhoneMisses)

        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        val secondMiss = engine.scan(state, clock, setOf(TRUCK_SSID), config)

        assertEquals(WifiTarget.TRUCK, secondMiss.decision.target)
        assertTrue(secondMiss.decision.shouldApply)
        assertEquals(DecisionReason.PHONE_ABSENT_TRUCK_VISIBLE, secondMiss.decision.reason)
        assertEquals(2, secondMiss.decision.consecutivePhoneMisses)
        val elapsed = secondMiss.decision.lastSwitchTimeMillis!! - first.decision.lastSwitchTimeMillis!!
        assertEquals(2 * ScanPolicy.INTERVAL_MILLIS, elapsed)
        assertTrue(elapsed <= 2 * 60 * 1000L)
    }

    @Test
    fun phoneVisibleAgain_targetsPhoneOnNextScanWithinOneMinute() {
        var state = EngineState(
            target = WifiTarget.TRUCK,
            consecutivePhoneMisses = 2,
            lastSwitchTimeMillis = 0L,
        )
        clock.set(ScanPolicy.INTERVAL_MILLIS)

        val step = engine.scan(state, clock, setOf(PHONE_SSID, TRUCK_SSID), config)

        assertEquals(WifiTarget.PHONE, step.decision.target)
        assertTrue(step.decision.shouldApply)
        assertEquals(DecisionReason.PHONE_VISIBLE, step.decision.reason)
        assertEquals(0, step.decision.consecutivePhoneMisses)
        val elapsed = step.decision.lastSwitchTimeMillis!! - state.lastSwitchTimeMillis!!
        assertEquals(ScanPolicy.INTERVAL_MILLIS, elapsed)
        assertTrue(elapsed <= 60_000L)
    }

    @Test
    fun phoneFlappingEveryOtherScan_doesNotSwitchBackAndForthToTruck() {
        var state = EngineState()
        val targets = mutableListOf<WifiTarget>()
        val applied = mutableListOf<WifiTarget>()
        val visibleFrames = listOf(
            setOf(PHONE_SSID, TRUCK_SSID),
            setOf(TRUCK_SSID),
            setOf(PHONE_SSID, TRUCK_SSID),
            setOf(TRUCK_SSID),
            setOf(PHONE_SSID, TRUCK_SSID),
            setOf(TRUCK_SSID),
        )

        for (visible in visibleFrames) {
            val step = engine.scan(state, clock, visible, config)
            state = step.state
            targets += step.decision.target
            if (step.decision.shouldApply) {
                applied += step.decision.target
            }
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        }

        assertEquals(List(visibleFrames.size) { WifiTarget.PHONE }, targets)
        assertEquals(listOf(WifiTarget.PHONE), applied)
        assertFalse(targets.contains(WifiTarget.TRUCK))
        assertTrue(state.consecutivePhoneMisses <= 1)
    }

    @Test
    fun singleMissingScan_staysOnCurrentTarget() {
        val onPhone = engine.scan(EngineState(), clock, setOf(PHONE_SSID), config).state
        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)

        val step = engine.scan(onPhone, clock, setOf(TRUCK_SSID), config)

        assertEquals(WifiTarget.PHONE, step.decision.target)
        assertFalse(step.decision.shouldApply)
        assertEquals(DecisionReason.SINGLE_MISS_HOLD, step.decision.reason)
        assertEquals(onPhone.lastSwitchTimeMillis, step.decision.lastSwitchTimeMillis)
    }

    @Test
    fun neitherVisible_keepsLastTarget() {
        var state = engine.scan(EngineState(), clock, setOf(PHONE_SSID), config).state
        val reasons = mutableListOf<DecisionReason>()
        repeat(2) {
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
            val step = engine.scan(state, clock, emptySet(), config)
            state = step.state
            reasons += step.decision.reason
            assertEquals(WifiTarget.PHONE, step.decision.target)
            assertFalse(step.decision.shouldApply)
        }
        assertEquals(
            listOf(DecisionReason.SINGLE_MISS_HOLD, DecisionReason.NEITHER_VISIBLE_HOLD),
            reasons,
        )
        assertEquals(WifiTarget.PHONE, state.target)
    }

    @Test
    fun neitherVisibleWithNoPriorTarget_staysNone() {
        var state = EngineState()
        var lastReason = DecisionReason.DISABLED
        repeat(3) {
            val step = engine.scan(state, clock, emptySet(), config)
            state = step.state
            lastReason = step.decision.reason
            assertEquals(WifiTarget.NONE, step.decision.target)
            assertFalse(step.decision.shouldApply)
            assertNull(step.decision.lastSwitchTimeMillis)
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        }
        assertEquals(DecisionReason.NEITHER_VISIBLE_HOLD, lastReason)
        assertEquals(3, state.consecutivePhoneMisses)
    }

    @Test
    fun disabled_takesNoAction() {
        val disabled = enabledConfig(enabled = false)
        var state = EngineState(
            target = WifiTarget.PHONE,
            consecutivePhoneMisses = 0,
            lastSwitchTimeMillis = 10L,
        )
        repeat(4) {
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
            val step = engine.scan(state, clock, setOf(TRUCK_SSID), disabled)
            assertFalse(step.decision.shouldApply)
            assertEquals(DecisionReason.DISABLED, step.decision.reason)
            assertEquals(state, step.state)
            state = step.state
        }
        assertEquals(WifiTarget.PHONE, state.target)
        assertEquals(0, state.consecutivePhoneMisses)
        assertEquals(10L, state.lastSwitchTimeMillis)
    }

    @Test
    fun emptyConfig_takesNoAction() {
        val empty = PriorityConfig(enabled = true)
        var state = EngineState()
        repeat(3) {
            val step = engine.scan(
                state,
                clock,
                setOf(PHONE_SSID, TRUCK_SSID),
                empty,
            )
            assertFalse(step.decision.shouldApply)
            assertEquals(DecisionReason.EMPTY_CONFIG, step.decision.reason)
            assertEquals(WifiTarget.NONE, step.decision.target)
            assertEquals(state, step.state)
            state = step.state
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        }
    }

    @Test
    fun blankSsids_treatedAsEmptyConfig() {
        val blank = PriorityConfig(
            enabled = true,
            phone = NetworkCredential("   ", "ignored"),
            truck = NetworkCredential("", "ignored"),
        )
        val step = engine.scan(EngineState(), clock, setOf(PHONE_SSID, TRUCK_SSID), blank)
        assertEquals(DecisionReason.EMPTY_CONFIG, step.decision.reason)
        assertFalse(step.decision.shouldApply)
        assertEquals(WifiTarget.NONE, step.decision.target)
    }

    @Test
    fun disabled_doesNotAccumulateMisses() {
        val disabled = enabledConfig(enabled = false)
        var state = EngineState(target = WifiTarget.PHONE, lastSwitchTimeMillis = 0L)
        repeat(3) {
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
            state = engine.scan(state, clock, setOf(TRUCK_SSID), disabled).state
        }

        val step = engine.scan(state, clock, setOf(TRUCK_SSID), config)
        assertEquals(WifiTarget.PHONE, step.decision.target)
        assertEquals(1, step.decision.consecutivePhoneMisses)
        assertFalse(step.decision.shouldApply)
        assertEquals(DecisionReason.SINGLE_MISS_HOLD, step.decision.reason)
    }

    @Test
    fun lastSwitchTime_updatesOnlyWhenTargetChanges() {
        clock.set(1_700_000_000_000L)
        val toPhone = engine.scan(EngineState(), clock, setOf(PHONE_SSID), config)
        assertEquals(1_700_000_000_000L, toPhone.decision.lastSwitchTimeMillis)

        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        val stay = engine.scan(toPhone.state, clock, setOf(PHONE_SSID), config)
        assertEquals(1_700_000_000_000L, stay.decision.lastSwitchTimeMillis)
        assertFalse(stay.decision.shouldApply)

        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        val miss = engine.scan(stay.state, clock, setOf(TRUCK_SSID), config)
        clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
        val toTruck = engine.scan(miss.state, clock, setOf(TRUCK_SSID), config)
        assertEquals(clock.nowMillis(), toTruck.decision.lastSwitchTimeMillis)
        assertTrue(toTruck.decision.lastSwitchTimeMillis!! > stay.decision.lastSwitchTimeMillis!!)
    }

    @Test
    fun ssidMatch_isCaseSensitive() {
        val step = engine.scan(EngineState(), clock, setOf(PHONE_SSID.lowercase()), config)
        assertEquals(WifiTarget.NONE, step.decision.target)
        assertEquals(DecisionReason.SINGLE_MISS_HOLD, step.decision.reason)
    }

    @Test
    fun configuredSsid_isTrimmedBeforeMatch() {
        val padded = enabledConfig(phoneSsid = "  $PHONE_SSID  ")
        val step = engine.scan(EngineState(), clock, setOf(PHONE_SSID), padded)
        assertEquals(WifiTarget.PHONE, step.decision.target)
        assertTrue(step.decision.shouldApply)
    }

    @Test
    fun truckUnconfigured_phoneAbsent_keepsLastTarget() {
        val phoneOnly = enabledConfig(truckSsid = null)
        var state = engine.scan(EngineState(), clock, setOf(PHONE_SSID), phoneOnly).state
        val reasons = mutableListOf<DecisionReason>()
        repeat(3) {
            clock.advanceBy(ScanPolicy.INTERVAL_MILLIS)
            val step = engine.scan(state, clock, setOf(TRUCK_SSID), phoneOnly)
            state = step.state
            reasons += step.decision.reason
            assertEquals(WifiTarget.PHONE, step.decision.target)
            assertFalse(step.decision.shouldApply)
        }
        assertEquals(
            listOf(
                DecisionReason.SINGLE_MISS_HOLD,
                DecisionReason.NEITHER_VISIBLE_HOLD,
                DecisionReason.NEITHER_VISIBLE_HOLD,
            ),
            reasons,
        )
    }
}
