package com.wifipriority.wifi

import android.content.Context

/**
 * Synchronous copy of the enabled flag. Boot and package-replaced receivers
 * must call `startForegroundService` on the receiver thread: reading DataStore
 * after `goAsync` runs outside the background-start exemption and the process
 * can be killed before the service exists.
 */
object EnabledLatch {
    const val PREFS = "wifi_priority_boot"
    const val KEY = "enabled"

    fun read(context: Context): Boolean? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) return null
        return prefs.getBoolean(KEY, false)
    }

    fun write(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .commit()
    }
}

/**
 * [savedEnabled] null means the latch was never written (upgrade). Start anyway;
 * the service stops itself if the stored config is disabled.
 * A known `false` must not start a foreground service on every boot.
 */
fun shouldStartOnBoot(action: String?, savedEnabled: Boolean?): Boolean {
    val restart = action == ACTION_BOOT_COMPLETED || action == ACTION_MY_PACKAGE_REPLACED
    return restart && savedEnabled != false
}

internal const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
internal const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
