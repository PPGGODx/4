# PlayZongX — Phase 1: Foundation & InnerTube Search — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Android app that builds locally, searches YouTube Music through InnerTube, and resolves a playable audio URL through NewPipeExtractor.

**Architecture:** Single-module Kotlin/Compose app with a manual `AppContainer` (no Hilt). Phase 1 is a thin vertical slice: Compose search screen → `SearchViewModel` → `MusicRepository` → `InnerTubeClient` (OkHttp + kotlinx JSON parsing) and, on tap, `StreamResolver` backed by NewPipeExtractor. All parsing and selection logic is pure and unit-tested on the JVM.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, AGP 8.7.3, Gradle 8.11.1, JDK 21, OkHttp 4.12.0, kotlinx-serialization-json 1.7.3, NewPipeExtractor v0.24.8 (JitPack), Coil 2.7.0, JUnit4 + MockWebServer + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-19-playzongx-design.md`

## Global Constraints

- Package `com.playzongx`; `minSdk 26`, `targetSdk 35`, `compileSdk 35`.
- Kotlin `2.0.21`, AGP `8.7.3`, Gradle `8.11.1`, JDK 21, Compose BOM `2024.12.01`.
- Android SDK at `/home/tadach/Progarm/android-sdk`; `local.properties` must contain `sdk.dir=/home/tadach/Progarm/android-sdk` and is git-ignored.
- Single Gradle module `:app`; dependencies declared only in `gradle/libs.versions.toml`.
- No backend, no Google Play publishing, no YouTube account sign-in, no cookies.
- All network work runs on `Dispatchers.IO`; UI state is exposed as `StateFlow`.
- User-facing failures are typed `MusicError` with an actionable message; no silent failures.
- Material 3, dark theme by default; UI copy is English/Thai mixed per spec.
- Every commit is small and green (`./gradlew test` + `./gradlew assembleDebug`).

## Review Focus

1. InnerTube JSON with missing/renamed fields → parser returns partial results, never crashes.
2. No network / HTTP 5xx / timeout → UI reaches a retryable Error state, never an endless spinner.
3. Thai or special-character search text → request body preserves UTF-8 and results map correctly.
4. Extractor returns no Opus stream or only M4A → selector picks the best available or reports "unavailable".
5. Duplicate videoIds across shelves → results are de-duplicated and list keys are stable.

## File Structure (Phase 1)

```
settings.gradle.kts, build.gradle.kts, gradle.properties
gradle/libs.versions.toml, gradle/wrapper/*
gradlew, gradlew.bat, .gitignore, local.properties (ignored)
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/playzongx/
  PlayZongXApp.kt              Application + AppContainer init
  app/AppContainer.kt          manual DI graph
  MainActivity.kt              Compose host
  core/DurationParser.kt       "3:45" -> millis
  domain/Models.kt             Track, AlbumSummary, ArtistSummary, PlaylistSummary, SearchResult
  data/network/MusicError.kt   typed errors
  data/network/InnerTubeClient.kt  request + HTTP + JSON entry point
  data/network/SearchMapper.kt     tolerant JsonElement -> domain mapping
  data/network/NewPipeDownloader.kt  NewPipeExtractor Downloader over OkHttp
  data/network/NewPipeStreamResolver.kt
  data/repo/MusicRepository.kt      interface
  data/repo/InnerTubeMusicRepository.kt
  player/StreamResolver.kt          interface + ResolvedStream
  player/AudioStreamSelector.kt     pure candidate selection
  ui/App.kt                    nav scaffold
  ui/theme/Theme.kt, Color.kt, Type.kt
  ui/search/SearchViewModel.kt
  ui/search/SearchScreen.kt
app/src/test/java/com/playzongx/...
app/src/test/resources/fixtures/*.json
.github/workflows/android.yml
```

---

### Task 1: Toolchain, project scaffold, and first build

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/playzongx/MainActivity.kt`, `app/src/main/java/com/playzongx/ui/theme/Theme.kt`, `app/src/main/java/com/playzongx/ui/theme/Color.kt`, `app/src/main/java/com/playzongx/ui/theme/Type.kt`
- Create (generated): Gradle wrapper files

**Interfaces:**
- Produces: a buildable `:app` module; `com.playzongx.MainActivity` shows "PlayZongX".

- [ ] **Step 1: Install JDK (owner runs; needs sudo password)**

```bash
sudo pacman -S --needed jdk21-openjdk
java -version   # expect openjdk 21
```

- [ ] **Step 2: Install Android command-line tools and SDK packages**

```bash
mkdir -p /home/tadach/Progarm/android-sdk/cmdline-tools
curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-unpack
rm -rf /home/tadach/Progarm/android-sdk/cmdline-tools/latest
mv /tmp/cmdline-tools-unpack/cmdline-tools /home/tadach/Progarm/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/home/tadach/Progarm/android-sdk
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

- [ ] **Step 3: Generate the Gradle wrapper 8.11.1 (outside the project, then copy in)**

```bash
sudo pacman -S --needed gradle
mkdir -p /tmp/wrapgen && cd /tmp/wrapgen && gradle wrapper --gradle-version 8.11.1 --distribution-type bin
cp -r /tmp/wrapgen/gradle /tmp/wrapgen/gradlew /tmp/wrapgen/gradlew.bat /home/tadach/Documents/PlayZongX/
```

- [ ] **Step 4: Write project files**

`settings.gradle.kts`:
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "PlayZongX"
include(":app")
```

`gradle/libs.versions.toml` — pin exactly:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigation = "2.8.5"
okhttp = "4.12.0"
serialization = "1.7.3"
coroutines = "1.9.0"
newpipe = "v0.24.8"
coil = "2.7.0"
desugar = "2.1.3"
junit = "4.13.2"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newpipe" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`gradle.properties`: `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `kotlin.code.style=official`.

`app/build.gradle.kts` — namespace `com.playzongx`, `compileSdk 35`, `minSdk 26`, `targetSdk 35`, `versionCode 1`, `versionName "1.0"`, Java/Kotlin 17, buildFeatures compose, coreLibraryDesugaring enabled, deps from catalog (`implementation(platform(libs.compose.bom))`, ui, material3, activity-compose, lifecycle viewmodels, navigation, okhttp, serialization-json, newpipe, coil, desugar; `testImplementation(libs.junit)`, `testImplementation(libs.okhttp.mockwebserver)`, `testImplementation(libs.kotlinx.coroutines.test)`).

`AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET"/>`; application label "PlayZongX", `android:theme="@style/Theme.PlayZongX"` is optional — use `android:theme="@android:style/Theme.Material.NoActionBar"` to avoid extra resources; activity `MainActivity` with `exported="true"` and LAUNCHER intent filter.

`MainActivity.kt`: `ComponentActivity` + `setContent { PlayZongXTheme { Text("PlayZongX") } }`. `Theme.kt` defines `PlayZongXTheme(darkTheme: Boolean = true, content)` using `darkColorScheme()`; `Color.kt`/`Type.kt` minimal.

`.gitignore`: `.gradle/`, `build/`, `local.properties`, `*.iml`, `.idea/`, `*.jks`, `*.keystore`, `keystore.properties`.

- [ ] **Step 5: Create `local.properties` and initialize git**

```bash
echo "sdk.dir=/home/tadach/Progarm/android-sdk" > local.properties
git init && git add . && git commit -m "chore: scaffold PlayZongX Android project"
```

- [ ] **Step 6: Verify the build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk` exists.

---

### Task 2: Duration parsing and domain models

**Files:**
- Create: `app/src/main/java/com/playzongx/core/DurationParser.kt`
- Create: `app/src/main/java/com/playzongx/domain/Models.kt`
- Test: `app/src/test/java/com/playzongx/core/DurationParserTest.kt`

**Interfaces:**
- Produces:
  - `fun parseDurationText(text: String?): Long?` — `"3:45"` → `225_000`, `"1:02:03"` → `3_723_000`, malformed/null → `null`.
  - `fun formatDuration(ms: Long?): String` — `null` → `"--:--"`, `225_000` → `"3:45"`, `3_723_000` → `"1:02:03"`.
  - `data class Track(val id: String, val title: String, val artists: String, val album: String?, val durationMs: Long?, val thumbnailUrl: String?)`
  - `data class AlbumSummary(val id: String, val title: String, val artist: String?, val thumbnailUrl: String?)`
  - `data class ArtistSummary(val id: String, val name: String, val thumbnailUrl: String?)`
  - `data class PlaylistSummary(val id: String, val title: String, val author: String?, val trackCount: Int?, val thumbnailUrl: String?)`
  - `data class SearchResult(val tracks: List<Track> = emptyList(), val albums: List<AlbumSummary> = emptyList(), val artists: List<ArtistSummary> = emptyList(), val playlists: List<PlaylistSummary> = emptyList())`

- [ ] **Step 1: Write the failing test**

```kotlin
class DurationParserTest {
    @Test fun `parses minutes and seconds`() = assertEquals(225_000L, parseDurationText("3:45"))
    @Test fun `parses hours, minutes, seconds`() = assertEquals(3_723_000L, parseDurationText("1:02:03"))
    @Test fun `rejects null and malformed values`() {
        assertNull(parseDurationText(null)); assertNull(parseDurationText("abc"))
        assertNull(parseDurationText("3:75")); assertNull(parseDurationText(""))
    }
    @Test fun `formats millis back to display text`() {
        assertEquals("--:--", formatDuration(null))
        assertEquals("3:45", formatDuration(225_000L))
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }
}
```

- [ ] **Step 2: Run it and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.core.DurationParserTest`
Expected: FAIL — unresolved reference `parseDurationText`.

- [ ] **Step 3: Implement `parseDurationText` and the domain models**

Parse `:`-separated numeric parts; reject a seconds part ≥ 60; support 2 or 3 parts only.

- [ ] **Step 4: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.core.DurationParserTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/playzongx/core app/src/main/java/com/playzongx/domain app/src/test/java/com/playzongx/core
git commit -m "feat: add domain models and duration parser"
```

---

### Task 3: InnerTube search client and tolerant mapper

**Files:**
- Create: `app/src/main/java/com/playzongx/data/network/MusicError.kt`
- Create: `app/src/main/java/com/playzongx/data/network/InnerTubeClient.kt`
- Create: `app/src/main/java/com/playzongx/data/network/SearchMapper.kt`
- Test: `app/src/test/java/com/playzongx/data/network/SearchMapperTest.kt`
- Test: `app/src/test/java/com/playzongx/data/network/InnerTubeClientTest.kt`
- Create: `app/src/test/resources/fixtures/search_mixed.json`, `search_partial.json`, `search_playlist_vl.json`

**Interfaces:**
- Consumes: `SearchResult`, `Track`, `AlbumSummary`, `ArtistSummary`, `PlaylistSummary`, `parseDurationText`.
- Produces:
  - `sealed class MusicError(message: String, cause: Throwable? = null) : Exception` with `class Network(cause)`, `class Parse(cause)`, `class Unavailable`.
  - `class InnerTubeClient(private val http: OkHttpClient, private val endpoint: String = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false") { suspend fun search(query: String): SearchResult }` — throws `MusicError` on failure.
  - `object SearchMapper { fun parse(root: JsonObject): SearchResult }`.

- [ ] **Step 1: Write the fixture files**

`search_mixed.json` — a minimal but realistic response: `contents.sectionListRenderer.contents[]` containing one `musicShelfRenderer` whose `contents[]` has a song renderer and a playlist renderer with browseId starting `VL`, plus a second shelf with a `musicCardShelfRenderer` for an album (browseId starting `MPRE`). Include one item with no duration and one item with a duplicate videoId.

`search_partial.json` — renderers where `flexColumns` is missing and `thumbnail` is absent (must be skipped, not crash).

`search_playlist_vl.json` — one playlist row where `browseId` is `VLPL123` and `navigationEndpoint.browseEndpoint.browseId` is `VLPL123`; `flexColumns[1]` text runs are `["Lo-fi Girl", " • ", "120 songs"]`.

Fixture row shape (song):
```json
{"musicResponsiveListItemRenderer":{
  "flexColumns":[
    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song One"}]}}},
    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist A"},{"text":" • "},{"text":"Album X"}]}}},
    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"3:45"}]}}}
  ],
  "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img.test/s.jpg","width":60,"height":60}]}}},
  "playlistItemData":{"videoId":"vid1"}
}}
```

- [ ] **Step 2: Write the failing mapper tests**

```kotlin
class SearchMapperTest {
    @Test fun `maps song rows with duration and thumbnail`() { /* load search_mixed.json -> exactly 1 track "vid1", title "Song One", artists contains "Artist A", durationMs 225000 */ }
    @Test fun `maps playlist rows by VL browseId`() { /* search_playlist_vl.json -> 1 playlist id VLPL123, title, trackCount 120 */ }
    @Test fun `maps album card shelf`() { /* musicCardShelfRenderer with MPRE browseId -> 1 album */ }
    @Test fun `skips malformed rows without crashing`() { /* search_partial.json -> empty result, no exception */ }
    @Test fun `deduplicates repeated videoIds`() { /* mixed fixture -> tracks.size == 1 */ }
}
```

- [ ] **Step 3: Run and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.data.network.SearchMapperTest`
Expected: FAIL — unresolved `SearchMapper`.

