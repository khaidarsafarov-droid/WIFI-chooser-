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
