# BrightnessLock 🔆🔒

BrightnessLock is a simple Android utility that **prevents the screen brightness from being increased beyond a locked maximum value**.

It was originally built to stop accidental or repeated brightness changes on a shared tablet.

The app runs a background monitor that **automatically resets brightness if it exceeds a configured limit**.

---

## Features

- Lock maximum screen brightness
- Live brightness adjustment with preview
- Simple **math challenge** to unlock settings
- Automatically resets brightness when users increase it
- Runs in a **foreground service** so protection stays active
- Auto-starts on device boot
- Lightweight and minimal UI
- Works on **Android 11+ / Fire OS 8**

---

## How It Works

BrightnessLock monitors system brightness using a `ContentObserver`.

User increases brightness  
↓  
System updates `SCREEN_BRIGHTNESS`  
↓  
`BrightnessObserver` detects change  
↓  
If brightness > locked value  
↓  
Reset brightness to allowed maximum  

This causes the brightness slider to **snap back instantly**.

---

## App Architecture

MainActivity  
- UI for brightness control  
- Math unlock challenge  

BrightnessService  
- Foreground service that runs continuously  

BrightnessObserver  
- Watches system brightness changes  

MathChallengeHelper  
- Generates simple math questions  

BootReceiver  
- Restarts the service when the device boots  

---

## Unlock Protection

To change brightness settings, the user must solve a simple math problem.

Example:

7 + 5 = ?

This prevents accidental changes by children.

---

## Requirements

- Android 11+ (API 30+)
- Fire OS 8 compatible
- WRITE_SETTINGS permission enabled

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| WRITE_SETTINGS | Modify system brightness |
| FOREGROUND_SERVICE | Keep the brightness monitor running |
| RECEIVE_BOOT_COMPLETED | Restart protection after reboot |

---

## Building the App

Clone the repository:

git clone https://github.com/YOUR_USERNAME/BrightnessLock.git

Open the project in **Android Studio**.

Build the APK:

Build → Generate App Bundles or APKs → Build APK

The APK will be generated at:

app/build/outputs/apk/debug/app-debug.apk

Install with ADB:

adb install -r app-debug.apk

---

## Debugging Brightness

To monitor brightness in real time via ADB:

while true; do adb shell settings get system screen_brightness; sleep 0.2; done

---

## Future Improvements

Possible enhancements:

- PIN unlock option
- Prevent quick settings brightness override
- Brightness schedules (day/night)
- Device admin / kiosk mode
- UI improvements

---

## License

MIT License

---

## Author

Created by **Anish Gorakavi**
