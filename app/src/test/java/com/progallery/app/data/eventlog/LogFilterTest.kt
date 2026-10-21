package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the log is narrowed to.
 *
 * The screen and any future export both go through here, so the meaning of "filtered" is pinned
 * in one place rather than in a UI.
 */
class LogFilterTest {

    private fun entry(
        event: EventName,
        level: LogLevel = event.level,
        userCode: String = "PG-4F9-K2Q",
        screen: String? = "MediaAccessDiagnostics",
        details: Map<String, String> = emptyMap(),
    ) = LogEntry(
        event = event,
        timestampMillis = 1_764_000_000_000,
        userCode = userCode,
        sessionId = "sess_9f2a",
        screen = screen,
        details = details,
        level = level,
    )

    private val entries = listOf(
        entry(EventName.ACCESS_FIX_REQUESTED, details = mapOf("state_before" to "partial")),
        entry(EventName.ACCESS_FIX_RESULT, details = mapOf("outcome" to "success")),
        entry(EventName.CRASH, details = mapOf("exception_type" to "NullPointerException")),
        entry(EventName.ACCESS_FIX_REQUESTED, userCode = "PG-7TT-111"),
    )

    @Test
    fun noFilterKeepsEverything() {
        assertEquals(entries, LogFilters.apply(entries, LogFilter()))
        assertFalse(LogFilter().isActive)
    }

    @Test
    fun aLevelNarrowsToOneSeverity() {
        val errors = LogFilters.apply(entries, LogFilter(level = LogLevel.ERROR))
        assertEquals(1, errors.size)
        assertEquals(EventName.CRASH, errors.single().event)
    }

    @Test
    fun anEventNarrowsToOneEvent() {
        val fixes = LogFilters.apply(entries, LogFilter(event = EventName.ACCESS_FIX_REQUESTED))
        assertEquals(2, fixes.size)
        assertTrue(fixes.all { it.event == EventName.ACCESS_FIX_REQUESTED })
    }

    @Test
    fun aUserCodeMatchesOnAPrefixSoAPartlyTypedCodeStillWorks() {
        // A code is copied by hand; demanding all nine characters before showing anything makes
        // the filter feel broken.
        val mine = LogFilters.apply(entries, LogFilter(userCode = "PG-4F9"))
        assertEquals(3, mine.size)
        assertTrue(mine.all { it.userCode == "PG-4F9-K2Q" })
    }

    @Test
    fun theUserCodeIsMatchedWithoutRegardToCase() {
        assertEquals(
            LogFilters.apply(entries, LogFilter(userCode = "pg-4f9")).size,
            LogFilters.apply(entries, LogFilter(userCode = "PG-4F9")).size,
        )
    }

    @Test
    fun freeTextSearchesTheEventTheScreenAndTheDetails() {
        assertEquals(1, LogFilters.apply(entries, LogFilter(text = "crash")).size)
        assertEquals(1, LogFilters.apply(entries, LogFilter(text = "exception_type")).size)
        assertEquals(
            "the detail value is searchable too",
            1,
            LogFilters.apply(entries, LogFilter(text = "NullPointer")).size,
        )
        assertEquals(4, LogFilters.apply(entries, LogFilter(text = "MediaAccessDiagnostics")).size)
    }

    @Test
    fun criteriaCombineWithAnd() {
        // The question people actually ask is "the errors from this device", not one or the other.
        val narrow = LogFilters.apply(
            entries,
            LogFilter(level = LogLevel.ERROR, userCode = "PG-7TT"),
        )
        assertTrue("no error came from that code", narrow.isEmpty())

        val both = LogFilters.apply(
            entries,
            LogFilter(event = EventName.ACCESS_FIX_REQUESTED, userCode = "PG-7TT"),
        )
        assertEquals(1, both.size)
    }

    @Test
    fun blankCriteriaAreIgnoredRatherThanMatchingNothing() {
        // An empty search box must not hide everything.
        val all = LogFilters.apply(
            entries,
            LogFilter(userCode = "  ", text = ""),
        )
        assertEquals(entries.size, all.size)
        assertFalse(LogFilter(userCode = "  ").isActive)
    }

    @Test
    fun aFilterThatMatchesNothingReturnsNothing() {
        assertTrue(LogFilters.apply(entries, LogFilter(event = EventName.ANR)).isEmpty())
    }

    @Test
    fun countsByLevelCountsEveryLevel() {
        val counts = LogFilters.countsByLevel(entries)
        assertEquals(4, counts.size)
        assertEquals(1, counts[LogLevel.ERROR])
        assertEquals(3, counts[LogLevel.INFO])
        assertEquals(0, counts[LogLevel.WARN])
    }

    @Test
    fun clearingRemovesEveryCriterion() {
        val filter = LogFilter(level = LogLevel.ERROR, event = EventName.CRASH, userCode = "PG", text = "x")
        assertTrue(filter.isActive)
        assertFalse(filter.cleared().isActive)
    }
}
