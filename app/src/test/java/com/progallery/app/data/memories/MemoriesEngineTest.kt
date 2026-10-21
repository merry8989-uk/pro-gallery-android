package com.progallery.app.data.memories

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date arithmetic is the whole feature, and it is the part that is easy to get wrong and
 * hard to notice — a memory that never appears looks exactly like a memory that has not
 * happened yet. So it is all pinned here.
 */
class MemoriesEngineTest {

    /** Noon UTC, so a test never depends on which side of midnight a zone sits. */
    private val utc = ZoneOffset.UTC

    private fun at(epochSeconds: Long, id: Long = epochSeconds, name: String = "p$id.jpg") =
        TestMedia.item(id = id, name = name).copy(dateModifiedSeconds = epochSeconds)

    private fun taken(epochSeconds: Long, id: Long = epochSeconds, name: String = "p$id.jpg") =
        at(epochSeconds, id, name).copy(dateTakenSeconds = epochSeconds)

    private val day = LocalDate.of(2026, 4, 5)

    // ---- the anniversary ----

    @Test
    fun aPhotoFromThisDayInAnEarlierYearIsAnAnniversary() {
        val memories = MemoriesEngine.forDate(listOf(taken(1_522_929_600)), day, utc)
        assertEquals(1, memories.onThisDay.size)
        assertEquals(8, memories.onThisDay.single().yearsAgo)
        assertEquals(LocalDate.of(2018, 4, 5), memories.onThisDay.single().date)
        assertEquals(1, memories.itemCount)
    }

    @Test
    fun aPhotoFromTodayIsNotAMemory() {
        // "Today is not a memory" — it is today.
        val memories = MemoriesEngine.forDate(listOf(taken(1_775_390_400)), day, utc)
        assertTrue(memories.isEmpty)
    }

    @Test
    fun severalYearsOfTheSameDayBecomeSeparateGroupsNewestFirst() {
        val memories = MemoriesEngine.forDate(
            listOf(taken(1_522_929_600, id = 1), taken(1_554_465_600, id = 2)),
            day,
            utc,
        )
        assertEquals(listOf(7, 8), memories.onThisDay.map { it.yearsAgo })
    }

    // ---- the capture date, which is the point ----

    @Test
    fun theCaptureDateIsUsedEvenWhenTheFileWasWrittenLater() {
        // The bug this guards: copying or restoring a photo rewrites its modification date, so
        // a 2018 picture can carry a 2026 date. Using that would lose the memory for ever.
        val photo = TestMedia.item(id = 1)
            .copy(
                dateTakenSeconds = 1_522_929_600,   // 2018-04-05
                dateModifiedSeconds = 1_775_390_400, // 2026-04-05 — rewritten by a backup
            )
        val memories = MemoriesEngine.forDate(listOf(photo), day, utc)
        assertEquals(1, memories.onThisDay.size)
        assertEquals(8, memories.onThisDay.single().yearsAgo)
    }

    @Test
    fun theModificationDateIsTheFallbackWhenThereIsNoCaptureDate() {
        val photo = at(1_522_929_600).copy(dateTakenSeconds = 0L)
        assertEquals(1, MemoriesEngine.forDate(listOf(photo), day, utc).onThisDay.size)
    }

    @Test
    fun aFileWithNoDateAtAllIsLeftOutRatherThanFiledUnder1970() {
        val undated = TestMedia.item(id = 1).copy(dateModifiedSeconds = 0L, dateTakenSeconds = 0L)
        assertTrue(MemoriesEngine.forDate(listOf(undated), day, utc).isEmpty)
    }

    @Test
    fun aFutureDatedFileIsIgnored() {
        // A wrong device clock must not produce a memory of the future.
        val future = taken(1_900_000_000)
        assertTrue(MemoriesEngine.forDate(listOf(future), day, utc).isEmpty)
    }

    // ---- the month ----

    @Test
    fun anotherDayOfTheSameMonthIsNotAnAnniversaryButIsStillTheMonth() {
        val memories = MemoriesEngine.forDate(listOf(taken(1_524_225_600)), day, utc) // 2018-04-20
        assertTrue("a different day is not the anniversary", memories.onThisDay.isEmpty())
        assertEquals(1, memories.thisMonth.size)
        assertEquals(8, memories.thisMonth.single().yearsAgo)
    }

