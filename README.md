# PawPal AI + PawPal 3D (libGDX)

This repository keeps the original PawPal AI Android application (Jetpack Compose, Gemini, Firebase, voice interaction, wardrobe, stories, and learning activities) and adds a native, real-time 3D pet-care mode powered by libGDX.

## PawPal v5 changes

- Continues from the full v4.1 hybrid project. The original Compose + Gemini + Firebase app, bundled art assets, and native libGDX 3D room are retained rather than replaced by the small standalone prototype.
- Pet needs now continue to decrease while the app is closed: hunger, happiness, energy, and cleanliness update from elapsed whole hours when the 3D room opens. Offline catch-up is capped at 72 hours and every stat is clamped to 0–100.
- Daily care progress and the daily reward are reset using the device-local date and are validated through a small deterministic rules class.
- The game persists the last-update timestamp alongside coins, XP, needs, and daily quest progress.
- App version advanced to 1.3.0 (versionCode 4).
- Added unit tests for offline decay, time reversal, the 72-hour cap, stat bounds, and daily quest reset.

## What changed in v4.1 (retained)

- Added a dedicated Android libGDX game activity at `app/src/main/java/com/example/game/PawPal3DGameActivity.kt`.
- Added a 3D renderer at `app/src/main/java/com/example/game/PawPal3DGame.kt`. It uses perspective projection, depth testing, lit 3D meshes, and interactive camera controls. The pet and room meshes are created procedurally, so no external model downloads are required.
- Added five species variants (cat, rabbit, dog, capybara, bear), pet color hand-off from the existing wardrobe state, and a furnished 3D living room.
- Added care actions (feed, play, bath, sleep), need meters, pet tapping, a timed star-catching mini-game, coin rewards, and state hand-off back to the original ViewModel.
- Added a **GAME 3D** button over the existing avatar stage. The original Compose screens and AI features remain the app's main shell.
- Added opt-in **conversation-based reminders**. When enabled, PawPal scans only user chat/voice transcripts locally for clear near-future commitments (for example, “Besok aku ada acara nikahan di keluarga loh”), saves a reminder, and schedules an Android alarm/notification outside the app UI.
- Reminder date parsing now supports “hari ini”, “besok/esok”, “lusa/dua hari lagi”, Indonesian weekday names, “minggu depan”, `tanggal 22`, named month dates, and `dd/mm/yyyy` dates. It recognizes explicit times such as “jam 7 malam” or “pukul 19.30”, plus broad dayparts such as “besok sore”. Unspecified time defaults to 08:00 in the device timezone.
- The parser rejects several hypothetical/cancelled/negated sentence patterns to reduce accidental reminders. It is still a conservative local rules engine, not a semantic AI promise detector; ambiguous plans should be confirmed by the user.
- Settings now displays up to six upcoming saved reminders and lets the user delete them. Notifications include **Tunda 10 menit**. Pending alarms are restored after reboot or package update.
- Added Robolectric coverage for the family-wedding example, hypothetical/negated statements, weekday and date parsing, dayparts, and reminder snoozing/removal.
- Added a 3D pet XP/level bar, XP rewards for care and mini-game activity, a daily care quest (care for the pet three times to claim 25 coins), and lighting/sky colors that follow the device's local time of day.
- Added procedural eye blinking to the 3D pet and fixed mini-game level-up feedback being overwritten by the coin message.
- Explicitly invalid clock values (for example, `25.99`) are rejected instead of being coerced into a different reminder time.
- Upgraded libGDX to 1.14.2 and added Android JNI packaging for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.
- Fixed the old Compose avatar's blink animation so the eye closure is animated continuously instead of snapping after each animation, and changed particle cleanup so rapid taps do not continually postpone removal.
- Marked `gradlew` executable and enabled AndroidX in Gradle properties.

## Run on Android

1. Open the project root in Android Studio.
2. Use JDK 17 or the JDK bundled with a compatible Android Studio release.
3. Sync Gradle while online so Android Gradle Plugin, libGDX 1.14.2, and native libraries can be resolved.
4. Run the `app` configuration on an Android device or emulator with OpenGL ES 2.0 support.
5. In PawPal AI, tap **GAME 3D** in the pet room to enter the libGDX room. Drag across the scene to orbit the camera, scroll to zoom (on devices that expose scroll input), tap the pet for affection, and use the care/game controls at the bottom.

## Conversation reminder behavior and limitations

