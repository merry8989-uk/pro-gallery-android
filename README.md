# ProGallery — Android Gallery App

A native Android gallery: a fast, Material 3 photo and video browser with **deep scanning** of user-authorised storage and a working **photo and video editor**. Built with Kotlin, Jetpack Compose and a clean MVVM + repository architecture.

> **Status:** builds green on GitHub Actions. The `Android CI` workflow runs the JVM unit tests and `assembleDebug`; the `Release APK` workflow builds the debug APK and attaches it to [Releases](../../releases). Download the latest APK from the Releases page, or see [Build & verify](#build--verify) to compile it yourself. Everything below labelled *Implemented* is real code, not a stub.

---

## Features

### Browse
- Material 3 UI with light/dark themes and dynamic colour on Android 12+.
- Home dashboard: All files, Photos, Videos, Audio, Documents, Albums, Favourites, Recently Deleted, Deep Scan, Search.
- Grid **and** list view; adjustable grid density.
- Sorting by date, name, size and media type.
- Search by file name, album/folder name and MIME type.
- Bulk selection: **Select all** from the top bar (no long-press needed) or long-press any item, then use the labelled action bar (share, edit, favourite, add-to-album, hide, delete) which clears the system navigation bar.
- **Immersive full-screen viewer and player**: system bars hidden, black background, swipe-to-reveal so you are never trapped, and bars restored on exit.
- **Viewer gestures**: tap to show/hide the controls, double-tap to zoom, pinch to zoom, and drag to pan *only while zoomed* so a swipe still changes page at natural size. Leaving a page resets it to fit.
- **Viewer actions** (revealed by tapping): share, edit, rename, move, delete and info, plus favourite. Videos show a play button that opens the player.
- Media extends into display cutouts on notched devices.
- Empty states, loading indicators, error handling, and a permission gate.

### Deep scanning
- Standard indexing via **MediaStore** across images, videos and audio, classified by a file-type registry.
- A separate **Deep Scan** that walks directories the user grants through the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), including nested folders, indexing **every file type** — documents, archives, raw files and unknown formats, not just media.
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

## File size range

Every file is shown from **1 byte upward, with no upper limit** — APKs, images,
videos, documents, archives, unknown types. Size never hides a file.

- The displayed size floors at **1 B**, so a real file never reads "0 B"; a size
  the provider did not report shows as **Unknown**.
- A size that cannot be read is never treated as "too small" — SAF reports `0`
  for unknown as well as for empty, so filtering on that would drop real files.
- If you want to narrow it, **Settings → Scan limits** (Advanced) offers a
  minimum of 1 B / 1 KB / 100 KB / 1 MB and a maximum of Unlimited / 100 MB /
  1 GB / 10 GB. The default is 1 B – unlimited.

## Settings

Open Settings from the home screen's top bar.

- **Basic** shows the options most people want: which scan methods run, which file
  types to include, thumbnails, appearance, and a shortcut to set the app as the
  default gallery.
- **Advanced mode** (a switch at the top) reveals detection options, scan limits,
  performance controls and diagnostics.

Every switch changes real behaviour — there are no decorative toggles. A few
examples: turning **Videos** off stops video discovery *and* video thumbnail
generation; turning **Thumbnails** off shows file-type icons instead of blank
tiles; turning **Duplicate detection** off skips the hashing pass entirely;
turning **Deep scan** off makes the scan screen explain why it cannot start. The
live summary at the top of Settings states exactly what a scan would look at, and
warns when the current combination would scan nothing.

Settings are stored with DataStore, survive restarts, and every numeric value is
clamped to a safe range so a bad value can never hang the scanner.

**Root recovery is not implemented.** The app can report whether a device looks
rooted (a read-only `su` check) but does not read raw storage — that bypasses
Android's security model and cannot honestly promise recovery of TRIMmed or
encrypted data.

## Testing

- **Unit tests** cover the pure logic: sort/search mapping, confidence and status
  rules, duplicate detection, undo checkpoints, thumbnail heuristics, the migration
  chain and the SQL-versus-entity column check.
- **Instrumented tests run on an emulator in CI**, which is where the migration is
  executed against real SQLite and the surviving rows are asserted.
- `tools/check_kotlin.py` catches the structural mistakes that have actually reached
  CI here — unbalanced braces, missing icon imports, duplicate imports, an import
  after code, a second `companion object`, and a project type used without an import.

## Settings

Every switch has to do something. Changes are resolved through a dependency registry
(`SettingsDependencies`), so a setting whose prerequisite is off is switched off with it
rather than left claiming to be active, and the screen explains what changed. Settings
can be exported and imported as versioned JSON that holds **only your choices** — no
paths, no URIs, no grants — and each section can be reset on its own.

## Access

Settings → Diagnostics & experimental → **Access diagnostics** explains every access the
app uses: what state it is in, what it enables, what being without it costs, and what to
do about it. Optional accesses say they are optional and name the alternative, and every
fix uses the official route — the system dialog, the system settings screen, or the
system folder picker.

## Platform