    @Test
    fun aDifferentMonthIsNotIncludedAtAll() {
        val memories = MemoriesEngine.forDate(listOf(taken(1_523_016_000)), day, utc) // 2018-04-06 is same month
        assertEquals(1, memories.thisMonth.size)

        val march = MemoriesEngine.forDate(listOf(taken(1_521_000_000)), day, utc) // 2018-03-14
        assertTrue("March is not April", march.isEmpty)
    }

    // ---- February, which is the awkward one ----

    @Test
    fun aLeapDayPhotoIsRememberedOnThe28thInANonLeapYear() {
        // Otherwise a 29 February picture is remembered once every four years.
        val memories = MemoriesEngine.forDate(
            listOf(taken(1_456_747_200)), // 2016-02-29
            LocalDate.of(2026, 2, 28),
            utc,
        )
        assertEquals(1, memories.onThisDay.size)
        assertEquals(10, memories.onThisDay.single().yearsAgo)
    }

    @Test
    fun aLeapDayPhotoIsNotMovedWhenTheYearHasA29th() {
        // In a leap year the 28th is the 28th, and the photo belongs on the 29th.
        val memories = MemoriesEngine.forDate(
            listOf(taken(1_709_208_000)), // 2024-02-29
            LocalDate.of(2028, 2, 28),
            utc,
        )
        assertTrue("there is a 29 February in 2028", memories.onThisDay.isEmpty())
    }

    // ---- ordering, and the shape of the result ----

    @Test
    fun picturesWithinAGroupAreNewestFirst() {
        // Two pictures from the same day, taken eight hours apart, in the same year — so they
        // land in one group and their order within it is what is being checked.
        val memories = MemoriesEngine.forDate(
            listOf(
                taken(1_522_915_200, id = 1), // 2018-04-05 08:00 UTC
                taken(1_522_951_200, id = 2), // 2018-04-05 18:00 UTC
            ),
            day,
            utc,
        )
        val group = memories.onThisDay.single()
        assertEquals(8, group.yearsAgo)
        assertEquals("the later picture comes first", listOf(2L, 1L), group.items.map { it.id })
    }

    @Test
    fun theYearsAreAvailableForTheHeading() {
        val memories = MemoriesEngine.forDate(
            listOf(taken(1_522_929_600, id = 1), taken(1_554_465_600, id = 2)),
            day,
            utc,
        )
        assertEquals(listOf(7, 8), memories.years)
    }

    @Test
    fun anEmptyLibraryHasNothingToShow() {
        val memories = MemoriesEngine.forDate(emptyList(), day, utc)
        assertTrue(memories.isEmpty)
        assertEquals(0, memories.itemCount)
    }

    // ---- the zone, which decides what "today" even is ----

    @Test
    fun theZoneDecidesWhichDayAPhotoFallsOn() {
        // 2018-04-06 00:15 in Kolkata is 2018-04-05 18:45 UTC: one instant, two different days.
        val lateNight = taken(1_522_953_900)
        assertEquals(
            "in Kolkata that is the 6th, so not the 5th's anniversary",
            0,
            MemoriesEngine.forDate(listOf(lateNight), day, ZoneId.of("Asia/Kolkata")).onThisDay.size,
        )
        assertEquals(
            "in UTC it is still the 5th",
            1,
            MemoriesEngine.forDate(listOf(lateNight), day, ZoneOffset.UTC).onThisDay.size,
        )
    }

    @Test
    fun aPhotoFromLastYearIsOneYearAgoNotZero() {
        // 2025-04-05: the day before the anniversary year, and never "0 years ago".
        val memories = MemoriesEngine.forDate(listOf(taken(1_743_854_400)), day, utc)
        assertEquals(listOf(1), memories.years)
        assertFalse("today must never be a memory", memories.years.contains(0))
    }

    @Test
    fun memoryDateIsTheCaptureDateWhenPresent() {
        val photo: MediaItem = TestMedia.item(id = 1).copy(
            dateTakenSeconds = 1_522_929_600,
            dateModifiedSeconds = 1_775_390_400,
        )
        assertEquals(LocalDate.of(2018, 4, 5), photo.memoryDate(utc))
    }

    @Test
    fun memoryDateIsNullRatherThan1970WhenThereIsNoDate() {
        val photo = TestMedia.item(id = 1).copy(dateModifiedSeconds = 0L, dateTakenSeconds = 0L)
        assertEquals(null, photo.memoryDate(utc))
    }
}
