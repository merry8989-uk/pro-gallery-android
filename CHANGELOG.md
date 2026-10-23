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

## [1.48.0]
The name, and an update check that is not a dead end.

### Changed
- The app is **Advanced File Manager**. **The application id stays `com.progallery.app`** — changing
  it would install a *second* app, leave the first one's data behind, and never update over it. The
  name changes; the identity does not.

### Fixed
- **A failed update check offered only "Try again"**, which is a dead end when the network is the
  thing that is wrong. It now also offers **Open the page**, which a browser may be able to reach when
  the app cannot — and failing that, at least lets a person copy a link somewhere that works.
- The failure message says what is usually wrong: a connection to GitHub is far more often the
  network than the app — a firewall, a captive portal, or a DNS server answering with the wrong
  address — and suggests trying another network.

### A note on the reported failure
A real report showed `Failed to connect to api.github.com/20.207.73.85:443`. GitHub's addresses are
in `140.82.112.0/20`; `20.207.73.85` is not one of them, which points at DNS on that network
answering with the wrong address rather than at the app. The app cannot fix an ISP, but it can stop
being a dead end when one is in the way — which is what this release does.

## [1.47.0]
Part D answered — and not by bundling a decoder.

### The problem
Media3 **bundles** its extractors, so what the app can play is the same on every device. That is
usually the point, and it is exactly the problem for MPEG program streams: Media3 has no extractor
for `video/mp2p`, so `.mpg` and its relatives were handed to another app.

### The answer: ask the device instead
Android's own `MediaParser` **does** read `video/mp2p`. So a `.mpg` that Media3 refuses is often a
file the device plays perfectly well.

`PlatformParserMediaSourceFactory` hands **exactly those extensions** to the platform's parser and
leaves every other format to the delegate untouched. Four things about it are deliberate:

- **Android 11 and up.** `MediaParserExtractorAdapter` is `@RequiresApi(30)`, so below that there is
  no platform parser to call and the engine does not install it at all — those files are handed to
  another app, exactly as before.
- **Only the MPEG program stream family.** `.ts` and `.flv` are deliberately absent: Media3 reads
  them already. A file that plays today must not start playing differently because of a change meant
  for `.mpg`.
- **The caveat is real.** The parser is the vendor's, so the answer is the device's — which is why
  the capability screen now calls these *"opens, with a caveat"* rather than *"opens here"*.
- **Failure stays honest.** If the device's parser cannot read it either, the player reports it the
  way it reports every other failure, and the hand-off is still offered.

### What moved, and what did not
**Moved** from *needs another app* to **opens, with a caveat**: `.mpg`, `.mpeg`, `.mpe`, `.mpv`,
`.m2v`, `.vob`, `.mp2`.

**Did not move**: ASF (`.wmv`, `.asf`, `.wma`), SWF and RealMedia have **no parser on Android at
all**, and the platform parser does not read them either. The only thing that would play them here
is bundling a decoder — a licensing decision, and `ffmpeg-kit` was retired in 2025, so there is no
maintained drop-in to reach for. **The reasons now say that** instead of leaving a dead end.

### Tests that pinned the old truth
Two tests asserted that `.mpg` needs another app, which was true and is not any more. They were
updated rather than deleted, and a third was added: a refusal that does not say what would change it
is a dead end, so the ASF reasons must name the decoder.

## [1.46.0]
Privacy and the result cards — and the last of Phase 3's list except for one.

### Added
- **Clear temporary scan data.** Removes the app's own index, its decoded previews, and any
  half-written export a failure left behind. **The message says what it did and what it did not
  touch** — a "clear data" button that leaves a person guessing whether their files are still there
  is a button they are right not to press. It never touches the trash, favourites, hidden items,
  albums, settings, or a single one of the user's files.
- **Show-source and show-confidence toggles.** Both were printed unconditionally on every card. On
  by default: a recovered file that does not say how it was found is asking to be trusted blindly.

### Where Phase 3 stands
Its list is now **empty except for incremental rescan**, which needs a per-folder record of what was
seen and when — a piece of work with its own plan rather than an item.

## [1.45.0]
Performance: pausing on a low battery and on a warm device — and saying so.

### Added
- **Pause when the battery is low**, from the platform's own `ACTION_BATTERY_LOW` broadcast.
- **Pause when the device is warm**, from the thermal status Android 10 publishes. The honest reason
  is not politeness to the phone: a hot device throttles, so a scan that keeps going runs slower
  **and** makes it hotter. Below Android 10 there is no signal to read, so nothing is claimed.
- Both are off by default, and both **wait rather than cancel** — a paused scan resumes where it
  stopped.

### The reason now reaches the screen, which it never did
`pauseReason()` has existed since v1.37.0 and **nothing called it**. A scan that waits and does not
say why is indistinguishable from a scan that has hung, so the promise that the reason would be
shown was never kept.

It is a `StateFlow` now rather than only a function, and that is not a detail: a scan that is waiting
**cannot emit anything** — it is suspended inside the gate — so without a flow of its own the screen
would have no way to say why nothing is happening. The Recovery screen shows it as a banner.

**A note on how this was missed.** The v1.41.0 release verification checked the APK for the string
and reported it "FOUND". It was — the string was there. **Finding a string is not finding a path**,
and that is a weaker check than it looked.

## [1.44.0]
UI customisation: the default tab, compact cards, and haptics that are off until asked for.

### Added
- **The Recovery screen opens on the tab you chose**, and tapping a tab during a visit sticks for
  that visit. The default is a starting point, not something that overwrites a choice made a moment
  ago.
- **Compact result cards** — more columns, and the size line goes. The name stays, because that is
  how a file is recognised at all.
- **Haptics, off by default.** There was **no haptic feedback anywhere in the app** before this.
  Every call site goes through one `Haptics` helper rather than reaching for the platform, so a new
  screen cannot buzz regardless of the setting — a phone that buzzes without being asked is a phone
  people turn things off on.

### The docs checker needed fixing, and that is the interesting part
It flagged `compactResultCards` and `hapticsEnabled` as read by nothing, when both were wired. The
rule treated **every** file under `ui/` as rendering, and a view model is not: it reads a setting and
decides what the screen should be.

It now counts every read in a `*ViewModel.kt`, and still requires a read that **drives a decision**
in a screen — because `checked = settings.x` is exactly how a decorative switch looks, which is the
defect the rule exists for. Verified both ways: it passes the real tree, and it still catches a
simulated switch that is rendered and never read.

### Fixed
The haptics helper was created in the top-level composable and used inside `CompletedPane`, which
cannot see it. CI caught it; made where it is used.

## [1.43.0]
Thumbnails: quality, size, the files the rule had no branch for, caching, and building them during a scan.

### Added
- **Preview quality** as Low / Balanced / High / Custom — a preset over the pixel size, the same
  shape as the performance mode and the signature depth, because *"Balanced"* is a decision a person
  can make and *"512"* is not.
- **A branch for files identified only from their bytes.** `ThumbnailPolicy.allows` answered for
  those using an extension nobody should trust, so their tiles fell back to an icon even when the
  bytes said they were pictures. The signature check now comes first, and the recovery grid says
  which results those are because the engine already records `RecoverySource.SIGNATURE`.
- **Caching, applied per request.** Coil's loader is a singleton built at startup, so a setting that
  only took effect after a restart would look like a switch that does nothing. `CachePolicy.DISABLED`
  is immediate — and both grids honour it, not only the one that was wired first.
- **Building previews during a scan**, bounded by the same policy that governs display and waiting at
  the same pause gate. A scan that ignored the battery setting while pre-warming would be worse than
  one that warmed nothing.

### The checker caught the half-done part
`tools/check_docs.py` **failed the build** because `generateDuringScan` was stored, exported and
shown while nothing read it — which is the exact defect v1.37.0 removed five of. The pre-warm was
the missing reader, and it exists now.

## [1.42.0]
The detection block: what is provably wrong with a file, and what it actually is.

### Added
- **`MediaIntegrity`** — pure, and deliberately conservative. It flags only what the bytes can
  prove: **empty**; a JPEG that does not end `FFD9`; a PNG with no `IEND` chunk; and a name that
  disagrees with the contents. **For a format with no end marker nothing is claimed**, because a
  complete MP4 and a truncated one cannot be told apart — and a "corrupt" label on a good file is
  worse than no label, since it makes a person distrust something that is fine.
- **Half the tests are about what it must NOT flag**: an unremarkable `.txt`, a `.zip`, a file with
  no extension, an extension nobody catalogues.
- **`MediaInspector`** reads a file's ends and its EXIF, bounded, with every failure a null rather
  than an exception — a scan must not stop because one file could not be opened.
- **EXIF is the date and the camera. Never the location.** `ExifInterface` hands over GPS
  coordinates as readily as a date, and reading them into a result card would put where somebody was
  onto a screen they might share. A recovery tool has no use for it. **A deliberate omission, not an
  unimplemented one.**
- **Signature depth** as Safe / Balanced / Extended / Custom — a preset over the byte count, the
  same shape as the performance mode, and real because the three scanners and the inspector all read
  `effectiveSignatureBytes` now.
- The result card shows the issues **with their evidence** — *"a JPEG must end with FFD9 and this
  does not"* — and says when a file checks out, rather than only speaking up when something is
  wrong. A null report means the check was switched off, which is not the same as "nothing wrong",
  so it says nothing.

### A contradiction found by walking the logic before writing the tests
My doc said an unrecognised header is reported only for a **known media extension**. The code
checked "not in a list of forty opaque extensions" — so `xyz` was flagged, which is the opposite.
The list went: the caller now says whether the extension is one the app catalogues, because that is
`FileTypeRegistry`'s question and should not have a second answer in another file.

The fifteen test cases were then run against a **faithful mirror of the logic in Python** before CI
saw them, so the doc, the code and the tests were checked against each other rather than trusted.

### Fixed
`ByteArray.take()` returns a `List<Byte>`, not bytes — the Kotlin collection extension, not an array
slice. CI caught it; the range is copied instead.

## [1.41.0]
The three open decisions, dealt with — two of them done, one half of a decision taken.

### 1. The trash purge — done
`purgeExpired` has existed since the trash did, and it ran from **one** place: the Trash screen's
`init`. So a library nobody opened never expired, and the promise that a deleted file goes after
thirty days was kept only for people who went looking for it.

A daily WorkManager job now does it. `KEEP` rather than `UPDATE`, so a purge is not pushed back by
every app start — one that keeps being rescheduled by a person who opens the app often would never
run. A day's grace before the first run, so an app opened immediately after install does not spend
that moment deleting files. And the screen still purges on open, so the list is clean the instant it
is looked at.

