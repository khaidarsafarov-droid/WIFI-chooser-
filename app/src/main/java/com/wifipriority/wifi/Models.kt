package com.wifipriority.wifi

/**
 * Desired device-wide Wi-Fi target.
 *
 * Stage 2 applies this with WifiNetworkSuggestion, not with
 * WifiNetworkSpecifier (app-local only). This stage only names the target.
 */
enum class WifiTarget {
    PHONE,
    TRUCK,
    NONE,
}

/**
 * SSID and passphrase for one hotspot. Passwords are never written into [toString].
 * Nothing in stage 1 hardcodes a real secret; callers supply the values.
 */
data class NetworkCredential(
    val ssid: String,
    val password: String,
) {
    override fun toString(): String = "NetworkCredential(ssid=$ssid, password=***)"
}

/**
 * User configuration for the priority engine.
 *
 * [enabled] is the master toggle. A null credential, or a blank SSID, means that
 * hotspot is not configured. Passwords are stored for the later connector and are
 * not read by [com.wifipriority.engine.PriorityEngine].
 */
data class PriorityConfig(
    val enabled: Boolean = false,
    val phone: NetworkCredential? = null,
    val truck: NetworkCredential? = null,
) {
    fun phoneSsid(): String? = phone?.ssid?.trim()?.takeIf { it.isNotEmpty() }

    fun truckSsid(): String? = truck?.ssid?.trim()?.takeIf { it.isNotEmpty() }

    fun hasNoNetworks(): Boolean = phoneSsid() == null && truckSsid() == null
}
