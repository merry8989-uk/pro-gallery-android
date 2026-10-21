package com.progallery.app.data.usage

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCodecTest {

    private val profile = DeviceProfile("Pixel 6", 34, "1.24.0", 40)

    private val day = UsageDay(
        day = LocalDate.of(2026, 10, 21),
        events = mapOf(UsageEvent.APP_OPENED to 3, UsageEvent.SEARCH_PERFORMED to 1),
        features = mapOf(FeatureId.PHOTOS to 12, FeatureId.SEARCH to 4),
        performance = mapOf(PerfOperation.LIBRARY_LOAD to PerfTotals(2, 1800, 1200)),
        crashes = listOf(CrashRecord("NullPointerException", listOf("Gallery.load", "Repo.read"), "1.24.0")),
    )

    @Test
    fun whatIsWrittenCanBeReadBack() {
        val (decodedProfile, decodedDays) = UsageCodec.decode(UsageCodec.encode(profile, listOf(day)))
        assertEquals(profile, decodedProfile)
        assertEquals(1, decodedDays.size)
        val back = decodedDays.single()
        assertEquals(day.day, back.day)
        assertEquals(day.events, back.events)
        assertEquals(day.features, back.features)
        assertEquals(day.performance, back.performance)
        assertEquals(day.crashes, back.crashes)
    }

    @Test
    fun severalDaysSurvive() {
        val other = day.copy(day = LocalDate.of(2026, 10, 20), features = mapOf(FeatureId.VIDEOS to 7))
        val (_, days) = UsageCodec.decode(UsageCodec.encode(profile, listOf(day, other)))
        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 10, 20), days.first().day)
    }

    @Test
    fun anEmptyFileReadsAsNothingRatherThanFailing() {
        val (decodedProfile, days) = UsageCodec.decode("")
        assertTrue(days.isEmpty())
        assertEquals("unknown", decodedProfile.model)
    }

    @Test
    fun anUnknownIdentifierIsIgnoredRatherThanInvented() {
        // A file from a newer build may name events this build does not know. Dropping them is
        // right: guessing what they meant would put made-up data in the review.
        val text = """
            day=2026-10-21
            event=some.future.event 5
            event=app.opened 2
            feature=not_a_feature 9
        """.trimIndent()
        val (_, days) = UsageCodec.decode(text)
        assertEquals(mapOf(UsageEvent.APP_OPENED to 2), days.single().events)
        assertTrue(days.single().features.isEmpty())
    }

    @Test
    fun commentsAndBlankLinesAreSkipped() {
        val text = "# a comment\n\nday=2026-10-21\nevent=app.opened 1\n"
        assertEquals(1, UsageCodec.decode(text).second.single().events[UsageEvent.APP_OPENED])
    }

    @Test
    fun aDayIsOnlyCreatedWhenItIsNamed() {
        // Events before any day line have nowhere to go, and must not be attached to a
        // different day.
        val (_, days) = UsageCodec.decode("event=app.opened 5\nday=2026-10-21\nevent=app.opened 1")
        assertEquals(1, days.single().events[UsageEvent.APP_OPENED])
    }

    @Test
    fun aCrashWithNoFramesSurvives() {
        val text = "day=2026-10-21\ncrash=OutOfMemoryError  1.24.0"
        val crash = UsageCodec.decode(text).second.single().crashes.single()
        assertEquals("OutOfMemoryError", crash.exceptionClass)
        assertEquals("1.24.0", crash.build)
    }

    @Test
    fun theTotalsRoundTripExactly() {
        val (_, days) = UsageCodec.decode(UsageCodec.encode(profile, listOf(day)))
        val totals = days.single().performance.getValue(PerfOperation.LIBRARY_LOAD)
        assertEquals(2, totals.count)
        assertEquals(1800, totals.totalMs)
        assertEquals(1200, totals.maxMs)
        assertEquals(900, totals.averageMs)
    }

    @Test
    fun averageOfNothingIsZeroRatherThanACrash() {
        assertEquals(0, PerfTotals().averageMs)
    }
}
