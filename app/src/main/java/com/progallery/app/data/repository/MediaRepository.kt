package com.progallery.app.data.repository

import android.content.Context
import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.entity.FavouriteEntity
import com.progallery.app.data.local.entity.HiddenEntity
import com.progallery.app.data.mediastore.MediaStoreDataSource
import com.progallery.app.data.mediastore.MediaStoreObserver
import com.progallery.app.data.model.Album
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.settings.AppSettings
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.util.runCatchingCancellable
import com.progallery.app.di.ApplicationScope
import com.progallery.app.domain.SortComparators
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.progallery.app.data.mediastore.MediaStorePagingSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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
    private val mediaStoreObserver: MediaStoreObserver,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    init {
        // The system's index changes while the app is open — a photo taken, a file
        // deleted elsewhere, a rename. Follow it so the UI is never stale.
        applicationScope.launch {
            mediaStoreObserver.changes().collect {
                runCatchingCancellable { refresh() }
            }
        }
    }

    /** Live settings, so the UI can react to toggles. */
    val settings: Flow<AppSettings> = settingsRepository.settings

    private val cache = MutableStateFlow<List<MediaItem>>(emptyList())

    private companion object {
        /** A ceiling for the viewer's full-list load, so a pathological index cannot exhaust memory. */
        const val MAX_SNAPSHOT_ITEMS = 50_000
    }

    /**
     * The media grid, paged.
     *
     * Keyed on the shape of the query — the enabled kinds, the sort and the search
     * text — plus the hidden set, because a hidden item must not enter the pager at
     * all. Favourites are deliberately **not** part of the key: toggling one must not
     * throw away the user's scroll position, so the UI applies that flag as it
     * renders (see [favouriteKeys]).
     */
    fun pagedMedia(
        kinds: Set<MediaType>,
        sort: SortOrder,
        query: String,
    ): Flow<PagingData<MediaItem>> =
        combine(hiddenDao.observeKeys(), settingsRepository.settings) { hidden, settings ->
            hidden.toHashSet() to settings
        }.flatMapLatest { (hiddenSet, settings) ->
            Pager(
                config = PagingConfig(
                    pageSize = MediaStoreDataSource.DEFAULT_PAGE_ROWS,
                    initialLoadSize = MediaStoreDataSource.DEFAULT_PAGE_ROWS * 2,
                    prefetchDistance = MediaStoreDataSource.DEFAULT_PAGE_ROWS / 2,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    MediaStorePagingSource(
                        dataSource = mediaStore,
                        kinds = kinds,
                        sort = sort,
                        query = query,
                        hiddenKeys = hiddenSet,
                        sizeFilter = settings::acceptsSize,
                    )
                },
            ).flow
        }

    /** Favourite keys, so the UI can flag paged items without reloading the pager. */
    val favouriteKeys: Flow<Set<String>> = favouriteDao.observeAll()
        .map { list -> list.mapTo(HashSet()) { it.mediaKey } }

    /**
     * The whole ordered list for the same query the pager used, decorated.
     *
     * The full-screen viewer needs every item in order to swipe through, so this is
     * loaded when it is opened rather than being kept in memory for the grid — which
     * is the point of paging the grid in the first place.
     */
    suspend fun snapshot(
        kinds: Set<MediaType>,
        sort: SortOrder,
        query: String,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.current()
        val favourites = favouriteDao.allKeys().toHashSet()
        val hidden = hiddenDao.allKeys().toHashSet()
        val all = ArrayList<MediaItem>()

        var row = 0
        var exhausted = false
        while (!exhausted && all.size < MAX_SNAPSHOT_ITEMS) {
            val page = mediaStore.page(kinds, sort, query, row, MediaStoreDataSource.DEFAULT_BATCH_SIZE)
            page.items.forEach { item ->
                if (item.mediaKey in hidden) return@forEach
                if (!settings.acceptsSize(item.sizeBytes)) return@forEach
                all += item.copy(isFavourite = item.mediaKey in favourites)
            }
            row += page.rowsRead
            exhausted = page.exhausted || page.rowsRead == 0
        }
        all
    }

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
        // Read the index in batches and publish each one, so the grid can paint
        // after the first batch instead of waiting for the whole library.
        val accumulated = ArrayList<MediaItem>()
        mediaStore.loadMediaBatches(current.enabledKinds, current.scanBatchSize).collect { batch ->
            accumulated += batch
            cache.value = accumulated.toList()
        }
        return accumulated.size
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
