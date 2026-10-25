package com.wifipriority.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri

@SuppressLint("BatteryLife")
fun batteryOptimizationIntent(context: Context): Intent = Intent(
    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    "package:${context.packageName}".toUri(),
)

fun wifiPanelIntent(): Intent = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun wifiSettingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun locationSettingsIntent(): Intent =
    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun appDetailsIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = "package:${context.packageName}".toUri()
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Opens the unused-app restriction screen when the platform provides it. */
fun autoRevokeIntent(context: Context): Intent {
    val auto = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        data = "package:${context.packageName}".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val resolved = context.packageManager.resolveActivity(
        auto,
        PackageManager.ResolveInfoFlags.of(0),
    )
    return if (resolved != null) auto else appDetailsIntent(context)
}