### 2. Part D — the hand-off half, completed
The capability matrix has named the reason for `.mpg`, `.wmv`, `.swf` and the rest since v1.29.0,
and the capability screen shows it. But **tapping one of those files was the one path that still
handed over silently**. It now says why first — *"Windows Media — the ASF container has no parser on
Android — opening it with another app."* — because being told in advance rather than after the
attempt is the whole point of that screen.

**The FFmpeg half is not mine to take.** Bundling it means accepting LGPL/GPL obligations and a
build cost. Handing off is implemented and documented; the other half needs a decision, not a
default.

### 3. "Usage permission" — implemented, as the reading I believe was meant
The Access screen used to say *"Logging is off. Turn on 'Anonymised usage data' in Settings →
Diagnostics"* — a strange thing for the one screen that displays the data to say. It now offers the
consent **itself**, as a switch with the explanation beside it: counts, timings, device model and
Android version; never a file name, a path, a search term, or anything from the photos; nothing
uploaded.

It is the **same** setting, not a second one. The change is recorded **before** it is applied when
consent is being withdrawn, because after it is applied nothing more can be written — and after,
when it is being granted, for the same reason in reverse.

**If Android's `PACKAGE_USAGE_STATS` was meant instead, that is a different thing and it has not
been touched.**

### Fixed on the way
A regex-shaped patch put the new callback in the middle of a lambda instead of at the end of the
argument list. The Kotlin checker passed it — it is balanced — and it was caught by reading the
call site. Repaired by hand.

## [1.40.0]
The export workflow: confirm, choose, name, and ask when a name clashes.

### The problem
The exporter worked. The person could not control it: no confirmation before a copy started, no way
to keep the folder it used, no way to say what a file should be called, and a name clash was
resolved by a rule the app had picked in advance.

### Added — five settings that do something
- **Ask before exporting** — a dialog naming what is about to happen and where, so an accidental tap
  cannot start a copy.
- **Ask for the folder each time** — off reuses the last folder, and still asks on the first export,
  because there is no way to export without a destination and inventing one would be worse.
- **Keep the original file name** — off prefixes `recovered-`, so the name says where the file came
  from, which is the one thing the name cannot otherwise tell you.
- **Keep export history for** — the retention the settings describe.
- **"Ask me each time"** as a duplicate-name policy. This needed a **third outcome**,
  `NeedsDecision`, which says the exporter is not allowed to choose rather than guessing — and the
  answer applies to that run only, because the person was asked about *these* files in *this*
  folder, not about how exports should behave from now on.

### The remembered destination is deliberately not a setting
A tree URI is not a preference, it is a **permission grant**, and the settings export is a file a
person can share. Putting it in `AppSettings` would put a grant into that file — the one thing the
master prompt says must never be exported. It lives in its own store, the grant is taken with
`takePersistableUriPermission` so it survives a restart, and a test asserts the settings export
carries no `content://` URI. The constraint and the design agree instead of fighting: the
settings-export completeness test requires every field of `AppSettings` to be listed, and a field
that must not be listed would have broken it.

### A dead function found on the way
**`purgeHistory` existed with a 90-day default and nothing called it**, so the export history grew
for ever — while a setting was about to be added to control a purge that never ran. It runs now,
when the Recovery screen is opened, and the retention is clamped so a hand-edited settings file
cannot make it delete everything or keep it for ever.

### Not done, and said rather than faked
- **Preserving original timestamps.** SAF gives no way to set a document's modified time, so a
  setting for it would be a control that does nothing — which is what this project spent two
  releases removing.
- **A default export format.** The export writes the original bytes; an "alternative" format would
  mean transcoding, which is not implemented.

### Fixed
A Kotlin local function cannot refer to a `val` declared below it, and my export helpers sat above
the pickers they use. CI caught it; moved below both.

## [1.39.0]
A scan you can run, and a screen that says what it will do first.

### The problem
Settings described a scan and offered no way to start one. The two questions worth asking before a
scan — **what will it look at**, and **what will it miss** — could only be answered by reading the
code. The master prompt asked for both: a button that runs a scan with the current configuration,
and a scope shown before it starts.

### Added
- **`ScanScopeBuilder`** — the honest core, and pure so it is tested rather than trusted. The scope
  is built from the settings **and the device**: the folders actually granted, whether this Android
  version will open `Android/media`, and whether All files access is held. A scope built from the
  settings alone would promise to walk folders nobody granted.
- **`ScanScopeScreen`** — the methods, the file kinds, the granted folders, the limits, and then
  **what it will not do**, as notes: no method switched on, no category switched on, folder scanning
  with nothing granted, `Android/media` out of reach, no All files access, signature detection off.
  **The lines that are not included are drawn as deliberately as the ones that are** — a scope that
  only listed what it would scan would be a sales page.
- **The button runs it.** Quick scan wins when both methods are on, because it is the cheap one — a
  documented order, tested, rather than a guess. The scan screens take a `start` argument so the
  button runs the scan rather than describing one, and the route pattern is built by a function so a
  bare string cannot drift from it.

### Fixed on the way
- **The test that claimed to check every route against the feature map used a hardcoded list**, so
  adding a route left it silently unchecked — the exact failure it was written to prevent. It now
  reads the routes from `Routes` itself, by reflection, and asserts there are more than twenty so a
  broken reflection cannot pass it.
- The scope passes the `ScanTarget` enum rather than its name, so the caller cannot compare the
  wrong string.

### Two mistakes in my own tests
Both mine, not the code's, both found by CI rather than by reading: a test expected one file kind
while the other categories were still switched on; and it expected an exact set from something that
is a set, so it asserted an order nothing promises. A third was named "twice for two different
reasons" and asserted one thing. Renamed.

### A document was destroyed, and it is worth writing down
While correcting the plans in v1.38.0, one of my scripts bound its output path to the wrong variable
— `p` where `c` was meant — and wrote the **contributing guide over the roadmap**, replacing it
entirely. Nothing noticed for two releases, and the docs check of the time did not catch it because
the version in the file still matched: the file was a *different document*, not a stale one.

It was recovered from the `v1.37.0` tag and every correction re-applied by a script that asserts
what it is writing to before it writes.

**`tools/check_docs.py` now checks that each document is the document it claims to be**, by its
title. It is the cheapest possible check for the most destructive possible mistake, and it fires on
a simulated overwrite.

### The docs check earned its place twice more
It **failed the build when the version was bumped without updating `ROADMAP.md`'s header** — exactly
the drift it was written for; the fix was one line. And in this release it also caught the
destroyed roadmap.

## [1.38.0]
The plans are corrected, and a check now keeps them corrected.

### Why this release exists
The audit that found the five decorative switches found a second thing: **the documents described a
different project**. `ROADMAP.md` said "Phase 3 is complete" while roughly half of it had never been
built. Its header said v1.5.1 and targetSdk 35 while the build was v1.36.0 and 36. `README.md` said
35 in one place and 36 in another, in the same file. `PLAYER-AND-READERS-PLAN.md` still marked Parts
B, C, E and F as unstarted after all four had shipped.

None of that was a coding mistake. It was the **root cause** of the first one: §5 of the roadmap had
listed *"confirm each mode really changes behaviour"* as an open item, the item was never done, and a
later section of the same document declared the phase complete. Nothing was checking, so the claim
became the record.

### The plans, corrected against the code
- **`ROADMAP.md`** — the header and §1 now state v1.38.0, targetSdk 36, 63 settings and the current
  quality bar. **§5 is rewritten** with the verified counts: of the 59 items in the settings master
  prompt, ~24 done, ~27 missing, 3 present under another name, 4 partial — and every verdict checked
  at file and symbol. The false "Phase 3 is complete" is struck out where it was written, with a note
  saying it was not true. §6k's "still pending from Part E" (shipped three releases later, per §6n)
  is corrected. §6g's Supabase entry now records that it was **removed** in v1.36.0 and why. §6's
  "still open: instrumented tests need an emulator" is corrected — that job exists. §8 marks the
  decisions that were answered and lists the three that are genuinely open. §9's release table, which
  stopped at v1.9.0 and had been wrong for twenty-eight releases, is replaced by a pointer to this
  file. Three headings that were each used twice are now unique.
- **`PLAYER-AND-READERS-PLAN.md`** — Parts B, C, E and F are marked shipped. Two dangling references
  to parts that do not exist ("planned, Part 5", "planned (Part 7)") now name the releases that built
  them. The contradiction about presentations is resolved: PPTX **is** read since v1.33.0, and the old
  binary `.ppt` is still handed off — a different format, not a different container. Part D is left
  marked as the open decision it is.
- **`README.md`** — the SDK line said 35 in a file whose other line said 36. And the sentence *"every
  switch changes real behaviour — there are no decorative toggles"* now says that it **was false for a
  while**, names the five, and points at the check that keeps it true.

### `tools/check_docs.py`, and it runs in CI
A check that the documentation's factual claims still match the code:
- the version and versionCode in the docs are the build's;
- `compileSdk` / `targetSdk` in the docs are the build's, in every document;
- **no setting that the Settings screen renders is read by nothing** — the audit's own check, automated;
- the log counts the docs quote (categories, levels, events) are the vocabulary's.

**It was wrong three times before it was right, and each time was found by testing it against a
deliberately introduced fault** rather than by reading it:
1. it counted **computed properties** as settings, so it called `enabledKinds` and `acceptsSize` dead;
2. it counted a setting's **own declaration line** as a read of itself, so a deliberately decorative
   setting **passed** — the check was blind to the exact bug it was written for;
3. it flagged `hasSeenDefaultGalleryPrompt`, which is app state rather than a setting, because a
   negated read in a plain assignment did not look like a decision.

The rule is now narrow and written down in the file: a **control the screen shows that nothing acts
on**. It fires on a rendered decorative setting, on a stale version and on a stale SDK, and it is
quiet on the clean tree.

**`tools/check_kotlin.py` had never run in CI either** — it only ran when someone remembered. Both
checkers now run as a step before the build, which is where a check that has caught real bugs (a
missing import, two companion objects in one class, an event declared with no call site) belongs.

## [1.37.0]
The five switches that changed nothing now change something.

### The finding
An audit of the whole app found five settings **declared, persisted, written into the export, and
rendered as real controls — and read by no logic anywhere**. Not decorative in appearance;
decorative in effect, which is worse, because a person changes one and believes something happened.
That is a direct breach of this project's own rule, stated twice in the Advanced Settings master
prompt: *"Every setting must affect real application behaviour"* and *"No fake switches."*

