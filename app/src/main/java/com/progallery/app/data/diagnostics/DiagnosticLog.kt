package com.progallery.app.data.diagnostics

import android.content.Context
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The local diagnostics log.
 *
 * It exists so a problem can be described without guessing. Three rules shape it:
 *
 *  * **Nothing is written unless the user asked for it.** With "Write diagnostics" off
 *    [record] returns immediately, so no file is created at all.
 *  * **Paths are redacted unless the user allows them.** A log is a file the user may
 *    share, and a path says where their files are. With paths off, a name is reduced to
 *    its extension — still useful for "what kind of file failed", useless as a
 *    fingerprint of someone's library.
 *  * **It is bounded.** Entries older than the configured number of days are dropped,
 *    and the file is trimmed if it grows past [MAX_BYTES], so a log can never quietly
 *    become the largest thing on the device.
 */
@Singleton
class DiagnosticLog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private companion object {
        const val FILE_NAME = "diagnostics.log"
        const val MAX_BYTES = 256 * 1024
        fun timestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
    }

    private val lock = Mutex()

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** True when a log is actually being kept. */
    suspend fun isEnabled(): Boolean = settingsRepository.current().saveDiagnosticLogs

    /**
     * Appends one event.
     *
     * [path] is redacted unless the user allowed paths, so a caller never has to
     * remember to do it.
     */
    suspend fun record(event: String, path: String? = null) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.current()
        if (!settings.saveDiagnosticLogs) return@withContext

        val safePath = path?.let { LogRedaction.describe(it, settings.includePathsInLogs) }
        val line = buildString {
            append(timestamp(System.currentTimeMillis()))
            append(" | ")
            append(event)
            if (safePath != null) {
                append(" | ")
                append(safePath)
            }
        }

        lock.withLock {
            runCatchingCancellable {
                file.appendText(line + "\n")
                prune(settings.autoClearLogsAfterDays)
            }
        }
    }

    /** The log as it stands, oldest first. */
    suspend fun lines(): List<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatchingCancellable { file.takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty()
        }
    }

    /** The whole log as text, for sharing. */
    suspend fun read(): String = lines().joinToString("\n")

    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { runCatchingCancellable { file.delete() } }
    }

    /** Drops entries older than [days], then trims the file to its size bound. */
    private fun prune(days: Int) {
        if (!file.exists()) return

        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val kept = file.readLines().filter { line -> timestampOf(line)?.let { it >= cutoff } ?: true }

        // Size bound: keep the newest lines that fit.
        val trimmed = ArrayList<String>(kept.size)
        var bytes = 0
        kept.asReversed().forEach { line ->
            val size = line.length + 1
            if (bytes + size <= MAX_BYTES) {
                trimmed += line
                bytes += size
            }
        }
        file.writeText(trimmed.asReversed().joinToString("\n", postfix = if (trimmed.isEmpty()) "" else "\n"))
    }

    private fun timestampOf(line: String): Long? {
        val first = line.substringBefore(" | ")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(first)?.time
        }.getOrNull()
    }

}
