package com.progallery.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatUtilsTest {

    @Test
    fun isImage_detectsCommonTypes() {
        assertTrue(MediaFormatUtils.isImage("image/jpeg"))
        assertTrue(MediaFormatUtils.isImage("image/heic"))
        assertFalse(MediaFormatUtils.isImage("video/mp4"))
        assertFalse(MediaFormatUtils.isImage(null))
    }

    @Test
    fun isVideo_detectsCommonTypes() {
        assertTrue(MediaFormatUtils.isVideo("video/mp4"))
        assertTrue(MediaFormatUtils.isVideo("video/x-matroska"))
        assertFalse(MediaFormatUtils.isVideo("image/png"))
    }

    @Test
    fun formatBytes_usesBinaryUnits() {
        assertEquals("512 B", MediaFormatUtils.formatBytes(512))
        assertEquals("1.0 KB", MediaFormatUtils.formatBytes(1024))
        assertEquals("1.0 MB", MediaFormatUtils.formatBytes(1024 * 1024))
        assertEquals("1.5 GB", MediaFormatUtils.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatDuration_handlesHours() {
        assertEquals("0:05", MediaFormatUtils.formatDuration(5000))
        assertEquals("1:05", MediaFormatUtils.formatDuration(65000))
        assertEquals("1:01:05", MediaFormatUtils.formatDuration(3665000))
    }

    @Test
    fun formatBytes_neverShowsZeroAndFlagsUnknownSizes() {
        // The app accepts every file from 1 byte upward, so a real file never
        // reads "0 B".
        assertEquals("1 B", MediaFormatUtils.formatBytes(0))
        assertEquals("1 B", MediaFormatUtils.formatBytes(1))
        assertEquals("1023 B", MediaFormatUtils.formatBytes(1023))
        assertEquals("1.0 KB", MediaFormatUtils.formatBytes(1024))
        // A negative value means the provider did not report a size.
        assertEquals("Unknown", MediaFormatUtils.formatBytes(-1))
    }

    @Test
    fun formatBytes_hasNoUpperLimit() {
        // Terabytes and beyond must still render, not overflow or blank out.
        assertEquals("1.0 TB", MediaFormatUtils.formatBytes(1024L * 1024 * 1024 * 1024))
        assertTrue(MediaFormatUtils.formatBytes(Long.MAX_VALUE).isNotEmpty())
    }

    @Test
    fun formatDate_handlesMissingAndValidValues() {
        assertEquals("—", MediaFormatUtils.formatDate(0))
        assertTrue(MediaFormatUtils.formatDate(1609459200L, java.util.Locale.US).isNotEmpty())
    }

    @Test
    fun extensionFor_mapsMimeTypes() {
        assertEquals("png", MediaFormatUtils.extensionFor("image/png"))
        assertEquals("mp4", MediaFormatUtils.extensionFor("video/mp4"))
        assertEquals("jpg", MediaFormatUtils.extensionFor("image/jpeg"))
    }
}
