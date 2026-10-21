package com.progallery.app.data.usage

import java.time.LocalDate

/** How long an operation took, totalled over a day. */
data class PerfTotals(
    val count: Int = 0,
    val totalMs: Long = 0,
    val maxMs: Long = 0,
) {
    val averageMs: Long get() = if (count == 0) 0 else totalMs / count

    fun plus(millis: Long): PerfTotals = PerfTotals(
        count = count + 1,
        totalMs = totalMs + millis,
        maxMs = maxOf(maxMs, millis),
    )
}

/**
 * A crash, reduced to what is useful and safe.
 *
 * **No message is kept.** An exception's message is the single most likely place for a file
 * path, a search term or a person's name to end up in a log, so it is dropped entirely rather
 * than filtered. What is kept is the exception's class name and the class-and-method of the
 * first few frames — enough to find the fault, and nothing that can carry content.
 */
data class CrashRecord(
    val exceptionClass: String,
    val frames: List<String>,
    val build: String,
)

/** One day of counts. Days are the unit, so nothing is timestamped to the minute. */
data class UsageDay(
    val day: LocalDate,
    val events: Map<UsageEvent, Int> = emptyMap(),
    val features: Map<FeatureId, Int> = emptyMap(),
    val performance: Map<PerfOperation, PerfTotals> = emptyMap(),
    val crashes: List<CrashRecord> = emptyList(),
) {
    val isEmpty: Boolean
        get() = events.isEmpty() && features.isEmpty() && performance.isEmpty() && crashes.isEmpty()
}

/**
 * The device, described in the least identifying way that is still useful.
 *
 * A model and an Android version are what a bug report needs; a device identifier, an
 * advertising id or an account would be what it does not.
 */
data class DeviceProfile(
    val model: String,
    val androidVersion: Int,
    val appVersion: String,
    val appBuild: Int,
)
