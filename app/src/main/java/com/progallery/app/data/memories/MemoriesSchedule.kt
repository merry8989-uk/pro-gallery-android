package com.progallery.app.data.memories

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When the daily memories notification should arrive.
 *
 * Pure, because "the next seven o'clock" has three edges that are easy to get wrong and
 * impossible to notice: what happens at exactly seven, what happens at one minute to, and what
 * happens across midnight. All three are tested.
 */
object MemoriesSchedule {

    /** The hour the reminder arrives. Morning, as asked for. */
    const val HOUR = 7

    /**
     * How long until the next [HOUR] o'clock from [now].
     *
     * At exactly [HOUR] the answer is **a full day**, not zero: the reminder for today has
     * already been due, and scheduling it for "now" would deliver it twice.
     */
    fun untilNext(now: ZonedDateTime, hour: Int = HOUR): Duration {
        val today = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }

    /** The wall-clock time the reminder is aimed at. */
    fun at(): LocalTime = LocalTime.of(HOUR, 0)
}
