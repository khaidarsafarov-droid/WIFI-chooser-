package com.wifipriority.wifi

import kotlinx.coroutines.flow.Flow

/**
 * Applies a priority target and reports the SSID the device is associated with.
 *
 * `null` means disconnected or unknown. Stage 2 registers both hotspots via
 * `WifiManager.addNetworkSuggestions` / `removeNetworkSuggestions` (phone at
 * higher priority) so internet is device-wide, and temporarily removes the
 * truck suggestion when the phone SSID returns. It must not call `addNetwork`,
 * `enableNetwork`, `setWifiEnabled`, or `requestNetwork` + `WifiNetworkSpecifier`.
 */
interface NetworkConnector {
    val connectedSsid: Flow<String?>

    suspend fun applyTarget(target: WifiTarget)
}