### Fixed
- **Theme.** `MainActivity` called `ProGalleryTheme { … }` with **no argument**, so the app was
  always whatever the system was. It now passes `ThemeMode.isDark(systemDark)`, a pure function, so
  System / Light / Dark reach the theme.
- **Performance mode** was a label. It is now a **preset that writes real numbers**, with the
  **Custom** value the master prompt always asked for:
  | Mode | Concurrent tasks | Batch size | Background |
  | --- | --- | --- | --- |
  | Battery saver | 2 | 250 | pauses |
  | Balanced | 4 | 500 | keeps scanning |
  | Performance | 8 | 1000 | keeps scanning |
  | Custom | the controls below | the controls below | the controls below |

  Choosing a mode writes those numbers, so the effect is visible in the rows underneath; touching
  any of the three controls switches the mode to Custom, because that is what editing a custom
  option means.
- **Max concurrent tasks** now bounds something real: the dispatcher the three scanners run on, via
  `limitedParallelism`. A scan's parallelism is the honest reader for "how many files at once".
- **Batch size** now reaches the index read.
- **Pause when backgrounded** now pauses. A `ScanPauseGate` the scanners wait at, beside their
  existing cancellation points: it **waits** rather than cancels — a paused scan resumes where it
  stopped — and it can **say why**, because a scan that looks stuck without a reason is
  indistinguishable from one that has hung. The battery-saver preset turns it on, which is what
  makes it a battery saver.

### Removed
- **`experimentalMethodsEnabled` is gone.** It gated features that do not exist, and its own
  subtitle admitted it — *"Enabling it does not unlock anything unverified."* The master prompt says
  a toggle must not unlock what has not been implemented and tested, so it goes until there is
  something behind it.

### Why it cannot happen again the same way
The raw fields are now read only by `effectiveConcurrency`, `effectiveBatchSize` and
`effectivePauseWhenBackgrounded`, and every consumer reads those. Reading a raw field cannot quietly
reintroduce the bug, and the mode and the individual controls cannot disagree about what the
scanner is doing.

### Tests
`PerformanceModeTest` pins the presets: every mode stands for real numbers, Custom stands for none,
the saver is the gentlest of the three and the only one that pauses, and a preset overrides the
controls while Custom uses them. `ThemeModeTest` pins System/Light/Dark against a dark and a light
device.

## [1.36.0]
Supabase removed, at the user's request. The diagnostics stay; the upload path goes.

### Removed
- **The whole upload path**: the uploader, its worker, its scheduler, the watermark, the payload
  builder and the "send diagnostics to the developer" switch.
- **The build-time endpoint and key**, and the two repository variables that fed them, so the
  release workflow no longer passes anything about a server.
- **The database migrations and the ingest function**, because there is no database to apply them
  to.
- **The settings export field**, so a backup no longer carries a setting that no longer exists.

### Why
The project could not be used. The organisation is at its **two-free-project limit**, so the log
project could not be brought back from inactive — restoring it was refused. Rather than free a
slot by touching another project, the path goes. The user asked for it to be removed entirely.

### What stays, which is the part that was always the useful half
- **The local log**, and everything built on it: the live view on the Access screen, the filter by
  level, event, device code and free text, the four export formats, and the email hand-off.
- **The 12 categories, 6 levels and 30 events** from v1.35.0, all still emitted and still written
  locally. Nothing about the app's own diagnostics ever depended on a server.

### Two events the removal would have orphaned
Both were emitted **only** by the uploader, and both would have been left declaring themselves with
no call site — which the checker rule caught immediately, twice:
- **`consent_changed`** moved to the usage-data switch, which is now the only consent there is.
- **`trace_marker`** moved to each batch of the library read, which is a real step inside a real
  operation — which is what a trace is for.

### Also
- The usage-data row's subtitle no longer hedges about "unless you turn on sending below", because
  there is no sending. It says what is true: nothing leaves the device.

## [1.35.0]
All twelve log categories — and every event the app declares now has a call site.

### The finding this is built on
- **The vocabulary declared thirty events and the app emitted four of them.** The rest were
  promises with nothing behind them, and nothing said so: the log simply never contained them. That
  is the same failure the 21 October log showed, in a different place — a log that looks complete
  and is not.
- **A checker rule now fails the build when a declared event has no call site**, the same shape as
  the rule for a private function nothing calls. Writing it found that **it had never run once**:
  its path was built from the wrong root, so it was silently decorative. It now fails loudly if it
  cannot find its input, rather than passing by doing nothing.

### Added — the twelve categories
| Category | What it holds on a phone |
| --- | --- |
| application | the session, and every screen view |
| error | crashes, a file that would not play, an error on screen |
| security | access asked for, refused, and retried |
| performance | library load time, the media query, slow operations |
| user activity | navigation, and the friction signals the app can honestly see |
| infrastructure | what the process found on the device — memory, processors |
| network | the update check: the call, its latency, and its failures |
| database | the database opened and upgraded, and any read or write that failed |
| audit | a setting changed, data exported, data deleted |
| analytics | counted in the usage store, deliberately not logged — the standard says to keep them apart |
| background | the memories reminder and the log upload, start to outcome |
| trace | the steps inside an operation |

The categories written for a service are given the honest thing they hold on a phone rather than
left empty: there is no deployment to log from an app and no administrator to audit.

### Added — the two missing severity levels
- **`trace`**, for the steps inside an operation, which is what `debug` was being asked to do.
- **`fatal`**, for a failure the app cannot continue past — not the same as an error it can report
  and survive. Retention drops a trace before a debug and keeps fatals alongside errors.
- The `category` travels as its own field and its own column, so the database can be asked for
  "everything about the database" without parsing JSON.

### Removed, on purpose
- **`rage_tap`, `dead_click` and `scroll_depth` are gone from the vocabulary**, and `anr` with
  them. Each needs instrumentation that does not exist yet — counting taps on an element that did
  nothing, reading the scroll position of a list that has no list state, a watchdog with real
  battery cost. An enum entry is a promise, and these would have been a false one. They go back in
  when their call sites do.

### Also
- **The settings audit is a diff of the export, not of the data class.** The export already names
  every field and is guarded by a test that fails when a new setting is added without being listed,
  so the audit cannot quietly stop reporting a field added later — which is how an audit trail goes
  stale while still looking complete.
- A failed index write is recorded as a database error **without its message**, which is where a
  path or a file name would be.

### Known state
- **The database migration for these events (`0005`) is in the repository but not yet applied to
  the live project** — the connection to it was timing out. Until it is applied, an upload carrying
  a new event name is refused by the closed `event_name` constraint. Nothing is lost: the uploader
  stops at the first failed batch and does **not** advance its watermark, so those events stay
  pending and go out on the next run once the constraint knows them.

## [1.34.0]
One player. Audio and video were two screens with two players; now there is one.

### Changed
- **`MediaPlayerScreen` replaces `VideoPlayerScreen` and `AudioPlayerScreen`** — one screen, one
  route, one view model, one player. They had already drifted, and that drift is what made the
  same codec play as video and fail as audio: two players meant two places for the decoder
  fallback to be missing from.
  - What still differs is what should. **Video** fills the screen in a pager, so swiping moves to
    the next file in the order of the grid it was opened from, with trim, advanced edit and share.
    **Audio** shows the file with its controls and offers edit, cut, share, move, copy, info and
    delete — because an audio file is a file you manage, not only one you play.
  - Everything else is shared, because it is the same problem: the item, the queue, the position
    memory, the failure reasons, and the snackbar that offers what can actually be done about them.
  - Which surface to show is decided by **the file**, not by the route that opened it, so the two
    cannot disagree — and the editor the trim button opens follows the same rule.
  - **The view model does not own the player, and that is the load-bearing decision.** The player
    has to be attached to a `PlayerView`, released with the composition, and read for its position
    as the screen goes away — all of which are the screen's to do. What lives in the view model is
    everything that is not the player. The audio path's 500 ms position poll moved to the screen
    with it, so there is one loop for both surfaces instead of one per screen.

### The tooling, because a missing import cost a round trip
- **The checker now catches a project function called without its import.** It only looked at
  capitalised type names, so `sameUri` and `runCatchingCancellable` — both top-level functions in
  this project — could be called with no import and nothing noticed until the compiler did.
  - The new rule had **two blind spots of its own**, both found while testing it and both fixed: a
    declaration with a type parameter (`fun <T> foo(`) was not collected, and a call written as a
    trailing lambda (`foo { ... }`) has no parentheses to match. It is verified four ways: it fires
    on both real cases, a proper import silences it, and the clean tree passes.
  - It also made the checker **read the tree once** and share that between rules. The new rule on
    its own pushed a run past a minute, which is slower than the build it exists to save time on;
    it takes about fifteen seconds now.

## [1.33.0]
Presentations open too — and the earlier decision about them was wrong.

### Added
- **`.pptx`, `.pptm`, `.potx` and `.ppsx` are read.** In v1.32.0 I said a presentation is a
  different shape of document and that handing it off was better than half-supporting it. The
  shape turns out to be **the same one Word uses** — a zip with the words in `<a:t>` and the
  pretty-printing between the elements — so it gets the same reader, and the earlier call is
  reversed rather than quietly left standing.
  - **The order is the deck's, not the filenames'.** Slides are named `slide1.xml`, `slide2.xml`
    and so on, and they are *usually* in that order — which is exactly why reading by filename
    looks correct until a slide is reordered or deleted and the numbers stop matching the running
    order. `presentation.xml` lists the slides in reading order and the relationships file says
    which file each one is. The filename is the **fallback**, for a file missing its own
    description, not the rule — and the test builds a deck where `slide1.xml` is the *second*
    slide, so a reader that guessed would be caught.
  - Each slide is labelled, so the text reads as a deck rather than as one long page.
- **The old binary `.ppt` is still handed off**, deliberately. It is a completely different
  container — not a zip — and the capability screen says so, which is the honest answer rather
  than claiming a format the app cannot open.

### Also in this release
- The **capability screen** changed with the code, as it is built to: presentations now report
  that they open here, derived from the same table the reader uses.

## [1.32.0]
Word, Excel, OpenDocument and ePub files open in the app instead of being handed away.

