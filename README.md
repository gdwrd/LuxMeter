# LuxMeter

An Android exposure companion for film photography. Compose a frame, read the exposure, hold it, and optionally save the frame to a roll.

## Shooting

1. Choose your film and shooting ISO. Changing ISO keeps the film's reciprocity profile.
2. Select your film format and lens. Point the camera at the scene inside the outlined frame.
3. Choose an aperture to get a shutter recommendation, or switch to shutter priority or manual exposure.
4. Tap **Hold reading** to freeze the exposure and camera preview. Format and lens stay locked until you resume.
5. Optionally **Save frame**, with **Undo** if it was accidental.

The primary navigation is **Meter** and **Rolls**. Settings contains calibration, haptics, and film management. Sunny 16 is an explicit estimate tool. Depth of field expands on the meter; the long-exposure timer appears when needed.

## Metering and calibration

Camera metering requests an auto-exposure region matching the outlined preview. It verifies the reported region and exposure convergence, then derives EV100 from the camera's reported shutter duration, sensitivity, aperture, and post-RAW gain. Missing data or unsupported region metering produces an unavailable state, not a fabricated reading.

Phone camera auto-exposure remains a device-dependent estimate. Compare the phone against a reference meter before relying on it for film, and set the EV offset in Settings. Lens framing is approximate and bounded by the phone's zoom capability. Frame geometry and exposure mathematics have unit coverage; a successful build does not establish hardware accuracy.

The light-sensor mode measures light reaching the phone, rather than a camera composition. It remains separate from camera readings. Sunny 16 estimates do not inherit device calibration.

## Remembered setup and rolls

Film, ISO, format, lens, exposure mode, calibration, haptics, and the active roll are restored between sessions. Room stores film profiles and exposure records locally. Saving requires a valid held reading, rejects repeated save taps, and confirms only after storage succeeds. Existing records are retained; this redesign does not change the database schema.

The timer compensates for delayed UI frames and configuration changes using elapsed time. Start and close the real camera's shutter separately. Keep the timer screen open while timing; it is not a background alarm service.

## Build and test

Requirements: JDK 17 or newer (tested with JDK 21), Android SDK platform 36.1, build tools 36.0.0, and an SDK location supplied through `ANDROID_HOME` or an ignored `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The wrapper pins Gradle 9.3.1 and verifies its distribution checksum. Debug builds use Android's standard debug signing configuration and install as **LuxMeter Preview** (`com.aistudio.luxmeter.analog.debug`), alongside the release app with separate storage. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Tests cover exposure and depth-of-field calculations, camera crop/rotation/zoom geometry, exposure metadata validation, source isolation, calibration, held readings, persistence, saving/undo and failures, Sunny 16 choices, timer timing, and readable exposure controls.

Before relying on a release, verify camera permission/retry, frame alignment, rotation, held previews, daylight/low-light readings, font scaling, TalkBack, and calibration on physical devices.

## Technical foundation

Kotlin, Jetpack Compose Material 3, CameraX, StateFlow, and Room. Android 7.0 (API 24) or newer. The interface follows the system's light/dark appearance and adapts to wider screens.
