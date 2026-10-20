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
import com.progallery.app.data.repository.TrashRepository
import com.progallery.app.domain.MediaFilters
import com.progallery.app.util.MediaActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val rawItems: List<MediaItem> = emptyList(),
    val type: MediaType = MediaType.ALL,
    val favouritesOnly: Boolean = false,
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val viewMode: ViewMode = ViewMode.GRID,
    val columns: Int = 3,
    /** Selected items, keyed by [MediaItem.mediaKey] (the content URI). */
    val selection: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    val pendingDelete: PendingIntent? = null,
    /** Trash rows created for the delete awaiting consent, so they can be rolled back. */
    val pendingTrashIds: List<Long> = emptyList(),
    val message: String? = null,
) {
    /** Items after applying the search query and the favourites filter. */
    val items: List<MediaItem>
        get() {
            var result = MediaFilters.search(rawItems, query)
            if (favouritesOnly) result = MediaFilters.favourites(result)
            return result
        }

    val selectionMode: Boolean get() = selection.isNotEmpty()
    val selectedItems: List<MediaItem> get() = items.filter { it.mediaKey in selection }
}

/**
 * Shared browsing logic for Photos, Videos, Favourites and Search. Handles
 * selection, sorting, view density, and the real share/favourite/hide/delete
 * actions so every screen behaves identically.
 */
@HiltViewModel
class MediaBrowserViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val trashRepository: TrashRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _state.asStateFlow()

    /** User-created albums, for the "add to album" picker. */
    private val _customAlbums = MutableStateFlow<List<Album>>(emptyList())
    val customAlbums: StateFlow<List<Album>> = _customAlbums.asStateFlow()

    private var collectionJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            albumRepository.observeCustomAlbums().collect { _customAlbums.value = it }
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
        restartCollection()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                repository.refresh()
                _state.update { it.copy(loading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.message ?: "Failed to load media") }
            }
        }
        restartCollection()
    }

    private fun restartCollection() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            repository.observeMedia(
                type = _state.value.type,
                sortOrder = _state.value.sortOrder,
            ).collect { items ->
                _state.update { it.copy(rawItems = items, loading = false) }
            }
        }
    }

    fun setSort(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
        restartCollection()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setViewMode(mode: ViewMode) = _state.update { it.copy(viewMode = mode) }

    fun setColumns(columns: Int) = _state.update { it.copy(columns = columns.coerceIn(1, 8)) }

    fun toggleSelection(item: MediaItem) = _state.update { current ->
        val selection = current.selection.toMutableSet()
        if (!selection.add(item.mediaKey)) selection.remove(item.mediaKey)
        current.copy(selection = selection)
    }

    /** Hands a non-previewable file (audio, document, archive…) to the system. */
    fun openWith(item: MediaItem) {
        val opened = mediaActions.launchOpen(item)
        if (!opened) {
            _state.update { it.copy(message = "No app on this device can open ${item.displayName}") }
        }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun selectAll() = _state.update { it.copy(selection = it.items.map { m -> m.mediaKey }.toSet()) }

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
            val trashed = trashRepository.moveToTrash(items)
            if (trashed.isEmpty()) {
                _state.update {
                    it.copy(message = "Could not move to Recently Deleted — nothing was deleted")
                }
                return@launch
            }
            if (trashed.size < items.size) {
                _state.update {
                    it.copy(message = "Moved ${trashed.size} of ${items.size} to Recently Deleted")
                }
            }

            val toDelete = trashed.map { it.item }
            val request = mediaActions.deleteRequest(toDelete)
            if (request != null) {
                _state.update {
                    it.copy(pendingDelete = request, pendingTrashIds = trashed.map { it.trashId })
                }
            } else {
                val ok = mediaActions.deleteDirect(toDelete)
                if (ok) {
                    _state.update {
                        it.copy(message = "Moved ${trashed.size} item(s) to Recently Deleted")
                    }
                } else {
                    trashRepository.discard(trashed.map { it.trashId })
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
