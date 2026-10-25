# What works, and what breaks

**Checked at v1.55.0.** Four sources, and they disagree in an important way — which is the finding.

1. **The code**, read and swept for the defect classes this project keeps hitting.
2. **Your screen recording** (31.6 s, 12 frames pulled out of it).
3. **Your nine screenshots.**
4. **The diagnostics log** you exported earlier.

## First, the thing that matters most

**Your screenshots are from v1.46.0.** The Settings screenshot says so on the About row:
*"ProGallery 1.46.0"*. The app is now at **v1.55.0** — nine releases later — and **most of what your
screenshots show as broken has already been fixed.**

| What your screenshot shows | State now |
| --- | --- |
| *"The update server did not answer: Failed to connect to api.github.com…"* | **Your network**, not the app — and the dead end is fixed in v1.48.0 (it now offers **Open the page** and says what to try) |
| The red **"Photo locations"** row, offering **Allow** | **Removed in v1.54.0.** The permission was declared, offered, and never used — `setRequireOriginal` appears nowhere. It is the same row your diagnostics showed being pressed five times in one second |
| *"Could not move to Recently Deleted — nothing was deleted"* | **Already fixed.** The current message appends the reason: *"Could not copy to Recently Deleted, so nothing was deleted. <reason>"* — the old wording does not exist in the code any more |
| The **"youtu.be"** player message naming `.mpg` as unplayable | **Fixed today.** See below |

**So the honest answer to "what breaks" is: the app you are running is nine releases old, and the
thing stopping you updating is your network.** The direct APK link is in the last message.

## What the recording shows working

Pulled every two seconds from your 31.6-second recording:

- **The Photos grid renders.** Thumbnails, many of them, including ones that used to fall back to
  icons. The preview pipeline works.
- **A photo opens full-screen.** Tapping a tile goes to the viewer and shows the picture.
- **The drawer opens**, and lists *Photos · Videos · Favourites · Recently deleted · Private ·
  Storage*. **"Storage" is there** — that is the summary added in v1.53.0, so this part of the
  recording is from a build that has it.
- **Swipe between photos works** — the pager moves and the bottom bar appears.

Nothing in the recording looks broken. There is no crash, no blank screen, no stuck spinner.

## What the screenshots show working

- **Documents** lists files with type icons.
- **Audio** lists files with music-note placeholders — correct, since most `.aac` files carry no
  artwork to show.
- **Access** reports Photos and videos, Music and audio, All files access and granted folders all
  **Allowed**, with *"5 folders are being scanned"*. The permission plumbing works.
- **The file browser** and a **video player** with seek controls.

## The one real bug found today

**The player contradicted the capability screen, about the same file.**

Your `youtu.be` screenshot showed the player saying:

> *"The container is not one Android can read. MPEG program streams (.mpg), Windows Media (.wmv) and
> Flash (.swf) have no support on Android at all."*

That sentence was **hardcoded** in `PlaybackFailure`, and **Part D made it wrong**: MPEG program
streams are handed to the device's own parser from Android 11 and up, so the capability screen says
*"opens, with a caveat"* while the player still said *"no support at all"*.

**One table is supposed to settle this**, and the player was not reading it. Fixed: the message now
comes from `CapabilityMatrix`, so the two cannot disagree — and there is a test that walks **every**
extension the app knows and fails if the player calls a format unsupported that the matrix accepts.

## What the code sweep found

Swept for the defect classes this project has hit before:

| Check | Result |
| --- | --- |
| A **setting** stored, shown and read by nothing | **None.** The checker enforces it |
| A **declared event** with no call site | **None.** The checker enforces it |
| A **private function** never called | **None** |
| A **runtime permission** asked for and unused | **None left** — `ACCESS_MEDIA_LOCATION` was the one, removed in v1.54.0 |
| **TODO / FIXME / HACK** left in the source | **None** |
| A **hardcoded list duplicating a table** | **One**, the player's — fixed today. Everything else lives inside `CapabilityMatrix`, which is where it belongs |

## What I cannot tell you

**I have never run this app on a device.** Everything above about the code is read, compiled, and
tested in CI — never tapped. Your recording and screenshots are the only device evidence in this
whole project, and they are from an old build.

**So the one thing worth doing is: update to v1.55.0 and record the same walk-through again.** If
something breaks, it will break in the current build, and the diagnostics log will say what.

## Still open, honestly

- **Undo for file operations** (C5's other half). A delete through the system dialog cannot be undone
  at all, and the app must say which actions can be reversed rather than offering an undo that fails.
- **Incremental rescan.** Every scan re-walks everything.
- **The loop detector that never fired** — five permission attempts in one second against a threshold
  of three, and no loop event. I still cannot explain it. It is written down rather than guessed at.
