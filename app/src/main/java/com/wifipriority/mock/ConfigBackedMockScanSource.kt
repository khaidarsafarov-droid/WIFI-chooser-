package com.wifipriority.mock

import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.ScanSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Mock flavor stand-in: every [intervalMillis], emit the configured SSIDs as visible.
 * It does not call platform Wi-Fi APIs.
 */
class ConfigBackedMockScanSource(
    private val credentials: CredentialStore,
    private val clock: Clock,
    private val intervalMillis: Long = ScanPolicy.INTERVAL_MILLIS,
) : ScanSource {
    override val scans: Flow<ScanSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            val config = credentials.load()
            val visible = setOfNotNull(config.phoneSsid(), config.truckSsid())
            emit(ScanSnapshot(visible, clock.nowMillis(), wifiEnabled = true))
            delay(intervalMillis)
        }
    }
}
