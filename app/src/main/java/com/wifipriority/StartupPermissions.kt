package com.wifipriority

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

object StartupPermissions {
    val runtime: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    fun missing(context: Context): List<String> = runtime.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    fun needsBatteryExemption(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
