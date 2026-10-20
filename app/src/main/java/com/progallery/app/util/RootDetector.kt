package com.progallery.app.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only heuristic for whether the device appears rooted.
 *
 * This **never** requests root and never executes `su`. It only looks for the
 * usual `su` binaries and a test-keys build tag, which is a best-effort signal —
 * it can be wrong in both directions, and the UI says so.
 *
 * Root-based recovery is deliberately NOT implemented: reading raw block devices
 * bypasses Android's security model, cannot be verified here, and cannot
 * honestly promise recovery of TRIMmed or encrypted data.
 */
object RootDetector {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/bin/failsafe/su",
    )

    suspend fun isProbablyRooted(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            SU_PATHS.any { File(it).exists() } ||
                Build.TAGS?.contains("test-keys") == true
        }.getOrDefault(false)
    }

    /** A sentence for the diagnostics row. */
    fun describe(rooted: Boolean): String = if (rooted) {
        "A su binary was found, so this device may be rooted. Root recovery is not implemented in this app."
    } else {
        "No su binary found — this device does not appear to be rooted."
    }
}