- [ ] **Step 4: Implement `SearchMapper`**

Walk `contents.sectionListRenderer.contents[].musicShelfRenderer.contents[]` and `musicCardShelfRenderer`; accept `musicResponsiveListItemRenderer` and `musicTwoRowItemRenderer`. Helpers: `JsonObject.runsText(index)` (joins `text.runs[].text`), `JsonObject.videoId()` (`playlistItemData.videoId` or `navigationEndpoint.watchEndpoint.videoId`), `JsonObject.browseId()` (checks `navigationEndpoint.browseEndpoint.browseId`, `onTap.navigationEndpoint.browseEndpoint.browseId`, then `browseEndpoint.browseId`), `JsonObject.bestThumbnail()` (last thumbnail URL), `JsonObject.durationFromFlexColumns()`. Classify by browseId prefix: `VL` → playlist, `MPRE` → album, `UC`/`MPLA` → artist. Card shelves use `header.musicCardShelfRenderer.title.runs` for the title and `header.musicCardShelfRenderer.subtitle.runs` for the artist; two-row items use `title.runs`/`subtitle.runs`. Skip rows with no id or no title. Deduplicate by id within each list.

- [ ] **Step 5: Run the mapper tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.data.network.SearchMapperTest`
Expected: PASS.

- [ ] **Step 6: Write the failing client tests**

```kotlin
class InnerTubeClientTest {
    private val server = MockWebServer()
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()
    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()
    @Test fun `posts query to search endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("search_mixed.json")))
        val client = InnerTubeClient(OkHttpClient(), server.url("/youtubei/v1/search?prettyPrint=false").toString())
        val result = client.search("lofi")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"query\":\"lofi\""))
        assertEquals(1, result.tracks.size)
    }
    @Test fun `sends thai query as utf8`() = runBlocking { /* search("เพลงไทย") -> body contains "เพลงไทย" */ }
    @Test fun `http 500 becomes MusicError Network`() = runBlocking { /* enqueue 500 -> assertThrows<MusicError.Network> */ }
    @Test fun `invalid json becomes MusicError Parse`() = runBlocking { /* body "{oops" */ }
    @Test fun `empty results map to empty SearchResult`() = runBlocking { /* body {"contents":{}} -> all lists empty */ }
}
```

- [ ] **Step 7: Run and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.data.network.InnerTubeClientTest`
Expected: FAIL — unresolved `InnerTubeClient`.

- [ ] **Step 8: Implement `InnerTubeClient`**

Build the body with `kotlinx.serialization.json.buildJsonObject`:
`context.client = {clientName: "WEB_REMIX", clientVersion: "1.20250101.00.00", hl: "th", gl: "TH"}`, `query`. Headers: `Content-Type: application/json`, `User-Agent` desktop Chrome UA, `X-YouTube-Client-Name: 67`, `X-YouTube-Client-Version: 1.20250101.00.00`. Wrap `http.newCall(...).execute()` in `withContext(Dispatchers.IO)`; map non-2xx to `MusicError.Network`, JSON errors to `MusicError.Parse`, and call `SearchMapper.parse`.

- [ ] **Step 9: Run all network tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.data.network.*`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/playzongx/data/network app/src/test
git commit -m "feat: add InnerTube search client and tolerant mapper"
```

---

### Task 4: Repository and SearchViewModel

**Files:**
- Create: `app/src/main/java/com/playzongx/data/repo/MusicRepository.kt`
- Create: `app/src/main/java/com/playzongx/data/repo/InnerTubeMusicRepository.kt`
- Create: `app/src/main/java/com/playzongx/ui/search/SearchViewModel.kt`
- Test: `app/src/test/java/com/playzongx/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `InnerTubeClient`, `SearchResult`, `MusicError`.
- Produces:
  - `interface MusicRepository { suspend fun search(query: String): Result<SearchResult> }`
  - `class InnerTubeMusicRepository(private val client: InnerTubeClient) : MusicRepository` (wraps exceptions in `Result.failure`, rethrows cancellation).
  - `sealed interface SearchUiState { data object Idle; data object Loading; data class Success(val result: SearchResult); data class Error(val message: String); data object Empty }`
  - `class SearchViewModel(private val repository: MusicRepository) : ViewModel() { val state: StateFlow<SearchUiState>; fun search(query: String) }` — blank query → `Idle`; all-empty result → `Empty`; exceptions → `Error` with `MusicError` message.

- [ ] **Step 1: Write the failing ViewModel tests**

```kotlin
class SearchViewModelTest {
    @Test fun `blank query stays idle`() = runTest { /* vm.search("  "); assert state.value == Idle; verify repo not called */ }
    @Test fun `success emits result`() = runTest { /* fake repo returns one track; vm.search("a"); advanceUntilIdle(); assert Success with 1 track */ }
    @Test fun `empty result emits Empty`() = runTest { /* repo returns SearchResult(); assert Empty */ }
    @Test fun `failure emits Error message`() = runTest { /* repo returns Result.failure(MusicError.Network(...)); assert Error and message non-blank */ }
    @Test fun `second search replaces first`() = runTest { /* two calls; final state matches the second result */ }
}
```
Use `Dispatchers.setMain(StandardTestDispatcher())` and a `FakeMusicRepository` with a suspend lambda.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.ui.search.SearchViewModelTest`
Expected: FAIL — unresolved `SearchViewModel`.

- [ ] **Step 3: Implement repository and ViewModel**

Repository: `runCatching { client.search(query) }` with `CancellationException` rethrown. ViewModel: `MutableStateFlow`, `viewModelScope.launch`, trim query, ignore stale results by tracking the latest query in a `var`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.ui.search.SearchViewModelTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/playzongx/data/repo app/src/main/java/com/playzongx/ui/search app/src/test/java/com/playzongx/ui
git commit -m "feat: add music repository and search view model"
```

---

### Task 5: Search screen and app container

**Files:**
- Create: `app/src/main/java/com/playzongx/app/AppContainer.kt`
- Create: `app/src/main/java/com/playzongx/PlayZongXApp.kt`
- Create: `app/src/main/java/com/playzongx/ui/App.kt`
- Create: `app/src/main/java/com/playzongx/ui/search/SearchScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:name=".PlayZongXApp"`)
- Modify: `app/src/main/java/com/playzongx/MainActivity.kt` (host `App()`)

