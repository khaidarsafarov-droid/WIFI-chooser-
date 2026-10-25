package com.wifipriority.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTrackerTest {
    @Test
    fun lossOfAnotherNetwork_keepsTheActiveLink() {
        val tracker = LinkTracker()
        assertFalse(tracker.snapshot().known)
        tracker.onCapabilities("wifi-1", "TruckHotspot", validated = true)
        tracker.onPosted("TruckHotspot")
        assertNull(tracker.onLost("wifi-other"))
        val still = tracker.snapshot()
        assertEquals("TruckHotspot", still.connectedSsid)
        assertTrue(still.validated)
        assertEquals(true, still.fromOurSuggestion)
    }

    @Test
    fun nonActiveTruck_doesNotReplaceValidatedPhone() {
        val tracker = LinkTracker()
        tracker.onCapabilities("phone", "S26 ULTRA", validated = true, active = true)
        tracker.onPosted("S26 ULTRA")
        val ignored = tracker.onCapabilities("truck", "Verizon-MW501UV-6009", validated = false, active = false)
        assertEquals("S26 ULTRA", ignored.connectedSsid)
        assertTrue(ignored.validated)
        assertEquals(true, ignored.fromOurSuggestion)
        assertNull(tracker.onLost("truck"))
        assertEquals("S26 ULTRA", tracker.snapshot().connectedSsid)
        assertTrue(tracker.snapshot().validated)
    }

    @Test
    fun redactedSsid_keepsValidatedPhone() {
        val tracker = LinkTracker()
        tracker.onCapabilities("phone", "S26 ULTRA", validated = false, active = true)
        val next = tracker.onCapabilities("phone", null, validated = true, active = true)
        assertEquals("S26 ULTRA", next.connectedSsid)
        assertTrue(next.validated)
    }

    @Test
    fun activeValidatedPhone_isChosenOverOtherWifi() {
        val selected = selectWifiLink(
            listOf(
                WifiNetworkView("truck", "Verizon-MW501UV-6009", validated = false, active = false),
                WifiNetworkView("phone", "S26 ULTRA", validated = true, active = true),
            ),
        )
        assertEquals("S26 ULTRA", selected?.ssid)
        assertTrue(selected!!.validated)
        val step = SuggestionPolicy.evaluate(
            PolicyState(unvalidatedSince = 0L),
            PolicyObservation(
                enabled = true,
                target = WifiTarget.PHONE,
                phoneSsid = "S26 ULTRA",
                truckSsid = "Verizon-MW501UV-6009",
                phonePassphrase = "phone-secret-value",
                truckPassphrase = "truck-secret-value",
                connectedSsid = selected.ssid,
                visibleSsids = setOf("S26 ULTRA", "Verizon-MW501UV-6009"),
                validated = selected.validated,
                radioOn = true,
                fromOurSuggestion = true,
            ),
            SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS,
        )
        assertFalse(step.events.any { it.kind == LogKind.WATCHDOG_RESET })
        assertFalse(step.events.any { it.kind == LogKind.FORCE_RETURN_START })
        assertNull(step.state.forceReturnSince)
        assertTrue(step.suggestions.any { it.role == WifiTarget.TRUCK })
    }

    @Test
    fun lossOfTheActiveNetwork_clearsTheLink() {
        val tracker = LinkTracker()
        tracker.onCapabilities("wifi-1", "PhoneHotspot", validated = false)
        val lost = tracker.onLost("wifi-1")
        assertEquals(null, lost?.connectedSsid)
        assertEquals(false, lost?.validated)
        assertEquals(true, lost?.known)
        assertEquals(null, lost?.fromOurSuggestion)
    }
}
