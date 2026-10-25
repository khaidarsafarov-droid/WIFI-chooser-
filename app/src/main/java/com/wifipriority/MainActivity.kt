package com.wifipriority

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.content.edit
import com.wifipriority.ui.AppViewModel
import com.wifipriority.ui.AppViewModelFactory
import com.wifipriority.ui.BannerId
import com.wifipriority.ui.WifiApp
import com.wifipriority.ui.appDetailsIntent
import com.wifipriority.ui.autoRevokeIntent
import com.wifipriority.ui.batteryOptimizationIntent
import com.wifipriority.ui.locationSettingsIntent
import com.wifipriority.service.SuggestionCommands
import com.wifipriority.ui.wifiPanelIntent

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModelFactory(application) }
    private var askedBattery = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshSignals()
        maybeAskBatteryOptimization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WifiApp(viewModel, onFix = ::onFix)
            }
        }
        val missing = StartupPermissions.missing(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            maybeAskBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSignals()
    }

    private fun onFix(banner: BannerId) {
        when (banner) {
            BannerId.WIFI_OFF, BannerId.SAVED_NETWORK_BLOCKS -> startActivity(wifiPanelIntent())
            BannerId.FINE_LOCATION -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            BannerId.NEARBY_WIFI -> permissionLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
            BannerId.NOTIFICATIONS -> permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            BannerId.LOCATION_OFF -> startActivity(locationSettingsIntent())
            BannerId.SUGGESTIONS, BannerId.SUGGESTIONS_MISSING -> SuggestionCommands.reregister(this)
            BannerId.BATTERY -> startActivity(batteryOptimizationIntent(this))
            BannerId.AUTO_REVOKE -> startActivity(autoRevokeIntent(this))
            BannerId.SAMSUNG_NEVER_SLEEPING -> startActivity(appDetailsIntent(this))
            BannerId.SERVICE_DISABLED,
            BannerId.CANNOT_CONNECT_PHONE,
            BannerId.WRONG_PASSWORD,
            BannerId.PHONE_PASSPHRASE,
            BannerId.TRUCK_PASSPHRASE,
            BannerId.PASSWORD_MISSING,
            -> Unit
        }
    }

    @SuppressLint("BatteryLife")
    private fun maybeAskBatteryOptimization() {
        if (askedBattery || !StartupPermissions.needsBatteryExemption(this)) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(ASKED_BATTERY, false)) return
        prefs.edit { putBoolean(ASKED_BATTERY, true) }
        askedBattery = true
        startActivity(batteryOptimizationIntent(this))
    }

    private companion object {
        const val PREFS = "wifi_priority_ui"
        const val ASKED_BATTERY = "asked_battery"
    }
}
