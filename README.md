# IFrame

<p align="center"><img src="docs/iframe-logo.svg" width="128" alt="IFrame logo"></p>

**IFrame** turns an Android device into a digital photo frame, with local photo/video playback, flexible layouts, and a night clock/calendar. No Google account is required.

## Languages

English is the default app language, including on Vietnamese devices. Double-tap the frame, then choose **Language → English / Tiếng Việt**. The selection is saved, settings refresh immediately, and the frame updates when you return.

Tiếng Anh là ngôn ngữ mặc định. Chạm đúp khung ảnh để mở cài đặt, chọn **Language / Ngôn ngữ → Tiếng Việt**. Lựa chọn được lưu tự động; lịch, đồng hồ và thông báo cũng dùng ngôn ngữ đã chọn.

## Build and install

1. Install Android Studio, Android SDK 32, and JDK 17. Set `JAVA_HOME` to your JDK and configure `local.properties` with your SDK path (Android Studio can generate this file).
2. Open this project in Android Studio and sync Gradle, or run:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   On macOS/Linux: `./gradlew assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on Android 5.1 (API 22) or later. Grant media access, add photos/videos to shared storage, and double-tap to customize the frame.

For unit tests: `./gradlew testDebugUnitTest`. For device tests with an emulator/device connected: `./gradlew connectedDebugAndroidTest`.

## Publishing this repository

Upload the source, Gradle wrapper, this README, and `docs/iframe-logo.svg`. Keep machine-specific `local.properties`, signing keys, passwords, and build outputs out of Git. To distribute an APK, create a signed release with your own keystore in Android Studio and attach it to a GitHub Release; do not upload the keystore.

The app display name is IFrame. The existing application ID (`nuts.deez.photoframeslideshow`) is retained so this build can update existing installations. The logo is provided as an editable SVG and Android vector/adaptive launcher icons.

A Google-Free slideshow app intended for digital photo frames running on Android.

Intentionally decoupled from file transfer/syncing.  For my own personal use, I use SyncThing to share the Pictures directory from the photo frame.

By default the app reads all accessible public photos and videos from the device media library, including internal storage and removable cards. Settings can limit the source to /Pictures/ and its subfolders.  The app will generate a config.json file in the same folder which can be used to adjust the behaviour of the frame.  The config file will be reloaded each time the image changes, and the app will pull in any new images once all of the currently chosen images have been displayed.

---

## In-App Controls

### Swipe Left and Right
- Swipe left for the next photo; swipe right for the previous photo. Navigation wraps around the current album.

### Single Tap
- Tap the left half for the previous photo, or the right half for the next photo.

### Long Press
- Pauses or resumes the slideshow without a pause icon.

### Double Tap
- Opens a dedicated settings screen. Changes save automatically and apply when returning to the frame.

### Swipe Up and Down
- Modifies the rotation data in the image and redisplays it.  Swiping down does a clockwise rotation and swiping up does counter-clockwise.

### Physically rotating the device
- Will maintain the current image list and re-display the current image in the new orientation

---

## Config.json Options

### imageTime
- The amount of time each image is displayed for
- In seconds

### shuffle
- Should the images be displayed shuffled or alphabetically 
- true or false

### showFileNames
- Legacy option, ignored. Photo paths are never displayed.
- true or false

### activeHourStart
- The hour of the day when the frame should wake up and start showing images.  No support for minutes at the moment, as that level of granularity seems unnecessary.
- Integer, 0 to 23

### activeHourEnd
- The hour of the day when the frame should go to sleep and stop showing images.  No support for minutes at the moment, as that level of granularity seems unnecessary.
- Integer, 0 to 23

### ntpServer
- The NTP server that the frame will use to sync the time (if forceClockSync is enabled)
- String (an NTP server URL)

### forceClockSync
- Sync the system time with the specified NTP server.  Requires root.
- true or false

### ntpSyncInterval
- How frequently the clock should be synced with NTP
- In seconds

### fileTypes
- Legacy option, ignored by the library scanner. MediaStore supplies images/videos regardless of filename extension; legacy scanning recognizes common image and video extensions.
- List of strings (Filetypes, including the leading .)

### subdirectories
- A list of subdirectories within the /Pictures/ folder that can be enabled or disabled via config (Perhaps for seasonal images, etc)
- Each subdirectory entry includes two fields
  - **folder**
    - The folder name (without a leading or trailing slash)
    - String
  - **enabled**
    - Whether or not the folder should be included when loading new images
    - true or false


## Photo frame rendering

Requires Android 5.1 (API 22) or later. Build this project with JDK 17.
Photos retain their aspect ratio, with a dimmed, blurred copy filling the unused space.
An application-wide memory-bounded cache retains decoded photos and rendered collage pages across rotation and settings visits. Decode, EXIF correction, blur and collage composition run on a worker thread. Cache keys include file modification time, display size and rendering options.

## Frame overlays and ambient sleep

The upper-left overlay shows capture date/time from EXIF, original dimensions, size and camera model when available, without a file path. The lower-right clock shows current local time and date. A thin bottom bar counts toward the next photo.

With a light sensor, automatic sleep enters after 5 seconds at 2 lux or below and wakes after 5 seconds at 8 lux or above. Light thresholds and stabilization delay can be changed in Settings. Night mode requests low window brightness (3% by default), shows a fullscreen clock/calendar, stops slideshow/preloading/NTP scheduling and releases the partial wake lock. The night clock refreshes once per minute. The light listener remains active to detect morning. Actual minimum backlight is device-dependent. Waking restores brightness and the user pause state, starting a fresh photo interval.

## Layouts, transitions and performance

Settings provides 1, 2, 3 or 4 photos per page. Two-photo layouts adapt to portrait/landscape; three photos use one large and two small cells; four use a 2 × 2 grid. Pages wrap; the final page uses the remaining photos without duplicating them. Swipes/taps move whole pages. Rotation gestures apply only to single-photo mode.

Transitions: none, fade, horizontal slide, subtle zoom, or random. Duration is configurable. Animation keeps two page references without allocating a screenshot for each transition and stops when sleeping/backgrounded.

Cache defaults to the maximum preset, targeting 70% of the process Java heap (not all physical RAM), with 35% and 55% alternatives. The budget also respects available system memory and leaves at least 64 MB of heap headroom where possible. Memory-pressure callbacks shrink the cache. Preloading scales to the budget and album size, up to 24 source-photo slots. Settings displays cache usage and lets users clear it.

Additional controls: shuffle/alphabetical order, fit/crop, blurred/black background, clock/photo-information/progress visibility, ambient sleep threshold and delay. A saved Settings preference takes precedence over the matching legacy config.json value.


### Random photo count and lightweight blur

Choose Random under photos per page to vary between 1 and 4 photos. Each page has a 60% chance of selecting one photo; the other 40% is shared evenly among 2, 3 and 4 photos. Album boundaries may increase the single-photo share, especially in small albums. A page plan is retained for the album cycle, navigation and rotation, so going back restores the same page layout; the last page uses the remaining photos. Preloading uses this same plan.

Blur backgrounds use the original lightweight three-pass blur on a 160-pixel thumbnail, with a dimmed center-crop backdrop. Background and foreground transition together without vignette, rounded clipping or shadow passes.


## Whole-device library and video

The library scanner queries MediaStore and, on Android 5.1-9, also scans readable shared storage recursively for unindexed files. Legacy scanning includes readable hidden/.nomedia folders but skips other apps' private Android/data and Android/obb directories. Encrypted and cloud-only media are not accessible. Scanning runs on a separate worker and suspends when sleeping/backgrounded. Settings includes a manual rescan and permission shortcut.

Images use Android platform codecs with optional EXIF metadata. Typical Android 5.1 formats: JPEG, PNG, WebP, BMP and GIF (first frame). HEIC/HEIF, AVIF, TIFF and RAW depend on device/OS decoder support and may be skipped. Content URIs support scoped storage on newer Android. READ_EXTERNAL_STORAGE remains for target SDK 32; granular image/video permissions are declared for a future target-SDK upgrade.

Videos play on their own page using TextureView/MediaPlayer, preserving gestures, clocks and dark overlays. They are muted by default (change in Settings), advance on completion and pause on long press or ambient sleep. Playback position is restored when possible. The progress bar tracks video duration. Unsupported files are skipped; video frames do not occupy the photo bitmap cache. Codec support depends on the device.

Large library lists are retained in memory across rotation instead of being placed in the saved-state Bundle.


## Operating hours

The frame runs 24 hours by default; legacy config.json active-hour values no longer silently stop navigation at night. Enable Operating hours explicitly in Settings to apply the start/end hours. Manual photo navigation remains available outside the schedule, while automatic playback pauses. Ambient-light sleep remains independent of the schedule.


## Night calendar and notification gestures

Ambient darkness now opens a fullscreen, black-background clock and monthly calendar, with today highlighted. Default window brightness is 3% (adjustable in Settings). Photos, video, scans and NTP remain suspended; only the clock/calendar refresh once per minute. The display stays awake. Double-tap the night calendar to open Settings; brighter ambient light restores normal playback and the earlier pause state.

On Android 5.1-7.x, a foreground-only transparent top-edge overlay can intercept notification pull-down gestures. It requires SYSTEM_ALERT_WINDOW permission and is controlled in Settings. The overlay is removed on pause/destroy and never intentionally covers other apps. Android 8+ does not allow this legacy overlay above the status bar; a properly provisioned dedicated-device kiosk is needed there. Immersive fullscreen by itself is not a complete notification lock.
