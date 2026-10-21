package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line format, and the privacy property it has to hold: only the fields the schema names can
 * ever appear, and a malformed line is dropped rather than thrown.
 */
class LogLineTest {

    private val context = LogContext(
        appVersion = "1.28.0 (44)",
        os = "Android 14",
        deviceModel = "mobile",
        releaseChannel = "production",
        network = "wifi",
    )

    private fun entry(
        event: EventName = EventName.ACCESS_FIX_RESULT,
        details: Map<String, String> = emptyMap(),
        attemptId: String? = "att_71c3",
        screen: String? = "MediaAccessDiagnostics",
    ) = LogEntry(
        event = event,
        timestampMillis = 1_764_000_000_000,
        userCode = "PG-4F9-K2Q",
        sessionId = "sess_9f2a",
        attemptId = attemptId,
        screen = screen,
        details = details,
    )

    @Test
    fun theEnvelopeIsWrittenAsTheSchemaNamesIt() {
        val line = LogLine.encode(entry(), context)
        listOf(
            "event_name", "schema_version", "timestamp", "session_id", "attempt_id", "user_code",
            "platform", "app_version", "os", "device_model", "network_type", "screen_or_route",
            "log_level", "release_channel",
        ).forEach { field ->
            assertTrue("$field missing from $line", line.contains("\"$field\":"))
        }
    }

    @Test
    fun theTimestampIsIsoUtcWithNoFraction() {
        assertEquals("2025-11-24T16:00:00Z", LogLine.isoTimestamp(1_764_000_000_000))
        assertTrue(
            LogLine.encode(entry(), context).contains("\"timestamp\":\"2025-11-24T16:00:00Z\""),
        )
    }

    @Test
    fun aLineSurvivesARoundTrip() {
        val original = entry(
            details = mapOf("outcome" to "success", "state_after" to "granted", "media_item_count" to "412"),
        )
        val decoded = LogLine.decode(LogLine.encode(original, context))!!

        assertEquals(original.event, decoded.event)
        assertEquals(original.level, decoded.level)
        assertEquals(original.userCode, decoded.userCode)
        assertEquals(original.sessionId, decoded.sessionId)
        assertEquals(original.attemptId, decoded.attemptId)
        assertEquals(original.screen, decoded.screen)
        assertEquals(original.timestampMillis, decoded.timestampMillis)
        assertEquals(original.details, decoded.details)
    }

    @Test
    fun theEnvelopeIsNotRepeatedAsEventDetails() {
        // Otherwise every export grows a second copy of the session context.
        val decoded = LogLine.decode(LogLine.encode(entry(), context))!!
        listOf("event_name", "schema_version", "log_level", "user_code", "os").forEach { key ->
            assertTrue("$key should not be an event detail", key !in decoded.details)
        }
    }

    @Test
    fun aValueWithQuotesOrNewlinesSurvives() {
        val original = entry(details = mapOf("note" to "a \"quoted\" word\nand a new line\tand a \\"))
        val decoded = LogLine.decode(LogLine.encode(original, context))!!
        assertEquals("a \"quoted\" word\nand a new line\tand a \\", decoded.details["note"])
    }

    @Test
    fun theLevelComesFromTheEventWhenTheLineDoesNotSay() {
        val line = LogLine.encode(entry(), context).replace("\"log_level\":\"info\",", "")
        assertEquals(EventName.ACCESS_FIX_RESULT.level, LogLine.decode(line)!!.level)
    }

    @Test
    fun aCrashIsWrittenAtErrorLevelWithoutTheCallerChoosingIt() {
        // The level belongs to the event, so it cannot drift to "info" at a call site.
        val line = LogLine.encode(entry(event = EventName.CRASH), context)
        assertTrue(line.contains("\"log_level\":\"error\""))
    }

    @Test
    fun aMalformedLineIsDroppedRatherThanThrown() {
        listOf(
            "", "   ", "not json", "[]", "{", "{\"event_name\":\"not_an_event\"}",
            "{\"event_name\":\"crash\"", "null",
        ).forEach { assertNull("should not decode: $it", LogLine.decode(it)) }
    }

    @Test
    fun anUnknownEventIsDroppedRatherThanInvented() {
        // The event list is closed; a line naming something else cannot become an event.
        assertNull(LogLine.decode("{\"event_name\":\"something_else\",\"log_level\":\"info\"}"))
    }

    @Test
    fun aMissingAttemptIdIsWrittenAsNullAndDecodesToNull() {
        val line = LogLine.encode(entry(attemptId = null), context)
        assertTrue(line.contains("\"attempt_id\":null"))
        assertNull(LogLine.decode(line)!!.attemptId)
    }

    @Test
    fun onlyStringDetailsSurvive() {
        // A nested object is exactly what the flat shape exists to prevent.
        val decoded = LogLine.decode(
            "{\"event_name\":\"crash\",\"log_level\":\"error\",\"details\":{\"a\":\"b\"},\"n\":42}",
        )!!
        assertTrue("n" !in decoded.details)
    }

    @Test
    fun everyEventInTheVocabularyCanBeWrittenAndRead() {
        EventName.entries.forEach { event ->
            val decoded = LogLine.decode(LogLine.encode(entry(event = event), context))
            assertEquals("$event did not survive a round trip", event, decoded?.event)
        }
    }
}
