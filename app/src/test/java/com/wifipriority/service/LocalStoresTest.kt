package com.wifipriority.service

import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.LogKind
import com.wifipriority.wifi.PolicyState
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStoresTest {
    @Test
    fun eventLog_keepsLast500() = runTest {
        val file = File.createTempFile("wifi-events", ".log")
        val log = FileEventLog(file)
        val events = (1..600).map { LogEvent(it.toLong(), LogKind.WIFI_ON, "n$it") }
        log.append(events)
        assertEquals(500, log.recent.value.size)
        assertEquals(101L, log.recent.value.first().timeMillis)
        val reloaded = FileEventLog(file)
        assertEquals(500, reloaded.recent.value.size)
        assertEquals("n600", reloaded.recent.value.last().detail)
        file.delete()
    }

    @Test
    fun policyState_roundTrips() = runTest {
        val file = File.createTempFile("wifi-policy", ".txt")
        val store = FilePolicyStateStore(file)
        val state = PolicyState(
            forceReturnSince = 10L,
            phoneFailures = 3,
            failureBackoffUntil = 20L,
            phoneUnvalidatedSince = 12L,
            disconnectRetryAt = 30L,
            lastFailureSsid = "S26 ULTRA",
            lastFailureReason = FailureReason.AUTHENTICATION,
            suggestionSignature = "PHONE:S26 ULTRA:WPA2",
        )
        store.save(state)
        assertEquals(state, FilePolicyStateStore(file).load())
        assertTrue(decodePolicy("garbage").phoneFailures == 0)
        file.delete()
    }
}
