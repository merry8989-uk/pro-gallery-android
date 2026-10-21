package com.progallery.app.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV servers disagree about prefixes, href shapes and which properties they report,
 * so the parser is tested against the shapes that actually turn up.
 */
class WebDavXmlTest {

    private val base = "https://cloud.example.com/remote.php/dav/files/alice/Photos/"

    /** A Nextcloud-shaped reply: `d:` prefix, absolute-path hrefs. */
    private val nextcloudReply = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
          <d:response>
            <d:href>/remote.php/dav/files/alice/Photos/</d:href>
            <d:propstat>
              <d:prop>
                <d:displayname>Photos</d:displayname>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/alice/Photos/Holiday/</d:href>
            <d:propstat>
              <d:prop>
                <d:displayname>Holiday</d:displayname>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/alice/Photos/beach%20sunset.jpg</d:href>
            <d:propstat>
              <d:prop>
                <d:displayname>beach sunset.jpg</d:displayname>
                <d:getcontentlength>204800</d:getcontentlength>
                <d:getcontenttype>image/jpeg</d:getcontenttype>
                <d:getlastmodified>Tue, 20 Oct 2026 10:00:00 GMT</d:getlastmodified>
                <d:resourcetype/>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    /** An Apache mod_dav-shaped reply: `D:` prefix, full-URL hrefs, no displayname. */
    private val apacheReply = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>https://dav.example.org/media/</D:href>
            <D:propstat>
              <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>https://dav.example.org/media/clip.mp4</D:href>
            <D:propstat>
              <D:prop>
                <D:getcontentlength>1048576</D:getcontentlength>
                <D:getcontenttype>video/mp4</D:getcontenttype>
              </D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun aNextcloudReplyIsReadAndTheCollectionItselfIsSkipped() {
        val entries = WebDavXml.parseListing(nextcloudReply, base).getOrThrow()
        assertEquals("the folder that was asked for is not an entry", 2, entries.size)
        assertEquals(listOf("Holiday", "beach sunset.jpg"), entries.map { it.name })
    }

    @Test
    fun aDirectoryIsRecognisedByItsCollectionElement() {
        val entries = WebDavXml.parseListing(nextcloudReply, base).getOrThrow()
        val folder = entries.first { it.name == "Holiday" }
        val file = entries.first { it.name == "beach sunset.jpg" }
        assertTrue(folder.isDirectory)
        assertFalse(file.isDirectory)
    }

    @Test
    fun aFilesDetailsAreRead() {
        val file = WebDavXml.parseListing(nextcloudReply, base).getOrThrow()
            .first { !it.isDirectory }
        assertEquals(204800L, file.sizeBytes)
        assertEquals("image/jpeg", file.mimeType)
        assertEquals(1_792_490_400_000L, file.lastModifiedMillis)
        assertTrue(file.isMedia)
    }

    @Test
    fun anEncodedHrefBecomesAUsableAddress() {
        val file = WebDavXml.parseListing(nextcloudReply, base).getOrThrow()
            .first { !it.isDirectory }
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice/Photos/beach%20sunset.jpg",
            file.url,
        )
    }

    @Test
    fun aReplyWithFullUrlHrefsAndNoDisplayNameStillWorks() {
        val entries = WebDavXml.parseListing(apacheReply, "https://dav.example.org/media/").getOrThrow()
        assertEquals(1, entries.size)
        // No displayname, so the name falls back to the last segment of the href.
        assertEquals("clip.mp4", entries.first().name)
        assertEquals("https://dav.example.org/media/clip.mp4", entries.first().url)
        assertEquals(1048576L, entries.first().sizeBytes)
    }

    @Test
    fun aPrefixlessReplyIsUnderstood() {
        // The prefix is not the server's contract; the local name is.
        val xml = """
            <?xml version="1.0"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/media/</href>
                <propstat>
                  <prop><resourcetype><collection/></resourcetype></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
              <response>
                <href>/media/song.mp3</href>
                <propstat>
                  <prop>
                    <getcontentlength>4096</getcontentlength>
                    <getcontenttype>audio/mpeg</getcontenttype>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()
        val entries = WebDavXml.parseListing(xml, "https://dav.example.org/media/").getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("song.mp3", entries.first().name)
    }

    @Test
    fun aPropertyReportedAsNotFoundIsIgnoredInFavourOfTheOneThatSucceeded() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/media/a.jpg</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>0</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop>
                    <d:displayname>a.jpg</d:displayname>
                    <d:getcontentlength>512</d:getcontentlength>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val entry = WebDavXml.parseListing(xml, "https://dav.example.org/media/").getOrThrow().single()
        assertEquals("the 200 propstat must win", 512L, entry.sizeBytes)
    }

    @Test
    fun aDateWithTheWrongWeekdayStillGivesATimestamp() {
        // Servers do send an inconsistent weekday. Strict parsing alone would throw the
        // timestamp away; the lenient pass keeps it, which is the more useful answer.
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/media/a.jpg</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>Mon, 20 Oct 2026 10:00:00 GMT</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val entry = WebDavXml.parseListing(xml, "https://dav.example.org/media/").getOrThrow().single()
        assertEquals(1_792_490_400_000L, entry.lastModifiedMillis)
    }

    @Test
    fun anUnreadableDateBecomesNoDateRatherThanAGuess() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/media/a.jpg</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>not a date at all</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val entry = WebDavXml.parseListing(xml, "https://dav.example.org/media/").getOrThrow().single()
        assertNull("no date is better than an invented one", entry.lastModifiedMillis)
    }

    @Test
    fun malformedXmlIsRefusedWithAReadableReason() {
        val result = WebDavXml.parseListing("<d:multistatus><d:response>", base)
        assertTrue(result.isFailure)
        assertEquals("The server's reply could not be read.", result.exceptionOrNull()?.message)
    }

    @Test
    fun anEmptyReplyIsAnEmptyFolderNotAnError() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"></d:multistatus>"""
        assertTrue(WebDavXml.parseListing(xml, base).getOrThrow().isEmpty())
    }

    @Test
    fun anAbsolutePathHrefResolvesAgainstTheCollection() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice/Photos/a.jpg",
            WebDavXml.resolve(base, "/remote.php/dav/files/alice/Photos/a.jpg"),
        )
    }

    @Test
    fun aFullUrlHrefIsLeftAlone() {
        val full = "https://other.example.net/media/clip.mp4"
        assertEquals(full, WebDavXml.resolve(base, full))
    }

    @Test
    fun aRelativeHrefResolvesBelowTheCollection() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice/Photos/sub/b.mp4",
            WebDavXml.resolve(base, "sub/b.mp4"),
        )
    }

    @Test
    fun goingUpOneFolderStopsAtTheRoot() {
        assertEquals("https://h.example.com/a/", WebDavXml.parentOf("https://h.example.com/a/b/"))
        assertEquals("https://h.example.com/", WebDavXml.parentOf("https://h.example.com/a/"))
        assertNull("the root has no parent", WebDavXml.parentOf("https://h.example.com/"))
    }

    @Test
    fun goingUpFromAFileLandsInItsFolder() {
        assertEquals(
            "https://h.example.com/a/b/",
            WebDavXml.parentOf("https://h.example.com/a/b/file.mp4"),
        )
    }

    @Test
    fun aCollectionUrlAlwaysEndsInASlash() {
        assertEquals("https://h.example.com/a/", WebDavXml.asCollection("https://h.example.com/a"))
        assertEquals("https://h.example.com/a/", WebDavXml.asCollection("https://h.example.com/a/"))
    }
}
