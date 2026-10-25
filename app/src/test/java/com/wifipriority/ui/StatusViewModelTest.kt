package com.wifipriority.ui

import com.wifipriority.enabledConfig
import com.wifipriority.service.PriorityStatus
import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModelTest {
    private val env = FakeEnvironment()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyConfig_opensSettings() {
        env.config = PriorityConfig()
        val viewModel = AppViewModel(env)
        assertEquals(StartDestination.SETTINGS, viewModel.start.value)
    }

    @Test
    fun savedConfig_opensHome_andPasswordsStayMasked() {
        env.config = enabledConfig()
        val viewModel = AppViewModel(env)
        assertEquals(StartDestination.HOME, viewModel.start.value)
        assertFalse(viewModel.form.value.revealPhonePassword)
        assertFalse(viewModel.form.value.revealTruckPassword)
        assertEquals("PhoneHotspot", viewModel.form.value.phoneSsid)
    }

    @Test
    fun save_requestsHome() {
        env.config = PriorityConfig()
        val viewModel = AppViewModel(env)
        viewModel.onPhoneSsid("PhoneHotspot")
        viewModel.onPhonePassword("phone-test-password")
        viewModel.onEnabled(true)
        viewModel.save()
        assertEquals(NavRequest.HOME, viewModel.navigation.value)
        assertTrue(env.started)
    }

    @Test
    fun cards_connectedVisibleAndNotVisible() {
        env.config = enabledConfig()
        val viewModel = AppViewModel(env)
        env.status.value = PriorityStatus(
            connectedSsid = "PhoneHotspot",
            visibleSsids = setOf("PhoneHotspot"),
            target = WifiTarget.PHONE,
        )
        viewModel.refreshSignals()
        val home = viewModel.home.value
        assertEquals(CardPresence.CONNECTED, home.phone.presence)
        assertTrue(home.phone.highlighted)
        assertEquals(CardPresence.NOT_VISIBLE, home.truck.presence)
        assertFalse(home.truck.highlighted)
        env.status.value = PriorityStatus(
            connectedSsid = "PhoneHotspot",
            visibleSsids = setOf("PhoneHotspot", "TruckHotspot"),
            target = WifiTarget.PHONE,
        )
        viewModel.refreshSignals()
        assertEquals(CardPresence.VISIBLE, viewModel.home.value.truck.presence)
    }

    @Test
    fun truckConnectedWhilePhoneIsPriority_explainsWhy() {
        env.config = enabledConfig()
        val viewModel = AppViewModel(env)
        env.status.value = PriorityStatus(
            connectedSsid = "TruckHotspot",
            visibleSsids = setOf("TruckHotspot"),
            target = WifiTarget.PHONE,
            connectionFromSuggestion = false,
        )
        viewModel.refreshSignals()
        val home = viewModel.home.value
        assertEquals(CardPresence.CONNECTED, home.truck.presence)
        assertTrue(home.truck.highlighted)
        assertEquals(CardPresence.NOT_VISIBLE, home.phone.presence)
        assertEquals(TruckNote.PHONE_NOT_VISIBLE, home.truckNote)
        assertTrue(BannerId.SAVED_NETWORK_BLOCKS in viewModel.banners.value)

        env.status.value = PriorityStatus(
            connectedSsid = "TruckHotspot",
            visibleSsids = setOf("PhoneHotspot", "TruckHotspot"),
            target = WifiTarget.PHONE,
            connectionFromSuggestion = true,
        )
        viewModel.refreshSignals()
        assertEquals(TruckNote.SWITCHING_BACK, viewModel.home.value.truckNote)
        assertFalse(BannerId.SAVED_NETWORK_BLOCKS in viewModel.banners.value)
    }

    @Test
    fun autoRevokeAndRuntimePermissions_showBanners() {
        env.config = enabledConfig()
        env.signals = DeviceSignals(
            fineLocation = false,
            nearbyWifi = false,
            notifications = false,
            batteryExempt = false,
            autoRevokeWhitelisted = false,
            manufacturer = "samsung",
        )
        val viewModel = AppViewModel(env)
        val banners = viewModel.banners.value
        assertTrue(BannerId.FINE_LOCATION in banners)
        assertTrue(BannerId.NEARBY_WIFI in banners)
        assertTrue(BannerId.NOTIFICATIONS in banners)
        assertTrue(BannerId.BATTERY in banners)
        assertTrue(BannerId.AUTO_REVOKE in banners)
        assertTrue(BannerId.SAMSUNG_NEVER_SLEEPING in banners)

        env.signals = DeviceSignals(autoRevokeWhitelisted = true, manufacturer = "Google")
        viewModel.refreshSignals()
        assertFalse(BannerId.AUTO_REVOKE in viewModel.banners.value)
        assertFalse(BannerId.FINE_LOCATION in viewModel.banners.value)
        assertFalse(BannerId.SAMSUNG_NEVER_SLEEPING in viewModel.banners.value)
    }

    @Test
    fun serviceDisabled_showsBanner() {
        env.config = enabledConfig(enabled = false)
        val viewModel = AppViewModel(env)
        assertTrue(BannerId.SERVICE_DISABLED in viewModel.banners.value)
        assertFalse(viewModel.home.value.running)
    }

    @Test
    fun wrongPassword_showsBanner() {
        env.config = enabledConfig()
        val viewModel = AppViewModel(env)
        env.status.value = PriorityStatus(
            lastFailureReason = FailureReason.AUTHENTICATION,
            lastFailureSsid = "PhoneHotspot",
            phoneFailures = 3,
            suggestionApproval = SuggestionApproval.APPROVED,
        )
        viewModel.refreshSignals()
        assertTrue(BannerId.WRONG_PASSWORD in viewModel.banners.value)
        assertTrue(BannerId.CANNOT_CONNECT_PHONE in viewModel.banners.value)
    }

    @Test
    fun missingSuggestions_andBlankPassword_showBanners() {
        env.config = enabledConfig(phonePassword = "")
        val viewModel = AppViewModel(env)
        env.status.value = PriorityStatus(
            suggestionsMissing = true,
            suggestionApproval = SuggestionApproval.PENDING,
        )
        viewModel.refreshSignals()
        assertTrue(BannerId.SUGGESTIONS_MISSING in viewModel.banners.value)
        assertTrue(BannerId.PASSWORD_MISSING in viewModel.banners.value)
        assertTrue(BannerId.SUGGESTIONS in viewModel.banners.value)
    }

    private class FakeEnvironment : AppEnvironment {
        var config = PriorityConfig()
        var signals = DeviceSignals()
        var started = false
        override val status = MutableStateFlow(PriorityStatus())
        override val events = MutableStateFlow<List<LogEvent>>(emptyList())
        override suspend fun loadConfig(): PriorityConfig = config
        override suspend fun saveConfig(config: PriorityConfig) {
            this.config = config
        }
        override fun signals(): DeviceSignals = signals
        override fun startService() {
            started = true
        }
        override fun stopService() {
            started = false
        }
        override fun acknowledgeSamsungHint() {
            signals = signals.copy(samsungHintDismissed = true)
        }
    }
}
