package com.example.brightnesslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listens for BOOT_COMPLETED and restarts [BrightnessService] so the
 * brightness cap continues to be enforced after a device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — starting BrightnessService.")
            val serviceIntent = Intent(context, BrightnessService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
