# WiFi Priority

Personal Android app for a sideloaded tablet. It keeps the phone hotspot as Wi-Fi priority 1 and the truck hotspot as priority 2, based on which SSIDs are visible in scan results.

This tree is **stage 1 only**:

- interfaces for scanning, connecting, credentials, time, and scan throttling
- a pure `PriorityEngine` state machine
- in-memory mocks, with `BuildConfig.USE_MOCK_WIFI = true` by default
- JVM unit tests
- an empty Jetpack Compose activity so the app module compiles

There is no real Wi-Fi stack, foreground service, or settings UI yet.

## Build

Requires JDK 17+, Android SDK platform 36, and build-tools 36.0.0. Point the build at the SDK with `ANDROID_HOME` or `local.properties` (`sdk.dir=`).

```bash
./gradlew assembleDebug testDebugUnitTest
```

## Priority rules

`PriorityEngine` is the source of truth:

- phone SSID visible → target `PHONE` on that scan
- phone missing for two consecutive scans and the truck SSID is visible → target `TRUCK`
- a single miss → keep the current target
- neither SSID visible → keep the last target, or `NONE` if none has been chosen
- disabled, or no SSID configured → no actions and no bookkeeping

Scans are planned every 45 seconds. `ScanThrottle` still refuses a fifth scan inside any two-minute window. Stage 1 does not call `WifiManager.startScan()`.
