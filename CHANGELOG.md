# Changelog

## v5.1 (1.3.1)
- Selaraskan GitHub Actions dengan Android SDK Platform 36 Extension 1 yang diminta oleh konfigurasi compileSdk.
- Naikkan versionCode menjadi 5 dan versionName menjadi 1.3.1.
- Mempertahankan fitur, aset, dan struktur PawPal v5.

1.3.0 - PawPal v5

- Continued from the full v4.1 project; retained the Compose/Gemini/Firebase application, existing 3D libGDX renderer, and bundled visual assets.
- Added offline pet-needs progression for hunger, happiness, energy, and cleanliness.
- Limited offline catch-up to 72 hours and clamped every need to the valid 0–100 range.
- Persisted the last-update timestamp together with the existing 3D pet progression.
- Centralized offline progression and daily quest date-reset rules in a deterministic helper.
- Added five unit tests for elapsed-time decay, negative elapsed time, maximum catch-up, stat clamping, and daily quest reset.
- Bumped Android version to 1.3.0 (versionCode 4).

# Changelog

## PawPal v4.1 integration update
- Preserved the full v4 Android/Compose and libGDX project, original assets, Firebase setup, and existing AI features.
- Extended the existing Indonesian reminder parser with timed pet-care reminders for feeding, cleaning, play, drinking-water checks, and rest.
- Pet-care reminders without an explicit date are scheduled for today when the time is still ahead, otherwise tomorrow.
- Added unit coverage for pet-care reminder labels, invalid/missing time, and next-day scheduling.
- Added an Android CI workflow that runs unit tests, builds the debug APK, and uploads the APK artifact.

## 1.2.0 - PawPal Hybrid polish

- Added natural eye blinking to the procedural 3D pet, coordinated with the head motion.
- Fixed a mini-game feedback bug where the level-up message was immediately overwritten by the ordinary coin message.
- Invalid clock values such as `25.99` and `24` are now rejected instead of being silently clamped to a different time.
- Fixed a reminder delivery edge case: an alarm that fires while Android notification permission is denied is retained for recovery instead of being deleted.
- On app resume, recent missed reminders (up to 12 hours) are delivered once permission is available; older missed items are pruned to avoid stale alerts.
- Added a parser regression test for impossible clock times.

## 1.1.0 - PawPal 3D Hybrid

- Expanded on-device Indonesian reminder extraction to weekdays, next-week language, numeric/named dates, and dayparts.
- Added reminder list management in Settings, delete action, and notification snooze for 10 minutes.
- Rescheduled future reminders after boot and package replacement; stale reminders are pruned when rescheduling.
- Added 3D pet XP/levels, care-action XP, a daily three-care quest with a 25-coin reward, and lighting/sky colors that follow the device's local time of day.
- Fixed cleanliness being reset to 80 whenever the 3D game reopened.
- Kept the existing Compose/Gemini/Firebase experience as the hybrid app shell.

## Known limitations

- Reminder extraction is local and rule-based. It can miss ambiguous natural-language plans and can conservatively skip hypothetical statements.
- 3D characters are procedural meshes, not rigged GLB models. Premium character models and skeletal facial animation still require dedicated 3D assets.
- APK compilation and device testing have not been verified in this environment because the Gradle distribution and Android SDK are unavailable.
