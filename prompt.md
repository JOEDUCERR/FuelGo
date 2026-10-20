# FuelGo Build Prompt

You are building FuelGo, an Android app. Read `agent.md` in this repository first —
it is the authoritative spec and defines scope, non-goals, tech stack, data
sources, and milestones. Follow it exactly; if anything is ambiguous, choose the
lowest-effort option consistent with it.

## Context

- Development machine: Windows 11. Android Studio is installed. Builds must work
  via `./gradlew` from the terminal. Target device is a physical Nothing phone
  connected over USB with USB debugging enabled (use `adb` to install and for
  logging); the emulator is a fallback.
- This is a low-effort utility app for taxi drivers: nearest fuel station by
  pre-selected fuel type, with open/closed status and a 1x1 circle home-screen
  widget that deep-links into Google Maps navigation. Speed and zero navigation
  are the product. Do not add features beyond the spec.
- The project directory is currently empty except for `agent.md` and this file.

## Build in this order

### Phase 1 — Scaffold and v1 core (M1)

1. Create the Android project: Kotlin, Compose, min SDK 26, package
   `com.fuelgo.app`. Add Glance, DataStore, Room, Retrofit/OkHttp,
   kotlinx.serialization, WorkManager dependencies. Use Gradle version catalogs.
2. Implement onboarding: single screen to pick Diesel / Petrol / CNG, stored in
   DataStore. Skipped after first run.
3. Implement the station data layer with OpenStreetMap Overpass as the default
   provider (no API key required): query fuel stations near the user's location,
   filter by fuel type where OSM tags allow (`fuel:diesel`, `fuel:petrol`,
   `fuel:cng`; when tags are absent, include the station), extract
   `opening_hours`, and cache stations in Room.
4. Implement the opening-hours parser and status derivation in `domain/` with
   unit tests: handle 24/7, multi-ranges, overnight ranges, and unparsable
   values (status = UNKNOWN, shown as grey).
5. Implement the Home screen: nearest 3–5 stations for the selected fuel type
   with name, distance, and status. Renders instantly from Room cache; network
   refresh happens in the background. Tap a station → Google Maps navigation
   intent (`google.navigation:q=lat,lng` with maps fallback).
6. Acceptance: `./gradlew assembleDebug test` passes; app installs on a device,
   shows stations without any API key, and survives airplane mode using cache.

### Phase 2 — Google Places provider (M2)

7. Add the Places API provider behind the same repository interface: read
   `PLACES_API_KEY` from `local.properties` (add a placeholder and document it in
   README; never commit a real key). When the key exists, Places is primary and
   OSM is fallback; without a key, OSM remains the default.
8. Implement cache-first behavior: station POIs cached by area, at most 1–2
   network calls per app open, stale-while-revalidate for widget reads.
9. Acceptance: with no key, behavior identical to Phase 1; with a key, results
   come from Places and repeated opens hit the network at most once.

### Phase 3 — Widget (M3)

10. Build the 1x1 Glance widget: nearest pump name (truncated) + status dot
    (green/red/grey). Tap → Maps navigation to nearest pump; only fall back to
    opening the app when location is unavailable.
11. Update policy: render from cache on every update; refresh via WorkManager at
    most every 15 minutes and on app open. Never block the widget render on
    network.
12. Acceptance: widget works on the home screen of a physical device, updates
    from cache with no network, and tapping it opens Maps navigation in one step.

### Phase 4 — v2 crowdsourced status (M4)

13. Add Firebase: Firestore schema per `agent.md` (`reports` collection with
    stationId, deviceId, status, fuelType, timestamp) and an aggregation approach
    for current per-station status with a 60-minute report window, falling back
    to hours-derived status when reports are stale or absent.
14. Add the reporting UI: one button row per station (Open now / Closed / Small
    queue / Long queue), maximum two taps, rate-limited client-side (1 report per
    device per station per 10 minutes) with a distance sanity check.
15. Add the crowd badge (Quiet / Moderate / Busy) derived from recent report
    ratio and count, per `agent.md`. Three buckets only.
16. Keep Firebase fully optional at runtime: the app must still work end-to-end
    (v1 behavior) when Firebase is unconfigured, with a clear README section on
    setting it up and running local tests against the Firebase emulator.
17. Acceptance: reports submitted from the app appear in aggregation within the
    window, stale reports decay correctly (unit-tested), and disabling Firebase
    config does not crash or degrade v1 features.

### Phase 5 — Polish (M5)

18. Error, empty, and offline states for all screens (e.g., "No stations cached —
    enable location and retry"), location permission flow with a graceful
    degraded mode.
19. README: project overview, `PLACES_API_KEY` setup, device install via `adb`,
    Firebase setup, and how to run tests.

## Working rules

- Follow the package layout in `agent.md`. Keep DI manual (AppContainer), no
  Hilt/Koin.
- Every phase must end with `./gradlew assembleDebug test` green before moving on.
- Write unit tests for the hours parser, status aggregation/decay, and repository
  caching as specified in `agent.md`. Do not write UI tests.
- Do not commit secrets. `PLACES_API_KEY` and any Firebase config go in
  `local.properties` / untracked files only.
- If a device is connected via adb, install and smoke-test each phase; otherwise
  use the emulator and say so in your summary.
- When the spec is silent, pick the simplest option that satisfies the
  performance requirements (cache-first, < 500 ms cold render, one-intent
  navigation) and note the choice in your summary.
