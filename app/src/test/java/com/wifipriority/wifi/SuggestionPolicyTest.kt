package com.wifipriority.wifi

import com.wifipriority.PHONE_PASSWORD
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_PASSWORD
import com.wifipriority.TRUCK_SSID
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPolicyTest {
    @Test
    fun bothRegistered_phoneHasHigherPriority() {
        val step = SuggestionPolicy.evaluate(PolicyState(), obs(connected = PHONE_SSID), 0L)
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), step.suggestions.map { it.role })
        val phone = step.suggestions.first { it.role == WifiTarget.PHONE }
        val truck = step.suggestions.first { it.role == WifiTarget.TRUCK }
        assertTrue(phone.priority > truck.priority)
        assertTrue(phone.priorityGroup > truck.priorityGroup)
    }

    @Test
    fun alreadyOnPhone_doesNotStartForceReturn() {
        val step = SuggestionPolicy.evaluate(PolicyState(), obs(connected = PHONE_SSID), 1_000L)
        assertNull(step.state.forceReturnSince)
        assertTrue(step.disconnectSsids.isEmpty())
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
        assertFalse(step.events.any { it.kind == LogKind.FORCE_RETURN_START })
    }

    @Test
    fun validatedPhone_doesNotWatchdogReset() {
        val armed = PolicyState(unvalidatedSince = 0L, forceReturnSince = 0L)
        val step = SuggestionPolicy.evaluate(
            armed,
            obs(connected = PHONE_SSID, validated = true),
            SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS * 2,
        )
        assertFalse(step.events.any { it.kind == LogKind.WATCHDOG_RESET })
        assertNull(step.state.unvalidatedSince)
        assertNull(step.state.forceReturnSince)
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
    }

    @Test
    fun phoneTargetWhileConnectedToTruck_startsShortForceReturn() {
        val step = SuggestionPolicy.evaluate(PolicyState(), obs(connected = TRUCK_SSID), 1_000L)
        assertEquals(listOf(WifiTarget.PHONE), step.suggestions.map { it.role })
        assertEquals(1_000L, step.state.forceReturnSince)
        assertEquals(setOf(TRUCK_SSID), step.disconnectSsids)
    }

    @Test
    fun stuckForceReturn_recoversOnTimeout_andDoesNotRemoveTruckAgain() {
        val started = SuggestionPolicy.evaluate(PolicyState(), obs(connected = TRUCK_SSID), 0L)
        val timedOut = SuggestionPolicy.evaluate(started.state, obs(connected = TRUCK_SSID), 30_000L)
        assertTrue(timedOut.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(timedOut.state.forceReturnSince)
        assertTrue(timedOut.events.any { it.kind == LogKind.FORCE_RETURN_TIMEOUT })
        val later = SuggestionPolicy.evaluate(timedOut.state, obs(connected = TRUCK_SSID), 31_000L)
        assertTrue(later.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(later.state.forceReturnSince)
    }

    @Test
    fun phoneDisappearsDuringForceReturn_restoresTruck() {
        val started = SuggestionPolicy.evaluate(PolicyState(), obs(connected = TRUCK_SSID), 0L)
        val gone = SuggestionPolicy.evaluate(
            started.state,
            obs(connected = TRUCK_SSID, visible = setOf(TRUCK_SSID)),
            5_000L,
        )
        assertTrue(gone.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(gone.state.forceReturnSince)
        assertTrue(gone.events.any { it.kind == LogKind.FORCE_RETURN_ABORT })
    }

    @Test
    fun targetBecomesTruck_restoresTruckImmediately() {
        val started = SuggestionPolicy.evaluate(PolicyState(), obs(connected = TRUCK_SSID), 0L)
        val truck = SuggestionPolicy.evaluate(
            started.state,
            obs(target = WifiTarget.TRUCK, connected = TRUCK_SSID),
            5_000L,
        )
        assertTrue(truck.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(truck.state.forceReturnSince)
    }

    @Test
    fun disconnectedAndTruckVisible_registersTruck() {
        val step = SuggestionPolicy.evaluate(
            PolicyState(),
            obs(connected = null, visible = setOf(TRUCK_SSID), validated = false),
            0L,
        )
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(step.state.forceReturnSince)
    }

    @Test
    fun repeatedPhoneFailures_backOffAndKeepTruck() {
        var state = PolicyState()
        repeat(3) { index ->
            val step = SuggestionPolicy.evaluate(
                state,
                obs(
                    connected = TRUCK_SSID,
                    failure = ConnectionFailure(PHONE_SSID, FailureReason.AUTHENTICATION),
                ),
                index * 1_000L,
            )
            state = step.state
        }
        assertEquals(3, state.phoneFailures)
        val blocked = SuggestionPolicy.evaluate(state, obs(connected = TRUCK_SSID), 10_000L)
        assertTrue(blocked.suggestions.any { it.role == WifiTarget.TRUCK })
        assertNull(blocked.state.forceReturnSince)
    }

    @Test
    fun phoneWithoutInternetForTwoMinutes_dropsPhoneAndKeepsTruck() {
        val started = SuggestionPolicy.evaluate(
            PolicyState(),
            obs(connected = PHONE_SSID, validated = false),
            0L,
        )
        val dropped = SuggestionPolicy.evaluate(
            started.state,
            obs(connected = PHONE_SSID, validated = false),
            SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS,
        )
        assertTrue(dropped.suggestions.none { it.role == WifiTarget.PHONE })
        assertTrue(dropped.suggestions.any { it.role == WifiTarget.TRUCK })
        assertTrue(dropped.events.any { it.kind == LogKind.WATCHDOG_DROP_PHONE })
        assertEquals(setOf(PHONE_SSID), dropped.disconnectSsids)
    }

    @Test
    fun noValidatedInternetForTwoMinutes_resetsAndRegistersTruck() {
        val armed = PolicyState(forceReturnSince = 0L, unvalidatedSince = 0L, failureBackoffUntil = 50_000L)
        val step = SuggestionPolicy.evaluate(
            armed,
            obs(connected = null, visible = setOf(TRUCK_SSID), validated = false),
            SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS,
        )
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
        assertTrue(step.suggestions.any { it.role == WifiTarget.PHONE })
        assertNull(step.state.forceReturnSince)
        assertNull(step.state.failureBackoffUntil)
        assertTrue(step.forceReapply)
        assertTrue(step.events.any { it.kind == LogKind.WATCHDOG_RESET })
    }

    @Test
    fun property_truckStaysRegisteredWhenUnvalidatedOverTwoMinutes() {
        val random = Random(2026)
        repeat(40) {
            var state = PolicyState()
            var now = 0L
            var unvalidatedFor = 0L
            repeat(12) {
                now += 45_000L
                val validated = random.nextBoolean()
                val phoneVisible = random.nextBoolean()
                val truckVisible = random.nextBoolean()
                val link = random.nextInt(3)
                val connected = when (link) {
                    0 -> PHONE_SSID
                    1 -> TRUCK_SSID
                    else -> null
                }
                val visible = buildSet {
                    if (phoneVisible) add(PHONE_SSID)
                    if (truckVisible) add(TRUCK_SSID)
                }
                if (validated && connected != null) unvalidatedFor = 0 else unvalidatedFor += 45_000L
                val step = SuggestionPolicy.evaluate(
                    state,
                    obs(
                        target = if (phoneVisible) WifiTarget.PHONE else WifiTarget.TRUCK,
                        connected = connected,
                        visible = visible,
                        validated = validated && connected != null,
                    ),
                    now,
                )
                state = step.state
                if (state.forceReturnSince != null) {
                    assertTrue(now - state.forceReturnSince!! <= SuggestionPlanner.FORCE_RETURN_MAX_MILLIS)
                }
                val forcing = state.forceReturnSince != null
                if (!forcing) {
                    assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
                }
                if (truckVisible && unvalidatedFor > SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS) {
                    assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK && it.ssid == TRUCK_SSID })
                }
            }
        }
    }

    @Test
    fun transitionMode_registersWpa2AndWpa3() {
        val step = SuggestionPolicy.evaluate(
            PolicyState(),
            obs(
                connected = PHONE_SSID,
                security = mapOf(PHONE_SSID to WifiSecurity.TRANSITION, TRUCK_SSID to WifiSecurity.WPA2),
            ),
            0L,
        )
        val phone = step.suggestions.filter { it.role == WifiTarget.PHONE }
        assertEquals(setOf(WifiSecurity.WPA2, WifiSecurity.WPA3), phone.map { it.security }.toSet())
    }

    @Test
    fun wpa3_registersOnlyWpa3() {
        val step = SuggestionPolicy.evaluate(
            PolicyState(),
            obs(connected = PHONE_SSID, security = mapOf(PHONE_SSID to WifiSecurity.WPA3)),
            0L,
        )
        assertEquals(listOf(WifiSecurity.WPA3), step.suggestions.filter { it.role == WifiTarget.PHONE }.map { it.security })
    }

    @Test
    fun shortPassphrase_isNotSuggested() {
        val step = SuggestionPolicy.evaluate(
            PolicyState(),
            obs(phonePassphrase = "short", truckPassphrase = "also"),
            0L,
        )
        assertTrue(step.suggestions.isEmpty())
    }

    @Test
    fun disabled_registersNothing() {
        val step = SuggestionPolicy.evaluate(PolicyState(suggestionSignature = "old"), obs(enabled = false), 0L)
        assertTrue(step.suggestions.isEmpty())
        assertTrue(step.forceReapply)
    }

    private fun obs(
        target: WifiTarget = WifiTarget.PHONE,
        connected: String? = null,
        visible: Set<String> = setOf(PHONE_SSID, TRUCK_SSID),
        validated: Boolean = true,
        enabled: Boolean = true,
        security: Map<String, WifiSecurity> = emptyMap(),
        failure: ConnectionFailure? = null,
        phonePassphrase: String = PHONE_PASSWORD,
        truckPassphrase: String = TRUCK_PASSWORD,
    ) = PolicyObservation(
        enabled = enabled,
        target = target,
        phoneSsid = PHONE_SSID,
        truckSsid = TRUCK_SSID,
        phonePassphrase = phonePassphrase,
        truckPassphrase = truckPassphrase,
        connectedSsid = connected,
        visibleSsids = visible,
        securityBySsid = security,
        validated = validated,
        radioOn = true,
        fromOurSuggestion = true,
        authFailure = failure,
    )
}