**Interfaces:**
- Consumes: `InnerTubeClient`, `InnerTubeMusicRepository`, `SearchViewModel`, `SearchUiState`.
- Produces:
  - `class AppContainer(context: Context) { val httpClient: OkHttpClient; val musicRepository: MusicRepository; val streamResolver: StreamResolver }` (stream resolver added in Task 6).
  - `@Composable fun App(container: AppContainer, onTrackClick: (Track) -> Unit = {})`.
  - `SearchScreen(state: SearchUiState, onSearch: (String) -> Unit, onRetry: () -> Unit, onTrackClick: (Track) -> Unit)`.

- [ ] **Step 1: Implement `AppContainer` and `PlayZongXApp`**

`PlayZongXApp` holds `val container by lazy { AppContainer(this) }`; register in manifest.

- [ ] **Step 2: Build the search UI**

`SearchScreen`: `OutlinedTextField` with search icon and IME action Search; `LazyColumn` of `Track` rows (`AsyncImage` from Coil, title, artists, duration via `formatDuration`), `CircularProgressIndicator` for Loading, actionable error card with Retry for Error, "No results" for Empty, and a hint for Idle. Use `key = { it.id }`.

- [ ] **Step 3: Wire `App` and `MainActivity`**

`App` uses `viewModel(factory = ...)` to create `SearchViewModel(container.musicRepository)`, collects state with `collectAsStateWithLifecycle()`, and renders `SearchScreen`.

