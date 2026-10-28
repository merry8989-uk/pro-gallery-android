# Audit: why "a lot isn't working", and what I can actually prove

**At v1.57.0.** This audit is different from the last two: those checked that the code was *wired*.
This one asked whether the **paths a person actually walks** work — and found that **the app cannot
tell us when they do not**.

## The finding that matters

**Four of the app's five viewers logged nothing when they failed.**

| Viewer | Failure recorded in the log? |
| --- | --- |
| Text (`.txt`, `.md`, `.json`, `.docx`, `.csv` …) | **no** |
| Image (`.jpg`, `.png`, `.webp` …) | **no** |
| PDF | **no** |
| Archive (`.zip`) | **no** |
| Video and audio | **yes** |

The text viewer put *"This file could not be opened"* on the screen and told the log nothing. So did
the image viewer, the PDF viewer and the archive viewer.

**Why that is the root of everything else:** the diagnostics export is the one artefact meant to
explain what is broken. Every export you have sent me was **silent about exactly the failures you
were reporting** — a file that would not open left no trace at all. So every question about "why does
this not work" had to be answered by reading code and guessing.

**And I guessed twice, and was wrong twice** — see below.

## Fixed in this release

- **All four viewers now record a failed open**, with the **extension**, the **reason in words**, and
  whether the address was even readable. Nothing about the file's contents, name or path — a log is
  a thing people send.
- **A checker rule was wrong and is fixed.** It reported a missing import for a **fully-qualified**
  use, which needs none. That is the second time this rule has cried wolf; it is now tested both
  ways — it ignores `com.example.Foo()` and still catches a genuinely missing `import`.

## My two wrong hypotheses, and why I am telling you

**Hypothesis 1 — the URI encoding was broken.** `Routes.kt` encodes every address with
`java.net.URLEncoder`, which encodes a space as `+`. I was sure that was it: "works in some folders,
not others" is exactly what a space in a folder name would cause.

**It was wrong.** The viewers decode with `URLDecoder`, which turns `+` back into a space. The pair
was **consistent** — it worked. I had already replaced all sixteen uses before I checked, and
reverted every one.

**Hypothesis 2 — non-media files never reach the browser.** `MediaStore` does not index `.md`,
`.txt` or `.zip`, so I expected they never appeared.

**Also wrong.** The data source queries `MediaStore.Files` rather than just images and video, **and**
the browser merges the deep-scan index on top.

Both are written down because a guess presented as a finding is worse than no finding — and I have
done that to you once already in this project.

## What is actually true about "some folders"

This part is not a bug and no update will fix it:

- **Another app's folder cannot be read by any app.** `Android/data/com.whatsapp`, `Android/obb`,
  another app's private storage — on a non-rooted phone that permission **does not exist**. An app
  that claims to show those files is lying.
- **A folder you grant works.** Granting a folder, or allowing all files access, is what changes the
  answer — and the Access screen already says which of those the app has.

## What I need to find the rest

The app can now tell us. **Do this, in this order:**

1. **Update to this build.**
2. **Open the file that fails** — let it fail.
3. **Settings → Save diagnostics**, and send that file.

The export will now contain a line per failure with the **extension** and the **reason** — and that
turns "a lot isn't working" into a list I can fix one by one, instead of guesses.

## Still open, honestly

- **Undo** for file operations; a delete through the system dialog cannot be undone at all.
- **Incremental rescan.**
- **The permission loop detector that never fired** — five attempts in one second against a threshold
  of three, no loop event. Still unexplained, still written down rather than guessed at.
