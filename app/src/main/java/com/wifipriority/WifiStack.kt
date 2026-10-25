package com.wifipriority

import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanSource

/**
 * Stage-1 composition root. [BuildConfig.USE_MOCK_WIFI] is true by default, so
 * every entry point returns an in-memory fake. The real Wi-Fi stack is not in
 * this stage; flipping the flag fails fast instead of calling platform APIs.
 */
object WifiStack {
    fun scanSource(
        clock: Clock,
        scenario: MockScenario,
        phoneSsid: String,
        truckSsid: String,
    ): ScanSource = mockOnly {
        MockScanSource.forScenario(
            scenario = scenario,
            phoneSsid = phoneSsid,
            truckSsid = truckSsid,
            clock = clock,
        )
    }

    fun networkConnector(
        credentials: CredentialStore = InMemoryCredentialStore(),
    ): NetworkConnector = mockOnly {
        FakeNetworkConnector(credentials)
    }

    fun credentialStore(
        initial: PriorityConfig = PriorityConfig(),
    ): CredentialStore = mockOnly {
        InMemoryCredentialStore(initial)
    }

    private inline fun <T> mockOnly(factory: () -> T): T {
        check(BuildConfig.USE_MOCK_WIFI) {
            "USE_MOCK_WIFI is false, but the real Wi-Fi stack is not implemented in stage 1."
        }
        return factory()
    }
}
