package com.wifipriority.wifi

/**
 * Remembers which network the callbacks are describing.
 * [onLost] for a different network must not clear the association we are tracking:
 * [ConnectivityManager.NetworkCallback] reports every Wi-Fi network, and a
 * stale loss would start the unvalidated watchdog while the tablet is still online.
 */
class LinkTracker {
    private var activeKey: Any? = null
    private var postedSsid: String? = null
    private var ssid: String? = null
    private var validated: Boolean = false
    private var known: Boolean = false

    fun snapshot(): LinkObservation = synchronized(this) {
        LinkObservation(
            connectedSsid = ssid,
            validated = validated,
            fromOurSuggestion = fromSuggestion(),
            known = known,
        )
    }

    /**
     * [active] is false for a Wi-Fi network that is not `ConnectivityManager.getActiveNetwork()`.
     * Those callbacks must not replace the association the watchdog and force-return read.
     * A null [ssid] on the same active network keeps the previous name so a redacted
     * `WifiInfo` does not wipe a validated link.
     */
    fun onCapabilities(
        networkKey: Any,
        ssid: String?,
        validated: Boolean,
        active: Boolean = true,
    ): LinkObservation = synchronized(this) {
        if (!active && activeKey != null && activeKey != networkKey) return@synchronized snapshotUnlocked()
        if (!active) return@synchronized snapshotUnlocked()
        val same = activeKey == networkKey
        activeKey = networkKey
        known = true
        this.ssid = when {
            ssid != null -> ssid
            same -> this.ssid
            else -> null
        }
        this.validated = validated
        snapshotUnlocked()
    }

    /** No active Wi-Fi network. Clears validation so a real outage can arm the watchdog. */
    fun onCleared(): LinkObservation = synchronized(this) {
        activeKey = null
        postedSsid = null
        ssid = null
        validated = false
        known = true
        snapshotUnlocked()
    }

    /** Null when [networkKey] is not the network currently reported as active. */
    fun onLost(networkKey: Any): LinkObservation? = synchronized(this) {
        if (activeKey != null && activeKey != networkKey) return@synchronized null
        activeKey = null
        postedSsid = null
        ssid = null
        validated = false
        known = true
        snapshotUnlocked()
    }

    fun onPosted(ssid: String?): LinkObservation = synchronized(this) {
        postedSsid = ssid
        snapshotUnlocked()
    }

    private fun snapshotUnlocked(): LinkObservation = LinkObservation(
        connectedSsid = ssid,
        validated = validated,
        fromOurSuggestion = fromSuggestion(),
        known = known,
    )

    private fun fromSuggestion(): Boolean? = when {
        ssid == null -> null
        postedSsid != null && postedSsid == ssid -> true
        postedSsid != null && postedSsid != ssid -> false
        else -> null
    }
}

/**
 * One Wi-Fi network from `ConnectivityManager`.
 * [active] is true only for `getActiveNetwork()` when that network is Wi-Fi.
 */
data class WifiNetworkView(
    val key: Any,
    val ssid: String?,
    val validated: Boolean,
    val active: Boolean,
)

/**
 * The association force-return and the watchdog are allowed to see.
 * A non-active network, including one left over after suggestion remove/add,
 * does not replace the active network. When the default network is not Wi-Fi,
 * a validated Wi-Fi network is the fallback.
 */
fun selectWifiLink(networks: List<WifiNetworkView>): WifiNetworkView? {
    networks.firstOrNull { it.active }?.let { return it }
    return networks.firstOrNull { it.validated && !it.ssid.isNullOrBlank() }
        ?: networks.firstOrNull { it.validated }
}
