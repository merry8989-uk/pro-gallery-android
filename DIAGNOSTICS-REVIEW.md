# What a real device's diagnostics said

**The first real device data in this project.** Everything before it was CI: compilation, unit tests,
lint and dex diffs. This is a 500-event log exported from the phone, and it found things CI cannot.

Source: `progallery-diagnostics.md.txt`, 500 events across five sessions, 21–23 October 2026, from
version 1.46.0.

## The shape of the log

| | |
| --- | --- |
| **trace** | **316** |
| info | 137 |
| warn | 39 |
| debug | 8 |
| error | **0** |
| fatal | **0** |

No crashes. No errors. That is worth saying first, because it means everything below is about quality
rather than breakage.

---

## 1. Every screen was logged under two names ✅ **fixed**

| The screen | Logged as |
| --- | --- |
| Photos | `photos` **and** `Photos` |
| Audio | `audio` **and** `Audio` |
| Documents | `documents` **and** `Documents` |
| Settings | `settings` **and** `Settings` |

**Cause, found in the code.** `ProGalleryNavHost` logged `route?.destination?.route` — the **route**
(`photos`, `settings`) — while `SettingsViewModel` and `PermissionDiagnosticsViewModel` logged a
**display name** of their own (`"Settings"`, `"AccessDiagnostics"`). Two sources for one fact, which
is the same defect this project keeps finding in other shapes.

**Why it matters more than it looks.** The weekly review — the thing built to decide what to improve
next — counts screens. Every count was **halved**, and `AccessDiagnostics` appeared under a name no
route has at all. The data meant to guide the work was itself the thing most in need of it.

**The fix.** `ScreenName.of(route)` is now the one place a route becomes a name, used by the
navigation host and by both screens that had their own. It is pure, and a test asserts that every
route has exactly one name, that a route with arguments matches on the route alone, and that an
unknown route records **nothing** rather than guessing.

## 2. 63% of the log was one event ✅ **fixed**

`trace_marker` — **316 of 500 events** — written once per **batch** of media rows read. A batch is an
implementation detail; the query is the step. With a 500-row batch size and 32 items, that is a
marker for every step of every walk.

**The fix.** One marker per **query**, carrying how many batches it took. The trace level still says
which part of the work happened — which is what the level exists for — without drowning everything
else.

## 3. A burst of permission events, and a detector that did not fire ⚠️ **partly explained**

```
2026-10-21T19:16:50Z  14 events in one second
  5 × access_fix_requested
  4 × access_fix_result     (outcome=no_op every time)
  5 × permission_state_resolved
```

Five requests for `media_location`, each answered `denied`, each recorded as `no_op`. The permission
genuinely is not granted, so the outcome is honest — but the app asked **five times in one second**,
and the log shows it happening on four separate occasions.

**There is already a loop detector**, `PERMISSION_LOOP`, with a threshold of three. **It never
fired.** I could not work out why from the code, and I am not going to guess: the counter is a
ViewModel field, it is incremented on every attempt, and it is never reset — which by reading says it
should have fired at the third attempt in that second.

**What I did fix** is the fragility next to it: the check was `fixAttempts == LOOP_THRESHOLD`. With
`==`, the event is missed for good the moment the count steps over the threshold, and that has no
upside. It is `>=` now, and recorded once rather than on every attempt past it.

**Still open:** why the count did not reach three. Worth another look with a device in hand.

## 4. The log and the screen disagreed about what was tried ✅ **fixed**

39 `network_error` events, all `endpoint=releases.atom`, in bursts of three, with `latency_ms` of
137–170 — a **fast** failure, which is a refused connection rather than a timeout.

Three requests in three seconds looked like a retry loop. **It is not.** The only caller is the
button, and the screenshot from the same day shows one "Check for updates" followed by two "Try
again" taps. The app did what it was asked, three times.

The real finding is narrower: the client tries `releases.atom` and then falls back to
`api.github.com`, and **only the feed's failure was recorded**. So the log said one endpoint was
tried while the dialog on the screen named the other. **A log that disagrees with the screen is worse
than a shorter one.** The failure now names both.

## 5. A performance signal worth keeping ✅ **noted**

`time_to_first_value` for Audio: **3576 ms** on one visit, 423 ms on another. Photos: 792 ms. A
3.5-second first paint is the kind of thing this event exists to catch, and it is the only number in
the log that suggests something a person would actually feel.

**Not investigated yet.** One slow visit is not a pattern, and the honest next step is more data
rather than a guess.

## 6. Two smaller things, noted and not changed

- **`memory_report` logs `memory_total_mb=256` then `64`.** Both are the heap, which legitimately
  varies — but a field called *total* that changes reads like a bug. A naming problem, not a data one.
- **`drop_off` fires on `media_player?uri={uri}`** — a route, with the argument in it. The screen name
  fix above does not touch this event, and it should: the same argument-in-the-name problem is there.

---

## What this says about the checks

Every previous release was verified by compiling and by searching the APK for strings. Both were
honest and both were **blind to this**: a log that double-counts every screen, a trace level that
swamps its own output, and a loop detector that does not detect. None of it fails a build.

**The log is the only instrument in this project that can see the app as it is actually used**, and
this is the first time it has been read. That is worth doing on every release from here.
