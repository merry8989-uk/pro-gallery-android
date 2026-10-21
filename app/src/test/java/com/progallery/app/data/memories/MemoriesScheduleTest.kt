package com.progallery.app.data.memories

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edges are the whole point: a reminder that fires twice, or a day late, is worse than one
 * that never fires at all because it is harder to notice.
 */
class MemoriesScheduleTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(hour: Int, minute: Int, day: Int = 5) =
        ZonedDateTime.of(2026, 4, day, hour, minute, 0, 0, kolkata)

    @Test
    fun beforeSevenTheWaitIsUntilThisMorning() {
        val wait = MemoriesSchedule.untilNext(at(6, 0))
        assertEquals(Duration.ofHours(1), wait)
    }

    @Test
    fun atSevenTheWaitIsAFullDay() {
        // Not zero: today's reminder has already been due. Scheduling it for "now" would
        // deliver it twice.
        val wait = MemoriesSchedule.untilNext(at(7, 0))
        assertEquals(Duration.ofDays(1), wait)
    }

    @Test
    fun oneMinuteBeforeSevenTheWaitIsOneMinute() {
        assertEquals(Duration.ofMinutes(1), MemoriesSchedule.untilNext(at(6, 59)))
    }

    @Test
    fun oneSecondAfterSevenTheWaitIsNearlyAFullDay() {
        val wait = MemoriesSchedule.untilNext(
            ZonedDateTime.of(2026, 4, 5, 7, 0, 1, 0, kolkata),
        )
        assertEquals(Duration.ofDays(1).minusSeconds(1), wait)
    }

    @Test
    fun lateAtNightTheWaitIsUntilTomorrowMorning() {
        assertEquals(Duration.ofHours(7), MemoriesSchedule.untilNext(at(0, 0, day = 5)))
        assertEquals(Duration.ofHours(8), MemoriesSchedule.untilNext(at(23, 0, day = 5)))
    }

    @Test
    fun theWaitIsAlwaysPositiveAndAtMostADay() {
        // Whatever the moment, the answer must be schedulable.
        (0..23).forEach { hour ->
            val wait = MemoriesSchedule.untilNext(at(hour, 0))
            assertTrue("at $hour:00 the wait must be positive", wait > Duration.ZERO)
            assertTrue("at $hour:00 the wait must fit in a day", wait <= Duration.ofDays(1))
        }
    }

    @Test
    fun midnightIsSevenHoursAwayNotSeventeen() {
        assertEquals(Duration.ofHours(7), MemoriesSchedule.untilNext(at(0, 0)))
    }

    @Test
    fun theHourIsSevenInTheMorning() {
        assertEquals(7, MemoriesSchedule.at().hour)
        assertEquals(0, MemoriesSchedule.at().minute)
    }

    @Test
    fun theHourCanBeChosen() {
        assertEquals(Duration.ofHours(2), MemoriesSchedule.untilNext(at(6, 0), hour = 8))
    }
}
