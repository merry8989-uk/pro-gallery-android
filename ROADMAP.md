# ProGallery — Pending Work Plan

Consolidated from every spec you gave me: the main project spec, the Deep Scan &
Recovery master prompt, the Advanced Settings master prompt, your audit, and the
UltData feature list used as a reference. Nothing here is invented — each item was
checked against the current code, not remembered.

**Current release:** v1.61.0 · `versionCode 80` · CI green (unit tests, lint and an emulator
job) · signed with the project key.

**Phases 1, 2A, 2B, 4 and 5 complete. Phase 3 is not** — the honest state is in §5, which is the
one section of this document that was corrected against the code on 23 October 2026 after an audit
found it claiming more than the code did.

---

**New, and the current work: the file manager.** `FILE-MANAGER-PLAN.md` has the parts. The app is
named **Advanced File Manager** from v1.48.0 and the update path no longer dead-ends; the tree, the
file operations and the one dispatch point that opens every format are still to build.

## 1. Where the project actually stands

| Area | State |
| --- | --- |
| Stack | Kotlin 2.0.21, Compose + Material 3, MVVM + repository, Hilt, Room, Coil, Media3, Gradle KTS + version catalog |
| SDK | minSdk 26 · compileSdk/targetSdk **36** (shipped in v1.8.0) |
| Library browsing | MediaStore index, folders, dates, grid/list, adjustable columns, search, sort, bulk select (incl. Select all), favourites, hidden items, custom albums |
| Viewers | One player for audio **and** video (decoder fallback, position memory, HLS/DASH/RTSP), full-screen image viewer, PDF, and readers for text/JSON/CSV/code, DOCX, XLSX, ODT/ODS/ODP, EPUB, PPTX, SVG and stickers |
| Editors | Photo editor (crop/rotate/flip/colour/blur/sharpen, undo/redo, before-after, EXIF, non-destructive), video editor (trim/mute/rotate/resolution, progress + cancel) |
| File types | 332 extensions across image/video/audio/document/archive/other + magic-byte signature detection |
| Deep Scan | SAF tree walk, progress, cancel, limits, duplicates, error list, signature identification |
| Android folders | Honest reachability report + scanner for `Android/media` and own data; ADB export scripts in `tools/` |
| Trash | Real recoverable trash: copy → consent delete → restore to the right collection; migration 2→3 stores real kind |
| Settings | DataStore, Basic/Advanced split, search, live summary, 63 settings, **every one with a reader** (five were not, until v1.37.0); **no destructive Room migration** |
| Quality | CI = unit tests + lint + an emulator job + the two checkers; a signed release AAB; 54 releases verified by dex digest and signature |
| Recent fixes | Cancellation no longer swallowed · "No installed app can open" (package visibility) · viewer swipes one kind · Home follows file types · size range 1 B–unlimited · pull-to-refresh · **five decorative settings wired** (v1.37.0) |
| Diagnostics | A local log with 12 categories, 6 levels and 30 events — every one with a call site — a live view, filters, four export formats, and an email hand-off. **Supabase was removed in v1.36.0** |

---

## 2. Already done — do not redo

Your audit was written against v1.3.0. These findings are **fixed**; verified in code:

