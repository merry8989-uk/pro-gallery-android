package com.progallery.app.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app tells Media3 a file is.
 *
 * The rule the tests are built around: a wrong hint is worse than none, because it makes Media3
 * pick the wrong extractor and fail on a file that would otherwise have played. So a mapping
 * must be certain to be here, and everything else must fall through to sniffing.
 */
class PlaybackMimeTest {

    private fun hint(mime: String? = null, name: String? = null, uri: String? = null) =
        PlaybackMime.hintFor(mime, name, uri)

    // ---- the item's own type wins when it is a real one ----

    @Test
    fun aRealDeclaredTypeIsPassedOn() {
        assertEquals("video/mp4", hint(mime = "video/mp4"))
        assertEquals("audio/mpeg", hint(mime = "audio/mpeg"))
        // Normalised to lower case, because MIME types are case-insensitive and one spelling
        // throughout means the hint can be compared and logged without surprises.
        assertEquals("application/x-mpegurl", hint(mime = "application/x-mpegURL"))
    }

    @Test
    fun aDeclaredTypeIsNotCaseSensitive() {
        assertEquals("video/mp4", hint(mime = "VIDEO/MP4"))
    }

    @Test
    fun theItemTypeBeatsTheFileName() {
        // The type came from the system; the name is a name.
        assertEquals("video/mp4", hint(mime = "video/mp4", name = "clip.mkv"))
    }

    // ---- a type that says nothing is not an answer ----

    @Test
    fun aGenericTypeIsTreatedAsAbsent() {
        assertFalse(PlaybackMime.isUsable("application/octet-stream"))
        // A slash-star inside a string is perfectly valid Kotlin; it is inside a *comment* that
        // it opens a nested comment and breaks the file. Written plainly here.
        assertFalse(PlaybackMime.isUsable("*/*"))
        assertFalse(PlaybackMime.isUsable("video/*"))
        assertFalse(PlaybackMime.isUsable(""))
        assertEquals("video/mp4", hint(mime = "application/octet-stream", name = "clip.mp4"))
    }

    // ---- the certain mappings ----

    @Test
    fun theCertainContainersAreMapped() {
        assertEquals("video/mp4", hint(name = "a.mp4"))
        assertEquals("video/x-matroska", hint(name = "a.mkv"))
        assertEquals("video/webm", hint(name = "a.webm"))
        assertEquals("audio/flac", hint(name = "a.flac"))
        assertEquals("audio/ogg", hint(name = "a.opus"))
        assertEquals("application/dash+xml", hint(name = "a.mpd"))
    }

    @Test
    fun anAdaptivePlaylistGetsTheTypeThatChoosesItsSource() {
        // The type is not decoration here: it is what decides which source Media3 builds.
        assertEquals("application/x-mpegURL", hint(uri = "https://host/live/stream.m3u8"))
        assertEquals("application/x-mpegURL", hint(uri = "https://host/live/stream.m3u"))
    }

    // ---- when there is no answer ----

    @Test
    fun anUnknownExtensionIsLeftToSniffing() {
        // Null is a decision, not a failure: it is the behaviour that was there before.
        assertNull(hint(name = "a.qqq"))
        assertNull(hint(uri = "https://host/media"))
    }

    @Test
    fun aContentUriWithNoExtensionIsLeftToSniffing() {
        assertNull(hint(uri = "content://media/external/video/media/42"))
    }

    @Test
    fun aStreamAddressIsReadBeforeItsQueryString() {
        // Reading past the '?' would look for an extension inside a token.
        assertEquals("video/mp4", hint(uri = "https://host/clip.mp4?token=abc&expires=1"))
        assertEquals("audio/mpeg", hint(uri = "https://host/track.mp3#fragment"))
    }

    @Test
    fun aFileNameWithNoDotIsLeftToSniffing() {
        assertNull(hint(name = "README"))
        assertNull(hint(uri = "https://host/media/clip"))
    }

    @Test
    fun aDotFileIsNotAnExtension() {
        assertNull(hint(name = ".gitignore"))
    }

    @Test
    fun aTrailingDotIsNotAnExtension() {
        assertNull(hint(name = "clip."))
    }

    @Test
    fun somethingThatIsNotAnExtensionIsRejected() {
        assertNull(hint(name = "clip.averylongextension"))
        assertNull(hint(name = "clip.m p4"))
        assertNull(hint(name = "clip.m-p4"))
    }

    @Test
    fun theNameIsUsedWhenTheUriSaysNothing() {
        assertEquals("video/mp4", hint(name = "clip.mp4", uri = "content://media/42"))
    }

    @Test
    fun nothingAtAllIsLeftToSniffing() {
        assertNull(hint())
    }

    @Test
    fun everyMappedExtensionRoundTrips() {
        // Whatever the table maps must be a type the app would itself accept as usable, or the
        // hint would be dropped on the next pass.
        listOf("mp4", "mkv", "webm", "avi", "flv", "ts", "mp3", "m4a", "flac", "wav", "opus", "m3u8", "mpd")
            .forEach { ext ->
                val mapped = hint(name = "clip.$ext")
                assertTrue("$ext should have a mapping", mapped != null)
                assertTrue("$ext maps to something unusable: $mapped", PlaybackMime.isUsable(mapped!!))
            }
    }
}