- **Android 8.0 – 16.** `minSdk 26`, `compileSdk`/`targetSdk` **36**.
- **The index stays current** while the app runs: a `MediaStoreObserver` follows the system's
  media index (debounced), and screens reload quietly on resume.
- **The library loads incrementally** — the cursor is read in batches (default 500, configurable)
  and published after each one, so the grid paints early on a large library.
- The release workflow builds a **signed Play-ready AAB** as well as the debug APK. The AAB is
  built and signed in CI but has not been run on a device.

## Recovery

The **Recovery** screen runs a Quick Scan and shows what the app can actually
reach, with the source on every result:

| Source | What it means |
| --- | --- |
| Recently Deleted | Bytes this app is holding — genuinely recoverable. |
| Granted folder | Found by walking a folder you allowed; the system's index does not list it. |
| System index | Already indexed by Android. Shown as indexed, **not** as a deleted file. |
| Thumbnail / cache | A preview artefact — never the original. Exportable, but named `...-thumbnail`. |

Two scan modes: **Quick Scan** (everything the app can reach) and **Thumbnail &
cache** (only thumbnails and cached previews in folders you granted — small
preview files, never the original, exported under a `-thumbnail` name).

Each result shows its **confidence** (High only when the bytes identified the
file), the **evidence** for that confidence, and its **status** (Available /
Preview only / Exported / Failed). Export writes through the Storage Access
Framework, verifies the copy against the source size before reporting success,
never overwrites, and cleans up a partial file on failure. Every attempt is
recorded in the export history.

## Thumbnail cache

The app's own preview cache has a configurable limit (32–1024 MB, applied at the
next launch, since Coil builds its cache once) and a **Clear** action that reports
how much it freed. Clearing it only empties generated previews — it can never
touch your photos, videos, or anything you recovered.

## Android folder access — what is actually possible

There is a **Android folders** screen for the restricted `Android/` tree. It is
honest about the platform rules rather than offering buttons that cannot work:

| Location | Non-rooted access |
| --- | --- |
| `Android/media` | **Readable** — shared media written by other apps (WhatsApp, Telegram…). Needs All files access on Android 11+. |
| `Android/data/<this app>` | **Always readable** — it is our own folder. |
| `Android/data/<other apps>` | **Blocked from Android 11.** Refused to every non-rooted app, including with All files access and the folder picker. Readable on Android 10 and below. |
| `Android/obb` | **Blocked from Android 11.** Readable on Android 10 and below. |

This is enforced by Android, not by this app: no permission grants it, and only
root bypasses it — which this app deliberately does not use. Anything that *is*
reachable can be scanned, previewed, opened and shared like any other file.

## Getting files out of `Android/data` (no root, no bypass)

No app can read another app's `Android/data` on Android 11+ — that is the OS
rule, and it holds with "All files access" and the folder picker too. A computer
with **ADB** can, because the `adb` shell runs as the `shell` user with broader
rights. `tools/` has ready-made scripts:

```bash
# macOS / Linux
./tools/adb_export_android_data.sh                 # the whole Android/data
./tools/adb_export_android_data.sh com.whatsapp    # one app's folder
```
```powershell
# Windows
.\tools\adb_export_android_data.ps1
.\tools\adb_export_android_data.ps1 -Package com.whatsapp
```

They copy the folder to your computer, then push it back into `Download/` so this
app can Deep Scan it. This is the documented developer interface — nothing is
exploited, and the phone is only read, never modified.

Worth checking first: messaging apps (WhatsApp, Telegram) keep their media in
**`Android/media`**, which the app *can* read with All files access — so it may
already be in the gallery.

## Default gallery

On first launch the app offers to become your default gallery. Android only lets
the *user* choose a default, so the app declares itself as a candidate
(`APP_GALLERY`, plus viewer filters for images, videos and audio) and the prompt
opens the system screen where you make the choice. You can revisit it from
Settings → Default app.

## Supported file types

The scanner indexes every kind of file it can reach, not just photos and videos.
Classification lives in `FileTypeRegistry` (pure Kotlin, unit tested) and resolves
by MIME type first, then by extension. Anything unrecognised is browsable as
**Other** rather than being hidden.

| Category | Examples |
| --- | --- |
| **Images** | JPG, JPEG, PNG, APNG, GIF, WebP, AVIF, HEIC/HEIF, JXL, BMP, TIFF, ICO, SVG, TGA, PSD/PSB, AI, EPS, INDD, XCF, Sketch, EXR, HDR, DDS |
| **Camera raw** | RAW, DNG, CR2/CR3/CRW, NEF/NRW, ARW/SRF/SR2, ORF, RAF, RW2, PEF, SRW, X3F, 3FR, MRW, ERF |
| **Video** | MP4, M4V, MOV/QT, MKV, WebM, AVI, DivX, WMV/ASF, FLV/F4V, MPG/MPEG, TS/M2TS/MTS, 3GP/3G2, OGV, RM/RMVB, VOB, MXF, DV |
| **Audio** | MP3, M4A/M4B/M4R, AAC, WAV, FLAC, OGG/Opus, WMA, AMR/AWB, MIDI, AIFF, AC3/EAC3/DTS, APE, CAF, DSF/DFF, MKA |
| **Documents** | PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, ODT/ODS/ODP, Pages/Numbers/Key, RTF, TXT, MD, CSV/TSV, JSON/XML/YAML/TOML, HTML/CSS, source code, EPUB/MOBI/AZW3, DjVu, CBZ/CBR, SRT/VTT/ASS, XPS, DWG/DXF/DWF/DGN |
| **Archives & packages** | ZIP, RAR, 7Z, TAR, GZ, BZ2, XZ, ZSTD, CAB, ARJ, LZH, CPIO, WIM, ISO, IMG, DMG, JAR/WAR, APK/AAB/XAPK, DEB/RPM, MSI, APPX |

