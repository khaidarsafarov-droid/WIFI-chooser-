package com.wifipriority.mock

import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.ScanSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted, cold [ScanSource]. Each collection replays [frames] and waits
 * [intervalMillis] between them. The timestamp is [Clock.nowMillis] at emit time;
 * this source does not advance the clock itself. A [FakeClock] stays put unless
 * the caller moves it. A clock that reads a test scheduler moves with virtual time.
 */
class MockScanSource(
    val frames: List<Set<String>>,
    private val clock: Clock,
    val intervalMillis: Long = ScanPolicy.INTERVAL_MILLIS,
) : ScanSource {
    init {
        require(intervalMillis >= 0L) { "intervalMillis must be >= 0" }
    }

    override val scans: Flow<ScanSnapshot> = flow {
        frames.forEachIndexed { index, visible ->
            emit(
                ScanSnapshot(
                    visibleSsids = visible.toSet(),
                    timestampMillis = clock.nowMillis(),
                ),
            )
            if (index < frames.lastIndex) {
                delay(intervalMillis)
            }
        }
    }

    companion object {
        fun forScenario(
            scenario: MockScenario,
            phoneSsid: String,
            truckSsid: String,
            clock: Clock,
            intervalMillis: Long = ScanPolicy.INTERVAL_MILLIS,
        ): MockScanSource {
            require(phoneSsid.isNotBlank()) { "phoneSsid must not be blank" }
            require(truckSsid.isNotBlank()) { "truckSsid must not be blank" }
            val frames: List<Set<String>> = when (scenario) {
                MockScenario.BOTH_VISIBLE -> List(3) { setOf(phoneSsid, truckSsid) }
                MockScenario.PHONE_DISAPPEARS -> listOf(
                    setOf(phoneSsid, truckSsid),
                    setOf(truckSsid),
                    setOf(truckSsid),
                    setOf(truckSsid),
                )
                MockScenario.PHONE_FLAPPING -> listOf(
                    setOf(phoneSsid, truckSsid),
                    setOf(truckSsid),
                    setOf(phoneSsid, truckSsid),
                    setOf(truckSsid),
                    setOf(phoneSsid, truckSsid),
                    setOf(truckSsid),
                )
                MockScenario.PHONE_RETURNS -> listOf(
                    setOf(truckSsid),
                    setOf(truckSsid),
                    setOf(phoneSsid, truckSsid),
                )
                MockScenario.NOTHING_VISIBLE -> List(3) { emptySet() }
            }
            return MockScanSource(frames, clock, intervalMillis)
        }
    }
}

enum class MockScenario {
    /** Phone and truck SSIDs in every frame. */
    BOTH_VISIBLE,

    /** Both, then the phone SSID drops out and stays gone. */
    PHONE_DISAPPEARS,

    /** Phone visible, gone, visible, gone… every other scan. */
    PHONE_FLAPPING,

    /** Truck only, long enough to select it, then the phone SSID is back. */
    PHONE_RETURNS,

    /** No configured SSID in any frame. */
    NOTHING_VISIBLE,
}
