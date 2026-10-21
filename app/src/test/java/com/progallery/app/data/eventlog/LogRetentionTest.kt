package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What survives the cap.
 *
 * The rule being tested is the one that makes a log useful: when something has to go, the oldest
 * debug and info lines go first, so the error that explains the session is still there.
 */
class LogRetentionTest {

    private val now = 1_764_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun entry(level: LogLevel, ageDays: Long, name: String = "e") = LogEntry(
        event = if (level == LogLevel.ERROR) EventName.CRASH else EventName.SCREEN_VIEW,
        timestampMillis = now - ageDays * day,
        userCode = "PG-4F9-K2Q",
        sessionId = "sess",
        level = level,
    )

    @Test
    fun everythingInsideTheWindowAndUnderTheCapIsKept() {
        val entries = listOf(entry(LogLevel.INFO, 1), entry(LogLevel.ERROR, 2))
        assertEquals(entries, LogRetention.prune(entries, now, keepEvents = 500, keepDays = 7))
    }

    @Test
    fun anythingOlderThanTheWindowGoesWhateverItsLevel() {
        // A retention policy that keeps some rows forever is not a retention policy.
        val entries = listOf(entry(LogLevel.INFO, 1), entry(LogLevel.ERROR, 30))
        val kept = LogRetention.prune(entries, now, keepEvents = 500, keepDays = 7)
        assertEquals(1, kept.size)
        assertEquals(LogLevel.INFO, kept.single().level)
    }

    @Test
    fun theOldestLeastUsefulLinesGoFirst() {
        val entries = listOf(
            entry(LogLevel.DEBUG, 6),
            entry(LogLevel.INFO, 5),
            entry(LogLevel.INFO, 4),
            entry(LogLevel.WARN, 3),
        )
        val kept = LogRetention.prune(entries, now, keepEvents = 2, keepDays = 7)
        assertEquals(2, kept.size)
        // The debug went first, then the oldest info.
        assertTrue(kept.none { it.level == LogLevel.DEBUG })
        assertEquals(listOf(4L, 3L), kept.map { (now - it.timestampMillis) / day }.sorted())
    }

    @Test
    fun anErrorSurvivesWhileAnythingLesserIsLeft() {
        // The whole point: the fifty scroll events after a crash must not push the crash out.
        val entries = listOf(
            entry(LogLevel.ERROR, 6),
            entry(LogLevel.INFO, 5),
            entry(LogLevel.INFO, 4),
            entry(LogLevel.DEBUG, 3),
        )
        val kept = LogRetention.prune(entries, now, keepEvents = 1, keepDays = 7)
        assertEquals(1, kept.size)
        assertEquals(LogLevel.ERROR, kept.single().level)
    }

    @Test
    fun theCapIsHonouredEvenWhenOnlyErrorsRemain() {
        // The cap is still the cap: a log that can grow without bound is a different problem.
        val entries = (1..5).map { entry(LogLevel.ERROR, it.toLong()) }
        val kept = LogRetention.prune(entries, now, keepEvents = 2, keepDays = 7)
        assertEquals(2, kept.size)
    }

    @Test
    fun anEmptyLogStaysEmpty() {
        assertTrue(LogRetention.prune(emptyList(), now, keepEvents = 500, keepDays = 7).isEmpty())
    }
}
