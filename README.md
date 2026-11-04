# Home Studio (Android)

The Android companion to the Home Studio server (`home-studio-server`, a Raspberry Pi service that plays YouTube audio to a Bluetooth speaker). The app is meant to be the phone remote for that server: now playing, agent requests, queue, history and taste.

This branch holds the app's original form, **AlarmAPI**: an Android alarm app that runs an embedded REST server (Ktor/Netty) so alarms can be listed, created, updated, toggled and deleted over HTTP, with exact-time scheduling, a boot receiver and a full-screen trigger activity. The rewrite into the Home Studio remote (Jetpack Compose, application ID `com.jamilcpu.homestudio`) is in progress on the `feat/home-studio-app` branch, which has its own install notes in `docs/INSTALL.md`.

## Stack

- Kotlin, Android (minSdk 26, target/compile SDK 35), Java 17
- Room (local alarm database) with KSP, AlarmManager exact alarms, foreground service
- Embedded Ktor server (Netty) with kotlinx.serialization JSON, Ktor client (OkHttp)
- Gradle Kotlin DSL; GitHub Actions workflow that builds a debug APK

## Running

Requires JDK 17 and the Android SDK.

```
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest
```

Install the APK on a device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The embedded server exposes `GET /api/health`, `GET/POST /api/alarms`, `GET /api/alarms/next`, and `GET/PUT/DELETE /api/alarms/{id}` plus `POST /api/alarms/{id}/toggle`.

## Project layout

| Path | What |
|------|------|
| `app/src/main/java/com/brainwash/alarm/api/` | Ktor routes |
| `.../data/` | Room entities, DAO, repository, settings |
| `.../service/` | alarm scheduler and receivers, API server foreground service |
| `.../ui/` | main screen and alarm trigger activity |
| `docs/superpowers/plans/` | implementation plan for the Home Studio rewrite |

## Status

Work in progress. The Home Studio remote is being built on `feat/home-studio-app` and is not yet merged. The embedded server has no authentication, so run it only on a trusted network.
