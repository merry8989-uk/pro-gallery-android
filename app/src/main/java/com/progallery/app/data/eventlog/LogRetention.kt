package com.progallery.app.data.eventlog

/**
 * What is kept, and what is dropped first.
 *
 * The schema asks for two things at once — **100% of errors, sampled info events** — and for a
 * log that lives on the device the honest reading of that is the *order things are dropped in*.
 * A cap alone would throw away the error that explains a session because fifty scroll events
 * happened after it; so when the file is over its limit, the oldest debug and info lines go
 * first, and errors survive until nothing else is left.
 *
 * Pure, so the order is unit tested rather than trusted.
 */
object LogRetention {

    fun prune(
        entries: List<LogEntry>,
        nowMillis: Long,
        keepEvents: Int,
        keepDays: Long,
    ): List<LogEntry> {
        val oldestAllowed = nowMillis - keepDays * 24L * 60L * 60L * 1000L

        // Age first: anything past the window goes, whatever level it is. A retention policy
        // that keeps some rows forever is not a retention policy.
        val recent = entries.filter { it.timestampMillis >= oldestAllowed }
        if (recent.size <= keepEvents) return recent

        // Over the limit: drop the oldest of the least useful level, repeatedly, until it fits.
        val keep = recent.toMutableList()
        val droppable = listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN)

        while (keep.size > keepEvents) {
            val victim = droppable.firstNotNullOfOrNull { level ->
                keep.withIndex().firstOrNull { it.value.level == level }?.index
            } ?: 0 // nothing droppable left: the oldest error goes, and the cap is still honoured
            keep.removeAt(victim)
        }
        return keep
    }
}
