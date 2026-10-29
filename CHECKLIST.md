# Checklist — what is done, what is left, and what nobody has listed

**At v1.62.0.** Three parts, and the third is the one worth reading: the first two are the audit's own
list, the third is what the audit never covered and what a person would still hit.

Who can do each item is marked, because most of what is left is **not** mine to do:

- **[me]** — I can do it here, and it ships as a release.
- **[you]** — needs your hands, your phone, or a decision only you can make.
- **[device]** — needs a physical phone. CI runs an emulator, which is not the same thing.

---

## Part 1 — The audit's P0: done

| | Item | Where |
| --- | --- | --- |
| ✅ | Photo overwrite made failure-safe — the image is encoded before the file is opened | v1.59.0 |
| ✅ | Legacy export collisions — unique names, `createNewFile`, and only a file this call created is ever deleted | v1.59.0 |
| ✅ | HTTPS enforced for credentialed WebDAV, before any I/O | v1.59.0 |
| ✅ | Regression tests for the three | v1.59.0 |
| ✅ | Credentials and secrets never enter diagnostics — path redaction plus a test both ways | v1.59.0, v1.60.0 |

## Part 2 — The audit's P1 and P2

### Done

| | Item | Where |
| --- | --- | --- |
| ✅ | WebDAV responses bounded while reading (4 MB, cancellation per chunk) | v1.61.0 |
| ✅ | Archive aggregate-size and entry-count limits | v1.57.0 |
| ✅ | Diagnostics call-site review — four of five viewers were failing silently | v1.58.0 |
| ✅ | Trash retention tested; purge worker's failure detail | v1.62.0 |
| ✅ | Dependency vulnerability and licence checks in CI | v1.62.0 |
| ✅ | CI declares `contents: read`; the decoded keystore is removed after use | v1.62.0 |

### Left

| | Item | Who | Why it is not done |
| --- | --- | --- | --- |
| ⬜ | Pin all eight actions to commit SHAs | **[you]** | I cannot resolve SHAs from here, and a guessed SHA is worse than an honest tag. Five minutes in a browser. `AUDIT-signing-and-actions.md` has the list. |
| ⬜ | Run the **minified release** build on a physical device | **[device]** | R8 renames everything it may. A class reached only by reflection would crash only in release. The debug APK you install is **not** the minified build. |
| ⬜ | Permission flows across API levels | **[device]** | Emulator images cover one or two levels; the app supports 26–36. |
| ⬜ | Scan benchmarks at 10k / 50k / 100k files | **[device]** | Needs a phone with that many files. |
| ⬜ | Large images and low-memory devices | **[device]** | |
| ⬜ | Archive stress tests | **[me]** | Can be done here with generated archives. |
| ⬜ | Accessibility and large font sizes | **[device]** | |
| ⬜ | Release signing verification | **[you]** | Confirm the four repository secrets are still set and that `signer_cert.der`'s SHA-256 is `7f5028…1e68`. |

---

## Part 3 — What the audit never listed, and what you have actually reported

This is the part the checklist needed. The audit was written from **reading code**; these come from
**using the app** and from shipping it.

### 3a. Things you asked for that are still open

| | Item | Who | State |
| --- | --- | --- | --- |
| ⬜ | **Formats that "do nothing"** — video, audio, `.webp`, `.opus`, WhatsApp `.jpeg`, stickers | **[you]** then **[me]** | **Blocked on one diagnostics export.** Since v1.60.0 a failed open records the extension and the reason, so the next export names the formats. Before that, every guess I made was wrong — twice, in writing. |
| ⬜ | **The UI is scattered** — **37 screens**, **22 drawer entries**, and three separate ways to reach files (All files, File tree, Android folders) | **[me]** | Planned, not started. Four groups (*Library · Browse · Tools · Settings*), one screen shell, a home dashboard. Three or four releases. **22 entries is a list nobody reads to the end** — the drawer should be six. |
| ⬜ | Built-in viewer/player/editor for **every** format | **[me]** | Viewers exist for text, image, PDF, archive, video, audio; editors for photo, video, audio. Missing: a text/PDF editor, and codec coverage the device does not have. |
| ⬜ | Open in another app **after** the built-in one | **[me]** | Already there — `FileOpener` hands off with a reason, and "Open with" is on every viewer. |

