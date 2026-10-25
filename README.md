# WiFi Priority

Personal Android app for a sideloaded tablet. It keeps the phone hotspot as Wi-Fi priority 1 and the truck hotspot as priority 2, based on which SSIDs are visible in scan results.

- `mock` flavor (`USE_MOCK_WIFI=true`): in-memory scan and connector, still uses the Keystore credential store on device
- `real` flavor (`USE_MOCK_WIFI=false`): `WifiManager.startScan()`, `WifiNetworkSuggestion`, foreground service

`minSdk` 33, `compileSdk` / `targetSdk` 36.

## Build

Requires JDK 17+, Android SDK platform 36, and build-tools 36.0.0.

```bash
./gradlew assembleDebug testDebugUnitTest lint
```

Real debug APK:

```bash
./gradlew assembleRealDebug
```

The file is `app/build/outputs/apk/real/debug/app-real-debug.apk`.

## Install the real APK over USB

1. On the tablet, enable Developer options. Turn **Wi-Fi scan throttling** off. Leave Location on (Android hides SSIDs when location is off).
2. Enable USB debugging and connect the tablet.
3. Build and install:

```bash
./gradlew assembleRealDebug
adb install -r app/build/outputs/apk/real/debug/app-real-debug.apk
```

4. Open **WiFi Priority**. Grant location, nearby Wi-Fi devices, and notifications. Accept the battery-optimization exemption when Android asks.
5. Enter the phone hotspot SSID and WPA2 password, then the truck hotspot SSID and password (at least 8 characters). Turn **Keep priority running** on and tap **Save**.
6. Approve the system prompt that lets this app suggest Wi-Fi networks. If you dismiss it, the app notification opens Wi-Fi settings so you can allow suggestions.
7. If Wi-Fi is off, the status notification has a button that opens the Wi-Fi panel. The app cannot turn Wi-Fi on itself.

## Priority rules

- Phone SSID visible → target phone on that scan.
- Phone missing for two scans and the truck SSID is visible → target truck (about 90 seconds at the 45-second cadence).
- One miss → stay. Neither SSID visible → keep the last target, or none if none was chosen.
- Both suggestions stay registered, phone at a higher priority, so the system can fail over to the truck. If the engine wants the phone while the tablet is still on the truck, the truck suggestion is removed until the phone is connected, then registered again.