- [ ] **Step 4: Verify build and tests**

Run: `./gradlew test assembleDebug`
Expected: PASS / `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual check (owner, optional device/emulator)**

Install `app-debug.apk`, open the app, search "lofi", confirm results list and thumbnails render; enable airplane mode and search again to confirm the Error state and Retry button.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/playzongx/app app/src/main/java/com/playzongx/PlayZongXApp.kt app/src/main/java/com/playzongx/ui app/src/main/AndroidManifest.xml app/src/main/java/com/playzongx/MainActivity.kt
git commit -m "feat: add search screen and app container"
```

---

### Task 6: Audio stream resolution (NewPipeExtractor)

**Files:**
- Create: `app/src/main/java/com/playzongx/player/StreamResolver.kt`
- Create: `app/src/main/java/com/playzongx/player/AudioStreamSelector.kt`
- Create: `app/src/main/java/com/playzongx/data/network/NewPipeDownloader.kt`
- Create: `app/src/main/java/com/playzongx/data/network/NewPipeStreamResolver.kt`
- Modify: `app/src/main/java/com/playzongx/app/AppContainer.kt`
- Modify: `app/src/main/java/com/playzongx/ui/search/SearchScreen.kt`, `ui/App.kt` (tap → resolve → show result)
- Test: `app/src/test/java/com/playzongx/player/AudioStreamSelectorTest.kt`

