@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wifipriority.mock

import app.cash.turbine.test
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_SSID
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.ScanPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MockScanSourceTest {
    @Test
    fun bothVisible_emitsBothSsids() = runTest(timeout = 5.seconds) {
        val source = scenario(MockScenario.BOTH_VISIBLE)
        source.scans.test {
            repeat(3) {
                assertEquals(setOf(PHONE_SSID, TRUCK_SSID), awaitItem().visibleSsids)
            }
            awaitComplete()
        }
    }

    @Test
    fun phoneDisappears_dropsPhoneAfterFirstFrame() = runTest(timeout = 5.seconds) {
        val source = scenario(MockScenario.PHONE_DISAPPEARS)
        source.scans.test {
            assertEquals(setOf(PHONE_SSID, TRUCK_SSID), awaitItem().visibleSsids)
            repeat(3) {
                assertEquals(setOf(TRUCK_SSID), awaitItem().visibleSsids)
            }
            awaitComplete()
        }
    }

    @Test
    fun phoneFlapping_alternatesEveryScan() = runTest(timeout = 5.seconds) {
        val source = scenario(MockScenario.PHONE_FLAPPING)
        source.scans.test {
            repeat(3) {
                assertEquals(setOf(PHONE_SSID, TRUCK_SSID), awaitItem().visibleSsids)
                assertEquals(setOf(TRUCK_SSID), awaitItem().visibleSsids)
            }
            awaitComplete()
        }
    }

    @Test
    fun phoneReturns_endsOnPhoneAndTruck() = runTest(timeout = 5.seconds) {
        val source = scenario(MockScenario.PHONE_RETURNS)
        source.scans.test {
            assertEquals(setOf(TRUCK_SSID), awaitItem().visibleSsids)
            assertEquals(setOf(TRUCK_SSID), awaitItem().visibleSsids)
            assertEquals(setOf(PHONE_SSID, TRUCK_SSID), awaitItem().visibleSsids)
            awaitComplete()
        }
    }

    @Test
    fun nothingVisible_emitsEmptySets() = runTest(timeout = 5.seconds) {
        val source = scenario(MockScenario.NOTHING_VISIBLE)
        source.scans.test {
            repeat(3) {
                assertEquals(emptySet<String>(), awaitItem().visibleSsids)
            }
            awaitComplete()
        }
    }

    @Test
    fun framesAreSpacedFortyFiveSecondsApart() = runTest(timeout = 5.seconds) {
        val clock = Clock { testScheduler.currentTime }
        val source = MockScanSource.forScenario(
            scenario = MockScenario.PHONE_DISAPPEARS,
            phoneSsid = PHONE_SSID,
            truckSsid = TRUCK_SSID,
            clock = clock,
        )
        source.scans.test {
            val times = mutableListOf<Long>()
            repeat(source.frames.size) {
                times += awaitItem().timestampMillis
            }
            awaitComplete()
            assertEquals(
                listOf(0L, 45_000L, 90_000L, 135_000L),
                times,
            )
            assertEquals(ScanPolicy.INTERVAL_MILLIS, source.intervalMillis)
        }
    }

    private fun scenario(scenario: MockScenario): MockScanSource =
        MockScanSource.forScenario(
            scenario = scenario,
            phoneSsid = PHONE_SSID,
            truckSsid = TRUCK_SSID,
            clock = FakeClock(),
        )
}
