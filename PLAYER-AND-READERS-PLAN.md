# The player and reader plan

What the app can actually open, what it cannot, and the order the gaps get closed.

This document exists because "add a player that plays everything" is not something any
Android app can honestly promise. Android gives an app a fixed set of decoders, and some
of the formats on the wish list have **no decoder on the platform at all**. So this is
written as: here is the truth, here is what is fixable, here is the order.

---

## Part 1 — What actually happened (the audit)

Four bugs were found by reading the code, not by guessing. Two of them explain most of
what was reported.

### Bug 1 — The content address was built from a guess (fixed)

`MediaStoreDataSource` built each file's `content://` address from the **kind the app
guessed** from the MIME type and extension. But MediaStore's own `media_type` decides
which view a row lives in:

- `content://media/external/images/media/<id>` only resolves for rows whose `media_type`
  is `IMAGE`.

So a file the app called an image, but which MediaStore did not call media — a WebP in a
folder it treats as non-media, anything under `Android/media/…` — was addressed through
the images view, which does not contain it. The file **appeared in the list and then
would not open**. That is exactly the WebP symptom, and it applies to a whole class of
files, not just WebP.

**Fixed** by asking MediaStore which collection the row belongs to, and using the union
view (`Files`) for anything it does not classify as media. The decision is now a pure
function, `MediaCollectionChoice.forRow`, that cannot be handed the app's display kind —
so the two can never disagree again.

### Bug 2 — A preview was promised that no decoder could draw (fixed)

`FileTypeRegistry` calls anything with an image extension an image — including `.psd`,
`.svg`, `.tiff`, `.jxl`, `.jp2`, `.ico`, `.xcf`. Android has **no decoder** for those.
`isPreviewable` was therefore true, a tile was promised, the platform failed, and the
result was a blank rectangle with no explanation.

**Fixed** with `ImageDecodability`, which answers a separate question from "what is this
file": *can this device draw it?* Formats with no decoder are never promised a preview,
the tile says `no preview`, and the full-screen viewer shows a card naming the reason and
offering another app.

### Bug 3 — Animated WebP and GIF were decoded one frame deep (fixed)

Coil was configured with only `VideoFrameDecoder`. Animated WebP — which is what
**WhatsApp stickers are** — drew a single frame or nothing.

**Fixed** by adding `coil-gif` and registering `ImageDecoderDecoder` on Android 9+
(`GifDecoder` below that). The class name was read off the published artifact rather than
guessed.

### Bug 4 — Playback failures were silent (fixed)

Neither the audio nor the video player listened for `onPlayerError`. A file that could not
be decoded produced a player that simply did nothing. This is why a failing `.opus` looked
like a broken app rather than an unsupported file.

**Fixed**: both players now name the reason, and the video player enables
`setEnableDecoderFallback`, so a missing hardware codec falls back to a software one
instead of giving up.

---

## Part 2 — The honest capability matrix

### Video

| Format | Container | Verdict |
|---|---|---|
| MP4, M4V, M4P | ISO-BMFF | **Plays.** H.264, HEVC, VP9, AV1 where the device has the codec |
| WebM | Matroska | **Plays.** VP8, VP9, AV1, Opus, Vorbis |
| MKV | Matroska | **Plays** for the codecs above; exotic codecs inside it still need a decoder |
| MOV, QT | ISO-BMFF | **Plays** for H.264/HEVC. **Not** ProRes or other editing codecs |
| AVI | AVI | **Usually plays** — the extractor handles MPEG-4 and MP3. DivX/Xvid variants may fail |
| FLV | FLV | **Plays if it holds H.264.** VP6 will not |
| OGG, OGV | Ogg | Audio (Vorbis, Opus) **plays**. **Theora video does not** — Android has no Theora decoder |
| TS, M2TS, MTS (**AVCHD**) | MPEG-TS | **Plays.** AVCHD is H.264 in MPEG-TS |
| 3GP, 3G2 | ISO-BMFF | **Plays** |
| **MPG, MPEG, MPE, MPV, MP2** | MPEG-1/2 **program stream** | **Cannot play.** Media3 has no program-stream extractor |
| **WMV, ASF** | ASF | **Cannot play.** Media3 has no ASF extractor |
| **SWF** | Flash | **Cannot play.** No decoder, and no platform support |
| RM, RMVB | RealMedia | **Cannot play** |

### Audio

| Format | Verdict |
|---|---|
| MP3, AAC, M4A, FLAC, WAV, AMR, MIDI | **Plays** |
| OGG, OGA (Vorbis) | **Plays** |
| **Opus** (`.opus`, `.ogg` with Opus) | **Plays** — Opus in an Ogg container is supported. A *raw* Opus stream with no Ogg wrapper is not |
| WMA | **Cannot play** (ASF) |
| APE, WavPack, DSD | **Cannot play** |

**On the `.opus` report:** Ogg-Opus is supported, so a file that fails is most likely
either a raw Opus stream or a file whose container is unusual. The player now says which,
instead of staying silent — that will tell us which it is.

