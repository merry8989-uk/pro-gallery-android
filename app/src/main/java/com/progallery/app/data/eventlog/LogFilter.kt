package com.progallery.app.data.eventlog

/**
 * What the log is narrowed to.
 *
 * All four criteria are optional and combine with AND, because that is how the questions are
 * actually asked: "the errors from this device" is a level and a code, not one or the other.
 */
data class LogFilter(
    val level: LogLevel? = null,
    val event: EventName? = null,
    val userCode: String? = null,
    val text: String? = null,
) {
    val isActive: Boolean
        get() = level != null || event != null || !userCode.isNullOrBlank() || !text.isNullOrBlank()

    fun cleared(): LogFilter = LogFilter()
}

/**
 * Applies a filter to entries.
 *
 * Pure, so every combination is unit tested — and so the screen and any future export cannot
 * disagree about what "filtered" means.
 */
object LogFilters {

    fun matches(entry: LogEntry, filter: LogFilter): Boolean {
        filter.level?.let { if (entry.level != it) return false }
        filter.event?.let { if (entry.event != it) return false }

        // The user code is matched on a *prefix* of what was typed, not exactly: a code is
        // something a person copies by hand, and demanding all nine characters before showing
        // anything makes the filter feel broken. It is still case-insensitive.
        filter.userCode?.takeIf { it.isNotBlank() }?.let { needle ->
            if (!entry.userCode.contains(needle.trim(), ignoreCase = true)) return false
        }

        // Free text searches the whole event, envelope included, so a value that is not a
        // first-class column can still be found without adding one.
        filter.text?.takeIf { it.isNotBlank() }?.let { needle ->
            val haystack = buildString {
                append(entry.event.wire).append(' ')
                append(entry.level.wire).append(' ')
                append(entry.screen.orEmpty()).append(' ')
                append(entry.userCode).append(' ')
                entry.details.forEach { (key, value) -> append(key).append('=').append(value).append(' ') }
            }
            if (!haystack.contains(needle.trim(), ignoreCase = true)) return false
        }

        return true
    }

    fun apply(entries: List<LogEntry>, filter: LogFilter): List<LogEntry> =
        if (!filter.isActive) entries else entries.filter { matches(it, filter) }

    /** How many entries each level would leave, for showing a count beside each option. */
    fun countsByLevel(entries: List<LogEntry>): Map<LogLevel, Int> =
        LogLevel.entries.associateWith { level -> entries.count { it.level == level } }
}