**Interfaces:**
- Consumes: `MusicError`, `AppContainer`.
- Produces:
  - `data class AudioCandidate(val url: String, val mimeType: String?, val bitrateKbps: Int?)`
  - `data class ResolvedStream(val url: String, val mimeType: String, val bitrateKbps: Int?, val itag: Int?)`
  - `fun selectBestAudioStream(candidates: List<AudioCandidate>): AudioCandidate?` — prefer Opus/WebM by highest bitrate; else best `audio/mp4`/`m4a`; else `null`.
  - `interface StreamResolver { suspend fun resolveAudio(videoId: String): Result<ResolvedStream> }`
  - `class NewPipeStreamResolver(private val service: StreamingService = ServiceList.YouTube) : StreamResolver`
  - `class NewPipeDownloader(private val http: OkHttpClient) : Downloader()` — `execute(Request)` maps to OkHttp and returns `Response`.
  - `AppContainer` gains `val streamResolver: StreamResolver` after calling `NewPipe.init(NewPipeDownloader(httpClient))`.

- [ ] **Step 1: Write the failing selector tests**

```kotlin
class AudioStreamSelectorTest {
    @Test fun `prefers highest bitrate opus`() { /* two opus + one m4a -> picks opus 160 */ }
    @Test fun `falls back to m4a when no opus`() { /* only audio/mp4 -> picks it */ }
    @Test fun `returns null when no audio candidates`() { assertNull(selectBestAudioStream(emptyList())) }
    @Test fun `ignores candidates with blank url`() { /* one blank + one valid -> valid; all blank -> null */ }
    @Test fun `treats unknown bitrate as zero`() { /* opus(null) vs opus(128) -> 128 */ }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.player.AudioStreamSelectorTest`
