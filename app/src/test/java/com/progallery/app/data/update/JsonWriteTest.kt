package com.progallery.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON writer.
 *
 * A writer is where a request body is assembled, and a value containing a quote is where it goes
 * wrong: the body is rejected, or — worse — accepted and read as something other than what was
 * meant. So the escaping is checked against what the reader gives back.
 */
class JsonWriteTest {

    @Test
    fun aFlatObjectRoundTrips() {
        val written = Json.write(mapOf("event_name" to "crash", "count" to 3, "ok" to true))
        val read = Json.objectAt(Json.parse(written))!!
        assertEquals("crash", Json.stringAt(read, "event_name"))
        assertEquals(3.0, Json.numberAt(read, "count")!!, 0.0)
        assertEquals(true, Json.booleanAt(read, "ok"))
    }

    @Test
    fun aQuoteIsEscapedAndSurvives() {
        val value = "say \"hello\""
        val written = Json.write(mapOf("note" to value))
        assertTrue("the quote must be escaped", written.contains("\\\""))
        val read = Json.objectAt(Json.parse(written))!!
        assertEquals(value, Json.stringAt(read, "note"))
    }

    @Test
    fun aBackslashAndANewlineSurvive() {
        val value = "a\\b\nc\td"
        val written = Json.write(mapOf("note" to value))
        assertEquals(value, Json.stringAt(Json.objectAt(Json.parse(written))!!, "note"))
    }

    @Test
    fun aControlCharacterIsWrittenAsAnEscape() {
        // A raw control character in a body is invalid JSON, and some servers reject the request
        // outright rather than the value.
        val written = Json.write(mapOf("note" to "a\u0001b"))
        assertTrue("expected a \\u escape, got $written", written.contains("\\u0001"))
        assertEquals("a\u0001b", Json.stringAt(Json.objectAt(Json.parse(written))!!, "note"))
    }

    @Test
    fun nestedObjectsAndListsRoundTrip() {
        val body = mapOf(
            "events" to listOf(
                mapOf("event_name" to "crash", "details" to mapOf("outcome" to "success")),
                mapOf("event_name" to "anr", "attempt_id" to "att_1"),
            ),
        )
        val read = Json.objectAt(Json.parse(Json.write(body)))!!
        val events = Json.arrayAt(read["events"])!!
        assertEquals(2, events.size)
        assertEquals("crash", Json.stringAt(Json.objectAt(events[0])!!, "event_name"))
        assertEquals(
            "success",
            Json.stringAt(Json.objectAt(Json.objectAt(events[0])!!["details"])!!, "outcome"),
        )
    }

    @Test
    fun aWholeNumberIsNotWrittenWithADecimalPoint() {
        // 412 must not go up as 412.0 — the column is a count.
        assertEquals("412", Json.write(mapOf("media_item_count" to 412)))
        assertEquals("412", Json.write(mapOf("n" to 412.0)))
        assertEquals("412.5", Json.write(mapOf("n" to 412.5)))
    }

    @Test
    fun nullIsWrittenAsNull() {
        assertEquals("{\"attempt_id\":null}", Json.write(mapOf("attempt_id" to null)))
    }

    @Test
    fun anEmptyObjectAndListAreWritten() {
        assertEquals("{}", Json.write(emptyMap<String, Any>()))
        assertEquals("[]", Json.write(emptyList<String>()))
    }

    @Test
    fun theReaderAndTheWriterAgreeOnAnEmptyString() {
        // stringAt filters blanks out, so this checks the writer, not the reader's choice.
        assertEquals("{\"a\":\"\"}", Json.write(mapOf("a" to "")))
    }
}
