package com.progallery.app.data.recovery

import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identification must come from the bytes, not the extension — that is the
 * whole point of the detector.
 */
class FileSignatureDetectorTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String, pad: Int = 0): ByteArray {
        val base = text.toByteArray(Charsets.US_ASCII)
        if (pad <= 0) return base
        return base + ByteArray(pad)
    }

    @Test
    fun jpegAndPngAndGif() {
        assertEquals("JPEG", FileSignatureDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))?.formatName)
        assertEquals(
            "PNG",
            FileSignatureDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))?.formatName,
        )
        assertEquals("GIF", FileSignatureDetector.detect(ascii("GIF89a", pad = 4))?.formatName)
        assertEquals(MediaType.IMAGE, FileSignatureDetector.detect(ascii("GIF87a", pad = 4))?.kind)
    }

    @Test
    fun riffContainersAreToldApartByBrand() {
        assertEquals("WAV", FileSignatureDetector.detect(ascii("RIFF????WAVE"))?.formatName)
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(ascii("RIFF????WAVE"))?.kind)
        assertEquals("AVI", FileSignatureDetector.detect(ascii("RIFF????AVI "))?.formatName)
        assertEquals(MediaType.VIDEO, FileSignatureDetector.detect(ascii("RIFF????AVI "))?.kind)
        assertEquals("WebP", FileSignatureDetector.detect(ascii("RIFF????WEBP"))?.formatName)
        assertEquals(MediaType.IMAGE, FileSignatureDetector.detect(ascii("RIFF????WEBP"))?.kind)
    }

    @Test
    fun isoBaseMediaBrandsDecideKind() {
        assertEquals(MediaType.VIDEO, FileSignatureDetector.detect(ascii("\u0000\u0000\u0000\u0018ftypisom"))?.kind)
        assertEquals(MediaType.VIDEO, FileSignatureDetector.detect(ascii("\u0000\u0000\u0000\u0018ftypqt  "))?.kind)
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(ascii("\u0000\u0000\u0000\u0018ftypM4A "))?.kind)
        assertEquals(MediaType.IMAGE, FileSignatureDetector.detect(ascii("\u0000\u0000\u0000\u0018ftypheic"))?.kind)
        assertEquals(MediaType.IMAGE, FileSignatureDetector.detect(ascii("\u0000\u0000\u0000\u0018ftypavif"))?.kind)
    }

    @Test
    fun audioFormats() {
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(ascii("ID3", pad = 8))?.kind)
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(ascii("fLaC", pad = 4))?.kind)
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(ascii("OggS", pad = 8))?.kind)
        assertEquals(MediaType.AUDIO, FileSignatureDetector.detect(bytes(0xFF, 0xFB, 0x90, 0x00))?.kind)
    }

    @Test
    fun videoAndContainerFormats() {
        assertEquals(MediaType.VIDEO, FileSignatureDetector.detect(bytes(0x1A, 0x45, 0xDF, 0xA3))?.kind)
        assertEquals("Matroska/WebM", FileSignatureDetector.detect(bytes(0x1A, 0x45, 0xDF, 0xA3))?.formatName)
    }

    @Test
    fun documentsAndArchives() {
        assertEquals(MediaType.DOCUMENT, FileSignatureDetector.detect(ascii("%PDF-1.7", pad = 4))?.kind)
        assertEquals(MediaType.ARCHIVE, FileSignatureDetector.detect(bytes(0x50, 0x4B, 0x03, 0x04))?.kind)
        assertEquals(MediaType.ARCHIVE, FileSignatureDetector.detect(bytes(0x1F, 0x8B, 0x08, 0x00))?.kind)
        assertEquals(MediaType.ARCHIVE, FileSignatureDetector.detect(bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C))?.kind)
        assertEquals(MediaType.ARCHIVE, FileSignatureDetector.detect(ascii("Rar!\u001A\u0007"))?.kind)
    }

    @Test
    fun professionalFormats() {
        assertEquals("Photoshop", FileSignatureDetector.detect(ascii("8BPS", pad = 4))?.formatName)
        assertEquals("TIFF (little endian)", FileSignatureDetector.detect(bytes(0x49, 0x49, 0x2A, 0x00))?.formatName)
        assertEquals("TIFF (big endian)", FileSignatureDetector.detect(bytes(0x4D, 0x4D, 0x00, 0x2A))?.formatName)
    }

    @Test
    fun textHeuristicsAreLowConfidence() {
        assertEquals(Confidence.MEDIUM, FileSignatureDetector.detect(ascii("<svg xmlns=", pad = 4))?.confidence)
        assertEquals(Confidence.LOW, FileSignatureDetector.detect(ascii("<?xml version=", pad = 4))?.confidence)
        assertEquals(Confidence.LOW, FileSignatureDetector.detect(ascii("{\"a\":1}", pad = 4))?.confidence)
    }

    @Test
    fun unknownAndShortInputsReturnNull() {
        assertNull(FileSignatureDetector.detect(bytes(0x00, 0x01, 0x02)))
        assertNull(FileSignatureDetector.detect(ByteArray(0)))
        assertNull(FileSignatureDetector.detect(ascii("this is just plain text, no signature at all")))
    }

    @Test
    fun evidenceIsReported() {
        val match = FileSignatureDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))
        assertTrue(match!!.evidence.isNotBlank())
        assertEquals(Confidence.HIGH, match.confidence)
        assertEquals("image/jpeg", match.mimeType)
    }

    @Test
    fun sniffReadsOnlyTheHeader() {
        val stream = java.io.ByteArrayInputStream(bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(10_000))
        assertEquals("JPEG", FileSignatureDetector.sniff({ stream }, maxBytes = 16)?.formatName)
    }
}
