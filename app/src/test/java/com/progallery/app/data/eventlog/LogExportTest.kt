package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export formats, and the CSV in particular — quoting is where a CSV silently corrupts: one
 * value containing a comma shifts every column after it, and the file still opens.
 */
class LogExportTest {

    private val context = LogContext("1.28.0 (44)", "Android 14", "mobile", "production", "wifi")

    private fun entry(
        event: EventName = EventName.ACCESS_FIX_RESULT,
        details: Map<String, String> = emptyMap(),
    ) = LogEntry(
        event = event,
        timestampMillis = 1_764_000_000_000,
        userCode = "PG-4F9-K2Q",
        sessionId = "sess_9f2a",
        attemptId = "att_71c3",
        screen = "AccessDiagnostics",
        details = details,
    )

    @Test
    fun theCsvStartsWithTheEnvelopeColumns() {
        val csv = LogExport.csv(listOf(entry()), context)
        val header = csv.lineSequence().first()
        listOf("timestamp", "user_code", "event_name", "log_level", "attempt_id", "schema_version")
            .forEach { assertTrue("$it missing from $header", header.contains(it)) }
    }

    @Test
    fun aValueWithACommaIsQuotedSoTheColumnsStayAligned() {
        val csv = LogExport.csv(
            listOf(entry(details = mapOf("note" to "one, two, three"))),
            context,
        )
        assertTrue("the comma must be inside quotes", csv.contains("\"one, two, three\""))

        // Every row must have the same number of fields, or a spreadsheet silently shifts them.
        val fields = csv.trim().lineSequence().map { countFields(it) }.toSet()
        assertEquals("rows disagree about how many columns there are: $fields", 1, fields.size)
    }

    @Test
    fun aValueWithAQuoteIsQuotedAndDoubled() {
        val csv = LogExport.csv(listOf(entry(details = mapOf("note" to "say \"hi\""))), context)
        assertTrue(csv.contains("\"say \"\"hi\"\"\""))
    }

    @Test
    fun aValueWithANewlineIsQuoted() {
        val csv = LogExport.csv(listOf(entry(details = mapOf("note" to "a\nb"))), context)
        assertTrue(csv.contains("\"a\nb\""))
    }

    @Test
    fun theDetailColumnsAreTheUnionOfEveryEvent() {
        // Two events with different properties still line up in one sheet — which is the point of
        // exporting to a CSV at all.
        val csv = LogExport.csv(
            listOf(
                entry(details = mapOf("outcome" to "success")),
                entry(event = EventName.CRASH, details = mapOf("exception_type" to "NPE")),
            ),
            context,
        )
        val header = csv.lineSequence().first()
        assertTrue(header.contains("outcome"))
        assertTrue(header.contains("exception_type"))
    }

    @Test
    fun theJsonIsAnArrayOfObjects() {
        val json = LogExport.json(listOf(entry(), entry()), context)
        assertTrue(json.trimStart().startsWith("["))
        assertTrue(json.trimEnd().endsWith("]"))
        assertEquals("two objects", 2, json.split("\"event_name\"").size - 1)
    }

    @Test
    fun theMarkdownSummarisesAndEscapesPipes() {
        val markdown = LogExport.markdown(
            listOf(entry(), entry(event = EventName.CRASH, details = mapOf("note" to "a|b"))),
        )
        assertTrue(markdown.contains("2 events"))
        assertTrue(markdown.contains("1 error"))
        assertTrue("a pipe would break the table", markdown.contains("a\\|b"))
    }

    @Test
    fun theTextHasOneLinePerEvent() {
        val text = LogExport.text(listOf(entry(), entry(event = EventName.CRASH)))
        assertEquals(2, text.trim().lines().size)
        assertTrue(text.contains("access_fix_result"))
    }

    @Test
    fun anEmptyLogStillProducesSomethingOpenable() {
        assertEquals("[\n\n]\n", LogExport.json(emptyList(), context))
        assertTrue(LogExport.csv(emptyList(), context).isNotBlank())
        assertTrue(LogExport.markdown(emptyList()).contains("0 events"))
        assertEquals("", LogExport.text(emptyList()))
    }

    /** Counts CSV fields, honouring quotes — the check the quoting exists to pass. */
    private fun countFields(line: String): Int {
        var fields = 1
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            when (line[index]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) fields++
            }
            index++
        }
        return fields
    }
}
