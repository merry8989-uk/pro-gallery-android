package com.progallery.app.data.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The failure this fixes: a `youtu.be` link was pasted into "Stream from link", and the app
 * answered that the *container* was one Android cannot read, naming MPEG program streams and
 * Windows Media. None of that was true — the address was a web page.
 */
class StreamLinkPageTest {

    @Test
    fun aShortVideoLinkIsRecognisedAsAPage() {
        assertTrue(StreamLinkPage.looksLikeAPage("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://m.youtube.com/watch?v=x"))
    }

    @Test
    fun aServiceDomainWithASubdomainIsStillRecognised() {
        assertTrue(StreamLinkPage.looksLikeAPage("https://music.youtube.com/watch?v=x"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://www.facebook.com/watch/?v=1"))
    }

    @Test
    fun aPageShapedPathOnAnyHostIsRecognised() {
        // Not only the known services: /watch and /shorts mean "a page about a video" anywhere.
        assertTrue(StreamLinkPage.looksLikeAPage("https://example.com/watch/12345"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://somewhere.net/shorts/abc"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://cdn.example.org/embed/xyz"))
    }

    @Test
    fun aDirectFileLinkIsNotCalledAPage() {
        listOf(
            "https://example.com/media/clip.mp4",
            "https://cdn.example.org/video.webm",
            "https://host.example/live/stream.m3u8",
            "https://host.example/manifest.mpd",
            "https://host.example/track.mp3",
            "https://host.example/photo.jpg",
        ).forEach { url ->
            assertFalse("$url is a file, not a page", StreamLinkPage.looksLikeAPage(url))
        }
    }

    @Test
    fun aDirectFileOnAStreamingHostIsStillAFile() {
        // A file served by a big host must not be warned about because of who serves it. The
        // first version of this classifier got this wrong: it decided on the host alone, so a
        // perfectly playable `.mp4` on a video site was called a page.
        assertFalse(StreamLinkPage.looksLikeAPage("https://vimeo.com/download/clip.mp4"))
        assertFalse(StreamLinkPage.looksLikeAPage("https://drive.google.com/file.mp4"))
        assertFalse(StreamLinkPage.looksLikeAPage("https://youtube.com/media/clip.mkv"))
    }

    @Test
    fun anExplicitMediaExtensionBeatsTheHost() {
        // The rule, stated plainly: what the address ends in is stronger evidence than the
        // reputation of the domain serving it.
        listOf("clip.mp4", "clip.webm", "live.m3u8", "manifest.mpd", "track.mp3", "photo.webp")
            .forEach { file ->
                assertFalse(
                    "$file is a file, whatever host serves it",
                    StreamLinkPage.looksLikeAPage("https://youtu.be/$file"),
                )
            }
    }

    @Test
    fun theQueryStringDoesNotConfuseIt() {
        assertFalse(StreamLinkPage.looksLikeAPage("https://host.example/clip.mp4?token=abc&expires=1"))
        assertTrue(StreamLinkPage.looksLikeAPage("https://example.com/watch?v=1&t=30"))
    }

    @Test
    fun aBareHostWithNoPathIsNotAPage() {
        assertFalse(StreamLinkPage.looksLikeAPage("https://example.com"))
        assertFalse(StreamLinkPage.looksLikeAPage("https://example.com/"))
    }

    @Test
    fun theWarningNamesTheServiceWhenItIsKnown() {
        val warning = StreamLinkPage.warning("https://youtu.be/abc")
        assertTrue("the service should be named", warning.contains("YouTube"))
        assertTrue("it should say a page is not a file", warning.contains("page"))
        assertTrue("and it should not pretend the app can play it", warning.contains("browser"))
    }

    @Test
    fun theWarningSaysWhatWouldWork() {
        val warning = StreamLinkPage.warning("https://youtu.be/abc")
        assertTrue("a working address should be described", warning.contains(".mp4"))
        assertTrue("including the adaptive ones", warning.contains(".m3u8"))
    }

    @Test
    fun theWarningNeverForbidsTrying() {
        // The classifier can be wrong about an unusual address; a warning must not become a wall.
        val warning = StreamLinkPage.warning("https://youtu.be/abc")
        assertTrue("the user must still be able to try", warning.contains("still try"))
    }

    @Test
    fun anUnknownPageHostStillGetsAUsefulWarning() {
        val warning = StreamLinkPage.warning("https://example.com/watch/1")
        assertTrue(warning.contains("web page"))
    }

    @Test
    fun aPageHostIsRecognisedCaseInsensitively() {
        assertTrue(StreamLinkPage.isPageHost("YouTube.com"))
        assertTrue(StreamLinkPage.isPageHost("WWW.YOUTU.BE"))
    }
}
