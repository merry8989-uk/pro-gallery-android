# ProGallery — Pending Work Plan

Consolidated from every spec you gave me: the main project spec, the Deep Scan &
Recovery master prompt, the Advanced Settings master prompt, your audit, and the
UltData feature list used as a reference. Nothing here is invented — each item was
checked against the current code, not remembered.

**Current release:** v1.5.1 · `versionCode 20` · CI green · signed with the project key.

**Phases 1, 2A, 2B and 4 complete.** Next: Phase 3 (settings completeness).

---

## 1. Where the project actually stands

| Area | State |
| --- | --- |
| Stack | Kotlin 2.0.21, Compose + Material 3, MVVM + repository, Hilt, Room, Coil, Media3, Gradle KTS + version catalog |
| SDK | minSdk 26 · compileSdk/targetSdk **35** (36 pending) |
| Library browsing | MediaStore index, folders, dates, grid/list, adjustable columns, search, sort, bulk select (incl. Select all), favourites, hidden items, custom albums |
| Viewers | Full-screen image viewer (pinch/double-tap zoom, one-kind paging), video player **playlist** (swipe to next in grid order), PDF viewer, audio player + trimmer |
| Editors | Photo editor (crop/rotate/flip/colour/blur/sharpen, undo/redo, before-after, EXIF, non-destructive), video editor (trim/mute/rotate/resolution, progress + cancel) |
| File types | 332 extensions across image/video/audio/document/archive/other + magic-byte signature detection |
| Deep Scan | SAF tree walk, progress, cancel, limits, duplicates, error list, signature identification |
| Android folders | Honest reachability report + scanner for `Android/media` and own data; ADB export scripts in `tools/` |
| Trash | Real recoverable trash: copy → consent delete → restore to the right collection; migration 2→3 stores real kind |
| Settings | DataStore, Basic/Advanced split, search, live summary, ~45 real settings; **no destructive Room migration** |
| Quality | CI = unit tests + debug APK build; 4 instrumented test classes; 7 releases verified by dex digest |
| Recent fixes | Cancellation no longer swallowed · "No installed app can open" (package visibility) · viewer swipes one kind · Home follows file types · size range 1 B–unlimited · pull-to-refresh |

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

Base exists (~45 settings). Missing from the Settings master prompt:

- **Export / Import settings** — SAF + versioned JSON, validated, rejects dangerous values, **no private paths or URI grants**. Plus **Restore defaults** (with confirmation) and **per-section reset**.
- **Thumbnails** — quality (Low/Balanced/High), max dimension, **cache size limit**, **clear cache**, and per-kind toggles: video thumbnails, audio artwork, document previews, thumbnails for signature-detected files, cache generated thumbnails, prefer real preview over icon, show resolution/duration, show source, show confidence, auto-generate during scan.
- **Detection** — orphaned-media discovery, corrupt-media detection, metadata/EXIF extraction, detect incorrect extensions, validate headers, signature scan depth (Safe/Balanced/Extended).
- **Recovery & export block** — enable export, multi-file export, preserve names/timestamps, verify after copy, confirm before export, allow thumbnail-only export, ask for destination each time, remember last destination, default format, duplicate-name policy (Ask/Rename/Skip), history retention, max simultaneous jobs.
- **Performance** — confirm each mode really changes behaviour; add pause on screen-off / low battery / thermal pressure, batch size, incremental rescan.
- **Storage & permissions** — remember granted folders, refresh after permission change, inaccessible-folder warnings, include secondary volumes, **show scan scope before starting**, **permission diagnostics screen**.
- **Privacy & diagnostics** — keep scan history, save diagnostic logs, include file paths in logs (**OFF by default**), auto-clear old logs, clear scan history, clear thumbnail cache, clear temporary scan data. Confirmation before each destructive cleanup; never delete recovered files.
- **UI customisation** — compact result cards, haptics, default scan-results tab.
- **Feature dependency registry** — one central config object instead of scattered checks, with tests for every dependency rule in the spec.
- **"Run a scan with the current configuration"** button, and a warning when every discovery method is off.

---

## 6e. Cloud inside the app — v1.17.0

**✅ Cloud folders browsed in-app**, with no sign-in of the app's own: a folder is granted
once through the system picker and persisted, and the service keeps its own session.

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
- ✅ Done in v1.12.0: the access diagnostics screen. **Phase 3 is complete.**

## 6. Phase 4 — performance & platform — ✅ mostly DONE in v1.8.0

Shipped: targetSdk 36 (AGP 8.9.3), MediaStore ContentObserver + quiet refresh on resume,
incremental batch loading with a configurable batch size, signed release AAB in CI, Android
lint in CI, Android 14 "Choose more" for limited access, and a device-free
`MigrationCoverageTest` guarding the upgrade path.

**Deliberately not done:** full Paging 3 — it would push sorting/search into SQL and decorate
page by page, a rewrite of the core data path every screen depends on. The audit's own
suggestion ("load from the cursor in batches") is what was implemented instead.

**Still open:** instrumented tests need an emulator in CI.

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

## 8. Decisions I need from you

1. **Order** — Phase 1 (13 small correctness items) first, or straight into the recovery engine? I recommend Phase 1: it is one release, low risk, and stops real data-handling bugs.
2. **Play Store** — do you want a signed release AAB + targetSdk 36, or keep sideload debug APKs for now?
3. **Trash retention** — make the 30-day purge automatic via WorkManager (currently it only runs when the Trash screen is opened)?
4. **Recovery priorities** — which matters most to you: photos, WhatsApp media, video, or documents? That sets the order inside the engine.

---

## 9. Proposed releases

| Release | Contents |
| --- | --- |
| ~~**v1.5.1**~~ | ✅ Phase 1 — the 13 correctness/safety items + the release-workflow tag fix (shipped) |
| ~~**v1.6.0**~~ | ✅ Recovery engine A: Quick Scan, result browsing, `RecoveryThumbnail`, result cards, safe export, history (shipped) |
| ~~**v1.7.0**~~ | ✅ Recovery engine B: thumbnail/cache mode, faster duplicates, cache limit + clear, progress throttling (shipped) |
| **v1.8.0** | ✅ Phase 4 — API 36, ContentObserver, incremental loading, signed AAB, lint, migration guard (shipped) |
| **v1.9.0** | Phase 3 — settings completeness (export/import, resets, per-kind thumbnail toggles, diagnostics, dependency registry) |

Every release: feature branch → PR → CI green → merge → tagged release → APK
verified by dex digest and signature, with an honest note on what was and was not
tested on a real device (there is no device in this environment).
