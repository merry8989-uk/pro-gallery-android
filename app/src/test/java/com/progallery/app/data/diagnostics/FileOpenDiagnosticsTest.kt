package com.progallery.app.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The structured record every file-open attempt produces.
 *
 * Two rules from the diagnostics spec are tested here because both are easy to break by accident:
 * **successes are recorded too** (a failure count with no denominator is not a rate), and **a reason
 * is never invented** (a record that guesses is worse than one that admits it does not know).
 */
class FileOpenDiagnosticsTest {

    private fun failed(reason: String? = null, exception: String? = null) =
        FileOpenDiagnostics.failed(
            feature = FileOpenDiagnostics.Feature.IMAGE_VIEWER,
            extension = "WEBP",
            mimeType = "image/webp",
            bytes = 2_400_000L,
            uriScheme = "content",
            stage = FileOpenDiagnostics.Stage.DECODE,
            reason = reason,
            exception = exception,
            appVersion = "1.2.0",
            eventId = "abc-123",
        )

    @Test
    fun aRecordCarriesEverythingTheSpecAsksFor() {
        val r = failed(reason = "decoder_failed", exception = "java.io.IOException")
        assertEquals("file_open_failed", r["event"])
        assertEquals("image_viewer", r["feature"])
        assertEquals("webp", r["extension"])
        assertEquals("image/webp", r["mime_type"])
        assertEquals("1-10MB", r["file_size_bucket"])
        assertEquals("media_store", r["source"])
        assertEquals("decode", r["stage"])
        assertEquals("decoder_failed", r["failure_reason"])
        assertEquals("IOException", r["exception"])
        assertEquals("1.2.0", r["app_version"])
        assertEquals("abc-123", r["event_id"])
        assertTrue("the device must travel with the record", r.containsKey("android_version"))
        assertTrue(r.containsKey("device_model"))
    }

    @Test
    fun aReasonThatWasNotObservedIsNotInvented() {
        // Nothing said why. The record says so rather than picking a plausible cause.
        assertEquals("unclassified", failed()["failure_reason"])
        assertFalse("and it must not invent an exception", failed().containsKey("exception"))
    }

    @Test
    fun aSuccessIsRecordedToo() {
        val r = FileOpenDiagnostics.opened(
            feature = FileOpenDiagnostics.Feature.VIDEO_PLAYER,
            extension = "mp4",
            mimeType = "video/mp4",
            bytes = 90_000_000L,
            uriScheme = "file",
            appVersion = "1.2.0",
            eventId = "x",
        )
        assertEquals("file_opened", r["event"])
        assertEquals("10-100MB", r["file_size_bucket"])
        assertEquals("file_path", r["source"])
        assertFalse("a success has no failure reason", r.containsKey("failure_reason"))
    }

    @Test
    fun theSizeIsABucketNotTheSize() {
        // A report must not be matchable to one particular photo.
        assertEquals("empty", FileOpenDiagnostics.sizeBucket(0L))
        assertEquals("<100KB", FileOpenDiagnostics.sizeBucket(99_000L))
        assertEquals("100KB-1MB", FileOpenDiagnostics.sizeBucket(500_000L))
        assertEquals("1-10MB", FileOpenDiagnostics.sizeBucket(5_000_000L))
        assertEquals("10-100MB", FileOpenDiagnostics.sizeBucket(50_000_000L))
        assertEquals(">100MB", FileOpenDiagnostics.sizeBucket(500_000_000L))
        assertEquals("unknown", FileOpenDiagnostics.sizeBucket(null))
        assertEquals("unknown", FileOpenDiagnostics.sizeBucket(-1L))
    }

    @Test
    fun theSourceIsWhatTheFailureMeans() {
        // A `file://` address that will not open is a permission problem; a `content://` one is a
        // provider problem. Recording which is which is the difference.
        assertEquals("media_store", FileOpenDiagnostics.sourceOf("content"))
        assertEquals("file_path", FileOpenDiagnostics.sourceOf("file"))
        assertEquals("network", FileOpenDiagnostics.sourceOf("https"))
        assertEquals("unknown", FileOpenDiagnostics.sourceOf(null))
    }

    @Test
    fun anUnknownExtensionIsNamedRatherThanLeftBlank() {
        val r = FileOpenDiagnostics.opened(
            feature = FileOpenDiagnostics.Feature.TEXT_READER,
            extension = "",
            mimeType = null,
            bytes = null,
            uriScheme = null,
            appVersion = "1.2.0",
            eventId = "x",
        )
        assertEquals("unknown", r["extension"])
        assertEquals("unknown", r["mime_type"])
    }
}
