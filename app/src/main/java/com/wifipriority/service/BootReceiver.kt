package com.wifipriority.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wifipriority.wifi.EnabledLatch
import com.wifipriority.wifi.shouldStartOnBoot

/**
 * Starts the priority service after boot when the user left the toggle enabled.
 * The start call is synchronous. A DataStore read here used to run after
 * [BroadcastReceiver.goAsync], outside the boot foreground-service exemption,
 * so the process could die before [PriorityService] called `startForeground`.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!shouldStartOnBoot(intent?.action, EnabledLatch.read(context))) return
        PriorityService.start(context.applicationContext)
    }
}

/** Restarts the service after an update so an unattended tablet does not stay stopped. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!shouldStartOnBoot(intent?.action, EnabledLatch.read(context))) return
        PriorityService.start(context.applicationContext)
    }
}
