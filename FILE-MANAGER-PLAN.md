# Advanced File Manager — the plan

**Written at v1.48.0.** Every "already there" and "missing" below was checked by searching the source
for it; nothing here is remembered.

## What the app already is

Not a bare gallery. It has, verified in the code:

- **A folder browser** — MediaStore collections plus a walk of granted folders, with `Android/media`
  reachability worked out and reported (`ScanScopeBuilder`).
- **Readers for documents and text** — `DocumentFormats` and `TextFormats`, with the formats each can
  actually read named rather than assumed.
- **A capability matrix** — for every extension the app knows, one verdict: *plays here* / *plays with
  a caveat* / *needs another app*, and the reason (`CapabilityMatrix`).
- **One player for audio and video** (`MediaPlayerScreen`), with decoder fallback, and since v1.47.0
  the device's own parser for MPEG program streams.
- **Image decodability worked out per format** (`ImageDecodability`), including the ones the platform
  cannot render, which is why they show a type icon instead of a blank tile.

## What is genuinely missing

- **No file tree.** Folders are browsed as collections, not as a hierarchy.
- **No new folder, copy, move, or in-app delete of an arbitrary file.** `MediaActions.renameRequest`
  exists and goes through the system dialog; the rest do not exist at all. The app's own trash is a
  gallery feature, not a file manager's.
- **No storage summary.**
- **No single dispatch point** that takes *any* file and opens the right viewer. Each screen decides
  for itself, which is how a format ends up with no way in.

---

## Part A — the name, and the update path ✅ **done, v1.48.0**

- The app is **Advanced File Manager**.
- The update check no longer dead-ends: a failed check offers **Open the page**, and the message says
  what is usually wrong (the network, not the app) and what to try.

**The application id stays `com.progallery.app`.** Changing it would install a *second* app, leave the
first one's data behind, and never update over it — so the name changes and the identity does not.
That is the difference between a rename and a new app.

## Part B — the file tree ⬜

A real hierarchy over what the app can actually reach: the granted roots, the app's own index, and
`Android/media`. Not a pretend root that shows an empty `/` — every node is something the app was
allowed to read, and a folder it cannot open says so rather than appearing empty.

- **B1** — a pure tree model over the existing scanner output, unit tested.
- **B2** — the tree screen, with the storage summary on it (see Part E).
- **B3** — breadcrumbs, and "go to folder" from a result.

## Part C — file operations ⬜

Honest about the platform: on Android 11+ an app **cannot** rename, move or delete another app's file
directly. The system dialog can, and `MediaActions.renameRequest` already uses it. Every operation
here goes through that door or through SAF, and none of them claims to bypass it.

- **C1** — **new folder**, via SAF in a granted tree.
- **C2** — **rename**, through the system dialog, with the result reflected in the index.
- **C3** — **copy** and **move**, within a granted tree; across trees means copy-then-delete, and the
  UI says so before it starts.
- **C4** — **delete**, through the system dialog, with the app's trash offered first when the file is
  one the app owns.
- **C5** — a progress and undo path, because a half-moved file is worse than a refused one.

## Part D — one way in, for every format ⬜

The dispatch the app does not have. Given any file, decide — from `CapabilityMatrix` — which viewer
opens it, and **say so plainly when none can**.

- **D1** — a single `FileOpener` that maps an extension to a viewer, with the matrix as its only
  source of truth, unit tested against every extension in `FileTypeRegistry`.
- **D2** — readers for the text-shaped formats the app does not render yet: `.json`, `.js`, `.html`,
  `.md`, `.xml`, `.csv`, `.yaml`, `.kt`, `.py`, and the rest — with syntax highlighting only where it
  is real, and plain monospace everywhere else.
- **D3** — `.svg` rendered as an image (Coil's SVG decoder is already a dependency).
- **D4** — the formats nothing on Android can render — ASF, SWF, RealMedia — open the hand-off, and
  the screen says why, as the matrix already does.

**What "every format" honestly means.** The app can render what the platform and its own readers can
render. It will not claim the rest, and the matrix's third verdict exists exactly so that a refusal
is informative instead of silent.

## Part E — storage summary ⬜

By kind, by folder, and what is recoverable — from the index the app already keeps, not from a walk
every time the screen opens.

## Part F — permissions, honestly ⬜

The request was "give every permission". Here is what that would actually do, and what this part does
instead.

- **Most permissions cannot be granted to any app.** Reading another app's `Android/data` on Android
  11+ is refused by the OS to every non-rooted app — it is not a permission that exists. Reading
  deleted internal-storage data is the same.
- **Some are refused by policy** for an app distributed this way, and asking for them all is how an
  app gets reported and removed.
- **`INTERNET` is the whole of "full network"** for this app. There is no broader one; the only other
  network permission is `ACCESS_NETWORK_STATE`, which reports whether a connection exists.
- **What this part does:** each permission the app *uses* is asked for at the moment it is needed,
  with the reason on the Access screen — which already exists and already says what each access is
  for and how to change it. A permission the app does not use is a permission it does not ask for,
  because a list of grants nobody can evaluate is not informed consent.

---

## Order

**A** ✅ → **D** (the dispatch is what makes "every file has a viewer" true, and it is mostly wiring
the matrix into one place) → **B** (the tree) → **C** (the operations, the largest and the most
platform-bound) → **E** → **F**.
