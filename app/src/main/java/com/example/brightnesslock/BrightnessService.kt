package com.example.brightnesslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service responsible for keeping [BrightnessObserver] alive.
 *
 * Lifecycle:
 *  - Started by MainActivity on launch.
 *  - Restarted by [BootReceiver] after a reboot.
 *  - Returns START_STICKY so the system restarts it if it is killed.
 *
 * Only one observer is registered at a time (the flag [observerRegistered]
 * guards against registering duplicates when the service is re-started).
 */
class BrightnessService : Service() {

    companion object {
        private const val TAG = "BrightnessService"
        private const val CHANNEL_ID = "brightness_lock_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var brightnessObserver: BrightnessObserver
    private var observerRegistered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val handler = Handler(Looper.getMainLooper())

        // The observer always reads the freshest cap from SharedPreferences
        brightnessObserver = BrightnessObserver(
            handler = handler,
            contentResolver = contentResolver,
            getMaxBrightness = {
                getSharedPreferences("brightness_prefs", MODE_PRIVATE)
                    .getInt("max_brightness", 120)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately to avoid ANR / background restrictions
        startForeground(NOTIFICATION_ID, buildNotification())

        // Register the observer only once — guard against duplicate registrations
        // if onStartCommand is called again (e.g. START_STICKY restart)
        if (!observerRegistered) {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                /* notifyForDescendants = */ false,
                brightnessObserver
            )
            observerRegistered = true
            Log.d(TAG, "BrightnessObserver registered.")
        }

        // START_STICKY: if the system kills the service, restart it automatically
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (observerRegistered) {
            contentResolver.unregisterContentObserver(brightnessObserver)
            observerRegistered = false
            Log.d(TAG, "BrightnessObserver unregistered.")
        }
    }

    /** This service is not designed for binding. */
    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Brightness Lock",
            NotificationManager.IMPORTANCE_LOW          // silent, no sound/vibration
        ).apply {
            description = "Keeps brightness enforcement running in the background"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Brightness Lock Active")
            .setContentText("Screen brightness is being monitored.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)           // prevents the user from swiping it away
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
