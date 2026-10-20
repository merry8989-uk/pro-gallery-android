package com.progallery.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.CancellationException

/** Low-level content-URI helpers. */
object FileUtils {

    /** Reads the display name of a content URI, falling back to the last path segment. */
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: "unknown"
    }

    /** Reads the size of a content URI in bytes, or -1 when unknown. */
    fun size(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return -1
    }

    /**
     * Copies a content URI into [destination]. Returns the destination file, or
     * null if the copy failed. Streams in chunks so large files never load fully.
     */
    fun copyToFile(context: Context, uri: Uri, destination: File): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.parentFile?.mkdirs()
                destination.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
            }
            if (destination.exists()) destination else null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    private const val DEFAULT_BUFFER = 64 * 1024
}
