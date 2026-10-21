package com.progallery.app.data.eventlog

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
 * The install's code, made once and kept.
 *
 * Stored as a one-line file in the app's own directory rather than in the settings export: the
 * export is a thing a user shares, and a code that identifies their device has no business
 * travelling in it.
 */
@Singleton
class UserCodeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    private fun file(): File = File(context.filesDir, "diagnostics/user_code.txt")

    /** The code for this install, creating it the first time it is asked for. */
    suspend fun code(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = runCatching { file().takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
            if (existing != null && UserCode.isValid(existing)) return@withLock UserCode.normalise(existing)

            val fresh = UserCode.generate()
            runCatching {
                file().parentFile?.mkdirs()
                file().writeText(fresh)
            }
            fresh
        }
    }

    /**
     * Forgets the code, so the next one is new.
     *
     * Offered because a code that cannot be reset is not a pseudonym a person controls.
     */
    suspend fun reset(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file().delete() }
            val fresh = UserCode.generate()
            runCatching {
                file().parentFile?.mkdirs()
                file().writeText(fresh)
            }
            fresh
        }
    }
}
