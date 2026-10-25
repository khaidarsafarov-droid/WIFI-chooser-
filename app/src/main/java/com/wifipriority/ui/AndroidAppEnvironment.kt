package com.wifipriority.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.wifipriority.WifiStack
import com.wifipriority.service.AppStores
import com.wifipriority.service.PriorityService
import com.wifipriority.service.PriorityStatus
import com.wifipriority.service.PriorityStatusRepository
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.PriorityConfig
import kotlinx.coroutines.flow.StateFlow

class AndroidAppEnvironment(
    context: Context,
) : AppEnvironment {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override val status: StateFlow<PriorityStatus> = PriorityStatusRepository.status
    override val events: StateFlow<List<LogEvent>> = AppStores.events(app).recent

    override suspend fun loadConfig(): PriorityConfig = WifiStack.credentialStore(app).load()

    override suspend fun saveConfig(config: PriorityConfig) {
        WifiStack.credentialStore(app).save(config)
    }

    override fun signals(): DeviceSignals {
        val wifiOn = runCatching {
            app.getSystemService(WifiManager::class.java).isWifiEnabled
        }.getOrDefault(true)
        val locationOn = runCatching {
            app.getSystemService(LocationManager::class.java).isLocationEnabled
        }.getOrDefault(true)
        val batteryExempt = runCatching {
            app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName)
        }.getOrDefault(false)
        val whitelisted = runCatching { app.packageManager.isAutoRevokeWhitelisted }.getOrDefault(false)
        return DeviceSignals(
            fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            nearbyWifi = granted(Manifest.permission.NEARBY_WIFI_DEVICES),
            notifications = granted(Manifest.permission.POST_NOTIFICATIONS),
            locationEnabled = locationOn,
            wifiEnabled = wifiOn,
            batteryExempt = batteryExempt,
            autoRevokeWhitelisted = whitelisted,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            samsungHintDismissed = prefs.getBoolean(SAMSUNG_DISMISSED, false),
        )
    }

    override fun startService() {
        PriorityService.start(app)
    }

    override fun stopService() {
        PriorityService.disable(app)
    }

    override fun acknowledgeSamsungHint() {
        prefs.edit { putBoolean(SAMSUNG_DISMISSED, true) }
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val PREFS = "wifi_priority_ui"
        const val SAMSUNG_DISMISSED = "samsung_hint_dismissed"
    }
}
