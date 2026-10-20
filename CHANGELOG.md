# Changelog

All notable changes to ProGallery are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Initial project: Kotlin + Jetpack Compose + Material 3 gallery.
- MediaStore indexing for images and videos, albums, counts, search and sorting.
- Grid and list views with adjustable density.
- Multi-select toolbar: share (Sharesheet), favourite, hide, delete with Android 11+ consent flow.
- Full-screen viewer with pinch-to-zoom and swipe paging; Media3 ExoPlayer video player.
- Immersive full-screen media: system bars hidden with swipe-to-reveal, black background, tap-to-reveal controls, double-tap zoom, and display-cutout support.
- Deep Scan over Storage Access Framework directory grants, with nested traversal, progress, cancel, and SHA-256 duplicate detection.
- Photo editor: crop presets, rotate, flip, brightness, contrast, saturation, warmth, exposure, grayscale, sepia, blur, sharpen; undo/redo; before/after; EXIF orientation; export to JPEG/PNG/WebP.
- Video editor: trim, mute, rotate, resolution presets, Media3 Transformer export with progress and cancellation.
- Room persistence: favourites, custom albums, album covers, hidden items, trash, scan directories.
- App-managed Recently Deleted trash with restore and 30-day purge.
- Custom albums: create, add items from the selection toolbar, remove items, set a cover, delete the album (media untouched).
- Unit tests for sorting, filtering, duplicate detection, edit-state history, and formatting.
- Instrumented tests for a UI component, the permission matrix, and MediaStore access.
- GitHub Actions workflow to run unit tests and build the debug APK.

### Known limitations
- The published APK is a debug build (Android debug key) — sideload/testing only, not Play-ready.
- Instrumented tests are not run in CI; they need a connected device or emulator.
- Free-form crop and Paging 3 for very large libraries are planned.

## [1.9.0]
Paging 3 in the grid, and the migration finally proved on a real device.

### Added
- **The instrumented suite runs in CI on an emulator** (API 35), alongside the unit
  tests and lint. The job needed a retry step: the runner intermittently caches a
  truncated SDK package ("Error on ZipFile unknown archive"), which clearing the SDK
  temp directory and retrying gets past.
- **`MigrationInstrumentedTest`** migrates a real v3 SQLite database holding a row in
  every user-facing table, runs `MIGRATION_3_4`, and asserts that favourites, hidden
  items, custom albums, album covers, album membership, trash and granted folders all
  survive and that `recovery_history` starts empty. A second test proves re-running the
  migration is harmless.
  It builds the v3 database from explicit DDL rather than Room's exported schema,
  because Room only exports the schema of the version being compiled — `3.json` does
  not exist. The exported schemas are now uploaded as a CI artifact so a future
  migration can also validate against Room's expected schema.

