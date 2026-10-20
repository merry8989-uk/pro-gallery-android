package com.progallery.app.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.progallery.app.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real, platform-correct media operations: sharing through the Sharesheet,
 * deleting via the recoverable-deletion consent flow on Android 11+, and
 * renaming. Uses content URIs only — never raw filesystem paths.
 */
@Singleton
class MediaActions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Builds a Sharesheet intent for one or many items. */
    fun shareIntent(items: List<MediaItem>): Intent {
        val uris = ArrayList(items.map { it.uri })
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /**
     * The Sharesheet chooser, fully flagged so it can be started from any
     * context. MediaActions holds an *application* context, and starting an
     * activity from one without FLAG_ACTIVITY_NEW_TASK throws
     * AndroidRuntimeException — which is what crashed sharing.
     */
    fun shareChooser(items: List<MediaItem>): Intent =
        Intent.createChooser(shareIntent(items), "Share via").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun launchShare(items: List<MediaItem>) {
        if (items.isEmpty()) return
        context.startActivity(shareChooser(items))
    }

    /**
     * Returns a delete-consent [PendingIntent] on Android 11+, or null when the
     * caller should delete directly (older versions / app-owned files).
     */
    fun deleteRequest(items: List<MediaItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = items.map { it.uri }
        return MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    /** Direct delete, used on Android 10 and below. Returns true if all succeeded. */
    fun deleteDirect(items: List<MediaItem>): Boolean {
        var ok = true
        items.forEach { item ->
            val result = runCatching {
                context.contentResolver.delete(item.uri, null, null)
            }.getOrDefault(0)
            if (result == 0) ok = false
        }
        return ok
    }

    /** Returns a write-consent PendingIntent for renaming on Android 11+. */
    fun renameRequest(uri: Uri): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
    }

    /** Applies a new display name to a MediaStore item. */
    fun applyRename(uri: Uri, newName: String): Boolean = runCatching {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /**
     * Write consent for renaming/moving. Shares the same platform flow as the
     * delete request: the user approves, then the change is applied.
     */
    fun writeRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris)
    }

    /**
     * Moves a MediaStore item into another folder by changing its relative path.
     * Supported from Android 11 (API 30); earlier versions cannot update
     * RELATIVE_PATH and must copy instead, which this method deliberately does
     * not fake.
     */
    fun applyMove(item: MediaItem, targetRelativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val normalized = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalized)
            }
            context.contentResolver.update(item.uri, values, null, null) > 0
        }.getOrDefault(false)
    }
}
