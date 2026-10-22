package com.progallery.app.data.upload

import com.progallery.app.data.eventlog.LogEntry

/**
 * What is pending, and how much of it goes at once.
 *
 * Pure, because "what has not been sent yet" is the kind of thing that quietly goes wrong: send
 * an event twice and the numbers lie, skip one and the picture has a hole.
 */
object LogUploadBatch {

    /** The server accepts at most this many per request; asking for more is a wasted round trip. */
    const val MAX_PER_REQUEST = 100

    /**
     * Everything newer than the watermark.
     *
     * The watermark is the timestamp of the newest event already accepted, so an event written
     * while a request is in flight is not lost and an event already sent is not sent again. Two
     * events can share a millisecond, so the comparison is `>` and the watermark moves to the
     * newest accepted — which means a same-millisecond event may be sent twice rather than
     * dropped. Duplicates are harmless; a hole is not.
     */
    fun pending(entries: List<LogEntry>, watermarkMillis: Long): List<LogEntry> =
        entries.filter { it.timestampMillis > watermarkMillis }.sortedBy { it.timestampMillis }

    fun batches(entries: List<LogEntry>, size: Int = MAX_PER_REQUEST): List<List<LogEntry>> {
        require(size > 0) { "a batch size of zero would never send anything" }
        return entries.chunked(size)
    }

    /** The watermark after a batch is accepted. */
    fun advancedWatermark(watermarkMillis: Long, accepted: List<LogEntry>): Long =
        accepted.maxOfOrNull { it.timestampMillis }?.let { maxOf(it, watermarkMillis) }
            ?: watermarkMillis
}
