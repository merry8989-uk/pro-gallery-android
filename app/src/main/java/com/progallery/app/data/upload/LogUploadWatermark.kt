package com.progallery.app.data.upload

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How far the log has been sent.
 *
 * A file rather than a setting, because it is bookkeeping and not a preference — and because a
 * user who clears their settings should not thereby resend a year of events.
 */
@Singleton
class LogUploadWatermark @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    private fun file(): File = File(context.filesDir, "diagnostics/upload_watermark.txt")

    suspend fun read(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file().takeIf { it.exists() }?.readText()?.trim()?.toLong() }.getOrNull() ?: 0L
        }
    }

    suspend fun write(millis: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                file().parentFile?.mkdirs()
                file().writeText(millis.toString())
            }
        }
    }

    /** Forgets the watermark, so the whole log is considered unsent. Offered for a fresh start. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { file().delete() } }
    }
}
