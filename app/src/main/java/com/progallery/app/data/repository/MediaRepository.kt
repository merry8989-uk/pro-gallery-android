package com.progallery.app.data.repository

import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.entity.FavouriteEntity
import com.progallery.app.data.mediastore.MediaStoreDataSource
import com.progallery.app.data.model.Album
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.domain.SortComparators
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for media shown in the UI. Merges the MediaStore
 * index with app-side state (favourites, hidden items) and exposes Flows so
 * every screen reacts to changes.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val mediaStore: MediaStoreDataSource,
    private val favouriteDao: FavouriteDao,
    private val hiddenDao: HiddenDao,
) {

    private val cache = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Reloads the MediaStore index into the in-memory cache. */
    suspend fun refresh(): Int {
        val items = mediaStore.loadMedia(MediaType.ALL)
        cache.value = items
        return items.size
    }

    /**
     * Observes media of [type], decorated with favourite state and with hidden
     * items removed. Sorted by [sortOrder].
     */
    fun observeMedia(
        type: MediaType = MediaType.ALL,
        sortOrder: SortOrder = SortOrder.DATE_NEWEST,
        includeHidden: Boolean = false,
    ): Flow<List<MediaItem>> = combine(
        cache,
        favouriteDao.observeAll(),
        hiddenDao.observeIds(),
    ) { items, favourites, hiddenIds ->
        val favouriteIds = favourites.mapTo(HashSet()) { it.mediaId }
        val hiddenSet = hiddenIds.toHashSet()
        val decorated = items.asSequence()
            .filter { type == MediaType.ALL || it.mediaType == type }
            .filter { includeHidden || it.id !in hiddenSet }
            .map { it.copy(isFavourite = it.id in favouriteIds) }
            .toList()
        SortComparators.sort(decorated, sortOrder)
    }.flowOn(Dispatchers.Default)

    /** Auto albums derived from folders/buckets present in the current index. */
    fun observeAlbums(): Flow<List<Album>> = combine(cache, hiddenDao.observeIds()) { items, hidden ->
        val hiddenSet = hidden.toHashSet()
        items.asSequence()
            .filterNot { it.id in hiddenSet }
            .groupBy { it.bucketId to it.bucketName }
            .map { (key, group) ->
                Album(
                    id = key.first,
                    name = key.second,
                    coverUri = group.firstOrNull()?.uri,
                    itemCount = group.size,
                    totalBytes = group.sumOf { it.sizeBytes },
                )
            }
            .sortedByDescending { it.itemCount }
            .toList()
    }.flowOn(Dispatchers.Default)

    suspend fun toggleFavourite(item: MediaItem) = withContext(Dispatchers.IO) {
        if (favouriteDao.isFavourite(item.id)) {
            favouriteDao.remove(item.id)
        } else {
            favouriteDao.add(FavouriteEntity(mediaId = item.id, uri = item.uri.toString()))
        }
    }

    suspend fun setFavourite(ids: List<Long>, uris: List<String>, favourite: Boolean) =
        withContext(Dispatchers.IO) {
            if (favourite) {
                ids.forEachIndexed { index, id ->
                    favouriteDao.add(FavouriteEntity(mediaId = id, uri = uris.getOrElse(index) { "" }))
                }
            } else {
                favouriteDao.removeAll(ids)
            }
        }

    suspend fun setHidden(ids: List<Long>, uris: List<String>, hidden: Boolean) =
        withContext(Dispatchers.IO) {
            if (hidden) {
                ids.forEachIndexed { index, id ->
                    hiddenDao.hide(
                        com.progallery.app.data.local.entity.HiddenEntity(
                            mediaId = id,
                            uri = uris.getOrElse(index) { "" },
                        ),
                    )
                }
            } else {
                ids.forEach { hiddenDao.unhide(it) }
            }
        }

    suspend fun currentSnapshot(): List<MediaItem> = cache.value

    /**
     * Distinct folders present in the index, as MediaStore relative paths
     * (e.g. "Pictures/", "DCIM/Camera/"). Used as move targets.
     */
    suspend fun folders(): List<String> = withContext(Dispatchers.IO) {
        cache.value.mapNotNull { it.relativePath }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
