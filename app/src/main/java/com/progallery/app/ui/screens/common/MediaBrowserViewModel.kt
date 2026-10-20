package com.progallery.app.ui.screens.common

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import com.progallery.app.data.model.ViewMode
import com.progallery.app.data.repository.MediaRepository
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
    val selection: Set<Long> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    val pendingDelete: PendingIntent? = null,
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
    val selectedItems: List<MediaItem> get() = items.filter { it.id in selection }
}

/**
 * Shared browsing logic for Photos, Videos, Favourites and Search. Handles
 * selection, sorting, view density, and the real share/favourite/hide/delete
 * actions so every screen behaves identically.
 */
@HiltViewModel
class MediaBrowserViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaActions: MediaActions,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var collectionJob: Job? = null

    init {
        refresh()
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
        if (!selection.add(item.id)) selection.remove(item.id)
        current.copy(selection = selection)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun selectAll() = _state.update { it.copy(selection = it.items.map { m -> m.id }.toSet()) }

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
            repository.setFavourite(
                ids = items.map { it.id },
                uris = items.map { it.uri.toString() },
                favourite = makeFavourite,
            )
            _state.update { it.copy(message = if (makeFavourite) "Added to favourites" else "Removed from favourites") }
        }
        clearSelection()
    }

    fun hideSelected() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.setHidden(
                ids = items.map { it.id },
                uris = items.map { it.uri.toString() },
                hidden = true,
            )
            _state.update { it.copy(message = "Hidden ${items.size} item(s)") }
        }
        clearSelection()
    }

    fun deleteSelected() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        val request = mediaActions.deleteRequest(items)
        if (request != null) {
            _state.update { it.copy(pendingDelete = request) }
        } else {
            val ok = mediaActions.deleteDirect(items)
            _state.update {
                it.copy(
                    message = if (ok) "Deleted ${items.size} item(s)" else "Could not delete some items",
                )
            }
            refresh()
        }
        clearSelection()
    }

    fun onDeleteConsentResult(approved: Boolean) {
        _state.update { it.copy(pendingDelete = null) }
        if (approved) {
            _state.update { it.copy(message = "Items deleted") }
            refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
