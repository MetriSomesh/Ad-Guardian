package com.rockstar.orderguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the status service after a reboot so protection does not silently stay off. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GuardianForegroundService.start(context)
        }
    }
}
