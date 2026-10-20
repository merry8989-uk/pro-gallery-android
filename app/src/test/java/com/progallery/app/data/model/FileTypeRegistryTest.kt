package com.progallery.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every format the app advertises must classify, and unknown formats must still
 * be browsable rather than dropped.
 */
class FileTypeRegistryTest {

    private fun kind(extension: String) = FileTypeRegistry.kindFor(null, "file.$extension")

    private fun assertAll(expected: MediaType, vararg extensions: String) {
        extensions.forEach {
            assertEquals("extension .$it", expected, kind(it))
        }
    }

    @Test
    fun photos_fromTheSupportedList() {
        assertAll(MediaType.IMAGE, "jpg", "jpeg", "png", "gif", "heic", "raw")
    }

    @Test
    fun videos_fromTheSupportedList() {
        assertAll(MediaType.VIDEO, "mp4", "mov", "mkv")
    }

    @Test
    fun audio_fromTheSupportedList() {
        assertAll(MediaType.AUDIO, "mp3", "m4a", "wav")
    }

    @Test
    fun documents_fromTheSupportedList() {
        assertAll(MediaType.DOCUMENT, "pdf", "docx", "xlsx", "ppt", "txt")
    }

    @Test
    fun imageFormatsFromTheFormatTable() {
        assertAll(
            MediaType.IMAGE,
            "jpg", "gif", "png", "webp", "heic", "heif", "tiff", "tif", "bmp",
            "psd", "raw", "eps", "ai", "indd",
        )
        // DWG is a technical drawing, grouped with documents.
        assertAll(MediaType.DOCUMENT, "dwg", "dxf")
    }

    @Test
    fun cameraRawFormats() {
        assertAll(
            MediaType.IMAGE,
            "dng", "cr2", "cr3", "nef", "arw", "orf", "raf", "rw2", "pef", "srw",
        )
    }

    @Test
    fun archivesAndPackages() {
        assertAll(MediaType.ARCHIVE, "zip", "rar", "7z", "tar", "gz", "xz", "apk", "iso")
    }

    @Test
    fun classificationIsCaseInsensitive() {
        assertEquals(MediaType.IMAGE, FileTypeRegistry.kindFor(null, "HOLIDAY.JPG"))
        assertEquals(MediaType.VIDEO, FileTypeRegistry.kindFor(null, "Clip.MOV"))
        assertEquals(MediaType.DOCUMENT, FileTypeRegistry.kindFor(null, "Report.PDF"))
    }

    @Test
    fun mimeTypeWinsWhenPresent() {
        assertEquals(MediaType.IMAGE, FileTypeRegistry.kindFor("image/jpeg", "mystery.bin"))
        assertEquals(MediaType.AUDIO, FileTypeRegistry.kindFor("audio/mpeg", "mystery.bin"))
        assertEquals(MediaType.VIDEO, FileTypeRegistry.kindFor("video/mp4", "mystery.bin"))
        assertEquals(MediaType.DOCUMENT, FileTypeRegistry.kindFor("application/pdf", "mystery.bin"))
        assertEquals(MediaType.ARCHIVE, FileTypeRegistry.kindFor("application/zip", "mystery.bin"))
    }

    @Test
    fun officeAndEbookMimeTypes() {
        assertEquals(
            MediaType.DOCUMENT,
            FileTypeRegistry.kindFor(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "letter",
            ),
        )
        assertEquals(MediaType.DOCUMENT, FileTypeRegistry.kindFor("application/epub+zip", "book"))
    }

    @Test
    fun genericMimeFallsBackToTheExtension() {
        // Providers often report octet-stream for camera raw and design files.
        assertEquals(MediaType.IMAGE, FileTypeRegistry.kindFor("application/octet-stream", "shot.cr2"))
        assertEquals(MediaType.DOCUMENT, FileTypeRegistry.kindFor("application/octet-stream", "plan.dwg"))
    }

    @Test
    fun unknownFilesAreStillBrowsable() {
        assertEquals(MediaType.OTHER, FileTypeRegistry.kindFor(null, "mystery.zzz"))
        assertEquals(MediaType.OTHER, FileTypeRegistry.kindFor("application/octet-stream", "noextension"))
        assertEquals(MediaType.OTHER, FileTypeRegistry.kindFor(null, null))
    }

    @Test
    fun extensionParsing() {
        assertEquals("jpg", FileTypeRegistry.extensionOf("photo.jpg"))
        assertEquals("jpg", FileTypeRegistry.extensionOf("photo.JPG"))
        assertEquals("", FileTypeRegistry.extensionOf("noextension"))
        assertEquals("", FileTypeRegistry.extensionOf(".hidden"))
        assertEquals("", FileTypeRegistry.extensionOf("trailing."))
        assertEquals("", FileTypeRegistry.extensionOf(null))
    }

    @Test
    fun onlyImagesAndVideosPreview() {
        assertTrue(FileTypeRegistry.isPreviewable(MediaType.IMAGE))
        assertTrue(FileTypeRegistry.isPreviewable(MediaType.VIDEO))
        assertFalse(FileTypeRegistry.isPreviewable(MediaType.AUDIO))
        assertFalse(FileTypeRegistry.isPreviewable(MediaType.DOCUMENT))
        assertFalse(FileTypeRegistry.isPreviewable(MediaType.ARCHIVE))
        assertFalse(FileTypeRegistry.isPreviewable(MediaType.OTHER))
    }

    @Test
    fun registryCoversAUsefulBreadthOfFormats() {
        assertTrue(
            "registry should know hundreds of extensions",
            FileTypeRegistry.knownExtensions().size >= 200,
        )
    }
}
