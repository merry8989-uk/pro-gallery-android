package com.progallery.app.data.deepscan

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Streams a content URI through SHA-256. Files are read in small chunks so
 * hashing a multi-gigabyte video never loads it into memory.
 */
@Singleton
class FileHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        /** 64 KB from each end is enough to tell two files apart in practice. */
        const val DEFAULT_SAMPLE_BYTES = 64 * 1024
    }

    /**
     * A cheap pre-filter hash: the file's length plus its first and last
     * [sampleBytes].
     *
     * It exists only to avoid full-hashing files that cannot be duplicates. Two
     * files that agree here are still confirmed with [sha256], so this can never
     * produce a wrong duplicate — only a cheaper one to find.
     */
    suspend fun sampledHash(uri: Uri, sampleBytes: Int = DEFAULT_SAMPLE_BYTES): String? =
        withContext(Dispatchers.IO) {
            try {
                val length = assetLength(uri)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    // The length is part of the identity, so files of different
                    // sizes can never share a sample hash.
                    digest.update(length.toString().toByteArray())

                    val buffer = ByteArray(sampleBytes)
                    digest.update(buffer, 0, readUpTo(input, buffer))

                    if (length > sampleBytes * 2L) {
                        skipUpTo(input, length - sampleBytes)
                        digest.update(buffer, 0, readUpTo(input, buffer))
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            }
        }

    private fun assetLength(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: -1L

    /** Reads as much as fits, returning how many bytes were read. */
    private fun readUpTo(input: java.io.InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    /** Skips forward, falling back to reading and discarding when skip falls short. */
    private fun skipUpTo(input: java.io.InputStream, target: Long) {
        var remaining = target
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                return
            } else {
                remaining--
            }
        }
    }

    /** Returns the lowercase hex SHA-256 of [uri], or null if it cannot be read. */
    suspend fun sha256(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }
}
