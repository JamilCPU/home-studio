# Home Studio Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn AlarmAPI into **Home Studio**, the Android control hub for the Home Studio Pi server: Now Playing remote with seek, Agent, Queue, Rate, History, Taste, Morning settings, and a media notification with quick 👍/👎.

**Architecture:** A single-activity Jetpack Compose app. A Ktor-client `HomeStudioApi` talks to the server (envelope JSON) at a base URL stored in DataStore. Each screen has a ViewModel exposing `StateFlow<UiState>`. A `NowPlayingRepository` polls `/api/now` every 1 s while the app is visible, or while the media notification is shown. The old alarm code (Room, AlarmManager, embedded Ktor server, trigger activity) is deleted. Morning now runs on the Pi, and the app only edits its settings.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM `2024.12.01`), Navigation Compose, `androidx.lifecycle` ViewModel + `collectAsStateWithLifecycle`, Ktor client 3.1.1 (OkHttp engine) + kotlinx.serialization 1.7.3, DataStore Preferences, Coil 2.7.0 for thumbnails, `androidx.media` `MediaSessionCompat`-style notification via `androidx.media3:media3-session` 1.5.0 (session only, with a custom `SimpleBasePlayer` that forwards to the API). Tests: JUnit4, `kotlinx-coroutines-test`, Ktor `MockEngine`, Compose UI tests (`ui-test-junit4`) for screen logic.

