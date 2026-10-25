package com.wifipriority.wifi

/**
 * One network the connector should register. [passphrase] is empty for [WifiSecurity.OPEN].
 * Never log this value.
 */
data class PlannedSuggestion(
    val role: WifiTarget,
    val ssid: String,
    val passphrase: String,
    val priority: Int,
    val priorityGroup: Int,
    val security: WifiSecurity = WifiSecurity.WPA2,
)

object SuggestionPlanner {
    const val PHONE_PRIORITY: Int = 100
    const val TRUCK_PRIORITY: Int = 0
    const val PHONE_GROUP: Int = 1
    const val TRUCK_GROUP: Int = 0
    const val MIN_PASSPHRASE_LENGTH: Int = 8

    /** Truck is removed only inside this window, and only while the phone is still visible. */
    const val FORCE_RETURN_MAX_MILLIS: Long = 30_000L

    /** No validated internet for this long triggers the watchdog. */
    const val UNVALIDATED_LIMIT_MILLIS: Long = 120_000L

    /** After this many phone auth failures, stop pulling the truck suggestion. */
    const val PHONE_FAILURES_BEFORE_BACKOFF: Int = 3

    /** How long a failed phone attempt or a phone-without-internet drop leaves the truck alone. */
    const val BACKOFF_MILLIS: Long = 10 * 60_000L

    /** Connected without a post-connection broadcast for this long means it is not our suggestion. */
    const val SUGGESTION_GRACE_MILLIS: Long = 8_000L
}
