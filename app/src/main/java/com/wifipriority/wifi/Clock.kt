package com.wifipriority.wifi

/**
 * Millisecond clock. Production code uses [WallClock]; tests use [com.wifipriority.mock.FakeClock].
 */
fun interface Clock {
    fun nowMillis(): Long
}

/** Wall clock. Not used by the stage-1 mock stack. */
object WallClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
