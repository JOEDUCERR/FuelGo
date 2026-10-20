# FuelGo

A minimal, blazing-fast Android app that shows the nearest fuel stations for your
pre-selected fuel type (Diesel / Petrol / CNG) — built for taxi drivers and
constant commuters who want an answer in two taps.

- **App opens straight to the nearest 3–5 pumps** with open/closed status
- **1x1 circle widget** for the home screen (works in the Nothing widget space):
  nearest pump + status dot; tap it → Google Maps navigation starts directly
- **Open/closed** from published opening hours, plus **v2 crowdsourced reports**
  (Open / Closed / Small queue / Long queue) and a 3-state crowd badge
  (Quiet / Moderate / Busy)
- **Zero in-app navigation** — FuelGo hands off to Google Maps in one intent

## Project layout

```
app/src/main/java/com/fuelgo/app/
  ui/        Compose screens (Onboarding, Home, Settings) + AppViewModel
  widget/    Glance widget + WidgetRefresher
  data/      LocationManager client, Retrofit APIs, Room DB, DataStore prefs
  domain/    Hours parsers, status aggregation, geo helpers (unit-tested)
  di/        AppContainer (manual DI)
  work/      15-min periodic widget refresh worker
app/src/test/  Unit tests for all core logic
```

## Running the project (Windows)

1. **Install Android Studio** (includes the Android SDK, emulator, JDK).
2. Open the project folder in Android Studio — it generates `local.properties`
   with your SDK path automatically.
3. First Gradle sync downloads all dependencies (a few minutes).

### Run on a physical phone (recommended — best for widgets + GPS)

1. On the phone: Settings → About phone → tap "Build number" 7× to enable
   Developer Options; then Settings → Developer options → enable **USB
   debugging**.
2. Connect via USB, accept the debugging prompt on the phone.
3. Verify the device is visible:

   ```powershell
   adb devices
   ```

4. Install and launch:

   ```powershell
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.fuelgo.app/.MainActivity
   ```

   (`adb` is in `%LOCALAPPDATA%\Android\Sdk\platform-tools` — add it to PATH or
   use the full path.)

### Run on the emulator

Create an AVD in Android Studio (Device Manager), then click Run, or:

```powershell
.\gradlew installDebug
```

## API keys

### Google Places (optional — Phase 2)

Without a key the app runs **entirely on OpenStreetMap (Overpass API)** — free,
no key, works out of the box. To switch the primary provider to Google Places
(Nearby Search, new API):

1. Create an API key in Google Cloud Console with the **Places API (New)**
   enabled.
2. Add to `local.properties` (never commit it):

   ```
   PLACES_API_KEY=your_key_here
   ```

3. Rebuild. The app then uses Places first and falls back to OSM automatically.

### Firebase (optional — v2 crowdsourced reports)

The app must still work with Firebase unconfigured — it does: reports are then
stored locally and status falls back to hours-only. To enable real sharing:

1. Create a project at https://console.firebase.google.com, add an **Android
   app** with package `com.fuelgo.app`.
2. Download `google-services.json` into `app/`.
3. Add the Google Services plugin to `app/build.gradle.kts`:

   ```kotlin
   // root build.gradle.kts
   plugins { id("com.google.gms.google-services") version "4.4.2" apply false }
   // app/build.gradle.kts
   plugins { id("com.google.gms.google-services") }
   ```

4. Firestore rules for the MVP (open — tighten before any real release):

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /reports/{doc} {
         allow read: if true;
         allow create: if true;
       }
     }
   }
   ```

5. Test locally with the Firebase emulator suite (`firebase emulators:start`)
   if you don't want to touch production data.

## Tests

Core logic (opening-hours parsing incl. overnight/24-7/multi-range rules,
Places hours conversion, status aggregation/report decay, ranking + fuel-type
filtering, report rate limiting) is unit-tested:

```powershell
.\gradlew test
```

## Build

```powershell
.\gradlew assembleDebug      # debug APK in app\build\outputs\apk\debug\
.\gradlew assembleRelease    # unminified release (add signing config to ship)
```

## Design notes / decisions

- **Cache-first**: every open renders instantly from the Room cache; network
  refresh (1 provider call + at most 1 reports query) happens in the background.
  The widget never blocks on the network.
- **OSM default**: avoids API costs at zero effort; Places drops in behind the
  same repository interface when a key exists.
- **Firebase optional**: guarded at runtime (`FirebaseApp.getApps`); the v1
  experience is unaffected when it's absent.
- **Crowd level is 3 buckets only** by design — no invented precision.
- **Manual DI (AppContainer)**: no Hilt/Koin at this codebase size.

## Roadmap

- [x] v1: onboarding, nearby list, hours-derived status, widget
- [x] v2: crowdsourced open/closed + queue reports, crowd badge
- [ ] Real-world hardening: Firestore security rules, abuse moderation
- [ ] Maybe: OSM crowd-source tie-ins, Hindi localization
