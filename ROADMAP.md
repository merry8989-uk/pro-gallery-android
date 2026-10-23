# Contributing to ProGallery

Thanks for helping improve ProGallery. Please read this before opening a pull request.

## Ground rules

- **No fake functionality.** Every visible control must do real work or clearly state it is unavailable. Do not add a button that only shows a toast for an unimplemented feature.
- **Respect scoped storage.** No root, no undocumented APIs, no attempts to bypass Android storage restrictions.
- **Never commit secrets.** No API keys, keystores, `local.properties`, or personal media. The `.gitignore` already covers the common cases.
- **Keep the domain layer pure.** `domain/` and the `*EditState` classes must stay free of Android imports so they remain unit-testable.

## Workflow

1. Fork the repo and create a topic branch: `feature/short-name` or `fix/short-name`.
2. Make focused commits with clear messages.
3. Add or update tests for logic you change.
4. Run the checks locally before pushing:

   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

5. Open a pull request describing the change, the motivation, and how you verified it.

## Code style

- Kotlin official style; 4-space indentation.
- Prefer immutable state and Flow over callbacks.
- Public APIs get KDoc. Comments explain *why*, not *what*.
- No new dependency without a note in the PR explaining why it is needed and that it is stable.

## Reporting bugs

Include device model, Android version, the exact steps, what you expected, and what happened. Logcat around the failure helps enormously. Do not attach personal photos.

## Releasing

`tools/check_docs.py` runs in CI and compares the documents' factual claims against the code, so a
release that bumps the version without updating `ROADMAP.md`'s header **fails the build**. That is
deliberate: the header said v1.5.1 while the build was v1.36.0, and nothing noticed for thirty
releases. The fix is one line, and the check also covers the SDK levels, the settings count and
whether any control the Settings screen shows does nothing.

The checkers are plain Python and take seconds:

```
python3 tools/check_kotlin.py   # braces, imports, duplicate companions, events with no call site
python3 tools/check_docs.py     # the docs against the code
```
