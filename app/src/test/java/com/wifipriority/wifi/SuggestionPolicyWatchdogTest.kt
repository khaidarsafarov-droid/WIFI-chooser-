package com.wifipriority.wifi

import com.wifipriority.PHONE_PASSWORD
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_PASSWORD
import com.wifipriority.TRUCK_SSID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2-minute watchdog has to keep asking the connector to leave an
 * unvalidated phone. One shot at the 2-minute mark is not enough: the platform
 * disconnect flag only works while the suggestion is still registered, and a
 * single miss leaves the tablet on the phone for the rest of the 10-minute
 * suppress window while the truck is visible.
 */
class SuggestionPolicyWatchdogTest {
    @Test
    fun stillOnUnvalidatedPhone_disconnectsAgainBeforeBackoffEnds() {
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
        assertEquals(setOf(PHONE_SSID), dropped.disconnectSsids)
        assertTrue(dropped.suggestions.none { it.role == WifiTarget.PHONE })
        assertTrue(dropped.suggestions.any { it.role == WifiTarget.TRUCK })

        val later = SuggestionPolicy.evaluate(
            dropped.state,
            obs(connected = PHONE_SSID, validated = false),
            SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS + SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS,
        )
        assertEquals(setOf(PHONE_SSID), later.disconnectSsids)
        assertTrue(later.suggestions.none { it.role == WifiTarget.PHONE })
        assertTrue(later.suggestions.any { it.ssid == TRUCK_SSID })
        assertFalse(later.events.any { it.detail.contains(PHONE_PASSWORD) || it.detail.contains(TRUCK_PASSWORD) })
    }

    @Test
    fun suggestionEvents_doNotContainPassphrases() {
        val step = SuggestionPolicy.evaluate(PolicyState(), obs(connected = PHONE_SSID), 0L)
        assertTrue(step.events.isNotEmpty())
        assertTrue(step.events.none { it.detail.contains(PHONE_PASSWORD) || it.detail.contains(TRUCK_PASSWORD) })
    }

    private fun obs(
        connected: String?,
        validated: Boolean = true,
    ) = PolicyObservation(
        enabled = true,
        target = WifiTarget.PHONE,
        phoneSsid = PHONE_SSID,
        truckSsid = TRUCK_SSID,
        phonePassphrase = PHONE_PASSWORD,
        truckPassphrase = TRUCK_PASSWORD,
        connectedSsid = connected,
        visibleSsids = setOf(PHONE_SSID, TRUCK_SSID),
        validated = validated,
        radioOn = true,
        fromOurSuggestion = true,
    )
}
