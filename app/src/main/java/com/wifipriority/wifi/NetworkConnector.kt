package com.wifipriority.wifi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Current Wi-Fi association. [validated] is [android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED].
 * [fromOurSuggestion] is true after `ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION` for this SSID,
 * false once it is clear the association is not ours, and null while that is still unknown.
 */
data class LinkObservation(
    val connectedSsid: String? = null,
    val validated: Boolean = false,
    val fromOurSuggestion: Boolean? = null,
)

/**
 * Applies a priority target and the suggestion list from [SuggestionPolicy].
 *
 * The real connector registers `WifiNetworkSuggestion`s. It must not call
 * `addNetwork`, `enableNetwork`, `setWifiEnabled`, or `requestNetwork` +
 * `WifiNetworkSpecifier`. Truck is removed only for a short force-return, and
 * that removal uses `removeNetworkSuggestions` with
 * `ACTION_REMOVE_SUGGESTION_DISCONNECT` so a suggestion we own is dropped.
 * A network the user saved by hand is not disconnected by that call.
 */
interface NetworkConnector {
    val connectedSsid: Flow<String?>

    val link: Flow<LinkObservation>
        get() = connectedSsid.map { ssid ->
            LinkObservation(
                connectedSsid = ssid,
                validated = ssid != null,
                fromOurSuggestion = if (ssid == null) null else true,
            )
        }

    val connectionFailures: Flow<ConnectionFailure>
        get() = emptyFlow()

    val suggestionAddStatus: Flow<Int?>
        get() = flowOf(null)

    suspend fun applyTarget(target: WifiTarget)

    suspend fun applySuggestions(
        suggestions: List<PlannedSuggestion>,
        disconnectSsids: Set<String> = emptySet(),
        force: Boolean = false,
    ) = Unit
}