### Where each type can be found

Android's storage rules decide how much the app can see, and the app reports
this honestly rather than pretending otherwise:

- **MediaStore (the fast index)** — images, videos and **audio**, on internal and
  removable storage. That needs `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` and
  `READ_MEDIA_AUDIO` on Android 13+, or `READ_EXTERNAL_STORAGE` below that.
- **From Android 11**, MediaStore does not return documents or archives created by
  other apps, regardless of permission. That is a platform limit, not a bug.
- **Deep Scan (SAF)** — point it at a folder and *every* file type inside it,
  including nested folders, is indexed: documents, archives, raw files, unknown
  formats. This is the way to make arbitrary files scannable.

The viewer swipes through **only the tapped item's own kind** — opening a photo never slides into a video. Images and videos get thumbnails and an in-app viewer/editor. Audio plays in the
in-app player. Documents, archives and unknown types show a type icon, and open
with whichever app on the device handles them — tapping the file opens the system
chooser, a single selection offers **Open in** in the action bar, and the PDF
viewer has its own **Open with** action. The chooser is only shown when an
installed app can actually handle the file; otherwise the app says so.

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

### Release build & signing

Signing is already wired up. `app/build.gradle.kts` reads `keystore.properties`
(git-ignored) and, when it is present, signs **both** the debug and release
builds with that key.

CI supplies the key from encrypted repository secrets, so **every build from
this repository is signed with the same certificate** and installs straight over
the previous version — no uninstall needed between updates.

To sign locally, create `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/progallery.p12
storePassword=********
keyAlias=progallery
keyPassword=********
storeType=PKCS12
```

Generate a key if you need one:

```bash
keytool -genkeypair -v -keystore progallery.p12 -storetype PKCS12 \
  -alias progallery -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleDebug      # signed with your key
./gradlew bundleRelease      # Play-ready AAB
```

If `keystore.properties` is absent, Gradle falls back to the default debug key —
convenient, but that key differs per machine and per CI run, so builds signed
with it will not upgrade in place over each other.

**Never commit `keystore.properties` or the keystore.** Both are in `.gitignore`.

---

## Permissions

| Permission | When | Why |
| --- | --- | --- |
| `READ_MEDIA_IMAGES` | Android 13+ | Read photos. |
| `READ_MEDIA_VIDEO` | Android 13+ | Read videos. |
| `READ_MEDIA_AUDIO` | Android 13+ | Read audio. |
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
- Room persistence for favourites, custom albums, album covers, hidden items, trash, scan dirs, all keyed by the item's content URI (never the colliding numeric MediaStore id), with an explicit migration.
- App-managed Recently Deleted trash: deleting copies the bytes first and only then removes the originals through the platform consent flow, with restore and a 30-day purge.
- Custom albums: create a collection, add items from the gallery selection toolbar, remove items, choose a cover, and delete the album without touching the media.

**Partially supported**
- Photo **crop** uses centred aspect presets rather than a free-form draggable crop box.
- Video rotation/resolution use Media3 effects that some hardware encoders may not honour for every codec.

**Planned**
- Free-form crop UI, curves/levels, text and sticker overlays.
- Video merging, background music, text overlays (only when the real pipeline is implemented — no fake buttons).
- Paging 3 integration for very large libraries (currently loads the index in memory).
- Hidden-album privacy model with biometric gate.
- Backup/restore of app-side metadata.

---

## Known limitations

- The published APK is a **debug build**. It is signed with the repository's stable key, so updates install in place; it is still a debug build, so it is for sideloading/testing rather than Play distribution.
- If you have an older build signed with an earlier throwaway key, that one install may need a single uninstall first. Every build after that upgrades in place.
- **Instrumented tests are not run in CI** (they need a connected device or emulator). Run `./gradlew connectedDebugAndroidTest` locally to exercise them.
- The JVM unit tests and `assembleDebug` are verified by CI; runtime behaviour on a physical device (permissions across OEMs, codec support, editing output) still deserves a manual pass.
- `Android/data` and `Android/obb` are often inaccessible on Android 11+ by design.
- HEIC/HEVC support depends on the device decoder.
- Trash moves a **copy** of the bytes into app storage and then removes the original through the platform consent flow; it is not a filesystem-level move, because that is not possible under scoped storage.

## License

MIT — see [LICENSE](LICENSE).
