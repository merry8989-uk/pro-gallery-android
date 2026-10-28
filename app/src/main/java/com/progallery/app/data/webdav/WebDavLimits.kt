package com.progallery.app.data.webdav

import java.io.InputStream

/**
 * How much of a WebDAV response the app will read, and how it stops.
 *
 * The client used to call `readBytes()`, which reads the **whole** response into memory before the
 * XML parser gets a chance to apply its 5,000-entry limit. A server — a broken one, or a hostile one —
 * could answer a `PROPFIND` with hundreds of megabytes and the app would allocate all of it before
 * anything noticed.
 *
 * **The limit is applied while reading, not checked against a header afterwards.** `Content-Length`
 * is the server's claim about itself; it can be absent, or wrong, or a lie. What matters is how many
 * bytes have actually arrived.
 *
 * Pure apart from the stream it is handed, so every case is tested with a stream the tests build.
 */
object WebDavLimits {

    /**
     * The most a listing may be.
     *
     * A 5,000-entry `PROPFIND` is a few hundred kilobytes; 4 MB is an order of magnitude of headroom
     * and still small enough that a phone never notices.
     */
    const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

    /** Read in 32 KiB steps: enough that the loop is cheap, small enough to stop promptly. */
    private const val CHUNK = 32 * 1024

    class TooLarge(val bytes: Int) :
        Exception("the server's answer was larger than ${MAX_RESPONSE_BYTES / (1024 * 1024)} MB")

    /**
     * Reads at most [limit] bytes, refusing rather than growing past it.
     *
     * [onChunk] is called after each read, which is where a caller checks for cancellation — a slow
     * server must not be able to hold a coroutine open with no way to stop it.
     */
    fun read(
        stream: InputStream,
        limit: Int = MAX_RESPONSE_BYTES,
        onChunk: () -> Unit = {},
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(limit, CHUNK))
        val buffer = ByteArray(CHUNK)
        var total = 0
        while (true) {
            onChunk()
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) {
                // Refused, and nothing more is allocated. The bytes already read are dropped.
                throw TooLarge(total)
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
