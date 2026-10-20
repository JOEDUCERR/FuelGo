# FuelGo — Agent Guide

## What FuelGo is

FuelGo is a minimal, blazing-fast Android app that shows the nearest fuel stations
for the user's pre-selected fuel type (diesel / petrol / CNG). It is a dedicated
niche utility for taxi drivers and constant commuters who want an answer in two
taps, not a navigation app.

Core differentiator: zero-navigation UX. The app opens directly to a short list of
the nearest pumps with open/closed status, and a 1x1 circle widget lives on the
home screen (Nothing Phone widget space, but it is a standard Android AppWidget)
showing the nearest pump and its status. Tapping either launches Google Maps
navigation to the selected pump. FuelGo does NOT do in-app navigation.

This is a low-effort utility / portfolio play, not a feature-heavy product. Do not
over-build. Every feature below is scoped to keep total effort small.

## Non-goals (do not build these)

- No iOS version.
- No user accounts, login, or profiles. Anonymous device ID only.
- No in-app navigation or turn-by-turn directions — hand off to Google Maps.
- No ads, no analytics beyond a minimal local event log if needed.
- No pump price comparison, no rewards, no social features.
- No real-time operator/partner integrations.

## Tech stack (keep it boring)

- Language: Kotlin.
- UI: Jetpack Compose for the app; Jetpack Glance for the widget.
- Min SDK 26 (Android 8.0), target/compile latest stable.
- Architecture: simple MVVM with ViewModels + Kotlin coroutines/Flow. Manual DI
  (a small AppContainer) — do NOT add Hilt/Koin; it is not worth it at this size.
- Networking: Retrofit + OkHttp (or Ktor if simpler) with kotlinx.serialization.
- Local storage: DataStore for user settings (fuel type); Room for the station
  cache. Keep the schema minimal.
- v2 backend: Firebase (Firestore + Cloud Functions). No custom server.

## Data sources

### Station data (v1)

Primary: Google Places API (Nearby Search, type `gas_station`), plus Place Details
for opening hours where needed. Requirements:

- API key read from `local.properties` as `PLACES_API_KEY` (never committed).
- Aggressive caching: station locations rarely change. Cache station POIs in Room
  keyed by geohash/area; only the distance ranking is recomputed per fix.
- A single app open should make at most 1–2 network calls. Widget updates must
  work from cache with a stale-while-revalidate pattern.

Fallback: OpenStreetMap Overpass API (free, no key). The app MUST be runnable and
demoable without a Places key — OSM fallback should be the default when no key is
configured, so builds work out of the box.

### Open/closed status

- v1: derived from published opening hours (Places/OSM `opening_hours`). "Open" =
  current time within parsed hours. Handle 24/7 values and unparsable hours
  gracefully ("hours unknown").
- v2: crowdsourced reports from app users (see below), decayed over time.

## v2 — crowdsourced status and crowd level

All data comes from FuelGo users themselves. Firebase backend:

- Firestore collection `reports`: { stationId, deviceId, status
  (OPEN | CLOSED | QUEUE_SHORT | QUEUE_LONG), fuelType, timestamp }.
- Cloud Function (or Firestore aggregation on read) computes per-station current
  status: most recent reports within a 60-minute window win; a station with no
  fresh reports falls back to v1 hours-derived status.
- Crowd level: shown as a simple 3-state badge (Quiet / Moderate / Busy) derived
  from recent QUEUE_SHORT/QUEUE_LONG report ratio and count. Do not invent
  precision — three buckets is enough.
- Abuse guard (minimal): rate-limit reports per device per station (e.g. 1 per
  10 minutes), ignore reports from a device that is not near the station
  (distance sanity check client-side).
- Reporting UI: on the pump detail/list item, one large button row —
  "Open now / Closed / Small queue / Long queue". Two taps maximum, no forms.

## App structure (deliverables)

```
app/
  src/main/
    java/.../fuelgo/
      ui/            # Compose screens: Onboarding, Home (pump list), Settings
      widget/        # Glance widget: 1x1 circle, nearest pump + status dot
      data/
        api/         # Places client, OSM Overpass client
        db/          # Room: StationEntity, ReportEntity
        repo/        # StationRepository, ReportRepository
        prefs/       # DataStore: selected fuel type, onboarding done
      domain/        # Status derivation (hours parsing + report aggregation)
      di/            # AppContainer (manual)
  src/test/          # Unit tests: hours parser, status aggregation, caching logic
```

### Screens and behavior

1. Onboarding (first run only): pick fuel type (Diesel / Petrol / CNG). One
   screen, one tap, done.
2. Home: nearest 3–5 stations for the selected fuel type. Each row: name,
   distance, open/closed (v2: + crowd badge). Tap row → Google Maps navigation
   intent. Pull-to-refresh allowed; app must render instantly from cache on
   every open regardless of network state.
3. Settings: change fuel type, widget refresh preference, report a problem.
   Nothing else.

### Widget behavior

- 1x1 resizable AppWidget (Glance). Shows: nearest pump name (truncated) and a
  status dot (green open / red closed / grey unknown).
- Tap widget → opens Google Maps navigation to nearest pump directly (no app
  launch in between; if location unknown, open the app).
- Updates: on location change (passive, coarse is fine), on app open, and at
  most every 15 min via WorkManager. Widget must never block on network — render
  from cache, refresh in background.

## Performance requirements (these are the product)

- Cold open to readable list: < 500 ms from cache.
- Widget tap → Maps navigation: one intent, no intermediate screen.
- Zero network on the critical render path; network only refreshes cache.

## Build and test

- `./gradlew assembleDebug` must succeed on Windows (this project is developed
  on Windows 11; use forward-slash-safe Gradle tasks, no shell-only scripts).
- Unit tests required for: opening-hours parser, status aggregation/decay, and
  repository caching logic. UI tests are out of scope.
- README section: where to put `PLACES_API_KEY`, how to enable USB debugging and
  install on a physical device via `adb install`, how to run the Firebase
  emulator for v2 local testing.

## Milestones

1. M1 — Project scaffold, onboarding, home list from OSM fallback, hours-derived
   status. App runs end-to-end without any API key.
2. M2 — Places API integration with caching, Google Maps hand-off intents.
3. M3 — Glance widget with cache-first updates.
4. M4 (v2) — Firebase reports: schema, submit UI, aggregation, crowd badge.
5. M5 — Polish: error/empty states, offline behavior, README.
