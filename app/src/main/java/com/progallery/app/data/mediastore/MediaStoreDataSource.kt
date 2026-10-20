package com.progallery.app.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 *
 * Three ways in, all sharing one row reader so they cannot disagree about what a
 * file is: [loadMedia] (everything at once), [loadMediaBatches] (incremental, for
 * the index cache) and [page] (a window, for Paging).
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

    /** One window of the index. */
    data class MediaPage(
        val items: List<MediaItem>,
        /** How many cursor rows were examined to produce [items]. */
        val rowsRead: Int,
        /** True when the cursor reached the end. */
        val exhausted: Boolean,
    )

    /** Loads files of the enabled [kinds] only, in one go. */
    suspend fun loadMedia(kinds: Set<MediaType> = DEFAULT_KINDS): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val all = ArrayList<MediaItem>()
            loadMediaBatches(kinds, DEFAULT_BATCH_SIZE).collect { all += it }
            all
        }

    /**
     * Loads the index in batches, emitting after each one.
     *
     * A library with tens of thousands of files used to be built entirely before
     * anything appeared. Emitting progressively lets the grid paint after the first
     * batch while the rest arrives, and keeps peak allocations smaller.
     */
    fun loadMediaBatches(
        kinds: Set<MediaType> = DEFAULT_KINDS,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): Flow<List<MediaItem>> = flow {
        val effectiveBatch = batchSize.coerceAtLeast(1)
        val batch = ArrayList<MediaItem>(effectiveBatch)
        runCatchingCancellable {
            openCursor(selection = null, args = null, orderBy = MediaQuery.orderBy(SortOrder.DATE_NEWEST))
                ?.use { cursor ->
                    val reader = RowReader(cursor)
                    while (cursor.moveToNext()) {
                        val item = reader.read(cursor) ?: continue
                        if (item.mediaType !in kinds) continue
                        batch += item
                        if (batch.size >= effectiveBatch) {
                            emit(batch.toList())
                            batch.clear()
                        }
                    }
                }
        }
        if (batch.isNotEmpty()) emit(batch.toList())
    }.flowOn(Dispatchers.IO)

    /**
     * Reads a window of the index starting at row [rowOffset].
     *
     * Rows are examined in SQL order and classified exactly as the other two paths
     * do, so a paged grid and the full list can never disagree about what a file is.
     * The window is a window on **rows**, not on matches: a page can contain fewer
     * items than rows, and the caller keeps asking until it has filled a page.
     *
     * From API 30 the platform applies LIMIT/OFFSET itself, so the database does the
     * skipping. Below that the cursor is walked to the offset — acceptable, because
     * those versions also have no platform windowing at all.
     */
    suspend fun page(
        kinds: Set<MediaType>,
        sort: SortOrder,
        query: String,
        rowOffset: Int,
        rowLimit: Int,
    ): MediaPage = withContext(Dispatchers.IO) {
        var items: List<MediaItem> = emptyList()
        var rowsRead = 0
        var exhausted = false

        runCatchingCancellable {
            val (selection, args) = MediaQuery.selectionFor(query)
            val orderBy = MediaQuery.orderBy(sort)
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.contentResolver.query(
                    collection,
                    projection,
                    windowArgs(selection, args, orderBy, rowOffset, rowLimit),
                    null,
                )
            } else {
                openCursor(selection, args, orderBy)
            } ?: return@runCatchingCancellable

            cursor.use { c ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    // No platform window: walk to the offset.
                    exhausted = !c.moveToPosition(rowOffset)
                }
                if (!exhausted) {
                    val reader = RowReader(c)
                    val batch = ArrayList<MediaItem>(rowLimit)
                    while (rowsRead < rowLimit && c.moveToNext()) {
                        rowsRead++
                        val item = reader.read(c) ?: continue
                        if (item.mediaType in kinds) batch += item
                    }
                    if (rowsRead < rowLimit) exhausted = true
                    items = batch
                }
            }
        }
        MediaPage(items = items, rowsRead = rowsRead, exhausted = exhausted)
    }

    private fun windowArgs(
        selection: String?,
        args: Array<String>?,
        orderBy: String,
        offset: Int,
        limit: Int,
    ): Bundle = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, orderBy)
        if (selection != null) {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
        }
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
    }

    private fun openCursor(selection: String?, args: Array<String>?, orderBy: String): Cursor? =
        context.contentResolver.query(collection, projection, selection, args, orderBy)

    /**
     * Reads one row into a [MediaItem], resolving its kind exactly as before.
     * Returns null for a row that cannot be read.
     */
    private inner class RowReader(cursor: Cursor) {
        private val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        private val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        private val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        private val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        private val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        private val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        private val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        private val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        private val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        private val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        private val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        private val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            -1
        }
        private val volumeCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
        } else {
            -1
        }

        fun read(cursor: Cursor): MediaItem? {
            val id = cursor.longOrZero(idCol)
            val displayName = cursor.stringOrNull(nameCol) ?: "Unnamed"
            val mime = cursor.stringOrNull(mimeCol)
            val mediaTypeHint = if (mediaTypeCol >= 0) cursor.intOrZero(mediaTypeCol) else 0
            val kind = resolveKind(mime, displayName, mediaTypeHint)
            val volume = if (volumeCol >= 0) cursor.stringOrNull(volumeCol) else null

            return MediaItem(
                id = id,
                uri = buildContentUri(id, kind, volume),
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
                mediaType = kind,
                source = MediaSource.MEDIA_STORE,
            )
        }
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
        /** Rows read before the index is published again. */
        const val DEFAULT_BATCH_SIZE = 500

        /** Rows requested per window when paging. */
        const val DEFAULT_PAGE_ROWS = 60

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
