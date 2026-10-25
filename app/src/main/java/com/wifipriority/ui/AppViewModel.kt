package com.wifipriority.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wifipriority.service.PriorityStatus
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.NetworkCredential
import com.wifipriority.wifi.PriorityConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface AppEnvironment {
    val status: StateFlow<PriorityStatus>
    val events: StateFlow<List<LogEvent>>
    suspend fun loadConfig(): PriorityConfig
    suspend fun saveConfig(config: PriorityConfig)
    fun signals(): DeviceSignals
    fun startService()
    fun stopService()
    fun acknowledgeSamsungHint()
}

class AppViewModel(
    private val env: AppEnvironment,
) : ViewModel() {
    private val formState = MutableStateFlow(SettingsForm())
    private val configState = MutableStateFlow(PriorityConfig())
    private val homeState = MutableStateFlow(homeUi(PriorityConfig(), PriorityStatus()))
    private val bannerState = MutableStateFlow<List<BannerId>>(emptyList())
    private val diagnosticState = MutableStateFlow(diagnostics(PriorityConfig(), PriorityStatus(), emptyList()))
    private val startState = MutableStateFlow<StartDestination?>(null)
    private val navigationState = MutableStateFlow<NavRequest?>(null)
    private var events = emptyList<LogEvent>()

    val form: StateFlow<SettingsForm> = formState.asStateFlow()
    val home: StateFlow<HomeUi> = homeState.asStateFlow()
    val banners: StateFlow<List<BannerId>> = bannerState.asStateFlow()
    val diagnostics: StateFlow<DiagnosticsUi> = diagnosticState.asStateFlow()
    val start: StateFlow<StartDestination?> = startState.asStateFlow()
    val navigation: StateFlow<NavRequest?> = navigationState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = env.loadConfig()
            configState.value = config
            formState.value = formFrom(config)
            refresh()
            startState.value = startDestination(config)
            if (config.enabled) env.startService()
        }
        viewModelScope.launch {
            env.status.collect {
                refresh()
            }
        }
        viewModelScope.launch {
            env.events.collect { latest ->
                events = latest
                refresh()
            }
        }
    }

    fun onEnabled(enabled: Boolean) {
        formState.value = formState.value.copy(enabled = enabled)
    }

    fun onPhoneSsid(value: String) {
        formState.value = formState.value.copy(phoneSsid = value)
    }

    fun onPhonePassword(value: String) {
        formState.value = formState.value.copy(phonePassword = value)
    }

    fun onTruckSsid(value: String) {
        formState.value = formState.value.copy(truckSsid = value)
    }

    fun onTruckPassword(value: String) {
        formState.value = formState.value.copy(truckPassword = value)
    }

    fun togglePhonePassword() {
        formState.value = formState.value.copy(revealPhonePassword = !formState.value.revealPhonePassword)
    }

    fun toggleTruckPassword() {
        formState.value = formState.value.copy(revealTruckPassword = !formState.value.revealTruckPassword)
    }

    fun save() {
        val form = formState.value
        val config = PriorityConfig(
            enabled = form.enabled,
            phone = credentialOrNull(form.phoneSsid, form.phonePassword),
            truck = credentialOrNull(form.truckSsid, form.truckPassword),
        )
        viewModelScope.launch {
            env.saveConfig(config)
            configState.value = config
            if (config.enabled) env.startService() else env.stopService()
            refresh()
            navigationState.value = NavRequest.HOME
        }
    }

    fun consumeNavigation() {
        navigationState.value = null
    }

    fun dismissSamsungHint() {
        env.acknowledgeSamsungHint()
        refresh()
    }

    fun refreshSignals() {
        refresh()
    }

    private fun refresh() {
        val config = configState.value
        val status = env.status.value
        homeState.value = homeUi(config, status)
        bannerState.value = deviceBanners(env.signals(), status, config)
        diagnosticState.value = diagnostics(config, status, events)
    }

    private fun credentialOrNull(ssid: String, password: String): NetworkCredential? {
        if (ssid.isBlank() && password.isBlank()) return null
        return NetworkCredential(ssid.trim(), password)
    }

    private fun formFrom(config: PriorityConfig) = SettingsForm(
        enabled = config.enabled,
        phoneSsid = config.phone?.ssid.orEmpty(),
        phonePassword = config.phone?.password.orEmpty(),
        truckSsid = config.truck?.ssid.orEmpty(),
        truckPassword = config.truck?.password.orEmpty(),
    )
}

enum class NavRequest { HOME }

class AppViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(AndroidAppEnvironment(app)) as T
        }
        error("Unknown model ${modelClass.name}")
    }
}

data class SettingsForm(
    val enabled: Boolean = false,
    val phoneSsid: String = "",
    val phonePassword: String = "",
    val truckSsid: String = "",
    val truckPassword: String = "",
    val revealPhonePassword: Boolean = false,
    val revealTruckPassword: Boolean = false,
)