**Spec:** `JamilCPU/home-studio-server` → `docs/superpowers/specs/2026-11-01-listening-station-design.md`. **API contract:** `JamilCPU/home-studio-server` → `docs/api.md` (produced by the server plan's Task 13). Build against that document exactly.

**Repo:** `JamilCPU/home-studio`, local `F:\Dev\AlarmAPI`, default branch `master`. Work on `feat/home-studio-app`. Gradle wrapper commands run from the repo root on Windows: `.\gradlew.bat …`.

**Depends on:** the server plan's Task 13 (`docs/api.md`) being merged. Tasks 1–3 here can start before that; later tasks follow the contract.

## Global Constraints

- Application ID **`com.jamilcpu.homestudio`**, namespace the same, display name **"Home Studio"**. Set in Task 1 and never changed. The first install of the new ID happens only after Task 1.
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`, Java/Kotlin target 17 (unchanged from the current build).
- Repo is **public**: never commit the server URL, Tailscale names or IPs, or the signing keystore/passwords. The keystore lives outside the repo (`%USERPROFILE%\.android\home-studio.jks`) and is referenced via `~/.gradle/gradle.properties` keys `HOME_STUDIO_STORE_FILE`, `HOME_STUDIO_STORE_PASSWORD`, `HOME_STUDIO_KEY_ALIAS`, `HOME_STUDIO_KEY_PASSWORD`.
- Every release is signed with that one key, so updates install over the top and keep app data.
- Server base URL default is empty, and the app shows a first-run "Connect to your Pi" field. Plain HTTP is allowed **only** for `*.ts.net` (Tailscale MagicDNS) hosts via `network_security_config`, because the traffic runs inside Tailscale's encryption. Everything else stays disallowed.
- Bottom tabs, in order: **Now · Agent · Queue · History · Taste**. Morning sits behind a ⚙ icon in the top bar. Rate is a bottom sheet.
- Copy and labels follow the approved mockups (spec §4 "Mockups from the session").
- Never hard-code channel or creator names anywhere (suggestion chips are generic: "Podcast on…", "Classical playlist", "More like this").
- Immutable UI state: `data class` states, updated with `copy`.

## Review Focus

1. **Pi unreachable (Tailscale off, Pi down, wrong URL):** every screen shows a "Can't reach your Pi" banner with Retry. Nothing crashes or spins forever. (Task 2 `apiErrorBecomesUnreachableState`, Task 4 `nowPlayingShowsUnreachableBanner`.)
2. **Dragging the seek bar:** the slider must not jump back while the finger is down (polling must not overwrite it), and exactly one seek is sent on release. (Task 4 `dragDoesNotSnapBackAndSeeksOnce`.)
3. **Agent request takes minutes or fails:** the "working…" state survives leaving and reopening the tab (it's resumed from `GET /api/agent/requests`), and a failure shows the server's error text. (Task 5 `resumesRunningRequestOnReopen`, `showsFailureError`.)
4. **Server reports problems:** `cookies:"expired"`, `bluetooth:"disconnected"` and `claude:"rate_limited"` each show a clear one-line notice on Now Playing with what to do. (Task 4 `statusNoticesRendered`.)
5. **Rating validation:** Save is disabled until like/dislike, a score or notes is set. The server's 422 message is shown if it comes back anyway. (Task 6 `saveDisabledWhenEmpty`.)

---

## File Structure

```
app/src/main/java/com/jamilcpu/homestudio/
  HomeStudioApp.kt                 # Application: creates AppContainer
  AppContainer.kt                  # manual DI: settings, api, repositories
  MainActivity.kt                  # setContent { HomeStudioRoot() }
  data/Settings.kt                 # DataStore: serverUrl
  data/api/Dto.kt                  # @Serializable DTOs mirroring docs/api.md
  data/api/HomeStudioApi.kt        # suspend functions, envelope → Result
  data/NowPlayingRepository.kt     # polling StateFlow<NowPlayingDto?> + status
  ui/HomeStudioRoot.kt             # Scaffold, bottom nav, NavHost, connect gate
  ui/theme/Theme.kt
  ui/now/NowPlayingScreen.kt, NowPlayingViewModel.kt
  ui/agent/AgentScreen.kt, AgentViewModel.kt
  ui/queue/QueueScreen.kt, QueueViewModel.kt
  ui/rate/RateSheet.kt, RateViewModel.kt
  ui/history/HistoryScreen.kt, HistoryViewModel.kt
  ui/taste/TasteScreen.kt, TasteViewModel.kt
  ui/morning/MorningScreen.kt, MorningViewModel.kt
  ui/connect/ConnectScreen.kt
  playback/RemotePlayer.kt          # SimpleBasePlayer forwarding to the API
  playback/RemoteSessionService.kt  # MediaSessionService + 👍/👎 custom commands
app/src/main/res/xml/network_security_config.xml
DELETE: everything under app/src/main/java/com/brainwash/alarm/, AlarmTrigger/Boot receivers in the manifest,
        Room/KSP/Ktor-server dependencies, viewBinding layouts.
docs/INSTALL.md                    # plain-language: dev options, wireless debugging, install, update
```

Tests: `app/src/test/java/com/jamilcpu/homestudio/...` (JVM: API, repositories, ViewModels) and `app/src/androidTest/...` (Compose UI tests for Tasks 4–6 behaviors that need gestures).

---

### Task 1: Rebrand, Compose skeleton, signing

**Files:** Modify `app/build.gradle.kts`, `settings.gradle.kts` (`rootProject.name = "home-studio"`), `build.gradle.kts`, `AndroidManifest.xml`, `.github/workflows/build.yml`, `README.md`; Delete `com/brainwash/alarm/**`, layouts; Create `HomeStudioApp.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`, `docs/INSTALL.md`

**Interfaces:**
- Produces: `applicationId`/`namespace` `com.jamilcpu.homestudio`, `versionCode 2`, `versionName "0.2.0"`. A `release` signingConfig reading the four `HOME_STUDIO_*` Gradle properties. Falls back to debug signing with a printed warning when they're absent (so CI still builds). `buildFeatures { compose = true }`, Kotlin Compose compiler plugin matching the project's Kotlin version.
- Dependencies removed: Room, KSP, `ktor-server-*`, appcompat/constraintlayout/recyclerview/viewBinding. Added: the Compose BOM set, navigation-compose, lifecycle-viewmodel-compose, datastore-preferences, coil-compose, media3-session, kotlinx-coroutines-test, ktor-client-mock.

- [ ] **Step 1:** `git checkout -b feat/home-studio-app`
- [ ] **Step 2:** Make the build changes and delete the old code. `MainActivity` shows `Text("Home Studio")` inside `HomeStudioTheme`.
- [ ] **Step 3:** `.\gradlew.bat assembleDebug`. Expect BUILD SUCCESSFUL.
- [ ] **Step 4:** Create the keystore once with `keytool -genkeypair -v -keystore %USERPROFILE%\.android\home-studio.jks -alias homestudio -keyalg RSA -keysize 2048 -validity 10000` and add the properties to `%USERPROFILE%\.gradle\gradle.properties`. Then `.\gradlew.bat assembleRelease`. Expect a signed APK in `app/build/outputs/apk/release/`.
- [ ] **Step 5:** Write `docs/INSTALL.md`: Pixel Developer options (tap Build number 7×), Wireless debugging, `adb pair <ip:port>` / `adb connect <ip:port>`, `adb install -r app-release.apk`, and why the keystore must be backed up. Uninstall the old `com.brainwash.alarm` app first.
- [ ] **Step 6:** Commit `chore: rebrand to Home Studio, compose skeleton, release signing`.

### Task 2: API client and settings

**Files:** Create `data/Settings.kt`, `data/api/Dto.kt`, `data/api/HomeStudioApi.kt`, `AppContainer.kt`, `res/xml/network_security_config.xml`; Test `data/api/HomeStudioApiTest.kt`

**Interfaces:**
- `Dto.kt`: `@Serializable` classes exactly matching `docs/api.md`: `Envelope<T>(ok: Boolean, data: T? = null, error: String? = null)`, `NowPlayingDto`, `QueueItemDto`, `ChapterDto`, `VideoDto`, `QueueDto`, `HistoryEntryDto`, `RatingDto`, `TasteDto`, `AgentRequestDto`, `MorningDto`, `StatusDto`, `StatusEventDto`, and the request bodies. Use `Json { ignoreUnknownKeys = true }`.
- `sealed interface ApiResult<out T> { Ok(value) ; Failure(message: String, kind: Kind) }`, where `enum Kind { Unreachable, Server, Validation }`.
- `class HomeStudioApi(baseUrl: () -> String?, engine: HttpClientEngine)`: one suspend fun per endpoint row in `docs/api.md` (`now()`, `toggle()`, `next()`, `previous()`, `seek(positionS)`, `skip(deltaS)`, `queue()`, `enqueue(videoId?, url?, where)`, `removeQueueItem(id)`, `clearQueue()`, `reorder(ids)`, `search(q)`, `rate(videoId, liked?, satisfaction?, notes?)`, `history(unratedOnly, limit, offset)`, `taste()`, `correctTaste(text)`, `removeTrait(kind, text)`, `submitAgent(prompt, play)`, `agentRequest(id)`, `agentRequests(limit)`, `morning()`, `saveMorning(dto)`, `testMorning()`, `status()`). Each returns `ApiResult`. Connection errors and timeouts (5 s connect, 10 s request) → `Unreachable`. 422 → `Validation` with the envelope error. Other non-2xx → `Server`.
- `Settings`: `serverUrl: Flow<String?>`, `suspend setServerUrl(url)` (validates that it starts with `http://` or `https://`).
- `network_security_config.xml`: the base config disallows cleartext. One `domain-config` with `<domain includeSubdomains="true">ts.net</domain>` allows cleartext. The Pi is therefore always addressed by its Tailscale MagicDNS name (`http://<pi-name>.<tailnet>.ts.net:8765`), and `setServerUrl` rejects plain-HTTP URLs whose host doesn't end in `.ts.net` with the message "Use your Pi's Tailscale name (….ts.net)". INSTALL.md explains where to find that name.

- [ ] **Step 1: Failing tests** (MockEngine): `rejectsPlainHttpNonTailscaleUrl`, `parsesNowPlayingEnvelope`, `seekPostsPositionBody` (body `{"position_s":120.0}`), `apiErrorBecomesUnreachableState` (engine throws `ConnectException` → `Failure(kind=Unreachable)`), `validationErrorCarriesMessage` (422 + envelope error → `Validation`), `missingBaseUrlIsUnreachable`.
- [ ] **Step 2:** `.\gradlew.bat testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: home studio api client and settings`.

### Task 3: App shell, navigation, connect screen

**Files:** Create `ui/HomeStudioRoot.kt`, `ui/connect/ConnectScreen.kt`; Modify `MainActivity.kt`; Test `androidTest/ui/RootTest.kt`

**Interfaces:**
- `HomeStudioRoot(container: AppContainer)`: shows `ConnectScreen` while `serverUrl` is null. Otherwise a `Scaffold` with a top bar (title of the current tab plus a ⚙ action → `morning` route) and a bottom `NavigationBar` with the tabs Now/Agent/Queue/History/Taste (routes `now`, `agent`, `queue`, `history`, `taste`). The start destination is `agent` (the spec's main entry point).
- `ConnectScreen(onSave(url))`: URL field, a "Test connection" button that calls `api.status()`, and a Save button enabled only after a successful test.

- [ ] **Step 1: Failing UI tests:** `showsConnectWhenNoUrl`, `startsOnAgentTab`, `bottomBarHasFiveTabsInOrder`, `gearOpensMorning`.
- [ ] **Step 2:** `.\gradlew.bat connectedDebugAndroidTest` (emulator or the Pixel over wireless ADB). Expect FAIL.
- [ ] **Step 3:** Implement, with placeholder screens. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: app shell, navigation and connect screen`.

### Task 4: Now Playing (approved v2)

**Files:** Create `data/NowPlayingRepository.kt`, `ui/now/NowPlayingScreen.kt`, `ui/now/NowPlayingViewModel.kt`; Test `test/.../NowPlayingViewModelTest.kt`, `androidTest/.../NowPlayingScreenTest.kt`

**Interfaces:**
- `NowPlayingRepository(api, scope)`: `now: StateFlow<NowPlayingDto?>`, `status: StateFlow<StatusDto?>`, `reachable: StateFlow<Boolean>`. Polls `now()` every 1000 ms and `status()` every 15 s while `start()`ed. `stop()` cancels. It's shared by the screen and the notification service.
- `NowPlayingUiState(item, state, positionS, durationS, outputDevice, chapters, notices: List<String>, unreachable: Boolean, dragPositionS: Float?)`. `NowPlayingViewModel` has `onDragStart()`, `onDrag(s)`, `onDragEnd()` (sends one `seek`, clears drag), `toggle()`, `next()`, `previous()`, `skipBack()` (−15), `skipForward()` (+30), `quickRate(liked: Boolean)`, `chapterTapped(startS)`, `openInYoutubeUri(): Uri` → `https://www.youtube.com/watch?v=<id>&t=<floor(pos)>s`.
- Notices (exact copy): cookies expired → "YouTube sign-in expired. Re-upload cookies on the Pi."; bluetooth disconnected → "Speaker disconnected. Reconnecting…"; claude rate_limited → "Agent is rate-limited. Try again later."; claude not_logged_in → "Agent isn't logged in on the Pi."; idle with empty queue → "Queue empty" with an "Ask the agent" button → `agent`.
- Layout per mockup v2: thumbnail (Coil), title, channel, "🤖 Picked because: <reason>", seek bar with chapter tick marks, elapsed "m:ss elapsed" / "−m:ss left", current chapter name, ⏪15s ⏮ ⏯ ⏭ 30s⏩, output device "🔊 <label>", 👍 👎 ✏️ Rate row, and an "↗ Open in YouTube at m:ss" link.

- [ ] **Step 1: Failing tests:** `dragDoesNotSnapBackAndSeeksOnce` (polled position updates during the drag don't change `dragPositionS`; one `seek` call on end), `skipButtonsSendMinus15Plus30`, `openInYoutubeUriHasTimestamp` (pos 451.7 → `t=451s`), `statusNoticesRendered`, `nowPlayingShowsUnreachableBanner`, `emptyQueueShowsAskAgent`.
- [ ] **Step 2:** Run unit and UI tests. Expect FAIL. **Step 3:** Implement. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: now playing remote with seek, chapters and notices`.

### Task 5: Agent tab

**Files:** Create `ui/agent/AgentScreen.kt`, `ui/agent/AgentViewModel.kt`; Test `test/.../AgentViewModelTest.kt`

**Interfaces:**
- `AgentUiState(history: List<AgentRequestDto>, input: String, playMode: "now"|"end", sending: Boolean, active: AgentRequestDto?)`. `AgentViewModel(api, clock)`: `onInput`, `send()` (POST, then poll `agentRequest(id)` every 2 s until `done`/`failed`), `playNow(requestId)` (moves that request's items to the front with `reorder`, then `next()` if idle), `addToEnd(requestId)` (no-op if already queued at the end), `chip(text)` (prefills the input). On init it loads `agentRequests(20)`, and if any is `pending`/`running` it resumes polling it.
- UI per mockup: chat bubbles (the user's prompt on the right; agent replies "Working… searching, reading transcripts", then "Queued N videos (M min):" with the list, "Why:" line, and Play now / Add to end buttons). The "Try" chips: "Podcast on…", "Classical playlist", "More like this". Input "Ask for something to play…" with the keyboard's mic for dictation.

- [ ] **Step 1: Failing tests:** `sendPostsAndPollsUntilDone`, `resumesRunningRequestOnReopen`, `showsFailureError` (failed request with error "rate limited" is exposed in state), `emptyInputCannotSend`, `chipPrefillsInput`.
- [ ] **Step 2:** Run. Expect FAIL. **Step 3:** Implement. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: agent tab`.

### Task 6: Queue, Rate, History, Taste, Morning

**Files:** Create the screen + ViewModel pairs under `ui/queue`, `ui/rate`, `ui/history`, `ui/taste`, `ui/morning`; Test one ViewModel test file per screen

**Interfaces (ViewModel → API):**
- **Queue:** list with source tags (Agent / You added / Morning, from `source` values `agent` / `user` / `morning` and `fallback` shown as "Morning"), drag handle reorder → `reorder(ids)`, swipe to remove → `removeQueueItem`, "Ends in Xh Ym, then stops" from `remaining_s`, "🔍 Search / add" (a dialog using `search(q)`, plus pasting a YouTube link → `enqueue(url=…)`), and Clear (with a confirmation dialog).
- **Rate sheet:** `RateViewModel(videoId)`: liked toggle (👍 Like / 👎 Dislike), satisfaction 1–10 chips, notes (≤2000 chars), Save → `rate(...)`. Save is disabled while all three are empty. It opens from Now Playing ✏️, from a History row, and from the notification (Task 7).
- **History:** grouped by local day ("Today", "Yesterday", then the date), each row showing "👍 8" / "👎 3" / "skipped m:ss" / "Rate?". Filter "Unrated (n)" → `history(unratedOnly=true)`. Paged with 50 per page.
- **Taste:** AI summary bubble, Likes and Dislikes lists each with ✕ → `removeTrait`, and an input "Correct it: \"I actually like…\"" → `correctTaste`. Shows "Updating…" until a `taste()` refetch returns a newer `updated_at` (poll every 3 s, give up after 60 s with "Will update after the agent's next run").
- **Morning (⚙):** on/off switch, a time picker (24-hour stored as "HH:MM", shown as 12-hour, labeled "Central Time · agent starts picking at <wake−5>"), day toggles M–S (Monday=0), topic chips (removable, "+ add"), count stepper 1–6, a status line from `status().events` (latest `morning` event), and "▶ Test it now" → `testMorning()` then navigate to `agent`.

- [ ] **Step 1: Failing tests:** `reorderSendsFullIdList`, `remainingTimeFormatted` (6060 s → "Ends in 1h 41m, then stops"), `saveDisabledWhenEmpty`, `ratingSendsOnlySetFields` (notes only → JSON has no `liked`/`satisfaction`), `historyGroupsTodayYesterday`, `removeTraitCallsApi`, `morningTimeLabelShowsMinus5` ("07:45" → "agent starts picking at 7:40 AM"), `morningRejectsEmptyTopics`.
- [ ] **Step 2:** Run. Expect FAIL. **Step 3:** Implement. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: queue, rate, history, taste and morning screens`.

### Task 7: Media notification with 👍/👎

**Files:** Create `playback/RemotePlayer.kt`, `playback/RemoteSessionService.kt`; Modify `AndroidManifest.xml`; Test `test/.../RemotePlayerTest.kt`

**Interfaces:**
- `RemotePlayer(repo: NowPlayingRepository, api, scope) : SimpleBasePlayer`: state comes from `repo.now` (title, artist=channel, artwork URI, duration, position, playWhenReady from `state=="playing"`). `handleSetPlayWhenReady` → `toggle()`, `handleSeek` → `seek()`, next/previous → `next()`/`previous()`. **It never plays audio locally.**
- `RemoteSessionService : MediaSessionService` with custom layout buttons 👍 and 👎 (`SessionCommand("home_studio.like")` / `"home_studio.dislike"`) → `api.rate(videoId, liked=…)`. Tapping the notification opens the app on Now Playing with the Rate sheet. The service starts when the app first sees `state != "idle"` and stops itself after 10 minutes idle. Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (requested at runtime on first launch).

- [ ] **Step 1: Failing tests:** `playerStateMirrorsNowPlaying`, `seekForwardsToApi`, `likeCommandRatesCurrentVideo`.
- [ ] **Step 2:** Run. Expect FAIL. **Step 3:** Implement. **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit `feat: media notification remote with quick ratings`.

### Task 8: Release build, install on the Pixel, end-to-end check

**Files:** Modify `.github/workflows/build.yml` (the artifact name `home-studio-<version>.apk`; release signing only when secrets exist), `README.md`, `docs/INSTALL.md`

- [ ] **Step 1:** `.\gradlew.bat testDebugUnitTest assembleRelease`. Expect PASS and a signed APK.
- [ ] **Step 2:** Uninstall the old `com.brainwash.alarm`, pair wireless ADB with the Pixel, then `adb install -r app\build\outputs\apk\release\app-release.apk`. Expect "Success" and the app launching as "Home Studio".
- [ ] **Step 3:** With the server from the server plan running on Pi3, connect using the Pi's Tailscale MagicDNS URL. Then check each of these on the phone: an Agent request queues and plays on the Echo; dragging the seek bar moves the Echo's audio; 👍 in the notification creates a rating (it shows in History); a Taste ✕ removes a trait; Morning "Test it now" starts a morning set; with Tailscale turned off, the "Can't reach your Pi" banner appears.
- [ ] **Step 4:** Build a second release with `versionCode 3` and install it over the top. Expect the server URL to still be set (proves same-key updates keep data).
- [ ] **Step 5:** Commit `chore: release workflow and install guide`. Push and open a PR to `master`.

---

## Self-review notes
- Spec coverage: real installed app on the Pixel (T1, T8), sideload and same-key updates (T1, T8), Tailscale fixed address (T2, T3), every approved screen (T4–T6), notification 👍/👎 (T7), alarm becomes Morning settings with the old alarm code removed (T1, T6), no creator names (Global), public-repo secrets (Global, T1).
- Not in this plan (spec §9): in-app YouTube browsing, synced video, Pi-hosted APK self-update, packaging for others.
