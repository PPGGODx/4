# PlayZongX — Hybrid Music App (Online/Offline) — Design

Date: 2026-11-19
Status: Draft for approval
Owner: personal use (single user, sideloaded APK)

## 1. Intent and why

Build a personal Android music app that behaves like a YouTube Music client with a
real offline-first library:

- Stream audio from YouTube Music when nothing is available locally.
- Download tracks once, play them with zero network afterwards.
- Back up metadata and downloaded audio to the user's own Google Drive
  (`appDataFolder`), so a reinstall or a second device can restore the library
  without any server of our own.

The app is for **personal use only** and will be **sideloaded**, not published on
Google Play. This is a deliberate constraint: the InnerTube-based stream
resolution and audio downloads are against YouTube's ToS, so distribution is out
of scope.

## 2. Goals

1. Play music reliably in three tiers: local file → Google Drive backup → live
   stream.
2. Fully offline playback and library browsing for downloaded tracks.
3. Search and browse YouTube Music (songs, artists, albums, playlists, charts).
4. Background playback with notification and lock-screen controls.
5. Download queue with progress, retry, cancel, and Wi-Fi-only option.
6. Personal playlists, favorites, and recent history stored locally (Room).
7. Metadata sync and optional audio backup to Google Drive `appDataFolder`.
8. Sleep timer and equalizer.
9. Build a working debug APK locally, and optionally in GitHub Actions.

## 3. Non-goals

- Publishing to Google Play, multi-user support, accounts, or any backend server.
- Signing into the user's own YouTube Music account in v1: no liked songs,
  personal subscriptions, or YT Music playlists. Search/catalog is public only;
  playlists are created and stored locally in Room.
- Video playback (audio-only).
- Lyrics, crossfade, gapless playback, social features.
- Automatic DRM or paid YouTube Music features.
- Perfect parity with the official YouTube Music app.
- iOS/desktop clients.

## 4. Anti-goals (what would make this a failure even if it compiles)

- Downloaded songs fail to play without a network (Tier 1 must be truly offline).
- A sync run deletes or corrupts the local library.
- The app silently shows "cannot play" with no explanation when a tier fails.
- Audio backup runs on metered connections or drains the battery.
- YouTube changes break playback with no fallback and no visible error.

## 5. Constraints

- Personal use, sideload only; no Play Store policy compliance.
- No server: all cloud storage is the user's own Google Drive (15 GB shared quota).
- Google Drive sync **is in scope** (user decision): a Google Cloud project with an
  Android OAuth client is required. The consent screen is set to
  "In production (unverified)" for personal use so refresh tokens stay valid;
  the app is expected to show Google's unverified-app warning at sign-in.
- One fixed signing keystore (`playzongx.jks`, never committed) is used for both
  local and CI builds; its SHA-1 is registered on the OAuth client. CI receives
  the keystore via base64 secret. Local build is the primary install path.
- Device verification is the owner's job; the agent supplies a per-milestone
  checklist. Automated tests are JVM/Robolectric only (no emulator/device here).
- InnerTube and extractor behavior can change at any time; the stream layer must
  be isolated behind an interface and degrade gracefully.
- App data folder is hidden from other apps but counts toward the user's quota;
  audio backup must be optional and user-controlled.
- Build toolchain lives on this machine; CI builds may be used to produce APKs.

## 6. Approaches considered (decided with the user)

### 6.1 Build/toolchain

| Option | Trade-off |
|---|---|
| A. Install full toolchain locally (chosen) | One-time install of JDK 21, cmdline-tools, platform 35, build-tools; enables local compile, tests, APK |
| B. Code only, user builds in Android Studio | Less setup, but no verification by the agent |

Decision: **A**, plus a GitHub Actions workflow that can produce a debug APK
artifact as a fallback/portable build.

### 6.2 Stream resolution

| Option | Trade-off |
|---|---|
| A. Custom InnerTube only | Full control, but must implement signature deciphering and PoToken handling; brittle |
| B. NewPipeExtractor only | Robust stream URLs, but weaker YT Music metadata/browse modeling |
| C. Hybrid (chosen) | InnerTube custom for search/browse/metadata; NewPipeExtractor for stream URLs |

Decision: **C**. InnerTube (`WEB_REMIX`) gives proper YouTube Music metadata;
NewPipeExtractor absorbs the fragile player/cipher work.

## 7. Architecture

Single Gradle module `:app`, manual dependency injection (`AppContainer`), Kotlin
+ Jetpack Compose + Material 3.

```
com.playzongx
  app/            Application, AppContainer, MainActivity, navigation
  core/           Result wrappers, dispatchers, time/format utils
  data/
    db/           Room database, entities, DAOs, migrations
    network/      InnerTubeClient, YT Music DTOs + mappers, DriveApi, AuthManager
    repo/         MusicRepository, LibraryRepository, DownloadRepository, SyncRepository
    download/     DownloadWorker, DownloadScheduler
    prefs/        DataStore settings
  player/         PlaybackService (MediaSessionService), PlaybackResolver,
                  QueueManager, EqualizerController, SleepTimer
  sync/           MetadataSync, AudioBackupWorker
  ui/             theme, screens (home, search, library, playlist, player, settings)
```

