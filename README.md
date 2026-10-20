# ProGallery — Android Gallery App

A native Android gallery: a fast, Material 3 photo and video browser with **deep scanning** of user-authorised storage and a working **photo and video editor**. Built with Kotlin, Jetpack Compose and a clean MVVM + repository architecture.

> **Status:** source-complete and structured to build, but the APK in this repository has **not been compiled by the author** — the build environment used to write it had no Android SDK. See [Build & verify](#build--verify) for the exact commands to compile and test it. Everything below labelled *Implemented* is real code, not a stub; nothing is labelled working unless the logic is complete.

---

## Features

### Browse
- Material 3 UI with light/dark themes and dynamic colour on Android 12+.
- Home dashboard: Photos, Videos, Albums, Favourites, Recently Deleted, Deep Scan, Search.
- Grid **and** list view; adjustable grid density.
- Sorting by date, name, size and media type.
- Search by file name, album/folder name and MIME type.
- Multi-select with a contextual action bar (share, favourite, hide, edit, delete).
- Full-screen viewer with pinch-to-zoom and swipe paging.
- Full-screen video player (Media3 ExoPlayer).
- Empty states, loading indicators, error handling, and a permission gate.

### Deep scanning
- Standard indexing via **MediaStore** (images and videos, all decodable formats).
- A separate **Deep Scan** that walks directories the user grants through the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), including nested folders, to find supported files the system index may have missed.
- **Duplicate detection**: candidates are narrowed by exact file size, then confirmed with a streamed SHA-256 hash. Reports reclaimable bytes.
- Live progress (folders, files, media found, current path), errors, and a working cancel button.
- Persisted directory grants so access survives process death.

### Edit
- **Photo editor:** crop (aspect presets), rotate, flip, brightness, contrast, saturation, warmth, exposure, grayscale, sepia, blur, sharpen. Undo/redo, before/after toggle, non-destructive edits, EXIF-orientation handling, and export as a new JPEG/PNG/WebP (or explicit overwrite) with quality selection.
- **Video editor:** trim (range slider), mute, rotate and resolution presets, exported through **Media3 Transformer** to an H.264 MP4 in `Movies/ProGallery`, with progress and cancellation.

### Storage & privacy
- Respects scoped storage. Requests `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on Android 13+, handles Android 14+ partial ("selected photos") access, and falls back to `READ_EXTERNAL_STORAGE` on older versions.
- Uses content URIs only — never exposes raw filesystem paths to other apps (sharing goes through the Sharesheet with `FLAG_GRANT_READ_URI_PERMISSION`).
- Operates entirely on-device. No account, no uploads, no analytics.

---

## Screenshots

_Add screenshots here after your first local build_ (e.g. `docs/screenshots/home.png`). The repository intentionally ships without placeholder images.

---

## Requirements

- **Android Studio** Ladybug (2024.2) or newer, or a command-line Android SDK.
- **JDK 17**.
- **Android SDK**: compileSdk 35, targetSdk 35, **minSdk 26** (Android 8.0).
- Gradle 8.11.1 (via the included wrapper).

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + repository, Kotlin Flow |
| DI | Hilt |
| Local DB | Room |
| Media index | MediaStore |
| Deep scan | Storage Access Framework (`DocumentFile`) |
| Image loading | Coil (with video frame decoding) |
| Playback | AndroidX Media3 ExoPlayer |
| Video editing | AndroidX Media3 Transformer |
| Paging / async | Coroutines + Flow (Paging 3 available) |
| Build | Gradle Kotlin DSL + version catalog |

---

## Setup

```bash
git clone https://github.com/merry8989-uk/pro-gallery-android.git
cd pro-gallery-android
```

Open the folder in Android Studio and let it sync, **or** build from the command line (see below). If you build from the command line, make sure `local.properties` points at your SDK — Android Studio creates this automatically:

```properties
sdk.dir=/path/to/Android/sdk
```

`local.properties` is git-ignored and must never be committed.

## Build & verify

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew testDebugUnitTest

# Instrumented tests (needs a connected device/emulator)
./gradlew connectedDebugAndroidTest

# Release build (see below for signing)
./gradlew assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### Release build

Create a keystore and a `keystore.properties` (both git-ignored), then wire signing into `app/build.gradle.kts`:

```properties
storeFile=/absolute/path/release.jks
storePassword=********
keyAlias=progallery
keyPassword=********
```

```bash
keytool -genkeypair -v -keystore release.jks -alias progallery \
  -keyalg RSA -keysize 2048 -validity 10000