### Added
- **A reader for the documents that are zip containers.** `.docx`, `.docm`, `.xlsx`, `.odt`, `.ods`,
  `.odp` and `.epub` now open and read. They were previously listed and then sent to "open with
  another app", which often found nothing — and the text reader **deliberately refused** them, for
  the reason its own comment gives: a `.docx` is a zip, and showing its bytes shows compressed
  noise. So they get a reader that understands the container.
  - **A bounded unzip.** The cap is enforced *while reading*, not by trusting the size the archive
    declares — which is exactly what a zip bomb relies on. A file that would expand past the limit
    is refused with a reason rather than half-read.
  - **A hand-written XML tokeniser**, because `XmlPullParser` lives in `android.jar` whose stubs
    throw in a JVM test. A document reader that is only tested on a device is a document reader
    nobody tests, so this runs anywhere and the formats are tested against containers the tests
    build themselves.
  - **Four walks over that tokeniser**, differing only in what counts as a paragraph: `w:p` in
    Word, a row in a spreadsheet, a chapter in an eBook, `text:p` in OpenDocument.
  - **Shared strings are resolved** in a spreadsheet, so a cell reads as the word it holds rather
    than as the index into the string table — the classic spreadsheet-reader bug, and the reason
    that case is tested on its own.
  - **An ePub is read in spine order, not file order**, so chapter two does not appear before
    chapter one when the filenames happen to sort that way.
  - The **capability screen** now says these open here, because it derives its answer from the
    same table the reader uses — the verdict changed with the code rather than being written down
    twice, and the test that asserted "office files have no reader here" was updated with it.

### What the tests caught, in order
1. **A real bug in tables**: the marker for "where this cell's text starts" was set *before* the
   tab was appended, so the check for content saw the tab itself and put a space after every one —
   `Name\t Age` instead of `Name\tAge`.
2. **A real bug that only a pretty-printed document shows**: Word files are indented, and the
   whitespace between tags is not part of the document. It came through, so a formatted document
   read as its own layout. Text is now taken from inside `<w:t>`, where whitespace is significant,
   and only non-blank text from outside it — and **both directions are tested**, because the fix
   could not simply be "drop whitespace": a run that is a single space is a real space between two
   words.
3. **An invariant worth keeping**: a format that simply opens must carry **no reason**, or the
   screen shows an explanation on every line of a list where nothing is wrong.
4. And two of my own test mistakes, which is the honest half of the count: a self-closing tag
   count that forgot the outer element, and an assertion that the tokeniser joins text around a
   comment when it flushes before every tag.

## [1.31.0]
A video picks up where you left it — and, more importantly, knows when not to.

### Added
- **Position memory.** Returning to a video resumes where it was left, with the two rules that
  make resuming not annoying, both pinned in tests at their boundaries:
  - **A position near the end starts over.** A video watched to within fifteen seconds — or to
    95% of its length — is finished, and restoring it means pressing play to watch the titles.
  - **A position barely past the start is not worth restoring.** Ten seconds in is not "in
    progress", and restoring it makes every file look half-watched.
  - An **unknown duration** trusts the position: a stream that has not reported its length yet
    still resumes, because refusing would mean never resuming anything played from a network.
- **The distinction between *finished* and *barely started*, which is the part that matters.**
  Finishing a video **forgets** the position so the next open starts from the beginning — but
  opening a file you are twenty minutes into and closing it after three seconds must not throw
  those twenty minutes away, so a too-early position is **left alone** rather than overwritten.
  A plain upsert would have got this wrong in one direction or the other.
  - Positions are saved **on pause**, **on leaving the screen** and **when swiping to the next
    video** — on the view model's scope, because the most important save happens as the screen is
    going away and a coroutine started from the composition is cancelled at exactly that moment.
  - Room goes to **version 6** with a migration the coverage test checks against the entity, and
    the table is bounded so a convenience does not outgrow its usefulness.

### What the build caught
- **Hilt refused to build the repository**: the new DAO had no provider. Every other DAO on the
  database has one and this was the only one missing — so the whole set was checked afterwards
  rather than just the one, and all nine now have a provider.

## [1.30.0]
The diagnostics log can be sent to a server — off by default, and with the database doing the
enforcing.

### Added
- **A Supabase project for the log** (`progallery-logs`, Mumbai), with the schema, the ingest
  function and the retention job in the repository as versioned migrations.
  - **The table is append-only through the API.** There is an INSERT policy and deliberately no
    SELECT, UPDATE or DELETE policy, and `anon` holds only the INSERT grant. So a leaked
    publishable key — which ships in every copy of the app — can add junk and **cannot read one
    row of anybody's log**. That is the whole security model, and it is what makes shipping a
    publishable key safe.
  - **The closed vocabulary is enforced by CHECK constraints, not by convention.** An event name
    or a log level outside the list is refused by the database. A column that accepts free text is
    a column that will eventually hold a filename.
  - **A guard rejects what must never be stored**, on both sides. The client redacts; the database
    repeats it, because a client is a thing you ship once and cannot fix. A detail key named
    `filename`, a value that is a path, a `content://` URI, an email address, a phone number or a
    media extension is refused — and every one of those was verified by attempting it.
  - **The salted hash is computed by the database**, from a salt in a table with row level
    security on and **no policies at all**, so nothing reaches it through the API. A hash the
    client computes is a hash whose salt the client holds, and that protects nothing. A trigger
    fills it, so a client that forgets still gets one.
- **Sending is opt-in, and off by default.** The local log works fully without it, so nothing is
  lost by defaulting to no — and consent that is assumed is not consent.
  - Turning the switch **off cancels the work that is already queued**, not just new work. A
    consent gate with a job still running behind it is not a consent gate.
  - The watermark only advances once the server has accepted a batch, so a crash mid-upload
    cannot lose events.
  - The endpoint and the key come from the build, never from the source: a key committed to a
    repository is a key that has to be rotated.
- **The settings export** now carries the new setting, which the export guard demanded — without
  it, the choice would have been silently lost on a backup and restore.

### Fixed — what the security advisors found, which reading the SQL had not
- **`prune_log_events` was callable by `anon`.** It is `security definer` — that is what lets it
  delete past row level security — and it takes the retention window as an argument. So anyone
  holding the publishable key could call `prune_log_events(-99999)` and **delete the entire
  table** in one unauthenticated request. The window is a parameter and the function was exposed;
  those two facts together were the hole. Execute is now the service role's alone.
- **Two `security definer` functions had a mutable `search_path`**, which is a privilege
  escalation: a caller who can create a function earlier in the path can have it resolved instead
  of the real one. Both are pinned now.
- The remaining lint is deliberate and documented: `log_hash_salt` has RLS on and no policies,
  because the intent is that nothing reaches it through the API at all.

### Found by testing rather than by reading
- **`page_view` was refused.** The table has a `platform` column that accepts `web`, so it was
  built to hold both sides — but the vocabulary had only ever learned the Android names, so half
  the rows it was designed for could not be written. Both platforms' names are now allowed, and
  the two that differ (`rage_tap`/`rage_click`, `api_request`/`api_call`) keep both spellings
  rather than renaming one side and invalidating what is already written.

### Not verified, and said plainly
The HTTP path — the app to the function — could not be exercised from the build environment,
because its egress proxy does not reach `*.supabase.co`. What **was** verified is the database
side, directly: a valid event stores a 64-character hash, and a file path, an email address, a
forbidden key, an out-of-vocabulary event name, an out-of-vocabulary level and a malformed device
code are each refused. The end-to-end call is the one thing left for a device to confirm.

## [1.29.0]
One place builds a player — and the app can finally answer "can I open this?" before you try.

### Changed
- **A single `PlaybackEngine`.** There were four players and two editor previews, each building its
  own `ExoPlayer`, and they had already drifted: **only the video player fell back to a software
  decoder.** So a codec this device lacks would play as a video and fail as the same audio — which
  reads as "the app is broken" rather than "this file needs another app". Every player now gets
  the same fallback, and the drift cannot come back, because there is nowhere else to build one.
  - The data source is a parameter rather than an afterthought: the WebDAV player's sign-in rides
    on it, and that is the only difference between it and the others.
  - The **editors' preview players** are included. An editor preview is still a player, and it had
    the same missing fallback.
- **Each player is told what the file is when the app is sure, and left to sniff it when it is
  not.** Sniffing is good and usually right; the hint removes the guess for a container with an
  ambiguous header and for an address with no extension at all. It is never guessed — an uncertain
  mapping returns nothing and the player sniffs, which is the honest default.

### Added
- **Format support**, a screen that answers the question before the attempt: every format the app
  knows, grouped by whether it opens here, opens with a caveat, or needs another app — **with the
  reason**. Being told that `.wmv` needs another app is a different experience from a file that
  fails when tapped.
  - It is generated from the same tables the players and the readers use, so it cannot promise
    something the app does not do.
  - Searchable by extension, by the verdict's own wording and by the reason — someone looking for
    ".wmv" and someone looking for "Windows Media" are asking the same question.
  - It already found two real gaps while being built: `m3u8`/`m3u`/`mpd` were missing from the
    registry although the app plays them, and several audio formats in the registry (`ape`, `wma`,
    `ra`, `caf`, `dsf`, tracker modules) have no Android decoder at all.

### What the compiler and lint caught, in order
1. `ExoPlayer.Builder` has **no three-argument overload in the order I assumed**. It is
   `(context, renderersFactory, mediaSourceFactory)` — the two are easy to write the wrong way
   round, and the reference documentation settled it rather than a guess.
2. Media3 marks the builder, the renderers factory and the media source factory as **unstable
   API**; the opt-in is now on the one function that touches them rather than over the whole file.

## [1.28.0]
The log, live, on the access screen — and the half that was missing.

### Added
- **A live log on the Access screen.** It reads from the store's own flow, so an event appears as
  it is written — there is nothing to refresh. That matters most here: the question this screen
  answers is "why is this stuck", and the answer is the sequence of events, which is no use a
  minute late.
  - **The filter** — by level with a count beside each, by free text, and by device code (a
    prefix is enough, because a code is copied by hand).
  - **Export** in four formats through the system picker, so the file lands where the person
    chooses rather than somewhere they have to go looking.
  - **Email**, which composes in **Gmail when it is installed** and falls back to a chooser when
    it is not. No recipient is filled in: there is no support address defined for this project —
    the contact is an Instagram handle — and inventing one would send a log to an address the
    person did not choose. The subject, the body and the attachment are filled in.
  - The body says what the log contains **and what it does not**, because a log sent without that
    is a log sent with an apology.
- **`access_fix_result` — the event whose absence made the 21 October log useless.** 71
  `access_fix_requested` entries and not one result, so the log proved a loop without saying what
  was looping. Every fix attempt now writes **both ends**: the request with an `attempt_id`, and
  the result with the same id, plus `state_before`, `state_after`, `action_taken`, `outcome` and
  `media_item_count`.
  - **The open attempt is kept on disk**, because the app can be killed while the user is in
    Settings — which is exactly when the answer arrives. An attempt held only in memory would be
    lost in the one case this exists for.
  - **`media_item_count` is new too**: the snapshot now counts the photos and videos the app can
    see. "Granted, 412 items" and "granted, 0 items" are the same permission state and completely
    different problems.
