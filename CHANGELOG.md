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
