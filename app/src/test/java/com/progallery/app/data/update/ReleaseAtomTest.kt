package com.progallery.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed is the source that keeps working on a mobile network, so its reader is tested
 * against a reply shaped the way GitHub actually writes it.
 */
class ReleaseAtomTest {

    /** Modelled on the real feed, including the media namespace and HTML notes. */
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/" xml:lang="en-US">
          <id>tag:github.com,2008:https://github.com/merry8989-uk/pro-gallery-android/releases</id>
          <link type="text/html" rel="alternate" href="https://github.com/merry8989-uk/pro-gallery-android/releases"/>
          <updated>2026-10-21T09:34:17Z</updated>
          <entry>
            <id>tag:github.com,2008:Repository/123/v1.20.0</id>
            <updated>2026-10-21T09:34:17Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/merry8989-uk/pro-gallery-android/releases/tag/v1.20.0"/>
            <title>ProGallery v1.20.0 — debug APK</title>
            <content type="html">&lt;p&gt;Adaptive streams actually play &amp;amp; a page link says so.&lt;/p&gt;&#10;&lt;ul&gt;&lt;li&gt;HLS and DASH are on the classpath.&lt;/li&gt;&lt;/ul&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/123/v1.19.0</id>
            <link rel="alternate" type="text/html" href="https://github.com/merry8989-uk/pro-gallery-android/releases/tag/v1.19.0"/>
            <title>ProGallery v1.19.0</title>
            <content type="html">&lt;p&gt;An older release.&lt;/p&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    private fun parse(xml: String = feed) =
        ReleaseAtom.parse(xml, "merry8989-uk", "pro-gallery-android")

    @Test
    fun theNewestReleaseIsRead() {
        val release = parse().getOrThrow()
        assertEquals("v1.20.0", release.tagName)
        assertEquals("1.20.0", release.versionName)
        assertEquals(
            "https://github.com/merry8989-uk/pro-gallery-android/releases/tag/v1.20.0",
            release.pageUrl,
        )
    }

    @Test
    fun theFirstEntryIsTheLatestOne() {
        // The feed is newest first; reading the last entry would offer an older version.
        assertEquals("1.20.0", parse().getOrThrow().versionName)
    }

    @Test
    fun theApkAddressIsDerivedFromTheTag() {
        // The feed carries no asset list, but a release's download URL is deterministic.
        assertEquals(
            "https://github.com/merry8989-uk/pro-gallery-android/releases/download/v1.20.0/pro-gallery-debug.apk",
            parse().getOrThrow().apkUrl,
        )
    }

    @Test
    fun theNotesArriveAsReadableTextRatherThanMarkup() {
        val notes = parse().getOrThrow().notes!!
        assertFalse("markup must not reach the dialog", notes.contains("<p>"))
        assertFalse("nor entities", notes.contains("&amp;"))
        assertTrue("but the words must survive", notes.contains("Adaptive streams actually play"))
        assertTrue("including an ampersand written as an entity", notes.contains("play & a page"))
    }

    @Test
    fun aFeedWithNoEntriesIsRefusedWithAReadableReason() {
        val empty = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>"""
        val result = parse(empty)
        assertTrue(result.isFailure)
        assertEquals("The release feed lists no releases.", result.exceptionOrNull()?.message)
    }

    @Test
    fun malformedXmlIsRefusedWithAReadableReason() {
        val result = parse("this is not xml at all")
        assertTrue(result.isFailure)
        assertEquals("The release feed could not be read.", result.exceptionOrNull()?.message)
    }

    @Test
    fun anEntryWithNoLinkIsRefused() {
        val noLink = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>v9.0</title></entry>
            </feed>
        """.trimIndent()
        val result = parse(noLink)
        assertTrue(result.isFailure)
        assertEquals("The latest release has no page address.", result.exceptionOrNull()?.message)
    }

    @Test
    fun aPrefixedFeedIsReadToo() {
        // Feeds are written by many hands; matching on local names keeps this working.
        val prefixed = """
            <?xml version="1.0"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
              <atom:entry>
                <atom:link rel="alternate" href="https://example.com/releases/tag/v2.5.1"/>
                <atom:title>v2.5.1</atom:title>
              </atom:entry>
            </atom:feed>
        """.trimIndent()
        assertEquals("2.5.1", parse(prefixed).getOrThrow().versionName)
    }

    @Test
    fun theTagIsReadFromTheAddressNotGuessedFromTheTitle() {
        // The address is structured; a title is prose someone typed.
        val oddTitle = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <link rel="alternate" href="https://example.com/releases/tag/v3.0.0"/>
                <title>Version three, the big one!</title>
              </entry>
            </feed>
        """.trimIndent()
        assertEquals("3.0.0", parse(oddTitle).getOrThrow().versionName)
    }

    @Test
    fun plainTextStripsMarkupAndEntities() {
        assertEquals(
            "Hello & goodbye",
            ReleaseAtom.plainText("<p>Hello &amp; goodbye</p>"),
        )
    }

    @Test
    fun plainTextTurnsBreaksIntoNewlines() {
        val text = ReleaseAtom.plainText("<p>one</p><p>two</p>")
        assertTrue(text.contains("\n"))
        assertTrue(text.contains("one"))
        assertTrue(text.contains("two"))
    }

    @Test
    fun plainTextHandlesNumericEntities() {
        assertEquals("A & B", ReleaseAtom.plainText("A &#38; B"))
        assertEquals("A & B", ReleaseAtom.plainText("A &#x26; B"))
    }

    @Test
    fun plainTextLeavesOrdinaryTextAlone() {
        assertEquals("just words", ReleaseAtom.plainText("just words"))
    }
}