- **The outcome rule, stated once**: success when the access is granted now and was not before;
  no_op when the state is exactly as it was — the most useful thing to know about a loop; failed
  when it moved and still is not granted. All sixteen state pairs are tested.
- **The logging schema, in code**: the install's readable code, a closed event vocabulary, the
  JSON line codec, and the filter. `PG-XXX-XXX`, from a Crockford alphabet that drops I, L, O and
  U, so a code read aloud cannot become a different one — random, and not derived from the device,
  the account or the phone number.

### The parts that are easy to get wrong, and are tested
- **CSV quoting.** One detail value containing a comma shifts every column after it and the file
  still opens. A value with a comma, a quote or a newline is quoted with inner quotes doubled, and
  a test counts the fields of every row to prove the columns line up.
- **Retention that keeps the error.** The standard asks for 100% of errors and sampled info; on a
  device the honest reading of that is the *order things are dropped in*. When the file is over
  its limit the oldest debug and info lines go first, so fifty scroll events cannot push out the
  crash that explains the session.
- **A second `AccessState` enum was avoided.** The app already had one; a rival copy for logging
  would have been two parts of the app disagreeing about the same idea. The schema's wire names
  are a mapping beside the log instead. The one gap is stated rather than papered over: the schema
  names `permanently_denied`, and the app does not distinguish it from `denied` yet.

## [1.27.0]
Stickers, shown so they can actually be seen — and SVG, which the app now draws itself.

### Added
- **A Stickers screen.** "There are many stickers but none of them showing anything" had **three
  separate causes**, all fixed: they live in an app folder the system's index does not hand over,
  so they were never in the grid; they were drawn on a flat colour, and a transparent sticker can
  be genuinely invisible on one; and a `.tgs` is not a picture at all. They are now read from the
  app's own index, drawn on a **chequerboard** — never the same colour as the artwork — and the
  formats with no renderer say what they are and offer another app instead of a blank tile.
  `Fit` rather than `Crop`, because cropping a sticker cuts off the outline that makes it
  recognisable.
- **SVG renders.** Android's platform decoder cannot draw a vector at all, so those files were
  listed and never shown. The app ships a decoder and draws them itself — making SVG the first
  format the app decodes rather than the platform, and the capability answer says so: `BY_APP`,
  deliberately distinct from `ALWAYS`.

## [1.26.0]
App media joins the gallery — the photos messaging apps keep are now in the grid, not beside it.

### Added
- **The app keeps its own index, and the gallery merges it into the system's rows.** This is the
  part that was missing: a scan could find a WhatsApp photo, but the grid only ever asked the
  system, so the photo was visible on one screen and absent from the one the user actually
  browses.
  - **Merged, not appended.** The two sources are interleaved by the same comparator, so a photo
    from this morning sorts where its date puts it. Appending would have put a photo taken today
    below a photo from 2014 whenever it came from a messaging app.
  - **Every sort order works**, not only the date ones: the app's index is small enough to hold,
    so it is ordered with exactly the comparator the grid is already using rather than with a
    second query per sort order — three more places for the ordering to differ.
  - **A file both sources know appears once**, and the system's copy wins, because it carries the
    row id that favourites, hidden items and the viewer's swipe list are all keyed on. Showing
    the app's copy instead would silently break those.
  - **"Show app media in the gallery"** is a real switch: off means the index is not read at all,
    so the grid goes back to exactly what the system reports.
  - The scan **replaces** the index rather than appending — a scan is a statement about what is
    there now, so a file the user deleted leaves with it.
- **A new table and a migration** (`indexed_media`, v4 → v5), purely additive: favourites,
  albums, hidden items, the trash and the recovery history all survive untouched.

### The part that was actually hard, and how it was found
- **The merge is a plain function, and its windowing is tested at every page boundary.** An item
  duplicated, dropped or swapped where one window meets the next looks like a stutter while
  scrolling, not like a bug — so the property that matters is checked directly: *paging through
  in small windows must produce exactly the list one big window would*, at every window size.
- **That test found a real ordering bug**, and a model of the algorithm written in Python
  confirmed it: the merge ran against a single batch of the system's rows, so when a batch ended
  it fell back to the app's index alone — and an item from the *next* batch, newer than one
  already emitted, arrived afterwards. The system's side is now read ahead until a window is
  fully determined, and the Python model was re-run to check the fix across window sizes before
  it was written in Kotlin.
- **Three of the new tests were wrong rather than the code**, and each is now corrected with the
  reason recorded: one fed the merge an unsorted list; one used a fake cursor that returned rows
  unsorted where a real query sorts; and two asserted *how the cursor is spelled* rather than
  that resuming continues the list.
- The un-merged paging source was **deleted**: the merging source with an empty index is exactly
  the same thing, and two paths are two places to differ.

### Tests
- `IndexMergeTest` — two ordered lists becoming one, an extra file taken today sorting above an
  old store file, windowing, cursor advances, both sources exhausted, a duplicate appearing once
  with the store's copy winning, a duplicate not shortening a page, a total order so ties cannot
  shuffle, and every sort order.
- `IndexedPagingTest` — paging through equals one big window, rejected rows not causing a skip or
  a duplicate, a window of one, the end of the list having no next key, more rows meaning a next
  key, a duplicate across a window boundary, resuming exactly where a window stopped, and the
  named fault this algorithm was rewritten for.
- `MigrationCoverageTest` — the new table's migration creates every column the entity declares,
  creates none it does not, and is keyed by the media key.

## [1.25.0]
Anonymised usage data, on the device only — and the seven-day review it feeds.

### Added
- **Anonymised usage data**, recorded locally and shown in Diagnostics. It answers the question
  the next round of work depends on: which features are actually used, how long loading takes,
  and whether anything is crashing.
  - **Collected:** which feature was opened, how often search ran and albums were created, how
    long loading took, the device model and Android version, and reduced crash records.
  - **Never collected:** photos, contacts, location, face data, file names, file paths, search
    terms, or anything inside your files. Photo access remains only for the photos the user
    chooses, through the system picker, and is revocable at any time.
  - **Nothing is uploaded.** There is no endpoint and no transmission anywhere in this code.
  - **A switch, a review, an export and a delete button** — all on the same screen, so the data
    is something the user can read and remove rather than something done to them.
- **The seven-day review.** Not a pile of numbers: the counts are turned into observations —
  which area carries the app, what is slow, whether crashes are happening, whether a refused
  permission suggests a screen is explaining itself badly. This is what the next round of work
  is chosen from, so "improve it every seven days" is a thing the app can actually support.

### The privacy guarantee is structural, not careful
- **Every recorded value comes from a closed enum or is a number.** A record has no field a
  filename, path or search term could be written into — not because the code remembers not to,
  but because the type has nowhere to put one. `UsagePrivacyTest` checks this.
- **A crash's message is dropped, not filtered.** A message is the likeliest place for a path or
  a search term to appear — "File not found: /storage/…/beach.jpg" is an ordinary crash message
  — and a filter can only remove the shapes someone thought of. What survives is the exception's
  class and the first few `Class.method` frames: enough to find the fault, nothing that can
  carry content. Tested with messages containing a real path, a real filename and a search term.
- **A crash is recorded even when recording is switched off**, and this is deliberate: the
  diagnostic log is a separate, user-visible thing that exists so a fault can be reported at
  all. It is deleted by the same button.
- **The stored file is plain text the user can read**, and says what it is at the top.

### Also
- **The version and the update check are now one row.** Two rows saying the same thing in
  different sections read as two unrelated features and took two lines to say one thing.
- **Search is counted once per search, not once per keystroke.** `setQuery` runs on every
  character, so counting there would have reported a typed word as five searches and made the
  whole review nonsense; a debounce finds where the typing stopped.

### Fixed — what the compiler and the tests caught
- A day is immutable, so every recording returns a new one. Mutating in place would have left a
  day half-updated when a write failed.
- The opening count was recorded on the startup path and could have delayed it; it is launched
  now.
- The crash line was parsed from the front, leaving a leading space on the build when a crash
  had no frames.
- Two of the new tests were wrong rather than the code: digits are legitimate in a frame name
  (a lambda is called `$load$1`), and the same word may name a feature and an operation because
  every line in the file says which kind it is.

### Tests
- `UsagePrivacyTest` — an exception message containing a path is not kept, nor one containing a
  search term; a cause chain is not followed; frames carry no file name or path; identifiers are
  unique within their kind and the line prefix separates the kinds; the file says what it is;
  no device identifier is part of the profile; no identifier looks like a path or a file name.
- `UsageCodecTest` — a day round-trips exactly, several days survive, an empty file reads as
  nothing, an unknown identifier is ignored rather than invented, comments and blank lines are
  skipped, an event before any day is not attached to the wrong day, a crash with no frames
  survives, totals round-trip exactly, and an average of nothing is zero rather than a crash.
- `UsageReviewTest` — only the last seven days count, the seventh day back is inside the window,
  a future day is not counted, no data says so, a partial week is admitted, the busiest area and
  a slow operation are called out and a fast one is not, crashes are counted and singularised, a
  refused permission is treated as a sign of confusing wording, and a custom window is honoured.
- `FeatureRouteTest` — every route the app has either maps to a feature or is deliberately not a
  place, so a new screen cannot silently be recorded under the wrong one.

## [1.24.0]
The update downloads and installs inside the app, instead of going to a browser.

### Changed
- **The update is fetched by the app, not by a browser.** "Open the APK" handed the address to
  whatever handled it — usually a browser, which is the wrong place for it: the file belongs to
  this app, and leaving the app loses the download, the check and the install.
  - **"Download and install"** fetches it with a progress bar.
  - **The file is checked before it can be installed**: that it is an APK, that it is *this*
    app, and that **its signing certificate matches the one this copy was signed with**. That
    check is only possible because the download happens here. An unverified file is **deleted**,
    not left where it could be opened by hand later.
  - A file the server does not give a length for shows an **indeterminate** bar rather than an
    invented percentage.
  - After downloading, the app asks Android to install it, and the system shows its own
    confirmation.

### Said plainly, because it cannot be otherwise
- **Android does not allow an app to replace itself silently.** There is no permission for it,
  by design, and no workaround short of root. What an app can do is fetch the file, check it,
  and ask the system — which is what happens. **The dialog says so in advance**, so the system's
  confirmation is an expected step rather than a surprise.
- On Android 8 and later, installing from outside the store needs a **per-app switch** the user
  controls. When it is off, the dialog offers **"Allow installs"**, opens that setting, and
  **re-reads it when the app comes back to the front** — otherwise it would keep asking for
  something already granted. Nothing is installed until the user returns and presses Install.
