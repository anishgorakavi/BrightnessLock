package com.example.brightnesslock

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import android.util.Log

/**
 * ContentObserver that watches Settings.System.SCREEN_BRIGHTNESS.
 *
 * When the system brightness rises above [maxBrightness], it immediately
 * resets it back to [maxBrightness].
 *
 * Loop-prevention: a simple boolean flag [isSetting] suppresses the observer
 * callback while we are writing the corrected value ourselves, preventing the
 * write from triggering another enforcement cycle.
 */
class BrightnessObserver(
    handler: Handler,
    private val contentResolver: ContentResolver,
    private val getMaxBrightness: () -> Int          // lambda so the cap is always fresh
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "BrightnessObserver"

        /**
         * Set to false while the parent is in Set Mode so the observer does not
         * fight the live slider preview. Must be reset to true before locking again.
         */
        @Volatile var isEnforcing: Boolean = true
    }

    // True while we are programmatically writing brightness — prevents re-entry
    @Volatile private var isSetting = false

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)

        // Skip if we triggered this change ourselves
        if (isSetting) return

        // Skip enforcement while the parent is actively adjusting brightness in Set Mode
        if (!isEnforcing) return

        try {
            val current = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            val max = getMaxBrightness()

            if (current > max) {
                Log.d(TAG, "Brightness $current exceeds cap $max — resetting.")
                isSetting = true
                try {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        max
                    )
                } finally {
                    // Always clear the flag, even if putInt throws
                    isSetting = false
                }
            }
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Could not read SCREEN_BRIGHTNESS: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to write SCREEN_BRIGHTNESS: ${e.message}")
        }
    }
}
