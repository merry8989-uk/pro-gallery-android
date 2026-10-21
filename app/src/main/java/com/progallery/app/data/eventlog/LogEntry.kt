package com.progallery.app.data.eventlog

import com.progallery.app.data.update.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The session's context: the things that are true of the whole session.
 *
 * It rides on every event rather than only on `session_start`, which the checklist asks for.
 * The reason is the export: the log becomes a **flat CSV imported into a row-oriented database**,
 * and a row whose device and version are unknown cannot be grouped, filtered or compared. The
 * `session_start` event stays the authoritative record of the session; this is the same context
 * repeated so every row stands on its own.
 */
data class LogContext(
    val appVersion: String,
    val os: String,
    val deviceModel: String,
    val releaseChannel: String,
    val network: String? = null,
    val platform: String = "android",
)

/**
 * One event, as the schema defines it.
 *
 * `details` is a map of string to string, and that is a privacy decision rather than a
 * convenience: a nested object can hold anything, while a flat map of strings can only hold what
 * a call site put there — and the call sites are a closed, tested set.
 */
data class LogEntry(
    val event: EventName,
    val timestampMillis: Long,
    val userCode: String,
    val sessionId: String,
    val attemptId: String? = null,
    val screen: String? = null,
    val details: Map<String, String> = emptyMap(),
    val level: LogLevel = event.level,
)

/**
 * The line format: one JSON object per event.
 *
 * Pure, and it never throws — a malformed line decodes to null rather than taking down the
 * screen that was trying to show it.
 */
object LogLine {

    /** Field order is fixed so two exports of the same data are byte-identical. */
    fun encode(entry: LogEntry, context: LogContext): String {
        val fields = linkedMapOf<String, String?>(
            "event_name" to entry.event.wire,
            "schema_version" to LOG_SCHEMA_VERSION,
            "timestamp" to isoTimestamp(entry.timestampMillis),
            "session_id" to entry.sessionId,
            "attempt_id" to entry.attemptId,
            "user_code" to entry.userCode,
            "platform" to context.platform,
            "app_version" to context.appVersion,
            "os" to context.os,
            "device_model" to context.deviceModel,
            "network_type" to context.network,
            "screen_or_route" to entry.screen,
            "log_level" to entry.level.wire,
            "release_channel" to context.releaseChannel,
        )
        // Event-specific properties come last, so the envelope is readable at the start of a line.
        entry.details.forEach { (key, value) -> fields[key] = value }

        return fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            if (value == null) "\"${escape(key)}\":null" else "\"${escape(key)}\":\"${escape(value)}\""
        }
    }

    fun decode(line: String): LogEntry? {
        val text = line.trim()
        if (!text.startsWith("{") || !text.endsWith("}")) return null
        val root = runCatching { Json.objectAt(Json.parse(text)) }.getOrNull() ?: return null

        val event = Json.stringAt(root, "event_name")?.let(EventName::ofWire) ?: return null
        val level = Json.stringAt(root, "log_level")
            ?.let { wire -> LogLevel.entries.firstOrNull { it.wire == wire } }
            ?: event.level

        // The envelope keys are removed so what is left is the event's own properties.
        val envelope = setOf(
            "event_name", "schema_version", "timestamp", "session_id", "attempt_id", "user_code",
            "platform", "app_version", "os", "device_model", "network_type", "screen_or_route",
            "log_level", "release_channel",
        )
        val details = root.entries
            .filter { (key, value) -> key !in envelope && value is String }
            .associate { (key, value) -> key to (value as String) }

        return LogEntry(
            event = event,
            timestampMillis = parseIsoTimestamp(Json.stringAt(root, "timestamp")),
            userCode = Json.stringAt(root, "user_code").orEmpty(),
            sessionId = Json.stringAt(root, "session_id").orEmpty(),
            attemptId = Json.stringAt(root, "attempt_id"),
            screen = Json.stringAt(root, "screen_or_route"),
            details = details,
            level = level,
        )
    }

    /** ISO 8601, UTC, whole seconds — the format the schema and the database both use. */
    fun isoTimestamp(millis: Long): String =
        Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString()

    private fun parseIsoTimestamp(value: String?): Long =
        value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private fun escape(value: String): String {
        val builder = StringBuilder(value.length + 8)
        value.forEach { character ->
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (character < ' ') builder.append("?") else builder.append(character)
            }
        }
        return builder.toString()
    }
}