### Images

| Format | Verdict |
|---|---|
| JPEG, PNG, GIF, WebP, BMP, WBMP | **Renders on every supported version** |
| HEIC, HEIF | **Renders on Android 9+** |
| AVIF | **Renders on Android 12+** |
| Animated GIF, animated WebP | **Renders and animates on Android 9+**; below that, GIF only |
| SVG, SVGZ | **Readable** — a decoder is bundled (Part F, v1.33.0) |
| TIFF, PSD, XCF, JXL, JPEG-2000, ICO, TGA, PCX, PPM family | **No decoder on Android** |
| EPS, AI, INDD, CDR, Sketch | **No decoder**, and no practical one |

### Documents

| Format | Verdict |
|---|---|
| PDF | **Built-in reader** (the platform's own renderer) |
| Plain text, JSON, XML, CSV, logs | **Readable** — a reader for these is built in (Part G, v1.19.0) |
| DOCX, XLSX, ODT, ODS, EPUB | **Readable** — a reader for these is built in (Part G, v1.32.0) |
| PPTX | **Readable** — the same container as Word, read in the deck's own order (v1.33.0) |
| PPT (the old binary format) | **No reader.** A different container entirely; hand off |

---

## Part 3 — The parts, in order

### Part A — Truth in the format layer ✅ **done, v1.15.0**

The four bugs above. Nothing here is a new feature; it is the app no longer lying about
what it can do.

### Part B — One dynamic player for audio and video ✅ **done, v1.34.0**

**What the survey found** (four players, 2014 lines, each building its own `ExoPlayer`):

| Player | Decoder fallback | MIME hint | Player build |
|---|---|---|---|
| `VideoPlayerScreen` (231) | **yes** | no | `ExoPlayer.Builder(context, renderers)` |
| `AudioPlayerScreen` (460) | **no** | no | `ExoPlayer.Builder(context)` |
| `StreamPlayerScreen` (164) | **no** | no | `ExoPlayer.Builder(context)` |
| `WebDavPlayerScreen` (159) | **no** | no | `DefaultMediaSourceFactory` + auth header |

Three real gaps, and none of them is cosmetic:

1. **Only the video player falls back to a software decoder.** A device with no hardware decoder
   for a codec plays a video and fails the same audio — which is a bug the user cannot explain.
2. **No player tells Media3 what the file is.** Every one calls `MediaItem.fromUri`, so the
   format is sniffed. Sniffing is good and often right; a hint is what makes the *right* extractor
   get chosen for a container whose header is ambiguous, and for a content URI that carries no
   extension at all.
3. **Four places to fix, and they have already drifted** — which is the actual defect. The video
   player got the fallback; the others did not, because there was nowhere to put it.

**Steps**

- **B1 — one factory.** `PlaybackEngine`: the single place a player is built, with decoder
  fallback on, taking an optional data source (WebDAV's auth) and an optional MIME hint. All four
  players use it. Testable part: the builder configuration.
- **B2 — the MIME hint, as a pure table.** `PlaybackMime.hintFor(...)`: the item's own MIME when
  it is a real media type, else a well-known extension mapping, else **null so Media3 sniffs**.
  A *wrong* hint is worse than none — it picks the wrong extractor — so only mappings that are
  certain are in the table, and every one is tested.
- **B3 — position memory.** ✅ **done, v1.31.0** — Returning to a video resumes where it was left, with the two rules
  that make it not annoying: a position near the end starts over, and a position near the start
  is not worth restoring. Pure and tested.
- **B4 — one screen.** ✅ **done, v1.34.0** — `MediaPlayerScreen` for audio and video on the same surface, replacing the
  internals of `VideoPlayerScreen` and `AudioPlayerScreen`. The audio player's queue and the
  video player's gestures stay.
- **B5 — failures.** The existing `PlaybackFailure` reasons and actions move to the new screen,
  including **"Try another app"**.
- **B6 — stream and WebDAV stay as wrappers.** They carry redirect and sign-in handling the
  library player has no business knowing about; they use the same engine and gain the fallback.

**Not doing:** `setExtensionRendererMode(PREFER)`. With no extension renderer bundled it is a
no-op, and a setting that does nothing is the kind of thing this app does not ship. It belongs
with Part D, if Part D is ever chosen.

### Part C — A capability screen ✅ **done, v1.29.0**

The matrix above, in the app, **generated from the same pure tables the player uses** — so it
cannot drift from what the app actually does.

**Steps**

- **C1 — the matrix, as a pure table.** `CapabilityMatrix`: for every extension the app knows,
  one verdict — **Plays here** / **Plays with a caveat** / **Needs another app** — and the reason,
  derived from `FileTypeRegistry`, `ImageDecodability`, `Stickers`, `TextFormats` and the list of
  containers Android has no parser for.
- **C2 — a test that nothing is unclassified.** Every extension in the registry must appear in the
  matrix, so a format cannot be added to the app and silently left out of the answer.
- **C3 — the screen.** Searchable, grouped by verdict, with the reason next to each format. The
  app then *tells* you that `.wmv` needs another app, instead of failing when you try.

### Part D — The formats Media3 cannot parse ✅ **answered, v1.47.0**

**Answered by asking the device instead of bundling a decoder.** Media3 bundles its extractors, so
what the app can play is the same everywhere — which is the problem for MPEG program streams.
Android's own `MediaParser` reads `video/mp2p`, so a `.mpg` Media3 refuses is often a file the device
plays perfectly well. `PlatformParserMediaSourceFactory` hands exactly those extensions to the
platform, **on Android 11 and up** where `MediaParserExtractorAdapter` exists, and leaves every other
format to the delegate untouched.

**What that changes:** `.mpg`, `.mpeg`, `.mpe`, `.mpv`, `.m2v`, `.vob` and `.mp2` moved from *needs
another app* to **opens, with a caveat** — the caveat being real, because the parser is the vendor's
and the answer is the device's.

**What it does not change:** ASF (`.wmv`, `.asf`, `.wma`), SWF and RealMedia have **no parser on
Android at all**, and the platform parser does not read them either. The only thing that would play
them here is bundling a decoder, which is a licensing decision — and `ffmpeg-kit` was retired in
2025, so there is no maintained drop-in to reach for. The matrix now says that in the reason rather
than leaving a dead end.

**Adaptive streaming is now done (v1.20.0):** HLS, DASH, RTSP and SmoothStreaming are on the
classpath, so `.m3u8` and `.mpd` — the SD/HD adaptive streams — play. What remains in this part
is the containers Android has no parser for at all.

`.mpg`/`.mpeg` (MPEG program stream), `.wmv`/`.asf`, `.swf`, RealMedia. Two honest options:

1. **Hand off** to another app (what the app does today, now with a clear message). No new
   dependency, no false promises.
2. **Bundle a software decoder.** This means a large native library (FFmpeg). Note that
   `ffmpeg-kit` — the usual choice — was **retired and archived in early 2025**, so this
   would mean building FFmpeg ourselves, adding tens of megabytes, and carrying LGPL/GPL
   obligations. It is a real option, but it is a project, not a checkbox.

I will not pretend option 2 is free. Part B makes option 1 work well; option 2 is a
separate decision.

### Part E — WhatsApp and `Android/media` visibility ✅ **done, v1.26.0** (and v1.23.0 for the report)

WhatsApp media lives under `Android/media/com.whatsapp/WhatsApp/Media/…`. Since Android
11, **MediaStore does not expose those files to other apps**. No decoder work will make
them appear, because the app is never given them in the first place. This is very likely
the root cause of "many `.jpg` from the WhatsApp folder do not show", and it is also where
WhatsApp stickers live.

The app already has the two legitimate routes — a user-granted folder (Deep Scan) and
all-files access — and an Android-folders screen. What is missing is folding those results
**into the main gallery** with thumbnails, so a WhatsApp photo appears next to a camera
photo instead of in a separate list. That is this part.

### Part F — Dynamic image reader ✅ **done, v1.33.0** (SVG decoder and the sticker viewer)

- an **SVG** decoder (a small library), so `.svg` renders instead of "no decoder";
- a **sticker viewer**: transparent background, looping, for animated WebP and GIF;
- a **sticker grid** once Part E makes the files visible;
- a decision on TIFF and JPEG-2000, which have decoders available as libraries but are
  large for how rarely they are used.

### Part G — Document readers ✅ **done, v1.19.0 and v1.32.0** (text/JSON/CSV/log/code; then Word, Excel, OpenDocument and ePub)

Remaining in this part: **nothing** — Word, Excel, OpenDocument, ePub and PowerPoint are all read,
and the old binary `.ppt` is the only one handed off, on purpose. Word, Excel, OpenDocument and ePub are read now — without a
library, because they are zip containers with XML inside and the reader is a bounded unzip plus a
walk over a tokeniser the app owns. **A presentation is read too, since v1.33.0** — this was written when it was not, and the reader turned out to be the same container with the slides ordered by `presentation.xml` rather than by file name. The old binary `.ppt` is still handed off: it is a different format, not a different container.

### Part G (original) — Document readers — the first list, superseded by the Part G above

- **text, JSON, XML, CSV, logs** — a real reader with wrapping, search and monospace
  rendering. Cheap and genuinely useful; the Documents screen is full of `.json` today.
- **PDF** — already done.
- **DOCX/XLSX/EPUB** — hand off, unless a library is added. ~~PPTX~~ **is read** since v1.33.0: it is the same container as Word, so the reader already had the hard part.

---

## Part 4 — What I will not do

- I will not claim a format plays when the platform cannot decode it.
- I will not silently show a blank tile; a file with no preview now says why.
- I will not build on unofficial, reverse-engineered endpoints.
- I will not present a format as supported because the *container* parses, when the
  *codec* inside it has no decoder.
