# LuxMeter

An Android exposure companion for film photography. Compose a frame, read the exposure, hold it, and optionally save the frame to a roll.

## Shooting

1. Choose your film and shooting ISO. Changing ISO keeps the film's reciprocity profile.
2. Select your film format and lens. Point the camera at the scene inside the outlined frame.
3. Choose an aperture to get a shutter recommendation, or switch to shutter priority or manual exposure. The shutter you choose in those modes is the actual exposure duration; film correction is included when calculating aperture or exposure balance.
4. Tap **Hold reading** to freeze the exposure and camera preview. Format and lens stay locked until you resume.
5. Optionally **Save frame**, with **Undo** if it was accidental.

The primary navigation is **Meter** and **Rolls**. Settings contains calibration, haptics, and film management. Sunny 16 is an explicit estimate tool. Depth of field expands on the meter; the long-exposure timer appears when needed.

## Metering and calibration

Camera metering requests an auto-exposure region matching the outlined preview. It verifies the reported region and exposure convergence, then derives EV100 from the camera's reported shutter duration, sensitivity, aperture, and post-RAW gain. Missing data or unsupported region metering produces an unavailable state, not a fabricated reading.

Phone camera auto-exposure remains a device-dependent estimate. Compare the phone against a reference meter before relying on it for film, and set the EV offset in Settings. Lens framing is approximate and bounded by the phone's zoom capability. Frame geometry and exposure mathematics have unit coverage; a successful build does not establish hardware accuracy.

Camera EV and original sensor lux are retained without clipping. A zero-lux sensor result is unavailable rather than a fabricated exposure. Phones with ambiguous focal-length metadata use phone framing instead of claiming a simulated film-camera lens.

The light-sensor mode measures light reaching the phone, rather than a camera composition. It uses actual on-change sensor values and remains separate from camera readings. Sunny 16 estimates do not inherit device calibration. Changing film format or lens preserves sensor and Sunny 16 readings.

Calibration stays on the device and is excluded from backup and transfer. The old backed-up calibration value is reset once on upgrade because its originating phone cannot be identified; a nonzero reset prompts recalibration.

## Film correction limits

HP5 and Delta 3200 use [Ilford's published powers](https://www.ilfordphoto.com/wp/wp-content/uploads/2024/05/Reciprocity-Failure-Compensation-v2.pdf). Tri-X uses [Kodak's published timed points](https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4017_TriX.pdf), with estimated interpolation and a development-adjustment reminder. CineStill uses its approximate power of 1.3 and explicitly asks you to bracket.

Kodak color films have no manufacturer-supplied timed correction beyond their stated normal range. Superia and Velvia publish aperture corrections, and Velvia also needs color filtration. Outside a supported timed range, the app shows the metered value and guidance, suppresses the corrected recommendation, and disables timing and saving that recommendation. A custom profile can use your own tested exponent. Corrected exposures and the foreground timer are limited to one day.

## Remembered setup and rolls

Film, ISO, format, lens, exposure mode, calibration, haptics, and the active roll are restored between sessions. Room stores film profiles and exposure records locally. Saving requires a valid held reading and supported film correction, rejects repeated save taps, and confirms only after storage succeeds. Existing records are retained; the database schema is unchanged. Built-in profile repair completes transactionally before queries, including recovery of an interrupted initial seed.

Deleting a saved record preserves the roll's physical frame counter. Undo reverses the most recent accidental save. Custom-film drafts remain available after a failed insert; locale decimal separators are accepted.

The timer compensates for delayed UI frames and configuration changes using elapsed time. Start and close the real camera's shutter separately. Keep the timer screen open while timing; it is not a background alarm service.

## Build and test

Requirements: JDK 17 or newer (tested with JDK 21), Android SDK platform 36.1, build tools 36.0.0, and an SDK location supplied through `ANDROID_HOME` or an ignored `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The wrapper pins Gradle 9.3.1 and verifies its distribution checksum. Debug builds use Android's standard debug signing configuration and install as **LuxMeter Preview** (`com.aistudio.luxmeter.analog.debug`), alongside the release app with separate storage. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Tests cover exposure and depth-of-field calculations, camera crop/rotation/zoom geometry, exposure metadata validation, source isolation, calibration, held readings, persistence, saving/undo and failures, film correction and inverse correction, transactional profile repair, backup exclusions, locale inputs, Sunny 16 choices, timer timing, and readable exposure controls.

Before relying on a release, verify camera permission/retry, frame alignment, rotation, held previews, daylight/low-light readings, font scaling, TalkBack, and calibration on physical devices.

## Technical foundation

Kotlin, Jetpack Compose Material 3, CameraX, StateFlow, and Room. Android 7.0 (API 24) or newer. The interface follows the system's light/dark appearance and adapts to wider screens.
