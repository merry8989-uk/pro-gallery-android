package com.progallery.app.data.eventlog

import android.content.Context
import com.progallery.app.data.permissions.AccessId
import com.progallery.app.data.permissions.AccessState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An access fix that has been asked for and not yet answered. */
data class PendingFix(
    val attemptId: String,
    val accessId: AccessId,
    val stateBefore: AccessState,
    /** What the user tapped, so the result can name it rather than leaving it to be guessed. */
    val actionTaken: String,
)

/**
 * The open attempt, kept on disk.
 *
 * It has to survive the process: the user leaves the app to change a permission in Settings, and
 * Android is free to kill the app while they are there. An attempt held only in memory would be
 * lost in exactly the case this exists for — which is how a log ends up with requests and no
 * results.
 */
@Singleton
class PendingFixStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    private fun file(): File = File(context.filesDir, "diagnostics/pending_fix.txt")

    suspend fun put(fix: PendingFix) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                file().parentFile?.mkdirs()
                file().writeText(
                    listOf(fix.attemptId, fix.accessId.name, fix.stateBefore.wire(), fix.actionTaken)
                        .joinToString("|"),
                )
            }
        }
    }

    /** Reads and clears it: an attempt is answered once. */
    suspend fun take(): PendingFix? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val line = runCatching { file().takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
            runCatching { file().delete() }
            if (line.isNullOrBlank()) return@withLock null
            val parts = line.split('|')
            if (parts.size != 4) return@withLock null
            val accessId = runCatching { AccessId.valueOf(parts[1]) }.getOrNull() ?: return@withLock null
            val before = accessStateOfWire(parts[2]) ?: return@withLock null
            PendingFix(parts[0], accessId, before, parts[3])
        }
    }
}