- Reminders are opt-in and stored locally on the device; the chat text is not uploaded to a reminder service for extraction.
- Reminder extraction is rule-based and on-device, not a general-purpose AI promise detector. It handles common Indonesian near-future dates, weekday names, date formats and agenda keywords, but vague or ambiguous statements can still be missed. Hypothetical/negated pattern checks are intentionally conservative and may skip some real plans phrased uncertainly.
- A detected reminder is scheduled for the date mentioned, at the stated time/daypart or 08:00 local time. Android notification permission must be granted. If permission is denied, the alarm is consumed without a visible notification; the user should enable notifications before relying on reminders.
- Notification timing uses Android's idle-friendly alarm API without requesting special exact-alarm access. Android may defer delivery due to Doze or manufacturer battery management. The notification's snooze action schedules a new local alarm 10 minutes later.
- Pending reminders are restored after reboot or app update. Android force-stop behavior, battery restrictions from some device vendors, notification permission changes, uninstalling the app, or clearing app data can still prevent delivery or erase locally stored reminders.

## Current 3D asset limitation

The original upload did not contain any `.glb`, `.gltf`, `.fbx`, or `.obj` 3D character assets. The new scene therefore builds stylized 3D characters and room furniture from lit libGDX meshes (spheres, cones, cylinders, and boxes). It is real perspective-rendered 3D, but it is a prototype look rather than a polished, rigged character model. High-quality fur, facial deformation, skeletal animation, and full room materials need dedicated 3D models and textures in a next pass.

## v5 gameplay note

Offline care decay is intentionally gentler than the accelerated needs changes while the 3D room is actively running. One whole elapsed hour reduces hunger by 2 points and happiness, energy, and cleanliness by 1 point each. Partial hours are carried implicitly by the last-update timestamp and applied on a later launch.

## Build note

A Gradle build could not be run in this environment because the Gradle 9.3.1 distribution is not cached and `services.gradle.org` could not be resolved from the current network. The source and Gradle configuration were statically reviewed, but APK compilation and on-device rendering still need verification in Android Studio.

## Audit notes

- The original `Avatar3DCanvas.kt` uses Jetpack Compose `Canvas` drawing and 2D transforms for a 3D-style appearance; it is not a libGDX/OpenGL 3D scene. The new activity adds a separate true 3D renderer without deleting the original AI app flow.
- The old blink coroutine did not expose an animated progress value to the draw pass, which could make eyelid closure appear to snap; it now uses an `Animatable` value.
- Particle expiration was tied to the changing particle count, so frequent new particles could repeatedly restart cleanup; expiry now runs on a steady cleanup loop.
- The first JNI extraction draft risked retaining nested archive paths. The Gradle task now extracts `.so` files by basename into the ABI-specific jniLibs folders.
- The original project contains several audio/voice subsystems and a large ViewModel. This pass avoids broad rewrites of those systems because an unverified rewrite could break microphone, Gemini Live, Firebase, or voice-session behavior. Those components remain intact.
- A persistence issue was corrected: the hybrid launcher previously supplied a fixed cleanliness value of 80 on every game launch, overriding the value saved by the 3D game. It now lets the game restore its saved cleanliness value.
- The original Android test suite is retained. The pure reminder parser was compiled and exercised with a local Kotlin harness, including relative dates, weekdays, named dates, slash dates, dayparts, and negated/hypothetical examples. The Android/Robolectric test suite and APK build could not be run here because the Gradle 9.3.1 distribution is not cached, `services.gradle.org` could not be resolved, and no usable Android SDK is configured in this environment.

## v4.1 integration update

This update keeps the original v4 Compose + libGDX application and its bundled assets intact.
The Indonesian conversation reminder parser also recognizes timed pet-care routines (feeding,
cleaning, play, water checks, and rest). If no date is stated, a care reminder is scheduled
for today when its time is still ahead, or tomorrow when that time has passed.

GitHub Actions runs the Android unit tests, assembles a debug APK, and stores the APK as a
workflow artifact for download.


## Build otomatis v5.1
GitHub Actions memasang Android SDK Platform 36 dan Extension 1 (`platforms;android-36-ext1`) karena modul Android menggunakan `compileSdk` 36.1. Jalankan workflow melalui push ke branch `main` atau tab Actions > PawPal Android CI > Run workflow. APK debug akan tersedia sebagai artifact `PawPal-3D-debug-apk` jika seluruh tes dan build berhasil.
