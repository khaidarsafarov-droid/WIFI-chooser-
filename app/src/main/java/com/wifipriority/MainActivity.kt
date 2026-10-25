package com.wifipriority

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.wifipriority.ui.SettingsScreen
import com.wifipriority.ui.SettingsViewModel
import com.wifipriority.ui.batteryOptimizationIntent

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()
    private var askedBattery = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshWarnings()
        maybeAskBatteryOptimization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(viewModel)
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
        viewModel.refreshWarnings()
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