./gradlew bundleRelease   # Play-ready AAB
```

---

## Permissions

| Permission | When | Why |
| --- | --- | --- |
| `READ_MEDIA_IMAGES` | Android 13+ | Read photos. |
| `READ_MEDIA_VIDEO` | Android 13+ | Read videos. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ | Partial access to selected items. |
| `READ_EXTERNAL_STORAGE` | Android 12 and below (maxSdk 32) | Legacy media read. |
| `WRITE_EXTERNAL_STORAGE` | Android 9 and below (maxSdk 28) | Legacy export on old versions. |
| `ACCESS_MEDIA_LOCATION` | All | Read EXIF location when present. |

### What the app can and cannot access

- MediaStore exposes the media the user has granted. The app does not and cannot index the whole filesystem on modern Android.
- `Android/data` and `Android/obb` are frequently restricted on Android 11+ and may be inaccessible regardless of user action. The app reports these gracefully.
- Deep Scan only touches directories the user picks in the system file picker. No root, no undocumented APIs, no bypassing scoped storage.

---

## Architecture

```
app/src/main/java/com/progallery/app/
├── data/
│   ├── local/        Room database, entities, DAOs
│   ├── mediastore/   MediaStoreDataSource — the fast index
│   ├── deepscan/     DeepScanRepository, FileHasher, DuplicateDetector
│   ├── model/        MediaItem, Album, ScanSummary, …
│   └── repository/   MediaRepository, TrashRepository
├── domain/           Pure, testable filtering + sorting
├── edit/
│   ├── photo/        PhotoEditState, BitmapEditor, PhotoExporter
│   └── video/        VideoEditState, VideoTrimmer (Media3 Transformer)
├── ui/
│   ├── components/   Thumbnails, grid/list, selection bar, permission gate
│   ├── navigation/   Compose Navigation graph
│   ├── screens/      home, photos, albums, videos, favourites, trash,
│   │                 deepscan, viewer, player, editor/photo, editor/video, search
│   └── theme/        Material 3 theme
└── util/             Permissions, formatting, media actions, file helpers
```

The UI never touches MediaStore directly: screens talk to a ViewModel, ViewModels talk to repositories, and repositories own the platform APIs. Filtering and sorting live in `domain/` with no Android imports, which is what makes them unit-testable.

---

## Feature status

**Implemented**
- MediaStore indexing, albums, counts, search, sort, grid/list, adjustable density.
- Multi-select actions: share, favourite, hide, delete (with Android 11+ consent flow).
- Full-screen viewer with zoom/paging; ExoPlayer video player.
- Deep Scan with SAF directory grants, nested traversal, duplicate detection, progress, cancel.
- Photo editor (full pipeline above) with undo/redo and export.
- Video editor: trim, mute, rotate, resolution, Media3 export with progress/cancel.
- Room persistence for favourites, custom albums, album covers, hidden items, trash, scan dirs.
- App-managed Recently Deleted trash with restore and 30-day purge.

**Partially supported**
- Photo **crop** uses centred aspect presets rather than a free-form draggable crop box.
- Video rotation/resolution use Media3 effects that some hardware encoders may not honour for every codec.
- Custom albums have DB support and DAOs; the create/manage UI is not yet wired to a screen.

**Planned**
- Free-form crop UI, curves/levels, text and sticker overlays.
- Video merging, background music, text overlays (only when the real pipeline is implemented — no fake buttons).
- Paging 3 integration for very large libraries (currently loads the index in memory).
- Hidden-album privacy model with biometric gate.
- Backup/restore of app-side metadata.

---

## Known limitations

- **Not compiled here.** The authoring environment had no Android SDK, so `assembleDebug` and the test suites were not run against this source. Treat the first local build as the real verification step.
- `Android/data` and `Android/obb` are often inaccessible on Android 11+ by design.
- HEIC/HEVC support depends on the device decoder.
- Trash moves a **copy** of the bytes into app storage and then removes the original through the platform consent flow; it is not a filesystem-level move, because that is not possible under scoped storage.

## License

MIT — see [LICENSE](LICENSE).
