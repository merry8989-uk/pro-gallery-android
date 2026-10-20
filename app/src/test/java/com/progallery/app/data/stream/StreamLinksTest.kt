package com.progallery.app.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app will open, and what it refuses, is decided here — so it is pinned down.
 */
class StreamLinksTest {

    private fun parsed(raw: String): StreamLink = StreamLinks.parse(raw).getOrThrow()

    @Test
    fun aPlainHttpsLinkIsAccepted() {
        val link = parsed("https://example.com/videos/clip.mp4")
        assertEquals("https://example.com/videos/clip.mp4", link.url)
        assertEquals("example.com", link.host)
        assertEquals(StreamKind.VIDEO, link.kind)
    }

    @Test
    fun aBareAddressGetsHttps() {
        val link = parsed("example.com/clip.mp4")
        assertEquals("https://example.com/clip.mp4", link.url)
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals("example.com/a.mp4", parsed("   example.com/a.mp4  ").host + "/a.mp4")
    }

    @Test
    fun theKindComesFromTheExtension() {
        assertEquals(StreamKind.VIDEO, parsed("https://e.com/a.MP4").kind)
        assertEquals(StreamKind.VIDEO, parsed("https://e.com/a.mkv").kind)
        assertEquals(StreamKind.AUDIO, parsed("https://e.com/a.mp3").kind)
        assertEquals(StreamKind.AUDIO, parsed("https://e.com/a.flac").kind)
        assertEquals(StreamKind.IMAGE, parsed("https://e.com/a.jpeg").kind)
        assertEquals(StreamKind.PLAYLIST, parsed("https://e.com/live.m3u8").kind)
        assertEquals(StreamKind.PLAYLIST, parsed("https://e.com/manifest.mpd").kind)
    }

    @Test
    fun aQueryStringDoesNotHideTheExtension() {
        // Signed links are the normal case, and the token must not be mistaken for the
        // file's extension.
        val link = parsed("https://cdn.example.com/clip.mp4?token=abc123&expires=99")
        assertEquals(StreamKind.VIDEO, link.kind)
        assertEquals("cdn.example.com", link.host)
    }

    @Test
    fun anAddressWithNoExtensionIsNotRejected() {
        // Not a rejection: Media3 sniffs the content, so it still gets a chance.
        val link = parsed("https://example.com/watch")
        assertEquals(StreamKind.UNKNOWN, link.kind)
        assertEquals("Link", link.kind.label)
    }

    @Test
    fun anEmptyLinkIsRefusedWithAReadableReason() {
        val result = StreamLinks.parse("   ")
        assertTrue(result.isFailure)
        assertEquals("Paste a link first.", result.exceptionOrNull()?.message)
    }

    @Test
    fun aNonHttpSchemeIsRefusedAndSaysWhich() {
        listOf(
            "file:///sdcard/a.mp4",
            "content://media/external/video/media/1",
            "ftp://example.com/a.mp4",
            "javascript:alert(1)",
            "mailto:someone@example.com",
        ).forEach { raw ->
            val result = StreamLinks.parse(raw)
            assertTrue("$raw should be refused", result.isFailure)
            assertTrue(
                "the reason should name the scheme: ${result.exceptionOrNull()?.message}",
                result.exceptionOrNull()?.message?.contains("http") == true,
            )
        }
    }

    @Test
    fun anAddressWithNoHostIsRefused() {
        val result = StreamLinks.parse("https:///a.mp4")
        assertTrue(result.isFailure)
    }

    @Test
    fun aLinkWithSpacesIsRefusedRatherThanGuessedAt() {
        val result = StreamLinks.parse("https://example.com/my video.mp4")
        assertTrue(result.isFailure)
        assertEquals(
            "That does not look like a link the app can open.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun aPortAndPathSurvive() {
        val link = parsed("http://192.168.1.5:8080/media/a.mp3")
        assertEquals("192.168.1.5", link.host)
        assertEquals(StreamKind.AUDIO, link.kind)
        assertEquals("http://192.168.1.5:8080/media/a.mp3", link.url)
    }

    @Test
    fun aTrailingDotIsNotAnExtension() {
        assertEquals(StreamKind.UNKNOWN, parsed("https://example.com/weird.").kind)
    }
}
