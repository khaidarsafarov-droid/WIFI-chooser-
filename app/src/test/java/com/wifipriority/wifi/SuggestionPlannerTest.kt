package com.wifipriority.wifi

import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPlannerTest {
    private val config = enabledConfig()

    @Test
    fun bothRegistered_phoneHasHigherPriority() {
        val plan = SuggestionPlanner.plan(
            target = WifiTarget.PHONE,
            connectedSsid = PHONE_SSID,
            config = config,
            suppressingTruck = false,
        )
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), plan.suggestions.map { it.role })
        val phone = plan.suggestions.first { it.role == WifiTarget.PHONE }
        val truck = plan.suggestions.first { it.role == WifiTarget.TRUCK }
        assertTrue(phone.priority > truck.priority)
        assertTrue(phone.priorityGroup > truck.priorityGroup)
        assertFalse(plan.suppressingTruck)
    }

    @Test
    fun phoneTargetWhileConnectedToTruck_removesTruckSuggestion() {
        val plan = SuggestionPlanner.plan(
            target = WifiTarget.PHONE,
            connectedSsid = TRUCK_SSID,
            config = config,
            suppressingTruck = false,
        )
        assertEquals(listOf(WifiTarget.PHONE), plan.suggestions.map { it.role })
        assertTrue(plan.suppressingTruck)
    }

    @Test
    fun forceReturn_keepsTruckRemovedUntilPhoneConnects() {
        var suppressing = false
        fun step(connected: String?): SuggestionPlanner.Plan {
            val plan = SuggestionPlanner.plan(WifiTarget.PHONE, connected, config, suppressing)
            suppressing = plan.suppressingTruck
            return plan
        }

        assertFalse(step(null).suppressingTruck)
        assertEquals(listOf(WifiTarget.PHONE), step(TRUCK_SSID).suggestions.map { it.role })
        val roaming = step(null)
        assertTrue(roaming.suppressingTruck)
        assertEquals(listOf(WifiTarget.PHONE), roaming.suggestions.map { it.role })
        val restored = step(PHONE_SSID)
        assertFalse(restored.suppressingTruck)
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), restored.suggestions.map { it.role })
    }

    @Test
    fun truckTarget_keepsBothSuggestions() {
        val plan = SuggestionPlanner.plan(
            target = WifiTarget.TRUCK,
            connectedSsid = TRUCK_SSID,
            config = config,
            suppressingTruck = true,
        )
        assertFalse(plan.suppressingTruck)
        assertEquals(listOf(WifiTarget.PHONE, WifiTarget.TRUCK), plan.suggestions.map { it.role })
    }

    @Test
    fun noneTarget_registersNothing() {
        val plan = SuggestionPlanner.plan(
            target = WifiTarget.NONE,
            connectedSsid = TRUCK_SSID,
            config = config,
            suppressingTruck = true,
        )
        assertTrue(plan.suggestions.isEmpty())
        assertFalse(plan.suppressingTruck)
    }

    @Test
    fun shortPassphrase_isNotSuggested() {
        val weak = enabledConfig(phonePassword = "short", truckPassword = "also")
        val plan = SuggestionPlanner.plan(WifiTarget.PHONE, null, weak, false)
        assertTrue(plan.suggestions.isEmpty())
    }
}
