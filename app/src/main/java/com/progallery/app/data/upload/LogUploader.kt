package com.progallery.app.data.upload

import android.util.Log
import com.progallery.app.BuildConfig
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.eventlog.LogEntry
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.data.update.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** What one attempt at sending achieved. */
data class UploadResult(
    val sent: Int,
    val batches: Int,
    val skipped: Boolean = false,
    val reason: String? = null,
)

/**
 * Sends the log to the ingest function.
 *
 * Four rules, each one a promise made somewhere else:
 *
 * - **Nothing is sent unless the user turned it on.** The check is the first line, not a caller's
 *   responsibility, because a consent gate that lives at the call site is a consent gate with
 *   holes.
 * - **Nothing is sent that the app cannot show.** The payload is built from the same entries the
 *   Diagnostics screen renders, so what goes up is what the user can read before it goes.
 * - **The watermark only moves after the server has accepted a batch.** A crash mid-upload must
 *   not lose events.
 * - **A failure is quiet.** No retry storm, no dialog: it will be tried again later, and the log
 *   itself is unaffected either way.
 */
@Singleton
class LogUploader @Inject constructor(
    private val store: EventLogStore,
    private val watermark: LogUploadWatermark,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Whether sending is configured at all.
     *
     * The endpoint and the publishable key come from the build, never from the source — a key
     * committed to a repository is a key that has to be rotated.
     */
    fun isConfigured(): Boolean =
        BuildConfig.LOG_INGEST_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    suspend fun uploadPending(): UploadResult {
        if (!settingsRepository.current().uploadDiagnosticsEnabled) {
            return UploadResult(0, 0, skipped = true, reason = "not enabled")
        }
        if (!isConfigured()) {
            return UploadResult(0, 0, skipped = true, reason = "not configured")
        }

        val context = store.context()
        val mark = watermark.read()
        val pending = LogUploadBatch.pending(store.read(), mark)
        if (pending.isEmpty()) return UploadResult(0, 0)

        var sent = 0
        var batches = 0
        for (batch in LogUploadBatch.batches(pending)) {
            // One line per step of the upload, at the trace level: this is what "detailed
            // execution" means, and it is why the level exists separately from debug.
            store.log(
                EventName.TRACE_MARKER,
                details = mapOf("step" to "upload_batch", "size" to batch.size.toString()),
            )
            val accepted = post(LogUploadPayload.batch(batch, context))
            if (accepted == null) {
                // Stop at the first failure rather than hammering. Whatever is left is still
                // pending, because the watermark has not moved past it.
                break
            }
            sent += batch.size
            batches += 1
            watermark.write(LogUploadBatch.advancedWatermark(mark, batch))
        }

        // The attempt itself is worth recording locally, so the user can see that sending is
        // happening. It is written after the upload so it is not itself sent in the same pass.
        store.record(
            LogEntry(
                event = EventName.CONSENT_CHANGED,
                timestampMillis = System.currentTimeMillis(),
                userCode = store.code(),
                sessionId = store.session(),
                screen = "Diagnostics",
                details = mapOf("upload_batches" to batches.toString(), "upload_events" to sent.toString()),
            ),
        )

        return UploadResult(sent, batches)
    }

    /** Returns how many the server accepted, or null when the request failed. */
    private fun post(body: Map<String, Any>): Int? = runCatching {
        val connection = URL(BuildConfig.LOG_INGEST_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}",
            )

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(Json.write(body))
            }

            if (connection.responseCode !in 200..299) return@runCatching null
            // The function answers with the counts; the batch size is what matters here.
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = runCatching { Json.objectAt(Json.parse(text)) }.getOrNull()
            val accepted = parsed?.let { Json.numberAt(it, "accepted") }
            accepted?.toInt()
        } finally {
            runCatching { connection.disconnect() }
        }
    }.onFailure { Log.d("LogUploader", "upload attempt failed: ${it::class.simpleName}") }
        .getOrNull()
}
