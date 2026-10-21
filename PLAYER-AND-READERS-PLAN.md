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
| SVG, SVGZ | **No decoder.** Needs a library (planned, Part 5) |
| TIFF, PSD, XCF, JXL, JPEG-2000, ICO, TGA, PCX, PPM family | **No decoder on Android** |
| EPS, AI, INDD, CDR, Sketch | **No decoder**, and no practical one |

### Documents

| Format | Verdict |
|---|---|
| PDF | **Built-in reader** (the platform's own renderer) |
| Plain text, JSON, XML, CSV, logs | **Readable** — a reader for these is planned (Part 7) and is easy |
| DOCX, XLSX, PPTX, ODT, EPUB | **No reader.** Needs a library; hand off to another app |

---

## Part 3 — The parts, in order

### Part A — Truth in the format layer ✅ **done, v1.15.0**

The four bugs above. Nothing here is a new feature; it is the app no longer lying about
what it can do.

### Part B — One dynamic player for audio and video ⬜ next

A single `MediaPlayerScreen` that takes any media address and lets Media3 pick the
extractor and decoder:

- the MIME type is passed explicitly, so the right extractor is chosen instead of sniffed;
- `setEnableDecoderFallback(true)` plus extension-renderer preference, so a missing
  hardware codec falls back to software;
- the real reason on failure, with **"Try another app"** offered;
- position remembered, and audio and video handled by the same surface.

This replaces the four separate players (video, audio, stream, WebDAV) over time. They
stay until the new one covers what they do — the stream and WebDAV players carry sign-in
handling the library player has no business knowing about.

### Part C — A capability screen ⬜

The matrix above, in the app, generated from the same pure tables the player uses. The app
then *tells* you that `.wmv` needs another app, rather than failing when you try.

### Part D — The formats Media3 cannot parse ⬜ **decision needed**

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

### Part E — WhatsApp and `Android/media` visibility ⬜ **likely the real cause**

WhatsApp media lives under `Android/media/com.whatsapp/WhatsApp/Media/…`. Since Android
11, **MediaStore does not expose those files to other apps**. No decoder work will make
them appear, because the app is never given them in the first place. This is very likely
the root cause of "many `.jpg` from the WhatsApp folder do not show", and it is also where
WhatsApp stickers live.

The app already has the two legitimate routes — a user-granted folder (Deep Scan) and
all-files access — and an Android-folders screen. What is missing is folding those results
**into the main gallery** with thumbnails, so a WhatsApp photo appears next to a camera
photo instead of in a separate list. That is this part.

### Part F — Dynamic image reader ⬜

- an **SVG** decoder (a small library), so `.svg` renders instead of "no decoder";
- a **sticker viewer**: transparent background, looping, for animated WebP and GIF;
- a **sticker grid** once Part E makes the files visible;
- a decision on TIFF and JPEG-2000, which have decoders available as libraries but are
  large for how rarely they are used.

### Part G — Document readers ✅ **done, v1.19.0** (text/JSON/CSV/log/code)

Remaining in this part: DOCX/XLSX/PPTX/EPUB still hand off, unless a library is added.

### Part G (original) — Document readers ⬜

- **text, JSON, XML, CSV, logs** — a real reader with wrapping, search and monospace
  rendering. Cheap and genuinely useful; the Documents screen is full of `.json` today.
- **PDF** — already done.
- **DOCX/XLSX/PPTX/EPUB** — hand off, unless a library is added.

---

## Part 4 — What I will not do

- I will not claim a format plays when the platform cannot decode it.
- I will not silently show a blank tile; a file with no preview now says why.
- I will not build on unofficial, reverse-engineered endpoints.
- I will not present a format as supported because the *container* parses, when the
  *codec* inside it has no decoder.
