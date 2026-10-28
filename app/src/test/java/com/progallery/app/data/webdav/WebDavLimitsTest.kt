package com.progallery.app.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * The cap on how much of a WebDAV answer the app will hold.
 *
 * The client used to call `readBytes()`, which allocated the whole response before the XML parser
 * could apply its 5,000-entry limit — so a broken or hostile server could exhaust memory with one
 * `PROPFIND`.
 */
class WebDavLimitsTest {

    @Test
    fun aSmallAnswerIsReadWhole() {
        val body = "<multistatus>fine</multistatus>"
        assertEquals(body, WebDavLimits.read(ByteArrayInputStream(body.toByteArray())).decodeToString())
    }

    @Test
    fun anAnswerPastTheLimitIsRefusedRatherThanAllocated() {
        val huge = ByteArray(WebDavLimits.MAX_RESPONSE_BYTES + 1024) { 'x'.code.toByte() }
        val failure = runCatching { WebDavLimits.read(ByteArrayInputStream(huge)) }.exceptionOrNull()
        assertTrue("a response past the cap must be refused", failure is WebDavLimits.TooLarge)
    }

    @Test
    fun theLimitIsOnBytesReceivedNotOnAClaim() {
        // There is no `Content-Length` here at all: the reader never asks for one, because a server
        // can omit it or lie about it. What is counted is what actually arrives.
        val stream = object : InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 200_000) 'x'.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent >= 200_000) return -1
                val n = minOf(len, 200_000 - sent)
                java.util.Arrays.fill(b, off, off + n, 'x'.code.toByte())
                sent += n
                return n
            }
        }
        assertEquals(200_000, WebDavLimits.read(stream).size)
    }

    @Test
    fun aSlowServerCanBeStoppedBecauseEveryChunkIsAHook() {
        // A coroutine checks for cancellation in `onChunk`. Without it a slow server holds the read
        // open with no way to stop.
        var chunks = 0
        val body = ByteArray(100_000) { 'x'.code.toByte() }
        val failure = runCatching {
            WebDavLimits.read(ByteArrayInputStream(body)) {
                chunks++
                if (chunks > 1) throw kotlinx.coroutines.CancellationException("stopped")
            }
        }.exceptionOrNull()
        assertTrue("cancelling between chunks must stop the read", failure is kotlinx.coroutines.CancellationException)
        assertTrue("and it must have happened early, not after the whole body", chunks <= 3)
    }

    @Test
    fun anEmptyAnswerIsEmptyRatherThanAnError() {
        assertEquals(0, WebDavLimits.read(ByteArrayInputStream(ByteArray(0))).size)
    }

    @Test
    fun exactlyTheLimitIsAllowed() {
        val body = ByteArray(WebDavLimits.MAX_RESPONSE_BYTES) { 'x'.code.toByte() }
        assertEquals(WebDavLimits.MAX_RESPONSE_BYTES, WebDavLimits.read(ByteArrayInputStream(body)).size)
    }
}