- Share crash (`FLAG_ACTIVITY_NEW_TASK`) — fixed.
- Delete not wired to the trash — fixed (copy → consent delete → restore, with rollback).
- Unsafe "overwrite original" — fixed (write consent + format guard).
- Wrong crop presets — fixed (pixel-space `CropMath`, rotation-aware, unit tested).
- Colliding media IDs — fixed (URI-based `mediaKey`, Room migration 1→2, existing favourites preserved).
- Volume-unaware URIs — fixed (URI built from the row's `VOLUME_NAME`).
- `catch (Throwable)` swallowing `CancellationException` — fixed in v1.3.5 (`runCatchingCancellable`).
- `fallbackToDestructiveMigration` — **not present**; migrations 1→2 and 2→3 are explicit.

---

## 3. Phase 1: correctness & data safety — ✅ DONE in v1.5.1

All thirteen items are implemented, plus the release-workflow tag fix. Item 12
needed no code change: a directory whose persisted permission fails was already
rejected and reported rather than saved.

| # | Item | Why it matters | Where |
| --- | --- | --- | --- |
| 1 | **Release workflow tag bug** | A tag push falls back to the hard-coded `v1.0.1`, so a release can be published under the wrong tag | `.github/workflows/release.yml` — use `github.ref_name` |
| 2 | Video editor reads duration on the **main thread** | `MediaMetadataRetriever` in `load()` blocks the UI on a large file | `VideoEditorViewModel.load()` → `Dispatchers.IO` |
| 3 | Video export copies the output on the callback thread | UI freeze on big files; pending MediaStore row + temp file leak on failure/cancel | `VideoEditorViewModel`, `MediaTrimmer` |
| 4 | Trash `deleteForever()` drops the DB row even when the file delete fails | Orphaned trash file with no record | `TrashRepository` |
| 5 | Trash restore can publish a half-written file | Incomplete item appears as a real file | `TrashRepository.publishBack` — write temp, then finalise |
| 6 | Photo export leaves an orphaned pending MediaStore row on failure | Invisible junk in the user's library | `PhotoExporter` — check results, delete on failure |
| 7 | Blur/sharpen force alpha to `0xFF` | Transparent PNG/WebP come out opaque | `BitmapEditor.boxBlur()` / `sharpen()` |
| 8 | Photo export capped at 4096 px | A 50 MP photo exports smaller than the original | `PhotoEditorViewModel` — memory-safe full-res path or an explicit output-size choice |
| 9 | PNG export shows a quality slider that does nothing | A control that has no effect | `PhotoEditorScreen` — hide for PNG |
| 10 | Undo history commits on every slider movement | Undo becomes unusable | `PhotoEditorViewModel` — checkpoint commits |
| 11 | EXIF mirrored cases (transpose/transverse) untested | Rotated/mirrored photos can export wrong | `BitmapEditor` + tests |
| 12 | Deep Scan saves a directory URI even when the grant failed | A folder that will never open sits in the list | `DeepScanViewModel` |
| 13 | `ScanState.Failed` not emitted consistently | The UI can sit on "Scanning" forever after an error | `DeepScanRepository` |

---

## 4. Phase 2A — recovery engine — ✅ DONE in v1.6.0

Shipped: Quick Scan (index + Recently Deleted + granted folders), `RecoveryThumbnail`,
result cards with source/confidence/evidence/status, preview with metadata, safe SAF
export with verification, and export history (Room 3→4).

**Phase 2B — also DONE in v1.7.0:** thumbnail/cache discovery as its own scan mode,
faster duplicate detection (size → sampled hash → full hash), thumbnail cache limit
and clearing, clock-based progress, and recovery de-duplication.

From the Recovery master prompt. Priority order it specifies: fix scan → real
discovery → icons/thumbnails → preview → safe export → performance → tests.

**A. Scan modes (separate, clearly labelled)**
- Quick Scan — accessible MediaStore media **missing from our own index**; accessible files in granted folders.
- Deep Media Scan — exists (SAF walk). Needs the *result* side, not the scanning side.
- Thumbnail & Cache Discovery — find thumbnails/previews in legally accessible cache dirs; **distinguish a thumbnail from the original** and never claim it is the original.
- SAF Folder Scan — exists; add persisted-permission honesty (grant failed ⇒ say so, don't save it).
- Root module — **not doing** (see §7).

**B. `RecoveryThumbnail` component** — real image thumbnails, video frames, audio artwork/icons, PDF/document icons, generic icons for unknown, broken/corrupt placeholder, loading + error fallback, orientation/alpha/aspect handling, lazy + cached, decoded at display size. Never one icon for everything.

**C. Result cards** — thumbnail, name, type/extension, size, resolution/duration, **discovery source** (MediaStore / SAF / cache / thumbnail / signature), **confidence** (High/Medium/Low with evidence), **recovery status** (Available / Preview Only / Exported / Failed), plus Preview and Recover actions.

**D. Safe export workflow** — SAF destination, streaming copy, preserve extension + MIME, **verify before reporting success**, temp-then-finalise, never overwrite without consent, clean up partial output on failure/cancel, never touch the source during scanning, keep a **recovery history** (name, timestamp, destination, outcome). Thumbnail-only exports labelled honestly.

**E. Identification** — bounded reads only, format signatures, truncated/corrupt detection, confidence + evidence recorded, no extension-only classification, no renaming without validating contents.

**F. Architecture** — `RecoveryScanner`, `MediaStoreScanner`, `SafDirectoryScanner`, `RecoveryThumbnailLoader`, `MediaMetadataExtractor`, `RecoveryRepository`, `RecoveryViewModel`, `RecoveryResult`, `RecoverySource`, `RecoveryStatus`. Reuse what exists (`FileSignatureDetector`, `DeepScanRepository`, `FileClassifier`, `ScanState`) instead of duplicating.

**G. UI** — Quick/Deep buttons, folder picker, live progress + cancel, tabs (Photos / Videos / Audio / Documents / Thumbnails / Other), grid-list toggle, sort, search + type filters, preview with metadata, multi-select export, empty state naming what was actually scanned, permission-denied state with an action, error state with retry, real result counter.

**H. Honest framing** — an indexed file is **not** automatically a "deleted file". Classify each result as: existing media, orphaned/unindexed accessible media, thumbnail/cache artefact, corrupt/partial, or genuinely recoverable.

---

## 5. Pending — Phase 3: settings completeness

**Corrected 23 October 2026 against the code.** §6a below says "Phase 3 is complete". It is not,
and that sentence is why this section is worth reading carefully: the phase was declared finished
while roughly half of it had never been built, and nothing noticed because the only record was the
claim itself.

Of the 59 items in the Advanced Settings master prompt's settings list: **~25 done, ~26 missing,
3 present under another name, 4 partial.** Every verdict below was checked at file and symbol, not
remembered.

**Done since the audit**: **privacy and the result cards** (v1.46.0) — a "clear temporary scan
data" action that says what it did **and what it did not touch**, and toggles for the source and
confidence lines. **Phase 3's list is now empty except for incremental rescan.**

**Also done since the audit**: **performance** (v1.45.0) — pausing on low battery and on thermal
pressure, and **the reason now reaches the screen**, which it never did: `pauseReason()` existed
since v1.37.0 and nothing called it.

**Also done since the audit**: **UI customisation** (v1.44.0) — a default results tab that only applies
to an untouched screen, compact result cards, and haptics that are **off until asked for**. There was
no haptic feedback anywhere in the app before this.

**Also done since the audit**: **the thumbnails** (v1.43.0) — preview quality as a preset over the pixel
size, a branch in the policy for files identified only from their bytes, caching applied per
request, and building previews during a scan.

**Also done since the audit**: **the detection block** (v1.42.0) — truncated and empty files, names that
disagree with their contents, the capture date and camera (never the location), and signature depth
as Safe/Balanced/Extended/Custom. It flags only what the bytes can prove: a format with no end
marker is never called truncated, because a complete one and a short one cannot be told apart.

**Also done since the audit**: **the export workflow** (v1.40.0) — confirm before exporting, ask for the
folder or reuse the last one, keep the original name or say where the file came from, the history
retention that was a hard-coded 90 days, and **"Ask me each time"** as a duplicate-name policy. Two
parts of that item are deliberately **not** done and said so rather than faked: preserving original
timestamps (SAF gives no way to set a document's modified time) and a default export format (it
would mean transcoding, which is not implemented).

**Also done since the audit**: **a scan you can run, and a scope that says what it will do first**
(v1.39.0) — `ScanScopeBuilder` builds it from the settings *and* the device (the folders actually
granted, whether `Android/media` is reachable, whether All files access is held), the screen draws
what will be scanned and what will not, and the button runs the engine those settings mean.

**Done** (each with a real reader): export/import with a versioned JSON and validation ·
per-section reset · restore defaults · thumbnail cache limit and clearing · per-kind thumbnails
(video, audio artwork, documents) · resolution/duration on tiles · signature detection and its byte
bound · duplicate detection · export enable · verify-after-copy · duplicate-name policy
(Rename/Skip) · max concurrent export jobs · batch size · remembered granted folders · refresh
after permission change · inaccessible-folder warnings · secondary volumes · the permission
diagnostics screen · keep scan history · save logs · paths-in-logs off by default · auto-clear old
logs · clear scan history · the feature dependency registry with its tests · the "scanning is
disabled" warning · **and, since v1.37.0, the performance mode, the concurrency, the batch size and
the background pause** (all four were stored, shown and ignored).

**Missing**:

- **Performance** — incremental rescan. *(Battery and thermal pauses shipped in v1.45.0.)*

**Present under another name**: "prefer real preview over icon" is `generateThumbnails`; "validate
headers" is `signatureDetectionEnabled`.

## 6n. App media in the gallery — v1.26.0

**✅ Part E's goal**: the app's own index, merged into the grid's order. The scan fills it; the
gallery shows it. This is the fix for media the system's index does not hand over.

## 6m. Usage data and the seven-day review — v1.25.0

**✅** Anonymised usage counts, local only, from a closed vocabulary, with a seven-day review in
Diagnostics. Version and update check merged into one row.

## 6l. In-app update — v1.24.0

**✅** The update is fetched in the app, checked against this app's own signing key, and handed
to Android's installer. The system's confirmation still appears, and the dialog says so.

## 6k. App media — v1.23.0

**✅** Which messaging apps keep media on this device, and how much. The owning app is read from
the folder path and named by the system, so apps nobody listed are still found.

**Shipped in v1.26.0** — see §6n. This line said "still pending" for three releases after it shipped.

## 6j. Memories and the daily reminder — v1.22.0

**✅** Memories matched on the capture date, grouped by years ago, with a WorkManager reminder
at 7 that needs no setting up. The capture date was not being read at all before this.

## 6i. Update check fixed — v1.21.0

**✅** The dialog had no call site, so "Check for updates" opened nothing; and the check now
reads the Atom feed rather than the rate-limited API.

## 6h. Adaptive streams and page links — v1.20.0

**✅** HLS/DASH/RTSP/SmoothStreaming modules added, so `.m3u8` and `.mpd` actually play; and a
pasted page link is recognised and explained instead of being blamed on the container.

## 6g. Text reader — v1.19.0

**✅ Part G (text half)**: a reader for text, JSON, CSV, logs and code. Parts B–F of the
player/readers plan remain.

## 6f. Playback reasons — v1.18.0

**✅ Failures explain themselves.** Every Media3 error code maps to its real reason and to the
action that helps; a missing file is no longer described as a codec problem.

## 6e. Cloud inside the app — v1.17.0

**✅ Cloud folders browsed in-app**, with no sign-in of the app's own: a folder is granted
once through the system picker and persisted, and the service keeps its own session.

## 6p. The log, collected — v1.30.0

**Built in v1.30.0, removed in v1.36.0.** A Supabase project with the schema, the ingest function
and the retention job: append-only through the API, closed vocabularies as constraints, a guard that
refused what must never be stored, and the salted hash computed where the salt lived.

**Then it had to go.** The organisation is at its two-free-project limit, so the log project could
not be brought back from inactive — restoring it was refused. Rather than free a slot by touching
another project, the whole path was removed at the user's request: the uploader, the worker, the
scheduler, the watermark, the consent switch, the build-time endpoint and key, the repository
variables, the migrations and the ingest function.

**What stayed is the half that always mattered**: the local log, the live view, the filter, the
four export formats and the email hand-off. Nothing about the app's own diagnostics ever depended
on a server.

## 6o. Player engine and the capability screen — v1.29.0

**✅ Parts B and C, step 2**: one engine builds every player, the decoder fallback is everywhere,
the MIME hint is applied where the app is certain, and the format answer is a screen.

## 6q. What shipped since v1.16.0 — v1.17.0 to v1.28.0

Written as one entry rather than ten, because the roadmap stopped being updated at v1.16.0 and
ten back-dated sections would read as if they had been there all along.

| Version | What |
| --- | --- |
| v1.17.0 | Cloud browsed **inside** the app: a picked folder becomes a persisted grant, opened straight away. The provider sidebar could not be reproduced — `ContentResolver.acquireRoots()` is not public API — so the picker-first design is the honest alternative. |
| v1.18.0 | The real reason a file will not play, on every player, with **Try another app** offered. |
| v1.19.0 | A reader for text, JSON, CSV, log and code. Binary files are refused rather than shown as noise. |
| v1.20.0 | **HLS, DASH, RTSP and SmoothStreaming**, which were missing while the app's own text claimed they worked. |
| v1.21.0 | Check for updates fixed — the dialog was never rendered — and moved to the Atom feed, which is not rate-limited per address. |
| v1.22.0 | **Memories**: the capture date, not the modification date, and a reminder at seven with nothing to set up. |
| v1.23.0 | **App media**: which messaging apps have media, and what is installed. |
| v1.24.0 | The update downloads, is verified against the app's own signing key, and installs from inside the app. |
| v1.25.0 | **Anonymised usage data** in Diagnostics, with the seven-day review. A closed vocabulary, so no field exists that a filename could be written into. |
| v1.26.0 | **App media joins the gallery**: the app's own index merged into the system's rows. |
| v1.27.0 | **Stickers** on a chequerboard, and **SVG** drawn by the app's own decoder. |
| v1.28.0 | **The log, live, on the access screen** — with the filter, the export, the email, and `access_fix_result`, the outcome half that the 21 October log was missing. |

## 6d. Update check — v1.16.0

**✅ Check for updates** from the releases feed, manual only, with the version comparison
pure and tested. Along the way a real bug was found and fixed: `INTERNET` was never
declared, so Stream from a link, WebDAV browsing and the update check could not open a
connection at all.

## 6c. Player and readers — started v1.15.0

**✅ Part A — truth in the format layer** (v1.15.0): the content-address bug, decodability
truth, animated WebP/GIF, honest playback errors.

**⬜ Parts B–G** — one dynamic player, a capability screen, the formats Media3 cannot
parse (decision needed), WhatsApp/`Android/media` visibility, the SVG and sticker readers,
and the text/JSON document reader. Full detail and the honest capability matrix:
**`PLAYER-AND-READERS-PLAN.md`**.

## 6b. Streaming and cloud — v1.13.0

**✅ Stream from a link** — real: pure link parsing, a bounded recent list, a dedicated
player, and Media3's own reason shown on failure.

**✅ WebDAV browsing — DONE in v1.14.0.** Chosen by the user: it works today with no
registration. Browse, play, credentials kept out of the settings export and the log.

**🔶 In-app OAuth browsing for Drive / OneDrive / Box** — not built. It needs an
application id registered by the user with each provider, which cannot be invented here.
The entries currently open the provider's own app or website and say so. **Terabox was
removed** at the user's request: no official API exists.

## 6a. Phase 5 (in progress) — emulator CI, Paging 3, settings completeness

**✅ Done and verified (CI green, including a real emulator):**
- An **emulator CI job** runs the instrumented suite on API 35. It needed a retry
  step because the runner intermittently caches a truncated SDK package
  ("Error on ZipFile unknown archive"); clearing the SDK temp dir and retrying
  gets past it.
- **`MigrationInstrumentedTest`** migrates a real v3 SQLite database holding a row
  in every user-facing table, runs `MIGRATION_3_4`, and asserts the favourites,
  albums, album covers, membership, hidden items, trash and granted folders all
  survive and that `recovery_history` starts empty. It is self-contained: Room only
  exports the schema of the version being compiled, so `3.json` does not exist, and
  the v3 database is therefore built from explicit DDL. The exported schemas are now
  uploaded as a CI artifact so future migrations can validate against Room's
  expected schema too.

**✅ Paging 3 — DONE in v1.9.0:**
- Done: `MediaQuery` (SQL sort + escaped search, unit tested), a windowed
  `MediaStoreDataSource.page()` sharing one `RowReader` with the other two read
  paths so classification can never diverge, `MediaStorePagingSource` (fills a page
  by reading windows until it has enough matching items), and
  `MediaRepository.pagedMedia()` / `snapshot()`.
- The grid now renders with `LazyPagingItems`. Selection records the items themselves
  so it survives scrolling away; "select all" selects what is loaded; the viewer's
  playlist is loaded on demand; and `favouritesOnly` keys the pager because favourites
  are a Room set rather than a MediaStore column.

**🔶 Phase 3 (settings completeness) — started in v1.10.0.**
- ✅ Done: versioned settings export/import (SAF, no private data, strict format version,
  tolerant of unknown keys) and per-section reset.
- ✅ Done in v1.11.0: the feature-dependency registry, per-kind previews (video frames,
  embedded audio artwork, PDF first pages), resolution/duration on tiles, the
  recovery/export settings block wired into the exporter, and the privacy and
  diagnostics settings backed by a real, redacting, bounded log.
- ✅ Done in v1.12.0: the access diagnostics screen.
- ❌ **"Phase 3 is complete" was written here and was not true.** Corrected on 23 October 2026: about
  half of the phase's own list had never been built. See §5 for what is actually left.

## 6. Phase 4 — performance & platform — ✅ mostly DONE in v1.8.0

Shipped: targetSdk 36 (AGP 8.9.3), MediaStore ContentObserver + quiet refresh on resume,
incremental batch loading with a configurable batch size, signed release AAB in CI, Android
lint in CI, Android 14 "Choose more" for limited access, and a device-free
`MigrationCoverageTest` guarding the upgrade path.

**Deliberately not done:** full Paging 3 — it would push sorting/search into SQL and decorate
page by page, a rewrite of the core data path every screen depends on. The audit's own
suggestion ("load from the cursor in batches") is what was implemented instead.

**No longer open:** an emulator CI job runs the instrumented suite (it needed a retry step, because the runner intermittently caches a truncated SDK package).

### Original Phase 4 list

- **Paging 3** — declared in the version catalog but **not actually used**; the whole library loads at once.
- **MediaStore `ContentObserver`** — no observer, so the index goes stale until a manual refresh (an ON_RESUME refresh exists).
- **Home counts before permission is granted** can stay zero.
- **PermissionGate** — Android 14 partial access: refresh after a grant/revoke, and an "update selection" action beyond "grant full access".
- **Room migration tests** — prove favourites, albums, hidden items and trash survive an upgrade.
- **targetSdk 36** — Play requires API 36 for new apps/updates from **31 Aug 2026**. Currently 35.
- **Signed release build / AAB** — releases are debug-signed (sideload only, not Play-ready). `MANAGE_EXTERNAL_STORAGE` also carries Play policy restrictions.
- **CI** — add lint, dependency/security checks, an API 36 build, and emulator instrumentation runs.
- **Faster duplicate detection** — size grouping → sampled hash → full hash, instead of hashing every same-size candidate fully.
- **Scan progress throttling** — progress does not update per file, so a huge folder looks frozen.

---

## 7. Deliberately NOT doing — and what replaces it

| Not doing | Why | What you get instead |
| --- | --- | --- |
| Root recovery, raw block devices, partition reads | Requires exploiting Android or a device-specific hole; cannot honestly promise TRIMmed/encrypted data. Also outside what I'll write. | Read-only root *status* check (already there), and the UI states plainly that root recovery is not implemented. |
| Reading other apps' `Android/data` / `Android/obb` on Android 11+ | The OS refuses it to **every** non-rooted app — not a missing permission. | `Android/media` scanning (where messaging apps now keep media) + `tools/adb_export_android_data.sh` / `.ps1`. |
| Claiming recovery of deleted internal-storage data | Nothing on a non-rooted device can read it back. | Recovery limited to what is genuinely reachable: our own trash, orphaned/unindexed accessible files, thumbnails and caches — each labelled with its real source and confidence. |
| "Recycle Bin" / real-time deletion monitoring as UltData describes | There is no system recycle bin an app can read; UltData's own claims are not verifiable. | We *do* have our own trash, and a MediaStore observer (Phase 4) can watch deletions and add them to our trash while the app is installed. |

---

## 8. Decisions — what was asked, and what is still open

**Answered since this section was written:**

1. ~~Order — Phase 1 first, or the recovery engine?~~ **Answered by doing it**: Phase 1 shipped in
   v1.5.1, the recovery engine in v1.6.0 and v1.7.0.
2. ~~Play Store — a signed AAB and targetSdk 36, or sideload debug APKs?~~ **Both**: targetSdk 36
   shipped in v1.8.0 and every release carries a signed AAB.
4. ~~Recovery priorities — photos, WhatsApp media, video or documents?~~ **Answered by coverage**:
   all four, plus the app-media index (§6n).

**Settled in v1.41.0:**

- ~~Trash retention~~ — a daily WorkManager job now purges it; the screen still purges on open.
- ~~"Usage permission"~~ — implemented as the reading I believed was meant: the consent to record,
  offered **on the Access screen** where the data is displayed, rather than a sentence telling the
  person to go and find it in Settings. It is the same setting, not a second one. **If Android's
  `PACKAGE_USAGE_STATS` was meant instead, that is a different thing and it has not been touched.**
- ~~Part D, the hand-off half~~ — tapping a format the app cannot open now says **why** before
  handing it over, which was the one path that still did it silently.

**Still open, and this one is genuinely yours:**

1. ~~Part D~~ — **answered in v1.47.0**, and not by bundling anything. MPEG program streams
   (`.mpg`, `.mpeg`, `.mpe`, `.mpv`, `.m2v`, `.vob`, `.mp2`) are handed to the **device's own
   parser** on Android 11 and up, so they play here where the device can. ASF, SWF and RealMedia have
   no parser on Android at all and the platform parser does not read them either; the matrix now says
   that plainly instead of leaving a dead end.

---

## 9. Releases

The per-release table that used to sit here stopped at v1.9.0 and had been wrong for twenty-eight
releases. **`CHANGELOG.md` is the record** — every release, in order, with what changed and what was
not verified.

The process has not changed, and it is what makes the changelog trustworthy:

> Feature branch → PR → CI green (unit tests, lint, emulator, the two checkers) → merge → tagged
> release → the APK verified by dex digest and signature → an honest note on what was and was not
> tested on a real device. There is no device in this environment, so **nothing here has been run by
> hand**; every claim rests on compilation, unit tests, lint and the dex diff.

**Next release, when the user picks it:** whichever of §5's missing items comes first — the
recommended order is the export workflow, then the detection block.
