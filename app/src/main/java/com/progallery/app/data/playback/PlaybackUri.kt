package com.progallery.app.data.playback

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore

/**
 * Turns a raw file path into an address the player can actually open.
 *
 * **This is the "it says the file was moved, but the file is right there" bug.**
 *
 * The deep scan finds files the system's index does not list — another app's `Android/media` folder, a
 * folder nobody indexed — and stores them as `file:///storage/emulated/…`. Media3 is then handed that
 * address and, under scoped storage, **cannot open it**: it reports `ERROR_CODE_IO_FILE_NOT_FOUND`,
 * which the app turns into "it has been moved or deleted". The file has not moved. The address is one
 * the player is not allowed to use.
 *
 * The fix is not to guess at permissions. It is to **ask the system for an address it will honour**:
 * if MediaStore has a row for that path, its `content://` uri goes through the provider and opens
 * normally. If it does not, the `file://` address is kept — it is the only one there is, and it works
 * for the app's own storage.
 *
 * The decision is pure so it can be tested; only [resolve] touches the resolver.
 */
object PlaybackUri {

    /** Whether this address is a raw path that may need a provider address instead. */
    fun needsResolving(uri: Uri?): Boolean = uri?.scheme?.equals("file", ignoreCase = true) == true

    /**
     * A `content://` address for the same file, or the original when there is none.
     *
     * Never throws: a failed lookup means the caller keeps what it had, which is exactly the behaviour
     * before this existed.
     */
    fun resolve(resolver: ContentResolver, uri: Uri): Uri {
        if (!needsResolving(uri)) return uri
        val path = uri.path ?: return uri
        return runCatching {
            resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
            }
        }.getOrNull() ?: uri
    }
}