Expected: FAIL — unresolved `selectBestAudioStream`.

- [ ] **Step 3: Implement the selector**

Filter blank URLs; classify `isOpus = mimeType?.contains("opus", true) == true || mimeType?.contains("webm", true) == true`; `isMp4 = mimeType?.contains("mp4", true) == true || mimeType?.contains("m4a", true) == true`; `maxByOrNull { bitrateKbps ?: 0 }` within the preferred group.

- [ ] **Step 4: Run the selector tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.playzongx.player.AudioStreamSelectorTest`
Expected: PASS.

- [ ] **Step 5: Implement the NewPipe downloader and resolver**

`NewPipeDownloader.execute(request: Request): Response` reads method, url, headers, and body; executes with OkHttp; returns `Response(code, message, responseHeaders, responseBody, request.url())`. `NewPipeStreamResolver.resolveAudio` runs on `Dispatchers.IO`: `service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")`, `fetchPage()`, map `audioStreams` to `AudioCandidate(it.content, it.format?.mimeType, it.averageBitrate)`, select, and throw `MusicError.Unavailable` when none. Map extractor exceptions to `MusicError.Unavailable`/`Network`.

- [ ] **Step 6: Wire tap-to-resolve**

`AppContainer` exposes `streamResolver`; `App` builds a coroutine scope and shows a `ModalBottomSheet` with "Resolving…", then "Ready (opus, 160 kbps)" or the error message plus Retry. Do not display the raw URL.

