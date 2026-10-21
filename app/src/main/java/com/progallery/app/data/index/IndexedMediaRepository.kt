package com.progallery.app.data.index

import android.net.Uri
import com.progallery.app.data.local.dao.IndexedMediaDao
import com.progallery.app.data.local.entity.IndexedMediaEntity
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's own index of files the system's does not hand over.
 *
 * The gallery merges these into its own order, which is what makes a photo from a messaging app
 * appear among the photos rather than in a separate list — or, before this existed, not at all.
 *
 * **Bounded on purpose.** The whole set is read into memory, which is only honest because these
 * files come from a handful of app folders rather than from the whole device; the cap says so
 * rather than pretending the list is complete.
 */
@Singleton
class IndexedMediaRepository @Inject constructor(
    private val dao: IndexedMediaDao,
    private val settingsRepository: SettingsRepository,
) {

    private companion object {
        /**
         * The most rows the gallery will merge. Beyond this the extra files stop being a
         * handful and the honest answer is to say the list is capped rather than to hold a
         * second library in memory.
         */
        const val MAX_INDEXED = 20_000
    }

    /**
     * Replaces the index with what a scan just found.
     *
     * Replaced rather than appended: a scan is a statement about what is there *now*, so a file
     * the user deleted must leave the index with it. Appending would keep showing a file that no
     * longer exists, and tapping it would fail.
     */
    suspend fun replaceFromScan(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val entities = items
            .filter { it.source != MediaSource.MEDIA_STORE }
            .take(MAX_INDEXED)
            .map { it.toEntity() }
        dao.clear()
        if (entities.isNotEmpty()) dao.upsert(entities)
    }

    /** Adds or updates without removing anything — for a scan that only covered part of a tree. */
    suspend fun addFromScan(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val entities = items
            .filter { it.source != MediaSource.MEDIA_STORE }
            .take(MAX_INDEXED)
            .map { it.toEntity() }
        if (entities.isNotEmpty()) dao.upsert(entities)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    /**
     * Everything indexed, as the gallery's own model.
     *
     * Returns nothing when the user has switched app media out of the gallery — the switch has
     * to change real behaviour, so it is enforced here rather than only in the UI.
     */
    suspend fun all(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!settingsRepository.current().includeAppMediaInGallery) return@withContext emptyList()
        dao.all().take(MAX_INDEXED).map { it.toItem() }
    }

    private fun MediaItem.toEntity() = IndexedMediaEntity(
        mediaKey = mediaKey,
        uri = uri.toString(),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateModifiedSeconds = dateModifiedSeconds,
        dateTakenSeconds = dateTakenSeconds,
        width = width,
        height = height,
        durationMs = durationMs,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        mediaType = mediaType.name,
    )

    private fun IndexedMediaEntity.toItem() = MediaItem(
        id = 0,
        uri = Uri.parse(uri),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateModifiedSeconds = dateModifiedSeconds,
        dateTakenSeconds = dateTakenSeconds,
        width = width,
        height = height,
        durationMs = durationMs,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.OTHER),
        source = MediaSource.DEEP_SCAN,
    )
}
