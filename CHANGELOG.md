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
