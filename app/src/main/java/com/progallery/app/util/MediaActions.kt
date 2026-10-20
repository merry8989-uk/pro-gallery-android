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

    /** Intent that hands a file to whatever app can display it. */
    fun openIntent(item: MediaItem): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** MIME to use when handing a file to another app. */
    private fun mimeFor(uri: Uri, mimeType: String): String = mimeType.ifBlank {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        type ?: "*/*"
    }

    private fun editIntent(uri: Uri, mimeType: String, grantWrite: Boolean): Intent =
        Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeFor(uri, mimeType))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Editors that save in place need write access; harmless otherwise.
            if (grantWrite) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** True when an installed app can edit this file. */
    fun canAdvancedEdit(uri: Uri, mimeType: String): Boolean = runCatching {
        context.packageManager
            .queryIntentActivities(editIntent(uri, mimeType, grantWrite = false), 0)
            .isNotEmpty()
    }.getOrDefault(false)

    /**
     * Hands a photo or video to another app for **advanced** editing — Snapseed,
     * Photoshop Express, CapCut and the like — while the in-app editor stays for
     * the basic adjustments.
     *
     * Prefers ACTION_EDIT; when no editor is installed it falls back to opening
     * the file, and returns false when nothing on the device can handle it so the
     * caller can say so instead of showing an empty chooser.
     */
    fun launchAdvancedEdit(
        uri: Uri,
        mimeType: String,
        chooserTitle: String = "Advanced edit",
    ): Boolean {
        if (canAdvancedEdit(uri, mimeType)) {
            val launched = runCatching {
                context.startActivity(
                    Intent.createChooser(editIntent(uri, mimeType, grantWrite = true), chooserTitle)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
            if (launched) return true

            // Some URIs cannot be granted write access; retry read-only.
            return runCatching {
                context.startActivity(
                    Intent.createChooser(editIntent(uri, mimeType, grantWrite = false), chooserTitle)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
        return launchOpen(
            MediaItem(
                id = 0,
                uri = uri,
                displayName = "",
                mimeType = mimeFor(uri, mimeType),
                sizeBytes = 0,
                dateModifiedSeconds = 0,
                width = 0,
                height = 0,
                durationMs = 0,
                bucketId = 0,
                bucketName = "",
                relativePath = null,
            ),
            chooserTitle,
        )
    }

    /** True when at least one installed app can view this file. */
    fun canOpen(item: MediaItem): Boolean = runCatching {
        context.packageManager.queryIntentActivities(openIntent(item), 0).isNotEmpty()
    }.getOrDefault(false)

    /**
     * Hands the file to another app through a chooser, so the user picks among
     * the apps they already have (their PDF reader, office suite, video player…).
     *
     * Returns false when nothing on the device can open it — the caller then says
     * so instead of showing an empty chooser.
     */
    fun launchOpen(item: MediaItem, chooserTitle: String = "Open with"): Boolean {
        if (!canOpen(item)) return false
        return runCatching {
            context.startActivity(
                Intent.createChooser(openIntent(item), chooserTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Returns a delete-consent [PendingIntent] on Android 11+, or null when the
     * caller should delete directly (older versions / app-owned files).
     */
    /**
     * Consent request for deleting MediaStore entries (Android 11+).
     *
     * `createDeleteRequest` rejects a set that spans different collections or
     * volumes (internal + SD card together, or mixed media types) and throws.
     * That used to propagate out of the caller and make deletion look like it
     * did nothing, so a failure here returns null and the caller falls back to
     * deleting items individually with a clear result.
     */
    fun deleteRequest(items: List<MediaItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (items.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
        }.getOrNull()
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
     * Copies a file into another folder by re-publishing its bytes through
     * MediaStore. Needs Android 10+ for RELATIVE_PATH; for documents created by
     * other apps it additionally needs All-files access.
     *
     * A failed copy deletes the pending row it created rather than leaving an
     * empty entry behind.
     */
    fun copyTo(item: MediaItem, targetRelativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val normalized = if (targetRelativePath.endsWith("/")) {
                targetRelativePath
            } else {
                "$targetRelativePath/"
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    item.mimeType.ifBlank { "application/octet-stream" },
                )
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalized)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collectionFor(item), values)
            if (uri == null) {
                false
            } else {
                val copied = context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false

                if (copied) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                } else {
                    // Don't leave a half-written, pending row behind.
                    context.contentResolver.delete(uri, null, null)
                }
                copied
            }
        }.getOrDefault(false)
    }

    /** The MediaStore collection a file of this kind belongs in. */
    private fun collectionFor(item: MediaItem): Uri = when {
        item.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        item.isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        item.isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    /**
     * Write consent for renaming/moving. Shares the same platform flow as the
     * delete request: the user approves, then the change is applied.
     */
    fun writeRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (uris.isEmpty()) return null
        // Same constraint as the delete request: a set spanning volumes or
        // collections throws. Also throws for non-MediaStore URIs, such as a file
        // picked through the Storage Access Framework. Returning null lets the
        // caller report a clear failure instead of the exception escaping.
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        }.getOrNull()
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
