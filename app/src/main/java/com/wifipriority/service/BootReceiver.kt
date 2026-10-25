package com.wifipriority.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wifipriority.WifiStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts the priority service after boot when the user left the toggle enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = WifiStack.credentialStore(appContext).load()
                if (shouldStartService(config.enabled)) {
                    PriorityService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
