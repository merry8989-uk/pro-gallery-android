package com.progallery.app.data.repository

import android.content.Context
import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.entity.FavouriteEntity
import com.progallery.app.data.local.entity.HiddenEntity
import com.progallery.app.data.mediastore.MediaStoreDataSource
import com.progallery.app.data.model.Album
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.settings.AppSettings
import com.progallery.app.data.settings.SettingsRepository
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
 *
 * App-side state is matched on [MediaItem.mediaKey] (the content URI), never on
 * the numeric id, so an image and a video that happen to share an id cannot
 * affect each other.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStoreDataSource,
    private val favouriteDao: FavouriteDao,
    private val hiddenDao: HiddenDao,
    private val settingsRepository: SettingsRepository,
) {

    /** Live settings, so the UI can react to toggles. */
    val settings: Flow<AppSettings> = settingsRepository.settings

    private val cache = MutableStateFlow<List<MediaItem>>(emptyList())

    /**
     * Reloads the MediaStore index into the in-memory cache.
     *
     * Respects the settings: if Quick scan is off, or every file category is
     * off, the index is emptied instead of quietly loading anyway.
     */
    suspend fun refresh(): Int {
        val current = settingsRepository.current()
        if (!current.quickScanEnabled || !current.anyCategoryEnabled) {
            cache.value = emptyList()
            return 0
        }
        val items = mediaStore.loadMedia(current.enabledKinds)
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
        hiddenDao.observeKeys(),
        settingsRepository.settings,
    ) { items, favourites, hiddenKeys, settings ->
        val favouriteKeys = favourites.mapTo(HashSet()) { it.mediaKey }
        val hiddenSet = hiddenKeys.toHashSet()
        val enabledKinds = settings.enabledKinds
        val decorated = items.asSequence()
            // A disabled category disappears from every list, not just its own tab.
            .filter { it.mediaType in enabledKinds }
            // Size range (1 byte to unlimited by default). Unreadable sizes are
            // never treated as too small, so nothing real is hidden.
            .filter { settings.acceptsSize(it.sizeBytes) }
            .filter { type == MediaType.ALL || it.mediaType == type }
            .filter { includeHidden || it.mediaKey !in hiddenSet }
            .map { it.copy(isFavourite = it.mediaKey in favouriteKeys) }
            .toList()
        SortComparators.sort(decorated, sortOrder)
    }.flowOn(Dispatchers.Default)

    /** Auto albums derived from folders/buckets present in the current index. */
    fun observeAlbums(): Flow<List<Album>> = combine(cache, hiddenDao.observeKeys()) { items, hidden ->
        val hiddenSet = hidden.toHashSet()
        items.asSequence()
            .filterNot { it.mediaKey in hiddenSet }
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
        if (favouriteDao.isFavourite(item.mediaKey)) {
            favouriteDao.remove(item.mediaKey)
        } else {
            favouriteDao.add(FavouriteEntity(mediaKey = item.mediaKey, uri = item.uri.toString()))
        }
    }

    suspend fun setFavourite(items: List<MediaItem>, favourite: Boolean) =
        withContext(Dispatchers.IO) {
            if (favourite) {
                items.forEach {
                    favouriteDao.add(FavouriteEntity(mediaKey = it.mediaKey, uri = it.uri.toString()))
                }
            } else {
                favouriteDao.removeAll(items.map { it.mediaKey })
            }
        }

    suspend fun setHidden(items: List<MediaItem>, hidden: Boolean) =
        withContext(Dispatchers.IO) {
            if (hidden) {
                items.forEach {
                    hiddenDao.hide(HiddenEntity(mediaKey = it.mediaKey, uri = it.uri.toString()))
                }
            } else {
                items.forEach { hiddenDao.unhide(it.mediaKey) }
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