- [ ] **Step 7: Verify build and tests**

Run: `./gradlew test assembleDebug`
Expected: PASS / `BUILD SUCCESSFUL`.

- [ ] **Step 8: Manual check (owner)**

Tap a result on a real device; confirm a resolved audio stream is reported. Then test with a video that has no audio (or airplane mode) and confirm the clear error message.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/playzongx/player app/src/main/java/com/playzongx/data/network app/src/main/java/com/playzongx/app app/src/main/java/com/playzongx/ui
git commit -m "feat: resolve audio streams with NewPipeExtractor"
```

---

### Task 7: CI build and phase verification

**Files:**
- Create: `.github/workflows/android.yml`

**Interfaces:**
- Produces: a GitHub Actions workflow that builds `app-debug.apk` and uploads it as an artifact.

- [ ] **Step 1: Write the workflow**

```yaml
name: Android CI
on:
  push:
    branches: [ main ]
  pull_request:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew test assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: playzongx-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Verify locally**

Run: `./gradlew clean test assembleDebug`
Expected: `BUILD SUCCESSFUL`, all unit tests pass, APK exists.

- [ ] **Step 3: Commit and push (owner)**

```bash
git add .github/workflows/android.yml
git commit -m "ci: build debug APK on GitHub Actions"
# owner creates the GitHub repo and pushes; confirm the workflow produces the artifact
```

- [ ] **Step 4: Phase 1 acceptance check**

- App installs and launches on the owner's device.
- A search returns YT Music results with thumbnails.
- Airplane mode yields the retryable error state.
- Tapping a result resolves an audio stream (or shows a clear error).
- `./gradlew test assembleDebug` is green; CI uploads the APK.

---

## Self-Review

**Spec coverage (Phase 1):** toolchain/build (spec §8), InnerTube search client (§7.2), stream resolution via NewPipeExtractor (§7.3), typed errors and offline behavior (§7.1/§7.8), manual device verification (§9/§5), CI APK (§8). Later phases cover Room, downloads, playback, Drive sync, backup, sleep timer/equalizer, and UI polish — each gets its own plan after Phase 1 is proven.

**Step scan:** every step names a file, command, or exact assertion; no "handle edge cases" placeholders.

**Type consistency:** `SearchResult`/`Track` flow unchanged from Task 2 → 6; `Result<T>` is used for repository and resolver boundaries; `MusicError` subtypes referenced consistently.

**Review Focus tests:** missing fields → `skips malformed rows`; offline/5xx → `http 500` + manual check; Thai query → `sends thai query as utf8`; no Opus → `falls back to m4a` + `returns null`; duplicates → `deduplicates repeated videoIds`.

**Proportion:** plan is longer than the spec's Phase 1 slice because it pins versions, file layout, and test assertions; implementation bodies are left to the implementer.