- **A copy installed by Play is sent to the store instead**, as before: a hand-installed APK
  would fight the store.
- `REQUEST_INSTALL_PACKAGES` is now declared. Play restricts this permission, so a
  Play-distributed build would use Play's own update flow; this build is sideloaded, which is
  exactly the case it is for.

### Tests
- `UpdateInstallPlanTest` — a sideloaded copy offering a download first, the install once
  downloaded and allowed, asking for the permission **before** installing rather than failing,
  a Play install going to the store even when the file is already downloaded and the permission
  is granted, a release with no APK, every step having a label, the download step naming the
  signing-key check, the install step saying the system will ask **and that it cannot be
  skipped**, the permission step not reading as though the install already happened, and **no
  step claiming the install can happen silently**.

## [1.23.0]
App media — which messaging apps keep media on this device, and how much.

### Added
- **App media.** A screen that answers the question a person actually has — *is my WhatsApp /
  Telegram / Arattai / Hyped media here or not* — instead of a folder tree to interpret. One
  card per app, saying which of three situations it is in: **has media** (with a breakdown by
  kind), **installed with none found**, or **not installed**.
  - Reached from the sidebar.
  - The folder path is shown, so the answer can be checked.
  - Apps with media come first, then by how much, so the list is useful before it is complete.
  - "Browse Android media" opens the existing browser, which shows those files with thumbnails.

### How the app is identified, and why it matters
- **The owning app is read from the folder path**, not from a table. The segment after
  `Android/media/` *is* the package name, so **an app nobody listed is still found** — which is
  what makes this work for Sandesh, or for anything else, without a guess.
- **The name comes from the system.** For an installed app the label is read from the
  PackageManager, so "Arattai" and "Hyped" appear under the name the device itself uses. A
  hardcoded name is only a fallback for an app that is *not* installed, where there is no label
  to ask for.
- **The listed package names were checked against each app's own Play Store listing, not
  guessed** — `com.aratai.chat` for Arattai and `com.friendsV` for Hyped. A wrong package name
  is worse than none: it would put one app's media under another app's name.
  - **Sandesh is deliberately absent.** No package name for it could be verified, and inventing
    one would misattribute media. If it keeps media under `Android/media`, discovery still finds
    it and the system still names it.
- **The older layout is handled too**: top-level `WhatsApp/`, `WhatsApp Business/` and
  `Telegram/` folders, which are still present on devices that upgraded rather than reinstalled.
- **SD cards are included**: the storage roots come from the app's own external media
  directories, so a card's app media is found as well as internal storage.
- **Counting is bounded** (20,000 entries, depth 6) so a folder with tens of thousands of files
  cannot hang the screen — and when it stops early the UI says **"20,000+"** rather than
  presenting a floor as a total.
- **The platform rule is stated on the screen**, from the existing policy: on Android 11+,
  `Android/media` needs All files access, and when it is missing the reason is shown instead of
  a list that is quietly empty.

### Tests
- `AppMediaPathsTest` — a file under `Android/media` belonging to the folder-named package,
  every messaging app resolving the same way, a nested folder still resolving, a folder without
  a dot not being a package, an ordinary picture not being app media, the legacy `WhatsApp` and
  `Telegram` folders, `Android/media` taking precedence over the legacy check, an unrelated
  top-level folder, both layouts through `ownerOf`, and Windows separators not defeating it.

### Fixed — a real bug the tests caught before it shipped
- **The legacy folder check silently returned nothing.** Chaining two `substringAfter` calls let
  the second fall back to the *whole* path and undo the first, so `WhatsApp/` and `Telegram/`
  were never recognised as belonging to an app. Found by writing the parser out in Python and
  comparing against the expected answers; the root is now stripped once.

## [1.22.0]
Memories — the pictures from this day in earlier years, and a reminder at seven.

### Added
- **Memories.** A photo taken on 5 April 2018 comes back on 5 April, and again the year after.
  There is **nothing to set up**: the date is already in the file, so the app works it out.
  - Grouped by how long ago — "8 years ago · 5 April 2018" — newest anniversary first, with a
    cover and the rest of that day's pictures beside it.
  - A second section for **the rest of the month** in earlier years.
  - **Tapping a picture opens it with the rest of that memory behind it**, so swiping stays
    inside the memory rather than wandering into the whole library.
  - Reached from Home and from the sidebar.
- **A reminder at 7 in the morning**, when there is something to look back on. Tapping it opens
  Memories. It is **not sent when there is nothing to remember** — a notification that says
  "nothing today" is worse than silence.
  - Delivered by **WorkManager**, so it survives a reboot and does not need the app to be
    running. It is told not to run on a low battery: a reminder about old photographs should not
    cost the last of the charge.
  - **Scheduled automatically at startup** — no switch, no first visit to a screen.
  - Android 13 and later require permission to post notifications. It is asked **in context**,
    on the Memories screen, the first time it is opened — not as a setup step — and everything
    else in the app works whether or not it is granted.

### Fixed — the date the app was using was the wrong one
- **The capture date is read now, not the date the file was last written.** The app only read
  `DATE_MODIFIED`, and that is the wrong date for this: copying a photo, restoring a backup, or
  having any app rewrite it changes the modification date, so **a picture taken in 2018 can
  carry a 2026 date and would then never be remembered**. The capture date comes from the file's
  own properties; the modification date is only a fallback.
  - **The two columns use different units.** MediaStore reports `DATE_TAKEN` in
    *milliseconds* and `DATE_MODIFIED` in *seconds*. Reading one as the other would put every
    memory in 1970 or in the far future, so the conversion happens in one place.
  - The column is read by name rather than by constant, because `MediaColumns.DATE_TAKEN` only
    exists from API 29 and this app runs on API 26.

### Deliberate decisions, each tested
- **Today is not a memory.** A photo from this morning is not "0 years ago".
- **29 February is remembered on 28 February** in a year with no 29th — otherwise those
  pictures would be remembered once every four years.
- **A file with no usable date is left out**, not filed under 1970.
- **A future-dated file is ignored**, so a wrong device clock cannot produce a memory of the
  future.
- **Pictures within a day are ordered by the instant**, not the day — otherwise a morning and
  an evening picture come out in whatever order the library happened to return.

### Tests
- `MemoriesEngineTest` — the anniversary, today not being a memory, several years becoming
  separate groups newest first, **the capture date winning over a rewritten modification date**,
  the modification date as the fallback, an undated file being left out, a future-dated file,
  another day of the same month, a different month, the leap-day rule in both directions, the
  order within a group, and the zone deciding which day a photo falls on.
- `MemoriesScheduleTest` — before seven, at exactly seven (a full day, not zero, or the
  reminder would arrive twice), one minute before, one second after, late at night, and that
  the wait is always positive and never more than a day.

## [1.21.0]
Check for updates actually opens — and no longer depends on a budget it cannot see.

### Fixed
- **The dialog was never rendered.** The settings row set its flag and started the check, but
  the block that draws the result **had no call site** — so pressing "Check" opened nothing at
  all. It compiled, it passed lint, and every unit test passed: the only trace was a private
  function nobody called. The call site is back.
- **The check no longer depends on a rate limit it cannot see.** It asked the GitHub **API**,
  which allows only a small number of unauthenticated requests per hour **per address** — and
  mobile networks in India put thousands of subscribers behind one address, so the allowance is
  often already spent by strangers. The app then reported "rate-limiting this device", which is
  a confusing way to say "someone else used it up".
  - The **releases Atom feed** is now the first source. It is an ordinary web page rather than
    an API call, so it is not subject to that budget at all.
  - The API remains as a **fallback**, because it carries the asset list.
  - The APK's address is **derived from the tag**, so the feed does not need to list assets —
    a release's download URL is deterministic.
  - A 403 now reads "The update server is busy with other requests from this network", and a
    **"Try again"** button is offered on any failure, so a retry does not mean closing the
    dialog and finding the row again.

### Added — a check that would have caught this
- **`tools/check_kotlin.py` now flags a private function that nothing calls.** That is exactly
  what this bug looked like from the outside: a declared, correct, never-invoked dialog. The
  rule was verified by deleting the call site again and confirming it fires.

### Tests
- `ReleaseAtomTest` — the newest entry is read (the feed is newest first, and reading the last
  would offer an older version), the APK address is derived from the tag, the notes arrive as
  readable text rather than markup (entities and tags included), a feed with no entries is
  refused with a readable reason, malformed XML is refused, an entry with no link is refused, a
  namespace-prefixed feed is read, and the tag is read from the **address** rather than guessed
  from a title someone typed.

## [1.20.0]
Adaptive streams actually play — and a page link no longer gets blamed on the container.

### Fixed
- **HLS, DASH, RTSP and SmoothStreaming were never on the classpath.** The app's own text told
  the user that "an HLS/DASH stream (.m3u8 / .mpd)" would play, and `StreamLink` classified
  those addresses as a stream Media3 assembles as it plays — **neither was true**. The modules
  that make it so were simply never added, so every `.m3u8` and `.mpd` address failed. This is
  what adaptive SD/HD streams are, so the formats that most needed to work could not. The
  modules are now dependencies, and the promise is true.
- **A link to a page is no longer described as an unreadable container.** A `youtu.be` address
  was pasted into "Stream from link", and the app answered "The container is not one Android
  can read. MPEG program streams (.mpg), Windows Media (.wmv) and Flash (.swf) have no support
  on Android at all." Every word of that was wrong for the situation: the address was a web
  page. The same mistake as the codec one, in a new place.
  - A pasted link is now **recognised as a page before playback is attempted**, so the answer
    arrives without a black screen first.
  - The service is **named** when it is known — "This is a link to a YouTube page, not to a
    video file" — and the explanation says a page is shown by a browser, and that the service's
    own sign-in is involved, which **no codec pack changes**.
  - It is a **warning, never a refusal**: the classifier can be wrong about an unusual address,
    and a link that works must never be blocked by a guess. The card says so.
  - A container failure on a link now explains the page case first; a container failure on a
    **local file** keeps the original explanation, which is right for it.

### Tests
- `StreamLinkPageTest` — short video links, subdomains, page-shaped paths on any host, direct
  file links, the query string not confusing it, a bare host not being a page, the warning
  naming the service, the warning saying what *would* work, the warning never forbidding a try,
  an unknown host still getting a useful warning, and case-insensitivity.
- One of those tests pins a mistake the classifier made on its first version: it decided on the
  host alone, so `vimeo.com/download/clip.mp4` — a perfectly playable file — was called a page.
  **An explicit media extension now outweighs the host's reputation.**

