package com.progallery.app.ui.screens.common

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.Album
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.repository.AlbumRepository
import com.progallery.app.data.repository.MediaRepository
import com.progallery.app.data.repository.ViewerContext
import com.progallery.app.data.settings.LayoutMode
import com.progallery.app.data.settings.SettingsRepository
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import com.progallery.app.util.runCatchingCancellable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

data class BrowserUiState(
    val type: MediaType = MediaType.ALL,
    /** The categories switched on in Settings; they shape the pager's query. */
    val enabledKinds: Set<MediaType> = MediaType.entries.filter { it != MediaType.ALL }.toSet(),
    val favouritesOnly: Boolean = false,
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val viewMode: ViewMode = ViewMode.GRID,
    val columns: Int = 3,
    /** Selected items, keyed by [MediaItem.mediaKey] (the content URI). */
    val selection: Set<String> = emptySet(),
    /**
     * The selected items themselves, recorded when they are picked.
     *
     * The grid is paged, so what is on screen is not the whole library and a selection
     * can outlive the page it was made on. Keeping the objects alongside the keys is
     * what lets share, delete, hide and "add to album" act on something the user
     * selected earlier and then scrolled away from.
     */
    val selectionItems: Map<String, MediaItem> = emptyMap(),
    val loading: Boolean = true,
    /** True only while a pull-to-refresh is in flight. */
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Display flags driven by Settings. */
    val generateThumbnails: Boolean = true,
    val showFileNames: Boolean = false,
    val showFileSize: Boolean = false,
    val pendingDelete: PendingIntent? = null,
    /** Trash rows created for the delete awaiting consent, so they can be rolled back. */
    val pendingTrashIds: List<Long> = emptyList(),
    val message: String? = null,
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()

    val selectedItems: List<MediaItem>
        get() = selection.mapNotNull { selectionItems[it] }
}

/**
 * Shared browsing logic for Photos, Videos, Favourites and Search. Handles
 * selection, sorting, view density, and the real share/favourite/hide/delete
 * actions so every screen behaves identically.
 */
