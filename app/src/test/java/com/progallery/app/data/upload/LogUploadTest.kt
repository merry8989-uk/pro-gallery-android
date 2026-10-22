package com.progallery.app.data.upload

import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.eventlog.LogContext
import com.progallery.app.data.eventlog.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What leaves the device, and what is considered already sent.
 *
 * The payload's field list is a contract with the ingest function, so it is asserted exactly
 * rather than approximately: a field added here and not there is an event the server drops.
 */
class LogUploadTest {

    private val context = LogContext("1.29.0 (45)", "Android 14", "mobile", "production", "wifi")

    private fun entry(millis: Long, details: Map<String, String> = emptyMap()) = LogEntry(
        event = EventName.ACCESS_FIX_RESULT,
        timestampMillis = millis,
        userCode = "PG-4F9-K2Q",
        sessionId = "sess_0123456789abcdef",
        attemptId = "att_71c3",
        screen = "AccessDiagnostics",
        details = details,
    )

    @Test
    fun thePayloadIsExactlyTheEnvelopeAndNothingElse() {
        val payload = LogUploadPayload.of(entry(1_764_000_000_000, mapOf("outcome" to "success")), context)
        assertEquals(
            setOf(
                "event_name", "schema_version", "timestamp", "session_id", "attempt_id",
                "user_code", "platform", "app_version", "os", "device_model", "network_type",
                "screen_or_route", "log_level", "release_channel", "details",
            ),
            payload.keys,
        )
    }

    @Test
    fun theAppNeverSendsAHashOfItsOwn() {
        // The salt lives on the server. A hash the client computes is a hash whose salt the
        // client holds, and that protects nothing.
        val payload = LogUploadPayload.of(entry(1_764_000_000_000), context)
        assertFalse("user_id_hash must be the server's to add", payload.containsKey("user_id_hash"))
        assertTrue("the code is what the server hashes", payload.containsKey("user_code"))
    }

    @Test
    fun absentOptionalFieldsAreOmittedRatherThanSentAsNull() {
        val bare = LogEntry(
            event = EventName.SESSION_START,
            timestampMillis = 1_764_000_000_000,
            userCode = "PG-4F9-K2Q",
            sessionId = "sess_0123456789abcdef",
        )
        val payload = LogUploadPayload.of(bare, context)
        assertFalse(payload.containsKey("attempt_id"))
        assertFalse(payload.containsKey("screen_or_route"))
        assertFalse(payload.containsKey("details"))
    }

    @Test
    fun theTimestampIsIsoUtc() {
        val payload = LogUploadPayload.of(entry(1_764_000_000_000), context)
        assertEquals("2025-11-24T16:00:00Z", payload["timestamp"])
    }

    @Test
    fun aBatchCarriesTheEventsUnderOneKey() {
        val body = LogUploadPayload.batch(listOf(entry(1), entry(2)), context)
        assertEquals(setOf("events"), body.keys)
        assertEquals(2, (body["events"] as List<*>).size)
    }

    @Test
    fun onlyEventsNewerThanTheWatermarkArePending() {
        val entries = listOf(entry(100), entry(200), entry(300))
        assertEquals(listOf(200L, 300L), LogUploadBatch.pending(entries, 100).map { it.timestampMillis })
        assertEquals(entries, LogUploadBatch.pending(entries, 0))
        assertTrue(LogUploadBatch.pending(entries, 300).isEmpty())
    }

    @Test
    fun pendingEventsComeBackInTimeOrder() {
        // Whatever order the file was read in, the request goes up in the order things happened.
        val entries = listOf(entry(300), entry(100), entry(200))
        assertEquals(listOf(100L, 200L, 300L), LogUploadBatch.pending(entries, 0).map { it.timestampMillis })
    }

    @Test
    fun batchesRespectTheServerLimit() {
        val entries = (1..250).map { entry(it.toLong()) }
        val batches = LogUploadBatch.batches(entries)
        assertEquals(listOf(100, 100, 50), batches.map { it.size })
        assertEquals("nothing is lost in the split", 250, batches.sumOf { it.size })
    }

    @Test
    fun anEmptyPendingListProducesNoBatches() {
        assertTrue(LogUploadBatch.batches(emptyList()).isEmpty())
    }

    @Test
    fun theWatermarkOnlyMovesForward() {
        assertEquals(300L, LogUploadBatch.advancedWatermark(100, listOf(entry(200), entry(300))))
        // An out-of-order or empty response must not rewind it and resend everything.
        assertEquals(300L, LogUploadBatch.advancedWatermark(300, listOf(entry(100))))
        assertEquals(300L, LogUploadBatch.advancedWatermark(300, emptyList()))
    }
}
