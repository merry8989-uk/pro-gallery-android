package com.progallery.app.data.text

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the reader will and will not show, and what it admits to having guessed.
 */
class TextDecoderTest {

    @Test
    fun plainUtf8IsReadAsItself() {
        val decoded = TextDecoder.decode("hello — café".toByteArray(StandardCharsets.UTF_8)).getOrThrow()
        assertEquals("hello — café", decoded.text)
        assertEquals("UTF-8", decoded.encoding)
        assertFalse(decoded.guessed)
    }

    @Test
    fun aUtf8ByteOrderMarkIsBelievedAndRemoved() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()
        val decoded = TextDecoder.decode(bytes).getOrThrow()
        assertEquals("hi", decoded.text)
        assertEquals("UTF-8", decoded.encoding)
        assertFalse(decoded.guessed)
    }

    @Test
    fun aUtf16LittleEndianMarkIsHonoured() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(StandardCharsets.UTF_16LE)
        val decoded = TextDecoder.decode(bytes).getOrThrow()
        assertEquals("hi", decoded.text)
        assertEquals("UTF-16 LE", decoded.encoding)
    }

    @Test
    fun aUtf16BigEndianMarkIsHonoured() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "hi".toByteArray(StandardCharsets.UTF_16BE)
        val decoded = TextDecoder.decode(bytes).getOrThrow()
        assertEquals("hi", decoded.text)
        assertEquals("UTF-16 BE", decoded.encoding)
    }

    @Test
    fun invalidUtf8FallsBackAndAdmitsItGuessed() {
        // Windows-1252 text is still very common, so it is read rather than refused — but the
        // screen is told the encoding was a guess, so it can say the accents may be wrong.
        val bytes = byteArrayOf(0x63.toByte(), 0x61.toByte(), 0x66.toByte(), 0xE9.toByte())
        val decoded = TextDecoder.decode(bytes).getOrThrow()
        assertEquals("ISO-8859-1", decoded.encoding)
        assertTrue(decoded.guessed)
        assertTrue(decoded.text.startsWith("caf"))
    }

    @Test
    fun aBinaryFileIsRefusedRatherThanShownAsNoise() {
        // A .docx is a zip archive. Its first bytes include NULs.
        val zipish = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x08, 0x00)
        val result = TextDecoder.decode(zipish)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("not text"))
    }

    @Test
    fun binaryDetectionLooksOnlyAtTheStart() {
        // A NUL far into a large file must not condemn it.
        val bytes = ByteArray(4096) { 'a'.code.toByte() }
        bytes[3000] = 0
        assertFalse(TextDecoder.looksBinary(bytes))
    }

    @Test
    fun aNulAtTheStartIsDetected() {
        assertTrue(TextDecoder.looksBinary(byteArrayOf(0, 65, 66)))
    }

    @Test
    fun aFileLongerThanTheReaderIsTruncatedAndSaysSo() {
        val bytes = ByteArray(200) { 'x'.code.toByte() }
        val decoded = TextDecoder.decode(bytes, limit = 50).getOrThrow()
        assertTrue(decoded.truncated)
        assertEquals(50, decoded.text.length)
    }

    @Test
    fun aShortFileIsNotTruncated() {
        val decoded = TextDecoder.decode("short".toByteArray(), limit = 50).getOrThrow()
        assertFalse(decoded.truncated)
    }

    @Test
    fun anEmptyFileReadsAsEmptyRatherThanFailing() {
        val decoded = TextDecoder.decode(ByteArray(0)).getOrThrow()
        assertEquals("", decoded.text)
        assertFalse(decoded.truncated)
    }

    @Test
    fun aNewlineIsNotMistakenForBinary() {
        val decoded = TextDecoder.decode("a\nb\r\nc".toByteArray()).getOrThrow()
        assertEquals("a\nb\r\nc", decoded.text)
    }
}
