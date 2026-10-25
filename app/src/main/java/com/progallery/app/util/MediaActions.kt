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
import com.progallery.app.util.runCatchingCancellable
import com.progallery.app.data.transfer.CopyProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
        val uris = ArrayList(items.map { shareableUri(it.uri) })
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
     * Other apps cannot receive a `file://` URI (it throws
     * FileUriExposedException), so convert one to a FileProvider content URI.
     * Files discovered under `Android/` are File-based, which is why this exists.
     */
    private fun shareableUri(uri: Uri): Uri =
        if (uri.scheme == "file") {
            val path = uri.path
            if (path.isNullOrEmpty()) {
                uri
            } else {
                runCatching {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        java.io.File(path),
                    )
                }.getOrDefault(uri)
            }
        } else {
            uri
        }

    /** MIME to use when handing a file to another app. */
    private fun mimeFor(uri: Uri, mimeType: String): String = mimeType.ifBlank {
        runCatchingCancellable { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
    }

    private fun viewIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareableUri(uri), mimeFor(uri, mimeType))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Intent that hands a file to whatever app can display it. */
    fun openIntent(item: MediaItem): Intent = viewIntent(item.uri, item.mimeType)

    private fun editIntent(uri: Uri, mimeType: String, grantWrite: Boolean): Intent =
        Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(shareableUri(uri), mimeFor(uri, mimeType))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Editors that save in place need write access; harmless otherwise.
            if (grantWrite) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** True when at least one installed app can handle this intent. */
    private fun canHandle(intent: Intent): Boolean = runCatchingCancellable {
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }.getOrDefault(false)

    /** Shows a chooser. Returns false only if the chooser itself could not start. */
    private fun launchChooser(intent: Intent, title: String): Boolean = runCatchingCancellable {
        context.startActivity(
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** True when at least one installed app can view this file. */
    fun canOpen(item: MediaItem): Boolean = canHandle(openIntent(item))

    /**
     * Hands the file to another app through a chooser, so the user picks among
     * the apps they already have (their PDF reader, office suite, video player…).
     *
     * Returns false when nothing on the device can open it — the caller then says
     * so instead of showing an empty chooser.
     */
    fun launchOpen(item: MediaItem, chooserTitle: String = "Open with"): Boolean {
        // Deliberately NOT gated on canHandle(). Package visibility filtering can
        // report "nothing can handle this" while five apps are installed, which
        // made the app refuse to show the chooser at all. The system chooser is
        // the authority: it lists the real apps, and says so itself when there
        // genuinely are none.
        return launchChooser(openIntent(item), chooserTitle)
    }

    /** True when an installed app can edit this file. */
    fun canAdvancedEdit(uri: Uri, mimeType: String): Boolean =
        canHandle(editIntent(uri, mimeType, grantWrite = false))

    /**
     * Hands a photo or video to another app for **advanced** editing — Snapseed,
     * Photoshop Express, CapCut and the like — while the in-app editor stays for
     * the basic adjustments.
     *
     * Prefers ACTION_EDIT. When no editor is installed it falls back to simply
     * opening the file, and returns false when nothing on the device can handle
     * it, so the caller can say so instead of showing an empty chooser.
     */
    fun launchAdvancedEdit(
        uri: Uri,
        mimeType: String,
        chooserTitle: String = "Advanced edit",
    ): Boolean {
        if (canAdvancedEdit(uri, mimeType)) {
            // Try with a write grant first; some URIs cannot be granted write
            // access, so retry read-only before giving up.
            if (launchChooser(editIntent(uri, mimeType, grantWrite = true), chooserTitle)) return true
            return launchChooser(editIntent(uri, mimeType, grantWrite = false), chooserTitle)
        }

        // No editor detected (or visibility hid it): still offer the chooser, so
        // the user sees the apps that really exist.
        return launchChooser(viewIntent(uri, mimeType), chooserTitle)
    }

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
        return runCatchingCancellable {
            MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
        }.getOrNull()
    }

    /** Direct delete, used on Android 10 and below. Returns true if all succeeded. */
    fun deleteDirect(items: List<MediaItem>): Boolean {
        var ok = true
        items.forEach { item ->
            val result = runCatchingCancellable {
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
    fun applyRename(uri: Uri, newName: String): Boolean = runCatchingCancellable {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /**
     * Copies a file into another folder by re-publishing its bytes through MediaStore.
     *
     * Needs Android 10+ for RELATIVE_PATH; for documents created by other apps it additionally needs
     * All-files access.
     *
     * **Suspending, cancellable, and it says how far it has got.** It used to be a blocking
     * `input.copyTo(output)` with no feedback at all, which meant a large video looked like a frozen
     * app — and there was no way to stop it. A suspend function is cancellable, so stopping it is
     * the caller cancelling the job.
     *
     * **A cancelled or failed copy leaves nothing behind.** The row is inserted as pending, which
     * means nothing else can see it, and it is deleted on every exit that is not success — including
     * cancellation, because `finally` runs for that too. A half-written file that looks like a real
     * one is worse than a refused copy.
     */
    suspend fun copyTo(
        item: MediaItem,
        targetRelativePath: String,
        onProgress: (CopyProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

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

        val uri = runCatchingCancellable { context.contentResolver.insert(collectionFor(item), values) }
            .getOrNull() ?: return@withContext false

        var finished = false
        try {
            onProgress(CopyProgress.Starting)
            val total = item.sizeBytes

            val copied = runCatchingCancellable {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        var written = 0L
                        while (true) {
                            // The suspension point that makes this cancellable: a cancel between
                            // reads is noticed here rather than after the whole file.
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(CopyProgress.of(written, total))
                        }
                        output.flush()
                        written
                    }
                }
            }.getOrNull()

            if (copied != null) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                runCatchingCancellable { context.contentResolver.update(uri, values, null, null) }
                finished = true
                true
            } else {
                false
            }
        } finally {
            // Failure, cancellation, anything: the pending row goes. `finally` is the only place
            // that runs for all three.
            if (!finished) {
                runCatchingCancellable { context.contentResolver.delete(uri, null, null) }
            }
        }
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
        return runCatchingCancellable {
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
        return runCatchingCancellable {
            val normalized = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalized)
            }
            context.contentResolver.update(item.uri, values, null, null) > 0
        }.getOrDefault(false)
    }
}