## [1.19.0]
A reader for text, JSON, CSV, logs and code — pending work, Part G.

### Added
- **The app reads text documents now.** `.json`, `.csv`, `.log`, `.txt`, `.md`, `.xml`, source
  code, configuration files and subtitles were classified as documents and then handed to
  "open with another app", which frequently found nothing — which is exactly what the
  Documents screen looked like: a list of icons. They open in a reader inside the app.
  - **Monospace, because column alignment is the point** of a CSV, a log or a stack trace.
    Text is selectable.
  - **A byte-order mark is believed.** If the file declares what it is, that is used.
  - **A guessed encoding is admitted to.** A file that is not valid UTF-8 is read as
    ISO-8859-1 — which cannot fail — but the screen says the encoding was guessed, so wrong
    accents are explained rather than passed off as the file's content.
  - **Binary is refused, not shown as noise.** A `.docx` is a zip archive; rendering its bytes
    would be worse than saying the app cannot read it. The extension whitelist and a NUL-byte
    check both have to agree.
  - **What it holds is capped** (2 MB) and the screen says when a file was longer, so a
    gigabyte of logs is not pulled into memory to show the first screenful.
  - **Only JSON is re-indented, and only when asked.** A reader that silently reformats what
    it reads is worse than no reader — and "tidy" is not offered for XML, because the app has
    no XML indenter. A button that does nothing would be a lie.

### Fixed — three bugs the tests and lint caught
- **The JSON indenter never decremented its depth for an empty container**, so a single `{}`
  anywhere in a document broke the whole thing. Found by writing the algorithm out in Python
  and comparing against the expected output.
- **The binary check ran before the byte-order mark**, so **every UTF-16 file was rejected as
  binary** — because UTF-16 text is full of NUL bytes by design. A declaration now beats a
  guess: the mark is read first, and only then is the content sniffed.
- **The video player's name label read a `StateFlow`'s value during composition**, so it would
  never have updated when the video changed. Lint caught it; it is collected now.

### Tests
- `TextFormatsTest` — the formats people actually have are readable, the case does not matter,
  an office file is *not* offered as text, an empty extension is not readable, the kind is
  recognised for display, and only JSON is offered tidying.
- `TextDecoderTest` — plain UTF-8, all three byte-order marks, invalid UTF-8 falling back and
  admitting it guessed, a binary file refused rather than shown as noise, the binary check
  looking only at the start, truncation reported, an empty file reading as empty rather than
  failing, and a newline not mistaken for binary.
- `JsonPrettyTest` — flat and nested objects, empty containers staying on one line, numbers
  *not* reformatted (`1` must not become `1.0`), braces and commas inside strings left alone,
  escaped quotes and backslashes not ending a string, already-indented input tidied rather than
  doubled, unbalanced input refused rather than formatted, and a chosen indent width.

## [1.18.0]
Playback failures say what actually went wrong.

### Fixed
- **Every playback failure was blamed on the codec, and most of them were not codec problems.**
  The user's screenshot showed `ERROR_CODE_IO_FILE_NOT_FOUND` — the file was never opened —
  reported as "the codec inside it may not be supported by this device", which sends someone
  looking in entirely the wrong place.
  - Each of Media3's error codes now maps to its real reason: a file that could not be
    opened, a permission that was not given, a container Android cannot read, a codec the
    device cannot decode, a damaged file, a server that refused, plain http that Android
    blocks, and so on. The mapping is pure and every claim is unit tested.
  - **A missing file is no longer described as a codec problem**, and the real causes are
    named instead — it was moved or deleted, or it is in a folder Android does not let the
    app read, such as another app's media folder.
- **The failure now offers what can actually be done about it.** The snackbar's action depends
  on the reason: a stale entry offers **"Check again"** (which re-reads the library and retries,
  because the entry may simply be out of date), an unsupported codec offers **"Open with"**
  another app, and a network failure offers **"Try again"**. Previously nothing was offered at
  all.
- **The failing file is named.** With thirty-two videos in a playlist, "3 / 32" does not say
  which one failed — so the message quotes the file, and the player's top bar now shows the
  name beside the position.

### Changed
- The audio player, the URI player and the WebDAV player all use the same explanations, so a
  failure reads the same wherever it happens.

### Tests
- `PlaybackFailureTest` — a missing file is not blamed on the codec; a missing file offers a
  rescan; a permission failure says "permission"; an unsupported codec *is* where the codec is
  mentioned; an unsupported container is distinguished from an unsupported codec; a damaged
  file does not blame the device; a network failure offers a retry and not another app; a web
  page instead of a file is explained; plain http is explained as Android blocking it; every
  known code gives a reason and a detail; an unknown code still says something useful; and the
  message names the file when it is known and omits it when it is not.

## [1.17.0]
Cloud folders, browsed inside the app — and no repeated sign-in.

### Changed
- **Cloud browsing happens in the app now.** The old entries opened the service's own app or
  website; they are gone, replaced by a real browser that walks a provider's folders, shows
  the files, and opens them in the app.
- **How a folder gets there, and why there is nothing to sign into.** A folder is chosen once
  through the system's own folder picker — the screen whose sidebar lists every installed
  service, which is exactly what the user's screenshot showed. That listing belongs to the
  platform: `ContentResolver.acquireRoots()`, which the system picker uses, **is not public
  API**, so an ordinary app cannot reproduce that sidebar. What an app *can* do is what the
  system picker offers: choose a folder, and be granted it.
  - The **service keeps its own account and session**, so this app never receives a password
    and never signs anything in — which is precisely why there is nothing to log into again
    later.
  - The grant is **persisted** (`takePersistableUriPermission`), so the folder opens straight
    away from then on. A grant revoked outside the app is dropped rather than shown as
    something that would fail when tapped.
  - Every provider is spoken to the same way, through the platform's document API, so one
    browser works for Drive, TeraBox, WPS Office, Zoho WorkDrive and a NAS alike.
- **Everything opens in the app.** A picture goes to a viewer built for a single cloud
  document; video and audio go to the URI player, which was generalised so it no longer
  assumes it is playing a web link.

### Added
- **A contact handle.** Settings → About shows the version and offers **@osintgram_io** for
  suggestions, feedback and feature requests; the sidebar links to it too.

### Fixed
- `INTERNET` (from v1.16.0) is what makes any of this reach a provider at all.

## [1.16.0]
Check for updates — and a permission the app could not work without.

### Fixed
- **`android.permission.INTERNET` was never declared.** On Android, a socket opened
  without it throws `SecurityException: Permission denied`, so **Stream from a link**,
  **WebDAV browsing** and the new update check could not open a connection at all. The code
  that called them was correct; the manifest was not, and nothing in the build noticed —
  it compiles, it lints, and the unit tests pass either way. Declared now, with a
  **manifest guard test** so it cannot go missing again.

### Added
- **Check for updates** (Settings → Diagnostics & experimental → Check for updates). It asks
  the project's releases feed what the latest version is and compares it with the one
  installed, then offers the release page or the APK — it never downloads or installs
  anything by itself.
  - **No automatic check and no background request.** The feed is asked only when the
    button is pressed, and nothing about the device is sent: it is a plain GET.
  - **The comparison is the point, and it is pure and tested.** `VersionName` ignores a
    leading `v`, treats `1.16` and `1.16.0` as the same version, cuts a pre-release suffix
    so `1.16.0-beta` counts as `1.16.0`, and compares numerically so `1.10.0` is newer than
    `1.9.0`. A tag that is not a version at all — `latest`, `nightly` — is **not** an
    update, because saying nothing beats offering something meaningless. The app can never
    tell you to "upgrade" to something older.
  - **The rate limit is reported honestly.** GitHub allows a limited number of
    unauthenticated requests per address per hour; running out says "try again in an hour"
    rather than looking like a broken app.
  - **A Play-installed copy is told so**, because updating it from a file can conflict with
    the store.
  - The releases feed is read by a small hand-written JSON reader, because `org.json` is
    stubbed out in unit tests and the payload's shape is an external contract — the one
    thing worth testing without a device. It was then **checked against the live API**: the
    field names, the asset chosen, and the comparison result all match a real reply.

### Tests
- `VersionNameTest` — a leading `v`, missing segments, pre-release and build suffixes,
  `1.10.0` versus `1.9.0`, equal versions not being an update, non-versions not being an
  update, and an older version never being offered.
- `ReleaseJsonTest` — a realistic reply with extra fields and nested objects, the APK
  chosen over the bundle, escapes in the release notes, a bundle-only release, a
  tag without the `v`, and readable refusals for a missing tag, a missing page, malformed
  JSON and an array where an object was expected.
- `NetworkPermissionTest` — `INTERNET` is declared, and declared outside a comment.

## [1.15.0]
Four real bugs in the format layer, found by reading the code. Plus the plan for the
player and the readers, written down in full.

### Fixed
- **The content address was built from a guess, and that is why files would not open.**
  Each file's `content://` address was built from the kind the app *guessed*, but
  MediaStore decides which view a row lives in: `content://media/external/images/media/<id>`
  only resolves for rows whose `media_type` is `IMAGE`. A WebP in a folder MediaStore
  treats as non-media — or anything under `Android/media/…` — was addressed through the
  images view, which does not contain it. **The file listed and then would not open.**
  Now the address is built from the row's own `media_type`, with the union view for
  anything MediaStore does not call media. The decision is a pure function that cannot be
  handed the display kind, so the two can never disagree again.
- **A preview was promised that no decoder could draw.** Anything with an image extension
  was called an image, including `.psd`, `.svg`, `.tiff`, `.jxl`, `.jp2`, `.ico` — formats
  Android has **no decoder** for. The tile was promised a picture and left blank. There is
  now a separate question — *can this device draw it?* — with an honest answer per format:
  a tile with no preview says **`no preview`**, and the viewer shows a card naming the
  reason and offering another app.
- **Animated WebP and GIF were decoded one frame deep**, which is why stickers showed
  nothing. Coil now registers `ImageDecoderDecoder` on Android 9+ (and `GifDecoder` below
  that), so animated WebP animates.
- **Playback failures were silent.** Neither player listened for `onPlayerError`, so a file
  that could not be decoded produced a player that simply did nothing — which is exactly
  what a failing `.opus` looked like. Both players now name the reason, and the video
  player enables the decoder fallback so a missing hardware codec falls back to software.
- **A PDF no longer offers "open with another app" as its only route.** It has a built-in
  reader, so that option is not offered for it (or for audio, which has a built-in player);
  offering it only ever produced "no installed app can open this".

