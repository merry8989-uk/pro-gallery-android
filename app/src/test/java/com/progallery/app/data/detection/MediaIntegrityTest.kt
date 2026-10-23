package com.progallery.app.data.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The integrity check.
 *
 * Half of these tests are about what it must **not** flag. A "corrupt" label on a good file is
 * worse than no label at all — it makes a person distrust something that is fine — so the negative
 * cases matter as much as the positive ones.
 */
class MediaIntegrityTest {

    private val jpegStart = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val jpegEnd = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xD9.toByte())
    private val pngStart = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
    private val pngEnd = byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42)

    private fun analyse(
        ext: String,
        size: Long = 1_000,
        header: ByteArray? = jpegStart,
        trailer: ByteArray? = jpegEnd,
        format: String? = "JPEG",
        matches: Boolean? = true,
        known: Boolean = true,
    ) = MediaIntegrity.analyse(
        MediaIntegrity.Input(
            extension = ext,
            declaredSize = size,
            header = header,
            trailer = trailer,
            signatureFormatName = format,
            signatureMatchesExtension = matches,
            extensionIsKnownMedia = known,
        ),
    )

    // ---- the things it should catch ----------------------------------------------------------

    @Test
    fun anEmptyFileIsFlaggedAndIsUnreadable() {
        val r = analyse("jpg", size = 0L)
        assertTrue(r.issues.contains(IntegrityIssue.EMPTY))
        assertTrue("nothing can be recovered from nothing", r.unreadable)
    }

    @Test
    fun aJpegThatDoesNotEndWithItsOwnMarkerIsTruncated() {
        val r = analyse("jpg", trailer = byteArrayOf(0x11, 0x22, 0x33, 0x44))
        assertTrue(r.issues.contains(IntegrityIssue.TRUNCATED))
        assertTrue(r.evidence.contains("FFD9"))
    }

    @Test
    fun aPngWithoutItsIendChunkIsTruncated() {
        val r = analyse("png", header = pngStart, trailer = byteArrayOf(0x00, 0x01, 0x02, 0x03), format = "PNG")
        assertTrue(r.issues.contains(IntegrityIssue.TRUNCATED))
        assertTrue(r.evidence.contains("IEND"))
    }

    @Test
    fun aNameThatDisagreesWithTheBytesIsFlaggedWithBothSides() {
        val r = analyse("png", format = "JPEG", matches = false)
        assertTrue(r.issues.contains(IntegrityIssue.EXTENSION_MISMATCH))
        assertTrue("the evidence must name both the bytes and the name", r.evidence.contains("JPEG"))
        assertTrue(r.evidence.contains(".png"))
    }

    @Test
    fun anUnrecognisedHeaderIsFlaggedForAMediaExtension() {
        val r = analyse("mp4", header = byteArrayOf(0x00, 0x01, 0x02), format = null, matches = null, known = true)
        assertTrue(r.issues.contains(IntegrityIssue.HEADER_UNRECOGNISED))
    }

    @Test
    fun aHeaderThatCannotBeReadIsSaidRatherThanSilentlyPassed() {
        val r = analyse("jpg", header = null, format = null, matches = null)
        assertTrue(r.issues.contains(IntegrityIssue.HEADER_UNRECOGNISED))
        assertTrue(r.evidence.contains("could not be read"))
    }

    // ---- and the things it must NOT -----------------------------------------------------------------

    @Test
    fun aWholeJpegIsNotFlagged() {
        val r = analyse("jpg")
        assertFalse("a complete JPEG is a complete JPEG", r.suspicious)
        assertEquals("", r.evidence)
    }

    @Test
    fun aWholePngIsNotFlagged() {
        val r = analyse("png", header = pngStart, trailer = pngEnd, format = "PNG")
        assertFalse(r.suspicious)
    }

    @Test
    fun aFormatWithNoEndMarkerIsNeverCalledTruncated() {
        // An MP4 has no single trailing marker, so a short one is indistinguishable from a whole
        // one. Claiming otherwise would be a guess dressed as a fact.
        val r = analyse("mp4", header = byteArrayOf(0, 0, 0, 0x18), trailer = byteArrayOf(1, 2, 3), format = "MP4", matches = true, known = true)
        assertFalse(r.issues.contains(IntegrityIssue.TRUNCATED))
    }

    @Test
    fun anExtensionTheAppDoesNotCatalogueIsNotCalledCorrupt() {
        // A .txt or a .zip legitimately starts with bytes this detector does not know.
        listOf("txt", "json", "zip", "pdf", "docx").forEach { ext ->
            val r = analyse(ext, header = byteArrayOf(0x7B, 0x22), format = null, matches = null, known = false)
            assertFalse(".$ext must not be called corrupt for being unremarkable", r.suspicious)
        }
    }

    @Test
    fun aFileWithNoExtensionIsNotCalledWrong() {
        val r = analyse("", format = "JPEG", matches = null, known = false)
        assertFalse(r.suspicious)
    }

    @Test
    fun anUnknownExtensionIsNotAMismatch() {
        // The detector not knowing a format is not the same as the format being wrong, and an
        // extension the app does not catalogue is not a media file it failed to recognise.
        val r = analyse("xyz", format = null, matches = null, known = false)
        assertFalse("an extension nobody catalogues is not an error", r.suspicious)
    }

    @Test
    fun aDeclaredSizeWithNoBytesToShowForItIsFlagged() {
        val r = analyse("jpg", size = 5_000, header = null, format = null, matches = null, known = true)
        assertTrue(r.issues.contains(IntegrityIssue.HEADER_UNRECOGNISED))
        assertTrue("and it is not called empty, because the size says otherwise", !r.issues.contains(IntegrityIssue.EMPTY))
    }

    @Test
    fun issuesAreNotRepeatedWhenTwoChecksAgree() {
        // A PNG with a bad header and a bad name could raise the same issue twice; the report
        // should read as a list of distinct problems.
        val r = analyse("png", header = byteArrayOf(1, 2, 3), format = null, matches = null, known = true)
        assertEquals(r.issues.size, r.issues.distinct().size)
    }
}
