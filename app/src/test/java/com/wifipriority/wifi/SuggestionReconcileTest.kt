package com.wifipriority.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionReconcileTest {
    private val phone = planned(WifiTarget.PHONE, "S26 ULTRA", "phone-secret-value", priority = 100, group = 1)
    private val truck = planned(WifiTarget.TRUCK, "Verizon-MW501UV-6009", "truck-secret-value", priority = 0, group = 0)

    @Test
    fun savedSignature_doesNotCountAsRegistered() {
        assertTrue(needsPlatformReadd(listOf(phone, truck), emptySet()))
        assertFalse(needsPlatformReadd(listOf(phone, truck), setOf(phone.ssid, truck.ssid)))
        assertFalse(needsPlatformReadd(listOf(phone, truck), registeredSsids = null))
        assertEquals(setOf(truck.ssid), missingSuggestionSsids(listOf(phone, truck), setOf(phone.ssid)))
    }

    @Test
    fun successStatus_withEmptySystemList_isRetried() {
        val platform = FakePlatform(storeOnSuccess = false, addCode = 0)
        val report = registerSuggestions(platform, listOf(phone, truck), emptySet())
        assertEquals(SuggestionCommit.FAILED, report.commit)
        assertEquals(0, report.addStatus)
        assertTrue(report.registeredSsids.orEmpty().isEmpty())
        assertEquals(2, platform.adds.size)
        assertEquals(0, platform.adds[1].first().priority)
        assertEquals(0, platform.adds[1].first().priorityGroup)
        assertTrue(platform.calls.first().startsWith("add"))
    }

    @Test
    fun invalidAdd_retriesWithoutPriority_andKeepsNetworksWhenTheSecondAddWorks() {
        val platform = FakePlatform(addCodes = ArrayDeque(listOf(7, 0)))
        val report = registerSuggestions(platform, listOf(phone), emptySet())
        assertEquals(SuggestionCommit.APPLIED, report.commit)
        assertEquals(setOf(phone.ssid), report.registeredSsids)
        assertEquals(0, platform.adds[1].first().priority)
        assertTrue(platform.calls.none { it.startsWith("remove") })
    }

    @Test
    fun addHappensBeforeRemove_andOnlyUnwantedSsidsAreRemoved() {
        val platform = FakePlatform(initial = setOf(phone.ssid, truck.ssid))
        val report = registerSuggestions(platform, listOf(phone), disconnectSsids = setOf(truck.ssid))
        assertEquals(SuggestionCommit.APPLIED, report.commit)
        assertTrue(platform.calls[0].startsWith("add:"))
        assertEquals("remove:disconnect:${truck.ssid}", platform.calls[1])
        assertEquals(setOf(phone.ssid), report.registeredSsids)
    }

    @Test
    fun userRejection_withNothingStored_isStillAFailureSoTheNextCycleRetries() {
        val platform = FakePlatform(storeOnSuccess = false, addCode = 6)
        val report = registerSuggestions(platform, listOf(phone), emptySet())
        assertEquals(SuggestionCommit.FAILED, report.commit)
        assertEquals(6, report.addStatus)
        assertEquals(SuggestionAddKind.NOT_ALLOWED, suggestionAddKind(6))
    }

    @Test
    fun statusDetail_andScanSummary_doNotContainPassphrases() {
        val detail = suggestionStatusDetail(
            addCode = 7,
            removeCode = 0,
            registered = emptySet(),
            missing = setOf(phone.ssid, truck.ssid),
        )
        assertEquals(
            "add=7 INVALID remove=0 SUCCESS registered=none missing=S26 ULTRA|Verizon-MW501UV-6009",
            detail,
        )
        assertFalse(detail.contains("phone-secret-value"))
        assertFalse(detail.contains("truck-secret-value"))
        val scan = scanSummary(phone.ssid, truck.ssid, setOf(phone.ssid), fresh = false, wifiEnabled = true)
        assertEquals("refused phone=visible truck=absent", scan)
        assertTrue(passwordMissing(phone.ssid, null))
        assertTrue(passwordMissing(phone.ssid, ""))
        assertFalse(passwordMissing(phone.ssid, "phone-secret-value"))
        assertFalse(passwordMissing(null, ""))
    }

    private fun planned(
        role: WifiTarget,
        ssid: String,
        passphrase: String,
        priority: Int,
        group: Int,
    ) = PlannedSuggestion(role, ssid, passphrase, priority, group, WifiSecurity.WPA2)

}

private class FakePlatform(
    initial: Set<String> = emptySet(),
    private val storeOnSuccess: Boolean = true,
    private val addCode: Int = 0,
    private val addCodes: ArrayDeque<Int>? = null,
) : SuggestionPlatform {
    val calls = mutableListOf<String>()
    val adds = mutableListOf<List<PlannedSuggestion>>()
    private val ssids = initial.toMutableSet()

    override fun currentSsids(): Set<String> = ssids.toSet()

    override fun add(suggestions: List<PlannedSuggestion>): Int {
        adds += suggestions
        calls += "add:" + suggestions.joinToString(",") { "${it.ssid}:${it.priority}" }
        val code = addCodes?.removeFirstOrNull() ?: addCode
        if (storeOnSuccess && (code == 0 || code == 3)) {
            ssids += suggestions.map { it.ssid }
        }
        return code
    }

    override fun remove(ssids: Set<String>, disconnect: Boolean): Int {
        calls += "remove:${if (disconnect) "disconnect" else "drop"}:" + ssids.sorted().joinToString(",")
        this.ssids -= ssids
        return 0
    }
}