### Added
- **`PLAYER-AND-READERS-PLAN.md`** — the honest capability matrix for video, audio, images
  and documents, the parts the gaps are closed in, and what will not be promised.

### Tests
- `MediaCollectionChoiceTest` — every media type maps to its own view, anything
  unrecognised maps to the union view, and the decision takes **only** the row's media type.
- `ImageDecodabilityTest` — every format that renders, the ones gated behind Android 9 and
  12, every format with no decoder at all, and the wording used when a preview is missing.

## [1.14.0]
WebDAV — cloud browsing that works without registering anything.

### Added
- **Browse a WebDAV server inside the app.** Add an address (Nextcloud, ownCloud, a NAS,
  Box over WebDAV), sign in, and walk the folders. Folders come first and files show their
  type and size, exactly as a file browser is read.
  - **No new dependency.** `HttpURLConnection` speaks `PROPFIND` and Basic authentication,
    and `javax.xml` reads the reply — so nothing large was added for this.
  - **The reply parser is tolerant in the ways real servers require.** Elements are matched
    by local name, so `d:response`, `D:response`, `lp1:response` and an unprefixed reply all
    work; absolute-path, full-URL and relative `href`s are all resolved; a `propstat`
    without a `200` status is still read when no `200` one exists.
  - **A file plays straight off the server**, with the sign-in attached to the player's own
    HTTP source — so the password never becomes part of a URL, a navigation argument, or a
    saved-state bundle.
  - **The password lives in its own store**, separate from settings, so it cannot reach the
    settings export, and it is never written to the diagnostics log. The line-based format
    strips tabs and newlines from every field, so a value cannot break the format it lives
    in — and a corrupted line costs one entry, not all of them.
  - Limits are stated rather than discovered: **Basic authentication only**, and a
    **self-signed certificate is refused** rather than waved through. The app does not
    weaken TLS to make a server connect.
- **Terabox was removed from the sidebar**, at the user's request. It publishes no official
  API, so the only honest entry for it was a browser link, which is not what was wanted.

### Tests
- `WebDavXmlTest` — a Nextcloud-shaped reply, an Apache-shaped reply with full-URL `href`s
  and no displayname, an unprefixed reply, a `404` propstat ignored in favour of the `200`
  one, malformed XML refused with a readable reason, an empty folder as an empty list,
  href resolution for all three shapes, and folder-walking that stops at the root. Plus two
  about dates: a server sending the **wrong weekday** still yields a timestamp (strict
  parsing alone would throw it away), and an unreadable date yields **no** date rather than
  an invented one.
- `WebDavServerCodecTest` — the round trip, several servers, a password with spaces, and a
  **newline or tab inside a value** not breaking the format or shifting the fields.

## [1.13.0]
Stream from a link, and a sidebar.

### Added
- **Stream from a link.** Paste an address and play it, with a recent list that keeps the
  last 20.
  - **Only `http` and `https` are accepted.** A `file:`, `content:`, `javascript:` or
    `ftp:` link is refused with a plain reason instead of being handed to the player,
    where it would fail obscurely or mean something the user did not intend. A bare
    address like `example.com/clip.mp4` gets `https://`, because that is what pasting one
    usually means.
  - **The kind is read from the path's extension, with the query string ignored** — signed
    links are the normal case and a `?token=…` must not be mistaken for the file type.
  - An unrecognised address is **not rejected**: Media3 sniffs the content, so it still
    gets a fair chance.
  - A **dedicated player**, not the local video player. That one carries trim, delete,
    rename and a playlist built from the library, none of which mean anything for a
    remote address; this one has the controls that do apply, and nothing that pretends to.
    When a link cannot be played, Media3's own reason is shown next to the most likely
    cause, instead of a spinner that never stops.
  - The screen states the limit up front: the link must point straight at the media, and
    a link to a web page will not play.
- **A sidebar** with three groups — Library, Cloud and Tools — opened from the home
  screen's menu button.

### Cloud entries, stated plainly
The sidebar offers **Google Drive, OneDrive, Box and Terabox**. Each opens the provider's
own app when it is installed, and its website when it is not — decided by a pure rule that
is unit tested.

They are **not** in-app browsing of those services, and the sidebar says so rather than
leaving it to be discovered. Reading a Drive, OneDrive or Box library inside another app
needs OAuth with an application id issued to *that* app's developer, which means
registering an app with each provider; those credentials cannot be invented here. Terabox
is a different case: it publishes **no official API**, so there is nothing honest to build
against, and the entry says that too.

What does work today is a direct media link: the Stream screen plays an address straight
from any of these services when the service gives one.

### Tests
- `StreamLinksTest` — http/https only, bare addresses, whitespace, extension reading
  including uppercase and query strings, unknown types not rejected, and refusals for
  `file:`/`content:`/`javascript:`/`mailto:`/`ftp:`, missing hosts and links with spaces.
- `CloudProvidersTest` — the four providers are offered, the app is preferred when
  installed, the target matches the plan, every entry says what it does, and Terabox says
  there is no API rather than promising one.

## [1.12.0]
Phase 3 complete: a screen that tells the truth about what the app can reach.

### Added
- **Access diagnostics** (Settings → Diagnostics & experimental → Access diagnostics).
  One card per access — photos and videos, music and audio, photo locations, all files
  access, the folders you granted, and the read-only root check — each saying what it is
  in state terms, **what it enables**, what being without it costs, and what to do.
  - The wording is deliberate. An optional access is described as optional and its
    alternative is named: all files access says that Deep Scan over a granted folder
    needs no extra permission. Missing audio says photos and videos still work. Partial
    photo access says the app sees only what you picked, and offers the system picker to
    widen it.
  - **Every fix goes through the official route** — the system permission dialog, the
    system settings screen, or the system folder picker. Nothing is worked around, and
    "Add a folder" goes to the real folder picker rather than a second copy of it.
  - The decision is pure (`AccessDiagnostics`) and unit tested; only the reading of the
    device state touches Android. A general invariant is asserted: **a fix is offered
    exactly when something is missing**, and never for an access that is present.
  - The screen says plainly that it only reports what the app can reach, and that
    nothing is sent anywhere.

### Tests
- `AccessDiagnosticsTest` — a fully-granted device has nothing blocking; denied media
  access is blocking and says so; partial access is not blocking and offers more photos;
  missing audio is explained as partial loss; all files access is presented as optional
  with its alternative; granted folders are counted; the root row appears only when the
  check was run and never offers a fix; and the fix/state invariant across every
  combination.

## [1.11.0]
Phase 3 continues: switches that cannot lie, previews that are really produced, and
diagnostics that are actually useful.

### Added
- **A dependency registry** (`SettingsDependencies`). Every settings change now passes
  through it, so a setting whose prerequisite is off is switched **off** with it instead
  of being left claiming to be active — deep scan without folder access, signature
  detection or duplicate detection with no scan method, hidden files without a folder
  walk, per-type previews without thumbnail generation, export with nothing to scan.
  Two deliberate non-rules: leaving Advanced mode only *hides* advanced settings rather
  than discarding them, and resolution **never turns anything on**. When a switch flips,
  the screen says why.
- **Per-type previews, and they are really produced.** Video frames, embedded audio
  artwork (read with `MediaMetadataRetriever`, which Coil has no decoder for) and a
  PDF's first page (rendered with the same platform engine the reader uses), each with
  its own switch, because each costs something different. Results are cached in memory
  by URI and bounded, and anything unavailable falls back to the type icon — a tile
  never goes blank.
- **Resolution and duration on tiles**, shown only where the app actually knows them.
- **A real diagnostics log.** Written only when asked, bounded by age and size, and
  **paths are redacted to their extension unless you allow them** — `DCIM/Camera/IMG_2024.jpg`
  becomes `*.jpg`, so the log can say what kind of file failed without saying whose or
  where. `LogRedactionTest` pins that guarantee down. It can be saved to a file you
  choose, or cleared.
- **Recovery and export settings that change behaviour**: which scan modes the Recovery
  screen offers, whether export is allowed at all (enforced in the exporter, not just
  hidden in the UI), whether a copy is verified against the source size, what happens
  when the destination already has that name (**keep both** or **skip** — overwriting is
  deliberately not offered), and how many copies run at once, with the naming step
  serialised so parallel copies cannot clobber each other.
- **`keepScanHistory` became `keepActivityHistory`**, because that is what it actually
  controls: it now gates whether recovery and export activity is recorded at all.

### Changed
- The export format is now generated from the settings source by
  `tools/generate_settings_export.py`, and `SettingsExportTest` reads that same source to
  fail if a field is missing from the export — so a new setting cannot quietly drop out
  of a backup.
- A skipped export is reported as **Skipped**, not as a failure.

### Still to come in Phase 3
The permission diagnostics screen.

## [1.10.0]
Phase 3 begins: settings you can take with you.

### Added
- **Export and import settings.** Every choice is written to a versioned JSON file you
  choose through the system picker, and can be read back on another install.
  - The file holds **only your choices** — no file paths, no content URIs, no SAF
    grants, no tokens. A test asserts exactly that, because an export is a file you
    will keep or share.
  - **Import is strict about the format and tolerant about everything else.** A file
    from a newer format is refused with a readable reason rather than half-applied;
    unknown keys are ignored so a file from a later build still loads; a missing key
    falls back to the current default; an unrecognised enum value falls back instead of
    crashing. Nothing is written until the whole file has parsed, so a bad file leaves
    your settings untouched.
  - The format is read and written by a small hand-rolled codec (`FlatJson`) rather
    than `org.json`, which is stubbed out in unit tests — this way the format itself is
    covered without a device and without a new dependency.
- **Reset a single section.** Every heading in Settings now has a **Reset** that
  restores only the rows under it, so the control does exactly what the heading
  promises. `advancedMode` is deliberately never reset: it is the switch that reveals
  the section, and flipping it while you are looking at it would be disorienting.

### Tests
- `FlatJsonTest` — round trips of every supported value type, escaping, unicode escapes,
  tolerance of whitespace and key order, and rejection of trailing content,
  unterminated strings, a missing colon, an unknown escape and a non-object root.
- `SettingsExportTest` — every one of the 34 settings round-trips; defaults round-trip;
  a newer format, a missing version and malformed input are each refused with a message;
  unknown keys ignored; missing keys defaulted; unknown enums defaulted; and the export
  contains no path, URI, token or grant.
- `AppSettingsTest` — per-section reset touches only its own section, handles every
  section name the screen shows (so a renamed heading cannot silently do nothing), and
  an unknown section changes nothing.

### Still to come in Phase 3
Per-kind thumbnail toggles, the recovery/export settings block, a permission
diagnostics screen, privacy and log settings, and a central feature-dependency registry.

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
