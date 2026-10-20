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
