# WiFi Priority

Personal Android app for a sideloaded tablet. It keeps the phone hotspot as Wi-Fi priority 1 and the truck hotspot as priority 2, based on which SSIDs are visible in scan results.

- `mock` flavor (`USE_MOCK_WIFI=true`): in-memory factories for unit tests. The installed service still scans with `WifiManager` and registers `WifiNetworkSuggestion`s. Diagnostics scan lines include `build=mock`.
- `real` flavor (`USE_MOCK_WIFI=false`): the same service path. Diagnostics scan lines include `build=real`. Install this APK.

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

The file is `app/build/outputs/apk/real/debug/app-real-debug.apk`. Both flavors share `applicationId` `com.wifipriority`. The status event log's scan line includes `build=real` or `build=mock`.

## Install the real APK over USB

1. On the tablet, enable Developer options. Turn **Wi-Fi scan throttling** off. Leave Location on (Android hides SSIDs when location is off).
2. Enable USB debugging and connect the tablet.
3. Build and install:

```bash
./gradlew assembleRealDebug
adb install -r app/build/outputs/apk/real/debug/app-real-debug.apk
```

4. Open **WiFi Priority**. Grant location, nearby Wi-Fi devices, and notifications. Accept the battery-optimization exemption when Android asks.
5. If no networks are saved, the app opens settings. Enter the phone hotspot SSID and WPA2 password, then the truck hotspot SSID and password (at least 8 characters). Turn **Keep priority running** on and tap **Save**. The app returns to the status screen.
6. Approve the system prompt that lets this app suggest Wi-Fi networks. If you dismiss it, the banner opens Wi-Fi settings.
7. If the truck network was saved by hand in Android Wi-Fi settings, forget it there. A saved network outranks suggestions, so the tablet can stay on the truck even when the phone is visible. The status screen shows that banner.
8. On the Galaxy Tab S10 Ultra (One UI 8.5): Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps → add WiFi Priority. Turn off Intelligent Wi-Fi → Auto switch to mobile data.
9. If Wi-Fi is off, the banner and the status notification open the Wi-Fi panel. The app cannot turn Wi-Fi on itself.

The truck suggestion stays registered except for a 30-second return to the phone. That window ends as soon as the phone disappears. If the phone has no validated internet for 2 minutes, the app drops back to the truck. A local log of the last 500 events is in Diagnostics.

## Priority rules

- Phone SSID visible → target phone on that scan.
- Phone missing for two scans and the truck SSID is visible → target truck (about 90 seconds at the 45-second cadence).
- One miss → stay. Neither SSID visible → keep the last target, or none if none was chosen.
- Both suggestions stay registered, phone at a higher priority, so the system can fail over to the truck. If the engine wants the phone while the tablet is still on the truck, the truck suggestion is removed until the phone is connected, then registered again.
