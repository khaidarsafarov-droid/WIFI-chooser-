package com.wifipriority.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsidTextTest {
    @Test
    fun stripsQuotesAndUnknownSsid() {
        assertEquals("PhoneHotspot", normalizePlatformSsid("\"PhoneHotspot\""))
        assertEquals("Plain", normalizePlatformSsid("Plain"))
        assertNull(normalizePlatformSsid(UNKNOWN_SSID))
        assertNull(normalizePlatformSsid("  "))
        assertNull(normalizePlatformSsid(null))
    }

    @Test
    fun octetsWinOverQuotedFallback() {
        val bytes = "TruckHotspot".toByteArray(Charsets.UTF_8)
        assertEquals("TruckHotspot", ssidFromOctets(bytes, "\"ignored\""))
        assertEquals("quoted", ssidFromOctets(null, "\"quoted\""))
    }
}
