# Home Studio

Android remote control for the Home Studio music server running on a Raspberry Pi:
now playing, agent requests, queue, history and taste.

Formerly AlarmAPI. Built with Kotlin and Jetpack Compose (`com.jamilcpu.homestudio`).

- Install on a phone: [docs/INSTALL.md](docs/INSTALL.md)
- Build: `./gradlew assembleDebug`; tests: `./gradlew testDebugUnitTest`
- Release builds are signed via `HOME_STUDIO_*` Gradle properties kept outside the repo.
