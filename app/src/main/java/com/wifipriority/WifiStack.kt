package com.wifipriority

import android.content.Context
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.mock.FakeNetworkConnector
import com.wifipriority.mock.InMemoryCredentialStore
import com.wifipriority.mock.MockScanSource
import com.wifipriority.mock.MockScenario
import com.wifipriority.service.AppStores
import com.wifipriority.service.PriorityCoordinator
import com.wifipriority.service.PriorityStatusRepository
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.KeystoreCredentialStore
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionNetworkConnector
import com.wifipriority.wifi.WallClock
import com.wifipriority.wifi.WifiScanSource

/**
 * Mock factories stay available when [BuildConfig.USE_MOCK_WIFI] is true.
 * [appRuntime] is what the service uses on every flavor: platform scans and
 * [SuggestionNetworkConnector]. The scan line carries `build=mock` or
 * `build=real` so a device log shows which APK is installed. Credentials
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

    fun appRuntime(
        context: Context,
        clock: Clock = WallClock,
        awaitDeadline: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): AppRuntime {
        val credentials = KeystoreCredentialStore.create(context)
        val suggestion = SuggestionNetworkConnector(context)
        val scans = WifiScanSource(context, clock)
        val coordinator = PriorityCoordinator(
            scans = scans,
            connector = suggestion,
            credentials = credentials,
            engine = PriorityEngine(clock),
            status = PriorityStatusRepository.status,
            approval = suggestion.approvalState,
            clock = clock,
            policyStore = AppStores.policy(context),
            eventLog = AppStores.events(context),
            awaitDeadline = awaitDeadline,
            buildLabel = if (BuildConfig.USE_MOCK_WIFI) "mock" else "real",
        )
        return AppRuntime(
            credentials = credentials,
            scans = scans,
            connector = suggestion,
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
