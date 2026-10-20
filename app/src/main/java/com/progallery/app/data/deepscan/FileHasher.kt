package com.progallery.app.data.deepscan

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams a content URI through SHA-256. Files are read in small chunks so
 * hashing a multi-gigabyte video never loads it into memory.
 */
@Singleton
class FileHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

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
            null
        }
    }
}