### Changed
- **The media grid is paged.** `MediaStorePagingSource` reads the cursor in windows
  and fills each page by continuing until it has enough items, so a page is never
  nearly empty just because a window held other kinds. The sort and the search are
  expressed in SQL (`MediaQuery`, unit tested, with `%`/`_`/`\` escaped and `ESCAPE`
  declared), and all three read paths share one row reader, so a paged list can never
  disagree with the full list about what a file is.
- **The viewer's playlist is now loaded on demand.** It used to reuse the grid's list;
  with paging that list only holds the pages you have scrolled to, so the full ordered
  set is fetched when the viewer is opened. That is the trade paging makes.
- **Selection records the items, not just their keys**, so share, delete, hide and
  "add to album" still work on something selected on a page you have since scrolled
  away from. "Select all" is now **"Select all" of what is loaded**, which is what the
  control can honestly promise.
- **Toggling a favourite no longer resets the grid.** Favourites are applied while
  rendering instead of keying the pager. On the Favourites screen the set *does* key
  the pager, because there the item genuinely has to leave the list.
- Album screens still use the in-memory list: an album is small and its full contents
  are what it shows.

## [1.8.0]
Phase 4: platform and performance.

### Changed
- **Targets Android 16 (API 36).** `compileSdk`/`targetSdk` 35 → 36 with **AGP 8.9.3** (compileSdk 36 needs AGP 8.9+). Play requires API 36 for new apps and updates from 31 August 2026. No orientation lock exists in the manifest, so the API 36 large-screen change does not affect the app.
- **The index is no longer stale.** A `MediaStoreObserver` watches the system's media index while the app runs, so a photo taken in the camera app, a file deleted elsewhere or a rename appears without a manual refresh. Signals are debounced (2 s) and conflated, because one change fires several callbacks. Screens also reload quietly when they come back to the foreground — without a spinner, since nothing was pulled.
- **The library loads incrementally.** Rows are read from the cursor in batches (default 500, configurable under Performance → *Index batch size*) and the index is published after each one, so the grid paints after the first batch instead of waiting for the whole library. This is the "load from the cursor in batches" strategy the audit asked for; see the note on Paging 3 below.
- **A signed release bundle is built for Play.** The release workflow now also runs `bundleRelease` and attaches `pro-gallery-release.aab` alongside the debug APK. **It compiles and signs in CI but has not been run on a device** — R8 has not been exercised at runtime here — and the release notes say so.
- **Android lint runs in CI**, with its report uploaded as an artifact.

### Added
- **"Choose more" for limited photo access.** On Android 14+ a user can share only selected photos. Re-requesting permission was the only option before; the app now also launches the system photo picker (`ACTION_PICK_IMAGES` with a selection limit) so they can widen the selection, which is the documented way to do it.
- **`MigrationCoverageTest`** — a device-free guard on the upgrade path: the `@Database` version must equal the newest migration's target, every version gap must have a migration, steps must be contiguous, and the newest migration's SQL must create exactly the columns its entity declares (checked both ways). This is the check that catches "bumped the version but forgot the migration", which is how a user loses their favourites or albums.
- Migration SQL and the schema version are now constants (`MigrationSql`, `DatabaseVersion`, `MigrationSteps`) so a test can read them.

### Notes on what was deliberately not done
- **Full Paging 3 was not adopted.** The library's sort, search and favourites/hidden decoration are all applied in memory today; moving to `PagingSource` would push sorting and searching into SQL and decorate page by page, which is a rewrite of the core data path that every screen and the viewer's playlist depend on. The audit's own wording — *"load data from the cursor in batches"* — is what was implemented instead: same data model, incremental load, no regression risk to a working app. Paging 3 remains available as a separate, opt-in piece of work.
- **Instrumented tests still do not run in CI** (they need an emulator). `MigrationCoverageTest` covers the migration logic that can be checked without one; the SQL itself is only exercised on a device.

## [1.7.0]
Phase 2B: the rest of the recovery engine.

### Added
- **Thumbnail & cache scan mode.** A second scan that looks only for thumbnails and cached previews in the folders you granted. `ThumbnailHeuristics` decides from the file's name, the folder it sits in, and a size written into its name — and deliberately **not** from small dimensions alone, because a genuinely small photo is a small photo. Every verdict carries its reason.
- **A thumbnail can be exported, but never as the original.** It is a real readable file, so it is copied — under a name like `photo-thumbnail.jpg`, with the dialog stating plainly that it is not the full-resolution image. A preview-only result that could not be read stays unexportable.
- **Thumbnail cache limit and clearing.** The app's own preview cache size is now a setting (32–1024 MB, applied at next launch because Coil builds its cache once), with a **Clear** action that reports how much it freed. It only empties the app's generated previews — never your photos, videos, or anything you recovered.
- **Estimated resolution for thumbnails.** Dimensions are read from the image header only (`inJustDecodeBounds`), so even a 50 MP source is cheap to inspect.

### Changed
- **Duplicate detection is much cheaper.** It used to fully hash every same-size candidate. Now: group by size → compare a sampled hash (length + first and last 64 KB) → fully hash **only** what still collides. Only the full hash can declare a duplicate, so the cheaper passes can never be wrong — they only avoid work. The Deep Scan summary now reports how many full hashes actually ran.
- **Progress is published on a clock as well as a count** (every 25 files *or* every 250 ms). A folder holding a few very large videos used to go a long time with no update, so the screen looked frozen. The Android-folders and recovery scans, which published nothing during their walk, now do.
- **Recovery results are de-duplicated** by canonical document URI, so a file reachable through two overlapping grants is reported once.

### Tests
- `ThumbnailHeuristicsTest` — every signal, and the important negatives: small dimensions alone, unreadable dimensions, and a 1920×1080 wallpaper with a size in its name are **not** thumbnails.
- `DuplicateDetectorTest` — unique sizes are never read at all; same-size files with different samples are not fully hashed; a sample collision with different bytes is **not** a duplicate; identical files are.
- `ScanProgressThrottleTest` — count trigger, clock trigger, reset behaviour, and a long run publishing repeatedly.

## [1.6.0]
Phase 2A: the recovery engine. Real discovery, real previews, and a safe export.

### Added
- **Recovery screen (Quick Scan).** Finds everything this app can actually reach and says where each piece came from:
  1. media the system's index lists,
  2. files this app is holding in **Recently Deleted**,
  3. files inside folders you granted that the index does **not** list.
  An indexed file is never called a deleted file — every result carries its source, and a scan summary states exactly how many were indexed, held, or unindexed, plus how many were skipped because the index already lists them.
- **`RecoveryThumbnail`** — real image thumbnails, video frames, type icons with the file's format, a broken-preview placeholder when decoding fails, and a **THUMBNAIL** / **PREVIEW** marker so an artefact is never mistaken for the original. Lazy and cached, decoded at display size.
- **Result cards** showing thumbnail, name, size, **discovery source**, **confidence** (High/Medium/Low) with the **evidence** behind it, and **recovery status** (Available / Preview only / Exported / Failed).
- **Preview dialog** with full metadata: type, size, dimensions, duration, modified date, source, where it was found, and the confidence reasoning.
- **Safe export** through the Storage Access Framework: you choose the destination, the copy is streamed, written under a temporary name, **verified against the source size**, and only then named. An existing file is never overwritten — a numbered name is used — and a failed or cancelled copy deletes the partial file. The source is never modified or deleted.
- **Export history** in Room (migration 3→4, purely additive) recording name, source, destination label, outcome and timestamp. Only a human-readable destination label is stored — never the SAF tree URI, which carries a permission grant.
- Tabs (All / Photos / Videos / Audio / Documents / Other / Recently Deleted / Thumbnails), search, sort (**Most recoverable** / Newest / Largest / Name), grid and list, bulk selection with Select all, and Share.
- **Confidence is derived, never asserted.** Signature identification is High; an extension on its own is only Medium; a file that cannot be read is Low and **Preview only**.

### Tests
- `RecoveryConfidencePolicyTest` — every confidence/status branch, including "an unreadable file is never offered as available" and "a thumbnail is never available".
- `RecoveryQueryTest` — tab filtering by kind *and* by provenance, search across name/source/evidence, and all four sort orders.

### Notes
- Thumbnail/cache discovery is the one scan mode still to come (Phase 2B), along with faster duplicate detection. The `THUMBNAIL_CACHE` source and the tab exist so nothing has to be reshaped later.
- The index and a granted folder use different URI schemes and can never be matched by URI; name + size is used instead. That is a heuristic, and the code says so.

## [1.5.1]
Phase 1 of the pending-work plan: correctness and data safety. No new features.

### Fixed
- **The release workflow could publish under the wrong tag.** A tag push fell back to a hard-coded `v1.0.1`; it now uses the pushed tag and fails loudly if none can be resolved.
- **The video editor read the duration on the main thread**, freezing the screen on a large file. It now runs on an IO thread, and the preview appears immediately.
- **Video export froze the UI and could leave debris.** Publishing the finished file to MediaStore ran on the Transformer's callback thread; it now runs on IO. A failed publish deletes the pending row, and the copy is verified before success is reported.
- **A failed "delete forever" still removed the trash record**, leaving an orphaned file nothing could find or clean up. The row is now kept until the file is genuinely gone, and the result is returned so it can be retried. `discard` and the 30-day purge behave the same way.
- **Restoring from the trash could publish a half-written file.** The copy is now verified and an incomplete row is removed instead of being shown in the library.
- **A failed photo export left an orphaned pending MediaStore row.** Compression and the MediaStore write are both checked now, the row is finalised and verified, and it is deleted on any failure. Overwriting no longer reports success when the encoder failed.
- **Blur and sharpen forced every pixel opaque**, turning transparent PNG/WebP images solid. Alpha is now blurred with the colour, and sharpening preserves the source alpha.
- **Photo export was capped at 4096 px**, so a 50 MP photo exported smaller than the original. Export size is now a choice — **Original** (default), 4096 px or 2048 px — and if the full size does not fit in the heap the app falls back and says so.
- **The PNG export dialog showed a quality slider that did nothing.** PNG is lossless, so the slider is replaced by a note.
- **Undo recorded every pixel of a slider movement**, making it unusable. Dragging now previews live and records **one** undo step when released; discrete edits (rotate, flip, filter, crop) remain single steps.
- **EXIF orientations 5 and 7 (transpose, transverse) were not handled**, so those photos exported sideways. All eight orientations are now mapped, with unit tests.
- **An unexpected Deep Scan failure left the screen on "Scanning" for ever.** Errors are converted to a terminal `Failed` state with a message, in both the Deep Scan and the Android-folders scanners, with a second safety net in the view models.

### Notes
- The audit's Deep Scan grant item needed no change: a directory whose persisted permission fails is already rejected and reported, and never saved.
- No device was available in this environment; these are verified by compilation, unit tests and code inspection, not by running the app.

## [1.5.0]
### Fixed
- **"No installed app can open this" even with five apps installed.** Android 11+ hides other packages from `queryIntentActivities()` unless the app declares them, so the check returned an empty list and the app refused to show the chooser — for PDFs, Word, Excel, JSON, text, everything. The manifest now declares the intents it needs to resolve (`<queries>` for VIEW/EDIT/SEND).
- **The chooser is no longer gated on that check at all.** Package visibility can still under-report, so `Open with` now always shows the system chooser, which is the authority: it lists the real apps and says so itself when there genuinely are none. The same applies to `Open in`, the PDF viewer's `Open with`, and `Advanced edit`.
- **A failed move to Recently Deleted now says why.** Previously every failure produced the same "nothing was deleted", which is unactionable. The copy keeps its reason (provider refused it / permission missing / empty copy) and the message names the file and the cause.
- A trash copy that came out empty is now treated as a failure rather than being recorded — deleting an original whose backup is empty would lose the file.
- Trash file names are sanitised (path separators stripped, length capped), so an odd display name can no longer make the destination invalid.

### Added
- **Swipe between videos.** The video player is now a playlist: sliding moves to the next video **in the order of the grid you came from**, and it starts playing. One ExoPlayer is reused, so only the media item is swapped — no second player and no overlapping audio. A "n / total" counter shows where you are. Rule is the same pure, unit-tested `ViewerPager`.
- **Pull down to refresh.** Drag down from the top of Home, or of any media list, to rescan. The indicator only appears for the refresh itself, not for the first load.

### Fixed
- **Videos appearing while sliding through photos.** The viewer built its pager from the *whole* library (`observeMedia(ALL)`) and the route's `type` argument was never passed to the screen, so a photo could be followed by a video — or an audio track or a PDF. The pager now contains only items that can actually be displayed **and only the tapped item's own kind**, and it uses the exact list that was on screen (via a new `ViewerContext`), so the swipe order matches the grid instead of the library's own order. Opening the viewer without a browser behind it falls back to the library, still limited to that kind. Rule is a pure, unit-tested `ViewerPager`.

### Changed
- **Home now follows the file-type settings.** Switching a category off removes its entry point from Home (Photos / Videos / Audio / Documents), and "All files", "Albums" and "Favourites" disappear once no category is left on. Previously Home kept offering a folder that was guaranteed to be empty. When everything is switched off, Home says so and links to Settings instead of showing a blank screen. The rule lives in a pure, unit-tested `HomeEntries`.

### Notes
- The in-app PDF reader was already wired: tapping a PDF in any list opens it. The message in the screenshot came from the *Open with* action inside that viewer, which the visibility bug had broken.

## [1.4.1]
### Added
- **`tools/adb_export_android_data.sh` and `.ps1`** — copy `Android/data` (or one app's folder) off a non-rooted phone using the official ADB interface, then push it back into `Download/` for Deep Scan. Nothing is exploited; the phone is only read.
- The **Android folders** screen now says where to go for another app's `Android/data`, with the script names, instead of leaving the user at a wall.

### Notes
- No app can read another app's `Android/data` on Android 11+. That is the OS rule, and it applies to every app equally — `MANAGE_EXTERNAL_STORAGE` does not change it and the folder picker refuses it. The only in-app alternative is root, which this project does not use.

## [1.4.0]
### Added
- **"Android folders" screen** — access to the restricted `Android/` tree, stated honestly.
  - Shows, per location, whether a **non-rooted** app can read it and exactly why.
  - **Scan `Android/media`** (shared media written by other apps — WhatsApp, Telegram…), this app's own `Android/data/<package>`, and on Android 10 and below the whole legacy `Android/data` and `Android/obb`.
  - Files found here can be opened, shared and previewed like any other, via a FileProvider (so a `file://` item never triggers `FileUriExposedException`).
  - A shortcut to the system "All files access" screen when that is what is missing.
- Shared `FileClassifier` so the SAF deep scan and the `Android/` scan identify files identically (MIME → extension → magic bytes).
- `AndroidFolderPolicy` — a pure, unit-tested model of what is reachable, including the reasons.

### Platform truth this release documents
On **Android 11+**, `Android/data/<other apps>` and `Android/obb` are refused to **every** non-rooted app. Neither `MANAGE_EXTERNAL_STORAGE` ("All files access") nor the Storage Access Framework can open them — the system picker declines the grant and the file APIs return nothing. Only root can read them, which this app deliberately does not use. On Android 10 and below they are ordinary storage and are scanned normally. `Android/media` remains readable, which is where most messaging apps now keep their media.

## [1.3.5]
### Fixed
- **Cancellation was being swallowed.** `runCatching` and `catch (Throwable)` catch
  `CancellationException`, so a cancelled operation was reported as an ordinary
  failure. Consequences: cancelling a copy returned `null`, which the delete flow
  read as "copy failed" and used to **roll back a trash entry that should have
  been kept**; and a cancelled MediaStore query returned a **half-populated
  library** that looked complete. New `runCatchingCancellable` /
  `ignoreUnlessCancelled` helpers rethrow cancellation, and they are now used by
  the scanner, hasher, signature detector, MediaStore load, trash, exporter,
  bitmap editor and the Media3 trimmer.
- **Removed dead code**: `VideoTrimmer` (superseded by `MediaTrimmer` in 1.2.0)
  was still in the tree — an earlier delete was undone by a later full-tree push.

## [1.3.4]
### Added
- **File size range: 1 byte to unlimited.** Every file the app can see is shown regardless of size — APKs, images, videos, documents, archives, anything.
  - The displayed size now has a floor of **1 B**, so a real file never reads "0 B". A size the provider did not report shows as **Unknown** rather than a misleading number.
  - The range is explicit and adjustable in **Settings → Scan limits** (Advanced): minimum 1 B / 1 KB / 100 KB / 1 MB, maximum Unlimited / 100 MB / 1 GB / 10 GB. The default is 1 B – unlimited, i.e. nothing is filtered.
  - A size that cannot be read is **never** treated as too small. SAF reports 0 for "unknown" as well as for "empty", so dropping on that basis would have hidden real files.
  - The range is applied by the deep scan and by the main list, and the scan summary reports how many files it skipped if you narrow it.

## [1.3.3]
### Added
- **Advanced editing in another app.** Basic adjustments stay in the app; heavy editing now hands the file to whichever editor is installed:
  - Image/video viewer: an **Advanced** action (alongside the existing **Edit** for the in-app editor).
  - Video player: a bottom bar with **Trim** (in-app), **Advanced** (another app) and **Share**.
  - Photo editor: an **Advanced** action in the top bar, so you can jump out mid-edit.
  The hand-off uses `ACTION_EDIT` with a chooser, falling back to a plain open when no editor is installed, and reports plainly when nothing on the device can edit the file — no empty chooser.

### Changed
- The video player gained a labelled action bar; trimming moved into it from the top scrim.

## [1.3.2]
### Added
- **"Open with" for documents and other files.** PDFs, Word, PowerPoint, Excel, archives, audio and any other file without an in-app preview can now be handed to the app you already use for them:
  - Tapping a document in any list opens the system chooser.
  - A single selected file shows **Open in** in the selection bar.
  - The PDF viewer has an **Open with** action, so a PDF can go to your own reader instead.
  The chooser only appears when at least one installed app can actually handle the file; otherwise the app says so plainly instead of showing an empty list.

### Changed
- The PDF viewer's **Upload** action now uses the same handler check, so it can no longer offer an empty chooser.

## [1.3.1]
### Fixed
- **Deleting audio did nothing.** `MediaStore.createDeleteRequest` rejects a set that spans different collections or volumes and *throws*; that exception escaped the caller, so no consent prompt appeared and no message was shown. The request is now guarded, with a per-item fallback and an honest result. `createWriteRequest` had the same flaw (it also throws for files picked through SAF) and is guarded too.
- **Non-video items restored into the wrong place.** The trash only recorded "video or not", so a deleted audio file was restored into `Pictures` as if it were an image — and on some devices that insert failed outright. Trash rows now store the real file kind (Room migration 2→3) and restore to Music, Documents or Downloads as appropriate.
- Bulk selection had no visible way in — only long-press worked, and there was no Select-all at all.

### Added
- **Select all** in the browser top bar (enters bulk selection without long-pressing) plus a **Select all** action in the selection bar.
- **Delete** in the audio player, using the same recoverable trash + consent flow as the rest of the app.
- **"Open a PDF"** on the Documents screen: pick any PDF on the device and read it in the built-in viewer, independent of the All-files-access permission. (Move/Copy on a picked PDF need it to be in the media index, and the app reports that rather than pretending.)

## [1.3.0]
### Added
- **Settings screen** with a clear split between **Basic** and **Advanced**: advanced sections stay hidden until you turn on Advanced mode. Includes a settings search, a live "active configuration" summary, an "Advanced mode" master switch, per-row helper text, and a Recommended/Experimental label.
- **Feature control with real effects.** Every switch changes actual behaviour:
  - Quick scan off → the MediaStore index is emptied and not queried.
  - A file category off → it disappears from every list *and* is skipped by the scanner.
  - Videos off → video discovery stops and video thumbnails are no longer generated.
  - Thumbnails off → tiles show the file-type icon instead of going blank.
  - Duplicate detection off → the expensive hashing pass is skipped entirely.
  - Signature detection off → classification falls back to MIME and extension.
  - Include hidden files / extensionless files, recursion depth, result limit and identification byte count are all enforced by the scanner.
  - Deep scan off → the scan refuses to start and explains why.
  - Default layout, grid size and sorting come from Settings and are applied to the real UI.
- **File signature detection** (`FileSignatureDetector`): identifies a file from its leading bytes rather than its extension — JPEG, PNG, GIF, WebP, BMP, TIFF, HEIC/HEIF, AVIF, the MP4/MOV/M4A/3GPP family, MKV/WebM, AVI, WAV, MP3, FLAC, Ogg, MIDI, PDF, PostScript, PSD, ZIP/RAR/7z/GZIP/BZIP2/XZ, ICO, plus low-confidence text heuristics. Reads only a bounded header, never the whole file.
- **"Set as default gallery" on first launch.** The app declares itself as a gallery candidate (`APP_GALLERY`, plus `ACTION_VIEW` filters for image/video/audio) and offers a one-time prompt that opens the system screen where the user makes the choice — Android does not let an app set this itself.
- Opening a photo from another app now jumps straight to that item.
- Root **status** is reported read-only (a `su` binary check). It never requests root.

### Notes — deliberately not implemented
- **Root-based recovery is not implemented.** Reading raw block devices bypasses Android's security model and cannot honestly promise recovery of TRIMmed or encrypted data, so there is no toggle pretending otherwise.
- Settings export/import, thumbnail cache-size limits, recovery history, Paging 3 and the full recovery engine from the master prompt are not in this release.

## [1.2.0]
### Added
- **In-app PDF viewer** built on the platform `PdfRenderer` (no third-party library): page-by-page rendering, swipe between pages, pinch/double-tap zoom, and a bottom bar with **Share, Copy, Move, Upload and Print**. Print goes through the system print framework, so installed print services and "Save as PDF" both work.
- **In-app audio player**: playback with a seek bar, and a bottom bar with **Edit, Cut, Share, Move, Copy and Info**.
- **Audio trim editor**: pick a start and end point, preview exactly that range, and export the clip to `Music/ProGallery` via Media3 Transformer. The original file is untouched.
- Copy support: copies a file into another folder through MediaStore, cleaning up the pending row if the copy fails.
- An optional **All files access** card on the Documents screen, which is what makes PDFs and other documents created by other apps visible.

### Fixed
- **Audio appeared to be missing.** The permission gate required *every* permission at once, so denying audio (or any single one) blanked the whole app. Access is now granted if the app can see any category, with an actionable banner for whatever is still off.
- **The list no longer stays empty after granting a permission.** Screens re-query MediaStore on resume, which also picks up files added while the app was in the background.
- **PDFs are visible and openable.** Android hides documents created by other apps from a normal media permission; with All files access they appear in the index, and Deep Scan remains the alternative. PDFs now open in the in-app viewer instead of being handed to the system.
- `MediaTrimmer` replaces `VideoTrimmer` and handles audio-only exports as well as video.

## [1.1.1]
### Fixed
- **The selection bar no longer hides under the system navigation bar.** It now clears the navigation-bar inset, so its actions are actually tappable on gesture- and button-navigation devices.

### Changed
- The selection bar now matches the full-screen viewer's action bar: larger icons with a label under each, and a proper ✕ to clear the selection instead of an ambiguous "more" icon.
- The viewer's snackbar also clears the navigation bar.

## [1.1.0]
### Added
- **Every file type is now scannable**, not just photos and videos. A pure-Kotlin `FileTypeRegistry` classifies by MIME then extension across images (including camera raw, PSD, AI, EPS, INDD, HEIF, TIFF, WebP), video, audio, documents (office, PDF, ebooks, subtitles, source, CAD/DWG) and archives — hundreds of extensions. Unknown files are browsable as **Other** instead of being dropped.
- New browse categories: **All files**, **Audio** and **Documents**, with counts on the home dashboard.
- Audio plays in the in-app player; documents, archives and unknown types open with whichever app on the device handles them, and report clearly when none does.
- Thumbnails for non-previewable kinds show a type icon and the file extension.
- `READ_MEDIA_AUDIO` is requested on Android 13+.
- Deep Scan indexes every file type in a granted folder, not only media.

### Changed
- `MediaType` is now a full file-kind enum (image, video, audio, document, archive, other, all).
- MediaStore rows are no longer filtered by "supported MIME"; each row is classified and kept.

## [1.0.5]
### Fixed
- **Share no longer crashes.** Sharing starts from an application context, which requires `FLAG_ACTIVITY_NEW_TASK`; without it the platform throws. Added, and covered by instrumentation tests for single- and multi-item sharing.
- **Crop presets now produce the requested ratio.** The crop was computed on a unit square, which ignores the photo's shape — so 1:1 cropped nothing at all, and 4:3 / 16:9 gave the wrong ratio. Crop geometry now works in pixel space and accounts for the current rotation. Unit tests cover portrait, landscape and square photos.
- **Delete is now recoverable.** The gallery and viewer copy the bytes into the app-managed trash *first*, then ask the platform to remove the originals. If you decline consent (or the removal fails), the copies are rolled back so the trash never diverges from reality. Permanent deletion from the trash now asks for confirmation.
- **"Overwrite original" is safe.** It now requires the platform's write consent on Android 11+, and is only offered when the export format matches the original file's format — a JPEG can no longer be rewritten as PNG behind a JPEG name. The default action preserves the original.
- **Stable media identity.** Favourites, hidden items and album membership were keyed by the numeric MediaStore id, which collides between photos and videos (and between volumes). They are now keyed by the item's content URI, with a Room migration that re-keys existing rows so saved favourites are preserved.
- **SD-card media.** Items are queried from the merged external view but their URIs are now built from each row's own volume, so photos and videos on removable storage resolve correctly for open, share, rename and delete.

## [1.0.4]
### Added
- **Stable release signing.** All builds from this repository are now signed with a single key held in encrypted repository secrets, so a new APK installs straight over the previous one instead of demanding an uninstall. The key also signs debug builds, and `keystore.properties` makes local builds use it too.
- A cached debug keystore fallback for the case where the signing secret is not configured.

### Fixed
- Repeated "app not installed / must uninstall first" when updating: each CI run previously generated its own throwaway debug key, so the signer changed every build.

## [1.0.3]
### Fixed
- **Tapping a thumbnail now opens that photo.** The viewer worked out its starting page while the media list was still empty, so it always resolved to index 0 and opened the first item. It now jumps to the tapped item once the library loads.
- **Swiping between photos works again.** The image's gesture handler was consuming single-finger drags even at natural size, so the pager never received the swipe. A one-finger drag now pans only while zoomed; at natural size the gesture is left untouched for the pager.
- Tap detection moved to an ancestor of the pager, so it can no longer swallow a swipe before the pager sees it.

## [1.0.2]
### Added
- Viewer controls revealed by tapping: share, edit, rename, move, delete and info, plus favourite in the top bar.
- A play button on videos in the viewer, which opens the full-screen player.
- "Move" now changes a MediaStore item's folder via `RELATIVE_PATH` on Android 11+ (reported plainly as unsupported on older versions).

### Fixed
- **Pinch-to-zoom.** Tap detection was on the same node as the transform gestures and swallowed them; tap detection now lives on the container and the transform on the image, so pinch works.
- **Zoom/pan leaking between pages.** Pager pages are now keyed by item, and a page resets to fit when you leave it — no more images stuck mid-drag or snapping back.
- The pager stops swiping while zoomed, so panning is not stolen by a page change.
- Editing a video from the viewer now opens the video editor instead of the photo editor.

## [1.0.1]
### Added
- Immersive full-screen media: the image viewer and video player hide the system bars, use a black background, reveal controls on tap, and support double-tap zoom. Bars are restored on exit, and a swipe reveals them transiently.
- Media now extends into display cutouts (`windowLayoutInDisplayCutoutMode=shortEdges`) on notched devices.

## [1.0.0] - TBD
Initial release.
