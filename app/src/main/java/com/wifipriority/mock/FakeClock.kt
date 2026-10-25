package com.wifipriority.mock

import com.wifipriority.wifi.Clock

/** Manually advanced clock for tests and the stage-1 mock stack. */
class FakeClock(initialMillis: Long = 0L) : Clock {
    var millis: Long = initialMillis
        private set

    override fun nowMillis(): Long = millis

    fun set(millis: Long) {
        this.millis = millis
    }

    fun advanceBy(deltaMillis: Long) {
        require(deltaMillis >= 0L) { "deltaMillis must be >= 0" }
        millis += deltaMillis
    }
}
