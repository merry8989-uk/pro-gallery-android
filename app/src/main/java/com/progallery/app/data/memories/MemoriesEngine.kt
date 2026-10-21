package com.progallery.app.data.memories

import com.progallery.app.data.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A day from an earlier year worth showing again. */
data class MemoryGroup(
    /** How many years ago this was. Always at least 1 — today is not a memory. */
    val yearsAgo: Int,
    /** The day it happened. */
    val date: LocalDate,
    val items: List<MediaItem>,
)

data class Memories(
    /** The same day and month in earlier years — the anniversaries. */
    val onThisDay: List<MemoryGroup>,
    /** The same month, other days, in earlier years. */
    val thisMonth: List<MemoryGroup>,
) {
    val isEmpty: Boolean get() = onThisDay.isEmpty() && thisMonth.isEmpty()

    /** How many pictures in total, for the notification and the heading. */
    val itemCount: Int
        get() = onThisDay.sumOf { it.items.size } + thisMonth.sumOf { it.items.size }

    val years: List<Int> get() = onThisDay.map { it.yearsAgo }
}

/**
 * Finds the pictures worth showing again today.
 *
 * **Which date is used, and why it matters.** A photo's *capture* date, not the date the file
 * was last written. The difference is not academic: copying a photo, restoring a backup, or
 * having an app rewrite it all change the modification date, so a picture taken in 2018 can
 * carry a 2026 modification date and would then never be remembered. The capture date comes
 * from the file's own properties; the modification date is only a fallback for files that do
 * not carry one.
 *
 * Pure, so the awkward parts — leap years, February, time zones, "today is not a memory" — are
 * unit tested rather than discovered in April.
 */
object MemoriesEngine {

    fun forDate(
        items: List<MediaItem>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Memories {
        val onThisDay = LinkedHashMap<Int, MutableList<MediaItem>>()
        val thisMonth = LinkedHashMap<Int, MutableList<MediaItem>>()

        items.forEach { item ->
            val taken = item.memoryDate(zone) ?: return@forEach
            if (taken.year >= today.year) return@forEach

            val yearsAgo = today.year - taken.year
            when {
                sameAnniversary(taken, today) ->
                    onThisDay.getOrPut(yearsAgo) { mutableListOf() } += item

                taken.monthValue == today.monthValue ->
                    thisMonth.getOrPut(yearsAgo) { mutableListOf() } += item
            }
        }

        return Memories(
            onThisDay = onThisDay.toGroups(zone),
            thisMonth = thisMonth.toGroups(zone),
        )
    }

    /**
     * Whether [taken] falls on today's anniversary.
     *
     * One deliberate exception: a picture taken on **29 February** is shown on **28 February**
     * in a year that has no 29th. Otherwise those pictures would be remembered once every four
     * years, which is not what "on this day" is for.
     */
    private fun sameAnniversary(taken: LocalDate, today: LocalDate): Boolean {
        if (taken.monthValue != today.monthValue) return false
        if (taken.dayOfMonth == today.dayOfMonth) return true
        return today.monthValue == 2 &&
            today.dayOfMonth == 28 &&
            taken.dayOfMonth == 29 &&
            !today.isLeapYear
    }

    private fun Map<Int, MutableList<MediaItem>>.toGroups(zone: ZoneId): List<MemoryGroup> =
        entries
            // Newest anniversary first: last year before ten years ago.
            .sortedBy { it.key }
            .map { (yearsAgo, group) ->
                // Sorted by the instant, not by the day. Sorting by the day leaves the order
                // within a day up to the order the library happened to return, so a morning and
                // an evening picture of the same day would come out in an arbitrary order.
                val sorted = group.sortedByDescending { it.memorySeconds() }
                MemoryGroup(
                    yearsAgo = yearsAgo,
                    date = sorted.firstOrNull()?.memoryDate(zone) ?: LocalDate.MIN,
                    items = sorted,
                )
            }
}

/**
 * The day a picture was taken.
 *
 * The capture date when the file carries one, and the modification date only as a fallback.
 * Null when the item has no usable date at all, so it is left out rather than filed under 1970.
 */
fun MediaItem.memoryDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    memorySeconds().takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }

/**
 * The instant a picture belongs to: its capture time when known, its modification time
 * otherwise, and zero when neither is usable.
 *
 * Separate from [memoryDate] because two pictures can share a day and still have an order, and
 * a memory should show them in the order they were taken.
 */
fun MediaItem.memorySeconds(): Long = dateTakenSeconds.takeIf { it > 0 } ?: dateModifiedSeconds