### 7.1 Playback resolution (3 tiers)

`PlaybackResolver.resolve(songId)` returns a `ResolvedSource`:

1. **Local**: a finished download exists at `files/audio/<songId>.<ext>` → play
   the file. Works with no network.
2. **Drive**: `drive_backups` has a file id → download to the local file store
   (then treat as Tier 1). Requires network; if offline, report a clear,
   specific error.
3. **Stream**: resolve a fresh audio URL with NewPipeExtractor → play via
   ExoPlayer with a disk LRU cache. Offer "Download" in the UI.

Note: Tier 2 is a backup source, not an offline source. The design must not
promise offline playback unless Tier 1 exists; the UI shows a "not downloaded"
badge when applicable.

Cache hits are not a fourth tier: they are a transparent ExoPlayer cache.

### 7.2 InnerTube client

- HTTP via Ktor/OkHttp, shared client instance.
- Client context: `WEB_REMIX`, `hl=th`, `gl=TH`, public web API key, standard
  headers; version pinned in one constants file.
- Endpoints:
  - `POST /youtubei/v1/search` for songs/albums/playlists/artists.
  - `POST /youtubei/v1/browse` for home shelves/charts and playlist contents
    (`VL` + playlist id).
  - `POST /youtubei/v1/player` (optional, diagnostics only; stream URLs come from
    NewPipeExtractor).
- Parse `musicResponsiveListItemRenderer`, `musicShelfRenderer`,
  `musicCarouselShelfRenderer` into `Track`, `Album`, `Artist`, `Playlist`
  domain models. All parsing isolated in `Mappers` with JSON fixtures for tests.
- Errors are typed: `Network`, `Parse`, `Unavailable`, `AgeRestricted`; the UI
  shows a retry affordance.

### 7.3 Stream URLs

- `NewPipeExtractor` resolves a video id to audio streams; prefer Opus
  (`audio/webm; codecs=opus`, ~128 kbps), fall back to M4A/AAC.
- If extraction fails, surface "stream unavailable" and keep Tier 1/2 usable.
- No attempt to bypass paid/DRM-only content; unsupported items are marked
  unavailable.

### 7.4 Local database (Room)

- `songs`: `id` (videoId, PK), `title`, `artist`, `album`, `durationMs`,
  `thumbnailUrl`, `addedAt`, `updatedAt`, `lastPlayedAt`, `playCount`,
  `isFavorite`, `deletedAt` (tombstone), `source` (STREAM/DOWNLOAD).
- `playlists`: `id` (UUID), `name`, `createdAt`, `updatedAt`, `deletedAt`.
- `playlist_songs`: `(playlistId, songId)` PK, `position`, `addedAt`.
- `downloads`: `songId` PK/FK, `status` (QUEUED/DOWNLOADING/DONE/FAILED),
  `filePath`, `fileSize`, `quality`, `error`, `createdAt`, `completedAt`.
- `drive_backups`: `songId` PK/FK, `driveFileId`, `sha256`, `fileSize`,
  `uploadedAt`.
- `sync_state`: key/value (last metadata sync, last audio scan, schema version).
- Indices on `lastPlayedAt`, `updatedAt`, `isFavorite`.

Library browsing reads only from Room; the network only enriches metadata.

### 7.5 Download pipeline

- User action → `DownloadScheduler.enqueue(songId)` with a unique WorkManager
  work name per song; constraint: Wi-Fi only if the setting is enabled.
- `DownloadWorker` (foreground, progress notification): resolve fresh stream URL
  → download to a temp file → atomic rename to `files/audio/<songId>.opus` →
  update `downloads` → enqueue audio backup.
- Cancel removes work and temp files. Failures retry with exponential backoff
  (max 3), then mark FAILED with a readable reason.
- Disk guard: refuse downloads below a configured free-space threshold.

### 7.6 Google Drive sync

- Auth: Credential Manager Google Sign-In for identity; `AuthorizationClient`
  with scope `https://www.googleapis.com/auth/drive.appdata` for an access token.
  Refresh on demand; never persist raw tokens in plain text.
- Drive API v3 via HTTP (`files.list/create/get/update/delete`,
  `alt=media`, `uploadType=multipart`), restricted to `spaces=appDataFolder`.
- Files:
  - `manifest.json`: `{ schemaVersion, exportedAt, songs[], playlists[],
    playlistSongs[], tombstones[] }` — the authoritative metadata snapshot.
  - `audio/<songId>.opus` — downloaded audio.
  - `audio_index.json`: `{ songId, driveFileId, size, sha256, uploadedAt }[]`.
- Merge policy: item-level last-write-wins by `updatedAt`; tombstones win over
  older edits; unknown fields ignored; `schemaVersion` checked before import.
- Import never deletes local files; it only removes DB rows that are tombstoned
  and older than the last sync, after a confirmation summary.
- `AudioBackupWorker`: periodic + on-demand; constraints `UNMETERED` + `CHARGING`;
  uploads only missing/changed files (by `sha256`), skips items deleted locally,
  respects a user toggle.
