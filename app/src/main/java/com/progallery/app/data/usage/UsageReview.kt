package com.progallery.app.data.usage

import java.time.LocalDate

/** What the last week of usage says, in a form worth acting on. */
data class UsageDigest(
    val windowDays: Int,
    val daysWithData: Int,
    /** Most used first. */
    val features: List<Pair<FeatureId, Int>>,
    val events: List<Pair<UsageEvent, Int>>,
    /** Most used first, by average duration. */
    val slowest: List<Pair<PerfOperation, Long>>,
    val crashes: Int,
    /** Plain observations, derived from the numbers rather than written by hand. */
    val observations: List<String>,
) {
    val hasEnoughToJudge: Boolean get() = daysWithData >= windowDays
}

/**
 * Turns a week of counts into something that can be acted on.
 *
 * This is the "improve the UI every seven days" part made concrete: instead of a pile of
 * numbers, it says what the numbers *mean* — which area is carrying the app, which operation is
 * slow, whether crashes are happening. The observations are derived, so they cannot drift out
 * of step with the data they describe.
 */
object UsageReview {

    /** How slow an operation has to be before it is called out. */
    private const val SLOW_MS = 400L

    /** How much of the week's activity one area has to be before it is called the busiest. */
    private const val BUSY_SHARE = 0.4

    fun digest(
        days: List<UsageDay>,
        today: LocalDate,
        windowDays: Int = 7,
    ): UsageDigest {
        val from = today.minusDays((windowDays - 1).toLong())
        val window = days.filter { !it.day.isBefore(from) && !it.day.isAfter(today) }

        val features = window
            .flatMap { it.features.entries }
            .groupingBy { it.key }
            .fold(0) { sum, entry -> sum + entry.value }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val events = window
            .flatMap { it.events.entries }
            .groupingBy { it.key }
            .fold(0) { sum, entry -> sum + entry.value }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val performance = window
            .flatMap { it.performance.entries }
            .groupingBy { it.key }
            .fold(PerfTotals()) { sum, entry ->
                PerfTotals(
                    count = sum.count + entry.value.count,
                    totalMs = sum.totalMs + entry.value.totalMs,
                    maxMs = maxOf(sum.maxMs, entry.value.maxMs),
                )
            }

        val slowest = performance.entries
            .filter { it.value.count > 0 }
            .sortedByDescending { it.value.averageMs }
            .map { it.key to it.value.averageMs }

        val crashes = window.sumOf { it.crashes.size }

        return UsageDigest(
            windowDays = windowDays,
            daysWithData = window.count { !it.isEmpty },
            features = features,
            events = events,
            slowest = slowest,
            crashes = crashes,
            observations = observe(window, features, events, slowest, crashes, windowDays),
        )
    }

    private fun observe(
        window: List<UsageDay>,
        features: List<Pair<FeatureId, Int>>,
        events: List<Pair<UsageEvent, Int>>,
        slowest: List<Pair<PerfOperation, Long>>,
        crashes: Int,
        windowDays: Int,
    ): List<String> {
        val observations = mutableListOf<String>()
        val daysWithData = window.count { !it.isEmpty }

        if (daysWithData == 0) {
            return listOf("No usage recorded in the last $windowDays days yet.")
        }

        if (daysWithData < windowDays) {
            observations += "Recorded on $daysWithData of the last $windowDays days."
        }

        val totalFeatureUse = features.sumOf { it.second }
        features.firstOrNull()?.let { (feature, count) ->
            if (totalFeatureUse > 0 && count.toDouble() / totalFeatureUse >= BUSY_SHARE) {
                val share = ((count * 100) / totalFeatureUse)
                observations += "${feature.id} is the busiest area, at $share% of opens — it " +
                    "deserves the most attention."
            }
        }

        slowest.firstOrNull()?.let { (operation, average) ->
            if (average > SLOW_MS) {
                observations += "${operation.id} averages ${average}ms — the slowest thing here."
            }
        }

        val searches = events.firstOrNull { it.first == UsageEvent.SEARCH_PERFORMED }?.second ?: 0
        val albums = events.firstOrNull { it.first == UsageEvent.ALBUM_CREATED }?.second ?: 0
        if (searches > 0 && albums == 0) {
            observations += "Search is used but no album was created in this window."
        }

        if (crashes > 0) {
            observations += "$crashes crash${if (crashes == 1) "" else "es"} recorded — the " +
                "stack traces are in the diagnostic log."
        }

        val refusals = events.firstOrNull { it.first == UsageEvent.PERMISSION_REFUSED }?.second ?: 0
        if (refusals > 0) {
            observations += "A permission was refused $refusals time" +
                "${if (refusals == 1) "" else "s"} — a screen may be explaining itself poorly."
        }

        return observations
    }
}
