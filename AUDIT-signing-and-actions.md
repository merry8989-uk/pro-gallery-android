# Release signing and Actions permissions — audit

**At v1.61.0.** The audit report's P1 item *"Verify release signing and GitHub Actions permissions"*.
This is what the files actually do, what was changed, and the one thing I could not verify.

## What was wrong, and is now fixed

### 1. CI inherited its permissions instead of declaring them
`release.yml` declared `permissions: contents: write` — needed, because it publishes a release.
`android.yml` declared **nothing**, so it ran with whatever the repository default happened to be.
A repository set to "read and write" would have given the build job the ability to push code, and
nobody would have noticed.

**Now** `android.yml` declares `contents: read` explicitly. CI reads the code, builds it and uploads
artifacts; it cannot write to the repository.

### 2. The keystore stayed on disk for the rest of the job
Both workflows decode the signing keystore into `$RUNNER_TEMP/progallery.p12`, because Gradle needs a
file and not a secret. Nothing removed it. The runner is destroyed afterwards either way, so this was
not a leak to anywhere — but "it will be destroyed anyway" is not a reason to leave a private key on a
disk while other steps run.

**Now** a final step removes it. It is placed **after** the step that needs it — the first attempt put
it between the debug build and the signed AAB build in `release.yml`, which would have deleted the
keystore **before** signing and failed the release. That was caught by reading the step order back.

### 3. Secrets are written to files, never printed
The four signing values are `echo`ed **into** a properties file, and no step enables `set -x`. So no
credential reaches the log. This was already correct; it is written down here because it is the thing
that most often breaks when a workflow is edited.

## What I could not fix, and why

**Every action is pinned to a moving tag, not a commit.**

| Action | Pinned to |
| --- | --- |
| `actions/checkout` | `@v4` |
| `actions/setup-java` | `@v4` |
| `actions/cache` | `@v4` |
| `actions/upload-artifact` | `@v4` |
| `android-actions/setup-android` | `@v3` |
| `gradle/actions/setup-gradle` | `@v4` |
| `reactivecircus/android-emulator-runner` | `@v2` |
| `softprops/action-gh-release` | `@v2` |

A tag can be moved. If any of those repositories were compromised, the next run would execute
whatever the tag pointed at — with the signing secrets available to it.

**Pinning to a commit SHA is the fix, and I have not done it, because I cannot verify the SHAs from
here.** Guessing a SHA would produce a workflow that either fails or, worse, pins to the wrong commit
and looks correct. This needs one person with network access to resolve each tag to its commit and
paste the forty characters in. It is a five-minute job and it is the single highest-value hardening
left in this file.

## Still needs a device or a human

- **The minified release build has never been run on a physical device.** R8 renames everything it is
  allowed to; a class reached only by reflection that R8 removed would crash only in the release
  build. `isMinifyEnabled = true` with `isShrinkResources = true` has shipped since early on and been
  installed by one person, which is weak evidence and not none.
- **The permission flows across API levels** are untested below and above the emulator's image.