- Backup scope: only songs the user explicitly downloaded (never the whole
  catalog). A configurable size cap (default: 10 GB or 80% of remaining Drive
  quota, whichever is smaller) guards the shared 15 GB.
- Quota handling: on HTTP 403 / storage-quota errors, stop the queue, notify once,
  keep all local files, mark affected items `PENDING`, and offer
  "clean up Drive backups" in Settings. Never delete Drive or local audio without
  explicit confirmation.
- OAuth setup: one Google Cloud project, one Android OAuth client bound to
  `com.playzongx` + the fixed keystore SHA-1, scope `drive.appdata`, consent
  screen published as unverified (personal use). Sign-out clears tokens and
  disables background backup.
- UI shows last sync time, pending uploads, quota usage, and storage used.

### 7.7 Player

- `MediaSessionService` + ExoPlayer; `MediaController` in Compose UI.
- `MediaItem` carries `songId` and resolved URI; queue mirrors the active
  playlist/album/search result in DB-backed order.
- Audio focus and becoming-noisy handled by ExoPlayer audio attributes.
- Foreground service with a media-style notification and lock-screen controls;
  `POST_NOTIFICATIONS` requested on Android 13+.
- `SimpleCache` LRU for streams (default 1 GB, configurable), cache keyed by
  video id; downloaded files bypass the cache.
- `SleepTimer`: coroutine in the service, optional fade-out, pause at zero.
- `EqualizerController`: Android `Equalizer` bound to `player.audioSessionId`;
  enable toggle + 5 bands; released and rebound when the session id changes.

### 7.8 UI

Material 3, dark-first, bottom navigation with:

- **Home**: recent plays, favorites, charts/home shelves, sync status chip.
- **Search**: query field, filter chips (songs/albums/playlists/artists), results.
- **Library**: tabs Songs / Playlists / Downloads, offline badge, sort.
- **Now Playing**: full-screen player + mini-player above the nav bar.
- **Playlist detail**, **Album detail**, **Artist detail** (basic).
- **Downloads**: queue, progress, retry/cancel, storage usage.
- **Settings**: account, sync now, Wi-Fi-only downloads, cache limit, audio
  backup toggle, equalizer, sleep timer, theme.

Offline state is visible: a banner and per-item download badges. Every failure
shows an actionable message (retry, download, sign in).

## 8. Build and tooling

- JDK 21 (`jdk21-openjdk`), Android cmdline-tools, `platforms;android-35`,
  `build-tools;35.0.0`, `platform-tools`.
- Gradle wrapper, version catalog (`libs.versions.toml`), Kotlin 2.x, Compose BOM,
  Media3, Room (KSP), WorkManager, Ktor/OkHttp, NewPipeExtractor (JitPack),
  DataStore, Credential Manager, Google Identity Authorization.
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`; package `com.playzongx`.
- `local.properties` points to the SDK; never committed.
- Signing: generate `playzongx.jks` once; use it for local and CI builds so the
  signature (and therefore the OAuth client SHA-1) stays stable and CI APKs can
  upgrade over local installs. Keystore + passwords live outside git; CI reads a
  base64 secret. Register both the keystore SHA-1 and the debug-key SHA-1 (if a
  debug variant is installed) on the OAuth client.
- GitHub Actions: Ubuntu runner, JDK 21, `./gradlew assembleDebug` (or
  `assembleRelease` with the fixed keystore), upload the APK artifact.
- Room schema export enabled; migrations tested.

## 9. Testing strategy

- Unit tests (JVM):
  - InnerTube response parsing from JSON fixtures.
  - InnerTube request/DTO mapping edge cases (missing fields, explicit content).
  - Tier resolution: local → drive → stream, including offline errors.
  - Sync merge: LWW, tombstones, schema mismatch, no destructive imports.
  - Download state machine and retry policy.
  - Metadata JSON round-trip.
- DAO tests with in-memory Room (instrumented or Robolectric).
- Compose UI smoke tests for navigation and empty/error states (Robolectric).
- Manual device checklist: background playback, lock screen, audio focus,
  airplane-mode playback, download → backup → restore on a clean install.

## 10. Delivery milestones

1. Project setup + toolchain + InnerTube search/browse.
2. Media3 playback of resolved streams, background playback.
3. Room schema + download pipeline + downloads UI.
4. Library UI + hybrid resolver + offline mode.
5. Google Sign-In + Drive auth.
6. Metadata export/import + merge + settings UI.
7. Audio backup with WorkManager (Wi-Fi + charging).
8. UI polish (sleep timer, equalizer), stability pass, APK build + CI.

Each milestone ends with a demoable build and passing tests for its logic.

## 11. Left to the builder

- Exact library versions and API shapes, verified during setup.
- Concrete Compose layouts, spacing, and icon choices within the Material 3
  direction.
- InnerTube field names for charts/home shelves as observed at build time.
- NewPipeExtractor version and any required initialization.
- Notification channel details and foreground-service types.
- Error copy (Thai/English) and empty-state illustrations.
