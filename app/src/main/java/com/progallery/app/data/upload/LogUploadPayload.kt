package com.progallery.app.data.upload

import com.progallery.app.data.eventlog.LogContext
import com.progallery.app.data.eventlog.LogEntry
import com.progallery.app.data.eventlog.LogLine

/**
 * An event as it goes over the wire.
 *
 * **The app never sends `user_id_hash`.** The server derives it, because a hash the client can
 * compute is a hash whose salt the client holds, and a salt the client holds protects nothing.
 * The device code goes up; the server turns it into a hash with a salt that never leaves it.
 *
 * Pure, so the exact shape that leaves the device is pinned in a test rather than described.
 */
object LogUploadPayload {

    /**
     * The envelope, exactly as the standard names it — and nothing else.
     *
     * Built as a map of the schema's field names rather than by serialising the log line, because
     * the line carries display conveniences and this must carry only what was agreed. The field
     * list is the contract; a test asserts it is exactly this.
     */
    fun of(entry: LogEntry, context: LogContext): Map<String, Any> = buildMap {
        put("event_name", entry.event.wire)
        put("schema_version", com.progallery.app.data.eventlog.LOG_SCHEMA_VERSION)
        put("timestamp", LogLine.isoTimestamp(entry.timestampMillis))
        put("session_id", entry.sessionId)
        entry.attemptId?.let { put("attempt_id", it) }
        put("user_code", entry.userCode)
        put("platform", context.platform)
        put("app_version", context.appVersion)
        put("os", context.os)
        put("device_model", context.deviceModel)
        context.network?.let { put("network_type", it) }
        entry.screen?.let { put("screen_or_route", it) }
        put("log_level", entry.level.wire)
        put("category", entry.event.category.wire)
        put("release_channel", context.releaseChannel)
        if (entry.details.isNotEmpty()) put("details", entry.details)
    }

    /** The body of one request. */
    fun batch(entries: List<LogEntry>, context: LogContext): Map<String, Any> =
        mapOf("events" to entries.map { of(it, context) })
}
