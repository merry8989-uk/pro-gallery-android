package com.progallery.app.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads files from the platform [MediaStore].
 *
 * Everything MediaStore exposes to the app is indexed — images, videos, audio
 * and any other file the app is permitted to see — and classified by
 * [FileTypeRegistry]. Rows are never discarded for having an unfamiliar type:
 * they land in [MediaType.OTHER] and stay browsable.
 *
 * The query runs against the *merged* external view (`VOLUME_EXTERNAL`) so one
 * pass covers internal and removable storage, but each item's URI is built from
 * that row's own volume. Building it from the primary volume instead would
 * produce URIs that do not resolve for SD-card files.
 *
 * Platform limits worth knowing: from Android 11, MediaStore only returns media
 * (image/video/audio) plus files the app itself owns. Documents and archives
 * created by other apps are not visible here — those are reachable through
 * Deep Scan over a user-granted folder.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val collection: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.BUCKET_ID)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.Files.FileColumns.MEDIA_TYPE)
        add(MediaStore.Video.VideoColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.VOLUME_NAME)
        }
    }.toTypedArray()

    /** Loads files of the enabled [kinds] only. */
    suspend fun loadMedia(kinds: Set<MediaType> = DEFAULT_KINDS): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val results = ArrayList<MediaItem>()
            runCatching {
                context.contentResolver.query(
                    collection, projection, null, null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1
                    val volumeCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val displayName = cursor.stringOrNull(nameCol) ?: "Unnamed"
                        val mime = cursor.stringOrNull(mimeCol)
                        val mediaTypeHint = if (mediaTypeCol >= 0) cursor.intOrZero(mediaTypeCol) else 0
                        val resolvedKind = resolveKind(mime, displayName, mediaTypeHint)

                        if (resolvedKind !in kinds) continue

                        val volume = if (volumeCol >= 0) cursor.stringOrNull(volumeCol) else null
                        val contentUri = buildContentUri(id, resolvedKind, volume)
                        results += MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = displayName,
                            mimeType = mime ?: "",
                            sizeBytes = cursor.longOrZero(sizeCol),
                            dateModifiedSeconds = cursor.longOrZero(dateCol),
                            width = cursor.intOrZero(widthCol),
                            height = cursor.intOrZero(heightCol),
                            durationMs = if (durationCol >= 0) cursor.longOrZero(durationCol) else 0L,
                            bucketId = cursor.longOrZero(bucketCol),
                            bucketName = cursor.stringOrNull(bucketNameCol) ?: "Unknown",
                            relativePath = if (pathCol >= 0) cursor.stringOrNull(pathCol) else null,
                            mediaType = resolvedKind,
                            source = MediaSource.MEDIA_STORE,
                        )
                    }
                }
            }
            results
        }

    /**
     * MIME first, then the MediaStore media-type hint (useful when a provider
     * reports a generic MIME), then the extension.
     */
    private fun resolveKind(mime: String?, displayName: String, mediaTypeHint: Int): MediaType {
        val fromMime = FileTypeRegistry.kindFor(mime, displayName)
        if (fromMime != MediaType.OTHER) return fromMime

        val fromHint = when (mediaTypeHint) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.IMAGE
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaType.AUDIO
            else -> null
        }
        return fromHint ?: MediaType.OTHER
    }

    /**
     * Builds the URI for a row on its own volume. On API 29+ we use the row's
     * VOLUME_NAME (falling back to the primary volume if it is absent); below
     * that, `getContentUri(volume)` does not exist, so the single external
     * collection is correct.
     */
    private fun buildContentUri(id: Long, kind: MediaType, volumeName: String?): Uri {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volume = volumeName ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
            when (kind) {
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
                MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
                else -> MediaStore.Images.Media.getContentUri(volume)
            }
        } else {
            when (kind) {
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        return ContentUris.withAppendedId(base, id)
    }

    companion object {
        /** Every real file kind; MediaType.ALL is the "no filter" value, not a kind. */
        val DEFAULT_KINDS: Set<MediaType> = MediaType.entries
            .filter { it != MediaType.ALL }
            .toSet()
    }

    private fun Cursor.stringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.longOrZero(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun Cursor.intOrZero(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0
}
