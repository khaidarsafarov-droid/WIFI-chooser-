package com.wifipriority.wifi

import com.wifipriority.PHONE_PASSWORD
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_PASSWORD
import com.wifipriority.TRUCK_SSID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionApplyTest {
    @Test
    fun plannedSuggestion_toString_hidesPassphrase() {
        val text = planned(WifiTarget.TRUCK, TRUCK_SSID, TRUCK_PASSWORD).toString()
        assertFalse(text.contains(TRUCK_PASSWORD))
        assertTrue(text.contains("***"))
    }

    @Test
    fun contentKey_changesWithPassphrase_andDoesNotContainIt() {
        val phone = planned(WifiTarget.PHONE, PHONE_SSID, PHONE_PASSWORD)
        val truck = planned(WifiTarget.TRUCK, TRUCK_SSID, TRUCK_PASSWORD)
        val updated = planned(WifiTarget.TRUCK, TRUCK_SSID, "a-different-truck-secret")
        val before = suggestionContentKey(listOf(phone, truck))
        val after = suggestionContentKey(listOf(phone, updated))
        assertNotEquals(before, after)
        assertFalse(before.contains(PHONE_PASSWORD))
        assertFalse(before.contains(TRUCK_PASSWORD))
        assertFalse(after.contains("a-different-truck-secret"))
    }

    @Test
    fun transientAddFailure_isRetried_userRejectionIsNot() {
        assertEquals(SuggestionCommit.FAILED, suggestionCommitFor(SuggestionAddKind.INTERNAL, removedOnly = false))
        assertEquals(SuggestionCommit.FAILED, suggestionCommitFor(SuggestionAddKind.OTHER, removedOnly = false))
        assertEquals(SuggestionCommit.REJECTED, suggestionCommitFor(SuggestionAddKind.NOT_ALLOWED, removedOnly = false))
        assertEquals(SuggestionCommit.REJECTED, suggestionCommitFor(SuggestionAddKind.APP_DISALLOWED, removedOnly = false))
        assertEquals(SuggestionCommit.APPLIED, suggestionCommitFor(SuggestionAddKind.SUCCESS, removedOnly = false))
        assertEquals(SuggestionCommit.APPLIED, suggestionCommitFor(SuggestionAddKind.DUPLICATE, removedOnly = false))
        assertEquals(SuggestionCommit.APPLIED, suggestionCommitFor(SuggestionAddKind.INTERNAL, removedOnly = true))
    }

    @Test
    fun recoveryDeadline_isTheSoonestTimer() {
        assertEquals(30_000L, PolicyState(forceReturnSince = 0L).recoveryDeadlineMillis())
        assertEquals(120_000L, PolicyState(unvalidatedSince = 0L).recoveryDeadlineMillis())
        assertEquals(
            15_000L,
            PolicyState(forceReturnSince = 0L, disconnectRetryAt = 15_000L).recoveryDeadlineMillis(),
        )
        assertEquals(null, PolicyState(failureBackoffUntil = 50_000L).recoveryDeadlineMillis())
    }

    @Test
    fun unknownLink_doesNotStartTheUnvalidatedClock() {
        val step = SuggestionPolicy.evaluate(
            PolicyState(),
            PolicyObservation(
                enabled = true,
                target = WifiTarget.PHONE,
                phoneSsid = PHONE_SSID,
                truckSsid = TRUCK_SSID,
                phonePassphrase = PHONE_PASSWORD,
                truckPassphrase = TRUCK_PASSWORD,
                connectedSsid = null,
                visibleSsids = setOf(PHONE_SSID, TRUCK_SSID),
                validated = false,
                radioOn = true,
                fromOurSuggestion = null,
                linkKnown = false,
            ),
            0L,
        )
        assertEquals(null, step.state.unvalidatedSince)
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
    }

    private fun planned(role: WifiTarget, ssid: String, passphrase: String) = PlannedSuggestion(
        role = role,
        ssid = ssid,
        passphrase = passphrase,
        priority = if (role == WifiTarget.PHONE) SuggestionPlanner.PHONE_PRIORITY else SuggestionPlanner.TRUCK_PRIORITY,
        priorityGroup = if (role == WifiTarget.PHONE) SuggestionPlanner.PHONE_GROUP else SuggestionPlanner.TRUCK_GROUP,
    )
}
