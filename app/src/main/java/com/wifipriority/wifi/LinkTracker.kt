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

    fun snapshot(): LinkObservation = LinkObservation(
        connectedSsid = ssid,
        validated = validated && ssid != null,
        fromOurSuggestion = fromSuggestion(),
        known = known,
    )

    fun onCapabilities(networkKey: Any, ssid: String?, validated: Boolean): LinkObservation {
        activeKey = networkKey
        known = true
        this.ssid = ssid
        this.validated = validated
        return snapshot()
    }

    fun onPosted(ssid: String?): LinkObservation {
        postedSsid = ssid
        return snapshot()
    }

    /** Null when [networkKey] is not the network currently reported as active. */
    fun onLost(networkKey: Any): LinkObservation? {
        if (activeKey != null && activeKey != networkKey) return null
        activeKey = null
        postedSsid = null
        ssid = null
        validated = false
        known = true
        return snapshot()
    }

    private fun fromSuggestion(): Boolean? = when {
        ssid == null -> null
        postedSsid != null && postedSsid == ssid -> true
        postedSsid != null && postedSsid != ssid -> false
        else -> null
    }
}
