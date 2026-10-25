package com.wifipriority.wifi

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSecurityTest {
    @Test
    fun capabilities_mapToSecurity() {
        assertEquals(WifiSecurity.WPA2, securityFromCapabilities("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA3, securityFromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.TRANSITION, securityFromCapabilities("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.TRANSITION, securityFromCapabilities("[RSN-PSK-CCMP][RSN-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.OPEN, securityFromCapabilities("[ESS]"))
        assertEquals(WifiSecurity.UNKNOWN, securityFromCapabilities(null))
    }

    @Test
    fun mixedResults_becomeTransition() {
        assertEquals(WifiSecurity.TRANSITION, mergeSecurity(WifiSecurity.WPA2, WifiSecurity.WPA3))
    }

    @Test
    fun variants_followDetectedSecurity() {
        assertEquals(listOf(WifiSecurity.WPA2), securityVariants(WifiSecurity.WPA2))
        assertEquals(listOf(WifiSecurity.WPA2), securityVariants(WifiSecurity.UNKNOWN))
        assertEquals(listOf(WifiSecurity.WPA3), securityVariants(WifiSecurity.WPA3))
        assertEquals(listOf(WifiSecurity.WPA2, WifiSecurity.WPA3), securityVariants(WifiSecurity.TRANSITION))
    }

    @Test
    fun addStatusCodes_matchPlatformValues() {
        assertEquals(SuggestionAddKind.SUCCESS, suggestionAddKind(0))
        assertEquals(SuggestionAddKind.APP_DISALLOWED, suggestionAddKind(2))
        assertEquals(SuggestionAddKind.DUPLICATE, suggestionAddKind(3))
        assertEquals(SuggestionAddKind.NOT_ALLOWED, suggestionAddKind(6))
        assertEquals(SuggestionAddKind.OTHER, suggestionAddKind(99))
    }
}
