package com.progallery.app.data.eventlog

/** The shapes a log can be handed over in. */
enum class LogFormat(val label: String, val extension: String, val mimeType: String) {
    CSV("CSV", "csv", "text/csv"),
    JSON("JSON", "json", "application/json"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    TEXT("Text", "txt", "text/plain"),
}

/**
 * Renders entries for a person or a spreadsheet to read.
 *
 * Pure, so the formats are unit tested — and the CSV in particular, because quoting is where a
 * CSV silently corrupts: one detail value containing a comma shifts every column after it, and
 * the file still opens.
 */
object LogExport {

    /** The envelope columns, in the order the schema lists them. */
    private val ENVELOPE_COLUMNS = listOf(
        "timestamp", "user_code", "session_id", "attempt_id", "event_name", "log_level",
        "category", "screen_or_route", "platform", "app_version", "os", "device_model",
        "network_type", "release_channel", "schema_version",
    )

    fun render(entries: List<LogEntry>, context: LogContext, format: LogFormat): String =
        when (format) {
            LogFormat.JSON -> json(entries, context)
            LogFormat.CSV -> csv(entries, context)
            LogFormat.MARKDOWN -> markdown(entries)
            LogFormat.TEXT -> text(entries)
        }

    fun json(entries: List<LogEntry>, context: LogContext): String =
        entries.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]\n") {
            "  " + LogLine.encode(it, context)
        }

    /**
     * One row per event, with the event-specific properties as their own columns.
     *
     * The detail columns are the **union** of every key in the file, so two events with different
     * properties still line up in one sheet — which is the point of exporting to a CSV at all.
     */
    fun csv(entries: List<LogEntry>, context: LogContext): String {
        val detailColumns = entries.flatMap { it.details.keys }.toSortedSet().toList()
        val columns = ENVELOPE_COLUMNS + detailColumns

        val builder = StringBuilder()
        builder.append(columns.joinToString(",") { field(it) }).append('\n')

        entries.forEach { entry ->
            val values = ENVELOPE_COLUMNS.map { column -> envelopeValue(entry, context, column) } +
                detailColumns.map { column -> entry.details[column].orEmpty() }
            builder.append(values.joinToString(",") { field(it) }).append('\n')
        }
        return builder.toString()
    }

    fun markdown(entries: List<LogEntry>): String {
        val builder = StringBuilder()
        builder.append("# ProGallery diagnostics log\n\n")
        builder.append("${entries.size} events")
        val counts = entries.groupingBy { it.level }.eachCount()
        if (counts.isNotEmpty()) {
            builder.append(" — ").append(
                LogLevel.entries.joinToString(", ") { level ->
                    "${counts[level] ?: 0} ${level.wire}"
                },
            )
        }
        builder.append("\n\n| Time | Level | Event | Code | Screen | Details |\n")
        builder.append("| --- | --- | --- | --- | --- | --- |\n")
        entries.forEach { entry ->
            builder.append("| ").append(LogLine.isoTimestamp(entry.timestampMillis))
                .append(" | ").append(entry.level.wire)
                .append(" | ").append(entry.event.wire)
                .append(" | ").append(entry.userCode)
                .append(" | ").append(entry.screen.orEmpty())
                .append(" | ").append(cell(entry))
                .append(" |\n")
        }
        return builder.toString()
    }

    fun text(entries: List<LogEntry>): String =
        entries.joinToString("\n") { entry ->
            buildString {
                append(LogLine.isoTimestamp(entry.timestampMillis))
                append("  ").append(entry.level.wire.uppercase().padEnd(5))
                append("  ").append(entry.event.wire)
                if (entry.screen != null) append("  [").append(entry.screen).append(']')
                if (entry.attemptId != null) append("  ").append(entry.attemptId)
                append("  ").append(entry.userCode)
                if (entry.details.isNotEmpty()) {
                    append("  ")
                    append(entry.details.entries.joinToString(" ") { (key, value) -> "$key=$value" })
                }
            }
        } + if (entries.isEmpty()) "" else "\n"

    /**
     * A CSV field.
     *
     * Quoted whenever it contains a comma, a quote, or a newline, with inner quotes doubled —
     * the rule that stops one value from shifting every column after it.
     */
    private fun field(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun envelopeValue(entry: LogEntry, context: LogContext, column: String): String =
        when (column) {
            "timestamp" -> LogLine.isoTimestamp(entry.timestampMillis)
            "user_code" -> entry.userCode
            "session_id" -> entry.sessionId
            "attempt_id" -> entry.attemptId.orEmpty()
            "event_name" -> entry.event.wire
            "log_level" -> entry.level.wire
            "category" -> entry.event.category.wire
            "screen_or_route" -> entry.screen.orEmpty()
            "platform" -> context.platform
            "app_version" -> context.appVersion
            "os" -> context.os
            "device_model" -> context.deviceModel
            "network_type" -> context.network.orEmpty()
            "release_channel" -> context.releaseChannel
            "schema_version" -> LOG_SCHEMA_VERSION
            else -> ""
        }

    /** Markdown cells cannot carry a raw pipe or a newline. */
    private fun cell(entry: LogEntry): String =
        entry.details.entries.joinToString("; ") { (key, value) -> "$key=$value" }
            .replace("|", "\\|")
            .replace("\n", " ")
}
