package com.progallery.app.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.util.MediaFormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads media metadata from the platform [MediaStore]. This is the fast,
 * index-backed path; it respects scoped storage and only returns items the
 * app has been granted access to.
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
        add(MediaStore.Video.VideoColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
    }.toTypedArray()

    /** Loads every image and/or video currently visible to the app. */
    suspend fun loadMedia(type: MediaType = MediaType.ALL): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val selection = when (type) {
                MediaType.ALL ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
                MediaType.IMAGE ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                MediaType.VIDEO ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            }
            val args: Array<String> = when (type) {
                MediaType.ALL -> arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                )
                MediaType.IMAGE ->
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                MediaType.VIDEO ->
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }

            val results = ArrayList<MediaItem>()
            runCatching {
                context.contentResolver.query(
                    collection, projection, selection, args,
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
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val mime = cursor.stringOrNull(mimeCol)
                        if (!MediaFormatUtils.isSupported(mime)) continue
                        val id = cursor.getLong(idCol)
                        val isVideo = MediaFormatUtils.isVideo(mime)
                        val contentUri = ContentUris.withAppendedId(
                            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        results += MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = cursor.stringOrNull(nameCol) ?: "Unnamed",
                            mimeType = mime ?: "",
                            sizeBytes = cursor.longOrZero(sizeCol),
                            dateModifiedSeconds = cursor.longOrZero(dateCol),
                            width = cursor.intOrZero(widthCol),
                            height = cursor.intOrZero(heightCol),
                            durationMs = if (durationCol >= 0) cursor.longOrZero(durationCol) else 0L,
                            bucketId = cursor.longOrZero(bucketCol),
                            bucketName = cursor.stringOrNull(bucketNameCol) ?: "Unknown",
                            relativePath = if (pathCol >= 0) cursor.stringOrNull(pathCol) else null,
                            mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                            source = MediaSource.MEDIA_STORE,
                        )
                    }
                }
            }
            results
        }

    private fun Cursor.stringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.longOrZero(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun Cursor.intOrZero(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0
}
