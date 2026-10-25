package com.wifipriority.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifipriority.R
import com.wifipriority.WifiStack
import com.wifipriority.service.PriorityService
import com.wifipriority.service.PriorityStatus
import com.wifipriority.service.PriorityStatusRepository
import com.wifipriority.wifi.NetworkCredential
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.SuggestionPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsForm(
    val enabled: Boolean = false,
    val phoneSsid: String = "",
    val phonePassword: String = "",
    val truckSsid: String = "",
    val truckPassword: String = "",
    val revealPhonePassword: Boolean = false,
    val revealTruckPassword: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WifiStack.credentialStore(application)
    private val formState = MutableStateFlow(SettingsForm())
    private val warningsState = MutableStateFlow<List<String>>(emptyList())

    val form: StateFlow<SettingsForm> = formState.asStateFlow()
    val warnings: StateFlow<List<String>> = warningsState.asStateFlow()
    val status: StateFlow<PriorityStatus> = PriorityStatusRepository.status

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = store.load()
            formState.value = formState.value.copy(
                enabled = config.enabled,
                phoneSsid = config.phone?.ssid.orEmpty(),
                phonePassword = config.phone?.password.orEmpty(),
                truckSsid = config.truck?.ssid.orEmpty(),
                truckPassword = config.truck?.password.orEmpty(),
            )
            if (config.enabled) {
                PriorityService.start(getApplication())
            }
            refreshWarnings()
        }
    }

    fun onEnabled(enabled: Boolean) {
        formState.value = formState.value.copy(enabled = enabled, message = null)
    }

    fun onPhoneSsid(value: String) {
        formState.value = formState.value.copy(phoneSsid = value, message = null)
    }

    fun onPhonePassword(value: String) {
        formState.value = formState.value.copy(phonePassword = value, message = null)
    }

    fun onTruckSsid(value: String) {
        formState.value = formState.value.copy(truckSsid = value, message = null)
    }

    fun onTruckPassword(value: String) {
        formState.value = formState.value.copy(truckPassword = value, message = null)
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
        viewModelScope.launch(Dispatchers.IO) {
            store.save(config)
            if (config.enabled) {
                PriorityService.start(getApplication())
            } else {
                PriorityService.disable(getApplication())
            }
            formState.value = form.copy(message = getApplication<Application>().getString(R.string.saved))
            refreshWarnings()
        }
    }

    fun refreshWarnings() {
        warningsState.value = buildWarnings(getApplication(), formState.value, status.value)
    }

    private fun credentialOrNull(ssid: String, password: String): NetworkCredential? {
        if (ssid.isBlank() && password.isBlank()) return null
        return NetworkCredential(ssid.trim(), password)
    }
}

internal fun buildWarnings(context: Context, form: SettingsForm, status: PriorityStatus): List<String> {
    val warnings = mutableListOf<String>()
    if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
        warnings += context.getString(R.string.warn_location_permission)
    }
    if (!hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)) {
        warnings += context.getString(R.string.warn_nearby_wifi)
    }
    if (!hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
        warnings += context.getString(R.string.warn_notifications)
    }
    val locationOn = context.getSystemService(LocationManager::class.java).isLocationEnabled
    if (!locationOn) {
        warnings += context.getString(R.string.warn_location_off)
    }
    val wifiOn = context.getSystemService(WifiManager::class.java).isWifiEnabled
    if (!wifiOn) {
        warnings += context.getString(R.string.warn_wifi_off)
    }
    val power = context.getSystemService(PowerManager::class.java)
    if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
        warnings += context.getString(R.string.warn_battery)
    }
    if (form.phonePassword.isNotEmpty() && form.phonePassword.length < SuggestionPlanner.MIN_PASSPHRASE_LENGTH) {
        warnings += context.getString(R.string.warn_phone_passphrase)
    }
    if (form.truckPassword.isNotEmpty() && form.truckPassword.length < SuggestionPlanner.MIN_PASSPHRASE_LENGTH) {
        warnings += context.getString(R.string.warn_truck_passphrase)
    }
    when (status.suggestionApproval) {
        SuggestionApproval.PENDING -> warnings += context.getString(R.string.suggestions_pending)
        SuggestionApproval.REJECTED -> warnings += context.getString(R.string.suggestions_rejected)
        SuggestionApproval.UNKNOWN, SuggestionApproval.APPROVED -> Unit
    }
    return warnings
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("BatteryLife")
fun batteryOptimizationIntent(context: Context) = android.content.Intent(
    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    "package:${context.packageName}".toUri(),
)