### 3b. Never on any list, and needed before this is a real app

| | Item | Who | Why it matters |
| --- | --- | --- | --- |
| ⬜ | **You cannot update the app** | **[you]** | Your network sends `github.com` and `api.github.com` nowhere useful — your own diagnostics show it, and the screenshot's IP `20.207.73.85` is not GitHub's. **No code change fixes this.** Fix the DNS/network, or install from the direct release link each time. |
| ⬜ | **A privacy policy** | **[you]** then **[me]** | Required by Play, and by the DPDP Act 2023 for an app that collects diagnostics. There is a consent gate and a redaction rule, but **no document** saying what is collected, why, and for how long. |
| ⬜ | **Play Store data-safety form** | **[you]** | Must match the app's real behaviour. It currently would have to declare "diagnostics, anonymised, optional" — which is true, and needs saying. |
| ⬜ | **Store listing** — screenshots, description, content rating | **[you]** | The app has a name and an AAB and nothing else a store needs. |
| ⬜ | **The app is English-only** | **[me]** | You write to me in Hindi and the app speaks English. A Hindi (and Marathi, Tamil…) string set is a real piece of work, not a find-and-replace. |
| ⬜ | **A delete-my-data path** | **[me]** | Diagnostics can be cleared; there is no "delete everything this app stored" button that says exactly what it removes. The DPDP Act expects one. |
| ⬜ | **Crash reporting beyond the phone** | **[you]** | Crashes are recorded locally and only reach me if you export and send. Play Console vitals or a crash SDK would surface them without you noticing — that is a privacy decision, not a technical one. |
| ⬜ | **The Supabase project** | **[you]** | `progallery-logs` is INACTIVE and should be deleted in the dashboard. It is the last piece of a removal that finished in v1.36.0. |
| ⬜ | **The permission loop that never fired** | **[me]** | Your diagnostics showed five permission attempts in one second against a threshold of three, and no loop event. Unexplained since v1.41.0. Written down rather than guessed at. |
| ⬜ | **Undo for file operations** | **[me]** | Deletes go through the system dialog and cannot be undone at all; copy/move have no undo either. |
| ⬜ | **Incremental rescan** | **[me]** | Every rescan is a full scan. Needs its own plan. |

### 3c. Honest weaknesses in how this project is built

| | Item | Who |
| --- | --- | --- |
| ⬜ | **I have never run this app.** Every release is verified by compiling, unit tests, lint and an emulator — never by using it. That is the root of most of the wrong guesses in this project. | **[device]** |
| ⬜ | **Release notes come from commit messages**, and the CHANGELOG silently stopped at 1.56.0 for four releases because a push script asserted before it wrote. Fixed, but the pattern is still there. | **[me]** |
| ⬜ | **Patches fail silently.** A `str.replace` whose anchor does not match does nothing and reports success. It has happened at least six times. Every patch is grepped afterwards now, but it is a manual habit, not a guard. | **[me]** |
| ⬜ | **APK string probes mislead.** I have used "is this string in the dex" as evidence four times this session and been wrong four times — R8 renames classes and strips nothing that is used, but source text and comments are never in a dex. The tests are the evidence; the probes are not. | **[me]** |

---

## What I would do next, in order

1. **You:** export diagnostics after opening the files that fail. That unblocks 3a's first row, which
   is the one thing you have asked for most.
2. **You:** pin the actions to SHAs (five minutes, `AUDIT-signing-and-actions.md`).
3. **Me:** the UI reorganisation, in three or four releases, starting with the navigation structure.
4. **Me:** the privacy policy text and a delete-my-data path — both are needed before a store listing
   means anything.
5. **Device:** the minified release build, once. It is the only untested thing that could crash for a
   user and not for me.
