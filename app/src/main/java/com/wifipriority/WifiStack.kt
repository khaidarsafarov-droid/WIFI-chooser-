package com.wifipriority

import android.content.Context
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.mock.ConfigBackedMockScanSource
import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.service.PriorityCoordinator
import com.wifipriority.service.PriorityStatusRepository
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.KeystoreCredentialStore
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.SuggestionNetworkConnector
import com.wifipriority.wifi.WallClock
import com.wifipriority.wifi.WifiScanSource
import kotlinx.coroutines.flow.flowOf

/**
 * Mock entry points stay available when [BuildConfig.USE_MOCK_WIFI] is true.
 * [appRuntime] is what the service and settings screen use: real Wi-Fi on the
 * `real` flavor, mocks for scan and connect on the `mock` flavor. Credentials
 * always go through [KeystoreCredentialStore] on device.
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

    fun credentialStore(context: Context): CredentialStore = KeystoreCredentialStore.create(context)

    fun appRuntime(context: Context, clock: Clock = WallClock): AppRuntime {
        val credentials = KeystoreCredentialStore.create(context)
        val suggestion: SuggestionNetworkConnector?
        val connector: NetworkConnector
        val scans: ScanSource
        if (BuildConfig.USE_MOCK_WIFI) {
            suggestion = null
            connector = FakeNetworkConnector(credentials)
            scans = ConfigBackedMockScanSource(credentials, clock)
        } else {
            val real = SuggestionNetworkConnector(context, credentials)
            suggestion = real
            connector = real
            scans = WifiScanSource(context, clock)
        }
        val coordinator = PriorityCoordinator(
            scans = scans,
            connector = connector,
            credentials = credentials,
            engine = PriorityEngine(clock),
            status = PriorityStatusRepository.status,
            approval = suggestion?.approvalState ?: flowOf(SuggestionApproval.UNKNOWN),
        )
        return AppRuntime(
            credentials = credentials,
            scans = scans,
            connector = connector,
            suggestionConnector = suggestion,
            coordinator = coordinator,
        )
    }

    private inline fun <T> mockOnly(factory: () -> T): T {
        check(BuildConfig.USE_MOCK_WIFI) {
            "USE_MOCK_WIFI is false. Use the real flavor's WifiScanSource and SuggestionNetworkConnector."
        }
        return factory()
    }

    data class AppRuntime(
        val credentials: CredentialStore,
        val scans: ScanSource,
        val connector: NetworkConnector,
        val suggestionConnector: SuggestionNetworkConnector?,
        val coordinator: PriorityCoordinator,
    )
}
