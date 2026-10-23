package com.progallery.app.data.usage

import java.time.LocalDate
import com.progallery.app.util.AppIdentity

/**
 * Reads and writes the stored usage file.
 *
 * A line-oriented text format rather than JSON, for two reasons: it appends and reads without a
 * parser to get wrong, and **it can be read by a person**. Data collected about someone should
 * be legible to them, and a file they can open in a text editor is the plainest form of that.
 *
 * Pure, so the format is tested rather than trusted with real data.
 */
object UsageCodec {

    private val HEADER = "# ${AppIdentity.NAME} usage — counts only. No filenames, no paths, no content."

    fun encode(profile: DeviceProfile, days: List<UsageDay>): String = buildString {
        appendLine(HEADER)
        appendLine("build=${profile.appVersion}/${profile.appBuild}")
        appendLine("model=${profile.model}")
        appendLine("android=${profile.androidVersion}")
        days.sortedBy { it.day }.forEach { day ->
            appendLine("day=${day.day}")
            day.events.forEach { (event, count) -> appendLine("event=${event.id} $count") }
            day.features.forEach { (feature, count) -> appendLine("feature=${feature.id} $count") }
            day.performance.forEach { (operation, totals) ->
                appendLine("perf=${operation.id} ${totals.count} ${totals.totalMs} ${totals.maxMs}")
            }
            day.crashes.forEach { crash ->
                appendLine("crash=${crash.exceptionClass} ${crash.frames.joinToString("|")} ${crash.build}")
            }
        }
    }

    fun decode(text: String): Pair<DeviceProfile, List<UsageDay>> {
        var model = "unknown"
        var android = 0
        var appVersion = "unknown"
        var appBuild = 0
        val days = LinkedHashMap<LocalDate, MutableDay>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            when {
                line.startsWith("build=") -> line.removePrefix("build=").split('/').let { parts ->
                    appVersion = parts.getOrNull(0) ?: "unknown"
                    appBuild = parts.getOrNull(1)?.toIntOrNull() ?: 0
                }

                line.startsWith("model=") -> model = line.removePrefix("model=")

                line.startsWith("android=") -> android = line.removePrefix("android=").toIntOrNull() ?: 0

                line.startsWith("day=") -> {
                    val date = runCatching { LocalDate.parse(line.removePrefix("day=")) }.getOrNull()
                    if (date != null) days.getOrPut(date) { MutableDay() }
                }

                line.startsWith("event=") -> {
                    val (id, count) = splitIdAndCount(line.removePrefix("event=")) ?: return@forEach
                    val event = UsageEvent.entries.firstOrNull { it.id == id } ?: return@forEach
                    days.values.lastOrNull()?.events?.merge(event, count) { a, b -> a + b }
                }

                line.startsWith("feature=") -> {
                    val (id, count) = splitIdAndCount(line.removePrefix("feature=")) ?: return@forEach
                    val feature = FeatureId.entries.firstOrNull { it.id == id } ?: return@forEach
                    days.values.lastOrNull()?.features?.merge(feature, count) { a, b -> a + b }
                }

                line.startsWith("perf=") -> {
                    val parts = line.removePrefix("perf=").split(' ')
                    val operation = PerfOperation.entries.firstOrNull { it.id == parts.getOrNull(0) }
                        ?: return@forEach
                    val count = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val total = parts.getOrNull(2)?.toLongOrNull() ?: return@forEach
                    val max = parts.getOrNull(3)?.toLongOrNull() ?: total
                    days.values.lastOrNull()?.performance?.put(operation, PerfTotals(count, total, max))
                }

                line.startsWith("crash=") -> {
                    // The build is the last field, so it is taken from the end; everything before
                    // it is the exception and its frames. Parsing from the front instead would
                    // leave a leading space on the build when there are no frames at all.
                    val rest = line.removePrefix("crash=").trim()
                    val lastSpace = rest.lastIndexOf(' ')
                    if (lastSpace <= 0) return@forEach
                    val build = rest.substring(lastSpace + 1)
                    val head = rest.substring(0, lastSpace).trim()
                    val firstSpace = head.indexOf(' ')
                    val exceptionClass = if (firstSpace < 0) head else head.substring(0, firstSpace)
                    val frames = if (firstSpace < 0) {
                        emptyList()
                    } else {
                        head.substring(firstSpace + 1).split('|').filter { it.isNotBlank() }
                    }
                    days.values.lastOrNull()?.crashes?.add(
                        CrashRecord(exceptionClass, frames, build),
                    )
                }
            }
        }

        return DeviceProfile(model, android, appVersion, appBuild) to days.map { (date, mutable) ->
            UsageDay(
                day = date,
                events = mutable.events.toMap(),
                features = mutable.features.toMap(),
                performance = mutable.performance.toMap(),
                crashes = mutable.crashes.toList(),
            )
        }
    }

    private fun splitIdAndCount(text: String): Pair<String, Int>? {
        val space = text.lastIndexOf(' ')
        if (space <= 0) return null
        val count = text.substring(space + 1).toIntOrNull() ?: return null
        return text.substring(0, space) to count
    }

    /** A day being built up while reading. */
    private class MutableDay {
        val events = LinkedHashMap<UsageEvent, Int>()
        val features = LinkedHashMap<FeatureId, Int>()
        val performance = LinkedHashMap<PerfOperation, PerfTotals>()
        val crashes = mutableListOf<CrashRecord>()
    }
}