@HiltViewModel
class MediaBrowserViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val albumRepository: AlbumRepository,
    private val viewerContext: ViewerContext,
    private val trashRepository: TrashRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _state.asStateFlow()

    /**
     * The grid, paged.
     *
     * Rebuilt only when the shape of the query changes — the category, the sort, the
     * search text or the favourites filter — and cached, so scrolling back up does
     * not re-read the cursor.
     */
    val pagedItems: Flow<PagingData<MediaItem>> = _state
        .map { PagingKey(it.type, it.enabledKinds, it.sortOrder, it.query, it.favouritesOnly) }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            repository.pagedMedia(
                kinds = if (key.type == MediaType.ALL) key.kinds else setOf(key.type),
                sort = key.sort,
                query = key.query,
                favouritesOnly = key.favouritesOnly,
            )
        }
        .cachedIn(viewModelScope)

    /**
     * Favourite keys, so the grid can flag an item without reloading the pager.
     * Toggling a favourite is frequent; rebuilding the pager for it would reset the
     * scroll position every time.
     */
    val favouriteKeys: StateFlow<Set<String>> = repository.favouriteKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** User-created albums, for the "add to album" picker. */
    private val _customAlbums = MutableStateFlow<List<Album>>(emptyList())
    val customAlbums: StateFlow<List<Album>> = _customAlbums.asStateFlow()

    /** Layout defaults come from Settings once, so in-screen changes still win. */
    private var layoutDefaultsApplied = false

    init {
        refresh()
        // The grid is paged and reports its own load state; the index cache still
        // fills in the background for Home's counts and the album screens.
        _state.update { it.copy(loading = false) }
        viewModelScope.launch {
            albumRepository.observeCustomAlbums().collect { _customAlbums.value = it }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { current ->
                    val withFlags = current.copy(
                        generateThumbnails = settings.generateThumbnails,
                        showFileNames = settings.showFileNames,
                        showFileSize = settings.showFileSize,
                    )
                    if (layoutDefaultsApplied) {
                        withFlags
                    } else {
                        layoutDefaultsApplied = true
                        withFlags.copy(
                            viewMode = if (settings.defaultLayout == LayoutMode.LIST) ViewMode.LIST else ViewMode.GRID,
                            columns = settings.gridSize.columns,
                            sortOrder = settings.defaultSort,
                        )
                    }
                }
            }
        }
    }

    /** Adds the current selection to an existing custom album. */
    fun addSelectedToAlbum(albumId: Long) {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            val added = albumRepository.addToAlbum(albumId, items)
            _state.update {
                it.copy(
                    message = if (added > 0) "Added $added item(s) to album"
                    else "Already in that album",
                )
            }
        }
        clearSelection()
    }

    /** Creates a new custom album and adds the current selection to it. */
    fun createAlbumAndAdd(name: String) {
        val items = _state.value.selectedItems
        viewModelScope.launch {
            val albumId = albumRepository.createAlbum(name)
            val added = if (items.isEmpty()) 0 else albumRepository.addToAlbum(albumId, items)
            _state.update { it.copy(message = "Created \"$name\" with $added item(s)") }
        }
        clearSelection()
    }

    /**
     * Hands the exact list on screen to the viewer before opening an item, so the
     * swipe order matches the grid and no other kind leaks in.
     */
    /**
     * Hands the viewer the whole ordered list for the current query.
     *
     * It cannot come from the grid any more — the grid only holds the pages that have
     * been scrolled to — so it is loaded when the viewer is opened. That is the trade
     * paging makes: the grid stays small, and the full order is paid for only when
     * something actually needs it.
     */
    fun prepareViewerContext() {
        val current = _state.value
        viewModelScope.launch {
            val all = repository.snapshot(
                kinds = if (current.type == MediaType.ALL) current.enabledKinds else setOf(current.type),
                sort = current.sortOrder,
                query = current.query,
            )
            viewerContext.set(all)
        }
    }

    fun configure(
        type: MediaType? = null,
        favouritesOnly: Boolean? = null,
    ) {
        _state.update {
            it.copy(
                type = type ?: it.type,
                favouritesOnly = favouritesOnly ?: it.favouritesOnly,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                repository.refresh()
                _state.update { it.copy(refreshing = false) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(refreshing = false, error = t.message ?: "Failed to load media")
                }
            }
        }
    }

    /**
     * Reloads the index without showing the pull-to-refresh indicator — used when
     * the screen comes back to the foreground, where a spinner would be noise.
     */
    fun refreshQuietly() {
        viewModelScope.launch { runCatchingCancellable { repository.refresh() } }
    }

    fun setSort(order: SortOrder) = _state.update { it.copy(sortOrder = order) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setViewMode(mode: ViewMode) = _state.update { it.copy(viewMode = mode) }

    fun setColumns(columns: Int) = _state.update { it.copy(columns = columns.coerceIn(1, 8)) }

    fun toggleSelection(item: MediaItem) = _state.update { current ->
        val selection = current.selection.toMutableSet()
        val recorded = current.selectionItems.toMutableMap()
        if (selection.add(item.mediaKey)) {
            recorded[item.mediaKey] = item
        } else {
            selection.remove(item.mediaKey)
            recorded.remove(item.mediaKey)
        }
        current.copy(selection = selection, selectionItems = recorded)
    }

    /**
     * Hands a document, archive or other non-previewable file to another app
     * (the phone's own PDF reader, office suite, and so on).
     */
    fun openWith(item: MediaItem) {
        val opened = mediaActions.launchOpen(item)
        if (!opened) {
            _state.update {
                it.copy(message = "No installed app can open ${item.displayName}")
            }
        }
    }

    fun clearSelection() = _state.update {
        it.copy(selection = emptySet(), selectionItems = emptyMap())
    }

    /**
     * Selects everything the grid has loaded.
     *
     * With paging there is no "everything" to select without reading the whole
     * library, so the control is named for what it does: it selects the items that
     * are on screen.
     */
    fun selectAll(loaded: List<MediaItem>) = _state.update { current ->
        current.copy(
            selection = loaded.mapTo(mutableSetOf()) { it.mediaKey },
            selectionItems = loaded.associateBy { it.mediaKey },
        )
    }

    fun shareSelected() {
        val items = _state.value.selectedItems
        if (items.isNotEmpty()) mediaActions.launchShare(items)
        clearSelection()
    }

    fun favouriteSelected() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        val makeFavourite = items.any { !it.isFavourite }
        viewModelScope.launch {
            repository.setFavourite(items = items, favourite = makeFavourite)
            _state.update { it.copy(message = if (makeFavourite) "Added to favourites" else "Removed from favourites") }
        }
        clearSelection()
    }

    fun hideSelected() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.setHidden(items = items, hidden = true)
            _state.update { it.copy(message = "Hidden ${items.size} item(s)") }
        }
        clearSelection()
    }

    /**
     * Recoverable delete: the bytes are copied into the app-managed trash first,
     * and only items that were safely copied are then removed from MediaStore
     * through the platform's consent flow. Declining consent (or a failed
     * removal) rolls the copies back, so the trash never diverges from reality.
     */
    fun deleteSelected() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            val outcome = trashRepository.moveToTrash(items)
            if (outcome.succeeded.isEmpty()) {
                _state.update {
                    it.copy(
                        message = "Could not copy to Recently Deleted, so nothing was deleted. " +
                            outcome.explain(),
                    )
                }
                return@launch
            }
            if (outcome.failedCount > 0) {
                _state.update {
                    it.copy(
                        message = "Copied ${outcome.succeeded.size} of ${items.size}. " + outcome.explain(),
                    )
                }
            }

            val toDelete = outcome.succeeded.map { it.item }
            val request = mediaActions.deleteRequest(toDelete)
            if (request != null) {
                _state.update {
                    it.copy(pendingDelete = request, pendingTrashIds = outcome.succeeded.map { it.trashId })
                }
            } else {
                val ok = mediaActions.deleteDirect(toDelete)
                if (ok) {
                    _state.update {
                        it.copy(message = "Moved ${outcome.succeeded.size} item(s) to Recently Deleted")
                    }
                } else {
                    trashRepository.discard(outcome.succeeded.map { it.trashId })
                    _state.update { it.copy(message = "Could not delete — nothing was changed") }
                }
                refresh()
            }
        }
        clearSelection()
    }

    fun onDeleteConsentResult(approved: Boolean) {
        val trashIds = _state.value.pendingTrashIds
        _state.update { it.copy(pendingDelete = null, pendingTrashIds = emptyList()) }
        if (approved) {
            _state.update { it.copy(message = "Moved to Recently Deleted") }
            refresh()
        } else {
            viewModelScope.launch {
                trashRepository.discard(trashIds)
                _state.update { it.copy(message = "Deletion cancelled — nothing was changed") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** The shape of the query the pager is built for. */
private data class PagingKey(
    val type: MediaType,
    val kinds: Set<MediaType>,
    val sort: SortOrder,
    val query: String,
    val favouritesOnly: Boolean,
)
