package com.example.brightnesslock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brightnesslock.ui.theme.BrightnessLockTheme

/** The three UI states the app can be in. */
private enum class AppState { LOCKED, UNLOCK_CHALLENGE, SET_MODE }

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "brightness_prefs"
        private const val KEY_MAX_BRIGHTNESS = "max_brightness"
        private const val DEFAULT_MAX_BRIGHTNESS = 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Request WRITE_SETTINGS permission if not already granted.
        ensureWriteSettingsPermission()

        // 2. Force manual brightness mode so auto-brightness can't override us.
        forceManualBrightness()

        // 3. Start the monitoring service (idempotent — guarded inside the service).
        startForegroundService(Intent(this, BrightnessService::class.java))

        setContent {
            BrightnessLockTheme {
                BrightnessLockApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply manual brightness mode every time we come to foreground,
        // in case another app switched it back.
        forceManualBrightness()
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun ensureWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            Log.d(TAG, "WRITE_SETTINGS not granted — opening system settings.")
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    /** Switches the brightness mode to manual (disables auto-brightness). */
    private fun forceManualBrightness() {
        if (!Settings.System.canWrite(this)) return
        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not set brightness mode: ${e.message}")
        }
    }
}

// ── Composable UI ─────────────────────────────────────────────────────────────

@Composable
fun BrightnessLockApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("brightness_prefs", Context.MODE_PRIVATE)

    // Current UI state
    var appState by remember { mutableStateOf(AppState.LOCKED) }

    // The saved maximum brightness read from SharedPreferences
    var maxBrightness by remember {
        mutableIntStateOf(prefs.getInt("max_brightness", 120))
    }

    // Live brightness read from the system (shown in Locked Mode)
    var currentBrightness by remember {
        mutableIntStateOf(readCurrentBrightness(context))
    }

    // Math challenge state
    var challenge by remember { mutableStateOf(MathChallengeHelper.generate()) }
    var answerInput by remember { mutableStateOf("") }
    var challengeError by remember { mutableStateOf(false) }

    // Slider value used in Set Mode for live preview
    var sliderValue by remember { mutableFloatStateOf(maxBrightness.toFloat()) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (appState) {

                // ── LOCKED MODE ───────────────────────────────────────────────
                AppState.LOCKED -> {
                    // Refresh current brightness every time we land on this screen
                    currentBrightness = readCurrentBrightness(context)

                    Text(
                        text = "Brightness Lock",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Current Brightness: $currentBrightness", fontSize = 16.sp)
                    Text("Max Allowed: $maxBrightness", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Status: Locked",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = {
                        // Generate a fresh challenge each time Unlock is pressed
                        challenge = MathChallengeHelper.generate()
                        answerInput = ""
                        challengeError = false
                        appState = AppState.UNLOCK_CHALLENGE
                    }) {
                        Text("Unlock")
                    }
                }

                // ── UNLOCK / MATH CHALLENGE MODE ──────────────────────────────
                AppState.UNLOCK_CHALLENGE -> {
                    Text(
                        text = "Solve to unlock",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = challenge.question,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = answerInput,
                        onValueChange = {
                            answerInput = it
                            challengeError = false
                        },
                        label = { Text("Your answer") },
                        isError = challengeError,
                        supportingText = {
                            if (challengeError) Text("Wrong answer, try again.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { appState = AppState.LOCKED }) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            val userInt = answerInput.trim().toIntOrNull()
                            if (userInt != null && MathChallengeHelper.verify(userInt, challenge)) {
                                // Correct — pause enforcement so the slider preview isn't reset
                                BrightnessObserver.isEnforcing = false
                                sliderValue = maxBrightness.toFloat()
                                appState = AppState.SET_MODE
                            } else {
                                challengeError = true
                            }
                        }) {
                            Text("Submit")
                        }
                    }
                }

                // ── SET MODE (Parent Mode) ────────────────────────────────────
                AppState.SET_MODE -> {
                    Text(
                        text = "Set Max Brightness",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current value: ${sliderValue.toInt()}",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider: 0–255, live preview of brightness as parent drags
                    Slider(
                        value = sliderValue,
                        onValueChange = { newVal ->
                            sliderValue = newVal
                            // Write brightness immediately for live feedback
                            applyBrightness(context, newVal.toInt())
                        },
                        valueRange = 0f..255f,
                        steps = 254,        // one step per integer value
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0", fontSize = 12.sp)
                        Text("255", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val newMax = sliderValue.toInt()
                            // Persist the new cap
                            prefs.edit()
                                .putInt("max_brightness", newMax)
                                .apply()
                            maxBrightness = newMax
                            // Apply the cap immediately, then re-enable enforcement
                            applyBrightness(context, newMax)
                            BrightnessObserver.isEnforcing = true
                            appState = AppState.LOCKED
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save & Lock")
                    }
                }
            }
        }
    }
}

// ── Brightness utility functions ──────────────────────────────────────────────

/**
 * Reads the current system brightness (0–255).
 * Returns the default cap on any error to avoid crashing.
 */
private fun readCurrentBrightness(context: Context): Int =
    try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        )
    } catch (e: Settings.SettingNotFoundException) {
        Log.e("BrightnessUtil", "Could not read brightness: ${e.message}")
        120
    }

/**
 * Writes [brightness] (clamped to 0–255) to the system setting.
 * No-op if WRITE_SETTINGS permission is not granted.
 */
private fun applyBrightness(context: Context, brightness: Int) {
    if (!Settings.System.canWrite(context)) return
    val clamped = brightness.coerceIn(0, 255)
    try {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            clamped
        )
    } catch (e: SecurityException) {
        Log.e("BrightnessUtil", "Could not write brightness: ${e.message}")
    }
}
