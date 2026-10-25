package com.wifipriority.mock

import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records [applyTarget] calls and reports the SSID from [credentials] for that target.
 * Does not touch the platform Wi-Fi APIs.
 */
class FakeNetworkConnector(
    private val credentials: CredentialStore = InMemoryCredentialStore(),
) : NetworkConnector {
    private val connected = MutableStateFlow<String?>(null)
    private val applied = mutableListOf<WifiTarget>()

    override val connectedSsid: Flow<String?> = connected.asStateFlow()

    fun appliedTargets(): List<WifiTarget> = synchronized(applied) { applied.toList() }

    override suspend fun applyTarget(target: WifiTarget) {
        val config = credentials.load()
        synchronized(applied) { applied += target }
        connected.value = when (target) {
            WifiTarget.PHONE -> config.phoneSsid()
            WifiTarget.TRUCK -> config.truckSsid()
            WifiTarget.NONE -> null
        }
    }
}
